# Engineering Standards & Methodology

This project is intentionally built as a **fintech-grade backend** playground: correctness first, then resilience, then scale.
The goal is to apply modern Java/Spring engineering practices **rigorously**, with production-like constraints.

---

## 1) Target Stack (Non-negotiable)

### Language & Runtime
- **Java 21+**
- JVM tooling: JFR / async-profiler (later stages)

### Frameworks
- **Spring Boot 4.0.3** (Spring Framework 7)
- Spring Web MVC (REST)
- Spring Validation
- Spring Data JPA for writes, `NamedParameterJdbcTemplate` for reads
- Spring Security — in place: JWT, refresh tokens, `ROLE_CUSTOMER` / `ROLE_ADMIN`

### Data & Messaging
- PostgreSQL (preferred) or MySQL
- Redis (later stage: caching, rate limiting)
- Kafka (later stage: outbox + event-driven)
- (Optional) Debezium for CDC experiments

### Observability
- Micrometer
- Prometheus + Grafana
- OpenTelemetry (later stage: traces)

### DevOps / Cloud
- Docker
- Docker Compose (local dependencies)
- Terraform (infra as code)
- AWS (staged adoption):
    - IAM, VPC
    - RDS
    - ECS or EKS
    - CloudWatch
    - (Optional) MSK for Kafka

---

## 2) Methodology: How We Work

### Core Principles
- **Correctness > Performance** (until proven otherwise by measurements)
- **Make state explicit** (no hidden state transitions)
- **Design for failure** (timeouts, retries, partial success)
- **Evidence-driven decisions** (benchmarks + metrics, not opinions)

### Definition of Done (DoD) for every change
A task is done only if:
- ✅ Tests added (unit + relevant integration)
- ✅ Domain rules captured as invariants
- ✅ No business logic in controllers/adapters
- ✅ Docs updated if behavior changes (README/docs/)
- ✅ Lint/format checks pass
- ✅ Observability added if endpoint/process affects money movement

---

## 3) Testing Strategy (TDD + 3 Levels)

We use **TDD** with three test layers. The goal is not “high coverage”, but **high confidence**.

### 3.1 Unit Tests (Fast, deterministic)
**Scope**
- Domain model (entities, value objects)
- Domain services
- Interactors, driven by hand-written in-memory adapters

**Rules**
- No Spring context
- No DB
- No network
- Execution time: seconds
- **No mocking framework.** There is no Mockito on the classpath. A port is faked by
  a real in-memory implementation in `<module>/unit/doubles/`, which can be asserted
  against and reused. A double that answers differently from the thing it replaces is
  the bug a mock cannot show you — it has happened here twice.

**Examples**
- Ledger invariants: `sum(debits) == sum(credits)`
- Payment lifecycle transitions are valid/invalid
- Which direction a transfer reads as, from each party's side

---

### 3.2 Integration Tests (Real infrastructure, limited scope)
**Scope**
- Repository adapters and the JDBC read adapters
- DB constraints, indexes, transaction isolation behavior

**Rules**
- Real DB (Testcontainers)
- Spring context allowed but minimal
- Verify SQL queries and transaction boundaries
- **No `@DataJpaTest`, no `@AutoConfigureTestDatabase`** — both were removed in
  Spring Boot 4. Extend `AbstractRepositoryTest`: `@SpringBootTest` + `@Transactional`
  + a static `PostgreSQLContainer` wired through `@DynamicPropertySource`.
- No HTTP at this level; that is e2e, and `TestPyramidTest` asserts it

**Examples**
- Verify ledger entries persist atomically with the transaction record
- Verify optimistic locking behaviour under contention
- Verify a history page's SQL scopes, filters and folds its joins correctly

---

### 3.3 End-to-End (E2E) Tests (System-level confidence)
**Scope**
- Full app + DB + messaging (when introduced)
- HTTP boundary and full flow

**Rules**
- Use Testcontainers for dependencies
- Test the "real" business flows (not every edge case)
- Run in CI (can be slower)

**Examples**
- `POST /payments/cash-in` walks INITIALIZED → AUTHORIZED → CAPTURED → … → COMPLETED
- A provider refusal leaves the transaction in `AUTHORIZATION_FAILED` and the balance
  untouched
- `POST /payments/transfer` moves money between two wallets with the ledger balanced
  at every step

Idempotency is **not** covered here yet — there is no idempotency store (Étape 4 of
`ROADMAP.md`). A client retry currently creates a second `INITIALIZED` transaction.

---

## 4) SOLID & Advanced OOP (Deep OOP)

### SOLID
- **S**: Single responsibility per class/use case
- **O**: Extend via composition, not modification
- **L**: Substitution respected (no “fake implementations”)
- **I**: Small ports (interfaces) aligned with use cases
- **D**: Dependency inversion enforced (domain knows no frameworks)

### Deep OOP (Java 21)
We use Java 21 features where they improve modeling:
- `record` for immutable DTOs/value types
- sealed types for closed hierarchies (e.g., transaction types)
- pattern matching for `switch`
- explicit invariants in constructors / factories
- immutability by default in domain

**Rule:** If a class has no invariants, it might be a value type (`record`) or should be removed.

---

## 5) Domain-Driven Design (DDD)

### Why DDD here?
Fintech systems require:
- explicit business rules
- auditability
- correctness under failure

### DDD Building Blocks

The vocabulary below is the one in the code. `CLAUDE.md` §5 is the authoritative table;
`Order`, `PaymentPart`, `Money`, `ProviderReference` and `IdempotencyKey` are names this
project deliberately does **not** use.

- **Modules** (today): `auth`, `payment`, `shared` — the kernel. Risk and reconciliation
  arrive with Étapes 6 and 8.
- **Aggregates**:
    - `Transaction` — the money movement, aggregate root; owns its ledger entries,
      its state and its history
    - `Account` — the balance holder
    - `Ledger` — a domain service, not an aggregate: it creates transactions and
      enforces the double-entry invariant
- **Value Objects**:
    - `Amount(BigDecimal value, String currency)` — the money type
    - `Id`, `Msisdn`, `Pin` in the kernel; `PhoneNumber`, `OtpCode` in auth
- **Domain Events** (later stage, Étape 5):
    - not implemented; no event type exists yet

### DDD Rules
- Business logic belongs in the domain (not in controllers)
- Invariants must be enforced at aggregate boundaries
- State transitions must be explicit and validated

---

## 6) Design Patterns (Pragmatic, Context-driven)

We use patterns only when they remove real complexity:

### Core patterns used
- **State Machine** (Payment lifecycle)
- **Strategy** (provider selection / routing)
- **Factory** (constructing domain objects safely)
- **Repository** (persistence boundary)
- **Adapter** (provider integrations)
- **Command Handler** (use cases)

### Fintech/DDD-aligned patterns
- **Outbox Pattern** (DB + events consistency)
- **Idempotency** (request de-duplication)
- **Saga / Process Manager** (later, orchestration across steps)
- **Anti-Corruption Layer** (provider API boundary)

---

## 7) Architecture: Progressive Clean Architecture / Hexagonal

We evolve architecture only when justified.

### Phase A — Monolith (strict separation) — done
- Domain + application logic separated from the web layer

### Phase B — Hexagonal — done
- **Domain**: pure, JDK only
- **Application**: interactors, commands, queries — no foreign type at all
- **Ports**: `ports/in` driving, `ports/out` driven
- **Adapters**: `adapters/in` (REST, bus), `adapters/out` (JPA, JDBC, provider, mail)
- **Config**: the composition root, and the only place Spring wires anything

### Phase C — Modular Monolith — done
- `auth`, `payment`, `shared`; the kernel names no module

**Phases B and C are in this order on purpose**, against the original roadmap. Drawing
module boundaries over an unclear layering freezes the framework dependency into the
module contract, and unpicking it afterwards means moving every file twice. See
ADR-007.

There is no `infrastructure/` package and no `web/` package. Both were replaced by
`adapters/out` and `adapters/in`, which name the direction rather than the technology.

**Rule:** No Spring annotations in the domain model — and none in `application/` or
`ports/` either, asserted by an allow-list that is currently empty.

---

## 8) DevOps Standards

### Local Environment
- `docker-compose.yml` plus `.override.yml` and `.prod.yml`; services are selected by
  `COMPOSE_PROFILES`, not by commenting blocks out (ADR-006, `CONTRIBUTING.md` §3.2)
- bootstrap: `docker compose up -d` then `./gradlew bootRun`. There is no Makefile.

### CI/CD (baseline)
Pipeline must include:
- build + unit tests
- integration tests (Testcontainers)
- E2E tests (optional per stage)
- static analysis + formatting
- dependency scanning (later)

### Delivery discipline
- small PRs
- meaningful commits
- changelog entries for behavior changes

---

## 9) AWS Adoption (Progressive)

We adopt AWS based on roadmap stages:

### Early (single service)
- RDS
- IAM minimal privileges
- VPC basics
- CloudWatch logs

### Mid (scaling + reliability)
- ECS or EKS (choose one)
- ALB
- autoscaling
- secrets manager

### Advanced (distributed)
- MSK (Kafka) or managed alternatives
- multi-AZ design
- DR strategy and backups
- cost optimization

**Rule:** Cloud changes require cost and reliability justification.

---

## 10) Quality Gates (Rigor)

Every stage should produce **evidence**:
- Performance measurements (k6/Gatling)
- DB QPS and query plans (EXPLAIN)
- P95/P99 latency tracked
- Retry/timeout strategy documented

---

## 11) Project Conventions

### Naming
- Use the business names this project actually uses: `Transaction`, `LedgerEntry`,
  `Account`, `Ledger`, `AuthorizationRecord`. `CLAUDE.md` §5 is the reference.
- Avoid technical names in domain types (no “Entity”, “DTO”). `*Entity` is reserved
  for the JPA adapter, where it is accurate.

### Error Handling
- Domain errors are explicit (typed)
- Infrastructure errors are mapped at boundaries
- No “catch Exception and ignore”

### Logging
- Correlation ID propagated end-to-end
- Structured logs for money movement
- Never log sensitive data

---

## 12) Expected Outcomes (What you should be able to explain)

By progressing through this project, you should be able to answer:
- Why ledgers are immutable, and why the balance here is a **denormalized cache**
  written in the same transaction as the entries rather than derived on read — the
  trade-off, and what makes the entries the source of truth anyway (ADR-001)
- How to design idempotency under retries/timeouts
- How to handle partial failures without breaking trust
- How to reconcile internal vs provider statements
- How to introduce events without dual-write bugs
- When microservices are justified (and when they aren’t)
- How to scale while preserving financial correctness