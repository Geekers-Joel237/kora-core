# Building Kora Core — Paying the Architectural Debt

## An Engineering State of View, v2

**Author**: Ivan Joël Tchatchoua Bayon — [@Geekers-Joel237](https://github.com/Geekers-Joel237)
**Date**: September 2026
**Version**: Étape 3 delivered · Étape 2 begun
**Tags**: `java-21` `spring-boot-4` `hexagonal-architecture` `modular-monolith` `cqrs` `command-bus` `architecture-tests` `test-doubles` `tdd`

---

> *v1 of this document is a snapshot: what Kora Core was in May 2026, at the end of Étape 1. It has not been edited, and it should not be — a retrospective that gets quietly rewritten every time the code moves stops being evidence of anything. This is the next chapter. Where v1 no longer describes the repository, this document says so explicitly rather than reaching back.*

---

## Preface — What changed, and what this document is for

v1 ended with a list titled *What Is Not Yet Done*. Two entries on it were architectural
debts rather than missing features:

> **`TransactionBoundary` port**: the Spring infrastructure violation in the application
> layer is documented and bounded. Clean resolution in Step 2.

That debt was recorded by ADR-004 in May and left standing for four months. This document
is the account of paying it — and of discovering, on the way, that paying it properly
meant changing far more than one class.

The arc: a transaction boundary, explicit interactors, a command bus, three modules, a
separated read side, and nineteen architecture rules that make all of it fail the build
if it decays. Along the way, four bugs that had been invisible because the test doubles
were more forgiving than production, and three architecture rules that had been passing
while guarding nothing.

### What v1 says that is no longer true

Listed here rather than corrected there, because v1 is dated and accurate for its date.

| v1 says | Today |
|---|---|
| 381 tests across 3 layers | 547 tests, 54 classes, across the same three levels — now with the level in the directory name |
| `V1__initial_schema.sql` | `V202605270702__initial_schema.sql`. Migrations are versioned by timestamp so two branches cannot both claim the next integer; a test enforces the convention |
| `InMemoryOtpStoreAdapter` is a sufficient single-instance implementation | The type is gone. OTP codes live in `ExpiringStore<OtpCode>`, a generic kernel port; the in-memory adapter is one `@Bean` line away from Redis |
| `MailPort.sendOtp(email, code, subject)` | `MailPort.send(Mail)`. See Chapter 7 — the old signature was hiding a bug |
| "Clean resolution in Step 2" for `TransactionBoundary` | Done. The application layer names no foreign type at all, asserted by an allow-list |
| ADR index, 001–005 | 001–007 |
| The gradient table, keyed on "Step" | `ROADMAP.md`'s "Étape" numbering is now authoritative and this document uses it. The two files meant different things by "Step 2" for months (ADR-007) |

---

## Chapter 1 — The debt that came due

ADR-004 fixed a real production failure: a class-level `@Transactional` spanning a
provider call held a HikariCP connection for ~1 400 ms, and at 25 req/s the pool
collapsed — p95 60 000 ms, 73.93 % errors. The fix split cash-in and cash-out into
TX-1 / provider I/O / TX-2, and it worked.

But it was implemented with Spring's `TransactionTemplate`, held directly by
`PaymentTransactionalExecutor`, in the application layer. So the layer that was supposed
to know nothing about infrastructure named the framework's transaction manager and
caught its persistence exceptions. ADR-004 wrote this down as debt and moved on, which
was the right call at the time and the wrong state to stay in.

### Why hexagonal before modular, against the roadmap

`ROADMAP.md` planned Étape 2 as the modular monolith and Étape 3 as the hexagonal
migration. Étape 3 was done first, and ADR-007 records why. Étape 2 was begun after it
and is **not** finished — Chapter 5 says what is missing.

A module boundary is a statement about which types may cross it. Drawing that boundary
while `application/service` still imported `org.springframework.orm` would have frozen
the framework dependency *into the module contract*. Unpicking it afterwards means
moving every file twice.

There is also an asymmetry in how checkable the two are. "Does `domain/` name Spring?" is
decidable from the source alone. "Should this type belong to payment or to auth?" needs
the layering to already be legible, because the honest answer is usually *"it belongs to
the port, and the port belongs to whoever declares it."*

The work was cut into ten steps, and executed in that order. Idempotency was explicitly
kept out and left at Étape 4, where the roadmap put it — the temptation to fold it into
the command bus, once a bus existed, was real and was refused.

---

## Chapter 2 — A boundary that is not a Unit of Work

```java
@FunctionalInterface
public interface TransactionBoundary {
    <T> T execute(Supplier<T> work);
}
```

One method. The adapter holds the `TransactionTemplate`, translates
`ObjectOptimisticLockingFailureException` and `PessimisticLockingFailureException` into
one `ConcurrentUpdateException`, and that is all it does.

Two things about it are written down in ADR-007 specifically because the interface
*looks* like the beginning of something larger.

### It is not a Unit of Work, and must not become one

Nothing is tracked, queued, or flushed. The work writes through repositories as it goes;
the boundary decides only whether those writes become permanent. A Unit of Work owns the
identity map and the write order — taking that over from Hibernate here would mean
re-implementing what the ORM already does, and giving the application layer a second
opinion about when a write happens.

"Improving" this into a Unit of Work later would be a silent, expensive change. Hence
the sentence in the ADR, in the port's javadoc, and here.

### Rollback happens by throwing — and that was measured, not assumed

There is no `setRollbackOnly`, no status object, and no way to ask for a rollback while
returning normally. A use case aborts by letting the aggregate throw.

This works because `TransactionTemplate` rolls back on any unchecked throwable. That is
a Spring default — exactly the kind of thing that stays true until it does not, and
nothing in the codebase asserted it. The existing test proved only that the exception
*propagated*, because it used a transaction manager whose `rollback()` was empty.

So the behaviour was measured with a manager that keeps score:

| From inside the supplier | commit | rollback |
|---|---|---|
| normal return | 1 | 0 |
| `BusinessException` | 0 | **1** |
| `Error` | 0 | **1** |

Green before the change, and green after — but now it is a contract rather than a
coincidence.

### Retry wraps the boundary; the boundary never wraps the retry

```java
retry.execute(() -> boundary.execute(() -> settle(...)))   // correct
boundary.execute(() -> retry.execute(() -> settle(...)))   // wrong
```

`ConcurrentUpdateException` surfaces when the *commit* fails, so by the time it is
visible the transaction is already dead. Retrying inside it replays work on a
transaction that can no longer commit.

Retry is also scoped to the phase that is safe to replay. In a saga that is TX-2, after
the provider has answered. Retrying TX-1 re-initiates; retrying across the provider call
charges twice.

`RetryExhaustedException` — formerly `TransientPaymentException`, a name that put a
module inside the kernel — no longer extends `BusinessException`. Running out of
attempts is not a refusal; the request was legal every time. It leaves as a 503.

---

## Chapter 3 — From one service to explicit interactors

`PaymentService` and `PaymentTransactionalExecutor` are gone.

What replaced them is one class per use case: `CashInService`, `CashOutService`,
`TransferService`, `ReversePaymentService`, `ExpireAuthorizationsService`. Each
implements exactly one driving port, which declares exactly one method.

The property that makes this worth the churn is not aesthetic. Every interactor is a
plain class with constructor injection and no annotations, so a test builds one in a
line:

```java
new CashInService(boundary, retry, pinVerifier, accounts, customers,
                  transactions, history, provider, ledgers, authorizations)
```

No container, no context, no classpath scan. The production wiring in
`UseCaseConfiguration` does the same thing by hand, which means the assembly is readable
in one file rather than inferred from annotations spread across thirty.

A rule was added and holds: **no interactor imports another interactor.** Two use cases
chained share one caller's transaction boundary without either knowing it, so the inner
one commits work the outer one is about to roll back. Composition is the configuration's
job.

---

## Chapter 4 — The bus is an adapter, not the door

A command bus is tempting to model as *the* entry point. It is not. It is one **driving
adapter** among several, and modelling it that way is what keeps the scheduler able to
call `ExpireAuthorizationsService` directly, and a test able to call any use case with no
bus at all.

```
HTTP Action ─┐
Scheduler   ─┼─→ CommandBus ─→ middlewares ─→ CommandRegistry ─→ CommandHandler
Test        ─┘         (or straight to the handler, which is the point)
```

Three middlewares, ordered explicitly: correlation id into the log context, command
validation, anti-replay over a TTL window. **No middleware opens a transaction** — the
boundary belongs to the use case, which is the only thing that knows how many phases it
has. A blanket transactional middleware would have put the provider call back inside a
transaction and undone ADR-004 in one line.

### The startup guard that fired for real

Registration is explicit. A command with no registered handler is a 500 on the one
endpoint that dispatches it — discovered in production, by a customer. So a verifier
compares the `Command` types found on the classpath against the registered ones and
**refuses to start** if any is unregistered.

It fired for real during the module split, exactly as designed:

```
No Command type found — the classpath scan is misconfigured
```

The scan was still pointed at the pre-module package. Note what failed: not "a command
is missing" but "the scan found nothing at all". A guard whose scan silently finds
nothing reports success and guards nothing — so the verifier asserts its own input is
non-empty. That lesson comes back in Chapter 8, three more times.

---

## Chapter 5 — Three packages, and a kernel that names neither

```
auth/      identity, credentials, the OTP challenge, tokens
payment/   accounts, ledger, transactions, provider, history
shared/    the kernel
```

Each with the same five layers: `domain`, `application`, `ports`, `adapters`, `config`.
There is no `infrastructure/` package and no `web/` package any more — `adapters/in` and
`adapters/out` name the *direction*, which is the thing that matters, rather than the
technology, which changes.

**These are packages, not yet modules, and the difference is the whole of Étape 2.** One
Gradle project, one `@SpringBootApplication`, one classpath: nothing at build time stops
an import, only a test reports it afterwards. And the two business packages still share
domain models — `payment` reaches into `auth.domain.model.Customer`, `auth` into
`payment.domain.model.Account`. A package whose aggregate the neighbour imports has a
directory, not a boundary.

What follows is therefore phase A: the boundary drawn, and made impossible to erode
silently. Phase B — separate applications, and no shared entity — is not started.

### The kernel rule

> **`shared` names no module.** Not almost none.

This was not true when the split started. One exception handler knew every module's
failures, and one perf-profile endpoint bootstrapped another module's aggregate — enough
to make `shared` the most coupled package in the repository while looking like the least.

The handler became three, one per module. The endpoint moved.

### The couplings that remain are written down, as an equality

```java
"auth -> payment": AuthUseCaseConfiguration, CustomerPinVerifier,
                   LoginService, RegisterService
"payment -> auth": CashInService, CashOutService, MobileMoneyProviderAdapter,
                   ProviderPort, TransactionHistoryService,
                   TransferService, UseCaseConfiguration
```

An **equality**, not a subset. A new coupling fails the build — and so does closing one
without updating the list. That second half is the point: a subset assertion only ever
grows, and a list that only grows stops describing anything. This one has to shrink
deliberately, when `OpenWalletUseCase` is published and a wallet can be looked up by
msisdn.

---

## Chapter 6 — The read side stops pretending

`TransactionRepository` used to carry four methods: `save`, `findById`, and two
`findByAccountId` overloads. Rendering one page of history therefore rebuilt twenty
`Transaction` aggregates — each with its ledger entries and its full state history — to
display six fields each.

CQRS level one: same database, two ports, two shapes.

| | Write | Read |
|---|---|---|
| Port | `TransactionRepository` | `TransactionQueryPort` |
| Adapter | JPA, aggregates | `NamedParameterJdbcTemplate`, rows |
| Transaction | yes, via the boundary | none — a single statement is atomic on its own |
| Bus | yes | no: nothing to correlate, no replay to refuse |

The query handlers are transaction scripts. They do not open a boundary, because
wrapping a `SELECT` in a transaction takes a read-write connection to answer a question.
`Query<R>` deliberately carries no correlation id — the mirror of `Command<R>` with the
one difference stated in its javadoc.

What stays in Java is what SQL should not decide: which *direction* a movement reads as
depends on which side the reader is on — one row, two answers, which is exactly why
direction cannot be a column — and a counterparty's phone number must be masked before
anyone sees it.

### The coverage moved; it did not evaporate

Deleting the read methods deleted the tests that covered them. Filtering, paging, date
ranges and the join folding used to be asserted over an in-memory map. They are now
asserted in `JdbcTransactionQueryAdapterTest` against a real Postgres — including one
test that writes through the real repository and reads back through the query port,
which is the only thing that would catch the two sides drifting apart.

And the in-memory query double deliberately does **no** filtering and no paging. A
double that reimplemented them would test a second implementation nobody ships. Which
brings us to the chapter this one was heading for.

---

## Chapter 7 — The doubles that lied

The most useful finding of this arc is not architectural. It is that **four bugs were
hidden by test doubles that were more forgiving, or simply different, from what runs in
production.**

### The OTP mail that contained no code

```java
public void sendOtp(String toEmail, String otpCode, String subject) {
    message.setSubject(subject);
    message.setText("Your one-time code is valid for 5 minutes.\n\n"
                  + "Do not share it with anyone.");   // otpCode never used
    ...
}
```

Every OTP mail went out announcing a code that was not in it. The parameter was
accepted and dropped on the floor.

Nothing caught it, and the reason is precise: `InMemoryMailPort` recorded the **argument
it was passed**, not the message that was composed. So the double reported a code the
recipient would never have seen, and every OTP test passed on a code that never left
the building.

This is the uncomfortable part. v1 celebrates the refactor that produced this signature:

> *"Another clean boundary fix... The subject is the caller's concern."*

It was the right instinct applied to the wrong half. `subject` moved out; `otpCode`
stayed in, and an adapter that receives a secret it has to render is an adapter with a
reason to be wrong. The fix goes all the way: `MailPort.send(Mail)`, where `Mail` is a
message already written, and `OtpMailTemplate` in the auth module is the one place that
knows what such a message says.

Now the double records the `Mail`, and the auth test digs the code out of the **body**
with a regex — the only place a recipient could find it. The bug is structurally
unreproducible.

### The store that checked a different clock

```java
// InMemoryOtpStore, in test sources
// Check expiry against the real system clock so that OTPs created with
// a past-fixed clock (in expiry tests) are seen as expired immediately.
if (otp.isExpired(Clock.systemUTC())) return Optional.empty();
```

A test double consulting a different clock from production, with a comment explaining
the workaround. It was there because the *store* could not own expiry: `OtpStore` was
typed on `Otp`, and the adapter asked the value whether it had died — so only
self-expiring auth types could live in it.

Making the store generic forced the question. `ExpiringStore<V>` takes the TTL at the
write, the value is opaque, and the store enforces the lifetime. Which is exactly
`SET key value EX ttl`, which is what makes Redis a swap instead of a rewrite.

The hand-written double then had no reason to exist: `InMemoryExpiringStore` is honest —
injected clock, opaque value — so it *is* the test object. `Otp` collapsed into
`OtpCode`; a code that knew when it died had nothing left once the store owned the
lifetime.

### Two more, briefly

`InMemoryTransactionRepository` returned the same mutable instance it had stored, so a
test could mutate the "persisted" aggregate through its own reference — something JPA
would never allow. It stores snapshots now.

`InMemoryOtpStore.get` hid expired entries, so a test *about* expiry could not use the
store to learn the code it was about to try. That is why the auth tests read the code
from the mail: not preference, necessity.

### The rule that came out of it

> A test double must fail the way the real thing fails. When it cannot, the design is
> telling you something about the port, not about the double.

Every one of these four was a port doing too much: a mail port that composed, a store
that inspected its values, a repository that served two masters.

---

## Chapter 8 — The rules that went green and guarded nothing

`HexagonalArchitectureTest` existed before this arc. During it, it silently stopped
guarding anything **three times**.

1. **After the web split.** Rules resolved `domain` and `web` at the repository root. The
   modules moved both. A forbidden import prefix that matches nothing passes.
2. **After the module move.** Same failure, same day, second time.
3. **The `AuthUseCase` rule.** It watched for one type name in a string. That type had
   been split into four an hour earlier. The rule kept passing on a string that could no
   longer appear anywhere in the repository.

None of the three produced a failure. All three produced a green build, which is worse
than a red one, because a red build gets fixed.

Two changes came out of it. Layers are now located **by path segment, not by directory**,
so `auth/domain` and `payment/domain` and anything added later are all found by the same
expression. And every locator is asserted non-empty by `every_layer_is_populated` — a
guard on the guards.

### The allow-list

ADR-004's debt was worded as *"application must stop naming Spring's transaction
manager"*. A grep for `TransactionTemplate` would have declared that paid while
`@Service`, `@Transactional` and a handful of persistence exceptions were still in the
layer.

So the rule is not a ban. It collects **every** import in `application/` and `ports/`
that is neither JDK nor ours, and compares the whole set:

```java
private static final Set<String> FRAMEWORK_ALLOW_LIST = Set.of();
```

It is empty. Three deliberate choices in that one line:

- **Foreign, not "Spring."** A validation annotation or a JSON binding binds the layer
  just as firmly, and each arrived here before as something nobody thought of as a
  framework.
- **Equality, not subset.** Removing the last use of an allowed type has to fail too.
- **A list, not a prohibition.** Adding an entry is permitted; it costs a line and the
  sentence that justifies it. The layer stays framework-free by decision rather than by
  accident, and loosening it is legible in the diff.

Nineteen architecture rules now: twelve on layers, two on module boundaries, five on the
test pyramid. Thirteen more guard configuration and migration naming.

---

## Chapter 9 — The pyramid, made structural

The test tree used to be organised by the shape of the production code. It is now
organised by **level**, because the level is what a reader needs first:

```
<module>/
├── unit/          domain · application · doubles      no context, no container
├── integration/   persistence · query                 Testcontainers, no HTTP
└── e2e/                                               real HTTP
```

Within a level, grouped by type and then by concern — `payment/unit/domain/` splits into
`account`, `ledger`, `transaction`, `authorization`.

A directory is a claim, and a claim nothing checks is worth nothing: a `@SpringBootTest`
quietly added under `unit/` makes the fast suite slow and nobody notices until the build
takes four minutes. So `TestPyramidTest` reads the tree — every test declares a level, a
module holds only the three, nothing under `unit/` starts a context, nothing under
`integration/` opens a port, and every module/level pair is populated.

Two things fell out of the move, both instructive.

`CommandRegistry.dispatch` is package-private, and the test reached it only because it
shared the package. Widening it would have traded a production boundary for a test's
convenience; the test goes through `RegisteredCommandBus` with no middlewares instead,
which is the path production uses anyway.

And `AbstractE2ETest` violates the kernel rule — legitimately. An end-to-end harness
registers a customer and opens their wallet, so it names both modules by construction.
The rule is scoped to `shared/unit` and `shared/integration`, and says why. A rule that
could only be satisfied by duplicating the harness into both modules would be a rule
about nothing.

---

## Chapter 10 — The documents described a repository that no longer existed

Five markdown files were reviewed against the code. All five asserted things the code
contradicted.

`CLAUDE.md` described `infrastructure/`, `web/`, and `PaymentTransactionalExecutor` —
none of which exist. `CONTRIBUTING.md` gave four `--tests` selectors pointing at deleted
packages, and claimed a JaCoCo coverage gate that is not in `build.gradle` at all.
`HELP.md` prescribed `@DataJpaTest`, removed in Spring Boot 4 and explicitly forbidden by
`CLAUDE.md`, and told the reader to use `Money`, `Order` and `IdempotencyKey`, three
names `CLAUDE.md` lists as deliberately absent.

The most serious was in `README.md`, and it was about money:

> - No direct balance updates
> - Balance reconstruction from ledger entries

Both false, and both contradicting ADR-001, which is explicit that `balance_amount` is a
denormalized cache written in the same transaction as the entries and *not* recomputed on
read. That is the first line an engineer evaluating this repository would check.

`README.md` also presented eight capabilities in one present-tense voice: two shipped,
six roadmap. That is not only inaccurate, it devalues the two that are real.

A defect in production code fell out of the same pass: `HistoryApi` documented its filter
as `"Filter by state: INITIATED, …"`. The state is `INITIALIZED`. A client copying the
Swagger doc would have filtered on a value matching no row and received an empty page
with no error.

### The rule this produced

ADR-007 fixes the numbering: `ROADMAP.md`'s "Étape" is authoritative, and `CLAUDE.md`
numbers nothing of its own. The two files had meant different things by "Step 2" for
months, so a gap marked *"Step 2 target"* in one pointed at an unrelated milestone in the
other.

---

## Epilogue — The current state

### What exists

- **547 tests, 54 classes**, over 226 production files. No mocking framework: every port
  is faked by a real in-memory implementation that can be asserted against.
- **Three packages** — `auth`, `payment`, `shared` — with a hexagonal interior. The
  kernel names no module. What the two business packages still owe each other is
  recorded as an equality. This is the first half of Étape 2, not the whole of it; see
  *What is not done*.
- **Zero foreign types** in `application/` and `ports/`. Not "no Spring" — nothing
  outside the JDK and this repository.
- **A command bus as a driving adapter**, with three middlewares and a startup guard that
  refuses to boot on an unregistered command.
- **A separated read side** on `NamedParameterJdbcTemplate`, with paging rules living in
  one `Pagination` type instead of three places.
- **Nineteen architecture rules** that fail the build, plus thirteen on configuration and
  migration hygiene.
- **Everything from v1 still holds**: the double-entry invariant, the eleven-state
  machine, optimistic locking, the micro-transaction split, Flyway as the single schema
  owner.

### What is not done

- **The modular monolith itself.** Étape 2 has its boundary drawn and nothing more.
  Two things are missing and they are the substance of it: **separate Spring Boot
  applications per module** — today one Gradle project and one `@SpringBootApplication`,
  so an import is reported by a test rather than refused by the build — and **modules
  that do not share entities**, where eleven classes still cross and two of them carry
  aggregates. This document first recorded Étape 2 as done. It was not, and the
  evidence was in a test written the same week.
- **Idempotency** — a client retry still creates a second `INITIALIZED` transaction.
  Étape 4, deliberately not folded into the bus.
- **G-1 to G-4 from ADR-004** — no reaper between TX-1 and TX-2, no TX-2 retry after
  provider success. `AuthorizationRecord` still exists only to make them auditable.
- **A real provider** — `MobileMoneyProviderAdapter` is a latency-simulating stub with no
  circuit breaker. Deliberate: it makes failure modes reproducible in a test.
- **Redis** for `ExpiringStore` — single-instance today, one `@Bean` line away.
- **Automated settlement** — `SETTLEMENT_PENDING → SETTLED` is not triggered. Étape 6.
- **Database-level enforcement** of the double-entry and append-only invariants. Both
  live in the domain and in tests; neither has a constraint or a trigger.
- **A deterministic lock order** for crossing transfers. A deadlock surfaces as
  `ConcurrentUpdateException` and is retried, which is correct but not free.
- **The post-ADR-004 load baseline.** The 60 s p95 was measured; the number after the fix
  was not. That run belongs on staging with production-equivalent infrastructure, not on
  a laptop — measuring it locally would produce a number nobody should trust.

### The gradient, corrected

Keyed on `ROADMAP.md`'s numbering, which is now the only one.

Volumes are `ROADMAP.md`'s, not v1's — v1's table used the other numbering and a
different scale, and reconciling the two was part of what ADR-007 settled.

| Étape | Volume target | Peak | Architecture unlock | State |
|---|---|---|---|---|
| 0 | 5 000 tx/day | 5–10 req/s | Domain model, double-entry ledger, TDD baseline | done |
| 1 | 15 000 tx/day | 20–30 req/s | Lifecycle state machine, locking, micro-transaction | done |
| 3 | 50 000 tx/day | 80–100 req/s | Hexagonal: ports, interactors, bus, architecture rules | done |
| 2 | 30 000 tx/day | 50 req/s | Modular monolith: separate applications, no shared entities | **open** — phase A only |
| 4 | 70 000 tx/day | 150 req/s | Idempotency and network reality | next |
| 5 | 100 000 tx/day | 250 req/s | Internal events and the outbox | |
| 6 | 120 000 tx/day | | Reconciliation engine | |
| 7 | 150 000 tx/day | 300–400 req/s | Reconciliation extracted as a service | |
| 8 | 200 000 tx/day | 500 req/s | Multi-provider orchestration, risk and velocity | |
| 9 | 300 000 tx/day | 700–800 req/s | Containerisation and Kubernetes | |
| 10 | 500 000 tx/day | 1000+ req/s | Observability and business KPIs | |

Étapes 2 and 3 are listed in the order they were begun. The volume targets are not
re-ordered with them, and none of the three most recent has been demonstrated: Étape 3
was delivered but its 50 000 tx/day gate was never measured, and Étape 2 is not
delivered at all. See ADR-007, and the missing baseline
above.

---

## Appendix — ADR index

| ADR | Title | Key decision |
|---|---|---|
| 001 | Immutable Double-Entry Ledger | Ledger entries are the source of truth; `balance_amount` is a read cache; float account design |
| 002 | Payment Lifecycle & State Machine | Eleven states; `VersionedEntity` mutability contract; optimistic vs pessimistic locking |
| 003 | Single-Call Payment API Design | One saga endpoint per operation, no separate authorize/capture |
| 004 | Micro-Transaction Model | TX-1 / provider I/O / TX-2; `TransactionTemplate` accepted as debt |
| 005 | Load Test Calibration | Per-operation thresholds; calibration above the provider ceiling; deterministic seeding |
| 006 | Compose Topology | One file per environment; services selected by profile, not by commenting blocks out |
| 007 | Hexagonal Before Modular | The inversion; authoritative numbering; retry scope; `TransactionBoundary` is not a Unit of Work |

---

## Closing Note

v1 closed on the invariant:

```
SUM(DEBIT operations) == SUM(CREDIT operations)
```

and the claim that everything traces back to it. That still holds. This chapter is about
the layer underneath: **what makes a claim about a system trustworthy at all.**

Four bugs here were invisible because a test double was kinder than production. Three
architecture rules passed for weeks while guarding nothing. Five documents described a
repository that had stopped existing. In every case the signal was green, and green was
wrong.

The through-line of this arc is not hexagonal architecture. It is that a check which
cannot fail is worse than no check, because it costs the same to read and buys nothing —
and it is *believed*. Hence the guard on the guards, the equality instead of the subset,
the allow-list instead of the grep, the recording transaction manager instead of the
Spring default, and the code read out of the mail body instead of the argument.

An invariant is only as good as the thing that would notice if it broke.

---

*Living document. v1 remains as the Étape 1 snapshot; this covers Étape 3 and the
first half of Étape 2.*
*[github.com/Geekers-Joel237](https://github.com/Geekers-Joel237) · [geekersjoel237.substack.com](https://geekersjoel237.substack.com)*
