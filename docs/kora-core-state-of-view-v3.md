# Kora Core — What Holds, and What Does Not

## An Engineering State of View, v3

**Author**: Ivan Joël Tchatchoua Bayon — [@Geekers-Joel237](https://github.com/Geekers-Joel237)
**Date**: 2026-10-15 · **Basis**: commit `d28a6be` on `develop`
**Scope**: Steps 0, 1, 3 delivered · Step 2 phase A · Step 4 not started

> v1 (Step 1) and v2 (Steps 3 and 2A) stay as they were written. This document is a review,
> not a changelog: it records what the system guarantees today, what each guarantee cost,
> and where it stops. Every claim was checked against the code at the commit above; §7
> says how, and what was not verified.

---

## 0. Summary

- **Architecture is ahead of the system's needs.** The hexagon is complete and enforced: no
  foreign type in `application/` or `ports/`, 19 architecture rules, 579 tests, no mocking
  framework.
- **Money safety is behind it.** Within one transaction, the ledger is correct. Across the
  provider call, it is not: three failure paths can move money at the provider without a
  matching ledger entry, and none of them can be detected today (L-1 to L-3, §3.1).
- **Security is a baseline, not a posture.** No rate limiting anywhere. OTP attempts are
  unbounded within their window. The application connects to the database as the superuser.
- **Performance is measured once, on a laptop, at 25 req/s.** The Step 3 target is 100 req/s.
- **Observability exists only for load tests.** Production would run with no metrics, no
  alerts and no business signal.

The next step should be **money safety**, not more architecture: reservation on cash-out,
a durable trace before the provider call, and detection of stuck transactions (§5).

---

## 1. The system today

| Area | State |
|---|---|
| Operations | Register, OTP login, refresh · cash-in, cash-out, P2P transfer, balance, history · operator reversal (`/admin/payments/{txId}/reverse`) |
| Ledger | Double entry, append-only, per transaction and per currency. One currency in practice (XAF), whole units only |
| Lifecycle | 12 states; 11 reachable (`UNKNOWN` is not, see L-12). Every transition writes a `TrxStateHistoric` row |
| Provider | Stub (`MobileMoneyPaymentProviderAdapter`) that simulates latency and failures |
| Shape | One Gradle project, one deployable. Three packages (`auth`, `payment`, `shared`), each hexagonal |
| Persistence | PostgreSQL 17, 4 Flyway migrations, 11 `CHECK` constraints on enumerated columns |
| Tests | 58 classes; 579 tests in the last full run (2026-10-15), 0 failures. Unit 401 · integration 72 · e2e 66 test methods (`@ParameterizedTest` expands at run time) |
| Load | ADR-005 amendment, 2026-10-13, local machine, 25 req/s: balance p95 8.7 ms · cash p95 2.06 s · transfer p95 219 ms (target < 200, missed) · 0 % errors |
| Clients | Mobile app (`mobile/`) consuming the API; it works around 7 backend gaps (see `mobile/docs/09-api-evolution.md` §4) |

---

## 2. Decisions and their price

Each row is a choice that was made on purpose. "Cost" is the part that is still being paid.

| # | Decision | Rejected alternative | Why | Cost still paid |
|---|---|---|---|---|
| D-1 | **Balance cache**: `accounts.balance_amount` is written in the same transaction as the ledger entries (ADR-001) | Compute the balance from the ledger on every read (ROADMAP Step 0 asks for this) | Constant-time balance reads, and one row to lock | Two sources of truth. Nothing checks that they agree (L-7) |
| D-2 | **Micro-transactions**: TX-1 / provider I/O / TX-2 (ADR-004) | One `@Transactional` around the whole flow | One transaction holding a connection during the provider call gave p95 60 s and 73.93 % errors at 25 req/s | No transaction spans the money movement. Failures between the phases become money-safety gaps (L-1 to L-4) |
| D-3 | **One endpoint per operation** (ADR-003) | Separate authorize and capture calls exposed to the client | Simpler for the client; the server drives the saga | The client waits the full provider latency (~2 s) on a single request |
| D-4 | **Hexagonal before modular** (ADR-007) | Follow the roadmap order | A module boundary drawn over a framework-coupled layer would have frozen that coupling into the contract | Step 2 is still open; the 50 000 tx/day gate of Step 3 was never measured |
| D-5 | **`TransactionBoundary` is not a Unit of Work**; a rollback happens only by throwing | A UoW port, or `setRollbackOnly` | Hibernate already tracks writes. A second tracker would be a second opinion on when a write happens | A use case cannot commit partial work on purpose. Intentional |
| D-6 | **Retry wraps TX-2 only, and only on `ConcurrentUpdateException`** | Retry the whole flow, or retry inside the transaction | Retrying inside a dead transaction is useless; retrying across the provider charges the customer twice | Any other TX-2 failure is final, even after the provider moved the money (L-2) |
| D-7 | **Settlement is recorded synchronously**: TX-2 walks `CAPTURED → SETTLEMENT_PENDING → SETTLED → COMPLETED` in one transaction | Stop at `SETTLEMENT_PENDING` and settle later | Nothing reconciles yet (Step 6), so nothing could ever settle a pending transaction | `SETTLED` states a fact nobody observed (L-5) |
| D-8 | **Real in-memory doubles, no mocking framework** | Mockito | A double can be asserted and has to behave like the real thing. v2 §7 records four bugs that forgiving doubles had hidden | Each double has to be maintained, and kept honest |
| D-9 | **`CHECK` constraints instead of native `ENUM` types** | `CREATE TYPE … AS ENUM` | PostgreSQL has no `DROP VALUE`, and native enums would add `@JdbcTypeCode` to ten entities | Adding an enum constant requires a migration in the same diff; a test asserts both sides are equal |
| D-10 | **PIN checked with BCrypt on every payment** | Check it once per session, or with a cheaper hash | A stolen access token alone cannot move money | ~60–80 ms of CPU per payment. At 100 req/s, about 6 cores (ADR-005). It is the first expected bottleneck |
| D-11 | **Stub provider** | A real sandbox | Every failure mode can be reproduced in a test | Real network behaviour has never been met: timeouts, late confirmations, partial failures (§3.5) |
| D-12 | **Metrics exported to InfluxDB only in the `perf` profile** | Always-on metrics | Avoid running a metrics stack every day for a few hours of load tests a month | Every other profile, including `prod`, has no metrics at all (L-15) |

---

## 3. Limits

Ranked by impact. Each ID gives the location in the code, the failure, and whether a test
covers it.

### 3.1 Money — critical

**L-1 · Cash-out: funds are checked without a reservation, then the money is paid out.**
`Ledger.initiate` checks the balance in TX-1 without a lock and without a hold. The provider
then disburses. The debit happens only in TX-2. Two concurrent cash-outs, or a cash-out and
a transfer, can both pass TX-1. In that case:
- The provider pays out twice.
- TX-2 of the second request throws `InsufficientFundsException` and rolls back.
- The client receives a refusal.
- The money has already left. The transaction stays `INITIALIZED`, and no ledger entry
  records the payout.

`CashOutService.java:69-91`. This was found by reading the code and not reproduced: the only
concurrency test (`MoneyIntegrityE2ETest.concurrent_cash_ins_all_return_200`) covers
credits, never debits.

> **Closed — 2026-10-17** (ADR-008). Reproduced first by `ConcurrentDebitE2ETest`: two
> concurrent cash-outs left one transaction `INITIALIZED` after a payout, and a transfer
> could drain a wallet while a cash-out waited on the provider. A cash-out now places a
> funds hold in TX-1 under the wallet lock, and every debit is checked against the
> available balance (ledger balance minus active holds). The second debit is refused
> before the provider pays.

**L-2 · A provider success followed by a TX-2 failure leaves no internal trace.**
ADR-004 documents this gap as G-2/G-3 and states that the `AuthorizationRecord` makes it
auditable. It does not: the record is created inside TX-2 (`CashInService.settle`,
`CashOutService.settle`), so it rolls back with TX-2. The only remaining internal trace is
the `INITIALIZED` row written by TX-1.

The same outcome follows when the retry runs out (`RetryExhaustedException` → 503): the
provider moved the money, and the client receives a 503.

> **Accepted — 2026-10-17.** A known gap, not fixed in the payment path: the
> reconciliation engine of roadmap Step 6 catches it after the fact, as
> `MISSING_INTERNAL`. Reproduced by `ProviderSuccessLostE2ETest`, kept disabled to
> document it: with TX-2 forced to fail after the provider succeeded, a
> cash-in ends `INITIALIZED` with its wallet not credited (503), and a cash-out ends
> `INITIALIZED` with its hold still `ACTIVE` (401, see N-4). In both cases no provider
> reference is stored anywhere. The cash-out hold of L-1 keeps the paid-out funds from
> being spent twice, but does not say whether the provider paid.

**L-3 · A crash between TX-1 and TX-2 leaves the transaction `INITIALIZED` forever** (G-1).
There is no reaper and no detection query. ADR-004 planned both for Step 3; they did not
ship.

> **Accepted — 2026-10-17.** A known gap, not fixed in the current payment path. With the
> event-driven architecture of roadmap Step 5 and a real transactional flow per use case,
> the cash-out becomes a saga with its own compensations; recovery belongs there, and
> reconciliation (Step 6) catches what is left. Until then, a crash between TX-1 and TX-2
> also leaves a cash-out's funds hold `ACTIVE` (ADR-008): the customer's funds stay
> blocked until someone resolves it by hand.

**L-4 · No idempotency.** A client retry creates a second transaction. Roadmap Step 4. The
anti-replay middleware is not a substitute:
- It is keyed on `X-Correlation-Id`, a header the client may omit. Without the header,
  every request gets a fresh id, so nothing is ever flagged as a replay.
- A replay is refused (409); the original result is not returned.
- The key is not bound to the user.
- The seen ids live in the JVM heap, so it works on a single instance only.

The mobile app compensates with a four-layer lock on the client side.

> **Accepted — 2026-10-17.** A known bug, accepted for now. Idempotency is planned for
> roadmap Step 4 and is not to be fixed before it.

### 3.2 Money — structural

**L-5 · `SETTLED` and `COMPLETED` are written when the capture succeeds** (D-7). A customer
sees `COMPLETED` before any settlement has been confirmed. `SETTLEMENT_FAILED` can only be
reached from `SETTLEMENT_PENDING`, which never persists. A provider that fails at D+1 can
therefore only be recorded as a reversal of a `COMPLETED` transaction.

> **Withdrawn — 2026-10-17.** Not a limit. Settlement is internal; the customer never sees
> it. What the customer sees is `CustomerStatus`, which reads `COMPLETED` from capture on:
> once the money is captured, the operation is done for them. The internal settlement
> states belong to reconciliation (roadmap Step 6), which is not built yet.

**L-6 · The ledger invariants are not enforced by the database.** No constraint enforces
`SUM(DEBIT) = SUM(CREDIT)`, and no trigger prevents `UPDATE` or `DELETE` on
`ledger_entries`. Both rules live in the domain and in tests, so any SQL client that
bypasses the domain bypasses them too. Given L-11, that SQL client is the application's own
connection, which has every right.

> **Closed — 2026-10-17**, for append-only. The database refuses any `UPDATE` or `DELETE`
> on `ledger_entries` and `trx_state_historics`, a soft delete included, and a negative
> ledger amount (`V202610171900__make_ledger_append_only.sql`). Reproduced first by
> `AppendOnlyLedgerDbTest`. The trigger then caught the application itself: the
> unidirectional `@OneToMany` of `TransactionEntity` re-pointed every new entry with an
> `UPDATE ledger_entries SET transaction_id`; its join column is now read-only.
> Double entry stays a domain rule, by decision. `TRUNCATE` is not blocked, and a
> superuser can disable a trigger: L-11 closes that door.

**L-7 · Nothing checks the balance cache against the ledger** (D-1). On divergence, the
ledger wins (ADR-001), but no process computes the divergence. The float account keeps
`balance_amount = 0` by design, so its position is visible only through a query on
`ledger_entries`.

> **Deferred — 2026-10-17.** Detection only, never automatic correction (ADR-001): a read
> query listing customer accounts whose `balance_amount` differs from their ledger sum,
> with the float account's ledger position, run by a scheduled check — incremental on
> touched accounts, full at night. Off the payment path, no cost on TX-1 or TX-2. A
> trigger recomputing the sum at commit was rejected: under the wallet lock, it grows
> with the ledger. Comes with the daily balance snapshots of reconciliation (Step 6).

### 3.3 Security

**L-8 · OTP attempts are unbounded.** In `MailedOtpChallenge.consume`, a wrong code does not
invalidate the challenge. A 6-digit code lives 5 minutes, and nothing limits how many codes
can be tried in that window.

> **Confirmed — 2026-10-17.** To be done. Planned in roadmap Step 4: "Brute-force /
> credential stuffing protection".

**L-9 · No rate limiting.** Nothing limits requests on `/auth/**`, on PIN checks, or on
payments. ROADMAP Step 3 required hardened configuration with rate limiting; it was not
built.

> **Confirmed — 2026-10-17.** To be done. Planned in roadmap Step 4: "Strict rate limiting
> (anti-abuse)", carried over from Step 3.

**L-10 · `/auth/refresh` accepts any validly signed token.** Access and refresh tokens share
the signing key and carry no type claim (`JwtTokenIssuer.subjectOf`). So an access token
valid for 15 minutes can be exchanged for a refresh token valid for 7 days. Tokens cannot
be revoked, and logout exists only on the client.

> **Closed — 2026-10-16**, on `feature/KC-xx-l10-token-hardening`. Reproduced first: an
> access token at `/auth/refresh` returned 200, a refresh token as `Bearer` returned 403.
> Now each token use has its own signing key and a `typ` claim, and each is refused at the
> other's door with 401. Every refresh token is recorded in `refresh_tokens`: it can be
> exchanged once, a replay revokes its whole session, and `POST /auth/logout` revokes it
> server-side. A suspended user can no longer renew. All of it lives in the JWT adapter;
> the domain and the use cases see opaque tokens only, so an OAuth2 server could replace
> the adapter without touching them. What remains: an access token stays valid up to
> 15 minutes after logout, and expired `refresh_tokens` rows are not purged.

**L-11 · The application connects to the database as the superuser.** The application user,
the migration user and `POSTGRES_USER` are the same account in `.env`. The split into
`kora_app` (DML only) and `kora_migration` (DDL) is designed but not done (KC-05). A SQL
injection would therefore have DDL rights.

> **Closed — 2026-10-17.** `docker/postgres/initdb/10-kora-roles.sh` creates `kora_migration`,
> owner of the schema, and `kora_app`; `V202610171921__grant_application_role.sql` gives
> `kora_app` DML only, without `UPDATE` or `DELETE` on `ledger_entries` and
> `trx_state_historics`. The application can no longer `CREATE`, `ALTER`, `DROP` or
> `TRUNCATE`, nor disable the append-only triggers. Reproduced first by
> `ApplicationDatabaseRoleTest`; every test now connects as `kora_app`. The perf
> `/test/reset` truncates through the migration role. An existing volume is recreated,
> or realigned by running the script by hand (CONTRIBUTING.md section 9).

Lower: the `/test/**` endpoints are compiled into every build and enabled by the `perf`
profile alone; one of them returns OTP codes.

### 3.4 Correctness and contract

**L-12 · `UNKNOWN` is a dead state.** It was added to the domain and to the `CHECK`
constraints (migration `V202610131159`). Nothing transitions into it. Unlike the 11 other
states, it throws `IllegalStateException` instead of `InvalidStateTransitionException`, and
no test covers it. Likewise, `UserStatus.PENDING` is never assigned.

> **Requalified — 2026-10-17.** `UNKNOWN` is not dead by design: it is the state for a
> provider timeout with no clear answer. It is **unwired**. Nothing leads into it, and the
> adapter throws the same `PaymentProviderException` for a timeout and for a refusal, so a
> timeout today ends in `AUTHORIZATION_FAILED` or `CAPTURE_FAILED`.
>
> **Closed — 2026-10-17.** `PaymentTimeoutProviderException` extends
> `PaymentProviderException` and is caught before it at every provider call. A timeout on
> `authorize` moves the transaction from `INITIALIZED` to `UNKNOWN`; on `capture`, the
> authorization is recorded first, its provider reference kept for reconciliation, then
> `AUTHORIZED` to `UNKNOWN`; on `reverse`, `AUTHORIZED` to `UNKNOWN`. A cash-out hold stays
> `ACTIVE`. Any other provider error keeps its failed state. `UnknownState` now throws
> `InvalidStateTransitionException`. Reproduced first in `PaymentUseCaseTest` and
> `ProviderFailureE2ETest`. Leaving `UNKNOWN` belongs to reconciliation (Step 6): until then
> `UnknownState` refuses every transition.

**L-13 · A provider failure during a reversal returns an unhandled 500.**
`ReversePaymentService` calls `provider.reverse` outside any `try`.
`PaymentProviderException` has no `@ExceptionHandler`, which breaks review rule 15. The
mobile team reported this on 2026-08-06; it is still open.

> **Still open — 2026-10-17.** Also found: the result of `provider.reverse` is ignored. A
> `ReverseResult` with `success = false` still marks the transaction `REVERSED` and cancels
> the authorization record, while the hold stays in place at the provider. A fix was drafted
> (timeout retried 3 times; refusal 502, unknown outcome 504; the transaction stays
> `AUTHORIZED`) and set aside, not merged.
>
> **Partly closed — 2026-10-17**, with L-12. `PaymentProviderException` is mapped to 502,
> so a refusal during a reversal no longer escapes as a 500; a timeout leaves the
> transaction `UNKNOWN`. Still open: `ReverseResult.success` is ignored. The drafted fix
> is obsolete: it kept a timed-out reversal `AUTHORIZED`.
>
> **Still open — 2026-11-23.** Confirmed by reading `ReversePaymentService.execute`, and
> wider than stated: the provider's reversal reference is dropped as well, so the history
> row of a release records `providerRef = null`. See §9.2, R-3.

**L-14 · The XAF rule (whole units only) is enforced at the web edge, not in the domain.**
`@Digits(fraction = 0)` on the three request types is the only guard. `Amount` accepts any
scale, so any other driving adapter (scheduler, event consumer) would bypass the rule.

> **Withdrawn — 2026-10-17.** Not a limit. Currency is an open set; XAF is today's usage,
> not a rule the domain has to formalise.

### 3.5 Performance and scale

- **No measurement above 25 req/s.** The 100 req/s run is calibrated but has not been
  executed. The only measurement comes from a laptop.
- **One thread blocked per provider call.** The stub holds a Tomcat thread for about 1.9 s,
  which means about 95 threads busy at 100 req/s, out of 200. Virtual threads are off.
  There is no timeout or circuit breaker on the provider. A slow real provider would
  exhaust the threads before the connection pool.
- **CPU bound by BCrypt** before the database is the limit (D-10).
- **Single instance by construction.** The OTP store and the anti-replay window both live
  in the JVM heap.

### 3.6 Architecture

- **Step 2 is phase A only.** Eight classes still cross between `auth` and `payment`. This
  list is an equality asserted by `ModuleBoundariesTest`. One deployable, one classpath.
- **Documentation drift persists.** `ROADMAP.md` still reports "eleven classes" crossing
  and places `PhoneNumber` in `auth`. The truth is eight classes, and `PhoneNumber` is in
  the kernel. ADR-004 still dates its gap fixes to "Step 3".

### 3.7 Observability

**L-15 · Production would be blind.** Metrics are exported only in the `perf` profile, to
InfluxDB 1.8. There are no business metrics, no alerting and no tracing; logs carry the
correlation id. The Grafana dashboard showed nothing for the application side until
2026-10-15: its queries used Prometheus-style names (`_bytes`, `_seconds`) and read a
`value` field that timers do not write.

**None of L-1, L-2, L-3 or L-7 would be noticed today.**

---

## 4. What v2 says that no longer holds

| v2 says | Today |
|---|---|
| 547 tests, 54 classes | 579 tests, 58 classes |
| Eleven-state machine | Twelve states; `UNKNOWN` added but unreachable (L-12) |
| Eleven classes cross the modules, including `MobileMoneyProviderAdapter` and `ProviderPort` | Eight: `AuthUseCaseConfiguration`, `CustomerPinVerifier`, `LoginService`, `RegisterService` → payment; `CashInService`, `CashOutService`, `TransferService`, `UseCaseConfiguration` → auth |
| Post-ADR-004 baseline not measured | Measured locally at 25 req/s (ADR-005 amendment); 100 req/s not run |
| `TransactionQueryPort` | `ReadTransactionRepository` / `ReadAccountRepository`, implemented by `JdbcRead*QueryAdapter` |
| No database-level enforcement | Still true for the ledger invariants; enumerated columns now carry 11 `CHECK` constraints |
| `AuthorizationRecord` makes G-1 to G-4 auditable | It does not cover G-2/G-3: it rolls back with TX-2 (L-2) |

---

## 5. What comes next

Ordered by risk removed per unit of work, not by roadmap number.

| Order | Work | Closes | Roadmap |
|---|---|---|---|
| 1 | **Reserve funds in TX-1** (a hold on the wallet, released or consumed in TX-2) + a concurrent-debit e2e test | L-1 | 4 |
| 2 | **Write a durable trace before the provider call** (intent row in TX-1, or `PENDING_RECOVERY` state) so the outcome survives a failed TX-2 | L-2, L-3 | 4 |
| 3 | **Stuck-transaction reaper and gauge**: `INITIALIZED` / `AUTHORIZED` rows older than N minutes | L-3, L-15 | 4 |
| 4 | **Server-side idempotency key**, bound to the user, that returns the original result | L-4 | 4 |
| 5 | **Abuse controls**: an attempt counter on OTP challenges, rate limits on `/auth/**` and on PIN checks, a `typ` claim on tokens | L-8, L-9, L-10 | 4 |
| 6 | **Database roles** (KC-05) and an append-only trigger on `ledger_entries` | L-11, L-6 | — |
| 7 | **Always-on business metrics** (commands, provider calls, stuck transactions, balance divergence) | L-15, L-7 | 10, pulled forward |
| 8 | **100 req/s run on production-like infrastructure** | §3.5 | 3 (gate) |
| 9 | Step 2 phase B: `OpenWalletUseCase`, wallet lookup by phone, crossing classes → 0 | §3.6 | 2 |

The small fixes need no step: a handler for `PaymentProviderException` (L-13), removing or
wiring `UNKNOWN` (L-12), and scale enforcement in `Amount` (L-14).

---

## 6. Closing

v2 ended on *"an invariant is only as good as the thing that would notice if it broke."*
v3 applies that sentence to money. Inside a transaction, the double-entry invariant holds.
At the boundary the system cannot control — the provider call — it has no guard, and
nothing would notice if it broke.

The architecture is ready for the next steps. The money flow and the observability are not
yet.

---

## 7. Method and verification

**Sources**: the code and tests at `d28a6be`, the last full test run (2026-10-15), ADR-001
to ADR-007, `ROADMAP.md`, `perf/PERF.md`, `mobile/docs/09-api-evolution.md`.

**Three verification passes:**

1. **Facts**: every number, file name and line reference was re-read in the source: counts
   of files and tests, state classes, migrations, routes, handlers, `.env` users (compared,
   never printed).
2. **Claims**: each limit was traced through its code path. L-1 and L-2 were followed from
   the REST action through TX-1, the provider and TX-2. L-8 and L-10 were followed through
   the adapter.
3. **Consistency**: every ID in §5 maps to a limit in §3, every decision in §2 to an ADR or
   to code, and every v2 correction in §4 to the source.

**Not verified by execution**: L-1, L-2 and L-10 come from reading the code; none was
reproduced by a test or a request. The load figures are those recorded in ADR-005, not
re-run.

---

## 8. Status at 2026-10-17

**Basis**: commit `6ead50e` on `feature/KC-xx-l10-token-hardening`. Sections 0 to 7 stay
as written. This section records what has changed since the review and what the review got
wrong. Each change also has a dated note under its limit.

### 8.1 The limits

| ID | Status | Note |
|---|---|---|
| L-1 | **Closed** 2026-10-17 | Funds holds, ADR-008. Reproduced first, then fixed |
| L-2 | **Accepted** | Known gap, reproduced 2026-10-17 (`ProviderSuccessLostE2ETest`, disabled). Caught after the fact by reconciliation, roadmap Step 6 (`MISSING_INTERNAL`) |
| L-3 | **Accepted** | Known gap. Recovery comes with the cash-out saga and its compensations (event-driven, roadmap Step 5), reconciliation (Step 6) as safety net |
| L-4 | **Accepted** | Known bug, accepted until roadmap Step 4, where idempotency is planned |
| L-5 | **Withdrawn** | Settlement is internal; for the customer, a capture completes the operation |
| L-6 | **Closed** 2026-10-17 | Append-only enforced by the database for `ledger_entries` and `trx_state_historics`; double entry stays in the domain |
| L-7 | **Deferred** | Scheduled detection, off the payment path, with the Step 6 snapshots |
| L-8 | **To do** | Planned, roadmap Step 4 (brute-force protection) |
| L-9 | **To do** | Planned, roadmap Step 4 (strict rate limiting), carried over from Step 3 |
| L-10 | **Closed** 2026-10-16 | Reproduced first, then fixed on this branch. See the note under L-10 |
| L-11 | **Closed** 2026-10-17 | `kora_migration` owns the schema, the application connects as `kora_app`, DML only |
| L-12 | **Closed** 2026-10-17 | A provider timeout leaves the transaction `UNKNOWN`, for reconciliation to resolve |
| L-13 | Partly closed 2026-10-17 | Provider refusal mapped to 502. Still open: `ReverseResult.success` is ignored |
| L-14 | **Withdrawn** | Not a limit |
| L-15 | Open | |

### 8.2 Found since the review

- **N-1 · Reversing a posted transaction calls no provider (to confirm).** For a `CAPTURED`
  to `COMPLETED` transaction, `ReversePaymentService` writes the compensating ledger
  entries and nothing else. Whether a refund must also go back through the provider is a
  product decision that has not been made.
- **N-3 · `refresh_tokens` is never purged.** Every login and every refresh adds a row,
  kept 7 days past its use. A consequence of closing L-10.
- **N-4 · An unhandled server error reaches the client as 401.** Found while reproducing
  L-2: a cash-out whose TX-2 failed on a database error answered **401**, not 500. The
  exception has no `@ExceptionHandler`, so Tomcat dispatches to `/error`; `/error` is not
  permitted in `SecurityConfig` and falls under `anyRequest().authenticated()`, and that
  dispatch carries no authentication. The logs fit this reading; it is not yet proven by a
  dedicated test. Consequence: a server fault looks like an expired session, and the mobile
  client refreshes its token or logs the user out.
- **H-1 · Dead code — removed 2026-10-17.** `AccountRepository.findFloatByProviderIdForUpdate`
  and its Spring Data query, `SpringDataTransactionRepository.findByFromAccountIdOrToAccountId`,
  and the unused `JpaSpecificationExecutor` on that repository. A scan of `src/main` found
  nothing else that nothing calls.
- **Accepted with L-10.** An access token stays valid up to 15 minutes after logout. Two
  concurrent refreshes with the same token count as a replay and end the session; the
  mobile client sends one refresh at a time.

The `/test/**` endpoints (§3.3, "Lower") and the documentation drift (§3.6) are
unchanged.

### 8.3 What else changed

- **Tests**: 62 classes and 621 tests in the last full run (2026-10-16), against 58 and 579
  in §1. L-10 is no longer "not verified by execution" (§7): both directions were
  reproduced by an e2e test before being fixed.
- **Token protocol**: confined to the JWT adapter. The domain and the use cases see opaque
  tokens only, so an OAuth2 server could replace the adapter without touching them.
- **Transactions**: the first `@Transactional` in `src/main`, `REQUIRES_NEW`, on
  `RefreshTokenStore.revokeFamilyOfReplayed`. A replay's revocation has to survive the 401
  that rolls the caller back.
- **Code that only tests used**: three members removed from `payment`
  (`HistoryCriteria.everything`, `HistoryCriteria.continuingAfter`,
  `TrxHistoricStatesRepository.findByTransactionId`). None found in `auth` or `shared`.

---

## 9. Status at 2026-11-23

**Basis**: commit `42090b1` on `develop`, after pull requests #19 to #23. Full build that day:
855 tests, 0 failures, 2 skipped (`ProviderSuccessLostE2ETest`, disabled on purpose for L-2).
Sections 0 to 8 stay as written. This section records the state of every limit, an
end-to-end review of the operator reversal, the place of the OTP mail in the auth
transactions, the error contract, tracing and correlation, and what changed since §8. Every claim below
was re-read in the code at that commit; what was established by reading only, and not by a
test, says so.

### 9.1 The limits

| ID | Status | Evidence at `42090b1` |
|---|---|---|
| L-1 | **Closed** 2026-10-17 | Funds holds (ADR-008); `ConcurrentDebitE2ETest` green |
| L-2 | **Accepted** | `ProviderSuccessLostE2ETest` still `@Disabled`; caught by reconciliation, Step 6 |
| L-3 | **Accepted** | No reaper, no detection query; recovery with the cash-out saga, Step 5 |
| L-4 | **Accepted** | No idempotency store, Step 4. The anti-replay window is still keyed on a client-supplied id and still records it *before* the command runs (§9.2, R-4) |
| L-5 | **Withdrawn** | — |
| L-6 | **Closed** 2026-10-17 | `AppendOnlyLedgerDbTest` green; double entry stays a domain rule |
| L-7 | **Deferred** | Nothing compares `balance_amount` with the ledger sum; Step 6 |
| L-8 | **Open** | `MailedOtpChallenge.consume` still counts no attempt; a wrong code leaves the challenge alive. Step 4 |
| L-9 | **Open** | No rate limiting anywhere: no dependency, no filter. Step 4 |
| L-10 | **Closed** 2026-10-16 | One key per token use, `typ` claim, revocable refresh-token families |
| L-11 | **Closed** 2026-10-17 | Every test connects as `kora_app`; `ApplicationDatabaseRoleTest` green |
| L-12 | **Closed** 2026-10-17 | A provider timeout leaves the transaction `UNKNOWN`. Since 2026-11-21 the expiry job leaves it alone too (§9.3, E-2) |
| L-13 | **Open** | `ReverseResult` ignored, and its reference dropped (§9.2, R-3) |
| L-14 | **Withdrawn** | — |
| L-15 | **Open** | The InfluxDB exporter is off in the base configuration and on in `perf` only; no business metric, no alert, no trace |
| N-1 | **Open — decision pending** | A reversal of a posted payment still calls no provider (§9.2, R-7) |
| N-3 | **Open** | `refresh_tokens` is never purged: the only scheduled job in `src/main` is the authorization expiry |
| N-4 | **Open**, by reading | `/error` is still not permitted in `SecurityConfig`. Any exception without a handler — an `IllegalStateException` among them — still reaches the client as 401. Not yet pinned by a test |
| N-5 | **Open**, by reading — new | The OTP mail is sent synchronously, inside the transaction of registration and login, with no SMTP timeout (§9.3) |
| N-6 | **Closed** 2026-11-24 | Every error, security included, is a problem detail (§9.4) |
| N-7 | **Closed** 2026-11-24 | Every problem carries a `type` and a stable `code`, catalogued in `docs/problems.md` (§9.4) |
| N-8 | **Closed** 2026-11-24 for the correlation id; tracing open | Every command answers with its correlation id, in a header and in its problem; reads carry none by design, and nothing is traced yet (§9.4, §9.5) |
| N-9 | **Closed** 2026-11-24 for the correlation id | Every line logged during a request or a command names its correlation id, observed in a captured log (§9.5) |

Unchanged since §3.5: no measurement above 25 req/s; one Tomcat thread blocked per provider
call, virtual threads off, no timeout and no circuit breaker on the provider; BCrypt the first
CPU bottleneck; the OTP store and the anti-replay window in the JVM heap, so one instance only.
The `/test/**` endpoints are still compiled into every build, active under `perf` only.

### 9.2 The operator reversal, end to end

`POST /admin/payments/{txId}/reverse`, `ROLE_ADMIN` → `ReverseAction` → `PaymentRequestMapper`
→ `ReversePaymentCommand` → the bus (correlation id, validation, anti-replay) →
`ReversePaymentService`, which classifies the transaction in one transaction, then either
releases an authorization at the provider (`AUTHORIZED`) or compensates in the ledger
(`CAPTURED` to `COMPLETED`). Any other state is refused with 422.

**What holds.** Every invariant of `CLAUDE.md` §6 that the reversal touches: the provider
call sits between two transactions (`ReversePaymentService.java:76`); retry wraps the
boundary; the compensation is a mirrored pair written with the balance change, double entry
verified; the account debited is locked and its holds respected; a release resolves the hold,
a timeout keeps it `ACTIVE` and moves the transaction to `UNKNOWN`; transitions go through
named methods and a rollback happens by throwing.

**What does not.**

- **R-1 · High — the actor is declared by the client.** `actorId` and `actorRole` come from
  the request body (`ReverseRequest.java:7`), not from the authenticated token. Any admin can
  record a reversal in another's name, and the history row stores whatever `actorId` was sent.
  `actorRole` is carried to the command and recorded nowhere.
- **R-2 · High — no HTTP test.** No e2e test and no contract test call the endpoint; only the
  service is unit-tested. It is outside `api-contract/payments.openapi.json`, and `ReverseApi`
  declares 200, 401 and 403, while the endpoint can also answer 400, 404, 409, 422, 502 and 503.
- **R-3 · L-13 — the provider's answer is ignored.** A `ReverseResult` with `success = false`
  still marks the transaction `REVERSED`, cancels the authorization record and releases the
  hold, while the provider keeps the funds. Its `reversalReference` is dropped, and the history
  row of the release records `providerRef = null` (line 162).
- **R-4 · Medium — the correlation id comes from the body.** The `X-Correlation-Id` header the
  endpoint declares is ignored — the known gap of `CLAUDE.md` §9. Across every command, the
  anti-replay middleware records the id before the command runs: a reversal refused by the
  provider (502) cannot be retried with the same id for five minutes; it gets 409.

  > **Closed — 2026-11-24.** `ReverseAction` reads the correlation id from the header, like every other command, and the field left `ReverseRequest`. The anti-replay window still records an id before the command runs; that goes with the idempotency key of Step 4.
- **R-5 · Medium — mistyped errors.** An unknown transaction raises
  `AccountNotFoundException("Transaction not found")`, a 404 that names an account. An
  `AUTHORIZED` transaction without an active authorization raises `IllegalStateException`,
  which has no handler: 500, then 401 through N-4.
- **R-6 · Finding — the provider branch is unreachable today.** No flow commits a transaction
  in `AUTHORIZED`: in TX-2, `authorize()` is always followed by `capture()`, `failCapture()` or
  `markUnknown()`. Only tests build that state. Half of the reversal therefore serves an
  asynchronous capture that does not exist yet. A payment left `UNKNOWN` cannot be reversed,
  consistently with L-12: reconciliation decides.
- **R-7 · Decision — N-1.** Reversing a posted cash-out credits the wallet back while the
  provider has already paid out: unless the money is recovered outside the system, Kora carries
  the loss. Reversing a posted cash-in debits the wallet and refunds nothing to the customer's
  mobile-money account. Whether a reversal goes back through the provider needs an ADR.
- **R-8 · Low — two operators, one authorized payment.** Nothing serialises two reversals of
  the same `AUTHORIZED` payment before the provider call, so both would call `provider.reverse`.
  Unreachable while R-6 holds.

R-1 to R-6 and R-8 come from reading the code at `42090b1`; none was reproduced by an HTTP
request, because no test drives the endpoint over HTTP (R-2).

### 9.3 The OTP mail, inside the transaction

**N-5 · The one-time code is mailed synchronously, inside the use case's transaction.**
`RegisterService.execute` and `LoginService.execute` call `otpChallenge.issue` from within
`boundary.execute`: the SMTP exchange happens while the transaction is open and holds a
database connection. ADR-004 removed exactly this from the payment path — a network call
inside a transaction — but the invariant of `CLAUDE.md` §6 names the provider only, and the
mail was left inside.

- **No bound on the wait.** `MailedOtpChallenge` tries up to three times, sleeping 100 then
  200 ms between attempts, and no profile sets an SMTP connection or read timeout, so JavaMail
  waits indefinitely by default. A mail server that accepts the connection and stops
  answering holds a database connection and a request thread for as long as it hangs — the
  pool exhaustion of ADR-004, reached through `/auth/register` and `/auth/login`.
- **The code and the transaction disagree.** The code is put in the expiring store before it
  is mailed, and that store is not transactional. A mail delivered before a commit that then
  fails hands the customer a code for a registration that was rolled back. Symmetrically,
  `VerifyOtpService` consumes the code inside its transaction: if issuing the tokens fails
  afterwards, the code is gone and the rollback does not bring it back.
- **Delivery is not guaranteed.** A delivery failure rolls the registration back and answers
  503, so no account is left without its code; but nothing retries once the request is over,
  and a crash between the send and the commit loses the outcome. This mail is critical: without
  it, a customer can neither finish registering nor log in.

**Direction.** The mail leaves the transaction: the use case commits the challenge, and the
code is delivered asynchronously after the commit, through a durable outbox with bounded
retries, so a mail the customer depends on is either delivered or visibly failed — Step 5,
with the transactional outbox. Until then, SMTP connection and read timeouts bound the wait.

Established by reading `RegisterService`, `LoginService`, `VerifyOtpService`,
`MailedOtpChallenge` and every `application*.yaml` at `42090b1`; not reproduced by a test.

### 9.4 The error contract

The API follows the convention of the reference APIs, and it holds: a success returns the
resource itself, with no envelope — `TransactionResponse`, or a page `{items, nextCursor}` for
the history — and the HTTP status tells success from failure. Errors are RFC 9457 problem
details, `application/problem+json`, declared in the OpenAPI document and pinned by the
contract tests. An envelope shared by success and error was considered and is not the
direction: it repeats the status, weakens the OpenAPI types into a `oneOf`, and blinds the
HTTP tooling that reads the status. Three gaps remain against the standard Kora aims at.

- **N-6 · A 401 or a 403 is not a problem detail.** The `authenticationEntryPoint` and the
  `accessDeniedHandler` of `SecurityConfig` write `{"status":401,"error":"Unauthorized"}` and
  `{"status":403,"error":"Forbidden"}` by hand, as `application/json`
  (`contracts/payment/cash-in/401-no-token.json`, `403-admin-token.json`). Every other error is
  `application/problem+json`, so a client parses two formats. Same piece of work as N-4.

  > **Closed — 2026-11-24.** The security entry point and access-denied handler hand their failure to the exception handlers, so a 401 is `UNAUTHENTICATED` and a 403 `FORBIDDEN`, both `application/problem+json`. The mobile client tells the two 401s apart by `code` in the same delivery.
- **N-7 · No error carries a machine-readable code.** `ProblemDetails.of` sets a status and a
  free-text `detail`, and no `type`: a problem reads `"title": "Bad Request"` with
  `"detail": "Unknown payment method: BITCOIN"`. Insufficient funds, a blocked account and an
  invalid transition are three 422s that a client can tell apart only by parsing their message.
  RFC 9457 answers with a documented `type` URI per problem, and an extension such as
  `code: "INSUFFICIENT_FUNDS"` for clients that switch on a string.

  > **Closed — 2026-11-24.** `BusinessException` is abstract and every refusal declares its `code()`; the handlers map a status and ask `ex.code()`. `type` points to the code's entry in `docs/problems.md`, and a test in each module fails when a code is missing from it.
- **N-8 · No response carries the correlation id.** `X-Correlation-Id` is read from the
  request and never written back, in a header or in a problem. When the client omits it, the
  server generates one the client never learns. Support has nothing to ask a customer for,
  where Stripe returns a `request_id`. Related to R-4.

  > **Closed — 2026-11-24.** `CorrelationIdFilter` keeps the `X-Correlation-Id` a request sent, or gives a command one, returns it in the response header, and every problem carries it as `correlationId` — a 401 or 403 from the security chain included. Pinned by `CorrelationIdContractTest`.

Each fix changes the body or the headers of existing responses, so each goes through a
reviewed diff of the contract files and of `api-contract/payments.openapi.json`. The mobile
client classifies errors by status today; a stable code lets it be finer without parsing text.

### 9.5 Tracing and correlation

Three identifiers answer three questions, and Kora has only one of them, doing two jobs.

| | Trace id | Correlation id | Idempotency key |
|---|---|---|---|
| Answers | what happened during *this request*: which calls, how long each, where it failed | what happened to *this operation*, end to end | was *this intent* already handled? |
| Lives | one request and its downstream calls | the whole operation: retries, provider calls, events, reconciliation | the de-duplication window |
| Created by | the server at the edge, or continued from an incoming `traceparent` | the client or the server, when the operation starts | the client |
| Standard | W3C Trace Context, OpenTelemetry | none — `X-Correlation-Id` by convention | `Idempotency-Key` |
| Stored | no — logs and traces | yes — the transaction's history | yes — with the original result |

A client that retries a cash-out sends two requests, so two trace ids, sharing one correlation
id and one idempotency key. Kora has the correlation id only: commands carry it, the history
stores it, and the anti-replay middleware uses it as a de-duplication key (L-4, R-4).

- **N-8, extended · Reads carry no identifier, and nothing is traced.** `CorrelationIdMiddleware`
  puts the correlation id in the MDC around a command's dispatch, then removes it. A query —
  balance, history — has none by design (`Query` carries no correlation id), and neither has
  anything outside the bus: the security filter, the exception handlers that run after the
  middleware has removed the key. There is no tracing library: neither Micrometer Tracing nor
  OpenTelemetry. A read request therefore leaves no identifier to follow.

  > **Closed — 2026-11-24.** For the correlation id: the filter publishes it in the MDC for the whole request, the security filter and the exception handlers included; the command bus publishes it only for a command born elsewhere, such as the expiry job. Reads still carry none, and the trace id waits for the tracing work.
- **N-9 · The correlation id is printed in no log line.** No `logging.pattern` is configured
  and there is no `logback` file. Spring Boot's default pattern prints the `traceId` and
  `spanId` of a tracer, not an arbitrary MDC key, so the `correlationId` placed in the MDC
  never reaches the output. `CommandBusChainTest` asserts the value is in the MDC, not that it
  is logged. The README and the engineering standards stated that it is "propagated into every
  log line"; they were corrected on 2026-11-23. Established by reading the configuration; not
  yet observed in a captured log.

  > **Closed — 2026-11-24.** `logging.pattern.correlation` prints `[%X{correlationId:-}]` on every line. `CorrelationIdLoggingE2ETest` observes it in a captured log: the warning of the JWT filter, logged before the command bus, names the request's correlation id.

**Direction.** Every request, read or write, gets a trace id: Micrometer Tracing with the
OpenTelemetry bridge creates one per HTTP request, continues an incoming `traceparent`, puts
`traceId` and `spanId` in the MDC, which the default pattern already prints, and the id is
returned in a response header and in every problem detail. No exporter is needed for that;
OTLP export to a tracing backend comes with Step 10. The correlation id keeps its business
role — read from the header, generated when absent, returned, and added to the log pattern —
and stops being a de-duplication key when the idempotency key arrives in Step 4. The hexagon
is untouched: tracing lives in adapters and configuration, and the allow-list of
`application/` and `ports/` stays empty. This is also the first concrete step on L-15.

### 9.6 Found and fixed since §8

- **E-1 · An expired cash-out authorization kept its funds hold `ACTIVE`** — fixed 2026-11-21
  (`2a24ae4`). The payment ended `AUTHORIZATION_FAILED` while the money stayed blocked on the
  wallet. The expiry job now releases the hold of an `AUTHORIZED` payment it fails. Found
  while writing the unit tests behind the coverage gate (§9.7), reproduced red first.
- **E-2 · One `UNKNOWN` payment stopped every authorization from expiring** — fixed 2026-11-21
  (`2a24ae4`). After a capture timeout, the expiry job called `failAuthorization()` on an
  `UNKNOWN` transaction; the refusal rolled back the whole run, every minute. The job now acts
  on `AUTHORIZED` payments only; any other state keeps its transaction, hold and authorization
  for reconciliation.
- **A cash-out reaches TX-2 only with its hold** (`390ef05`). The fallback that posted without a
  hold could follow only an out-of-band change to `funds_holds`, and hid it; TX-2 now fails.

### 9.7 What else changed

- **Tests**: 78 classes and 855 tests — unit 577, integration 119, contract 60, e2e 79 —
  against 62 and 621 in §8.3.
- **A fourth level, contract**, on cash-in, cash-out, transfer, balance and history, with the
  published `api-contract/payments.openapi.json` held to what the contracts observe (#20).
- **A coverage gate** on every build (#21): unit coverage of the hexagon at 99.9 % lines and
  99.3 % branches, payment at 100 / 100, all suites at 97.1 / 94.4. Floors: 100 % for payment
  and 90 % for the rest of the hexagon in the unit suite; a ratchet for all suites.
- **MapStruct at the two edges**, request to command and snapshot to entity (#19, #20).
- **The application layer by intent**, `command/<intent>/` and `query/<intent>/`.
- **One provider per payment method** (#22): `PaymentProviderRouter` sends every step to
  `OrangeMoneyProvider`, `MomoProvider` or `CardProvider`, all three still composing the
  latency-simulating stub of D-11. `capture` and `reverse` now name the payment method.
- **Schema**: 8 Flyway migrations, against 4 in §1.
- **Documentation** to open-source standards (#23): `SECURITY.md`, GitHub templates,
  `docs/development/`, `docs/engineering-standards.md` (formerly `HELP.md`), an ADR index.

### 9.8 Documentation drift still present

- `ROADMAP.md:137-138` still counts eleven classes crossing the modules and places
  `PhoneNumber` in `auth`; the truth is eight, and `PhoneNumber` is in the kernel.
- ADR-004 still dates its gap fixes to "Step 3".
- ADR-006 points to `CONTRIBUTING.md` §3.2.2, whose content now lives in
  `docs/development/local-setup.md` §4 and §5. ADRs are records and stay as written.

### 9.9 What comes next

Ordered by risk removed per unit of work. The operator reversal comes first: it is the only
operator action, and its audit trail can be forged.

| Order | Work | Closes |
|---|---|---|
| 1 | **Pin the reversal over HTTP**: an e2e and a contract test, every response declared in the OpenAPI document | R-2 |
| 2 | **The actor comes from the token**; `actorId` and `actorRole` leave the request | R-1 |
| 3 | **Typed errors**: a `TransactionNotFoundException`, a domain exception for a missing authorization, and `/error` permitted | R-5, N-4 |
| 4 | **Honour the provider's answer**: a refusal leaves the transaction `AUTHORIZED` and answers 502; the reversal reference is recorded | R-3, L-13 |
| 5 | **The correlation id comes from the header** — done 2026-11-24 | R-4 |
| 6 | **Decide, in an ADR, whether a posted reversal goes back through the provider** | R-7, N-1 |
| 7 | **Abuse controls**: an attempt counter on OTP challenges, rate limits on `/auth/**` and on PIN checks | L-8, L-9 |
| 8 | **Always-on business metrics** | L-15 |
| 9 | **Take the OTP mail out of the transaction**: SMTP timeouts now; asynchronous delivery after commit through the outbox, with bounded retries, in Step 5 | N-5 |
| 10 | **One error contract**: a problem detail for 401 and 403, a stable `type` and `code` per problem, the correlation id returned in a header and in every problem — done 2026-11-24 | N-6, N-7, N-8 |
| 11 | **A trace id on every request**, read or write: Micrometer Tracing with the OpenTelemetry bridge, the id in the logs, in a response header and in every problem; the correlation id added to the log pattern | N-8, N-9, first step of L-15 |
| 12 | Fix the drift of §9.8 in `ROADMAP.md` | §9.8 |

---

*v1 — Step 1 snapshot · v2 — Steps 3 and 2A · v3 — review at `d28a6be`, status updated 2026-10-17 and 2026-11-23.*
*[github.com/Geekers-Joel237](https://github.com/Geekers-Joel237) · [geekersjoel237.substack.com](https://geekersjoel237.substack.com)*
