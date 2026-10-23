# Building Kora Core — A Fintech Wallet Engine From First Principles

## An Engineering State of View

**Author**: Ivan Joël Tchatchoua Bayon — [@Geekers-Joel237](https://github.com/Geekers-Joel237)  
**Date**: May 2026  
**Version**: Step 1 — First Stable Release  
**Tags**: `java-21` `spring-boot-4` `ddd` `hexagonal-architecture` `double-entry-ledger` `tdd` `testcontainers` `concurrency` `fintech`

---

> *This document is not a specification. It is a retrospective — a precise, chronological account of how Kora Core was built, what decisions were made, why they were made that way, and what it cost to make them. It is written in the belief that the path matters as much as the destination.*

---

## Prologue — The Problem Space

Before the first line of code, there is a question: what does it actually mean to move money?

Not in the abstract. Not in the banker's sense. But concretely — what has to be true in a system so that when 10,000 XAF leaves one wallet and arrives in another, everyone agrees that it happened, that it happened correctly, and that it can be proven?

This question is the foundation of Kora Core. The system is a production-grade mobile money wallet engine — the kind of backend that powers neobanks and mobile money operators in the CEMAC zone. It handles cash-in (depositing money from a mobile money provider), cash-out (withdrawing), P2P transfers between internal wallets, and the full lifecycle of those payments through authorization, capture, settlement, and potential reversal.

But the harder problem is not moving money. The harder problem is *knowing* that money moved correctly, under concurrent load, in the face of provider failures, with a full audit trail that satisfies BCEAO/CEMAC traceability requirements — and being able to reconstruct the authoritative state of any account at any point in time from immutable records.

Kora Core is built to solve that harder problem. This is the story of how.

---

## Chapter 1 — The Setup: Deliberate Choices Before the First Commit

### The Stack Decision

The initial commit (`3844bce Initial commit`) is, as all initial commits are, unremarkable. But the choices embedded in the Gradle build file are not.

**Java 21.** Not as a novelty pick, but because records, sealed types, and pattern matching are precisely the tools DDD domain modeling needs. A `PhoneNumber` is a record. A `TransactionState` is sealed. An `Amount` operation that tries to add XAF to EUR becomes a match that cannot compile without handling the mismatch case. The language serves the model.

**Spring Boot 4.0.3.** A deliberate choice to be on the latest stable, not the safest. This came with a cost: Spring Boot 4.x introduced breaking changes that are not obvious until you hit them — notably the removal of `@DataJpaTest` from the test autoconfigure module, the requirement for explicit `@Autowired` when multiple constructors exist (Spring Framework 7 behavior), and the need for `spring-boot-starter-flyway` instead of bare `flyway-core` for Flyway auto-configuration to engage. Each of these was discovered the hard way, documented, and resolved.

**PostgreSQL as the primary store.** Not an in-memory H2 shortcut. From the first integration test, real PostgreSQL — via Docker Compose in development, via Testcontainers in the test suite. The schema is real. The constraints are real. The behavior of `NUMERIC(19,4)` with `BigDecimal` is tested, not assumed.

**Lombok.** Controversial in some circles, unambiguous here: it eliminates boilerplate from JPA entities and configuration classes without polluting the domain layer (which remains zero-Lombok, zero-Spring — pure Java).

### Docker in Development — Infrastructure as Code from Day Zero

The `compose.yaml` file was part of the initial setup (`[#setup] feature: initialize project`). Spring Boot's Docker Compose support (`spring-boot-docker-compose`) means `./gradlew bootRun` is the entire local development command: it starts PostgreSQL, verifies connectivity, and brings the application up. No separate `docker compose up`, no manual database creation, no environment variable juggling.

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: kora-db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: root
    ports:
      - "5432:5432"
```

This matters more than it sounds. When infrastructure is one command away, it gets used. When it requires a setup ritual, it accumulates drift. The local environment drifting from the CI environment is one of the most reliable sources of "it works on my machine" bugs in fintech systems. Docker Compose from day zero closes that gap.

The observability stack was added later (`[#load-test] feat: production-grade load test pipeline with Grafana observability`): MailDev for local mail testing (SMTP port 1025, UI at :1080), InfluxDB 1.8, and Grafana for k6 metrics. But the pattern was set at the beginning: if the system needs it, it lives in `compose.yaml`.

---

## Chapter 2 — The Domain First: Value Objects and the Discipline of Invariants

The real work started at commit `[#core] feature: implement core id generation` and the sequence that followed it over the next several commits. The pattern is visible in the branch name itself: `feature/core-wallet-step0`. Not feature/auth, not feature/api — *core*. The domain came first.

### Id — The Simplest Thing That Must Be Right

```java
public record Id(String value) {
    public Id {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Id cannot be blank");
    }

    public static Id generate() {
        return new Id(UUID.randomUUID().toString());
    }
}
```

This is three lines of business logic and three lines of guard. But it establishes the pattern that every subsequent value object follows: **the compact constructor is the invariant enforcer**. There is no public setter. There is no way to create an `Id` with a blank value — the constructor makes it structurally impossible. The type hierarchy of the entire domain is built on this discipline.

### Amount — Where Fintech Starts Getting Serious

The `Amount` record (`[#core] feature: implement Amount vo, amount and currency, fintech approach, unit tests`) encodes two decisions that have long consequences:

**First**: `BigDecimal`, never `Float` or `Double`. This is non-negotiable in financial systems. IEEE 754 floating-point cannot represent 0.1 exactly. `0.1 + 0.2 == 0.30000000000000004` in double arithmetic. The `AmountTest` explicitly validates that `BigDecimal(0.1).add(BigDecimal(0.2))` stored in an `Amount` reads back from the database as exactly `0.3`. The database column is `NUMERIC(19,4)` — 19 significant digits, 4 decimal places. The test passes against real PostgreSQL via Testcontainers. It is not assumed.

**Second**: currency is mandatory and operations between different currencies throw `CurrencyMismatchException`. This is not a validation concern. It is a domain invariant. An amount without a currency is not a financial amount. An operation between XAF and EUR is not a rounding issue — it is a programming error that should be caught at the domain layer, not at the database constraint layer.

```java
public Amount add(Amount other) {
    requireSameCurrency(other);
    return new Amount(this.value.add(other.value), this.currency);
}
```

The result is always a new `Amount`. Value objects are immutable. This is not a convention — it is enforced by the record type.

### PhoneNumber — The African Mobile Money Context

```java
public record PhoneNumber(String prefix, String number) {
    public PhoneNumber {
        // prefix not blank
        // number matches [0-9]{8,12}
        // invariants enforced in compact constructor
    }

    public String fullNumber() {
        return prefix + number;
    }
}
```

The `PhoneNumber` value object reflects a specific architectural decision: in the CEMAC/BCEAO mobile money ecosystem, phone numbers are the primary financial identifier. They are not secondary contact information — they are how wallets are addressed. The `CustomerRepository` port therefore exposes `findByPhoneNumber(String fullNumber)` where the full string is the concatenation of prefix and number, not a structured type, allowing the JPQL to do a `CONCAT()` match against stored prefix and number columns without requiring a computed column.

### Balance — The Cache Contract

```java
public record Balance(Amount amount) {
    public Balance {
        if (amount.value().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Balance cannot be negative");
    }

    public Balance credit(Amount delta) {
        return new Balance(amount.add(delta));
    }

    public Balance debit(Amount delta) {
        return new Balance(amount.subtract(delta)); // throws if result < 0
    }
}
```

The `Balance` record is described as the "materialized cache of the Ledger for reads." This phrasing is not casual — it is the precise contract. The balance on an account entity is a read optimization, not the source of truth. The source of truth is always the sum of `Operation` entries in the double-entry ledger. When you read a balance, you are reading a denormalized cache that happens to be updated atomically with every ledger write. If it ever diverges from the ledger sum (which is detectable by audit), the ledger wins.

This distinction becomes architecturally critical in later chapters.

---

## Chapter 3 — The Entities: A Hierarchy That Encodes Mutability

### BaseEntity — The Universal Root

Every persistent entity in Kora Core inherits from `BaseEntity`:

```java
@MappedSuperclass
@SoftDelete
@SQLRestriction("deleted_at IS NULL")
public abstract class BaseEntity {
    @Id
    private String id;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
```

Four things to observe here:

**`@SoftDelete`**: Hibernate's built-in soft-delete annotation. Combined with `@SQLRestriction("deleted_at IS NULL")`, all queries against any entity extending `BaseEntity` automatically filter out soft-deleted rows. The `deleted` boolean column exists in the schema, but it is the `deleted_at` timestamp that drives the filter. Records are never physically deleted — they are timestamped. In a financial system, physical deletion is never acceptable. An account that was closed two years ago is not erased — it is closed, and its closure is timestamped.

**`@CreationTimestamp` and `@UpdateTimestamp`**: managed by Hibernate, not by application code. There is no `setCreatedAt()` method exposed to the application layer. Timestamps are facts about database operations, not application state. The database sets them.

**`String id`**: Not `UUID`, not `Long`. String. Because the domain's `Id` record wraps a UUID string, and the persistence layer stores what the domain produces. No auto-increment sequences in the payment domain — the application generates identifiers, and the database stores them. This is important for idempotency: the identifier is known before the database roundtrip.

### VersionedEntity — The Mutability Contract in the Type System

The most important architectural decision in the entity hierarchy is one that is easy to miss because it involves an entity that does not do much:

```java
@MappedSuperclass
public abstract class VersionedEntity extends BaseEntity implements Persistable<String> {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Override
    @Transient
    public boolean isNew() {
        return getId() == null;
    }
}
```

`@Version` is not on `BaseEntity`. It is on `VersionedEntity`, an intermediate class that sits between `BaseEntity` and the mutable entities that need concurrency protection.

The entities that extend `VersionedEntity`: `AccountEntity`, `TransactionEntity`, `UserEntity`, `CustomerEntity`, `AuthorizationRecordEntity`.

The entities that extend `BaseEntity` directly: `LedgerEntity`, `OperationEntity`, `TrxStateHistoricEntity`.

This is not a coincidence. It is an explicit architectural contract:

- `OperationEntity` is **INSERT-only**. It records a financial movement in the double-entry ledger. It is immutable after creation. Placing `@Version` on it would imply Hibernate might emit `UPDATE` statements against it — which must never happen. The absence of `@Version` is the enforcement mechanism.
- `TrxStateHistoricEntity` is **INSERT-only**. Same reasoning. Each state transition produces a new, immutable `TrxStateHistoric` entry. There is no "update a state history record."
- `LedgerEntity` is **read-only after bootstrap**. There is one ledger, created at startup. It is never updated.

The type hierarchy encodes the immutability contract. An entity that extends `BaseEntity` directly is a declaration that it will never be updated. An entity that extends `VersionedEntity` is a declaration that it is mutable and that Hibernate should enforce optimistic locking on concurrent updates.

The `isNew()` override on `VersionedEntity` resolves a subtle Spring Data JPA issue: Spring Data normally determines whether to call `persist()` or `merge()` based on whether the `@Version` field is null. Since `VersionedEntity` instances are built via Lombok's `@Builder` (which never sets `version` to null via the builder), Spring Data was calling `persist()` on entities that already existed — causing `NonUniqueObjectException` when the same entity was saved twice in a Hibernate session. The fix: override `isNew()` to use the presence of the `id` field instead.

### The Snapshot Pattern — Domain Meets Infrastructure

The domain model uses the Snapshot pattern to cross the domain/infrastructure boundary without leaking framework types into the domain:

```java
// In Transaction.java (domain)
public record Snapshot(
    Id transactionId,
    String transactionNumber,
    Id fromId,
    Id toId,
    TransactionState state,
    TransactionType type,
    PaymentMethod paymentMethod,
    Amount amount,
    Instant createdAt,
    List<Operation.Snapshot> operations,
    List<TrxStateHistoric> history
) {}

public Snapshot snapshot() {
    return new Snapshot(transactionId, transactionNumber, ...);
}

public static Transaction createFromSnapshot(Snapshot snap,
                                             List<Operation> operations,
                                             List<TrxStateHistoric> history) {
    // reconstruct domain object from infrastructure data
}
```

Every domain entity exposes `snapshot()` returning an inner record (pure data, no behavior), and `createFromSnapshot()` as the reconstruction path. The JPA adapter reads an entity from the database, maps it to a snapshot, and calls `createFromSnapshot()` to produce the domain object. The domain object never knows about JPA annotations. The JPA entity never knows about domain behavior.

This pattern makes the domain layer genuinely framework-free. Running a unit test against `PaymentService` requires no Spring context, no database, no Testcontainer — because the domain objects can be constructed directly, and the in-memory repository implementations are trivial.

---

## Chapter 4 — The Double-Entry Ledger: Why It Has to Be This Way

The commit `[#core] feature: implement operation, transaction and ledger logic, unit tests` is the most significant in the early codebase. Everything else builds on top of what was established here.

### The Invariant

The double-entry ledger invariant is as old as accounting itself:

```
SUM(DEBIT operations) == SUM(CREDIT operations)
```

For every transaction in the system, at every point in time, the total amount debited must equal the total amount credited. Money does not appear from nowhere, and it does not disappear.

A 10,000 XAF cash-in produces exactly two operations:
```
CREDIT  10,000 XAF  → customer account
DEBIT   10,000 XAF  → system float account
────────────────────────────────────────
Net ledger impact   = 0
```

This is verified in code immediately before any persistence:

```java
public void recordDoubleEntry(Amount amount, Id debitAccountId, Id creditAccountId) {
    this.operations.add(Operation.create(Id.generate(), OperationType.DEBIT, amount, debitAccountId));
    this.operations.add(Operation.create(Id.generate(), OperationType.CREDIT, amount, creditAccountId));
    verifyDoubleEntry();  // throws IllegalStateException if invariant violated
}

private void verifyDoubleEntry() {
    Amount debit  = sumByType(OperationType.DEBIT);
    Amount credit = sumByType(OperationType.CREDIT);
    if (!debit.equals(credit))
        throw new IllegalStateException(
            "Double-entry invariant violated: debit=" + debit.value()
            + " credit=" + credit.value());
}
```

The invariant check happens in memory, before the database is touched. If something is wrong with the balance logic — wrong account IDs, inverted operations, wrong amount — the `IllegalStateException` fires immediately, no partial state reaches the database.

### The Float Account — The Treasury's Sentinel

The float account represents the system treasury. It is the counterparty in every provider-bound operation:

- Cash-in: customer account is credited, float account is debited
- Cash-out: customer account is debited, float account is credited
- P2P transfer: sender account is debited, recipient account is credited (no float involvement)

The float account has a notable property: **its `balance_amount` in the database is always 0, regardless of how many transactions have passed through it.**

This is not a bug. `Account.debit()` is a no-op for `FLOAT_ACCOUNT` type:

```java
public void debit(Amount amount) {
    if (snapshot().accountType().resourceType() == ResourceType.FLOAT_ACCOUNT) {
        return;  // float account is unbounded — no balance tracking
    }
    this.balance = balance.debit(amount);  // may throw InsufficientFundsException
}
```

The float account's true balance is always the sum of its ledger `Operation` entries — computable at any time via a SQL aggregation. The denormalized `balance_amount` field is intentionally not maintained for the float account, because the float account is considered unbounded. It can always honour a debit, regardless of its nominal balance. `FinancialInvariantsDbTest` validates:

```sql
SELECT SUM(CASE WHEN type='CREDIT' THEN amount ELSE -amount END) AS float_balance
FROM operations
WHERE account_id = :floatAccountId
```

This SQL query is the ground truth for the float account. The database field is noise.

### Why Not Pure Event Sourcing?

This decision was explicit. Pure event sourcing — storing domain events like `MoneyDeposited`, `MoneyWithdrawn` and reconstructing balance by replaying the event stream — is architecturally aligned with what we are building. It will be the natural evolution for the extracted Ledger microservice in Step 7.

But at Step 0, it is disproportionate: event store, snapshots, projections, eventual consistency, CQRS infrastructure — this is the cost of pure event sourcing, and the system does not yet need it. The double-entry ledger with a denormalized balance cache is the practical middle ground: full auditability, reconstructibility after incident, without the operational overhead of a full event sourcing stack.

The balance cache (`balance_amount`) is the concession to read performance. Every balance read is O(1) against the accounts table. The trade-off: there are now two sources of truth, and the system must keep them in sync. The invariant tests (`FinancialInvariantsDbTest`, `MoneyIntegrityE2ETest`) are the mechanism by which this synchronization is continuously verified.

---

## Chapter 5 — Authentication: The Passwordless OTP Flow

### The Design

Kora Core uses a passwordless registration flow that reflects mobile money product norms in the CEMAC region:

1. `POST /auth/register` — user provides email and phone number; server generates a 6-digit OTP, stores it in the OTP store with a 5-minute TTL, sends it via email
2. `POST /auth/verify` — user provides email, OTP, and PIN; if OTP is valid, the system creates User + Customer + Account atomically and returns JWT tokens
3. `POST /auth/login` — same OTP flow for returning users

PIN replaces password. It is hashed with BCrypt (not Argon2 as originally designed — the implementation uses BCrypt, which is standard for Spring Security). It is never stored in plain text, never logged, never transmitted after the initial `verify` call.

### OTP Generation — Domain Logic

A subtle but important refactoring happened during the quality consolidation phase (`[#step-1] refactor: enforce clean boundaries`): OTP generation was moved from `AuthService` into the `Otp` value object itself:

```java
public record Otp(String code, Duration ttl, Instant createdAt) {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static Otp generate(Duration ttl, Clock clock) {
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        return new Otp(code, ttl, Instant.now(clock));
    }

    public boolean isExpired(Clock clock) {
        return Instant.now(clock).isAfter(createdAt.plus(ttl));
    }
}
```

Why does this matter? Because OTP generation is business behavior, not infrastructure plumbing. The domain knows what an OTP is, how long it lives, and how to generate one securely. Keeping that logic in the service layer was a violation of the domain's responsibility boundary. Moving it to the value object makes the `Otp` type self-sufficient and makes tests trivially easy — `Otp.generate(Duration.ofMinutes(5), Clock.fixed(...))` is all you need.

### Mail — Subject Belongs to the Caller

Another clean boundary fix: the `MailPort` interface originally took an `OtpMailContext` enum so the SMTP adapter could choose the subject line:

```java
// Old — adapter had to know context semantics
void sendOtp(String toEmail, String otpCode, OtpMailContext context);

// New — adapter sends what it receives
void sendOtp(String toEmail, String otpCode, String subject);
```

The application service that calls `sendOtp` knows the context perfectly — it is calling this as part of `register()` or `login()`. The SMTP adapter's job is to deliver mail. The subject is the caller's concern. Giving that responsibility to the adapter required the `OtpMailContext` enum, which then had no other use in the codebase. The enum was deleted.

### JWT — Short-Lived Access, Long-Lived Refresh

Access tokens expire in 15 minutes. Refresh tokens expire in 7 days. Both are signed with a configurable secret (`kora.security.jwt.secret`) with a 32-character minimum enforced in configuration. The short access token lifetime is appropriate for financial applications: a stolen access token has a 15-minute window, not a session-long one.

---

## Chapter 6 — The Payment State Machine: From Four States to Eleven

This is where the system's complexity gradient truly begins.

### Step 0 — The Minimal Machine

The original state machine at Step 0 was:

```
INITIALIZED → PENDING → COMPLETED
                      → FAILED
```

Sufficient for 5,000 tx/day on a single instance with stub providers. Completely wrong for a real payment system.

The problem is not that these states are incorrect. The problem is that they are *underdifferentiated*. When a support agent asks "the customer was debited but the merchant received nothing," this state machine cannot answer. `COMPLETED` conflates "funds left the source account" with "funds arrived at the destination" — two events that, in an interbank settlement system, are separated by up to 24 hours.

### The State Pattern — Making Transitions Compile

The state machine is implemented using the State pattern. `TransactionState` is an interface:

```java
public interface TransactionState {
    TransactionState INITIALIZED    = new InitializedState();
    TransactionState AUTHORIZED     = new AuthorizedState();
    TransactionState CAPTURED       = new CapturedState();
    TransactionState SETTLEMENT_PENDING = new SettlementPendingState();
    TransactionState SETTLED        = new SettledState();
    TransactionState COMPLETED      = new CompletedState();
    TransactionState FAILED         = new FailedState();
    TransactionState AUTHORIZATION_FAILED  = new AuthorizationFailedState();
    TransactionState CAPTURE_FAILED        = new CaptureFailedState();
    TransactionState SETTLEMENT_FAILED     = new SettlementFailedState();
    TransactionState REVERSED       = new ReversedState();

    TransactionState transitionTo(TransactionState next);
    String name();
    boolean isTerminal();
}
```

Each state class implements `transitionTo()` to permit only the transitions legal from that state. An `InitializedState.transitionTo(COMPLETED)` does not silently return `COMPLETED` — it throws `InvalidStateTransitionException`. There is no `switch` statement to forget to update when a new state is added. Each state is responsible for its own transition table.

```java
// InitializedState.java
@Override
public TransactionState transitionTo(TransactionState next) {
    if (next == AUTHORIZED || next == FAILED || next == AUTHORIZATION_FAILED)
        return next;
    throw new InvalidStateTransitionException(this, next);
}

// CompletedState.java
@Override
public TransactionState transitionTo(TransactionState next) {
    throw new InvalidStateTransitionException(this, next); // terminal
}
```

Terminal states (`COMPLETED`, `FAILED`, `AUTHORIZATION_FAILED`, `CAPTURE_FAILED`, `SETTLEMENT_FAILED`, `REVERSED`) throw on any `transitionTo()` call. Once a transaction is in a terminal state, it cannot move. This is not a business rule enforced by a guard condition — it is a structural property of the type.

### Step 1 — The Full Lifecycle

The complete state machine after ADR-002:

**Happy path:**
```
INITIALIZED
    → AUTHORIZED       (provider reserves funds)
    → CAPTURED         (provider debits — ledger entries written HERE)
    → SETTLEMENT_PENDING (funds in transit, interbank)
    → SETTLED          (settlement report matched)
    → COMPLETED        (administrative closure)
```

**Failure branches:**
```
INITIALIZED       → FAILED                (pre-provider failure)
AUTHORIZED        → AUTHORIZATION_FAILED  (provider refused reservation)
AUTHORIZED        → REVERSED              (business reversal before capture)
CAPTURED          → CAPTURE_FAILED        (provider confirmed auth, failed debit)
CAPTURED          → REVERSED              (business reversal post-capture)
SETTLEMENT_PENDING→ SETTLEMENT_FAILED     (settlement mismatch)
```

### The Critical Ledger Implication

Ledger entries are written at **CAPTURED**, not at AUTHORIZED. The rationale is precise: at AUTHORIZED, money is reserved but has not moved. The customer's available balance is reduced, but their real balance is unchanged. No accounting entry is warranted. At CAPTURED, the provider has executed the effective debit — money has left the source account. This is the moment of financial commitment, and this is when the double-entry entries are written.

This distinction is operationally critical for a support team: a transaction stuck at AUTHORIZED means "locked but not moved." A transaction at CAPTURED means "moved, in settlement." Different diagnosis, different resolution.

### Every Transition Produces an Audit Entry

Every state transition — including automated ones triggered by TTL expiry or settlement batch processing — produces an immutable `TrxStateHistoric` entry:

```java
private void transitionTo(TransactionState newState) {
    TransactionState old = this.state;
    this.state = this.state.transitionTo(newState);
    this.history.add(TrxStateHistoric.of(this.transactionId, old, this.state));
}
```

After a full cash-in cycle, the `TrxStateHistoric` table for that transaction will contain:
1. `null → INITIALIZED` (created at transaction instantiation)
2. `INITIALIZED → AUTHORIZED`
3. `AUTHORIZED → CAPTURED`
4. `CAPTURED → SETTLEMENT_PENDING`
5. `SETTLEMENT_PENDING → SETTLED`
6. `SETTLED → COMPLETED`

Six entries, atomically written in TX-2. Six immutable audit records for a single cash-in operation. This is the audit trail that satisfies BCEAO traceability requirements.

---

## Chapter 7 — Infrastructure: Testcontainers and the Real-Database Discipline

The test infrastructure was set up at commit `[#payment] feature: implement repo infrastructures, setup testcontainers, integration tests with isolation`.

### The Spring Boot 4.x Break

Here is the first serious encounter with the Spring Boot 4.x migration cost. The familiar `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` pattern — standard in Spring Boot 3.x for running JPA repository tests against a real database — was removed from the `spring-boot-test-autoconfigure` module in Spring Boot 4.0.

The replacement: `@SpringBootTest` + `@Testcontainers` + `@ServiceConnection`.

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestMailConfig.class)
abstract class AbstractE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    // @BeforeEach: TRUNCATE operations, trx_state_historics,
    //              transactions, accounts, customers, users
}
```

`@ServiceConnection` is a Spring Boot 4 feature: the container's connection details (URL, username, password) are automatically wired into the Spring `DataSource` configuration. No `@DynamicPropertySource`. No manual URL construction. The container starts, Spring Boot reads its connection details, done.

The Testcontainer version needed explicit pinning in `build.gradle`:

```gradle
testImplementation 'org.testcontainers:junit-jupiter:1.20.4'
testImplementation 'org.testcontainers:postgresql:1.20.4'
```

Because Spring Boot 4's BOM does not manage Testcontainers versions (Spring Boot 4 migrated to Testcontainers 2.x, which was pulled in transitively via `spring-boot-testcontainers` — but the explicit module versions still needed pinning to avoid resolution conflicts).

### Test Isolation — TRUNCATE, Not Rollback

E2E tests use `TRUNCATE` before each test, not `@Transactional` rollback. The reason is important: rolling back an E2E test would roll back the HTTP request that the test triggered — which did not happen inside the test's transaction context. The application's `@Transactional` boundaries are fully committed before the test receives the HTTP response. To reset state, you must actually delete the data.

The TRUNCATE order matters due to foreign key constraints:

```sql
TRUNCATE operations, trx_state_historics, authorization_records,
         transactions, accounts, customers, users CASCADE;
```

After TRUNCATE, the `DataInitializer` (which created the float account at startup) has already run — and will not run again. The float account must be manually recreated after truncation:

```java
@BeforeEach
void resetDatabase() {
    jdbcTemplate.execute("TRUNCATE ...");
    // recreate float account because DataInitializer only runs once at startup
    accountRepository.save(floatAccountFor(SystemConstants.PROVIDER_ID));
}
```

This was the source of a non-obvious bug: a test class initially tried to create the float account in its setup, but the `DataInitializer` had already created it at startup — violating the `UNIQUE(resource_type, resource_id)` constraint. The fix was recognizing that `DataInitializer` creates the float account once, TRUNCATE removes it, and the test setup must recreate it.

Integration tests (repository-level) use `@Transactional` and rollback — they operate at a lower level and the Spring transaction context wraps the entire test.

### The OTP Bypass in Tests

Tests need to verify the OTP without sending email. The solution: in tests, the OTP is retrieved directly from `OtpStore`, not from the inbox:

```java
// In AbstractE2ETest
protected String waitAndGetOtpCode(String email) {
    // reads directly from InMemoryOtpStore — no mail required
    return otpStore.get("otp:" + email)
                   .map(Otp::code)
                   .orElseThrow();
}
```

`SmtpMailAdapter` is annotated `@Profile("!test")`. In the test profile, `TestMailConfig` provides an `@Primary InMemoryMailPort` that is a no-op. The OTP is generated, stored in the OTP store, and the adapter does nothing. Tests retrieve the code from the store directly. There is no mock, no spy — the OTP flow is real; only the mail delivery is bypassed.

---

## Chapter 8 — Concurrency: The Lost Update Problem and Its Resolution

At Step 0, the concurrency story was documented honestly as technical debt:

> *The current implementation reads an account balance, applies a delta in memory, and writes back the result. This is a lost update problem under concurrent requests targeting the same account.*

With 5,000 tx/day and ~0.06 tx/sec nominal on a single JVM, the probability of two requests hitting the same account in the same millisecond window was negligible. The double-entry ledger still recorded correct operations even if the denormalized `balance_amount` diverged temporarily. The debt was accepted.

At Step 1, it was not.

### Optimistic Locking — @Version and the Retry Loop

For user accounts, the solution is optimistic locking via the `@Version` field on `VersionedEntity`:

```sql
UPDATE accounts
SET balance_amount = ?, version = 2
WHERE id = ? AND version = 1   -- guards against lost update
```

If two threads both read `version = 1`, both compute a new balance, and both attempt to write, only the first write succeeds. The second hits the WHERE clause on `version = 1`, finds no matching row (it's now `version = 2`), and Hibernate throws `OptimisticLockingFailureException`.

The application layer catches this and retries with exponential backoff + jitter (implemented during the load test debugging phase, commit `[#load-test] fix(concurrent-cash-in): increase retry attempts and add jitter to prevent 503 under high concurrency`):

```java
// Retry configuration
int MAX_RETRY_ATTEMPTS = 5;
long BASE_DELAY_MS = 100;
// Each retry: delay * 2^attempt ± 50% jitter
```

Optimistic locking is appropriate for user accounts because the contention rate per individual account is low at 20-30 req/sec across many accounts. The retry rarely fires.

### Pessimistic Locking — The Float Account Hot Spot

The float account is structurally different. Every cash-in and every cash-out touches it. Under optimistic locking, concurrent cash-in operations on the float account would all read the same `version`, all try to write, and all but one would fail and retry. With 30 concurrent requests, 29 retries per round produces a retry storm — exponentially worse than sequential processing.

The solution: `SELECT ... FOR UPDATE` on the float account row.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM AccountEntity a " +
       "WHERE a.resourceType = 'FLOAT_ACCOUNT' " +
       "AND a.resourceId = :providerId " +
       "AND a.deletedAt IS NULL")
Optional<AccountEntity> findFloatAccountForUpdate(@Param("providerId") String providerId);
```

PostgreSQL acquires an exclusive row lock. Concurrent threads queue behind the lock holder instead of racing and failing. This transforms contention from a retry storm into a serialized queue.

But then a critical insight emerged from the load tests, detailed in ADR-001 and resolved in ADR-004:

**The float account balance is never actually written.** Per ADR-001, `Account.debit()` and `Account.credit()` are no-ops for `FLOAT_ACCOUNT` type — there is no mutable balance to protect. The float account's integrity comes entirely from the immutable ledger entries. The `SELECT FOR UPDATE` on the float account was, architecturally, protecting shared mutable state that did not exist.

Once recognized, the float account pessimistic lock was removed entirely. The concurrency concern for the float account is handled by the immutability of ledger operations, not by row-level locking.

---

## Chapter 9 — The Micro-Transaction Pattern: When the Database Connection Is the Bottleneck

This is the most technically dense decision in the codebase, and the one with the greatest performance impact. It emerged from failure — specifically, from the load test results that showed 73.93% error rate and 60-second p95 latency under sustained 25 req/sec load.

### The Root Cause

The original `PaymentTransactionalExecutor` was annotated `@Transactional` at class level. This meant every payment request held a live database connection for the full duration of the call — including the simulated provider round-trip:

```
authorize: ~800ms + jitter → up to 1,120ms
capture:   ~600ms + jitter → up to 840ms
total provider I/O:         → up to 1,960ms
```

At 25 req/sec with 55% provider-bound operations, Little's Law gives:

```
concurrent connections = 25 × 0.55 × 1.4 ≈ 19.25
```

With a HikariCP pool of 30, nearly two-thirds of the pool was occupied by threads blocked on provider I/O — not doing database work, just holding connections while waiting for network responses. Any burst exhausted the pool. `HikariPool-1 - Connection is not available, request timed out after 30000ms` appeared in every log.

### The Solution: Explicit Transaction Boundaries

The micro-transaction model splits each provider-bound operation into three phases separated by explicit DB transaction boundaries:

```
TX-1 (~10ms)          Provider I/O (~1,400ms)      TX-2 (~20ms)
─────────────          ─────────────────────────    ─────────────
validate payer         authorize(provider)          reload account
check balance          capture(provider)            apply balance
persist INITIALIZED    (zero DB connections)        persist COMPLETED
release connection ────────────────────────────── acquire connection
```

This is implemented using `TransactionTemplate` directly, not `@Transactional`:

```java
// TX-1: validate context, persist INITIALIZED
Tx1Context ctx = Objects.requireNonNull(txTemplate.execute(status -> {
    ValidatedPayer payer = validatePayerNoLock(cmd.customerId());
    Account floatAccount = getSystemFloatAccount();
    Ledger ledger        = ledgerRepository.findFirst();

    Transaction tx = ledger.initiate(floatAccount, payer.account(),
            TransactionType.CASH_IN, cmd.paymentMethod(), cmd.amount());
    persistInitialState(tx);  // save tx + INITIALIZED history entry
    return new Tx1Context(tx, payer.customer().snapshot().phoneNumber());
}));

// Provider I/O — zero DB connections held
AuthorizationResult authResult = provider.authorize(...);
provider.capture(...);

// TX-2: reload with lock, apply balance, persist all state transitions
return txTemplate.execute(status -> {
    Account customerAccount = accountRepository
            .findByCustomerIdForUpdate(cmd.customerId())  // pessimistic lock
            .orElseThrow(...);

    tx.authorize();
    tx.capture();
    ledger.writeEntries(tx, floatAccount, customerAccount, cmd.amount());
    applyBalanceUpdate(floatAccount, customerAccount, cmd.amount());
    tx.pendSettlement();
    tx.settle();
    tx.markCompleted();

    flushHistorySince(tx, 1);  // flush 5 history entries at once
    transactionRepository.save(tx);
    return tx;
});
```

The `flushHistorySince(tx, 1)` call is notable: it saves all history entries accumulated in memory during the provider I/O phase and TX-2 state transitions in a single pass. The domain `Transaction` accumulates all state changes in its internal `history` list. TX-1 saves index 0 (INITIALIZED). TX-2 saves indices 1 through N (all subsequent transitions). This reduces the number of `INSERT` statements on `trx_state_historics` from 6 separate calls to 1 batch pass.

### The Architectural Compromise

Using `TransactionTemplate` — a Spring infrastructure type — directly in the application layer is a hexagonal architecture violation. The application layer should depend only on domain types and port interfaces.

ADR-004 documents this as accepted architectural debt and describes the clean resolution:

```java
// application/port/out/TransactionBoundary.java
@FunctionalInterface
public interface TransactionBoundary {
    <T> T execute(Supplier<T> work);
}
```

The infrastructure provides `SpringTransactionBoundary(TransactionTemplate)`. Unit tests use `work -> work.get()`. The application layer becomes framework-free.

This resolution is tracked as a Step 2 refactor target. The current approach is functionally correct, all tests pass, and the performance problem is solved. The debt is documented and bounded.

### Known Gaps (Explicit, Accepted)

The micro-transaction model introduces failure scenarios that did not exist in the monolithic transaction approach — or rather, makes them explicit that were previously hidden:

| Gap | Scenario | Consequence |
|-----|----------|-------------|
| G-1: Process crash between TX-1 and TX-2 | JVM dies after TX-1 commits | Transaction stays INITIALIZED forever |
| G-2: Provider success + TX-2 failure | Provider captures money, TX-2 DB error | Customer charged, balance not credited |
| G-3: No TX-2 retry | TX-2 throws after provider success | Same as G-2 |

These are not regressions. A process crash mid-`@Transactional` in the monolithic approach had the same consequences. The difference is that the micro-transaction model makes the gaps explicit and provides `AuthorizationRecord` as the audit mechanism: any transaction with a committed `AuthorizationRecord` but no `COMPLETED` state is a candidate for investigation.

Step 3 will introduce a scheduled reaper and idempotency store to close these gaps. For Step 1 at 15,000 tx/day, the risk is accepted and monitored.

---

## Chapter 10 — The API Surface: One Endpoint Wins

### The Client Simplicity Argument

ADR-003 documents the choice between Option A (explicit state transition endpoints per HTTP call) and Option B (single saga endpoint):

```
Option A:
POST /payments/authorize
POST /payments/{id}/capture
POST /payments/{id}/reverse

Option B:
POST /payments → SETTLEMENT_PENDING (or error)
```

Option B was chosen. The mobile client should not implement a state machine in client code. "Pay → success or failure" is the correct mental model for a mobile money user. Intermediate states are internal to the backend.

Concretely: if `authorize` succeeds and `capture` fails, the backend compensates automatically. Placing the capture call on the client creates a class of bugs where users are left with locked balances because a client-side network error interrupted the flow. The backend's `executePaymentSaga` handles this compensation internally.

The step-by-step endpoints remain in the codebase — they are the backbone of the future admin/operator dashboard (`POST /admin/payments/{id}/capture`). The reverse endpoint was specifically moved to `/admin/payments` (`@RequestMapping("/admin/payments")`) during the clean boundary refactoring — admin operations are not customer-facing.

### RBAC at the Router Level

Security configuration separates concerns at the HTTP layer:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/**").permitAll()
    .requestMatchers("/admin/**").hasRole("ADMIN")
    .requestMatchers("/payments/**").hasRole("CUSTOMER")
    .requestMatchers("/actuator/**").permitAll()
    .anyRequest().authenticated()
)
```

Authorization is orthogonal to domain logic. The domain does not know which role triggered a payment — it knows the payer's ID, the amount, and the accounts involved. RBAC is a cross-cutting concern enforced by the security filter chain before the request reaches the application layer.

---

## Chapter 11 — Schema Migration: Flyway and the Step 1 Stable Line

This chapter is about a specific problem that consumed disproportionate debugging time — and the fix that was ultimately simple.

### The Problem: Flyway Not Running in Spring Boot 4.x

The integration of Flyway (`[#step-1] feat: integrate Flyway — V1 initial schema baseline`) started with a seemingly obvious dependency:

```gradle
implementation 'org.flywaydb:flyway-core'
runtimeOnly 'org.flywaydb:flyway-database-postgresql'
```

The application started. Zero Flyway log lines appeared. Tables did not exist. The initial diagnosis was configuration issues — Spring properties, datasource URL, Flyway configuration keys. None of it was the problem.

The real cause: **In Spring Boot 4.x, Flyway auto-configuration was moved out of `spring-boot-autoconfigure` into a separate module**. The bare `flyway-core` dependency no longer triggers auto-configuration. The correct dependency is the starter:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-flyway'
runtimeOnly 'org.flywaydb:flyway-database-postgresql'
```

The `spring-boot-starter-flyway` starter pulls in the auto-configuration module that registers the `FlywayAutoConfiguration` bean. Without it, Flyway is on the classpath but Spring Boot does not know it should configure it.

There was a misleading clue: log lines that looked like Flyway output (`Database JDBC URL`, `Default catalog/schema: test/public`) were actually Hibernate's `org.hibernate.orm.connections.pooling` logger — not Flyway at all. This delayed diagnosis by concealing the absence of Flyway log output.

### V1 — The Initial Schema Baseline

With Flyway running, the initial schema migration (`V1__initial_schema.sql`) became the canonical definition of the database:

```sql
-- Eight tables in dependency order:
-- users → customers
-- ledgers
-- accounts
-- transactions → operations
--             → trx_state_historics
-- authorization_records

CREATE TABLE users (
    id          VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(255) NOT NULL,
    status      VARCHAR(255) NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);
-- ... and 7 more tables
```

Key schema observations:
- `VARCHAR(255)` for all identifiers — UUID strings fit in 36 characters; the 255 ceiling is intentional slack
- `TIMESTAMP WITH TIME ZONE` for all timestamps — timezone-aware storage is mandatory in a system that may serve users across CEMAC time zones and interoperate with providers in different zones
- `NUMERIC(19,4)` for all financial amounts — 15 significant digits before the decimal, 4 after; sufficient for XAF amounts in the billions
- `version BIGINT DEFAULT 0` on `VersionedEntity` tables (`users`, `customers`, `accounts`, `transactions`, `authorization_records`)
- No `version` on `ledgers`, `operations`, `trx_state_historics` — immutable entities do not need optimistic locking

### Test Configuration Change

With Flyway managing the schema, the test configuration changed:

```properties
# Before (Step 0 — Hibernate DDL)
spring.jpa.hibernate.ddl-auto=create-drop

# After (Step 1 — Flyway owns DDL)
spring.jpa.hibernate.ddl-auto=none
```

`ddl-auto=none` means Hibernate never touches DDL. Flyway runs its migrations and creates the schema. Hibernate validates at startup that the schema matches the entity mappings — no surprises. If the schema diverges from the entities (which would indicate a migration was missed), startup fails loudly.

The production configuration similarly moved from `ddl-auto=update` (dangerous in production — Hibernate would silently alter tables) to `ddl-auto=validate` (safe — Hibernate checks but does not touch).

---

## Chapter 12 — Load Testing: The Calibration Story

### The k6 Infrastructure

Load tests are written in k6 (`perf/smoke.js`, `perf/load.js`, `perf/stress.js`, `perf/soak.js`) with a full observability stack: InfluxDB collects metrics, Grafana dashboards visualize them (k6 community dashboard ID 2587).

The test suite covers four scenarios:
- **Smoke**: 1 VU, 2 minutes — sanity check, catches obvious breakage
- **Load**: `ramping-arrival-rate` to 25 req/sec, 5 minutes — steady state validation
- **Stress**: stages to 100 VUs — finds the breaking point
- **Soak**: 6 req/sec for 15 minutes — detects memory leaks and performance drift

### The Mix

The realistic business operation mix:
- Cash-in: 40%
- Transfer: 35%
- Cash-out: 15%
- Balance check: 10%

This means 55% of operations involve provider I/O — the most expensive path.

### Threshold Calibration (ADR-005)

The initial thresholds were wrong in an instructive way. `p(95)<200ms` was written for an application without provider latency in the test profile. When realistic provider latency was added:

```properties
# application-perf.properties
kora.provider.latency.authorize-ms=800
kora.provider.latency.capture-ms=600
```

The theoretical p95 ceiling for cash-in/cash-out operations:

```
authorize (max 40% jitter): 800 × 1.4 = 1,120ms
capture   (max 40% jitter): 600 × 1.4 =   840ms
overhead (TX-1/TX-2/network):          ≈   100ms
─────────────────────────────────────────────────
Ceiling                               ≈ 2,060ms
```

A threshold of `p(95)<200ms` is physically impossible when provider I/O alone takes 1,400ms. The threshold was not detecting application regressions — it was failing because the system was working correctly.

The corrected thresholds in ADR-005 separate what the application controls from what the provider controls:

```
http_req_duration{operation:balance}   p(95)<100ms   ← pure DB read, no I/O
http_req_duration{operation:transfer}  p(95)<200ms   ← single TX, no I/O
http_req_duration{operation:cash}      p(95)<2500ms  ← provider I/O included
```

The global `p(95)<2500ms` calibrated at 370ms above the measured provider ceiling: tight enough to catch genuine application degradation (pool exhaustion, lock contention, DB timeout), wide enough to not fire under normal provider variability.

### The Seed Problem

A non-obvious issue in the load test setup: the business mix was 40% cash-in, but if the first operation a virtual user encounters is a cash-out (15% probability per iteration), their balance is 0 and the operation fails with `InsufficientFundsException`.

The fix: every test user receives a `seedCashIn` of 100,000 XAF immediately after authentication, before entering the VU pool. The seed covers the worst case: 20 consecutive cash-outs of 5,000 XAF each. After the seed, the net balance effect per iteration is positive (more cash-in than cash-out in the mix), so the balance grows indefinitely. The seed only matters for the initial burst.

---

## Chapter 13 — Primitive Obsession: The Types the Domain Was Missing

Chapter 2 established the discipline: every domain concept gets a type, and the compact constructor enforces its invariant. That discipline was applied unevenly. Three fields had been left as bare `String` since Step 0, and each one was a business concept wearing a primitive's clothes:

```java
// Before
public record CashInCommand(Id customerId, String rawPin, Amount amount, String paymentMethod) {}

public record TrxStateHistoric(
        ...,
        String correlationId,
        String providerRef,
        String actorId,
        String notes) {}
```

`paymentMethod` is a closed set of provider channels, not free text. `correlationId` and `actorId` are business identifiers — the same kind of thing as `transactionId` and `customerId`, which were `Id` from the first commit. Nothing prevented `new CashInCommand(..., "banana")` from compiling, and nothing prevented an `actorId` and a `providerRef` from being swapped at a call site.

### PaymentMethod — Two Names for the Same Thing

```java
public enum PaymentMethod {
    CARD("CARD"),
    ORANGE_MONEY("OM"),
    MOBILE_MONEY("MOMO"),
    WALLET("WALLET");

    private final String value;
    // ...
}
```

The enum carries two representations: the constant name is the business name used in the API and the database, and `value()` is the short code used on provider wire protocols. This duality is real — `OM` is what an Orange Money endpoint expects — but it is also the source of the first bug the refactoring produced.

The original `fromValue` was `valueOf(value.toUpperCase())`. That resolves the *name*, never the *value*. So `PaymentMethod.ORANGE_MONEY.value()` produced `"OM"`, and `PaymentMethod.fromValue("OM")` threw `IllegalArgumentException`. Any code path that wrote the short code and read it back would fail — silently correct in tests that only used `WALLET` and `CARD` (where name and value coincide), broken for every Orange Money and MTN transaction.

The fix resolves against both representations:

```java
public static PaymentMethod fromValue(String value) {
    if (value == null || value.isBlank())
        throw new IllegalArgumentException("Error, cannot accept empty payment method !");
    String normalized = value.trim().toUpperCase();
    for (PaymentMethod method : values()) {
        if (method.name().equals(normalized) || method.value.equals(normalized))
            return method;
    }
    throw new IllegalArgumentException("Unknown payment method: " + value);
}
```

The lesson generalizes: when a type has two external representations, every parse must accept both, or the pair is not a round trip. A `toX`/`fromX` pair that does not satisfy `fromX(toX(v)) == v` for all `v` is not a serialization — it is a trap that fires on the values nobody tested.

For persistence, `TransactionEntity.paymentMethod` became `@Enumerated(EnumType.STRING)`, matching the `type` field directly above it:

```java
@Enumerated(EnumType.STRING)
@Column(name = "type", nullable = false)
private TransactionType type;

@Enumerated(EnumType.STRING)
@Column(name = "payment_method", nullable = false)
private PaymentMethod paymentMethod;
```

This stores the constant name, which is exactly what the pre-refactoring free-text column already contained (`ORANGE_MONEY`, `WALLET`) — so no data migration was required, and `V1__initial_schema.sql`'s `payment_method VARCHAR(255) NOT NULL` is unchanged. Hibernate now rejects an unmappable value at read time instead of propagating a bad string into the domain.

### Id for correlationId and actorId — and the Nullability Trap

Replacing `String correlationId` and `String actorId` with `Id` is the obvious move. It is also where the refactoring nearly introduced three production NPEs, and the reason is worth stating precisely.

`Id`'s compact constructor rejects null and blank. That guarantees *an `Id` instance is never empty*. It does not guarantee *a field of type `Id` is never null* — and both of these fields are legitimately absent most of the time. `TrxStateHistoric` has two factories:

```java
// Internal transitions — no operator, no external correlation
public static TrxStateHistoric of(Id transactionId, TransactionState oldState, TransactionState newState) {
    return new TrxStateHistoric(Id.generate(), transactionId, oldState, newState, Instant.now(),
            null, null, null, null, null);
}

// Operator or provider-driven transitions — fully attributed
public static TrxStateHistoric of(Id transactionId, ..., Id correlationId, String providerRef, Id actorId, String notes)
```

The three-argument factory is the one the state machine calls on every transition — it is the overwhelming majority of rows in `trx_state_historics`. Six of the six entries a cash-in produces (Chapter 6) come from it, all with null correlation and null actor. Only operator reversals and the TTL expiry job use the enriched form.

So `correlationId.value()` and `actorId.value()` — which had replaced the previous direct field reads — would have thrown on every ordinary state transition, at three sites:

| Site | Failure |
|------|---------|
| `TrxStateHistoric.snapshot()` | NPE on every internal transition |
| `JpaTrxHistoricStatesRepository.save()` | NPE writing any non-operator history row |
| `JpaTransactionRepository.toDomain()` | `new Id(null)` → `IllegalArgumentException` reading history back |

All three are now null-safe, in the style the surrounding mappers already used for `oldState` and `triggeredBy`:

```java
.correlationId(historic.correlationId() != null ? historic.correlationId().value() : null)
...
h.getCorrelationId() != null ? new Id(h.getCorrelationId()) : null,
```

The general principle: a value object's constructor validates the *value*, not the *field*. Optionality is a property of the field, and introducing a validating type does not model it. The two concerns have to be handled separately — the constructor for "what a valid instance looks like", the mapper for "whether there is an instance at all".

### Conversion at the Boundary, Not in the Type

The HTTP request records keep `String` and convert in `toCommand()`:

```java
public record CashInRequest(..., @NotBlank(message = "Payment method is required") String paymentMethod) {
    public CashInCommand toCommand(Id customerId) {
        return new CashInCommand(customerId, rawPin, new Amount(amount, currency),
                PaymentMethod.fromValue(paymentMethod));
    }
}
```

This mirrors what `HistoryAction` already did for `TransactionType` on query parameters, and it keeps the failure mode controlled. A typed field would delegate parsing to Jackson and surface a deserialization error; `fromValue` produces `Unknown payment method: X`, which `GlobalExceptionHandler` already maps to a 400 via its `IllegalArgumentException` handler. The wire contract does not change — clients and the k6 suite keep sending `"ORANGE_MONEY"` — and the domain receives a value that cannot be wrong.

`ReverseRequest` follows the same shape, wrapping `actorId` and `correlationId` in `Id` after `@NotBlank` has done its work.

### A Dependency That Pointed the Wrong Way

The same pass had temporarily moved `PageRequest`, `PageResult`, and `TransactionFilter` from `domain/query/` into `application/query/`. That compiled — Java enforces no layering — but it inverted a dependency: `domain/port/TransactionRepository` is a domain port, and it takes all three as parameters. The domain was importing from the application layer, which is precisely what CLAUDE.md forbids and what the port interfaces exist to prevent.

They were moved back to `domain/query/`. A port's signature may only mention domain types; if a query object appears in a port, it belongs to the domain by construction.

`TransactionSummary` stayed in `application/query/`, and the distinction is the useful part: it is a read model shaped for a specific use case — it carries a computed `Direction`, a masked counterpart phone number, and a flattened state history. No port references it. It is application output, not a domain concept.

This is a holding position rather than a resolution. The read and write paths are still sharing a single `TransactionRepository`, which is why a pagination type has to live in the domain at all. Step 5 splits them into separate read and write repositories under CQRS, at which point the query objects follow the read side and this question dissolves.

---

## Epilogue — The Current State

### What Exists

As of Step 1, Kora Core is a production-grade wallet backend with:

- **381 tests across 3 layers**: unit (domain VOs, domain models, application services with in-memory collaborators), integration (repository adapters against Testcontainers PostgreSQL), E2E (full HTTP flows against the complete Spring context with Testcontainers)
- **A strict double-entry ledger** with invariant verification at the domain layer and SQL-level validation in `FinancialInvariantsDbTest` and `MoneyIntegrityE2ETest`
- **An 11-state payment lifecycle** enforced by the State pattern, with `InvalidStateTransitionException` on any illegal transition and immutable audit entries on every transition
- **Optimistic locking** on mutable entities via `VersionedEntity`, with retry logic for concurrent access
- **A micro-transaction model** that holds database connections for 10-20ms instead of 1,400ms during provider I/O
- **Flyway-managed schema** with `V1__initial_schema.sql` as the baseline — a single source of truth for the database structure
- **A k6 load test suite** with Grafana observability, calibrated thresholds, and deterministic test data seeding
- **Clean architectural boundaries**: domain layer has zero Spring imports and zero imports from the application layer, port interfaces are the sole dependency inversion points, `VersionedEntity` encodes the mutability contract structurally
- **No primitive obsession on the payment path**: `PaymentMethod` is a closed enum, `correlationId` and `actorId` are `Id` value objects, and string-to-type conversion happens once at the HTTP boundary

### What Is Not Yet Done

Explicitly, by design:

- **Real provider integration**: `MobileMoneyProviderAdapter` is a configurable stub. Real HTTP clients with circuit breakers are a Step 3 concern
- **Redis OtpStore**: `InMemoryOtpStoreAdapter` is a sufficient single-instance implementation. Multi-instance deployment requires Redis — a Step 2 operational concern
- **Automated settlement**: `SETTLEMENT_PENDING → SETTLED` is triggered manually. The automated reconciliation engine that ingests provider CSV reports is Step 6
- **Idempotency store**: duplicate client retries are a known gap. Idempotency key table with unique constraint is a Step 3 safety mechanism
- **`TransactionBoundary` port**: the Spring infrastructure violation in the application layer is documented and bounded. Clean resolution in Step 2

### The Gradient

This is the essential shape of how the system will evolve, from the ROADMAP:

| Step | Volume Target | Architecture Unlock |
|------|--------------|---------------------|
| 0 | 5,000 tx/day (stub providers) | Domain model, double-entry ledger, TDD baseline |
| 1 | 15,000 tx/day (20-30 req/sec) | Full lifecycle state machine, optimistic locking, micro-transaction |
| 2 | 50,000 tx/day | Real provider integration, circuit breaker, idempotency |
| 3 | 150,000 tx/day | Async provider callbacks, outbox pattern, recovery jobs |
| 5 | 500,000 tx/day | Hexagonal completeness, CQRS for read path |
| 7 | 2M tx/day | Ledger microservice extraction, event sourcing |
| 10 | 10M tx/day | Full distributed system, global settlement |

---

## Technical Appendix — Architecture Decision Record Index

| ADR | Title | Date | Key Decision |
|-----|-------|------|-------------|
| ADR-001 | Immutable Double-Entry Ledger | 2026-03-14 | Double-entry ledger as source of truth; `balance_amount` as read cache only; float account design |
| ADR-002 | Payment Lifecycle & State Machine | 2026-03-18 | 11-state machine; `VersionedEntity` mutability contract; pessimistic vs. optimistic locking strategy |
| ADR-003 | Single-Call Payment API Design | 2026-04-13 | Single saga endpoint for mobile clients; internal step-by-step endpoints retained for operator tooling |
| ADR-004 | Micro-Transaction Model | 2026-05-24 | TX-1 / provider I/O / TX-2 split; float account lock elimination; `TransactionTemplate` as accepted debt |
| ADR-005 | Load Test Calibration | 2026-05-24 | Per-operation-type thresholds; threshold calibration above provider ceiling; deterministic VU seeding |

---

## Closing Note

There is a specific sentence worth quoting from ADR-001, written early in this project:

> *"The double-entry invariant holds across the full lifecycle of every transaction, including failures."*

That sentence is the entire purpose of the design. Not "the happy path is correct." Not "the error cases return the right HTTP status codes." But: the financial invariant holds under every condition the system can encounter — provider failure, concurrent requests, process crash, partial commit, operator reversal.

This is what it means to build a fintech system from first principles. Not the framework. Not the deployment. Not the observability. The invariant that must be true, always, and the layers of enforcement — domain, application, database, test — that make "always" mean something.

The code is a consequence of the invariant. The architecture is a consequence of the code. The load tests are a consequence of the architecture. Everything traces back to one equation:

```
SUM(DEBIT operations) == SUM(CREDIT operations)
```

Build outward from that, and the rest follows.

---

*Living document. Updated at each architecture milestone.*  
*[github.com/Geekers-Joel237](https://github.com/Geekers-Joel237) · [geekersjoel237.substack.com](https://geekersjoel237.substack.com)*
