# CLAUDE.md

Guidance for Claude Code and for the automated review agents that audit diffs
against this file. Every statement describes code present on this branch.

## 1. Project overview

Kora Core is a Spring Boot wallet backend for mobile-money operations: cash-in,
cash-out, P2P transfer, balance, history, operator reversal. Money moves as
immutable double-entry ledger entries on a transaction walking an 11-state
lifecycle. Provider calls hit a configurable stub, not a real provider.

## 2. Build & run

```bash
./gradlew build                                    # compile + full test suite
./gradlew test --tests "com.geekersjoel237.koracore.domain.model.LedgerTest"
./gradlew bootRun   # :8081; reads .env directly. Services come from COMPOSE_PROFILES
```

Tests ignore compose and start their own Testcontainers. `compose.yaml` does not
exist. Load tests: `perf/*.js`, run via `perf/*-run.sh`.

## 3. Stack

Java 21 · Spring Boot 4.0.3 · PostgreSQL + Flyway · springdoc-openapi 3.0.1 · jjwt 0.12.6 · Lombok · JUnit 5 + Testcontainers 1.20.4.
Starters: webmvc, data-jpa, data-jdbc, security, validation, mail, actuator, flyway.

## 4. Layers & packages

Root `com.geekersjoel237.koracore`. Dependencies point inward only.

| Layer | Package | May depend on |
|---|---|---|
| Domain | `domain/` — `model`, `model/state`, `vo`, `enums`, `port`, `query`, `exception` | JDK only; zero Spring imports today |
| Application | `application/` — `service`, `command`, `query`, `config`, `port/in` | domain; uses Spring `@Service`, `@Transactional`, `TransactionTemplate` |
| Infrastructure | `infrastructure/` — `persistence`, `provider`, `security`, `mail`, `otp`, `config`, `bootstrap`, `scheduler` | domain + application |
| Web | `web/api/**`, `web/exception` | application + domain |

## 5. Domain vocabulary

Use these names. `Order`, `PaymentPart`, `Money`, `ProviderReference` and `IdempotencyKey` do not exist here.

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
| `Id`, `PhoneNumber`, `Otp`, `AccountType` | Value objects; `Id` is the business identifier |
| `PaymentMethod` | Enum `CARD`, `ORANGE_MONEY` (`OM`), `MOBILE_MONEY` (`MOMO`), `WALLET` |
| `TransactionState` | Interface with 11 singletons: `INITIALIZED`, `AUTHORIZED`, `CAPTURED`, `SETTLEMENT_PENDING`, `SETTLED`, `COMPLETED`, `FAILED`, `AUTHORIZATION_FAILED`, `CAPTURE_FAILED`, `SETTLEMENT_FAILED`, `REVERSED` |

Every aggregate exposes `snapshot()` returning an inner record, plus a static `createFromSnapshot(...)` for reconstruction from persistence.

## 6. Non-negotiable invariants

| Invariant | Enforced today |
|---|---|
| `SUM(DEBIT) == SUM(CREDIT)` per transaction and currency | Domain: `Transaction.verifyDoubleEntry()` runs after every `recordDoubleEntry()`. No DB constraint. Asserted in `FinancialInvariantsDbTest` and `MoneyIntegrityE2ETest` |
| Ledger entries are append-only; a correction is a compensating entry | Application: `Ledger.reverse()` writes a mirrored pair; `JpaTransactionRepository.save()` only appends to the entries collection. No DB trigger |
| Ledger entries are the source of truth for balance | Domain + ADR-001. `accounts.balance_amount` is a denormalized read cache updated in the same transaction as the ledger write; on divergence, `LedgerEntry` rows win. It is **not** derived on read |
| Float account is unbounded | `Account.debit()` is a no-op for `FLOAT_ACCOUNT`, so its `balance_amount` stays 0; audit it through `ledger_entries` |
| Money is `BigDecimal`, never `double` or `float` | `Amount.value` is `BigDecimal`; all money columns are `NUMERIC(19,4)` |
| Currency is mandatory; cross-currency arithmetic forbidden | `Amount` compact constructor rejects a blank currency; `add` / `subtract` / `isGreaterThan*` throw `CurrencyMismatchException` |
| No illegal state transition | Each `*State` class validates its own successors and throws `InvalidStateTransitionException`. Legal edges: INITIALIZED to AUTHORIZED, FAILED, AUTHORIZATION_FAILED; AUTHORIZED to CAPTURED, FAILED, AUTHORIZATION_FAILED, CAPTURE_FAILED, REVERSED; CAPTURED to SETTLEMENT_PENDING, CAPTURE_FAILED, REVERSED; SETTLEMENT_PENDING to SETTLED, SETTLEMENT_FAILED, REVERSED; SETTLED to COMPLETED, REVERSED; COMPLETED to REVERSED |
| No provider network call inside a DB transaction | `PaymentTransactionalExecutor` splits cash-in and cash-out into TX-1 (~10 ms) / provider I/O (~1 400 ms, zero connections held) / TX-2 (~20 ms). Per ADR-004, the previous class-level `@Transactional` produced p95 60 000 ms and a 73.93 % error rate at 25 req/s through HikariCP pool exhaustion |

## 7. Review rules

Assertions to apply to a diff. Each one is decidable by reading the diff alone.

1. A file under `domain/` that imports `org.springframework.*` is a defect.
2. A file under `domain/` or `application/` that imports `koracore.infrastructure.*`
   or `koracore.web.*` is a defect. There is currently no exception.
3. A call to `ProviderPort.authorize` / `capture` / `reverse` placed inside a
   `txTemplate.execute(...)` lambda, or inside a method annotated
   `@Transactional`, is a defect.
4. Adding `@Transactional` to `PaymentService` or `PaymentTransactionalExecutor`
   at class level is a defect — both are non-transactional by design.
5. A `double` or `float` used for a monetary value, anywhere, is a defect.
6. Comparing or combining two `Amount` values through `.value()` instead of the
   `Amount` methods bypasses the currency check and is a defect.
7. A state change written as a direct field assignment instead of going through
   the named methods on `Transaction` (`authorize()`, `capture()`, `settle()`,
   `markCompleted()`, `reverse()`, `fail*()`) is a defect.
8. Any `UPDATE` or `DELETE` on `ledger_entries` or `trx_state_historics`, and any
   `clear()` or `remove()` on the entries collection of `Transaction` or
   `TransactionEntity`, is a defect.
9. A change to `accounts.balance_amount` not paired with `LedgerEntry` rows written
   in the same transaction is a defect.
10. An `*Action` class in `web/api/**` containing branching on domain state,
    arithmetic, or repository access is a defect — it may only build a command,
    delegate to a `*UseCase`, and map the result.
11. A command, port, or domain type accepting a raw `String` where a domain type
    exists (`Id`, `Amount`, `PaymentMethod`, `PhoneNumber`) is a defect.
    Conversion happens once, in `Request.toCommand()` at the web layer.
12. A new or altered table column without a new
    `V<yyyyMMddHHmm>__snake_case.sql` under `src/main/resources/db/migration/`
    is a defect. `ddl-auto` must stay `validate` in `application.yaml` and
    `none` in `src/test/resources/application-test.yaml`.
13. A new domain exception without a matching `@ExceptionHandler` entry in
    `GlobalExceptionHandler` is a defect.
14. New domain behaviour without a Spring-free unit test is a defect.

## 8. Testing strategy

38 test classes under `src/test/java`, three levels:

- **Unit** — `domain/**`, `application/**`. Plain JUnit 5, no Spring context; ports faked by the hand-written doubles in `shared/inmemory/`.
- **Integration** — `infrastructure/persistence/**` extends `AbstractRepositoryTest`: `@SpringBootTest` + `@Transactional` + `@ActiveProfiles("test")` + `@Import(TestMailConfig.class)`, with a static `PostgreSQLContainer<>("postgres:16-alpine")` wired via `@DynamicPropertySource`. `@DataJpaTest` and `@AutoConfigureTestDatabase` are gone in Spring Boot 4 — do not reintroduce them.
- **E2E** — `e2e/**` extends `AbstractE2ETest`: `@SpringBootTest(RANDOM_PORT)` over real HTTP with `RestTemplate`; isolation by `TRUNCATE` in `@BeforeEach`, not by rollback. `KoraCoreApplicationTests` is the only class using `@Testcontainers` + `@ServiceConnection`.

Flyway owns the schema in tests exactly as in production.

## 9. Known gaps — do not report

Deliberate and tracked. Do not raise them on a PR.

- `AuthService` signs JWTs with `io.jsonwebtoken` directly instead of through a
  `TokenIssuer` port. It imports no `koracore.infrastructure` type, so rule 2
  still holds; the port extraction is a Step 2 target alongside
  `TransactionBoundary`.
- `PaymentTransactionalExecutor` uses Spring's `TransactionTemplate` directly.
  The `TransactionBoundary` port that removes it is deferred (ADR-004, Step 2).
- No idempotency store: a client retry can create duplicate `INITIALIZED`
  transactions (ADR-004 G-5, Step 3).
- No recovery for a crash between TX-1 and TX-2, and no TX-2 retry after provider
  success; `AuthorizationRecord` exists only to make these auditable
  (ADR-004 G-1 to G-4, Step 3).
- `MobileMoneyProviderAdapter` is a latency-simulating stub, not an HTTP client,
  and has no circuit breaker (Step 3).
- `OtpStoreAdapter` is a `ConcurrentHashMap`, single-instance only; Redis is Step 2.
- `SETTLEMENT_PENDING` to `SETTLED` is not automated; the reconciliation engine
  is Step 6.
- The double-entry and append-only invariants have no database-level constraint.
- No CQRS split: `domain/query/` holds `PageRequest`, `PageResult` and
  `TransactionFilter` for the single `TransactionRepository` (Step 5).

## 10. Key files

- `ROADMAP.md` — 10-step evolution plan
- `HELP.md` — engineering standards, DDD/SOLID rules, project conventions
- `docs/kora-core-state-of-view.md` — engineering retrospective
- `docs/adr/` — ADR-001 immutable ledger and balance cache · ADR-002 payment lifecycle and locking · ADR-003 single-call payment API · ADR-004 micro-transaction model · ADR-005 load-test calibration
- `src/main/resources/db/migration/` — `V202605270702__initial_schema.sql` creates the
  whole schema; `V202609051542__rename_ledger_vocabulary.sql` renames
  `operations` to `ledger_entries` and `from_id`/`to_id` to
  `from_account_id`/`to_account_id`
- `perf/PERF.md`, `perf/SIMULATION.md` — load-test procedure
