# Engineering standards

KORA Core is built as a **fintech-grade backend**: correctness first, then resilience, then
scale. This document is how the code is written — the principles, the definition of done,
the testing strategy, the architecture and the conventions. The rules a reviewer applies to
a diff are in [`CLAUDE.md` §7](../CLAUDE.md#7-review-rules); the vocabulary is in
[`CLAUDE.md` §5](../CLAUDE.md#5-domain-vocabulary).

## Contents

1. [Principles](#1-principles)
2. [Definition of done](#2-definition-of-done)
3. [Testing strategy](#3-testing-strategy)
4. [Architecture](#4-architecture)
5. [Domain-driven design](#5-domain-driven-design)
6. [SOLID and Java 21](#6-solid-and-java-21)
7. [Design patterns](#7-design-patterns)
8. [Code conventions](#8-code-conventions)
9. [API surfaces](#9-api-surfaces)
10. [Evidence and quality gates](#10-evidence-and-quality-gates)
11. [Target platform](#11-target-platform)
12. [What you should be able to explain](#12-what-you-should-be-able-to-explain)

---

## 1. Principles

- **Correctness over performance**, until a measurement proves otherwise.
- **Make state explicit.** No hidden state transition, no `setState()`.
- **Design for failure.** Timeouts, retries, partial success and unknown outcomes are the
  normal case, not the exception.
- **Evidence over opinion.** A decision is justified by a benchmark, a metric or a failing
  test, and its cost is written down — in an [ADR](./adr/README.md) when it is significant.
- **Architecture evolves only when volume and business complexity justify it.** No
  premature microservices, no architectural theatre.

## 2. Definition of done

A change is done only when:

- ✅ **It was written test first**, and its behaviour is covered at the level that owns it —
  a domain rule by a unit test, a query by an integration test, a boundary by a contract test.
- ✅ **Domain rules are captured as invariants**, in the domain, not in an adapter.
- ✅ **No business logic sits in a controller or an adapter.** An `*Action` builds a command,
  delegates and maps the result.
- ✅ **`./gradlew build` is green**: every test level, the architecture tests, the API
  contract, and the coverage gate.
- ✅ **The documentation says what the code does** — README, these standards, `CLAUDE.md` or
  an ADR, whichever the change touches.
- ✅ **Money movement is observable**: an endpoint or a process that moves money logs it with
  its correlation id.

## 3. Testing strategy

The goal is not high coverage but **high confidence**; coverage is the floor that proves the
tests were written, not the measure of their quality. There are four levels. How to run them
and how the gates work is in [development/testing.md](./development/testing.md).

### 3.1 Unit — fast, deterministic

**Scope**: the domain model (entities, value objects, the state machine, the ledger) and the
application services, driven through their driving ports.

**Rules**

- No Spring context, no database, no network; the whole level runs in seconds.
- **Test a behaviour, not a class.** A test goes in through a use case or a domain entry point
  and lets every class concerned take part. Isolating one class behind fakes tests the wiring,
  not the rule.
- **No mocking framework.** There is no Mockito on the classpath. A port is faked by a real
  in-memory implementation in `<module>/unit/doubles/`, which can be asserted against and
  reused. A double that answers differently from the thing it replaces is the bug a mock
  cannot show you — it has happened here twice.
- **The hexagon is held to a floor**: 100 % lines and branches for payment, 90 % for every
  other module.

**Examples**: `SUM(DEBIT) == SUM(CREDIT)`; which transitions the lifecycle accepts; a cash-out
refused at capture releases its hold; an expired authorization gives the held funds back.

### 3.2 Integration — real infrastructure, limited scope

**Scope**: repository adapters, the JDBC read adapters, database constraints and transaction
behaviour.

**Rules**

- A real PostgreSQL through Testcontainers, reached as the application role, never as the
  owner.
- Extend `AbstractRepositoryTest`: `@SpringBootTest` + `@Transactional` + the `KoraPostgres`
  container wired through `@DynamicPropertySource`. **No `@DataJpaTest`, no
  `@AutoConfigureTestDatabase`** — both were removed in Spring Boot 4.
- No HTTP at this level; `TestPyramidTest` asserts it.

**Examples**: ledger entries persist atomically with their transaction; the database refuses
an `UPDATE` on `ledger_entries`; a history page scopes, filters and continues after its cursor.

### 3.3 Contract — the HTTP boundary

**Scope**: the payment journey as a client sees it — status, content type and body.

**Rules**

- The real web layer, security filter chain and exception handlers; the use cases replaced by
  doubles. No database, no server.
- Every case is compared to a committed file, and the published OpenAPI document must declare
  exactly the responses the contracts observe. A boundary changes only through a reviewed diff.

### 3.4 End to end — system-level confidence

**Scope**: the full application and its database over real HTTP.

**Rules**

- Test the real business flows, not every edge case — the edge cases belong to the unit level.
- Isolation by `TRUNCATE`, not by rollback, because the request runs in another thread.

**Examples**: a cash-in walks `INITIALIZED → … → COMPLETED`; a provider refusal leaves the
transaction `AUTHORIZATION_FAILED` and the balance untouched; two concurrent cash-outs cannot
both spend the same funds.

Idempotency is **not** covered yet — there is no idempotency store (Step 4 of
[`ROADMAP.md`](../ROADMAP.md)). A client retry currently creates a second `INITIALIZED`
transaction.

## 4. Architecture

### 4.1 Modules and layers

Three top-level packages — `auth`, `payment`, `shared` — each with the same five layers, and
dependencies pointing inward only:

```
adapters/in → ports/in → application → domain ← ports/out ← adapters/out
                                ↑
                             config wires it all
```

| Layer | Holds | May depend on |
|---|---|---|
| `domain/` | model, state machine, value objects, enums, exceptions | the JDK and `shared/domain` only |
| `application/` | `command/<intent>/` and `query/<intent>/` — the use cases | domain and ports |
| `ports/` | `in` (driving), `out` (driven) | domain and application |
| `adapters/` | `in` (REST, bus, scheduler), `out` (JPA, JDBC, provider, mail) | everything inward |
| `config/` | the composition root | everything |

`application/` and `ports/` name **no foreign type at all** — not Spring, not Jakarta, not a
JSON binding. `HexagonalArchitectureTest` compares every foreign import against an allow-list,
which is empty: adding a type costs a line there and the sentence that justifies it.

There is no `infrastructure/` package and no `web/` package: `adapters/out` and `adapters/in`
name the direction rather than the technology.

### 4.2 The application layer, by intent

The application layer is organised by business intent, command or query side first — CQS
today, CQRS later. An intent folder holds everything the intent is:

```
<module>/application/
├── command/<intent>/   XCommand · XService · XResult (when the command returns one)
└── query/<intent>/     XQuery · XService · XResult · the read model it needs
```

The `*Service` is the application service — the use case — and implements the intent's
driving port in `<module>/ports/in`. Application services are plain classes wired by hand in
`config/`: every use case is instantiable in one line, with no container. `shared/application`
is the kernel — the CQS contracts and the transaction boundary — and is not organised by
intent.

### 4.3 How the architecture got here

| Phase | Status |
|---|---|
| **A — Monolith with strict separation**: domain and application separated from the web layer | ✅ Done |
| **B — Hexagonal**: domain pure, application framework-free, ports, adapters, one composition root | ✅ Done |
| **C — Modular monolith**: each package its own application | 🟡 Open — roadmap Step 2 |

**Do not describe this codebase as a modular monolith.** `auth`, `payment` and `shared` are
packages with an asserted boundary, which is the first half of Phase C and not the phase.

**Landed** — the three packages; the kernel names no module; and whatever `auth` and `payment`
still take from each other is recorded as an **equality** in `ModuleBoundariesTest`, so a new
coupling fails the build and closing one fails it too.

**Not landed, and it is the substance of the phase**:

- **One Gradle project, one `@SpringBootApplication`, one classpath.** Nothing in the build
  stops an import across the boundary — only a test reports it.
- **`auth` and `payment` still share aggregates.** Eight classes cross today: `payment`
  reaches `auth.domain.model.Customer`, `auth` reaches `payment.domain.model.Account`. Closing
  them needs a published `OpenWalletUseCase` and a wallet lookup by phone number.

That recorded list is the measure of how far from done this is: the phase is finished when it
is empty. When it is, Java module rules and ArchUnit take over from the convention.

**Phases B and C are in this order on purpose.** Drawing module boundaries over an unclear
layering freezes the framework dependency into the module contract, and unpicking it means
moving every file twice. See [ADR-007](./adr/ADR-007-hexagonal-before-modular.md).

## 5. Domain-driven design

Fintech systems need explicit business rules, auditability and correctness under failure —
which is what DDD is for here.

The vocabulary below is the one in the code; [`CLAUDE.md` §5](../CLAUDE.md#5-domain-vocabulary)
is the authoritative table. `Order`, `PaymentPart`, `Money`, `ProviderReference` and
`IdempotencyKey` are names this project deliberately does **not** use.

- **Bounded contexts** (today): `auth`, `payment`, and `shared`, the kernel. Risk and
  reconciliation arrive with Steps 6 and 8.
- **Aggregates**
  - `Transaction` — the money movement, aggregate root; owns its ledger entries, its state
    and its history.
  - `Account` — a view on a customer's funds: the balance cache, the reserved amount,
    `available()`.
  - `FundsHold` — funds set aside while a debit awaits the provider.
- **Domain service**: `Ledger` owns everything a transaction does to money — `initiate`,
  `hold`, `post`, `capture`, `release`, `reverse` — and writes the entries and moves the
  accounts in the same call.
- **Value objects**: `Amount(BigDecimal value, String currency)`, the money type; `Id`,
  `PhoneNumber`, `Pin` in the kernel; `OtpCode` and the token types in `auth`.
- **Domain events**: not implemented yet — Step 5, with the transactional outbox.

**Rules**: business logic belongs in the domain; invariants are enforced at the aggregate
boundary; state transitions are explicit and validated.

## 6. SOLID and Java 21

| Principle | Applied as |
|---|---|
| **S** — single responsibility | one use case per application service, one intent per folder |
| **O** — open for extension | extend by composition; a new provider is a new strategy, not a new branch |
| **L** — Liskov substitution | in-memory doubles behave like what they replace — no fake implementations |
| **I** — interface segregation | small ports, one driving port per use case |
| **D** — dependency inversion | the domain and the use cases know no framework |

Java 21 features are used where they improve the model:

- `record` for immutable value types, commands, results and snapshots;
- sealed hierarchies and pattern matching for `switch` over closed sets;
- invariants in compact constructors and factories;
- immutability by default in the domain.

**Rule**: a class with no invariant is a value type (`record`) or should not exist.

## 7. Design patterns

Patterns are used only where they remove real complexity.

| Pattern | Where | Status |
|---|---|---|
| **State machine** | the payment lifecycle — each `*State` validates its own successors | ✅ In place |
| **Strategy** | provider selection — `PaymentProviderRouter` picks the provider of the payment method | ✅ In place |
| **Factory** | `create(...)` and `createFromSnapshot(...)` on every aggregate | ✅ In place |
| **Repository** | the persistence boundary, one port per aggregate | ✅ In place |
| **Adapter** | every integration: JPA, JDBC, provider, SMTP | ✅ In place |
| **Command handler** | the command bus dispatches each command to its one use case | ✅ In place |
| **Idempotency** | request de-duplication under retry storms | Step 4 |
| **Transactional outbox** | database and events kept consistent | Step 5 |
| **Saga / process manager** | orchestration and compensation across steps | Step 5 |
| **Anti-corruption layer** | the boundary with real provider APIs | Step 8 |

## 8. Code conventions

### 8.1 Where code goes

Two questions, in order. First, **which module?** `auth` owns identity, credentials and the
OTP challenge; `payment` owns accounts, the ledger and the provider; `shared` owns what
neither may claim — and `shared` may name neither of the other two.

Then, **which layer?**

- *Does it depend on Spring or any external library?* → `adapters/`
- *Is it a use case?* → `application/command/<intent>/` or `application/query/<intent>/`,
  beside its command or query
- *Is it an interface the use case needs?* → `ports/out/`, or `ports/in` if it is the use
  case's own entry point
- *Is it a business rule?* → `domain/`
- *Is it wiring?* → `config/`

### 8.2 The domain model

- **Snapshots** expose domain state read-only. A repository never returns a mutable object
  someone else holds.
- **Value objects** for every primitive that means something: `Amount`, `Balance`, `Id`,
  `PhoneNumber`, `Pin`, `OtpCode`. Never a raw `BigDecimal`, `String` or `Long` for financial
  or identifying data. Conversion happens once, at the web edge, in the module's MapStruct
  `*RequestMapper`; a request record holds only its fields and their validation.
- **State classes** carry the lifecycle; each validates its own successors. No entity exposes
  `setState()`.
- **Double entry**: every `Transaction` satisfies `SUM(DEBIT) == SUM(CREDIT)` before it is
  persisted; `Transaction.verifyDoubleEntry()` enforces it after every `recordDoubleEntry()`.

### 8.3 Money

Always `BigDecimal`, never `double` or `float`. `Amount` enforces it at the type level, and
refuses to combine two currencies.

```java
Amount amount = Amount.of(new BigDecimal("10000"), "XAF");   // ✅
double amount = 10000.00;                                    // ❌ floating-point error
```

### 8.4 Mapping

MapStruct maps at two boundaries, both in adapters, and nowhere else: request to command on
the way in, snapshot to entity on the way out. An unmapped field fails the build on both. An
update of a managed entity is a named method on the entity taking plain values, never a
mapper; a response is built by hand in `*Response.from(...)`.

### 8.5 Naming

- Use the business names the code uses: `Transaction`, `LedgerEntry`, `Account`, `Ledger`,
  `AuthorizationRecord`, `FundsHold`.
- No technical names in domain types — no `Entity`, no `DTO`. `*Entity` is reserved for the
  JPA adapter, where it is accurate.

### 8.6 Errors

- Domain exceptions extend `BusinessException` and are mapped to HTTP in the module's handler —
  `AuthExceptionHandler`, `PaymentExceptionHandler` — or in `SharedExceptionHandler` for what
  the kernel raises. Every error response is a `ProblemDetail` (RFC 9457) and carries nothing
  sensitive.
- **Throwing inside `TransactionBoundary.execute` is how a use case rolls back.** Catching a
  `BusinessException` silently therefore commits work the domain refused.
- The one legitimate catch **compensates**: `CashInService` catches `PaymentProviderException`
  between phases and writes the failure, because TX-1 has already committed and the
  transaction must not be left dangling. A catch that does not compensate is a defect.

### 8.7 Logging

- The correlation id is born at the HTTP edge and propagated into every log line.
- Never log PII — email, phone number, full name — at any level, nor a secret, a token, an
  OTP code or a PIN. `Pin`, `PhoneNumber`, `OtpCode` and the tokens redact themselves in
  `toString()`.
- `INFO` for business events, `WARN` for recoverable errors, `ERROR` for unexpected
  exceptions only.

## 9. API surfaces

Two surfaces, separated by role in `SecurityConfig`. A role check is a routing concern, never
a domain one.

**Customer** — `ROLE_CUSTOMER`

```
POST /payments/cash-in    POST /payments/cash-out    POST /payments/transfer
GET  /payments/balance    GET  /payments/history
```

Each operation runs its whole lifecycle internally ([ADR-003](./adr/ADR-003-payment-api-design.md));
the client sees the final state only, under contract in
[`api-contract/payments.openapi.json`](../api-contract/payments.openapi.json).

**Operator** — `ROLE_ADMIN`

```
POST /admin/payments/{txId}/reverse
```

That is the whole operator surface today, and it is outside the contract level until its full
journey is revisited. Listing transactions by state — what an operator needs to find a payment
stuck in `UNKNOWN` — arrives with the reconciliation engine (Step 6).

## 10. Evidence and quality gates

Every stage produces evidence, not claims:

- **Automated, on every build**: the four test levels, the architecture tests, the API
  contract, and the coverage floors.
- **Measured, per campaign**: p95 and p99 latency, error rate and throughput under k6
  ([ADR-005](./adr/ADR-005-load-test-calibration.md), [perf/PERF.md](../perf/PERF.md)).
- **Recorded, per decision**: the retry and timeout strategy, query plans (`EXPLAIN`) where a
  query is on the hot path, and the known limits in the
  [state of view](./kora-core-state-of-view-v3.md).

## 11. Target platform

What the project grows into, step by step; what runs today is in the
[README](../README.md#-tech-stack).

| Concern | Today | Later |
|---|---|---|
| Language, runtime | Java 21 | JFR and async-profiler for profiling; **Go** for settlement-file ingestion (Step 7); **Scala** for feature pipelines (Steps 20–22) |
| Framework | Spring Boot 4.0.3 — Web MVC, Validation, Security, Data JPA for writes, `NamedParameterJdbcTemplate` for reads | — |
| Data | PostgreSQL + Flyway | Redis for caching and rate limiting; Kafka with the outbox (Step 5); Debezium for CDC |
| Observability | Micrometer → InfluxDB → Grafana, load campaigns only | OpenTelemetry traces, Prometheus, business KPIs (Step 10) |
| Delivery | Docker Compose for local dependencies | CI pipeline: build, unit, integration, e2e, static analysis, dependency scanning |
| Cloud | — | AWS, staged (Step 9): IAM and VPC, RDS, ECS or EKS behind an ALB, Secrets Manager, CloudWatch; then MSK, multi-AZ, DR and backups. Terraform throughout |

**Rule**: a cloud change comes with its cost and its reliability justification.

## 12. What you should be able to explain

By working through this project, you should be able to answer:

- Why ledgers are immutable, and why the balance here is a **denormalized cache** written in
  the same transaction as the entries rather than derived on read — the trade-off, and what
  keeps the entries the source of truth anyway ([ADR-001](./adr/ADR-001-immutable-ledger.md)).
- Why a provider call never runs inside a database transaction
  ([ADR-004](./adr/ADR-004-micro-transaction-optimization.md)).
- How to design idempotency under retries and timeouts.
- How to handle partial failures, and an unknown outcome, without breaking trust.
- How to reconcile internal records with provider statements.
- How to introduce events without dual-write bugs.
- When microservices are justified, and when they are not.
- How to scale while preserving financial correctness.
