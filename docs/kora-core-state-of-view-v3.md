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

> **Confirmed — 2026-10-17.** To be done. Reproduced by `ProviderSuccessLostE2ETest`
> (disabled until the fix): with TX-2 forced to fail after the provider succeeded, a
> cash-in ends `INITIALIZED` with its wallet not credited (503), and a cash-out ends
> `INITIALIZED` with its hold still `ACTIVE` (401, see N-4). In both cases no provider
> reference is stored anywhere. The cash-out hold of L-1 keeps the paid-out funds from
> being spent twice, but does not say whether the provider paid.

**L-3 · A crash between TX-1 and TX-2 leaves the transaction `INITIALIZED` forever** (G-1).
There is no reaper and no detection query. ADR-004 planned both for Step 3; they did not
ship.

> **Confirmed — 2026-10-17.** To be done.

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

> **Confirmed — 2026-10-17.** To be done.

**L-7 · Nothing checks the balance cache against the ledger** (D-1). On divergence, the
ledger wins (ADR-001), but no process computes the divergence. The float account keeps
`balance_amount = 0` by design, so its position is visible only through a query on
`ledger_entries`.

> **Confirmed — 2026-10-17.** To be done.

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

> **Confirmed — 2026-10-17.** To be done.

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

**L-13 · A provider failure during a reversal returns an unhandled 500.**
`ReversePaymentService` calls `provider.reverse` outside any `try`.
`PaymentProviderException` has no `@ExceptionHandler`, which breaks review rule 15. The
mobile team reported this on 2026-08-06; it is still open.

> **Still open — 2026-10-17.** Also found: the result of `provider.reverse` is ignored. A
> `ReverseResult` with `success = false` still marks the transaction `REVERSED` and cancels
> the authorization record, while the hold stays in place at the provider. A fix was drafted
> (timeout retried 3 times; refusal 502, unknown outcome 504; the transaction stays
> `AUTHORIZED`) and set aside, not merged.

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
| L-2 | **To do** | Reproduced 2026-10-17 (`ProviderSuccessLostE2ETest`, disabled). The trace written before the provider call will need a state for an unknown outcome (L-12) |
| L-3 | **To do** | Confirmed |
| L-4 | **Accepted** | Known bug, accepted until roadmap Step 4, where idempotency is planned |
| L-5 | **Withdrawn** | Settlement is internal; for the customer, a capture completes the operation |
| L-6 | **To do** | Confirmed |
| L-7 | **To do** | Confirmed |
| L-8 | **To do** | Planned, roadmap Step 4 (brute-force protection) |
| L-9 | **To do** | Planned, roadmap Step 4 (strict rate limiting), carried over from Step 3 |
| L-10 | **Closed** 2026-10-16 | Reproduced first, then fixed on this branch. See the note under L-10 |
| L-11 | **To do** | Confirmed |
| L-12 | **Requalified** | Not dead: reserved for provider timeouts, and not wired yet |
| L-13 | Open | A fix exists but is not merged. Also: `ReverseResult.success` is ignored |
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

*v1 — Step 1 snapshot · v2 — Steps 3 and 2A · v3 — review at `d28a6be`, status updated 2026-10-17.*
*[github.com/Geekers-Joel237](https://github.com/Geekers-Joel237) · [geekersjoel237.substack.com](https://geekersjoel237.substack.com)*
