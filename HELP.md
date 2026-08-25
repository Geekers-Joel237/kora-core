# Engineering Standards & Methodology

This project is intentionally built as a **fintech-grade backend** playground: correctness first, then resilience, then scale.
The goal is to apply modern Java/Spring engineering practices **rigorously**, with production-like constraints.

---

## 1) Target Stack (Non-negotiable)

### Language & Runtime
- **Java 21+**
- JVM tooling: JFR / async-profiler (later stages)

### Frameworks
- **Spring Boot 3.x**
- Spring Web (REST)
- Spring Validation
- Spring Data JPA (or JDBC where appropriate)
- Spring Security (later stage, when auth/risk is introduced)

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
- Application services/use cases (with ports mocked)

**Rules**
- No Spring context
- No DB
- No network
- Execution time: seconds

**Examples**
- Ledger invariants: `sum(debits) == sum(credits)`
- Payment lifecycle transitions are valid/invalid
- Idempotency behavior for same key

---

### 3.2 Integration Tests (Real infrastructure, limited scope)
**Scope**
- Repository adapters
- DB constraints, indexes, transaction isolation behavior
- Outbox insertion logic

**Rules**
- Real DB (Testcontainers)
- Spring context allowed but minimal
- Verify SQL queries and transaction boundaries
- Use `@DataJpaTest` or sliced tests where possible

**Examples**
- Verify unique constraint prevents duplicate idempotency keys
- Verify ledger entries persist atomically with transaction record
- Verify optimistic/pessimistic locking behavior under contention

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
- `/payments` creates an Order + N payment parts and progresses state
- Retry of the same request returns same outcome (idempotency)
- Partial failure then manual retry of one part completes the global order

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
- **Bounded Contexts** (evolves with roadmap):
    - Payments
    - Ledger
    - Risk
    - Reconciliation
- **Aggregates**:
    - `Order` (payment intent) as aggregate root
    - `Ledger` behavior may be modeled via services + invariants
- **Value Objects**:
    - `Money(amount, currency)`
    - `ProviderReference`
    - `IdempotencyKey`
- **Domain Events** (later stage):
    - `PaymentPartSucceeded`
    - `OrderCompleted`
    - `ReconciliationMismatchDetected`

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

### Phase A — Monolith (strict separation)
- Domain + application logic separated from web layer

### Phase B — Modular Monolith
- Clear modules (payments/ledger/risk/recon)
- Explicit boundaries and ownership

### Phase C — Hexagonal
- **Domain**: pure (no Spring imports)
- **Application**: use cases, ports
- **Infrastructure**: adapters (DB, messaging, providers)
- **Web/API**: controllers only

**Rule:** No Spring annotations in domain model.

---

## 8) DevOps Standards

### Local Environment
- `docker-compose.yml` for DB, Redis, Kafka (when needed)
- one command bootstrap: `make up` / `./gradlew bootRun`

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
- Use business names in domain: `Order`, `PaymentPart`, `LedgerEntry`
- Avoid technical names in domain (no “Entity”, “DTO” in domain types)

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
- Why ledgers are immutable and balance is derived
- How to design idempotency under retries/timeouts
- How to handle partial failures without breaking trust
- How to reconcile internal vs provider statements
- How to introduce events without dual-write bugs
- When microservices are justified (and when they aren’t)
- How to scale while preserving financial correctness