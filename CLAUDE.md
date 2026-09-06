# CLAUDE.md

Guidance for Claude Code and for the automated review agents that audit diffs
against this file. Every statement describes code present on this branch.

Step numbers refer to `ROADMAP.md`, which is authoritative. This file numbers nothing
of its own — the two documents used to mean different things by "Step 2" (ADR-007).

## 1. Project overview

Kora Core is a Spring Boot wallet backend for mobile-money operations: cash-in,
cash-out, P2P transfer, balance, history, operator reversal. Money moves as
immutable double-entry ledger entries on a transaction walking an 11-state
lifecycle. Provider calls hit a configurable stub, not a real provider.

## 2. Build & run

```bash
./gradlew build     # compile + full test suite
./gradlew test --tests "*LedgerTest"
./gradlew test --tests "com.geekersjoel237.koracore.payment.unit.*"    # one pyramid level
./gradlew bootRun   # :8081; reads .env directly. Services come from COMPOSE_PROFILES
```

Tests ignore compose and start their own Testcontainers. `compose.yaml` does not
exist. Load tests: `perf/*.js`, run via `perf/*-run.sh`.

## 3. Stack

Java 21 · Spring Boot 4.0.3 · PostgreSQL + Flyway · springdoc-openapi 3.0.1 · jjwt 0.12.6 · Lombok · JUnit 5 + Testcontainers 1.20.4.
Starters: webmvc, data-jpa, data-jdbc, security, validation, mail, actuator, flyway.

## 4. Modules & layers

Root `com.geekersjoel237.koracore`. Three top-level packages, each with the same five
layers. Dependencies point inward only.

**They are not modules yet.** One Gradle project, one `@SpringBootApplication`, one
classpath — the boundary is asserted by a test, not enforced by the build — and `auth`
and `payment` still share domain models. Roadmap Étape 2 is open; do not describe this
as a modular monolith.

| Module | Owns |
|---|---|
| `auth/` | identity, credentials, OTP challenge, tokens |
| `payment/` | accounts, ledger, transactions, provider, history |
| `shared/` | the kernel: money and identity value objects, CQRS contracts, transaction boundary, mail, expiring store |

**`shared/` names no module.** Not "almost none" — none, asserted by
`ModuleBoundariesTest` over `src/main` and over `shared/unit` and `shared/integration`
in the tests. The e2e harness is exempt: it drives the whole application over HTTP, so
it names both modules by construction.

Whatever `auth` and `payment` still take from each other is recorded in that same test
as an **equality**, so a new coupling fails the build and closing one fails it too.
Eleven classes cross today, `Customer` and `Account` among them. That list is meant to
shrink, and Étape 2 is finished when it is empty and each package is its own
application.

| Layer | Package | May depend on |
|---|---|---|
| Domain | `<module>/domain/` — `model`, `model/state`, `vo`, `enums`, `exception` | JDK and `shared/domain` only |
| Application | `<module>/application/` — `usecases`, `command`, and what the module needs: `query` and `result` in payment, `mail` in auth, `cqrs` and `transaction` in shared | domain + ports |
| Ports | `<module>/ports/in`, `<module>/ports/out` | domain + application |
| Adapters | `<module>/adapters/in/**`, `<module>/adapters/out/**` | everything inward |
| Config | `<module>/config/` | everything — it is the composition root |

`application/` and `ports/` name **no foreign type at all**: not Spring, not Jakarta,
not a JSON binding. `HexagonalArchitectureTest` collects every import that is neither
JDK nor ours and asserts the set equals `FRAMEWORK_ALLOW_LIST`, which is empty. Adding
an entry costs a line there and the sentence that justifies it.

Interactors are plain classes wired by hand in `config/`. Every use case is
instantiable in one line, with no container.

## 5. Domain vocabulary

Use these names. `Order`, `PaymentPart`, `Money`, `ProviderReference`, `IdempotencyKey`
and `Otp` do not exist here.

| Type | Role |
|---|---|
| `Transaction` | Aggregate root of a money movement; owns ledger entries, state, history |
| `LedgerEntry` | One ledger entry, `DEBIT` or `CREDIT`; append-only. Table `ledger_entries` |
| `Ledger` | Domain service creating transactions (`initiate`, `writeEntries`, `reverse`) |
| `Account` | Balance holder; `CUSTOMER_ACCOUNT` or `FLOAT_ACCOUNT` |
| `TrxStateHistoric` | Immutable audit row emitted on every state transition |
| `AuthorizationRecord` | Provider authorization with TTL, used when capture fails |
| `Customer`, `User` | Identity and credentials |
| `Amount` | `record(BigDecimal value, String currency)` — the money type; `Balance` wraps one and returns new instances from `credit()` / `debit()` |
| `Id`, `Msisdn`, `Pin` | Kernel value objects; `Id` is the business identifier |
| `PhoneNumber`, `OtpCode`, `OtpPurpose` | Auth value objects. `OtpCode` is both the issued and the submitted code — the store owns the lifetime |
| `AccountType`, `Direction` | Payment value objects |
| `PaymentMethod` | Enum `CARD`, `ORANGE_MONEY` (`OM`), `MOBILE_MONEY` (`MOMO`), `WALLET` |
| `TransactionState` | Interface with 11 singletons: `INITIALIZED`, `AUTHORIZED`, `CAPTURED`, `SETTLEMENT_PENDING`, `SETTLED`, `COMPLETED`, `FAILED`, `AUTHORIZATION_FAILED`, `CAPTURE_FAILED`, `SETTLEMENT_FAILED`, `REVERSED` |

Kernel contracts:

| Type | Role |
|---|---|
| `Command<R>` / `CommandHandler<C,R>` | A write, and the one use case that answers it. Carries a `correlationId` |
| `Query<R>` / `QueryHandler<Q,R>` | A read. No correlation id: nothing to correlate, no replay to refuse |
| `PagedQuery<T>` / `Pagination` / `PageResult<T>` | Paging, opt-in. `Pagination` holds every paging rule: default 20, max 100, bounds, offset |
| `TransactionBoundary` | Runs a supplier in one transaction. **Not a Unit of Work** (ADR-007) |
| `MailPort` / `Mail` | Sends a written message. Composition belongs to the caller |
| `ExpiringStore<V>` | Keyed store whose entries expire; the value is opaque |
| `BusinessException` | The domain refused. Throwing one inside a boundary rolls it back |
| `RetryExhaustedException` | Retries ran out. Nothing was refused — 503, not 4xx |

Every aggregate exposes `snapshot()` returning an inner record, plus a static
`createFromSnapshot(...)` for reconstruction from persistence.

## 6. Non-negotiable invariants

| Invariant | Enforced today |
|---|---|
| `SUM(DEBIT) == SUM(CREDIT)` per transaction and currency | Domain: `Transaction.verifyDoubleEntry()` runs after every `recordDoubleEntry()`. No DB constraint. Asserted in `FinancialInvariantsDbTest` and `MoneyIntegrityE2ETest` |
| Ledger entries are append-only; a correction is a compensating entry | `Ledger.reverse()` writes a mirrored pair; `JpaTransactionRepository.save()` only appends. No DB trigger |
| Ledger entries are the source of truth for balance | Domain + ADR-001. `accounts.balance_amount` is a denormalized read cache updated in the same transaction as the ledger write; on divergence, `LedgerEntry` rows win. It is **not** derived on read |
| Float account is unbounded | `Account.debit()` is a no-op for `FLOAT_ACCOUNT`, so its `balance_amount` stays 0; audit it through `ledger_entries` |
| Money is `BigDecimal`, never `double` or `float` | `Amount.value` is `BigDecimal`; all money columns are `NUMERIC(19,4)` |
| Currency is mandatory; cross-currency arithmetic forbidden | `Amount` compact constructor rejects a blank currency; `add` / `subtract` / `isGreaterThan*` throw `CurrencyMismatchException` |
| No illegal state transition | Each `*State` class validates its own successors and throws `InvalidStateTransitionException`. Legal edges: INITIALIZED to AUTHORIZED, FAILED, AUTHORIZATION_FAILED; AUTHORIZED to CAPTURED, FAILED, AUTHORIZATION_FAILED, CAPTURE_FAILED, REVERSED; CAPTURED to SETTLEMENT_PENDING, CAPTURE_FAILED, REVERSED; SETTLEMENT_PENDING to SETTLED, SETTLEMENT_FAILED, REVERSED; SETTLED to COMPLETED, REVERSED; COMPLETED to REVERSED |
| No provider network call inside a DB transaction | `CashInService`, `CashOutService` and `ReversePaymentService` split into TX-1 (~10 ms) / provider I/O (~1 400 ms, zero connections held) / TX-2 (~20 ms). Per ADR-004, a class-level `@Transactional` produced p95 60 000 ms and a 73.93 % error rate at 25 req/s through HikariCP pool exhaustion |
| A rollback happens by throwing | `BusinessException` — or any unchecked throwable — inside `TransactionBoundary.execute` discards the writes; returning normally commits. The port hands out no transaction status, so throwing is the only way for a use case to abort. Pinned in `SpringTransactionBoundaryTest` against a recording transaction manager |
| Retry wraps the boundary, never the reverse | `ConcurrentUpdateException` surfaces at commit, so the transaction is already dead. Retry is scoped to the phase that is safe to replay — TX-2, after the provider answered (ADR-007) |

## 7. Review rules

Assertions to apply to a diff. Each one is decidable by reading the diff alone.

1. A file under `<module>/domain/` that imports `org.springframework.*` is a defect.
2. A file under `<module>/application/` or `<module>/ports/` that imports any type
   outside the JDK and `com.geekersjoel237.koracore` is a defect unless it is added to
   `FRAMEWORK_ALLOW_LIST` in the same diff, with its justification.
3. A file under `shared/` that imports `koracore.auth.*` or `koracore.payment.*` is a
   defect. The only exception is the e2e harness, `shared/e2e/`.
4. A call to `ProviderPort.authorize` / `capture` / `reverse` placed inside a
   `boundary.execute(...)` lambda, or inside a method annotated `@Transactional`,
   is a defect.
5. `retry.execute(() -> boundary.execute(...))` is correct.
   `boundary.execute(() -> retry.execute(...))` is a defect.
6. A `double` or `float` used for a monetary value, anywhere, is a defect.
7. Comparing or combining two `Amount` values through `.value()` instead of the
   `Amount` methods bypasses the currency check and is a defect.
8. A state change written as a direct field assignment instead of going through
   the named methods on `Transaction` (`authorize()`, `capture()`, `settle()`,
   `markCompleted()`, `reverse()`, `fail*()`) is a defect.
9. Any `UPDATE` or `DELETE` on `ledger_entries` or `trx_state_historics`, and any
   `clear()` or `remove()` on the entries collection of `Transaction` or
   `TransactionEntity`, is a defect.
10. A change to `accounts.balance_amount` not paired with `LedgerEntry` rows written
    in the same transaction is a defect.
11. An `*Action` class in `<module>/adapters/in/rest/api/**` containing branching on
    domain state, arithmetic, or repository access is a defect — it may only build a
    command or query, delegate, and map the result. Naming more than one driving port
    is a defect too.
12. An interactor in `<module>/application/usecases/` that imports another interactor,
    or a second driving port, is a defect.
13. A command, port, or domain type accepting a raw `String` where a domain type
    exists (`Id`, `Amount`, `PaymentMethod`, `Msisdn`, `Pin`, `PhoneNumber`) is a
    defect. Conversion happens once, in `Request.toCommand()` at the web layer.
14. A new or altered table column without a new
    `V<yyyyMMddHHmm>__snake_case.sql` under `src/main/resources/db/migration/`
    is a defect. `ddl-auto` must stay `validate` in `application.yaml` and
    `none` in `src/test/resources/application-test.yaml`.
15. A new domain exception without a matching `@ExceptionHandler` entry in the module's
    handler (`AuthExceptionHandler`, `PaymentExceptionHandler`) or in
    `SharedExceptionHandler` is a defect.
16. New domain behaviour without a Spring-free unit test is a defect.
17. A test placed outside `<module>/{unit,integration,e2e}/` is a defect, as is a
    `@SpringBootTest` under `unit/` or an HTTP client under `integration/`.

## 8. Testing strategy

55 test classes under `src/test/java`. The tree states the pyramid level first, and
`TestPyramidTest` checks that the statement is true.

```
src/test/java/com/geekersjoel237/koracore/
└── <module>/            auth · payment · shared
    ├── unit/            domain · application · doubles · (shared: adapters, ports, architecture)
    ├── integration/     persistence · query
    └── e2e/
```

Within a level, files are grouped by type, then by concern — `payment/unit/domain/`
splits into `account`, `ledger`, `transaction`, `authorization`.

- **Unit** — plain JUnit 5, no Spring context. Ports faked by the in-memory out
  adapters in `<module>/unit/doubles/`. No `@SpringBootTest`, no container, asserted.
- **Integration** — extends `AbstractRepositoryTest`: `@SpringBootTest` +
  `@Transactional` + `@ActiveProfiles("test")` + `@Import(TestMailConfig.class)`, with
  a static `PostgreSQLContainer<>("postgres:16-alpine")` wired via
  `@DynamicPropertySource`. No HTTP, asserted. `@DataJpaTest` and
  `@AutoConfigureTestDatabase` are gone in Spring Boot 4 — do not reintroduce them.
- **E2E** — extends `AbstractE2ETest`: `@SpringBootTest(RANDOM_PORT)` over real HTTP
  with `RestTemplate`; isolation by `TRUNCATE` in `@BeforeEach`, not by rollback.
  `KoraCoreApplicationTests` is the only class using `@Testcontainers` +
  `@ServiceConnection`.

Flyway owns the schema in tests exactly as in production.

Structural rules live in `shared/unit/architecture/`: `HexagonalArchitectureTest`
(layers and the allow-list), `ModuleBoundariesTest` (kernel purity and the recorded
couplings), `TestPyramidTest` (the tree above), `ConfigurationHygieneTest`,
`MigrationNamingConventionTest`.

## 9. Known gaps — do not report

Deliberate and tracked. Do not raise them on a PR.

- No idempotency store: a client retry can create duplicate `INITIALIZED`
  transactions. Roadmap Étape 4 — deliberately not folded into the command bus.
- No recovery for a crash between TX-1 and TX-2, and no TX-2 retry after provider
  success; `AuthorizationRecord` exists only to make these auditable (ADR-004 G-1 to G-4).
- `MobileMoneyProviderAdapter` is a latency-simulating stub, not an HTTP client,
  and has no circuit breaker.
- `InMemoryExpiringStore` holds OTP codes in one JVM's heap. Single-instance only;
  Redis replaces it by changing one `@Bean` line in `AuthUseCaseConfiguration`.
- `SETTLEMENT_PENDING` to `SETTLED` is not automated; the reconciliation engine is
  roadmap Étape 6.
- The double-entry and append-only invariants have no database-level constraint.
- A deterministic lock order for crossing transfers is not implemented; a deadlock
  surfaces as `ConcurrentUpdateException` and is retried.
- `auth -> payment` and `payment -> auth` couplings remain, listed and asserted in
  `ModuleBoundariesTest`. Closing them needs a published `OpenWalletUseCase` and a
  wallet lookup by msisdn.

## 10. Key files

- `ROADMAP.md` — 10-step evolution plan, authoritative numbering
- `HELP.md` — engineering standards, DDD/SOLID rules, project conventions
- `CONTRIBUTING.md` — configuration assignment and migration naming, both test-enforced
- `docs/kora-core-state-of-view-v2.md` — engineering retrospective, Étapes 2 and 3.
  `docs/kora-core-state-of-view.md` is the Étape 1 snapshot, kept unedited; v2 lists
  what it says that no longer holds
- `docs/adr/` — ADR-001 immutable ledger and balance cache · ADR-002 payment lifecycle
  and locking · ADR-003 single-call payment API · ADR-004 micro-transaction model ·
  ADR-005 load-test calibration · ADR-006 compose topology ·
  ADR-007 hexagonal before modular
- `src/main/resources/db/migration/` — `V202605270702__initial_schema.sql` creates the
  whole schema; `V202609051542__rename_ledger_vocabulary.sql` renames `operations` to
  `ledger_entries` and `from_id`/`to_id` to `from_account_id`/`to_account_id`
- `perf/PERF.md`, `perf/SIMULATION.md` — load-test procedure
