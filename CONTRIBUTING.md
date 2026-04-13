# Contributing to KORA Core

This document covers everything you need to run KORA Core locally, execute the
test suite, and understand the development conventions in use.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Repository structure](#2-repository-structure)
3. [Environment setup](#3-environment-setup)
4. [Running the application](#4-running-the-application)
5. [Running the tests](#5-running-the-tests)
6. [Load testing](#6-load-testing)
7. [Code conventions](#7-code-conventions)
8. [Commit conventions](#8-commit-conventions)
9. [Architecture decisions](#9-architecture-decisions)

---

## 1. Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 21+ | Runtime and compilation |
| Docker Desktop | Latest | PostgreSQL, Redis, MailDev, monitoring stack |
| Gradle | via wrapper (`./gradlew`) | Build, test, run |
| Git | Any | Version control |

No global installation of Gradle or k6 is required. Gradle is wrapped.
k6 runs inside a Docker container — the shell scripts handle everything.

Verify your setup:

```bash
java --version        # must show 21+
docker --version
./gradlew --version
```

---

## 2. Repository structure

```
kora-core/
├── src/
│   ├── main/java/com/geekersjoel237/koracore/
│   │   ├── application/        # Use cases, commands, application services
│   │   ├── domain/             # Entities, value objects, domain events, ports
│   │   ├── infrastructure/     # JPA adapters, security, mail, providers
│   │   └── web/                # REST controllers, DTOs, exception handlers
│   └── test/java/
│       ├── application/        # Unit tests — in-memory repositories
│       ├── domain/             # Domain model and value object tests
│       ├── e2e/                # End-to-end tests — real HTTP + Testcontainers
│       ├── infrastructure/     # JPA repository tests — Testcontainers
│       └── shared/inmemory/    # Shared in-memory test doubles
├── perf/                       # k6 load tests, Grafana dashboards, runbook
├── docs/
│   └── adr/                    # Architecture Decision Records
├── docker-compose.yml          # PostgreSQL, Redis, MailDev, InfluxDB, Grafana
└── CONTRIBUTING.md             # This file
```

### Architecture layers

KORA Core follows Hexagonal Architecture. The dependency rule is strict:

```
web → application → domain ← infrastructure
```

The `domain` package has zero Spring dependencies.
The `application` package depends only on domain interfaces (ports).
Infrastructure implements those ports and is wired by Spring.

---

## 3. Environment setup

### 3.1 Copy the environment file

```bash
cp .env.example .env
```

If `.env.example` does not exist yet, create `.env` at the project root with:

```env
# PostgreSQL
POSTGRES_DB=kora_dev
POSTGRES_USER=kora
POSTGRES_PASSWORD=kora_secret
DB_PORT=5432
DB_ADMIN_PORT=5050

# Redis
CACHE_PORT=6379

# Mail (MailDev)
MAIL_SMTP_PORT=1025
MAIL_UI_PORT=1080

# JWT — change this in any environment where security matters
JWT_SECRET=kora-core-default-secret-key-must-be-32-chars!
```

> **Security note**: `JWT_SECRET` must be at least 32 characters.
> Never commit a real secret. Use a secrets manager in any non-local environment.

### 3.2 Start the infrastructure services

```bash
docker compose up -d postgres redis maildev
```

Wait a few seconds, then verify:

```bash
# PostgreSQL
docker compose ps postgres       # should show "healthy"

# MailDev web UI
open http://localhost:1080        # or curl -s http://localhost:1080
```

### 3.3 Verify the database connection

```bash
./gradlew bootRun --args='--spring.profiles.active=dev' &
curl -s http://localhost:8081/actuator/health | python -m json.tool
# Expected: "status": "UP", "db": { "status": "UP" }, "mail": { "status": "UP" }
```

Stop the background process before running tests:

```bash
pkill -f "KoraCoreApplication" 2>/dev/null || true
```

---

## 4. Running the application

### Default (development)

```bash
docker compose up -d postgres redis maildev
./gradlew bootRun
```

The application starts on **http://localhost:8081**.

Spring Boot DevTools is active in development — the application reloads on
class changes without a full restart.

### Perf profile (required for load tests)

```bash
docker compose up -d postgres redis maildev influxdb grafana
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun
```

The `perf` profile activates:
- `GET /test/otp/{email}` — exposes OTP codes for k6 setup (never in production)
- Micrometer → InfluxDB export every 10 seconds
- Reduced logging to avoid biasing latency measurements

Verify the perf profile is active:

```bash
curl -s "http://localhost:8081/test/otp/anyone%40test.com"
# Returns 404 (no OTP exists yet) — endpoint is reachable = perf profile confirmed
```

### Useful URLs

| URL | Purpose |
|---|---|
| http://localhost:8081 | Application |
| http://localhost:8081/actuator/health | Health check |
| http://localhost:8081/swagger-ui.html | OpenAPI documentation |
| http://localhost:1080 | MailDev — inspect OTP emails |
| http://localhost:5050 | pgAdmin — inspect the database |
| http://localhost:3000 | Grafana (perf profile only) |

---

## 5. Running the tests

All test commands assume the infrastructure services are up
(`docker compose up -d postgres redis maildev`).

### Unit tests (no Docker required)

These tests use in-memory repositories and run without any external dependency:

```bash
./gradlew test --tests "com.geekersjoel237.koracore.application.*"
./gradlew test --tests "com.geekersjoel237.koracore.domain.*"
```

### JPA / repository tests (Testcontainers)

These tests spin up a real PostgreSQL container automatically via Testcontainers.
Docker must be running. No manual configuration required:

```bash
./gradlew test --tests "com.geekersjoel237.koracore.infrastructure.persistence.*"
```

### End-to-end tests (Testcontainers + real HTTP)

Full HTTP stack tests — real Spring Boot server, real PostgreSQL container,
real financial invariant assertions via JDBC:

```bash
./gradlew test --tests "com.geekersjoel237.koracore.e2e.*"
```

### Full test suite

```bash
./gradlew test
```

Test coverage is measured by JaCoCo. The load pipeline enforces a minimum
threshold — check `build.gradle` for the current gate value.

### Running a single test class

```bash
./gradlew test --tests "com.geekersjoel237.koracore.e2e.CashInE2ETest"
./gradlew test --tests "com.geekersjoel237.koracore.e2e.PaymentLifecycleE2ETest"
```

### Test profiles

Tests run with `@ActiveProfiles("test")`, which loads
`src/test/resources/application.properties`. This profile:
- Uses a dedicated JWT secret independent from the perf/dev secret
- Disables real SMTP (`TestMailConfig` replaces `SmtpMailAdapter` with an in-memory stub)
- Sets `ddl-auto=create-drop` so each test class gets a clean schema

---

## 6. Load testing

Load testing has its own dedicated runbook:

**→ [perf/PERF.md](./perf/PERF.md)**

Quick start:

```bash
# Start monitoring + app
docker compose up -d influxdb grafana maildev
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun

# Run the smoke test first (mandatory gate)
./perf/smoke-run.sh

# Then load, stress, soak — in that order
./perf/load-run.sh
./perf/stress-run.sh
./perf/soak-run.sh
```

**Never skip the smoke test.** Each test is a gate to the next.

### Step 0 SLOs

| Metric | Target |
|---|---|
| p95 latency | < 150ms |
| Error rate | < 1% |
| Check rate | > 99% |

---

## 7. Code conventions

### Package structure

Follow the existing hexagonal layers strictly. If you are unsure where something
belongs, ask: *does this depend on Spring or an external library?*

- **Yes** → it belongs in `infrastructure`
- **No, it's a use case** → it belongs in `application`
- **No, it's a business rule** → it belongs in `domain`

The `domain` package must never import anything from Spring, JPA, or any
infrastructure library. This is enforced by the test suite — domain tests
run without Spring context.

### Domain model conventions

- Use **snapshots** to expose domain state read-only.
  Never return mutable domain objects from repositories.
- Use **Value Objects** for all financial primitives: `Amount`, `Balance`, `Id`,
  `PhoneNumber`. Never use raw `BigDecimal`, `String`, or `Long` for financial data.
- Use **sealed state classes** for transaction state machine transitions.
  Never expose `setState()` on domain entities.
- **Double-entry invariant**: every `Transaction` must have `SUM(DEBIT) == SUM(CREDIT)`
  before being persisted. `Ledger.verifyDoubleEntry()` enforces this.

### Currency and precision

Always use `BigDecimal` — never `double` or `float` for monetary values.
The `Amount` value object enforces this at the type level.

```java
// ✅ Correct
Amount amount = Amount.of(new BigDecimal("10000.00"), "XOF");

// ❌ Wrong — floating point error in financial calculations
double amount = 10000.00;
```

### Logging

- Never log PII (email, phone number, full name) at any level
- Never log secrets, tokens, OTP codes, or PIN values
- Use structured MDC for correlation IDs (transaction ID, customer ID)
- `INFO` for business events (payment initiated, completed, failed)
- `WARN` for recoverable errors (JWT validation failure, OTP expired)
- `ERROR` for unexpected exceptions only

### Exception handling

Domain exceptions extend `BusinessException`. They are mapped to HTTP status
codes in `GlobalExceptionHandler`. Do not catch `BusinessException` in the
application or domain layer — let them propagate.

Never return sensitive information in error responses. `ProblemDetail` (RFC 9457)
is the standard response format for all errors.

---

## 8.1 Commit conventions

KORA Core uses [Conventional Commits](https://www.conventionalcommits.org/).

Format: `<type>(<scope>): <description>`

### Types

| Type | When to use |
|---|---|
| `feat` | New feature or use case |
| `fix` | Bug fix |
| `refactor` | Code change with no functional impact |
| `test` | Adding or fixing tests |
| `docs` | Documentation only |
| `perf` | Performance improvement |
| `chore` | Build, dependencies, tooling |
| `adr` | Architecture Decision Record |

### Scopes

Use the domain concept affected:

`ledger`, `payment`, `auth`, `reconciliation`, `risk`, `provider`, `perf`, `infra`

### Examples

```
feat(payment): add optimistic locking on AccountEntity
fix(auth): align JWT secret source between filter and service
test(ledger): add double-entry invariant assertions under concurrent load
refactor(payment): extract provider orchestration to dedicated service
adr(ledger): document balance concurrency technical debt for Step 1
perf(payment): reduce DB round-trips on cashIn with batch insert
```

### Branch naming

```
feat/payment-optimistic-locking
fix/jwt-secret-alignment
test/cash-out-e2e
refactor/provider-orchestration
```

## 8.2 Branch strategy

KORA Core follows **Git Flow**.
```
main          Production — always deployable, tagged on every release
release/*     Pre-release stabilisation — feature freeze, bugfixes only
develop       Integration branch — default target for all feature PRs
feature/*     New features — branched from develop, merged back to develop
hotfix/*      Production fixes — branched from main, merged to main AND develop
```

### Rules

**`feature/*`**
- Branch from: `develop`
- Merge into: `develop` only
- Naming: `feature/<scope>-<short-description>`
  → `feature/payment-optimistic-locking`
  → `feature/ledger-snapshot-support`

**`release/*`**
- Branch from: `develop` when the milestone is feature-complete
- Merge into: `main` AND `develop`
- Naming: `release/v<major>.<minor>`
  → `release/v0.1` (Step 0 complete)
  → `release/v1.0` (Step 1 complete)
- Only bugfixes, documentation, and version bumps are allowed on this branch
- No new features

**`hotfix/*`**
- Branch from: `main`
- Merge into: `main` AND `develop`
- Naming: `hotfix/<short-description>`
  → `hotfix/jwt-secret-alignment`
- Reserved for production incidents only — not for features disguised as fixes

**`develop`**
- Default branch for pull requests
- Must always be in a passing CI state
- Never force-push

**`main`**
- Protected — no direct commits, merges only
- Every merge is tagged with a semantic version: `v0.1.0`, `v1.0.0`
- Represents the current production-deployable state of the system

### Semantic versioning

`vMAJOR.MINOR.PATCH`

| Bump | When |
|---|---|
| `MAJOR` | Breaking architecture change (e.g. microservice extraction) |
| `MINOR` | New roadmap step completed (Step 0 → Step 1) |
| `PATCH` | Bugfix or hotfix |

Step 0 complete → `v0.1.0`  
Step 1 complete → `v0.2.0`  
Hotfix on Step 1 → `v0.2.1`  
Microservice extraction (Step 7) → `v1.0.0`

---

## 9. Architecture decisions

All significant architectural decisions are recorded in `docs/adr/`.

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](./docs/adr/ADR-001-immutable-ledger.md) | Immutable double-entry ledger with denormalized balance cache | Accepted |
| [ADR-002](./docs/adr/ADR-002-payment-lifecycle.md) | Payment lifecycle state machine: INITIATED → AUTHORIZED → CAPTURED → SETTLEMENT_PENDING → SETTLED → COMPLETED | Accepted |
| [ADR-003](./docs/adr/ADR-003-payment-api-design.md) | Payment lifecycle fully orchestrated inside cashIn/cashOut/transfer — no separate authorize/capture API | Accepted |

When making a significant architectural decision, create a new ADR following
the existing format. Link it from this table.

A decision is "significant" if it affects:
- Financial correctness or auditability
- The boundary between domain, application, or infrastructure layers
- Concurrency or transaction isolation strategy
- A trade-off between performance and correctness

---

## 10. Client vs admin surface separation

KORA Core exposes two distinct API surfaces with separate role requirements.

### Customer surface (`ROLE_CUSTOMER`)

```
POST /payments/cash-in   — wallet top-up
POST /payments/cash-out  — wallet withdrawal
POST /payments/transfer  — peer-to-peer transfer
GET  /payments/balance   — account balance
GET  /payments/history   — transaction history
```

Each operation orchestrates the full lifecycle internally (INITIALIZED → AUTHORIZED
→ CAPTURED → SETTLEMENT_PENDING → SETTLED → COMPLETED). Intermediate states are
not exposed to the client — only the final state is returned.

### Operator surface (`ROLE_ADMIN` / `ROLE_OPERATOR`) — Step 5+

```
POST /payments/{id}/reverse        — reverse an authorized or captured transaction
POST /admin/payments/{id}/reverse  — reverse an authorized or captured transaction
GET  /admin/payments               — list transactions by state (e.g. stuck in AUTHORIZED)
```

RBAC is enforced in `SecurityConfig`. Never add business logic to the security
layer — role checks are a routing concern, not a domain concern.

---

## Troubleshooting

### `health = DOWN` at startup

```bash
curl http://localhost:8081/actuator/health | python -m json.tool
```

| Component DOWN | Likely cause | Fix |
|---|---|---|
| `db` | PostgreSQL not running | `docker compose up -d postgres` |
| `mail` | MailDev not running | `docker compose up -d maildev` |
| `redis` | Redis not running | `docker compose up -d redis` |

### `jwt.secret` too short

Spring will reject startup if `kora.security.jwt.secret` is shorter than 32 characters.
Update the value in `.env` and restart.

### Testcontainers fails to start

Ensure Docker Desktop is running and the current user has access to the Docker socket:

```bash
docker run --rm hello-world
```

If this fails, restart Docker Desktop.

### OTP endpoint returns 404 in perf profile

The `GET /test/otp/{email}` endpoint is only active with `SPRING_PROFILES_ACTIVE=perf`.
Verify the profile is set before running k6:

```bash
curl -s http://localhost:8081/actuator/info | grep -i profile
```



---

*For load testing specifics, see [perf/PERF.md](./perf/PERF.md).*  
*For architectural decisions, see [docs/adr/](./docs/adr/).*
