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
9. [Database migrations](#9-database-migrations)
10. [Architecture decisions](#10-architecture-decisions)

---

## 1. Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 21+ | Runtime and compilation |
| Docker Desktop | Latest | PostgreSQL, MailDev, monitoring stack |
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
├── docker-compose.yml          # Architecture: PostgreSQL, MailDev, pgAdmin, InfluxDB, Grafana
├── docker-compose.override.yml # Development overrides (auto-loaded)
├── docker-compose.prod.yml     # Production overrides (explicit -f)
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

`.env.example` is the authoritative list of every variable the application and
Docker Compose can consume. It is versioned; `.env` is not, and must never be
committed.

Then generate your own JWT signing key and put it in `.env`:

```bash
openssl rand -base64 48
```

> **Security note**: `JWT_SECRET` must be at least 32 characters — HMAC-SHA256
> rejects anything shorter, and `SecurityProperties` refuses to start on it.
> The placeholder shipped in `.env.example` is deliberately non-functional.
> Never commit a real secret; use a secrets manager outside local development.

### 3.2 Start the infrastructure services

```bash
docker compose up -d
```

Or press **Run** in your IDE and skip this step entirely — the `dev` profile
lets Spring bring the same stack up itself (section 3.6).

That starts **two** containers: Postgres and MailDev. MailDev is not optional in
development — registration and login send an OTP by mail, so without it those
flows fail and `/actuator/health` reports `mail` as DOWN.

Everything else is an opt-in **Compose profile**, off by default:

| Profile | Services | When |
|---|---|---|
| *(none)* | postgres, maildev | always, in development |
| `tooling` | pgadmin | when you want a database GUI |
| `observability` | influxdb, grafana | load-test campaigns only |

```bash
# For a single command — naming a service enables its profile automatically
docker compose up -d pgadmin
docker compose up -d influxdb grafana

# For a whole session — set it once in .env and forget it
#     COMPOSE_PROFILES=tooling,observability
docker compose up -d
```

`COMPOSE_PROFILES` lives in `.env`, so `up`, `ps`, `logs` and `down` all agree on
the same set. That is precisely why an optional group is a profile and not a
separate `-f` file: with files you must repeat the identical `-f` list on the way
down, and forgetting one silently leaves those containers running.

Keeping the optional groups off by default is worth about 100 seconds every time
you start work — measured, containers already pulled, waiting for every service to
report healthy:

| | Containers | Time to healthy |
|---|---|---|
| Default | 2 | **12 s** |
| `tooling,observability` | 5 | **115 s** |

Then verify — every service declares a healthcheck, so `healthy` means ready,
not merely started:

```bash
docker compose ps                 # STATUS column must read "healthy"
open http://localhost:1080        # MailDev — read the OTP mails here
```

pgAdmin takes about a minute on a first boot; `health: starting` until then is
expected.

All ports are published on `127.0.0.1` only. The database is reachable from this
machine and from nowhere else on the network — the same posture production uses,
where operators reach it through an SSH tunnel onto that loopback port.

### 3.2.1 Files answer *where*, profiles answer *what else*

One rule keeps the layout readable from an `ls` alone:

> **One file = one environment. One profile = one optional group.**

The two axes are independent: `tooling` is not an environment, and `prod` is not
something you add on top of `dev`.

| File | Loaded | Answers | Holds | Never holds |
|---|---|---|---|---|
| `docker-compose.yml` | always | *What does the application need to exist at all?* | postgres: image and tag, `environment`, `healthcheck`, named volume, network | `ports`, `restart`, `container_name`, host bind mounts, any service only some environments run |
| `docker-compose.override.yml` | automatically, unless `-f` is used | *How do I run it on my machine?* | loopback port bindings, `container_name`, `restart: "no"`, and the development-only services — maildev, pgadmin, influxdb, grafana | anything a server would need |
| `docker-compose.prod.yml` | only when named with `-f` | *How does it run on a server?* | loopback binding, `restart: unless-stopped`, `shm_size`, `stop_grace_period`, log rotation | any secret |

```bash
docker compose up -d                                                    # socle + dev
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d   # socle + prod
```

Naming any file with `-f` suppresses the automatic pickup of
`docker-compose.override.yml`. That is the mechanism that keeps development port
bindings off a server, and it is the one mistake worth guarding against: a deploy
runbook must carry the full command, never a bare `docker compose up`.

It is also what makes production safe by construction. pgAdmin and Grafana are
declared *only* in the development file, so the production command never sees
them — there is no service definition to activate, with or without a profile.
Nothing has to be switched off, because nothing is switched on.

Two rules keep the files from drifting:

- **The socle holds only what every environment runs**, and it stays
  environment-agnostic. That is testable:

  ```bash
  docker compose -f docker-compose.yml config | grep -E "^\s+(ports|restart|container_name):"
  ```

  It must print nothing. Anything it prints is a decision that every environment
  now inherits, including ones that do not exist yet.
- **A service that only one environment runs is declared in that environment's
  file.** An environment file may therefore introduce services; that is the point
  of the axis. What it must not do is redefine architecture — image, healthcheck
  and volumes of a socle service stay in the socle.

### 3.2.2 Compose profiles and Spring profiles are two different things

They share a name and nothing else. One decides which **containers** run, the
other which **application configuration** applies, and neither reads the other.

| | Compose profile | Spring profile |
|---|---|---|
| Decides | which **containers** run | which **application configuration** applies |
| Selected by | `COMPOSE_PROFILES` in `.env`, or naming a service on the command line | `SPRING_PROFILES_ACTIVE`, or `spring.profiles.default: dev` |
| Values | *(none)*, `tooling`, `observability` | `dev`, `perf`, `prod`, `test` |
| Declared in | `docker-compose*.yml` | `src/main/resources/application-<profile>.yaml` |

Nothing enforces agreement between them, so a Spring profile that needs a
container will start happily without it and fail later:

| Spring profile | Requires which containers | Because |
|---|---|---|
| `dev` (default) | the default set | `SmtpMailAdapter` sends OTP mail to `MAIL_HOST:MAIL_SMTP_PORT` — MailDev, already running |
| `perf` | default **+ `observability`** | Micrometer pushes to InfluxDB every 10 s, and `perf/*-run.sh` waits on `/actuator/health`, whose `mail` component probes SMTP |
| `prod` | Postgres only | a real SMTP relay, no metrics export, no database GUI |
| `test` | *(none — Compose is not used at all)* | every test starts its own PostgreSQL through Testcontainers and reads no `.env` |

What a mismatch looks like:

- `perf` without `observability` — `InfluxMeterRegistry` logs a failed push every
  ten seconds and the Grafana panels stay empty, while the load test itself runs
  and reports normal-looking results.
- A stack started with `-f` but without naming `docker-compose.override.yml` —
  Postgres publishes no port, so the application on the host cannot reach the
  database it is being tested against.

Switching to a load test therefore means moving **both** axes together:

```bash
docker compose up -d influxdb grafana          # Compose axis
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun  # Spring axis
```

The two meet in exactly one place: `.env`. Compose and the application both read
it directly, so there is a single source of truth rather than a convention that
they are kept aligned by hand. A real environment variable still overrides the
file, which is how CI and a secrets manager inject values.

### 3.3 Verify the database connection

Both Docker Compose and the application read `.env` — Compose natively, the
application through `spring.config.import` in `application.yaml`. No shell
preparation, and no second copy of the values to drift:

```bash
./gradlew bootRun &
curl -s http://localhost:8081/actuator/health | python -m json.tool
# Expected: "status": "UP", "db": { "status": "UP" }, "mail": { "status": "UP" }
```

No `--spring.profiles.active` is needed: `spring.profiles.default` is `dev`.

Stop the background process before running tests:

```bash
pkill -f "KoraCoreApplication" 2>/dev/null || true
```

### 3.4 Where configuration lives

Every value has exactly one legitimate home.

| Support | Holds | Never holds |
|---|---|---|
| `.env` (git-ignored, mirrored by `.env.example`) | secrets, credentials, host-specific values: passwords, the JWT key, exposed ports | anything that is the same on every machine |
| `src/main/resources/application.yaml` | the environment contract (`${VAR}` bindings) and policy identical in every environment | monitoring, mail transport, pool sizing, provider behaviour |
| `src/main/resources/application-<profile>.yaml` | technical configuration for that environment only | secrets, and any fallback for one |

The base file answers *what must exist*; a profile answers *how it behaves here*.
That is why monitoring is never in the base file: `management.endpoint.health.show-details`
is `always` in dev and `never` in prod, so no single value could be correct for both.

| Variable | Consumed by | Profile |
|---|---|---|
| `POSTGRES_DB` `DB_HOST` `DB_PORT` | Compose + `application.yaml` | all |
| `POSTGRES_USER` `POSTGRES_PASSWORD` | Compose only — superuser, initialises a fresh volume | — |
| `POSTGRES_MIGRATION_USER` `POSTGRES_MIGRATION_PASSWORD` | `application.yaml` → `spring.flyway.*` | all |
| `POSTGRES_APP_USER` `POSTGRES_APP_PASSWORD` | `application.yaml` → `spring.datasource.*` | all |
| `JWT_SECRET` | `application.yaml` | all |
| `SERVER_PORT` | `application.yaml` | all |
| `MAIL_HOST` `MAIL_SMTP_PORT` | Compose + `application.yaml` | all |
| `MAIL_UI_PORT` | Compose | — |
| `MAIL_FROM` `MAIL_USERNAME` `MAIL_PASSWORD` | `application-prod.yaml` | prod |
| `INFLUXDB_HOST` `INFLUXDB_PORT` | Compose + `application-perf.yaml` | perf |
| `GRAFANA_PORT` `DB_ADMIN_PORT` | Compose | — |
| `PGADMIN_DEFAULT_EMAIL` `PGADMIN_DEFAULT_PASSWORD` | Compose | — |

Four profiles, one file each:

| Profile | File | Activated by |
|---|---|---|
| `dev` | `src/main/resources/application-dev.yaml` | nothing — `spring.profiles.default` |
| `perf` | `src/main/resources/application-perf.yaml` | `SPRING_PROFILES_ACTIVE=perf` |
| `prod` | `src/main/resources/application-prod.yaml` | `SPRING_PROFILES_ACTIVE=prod` |
| `test` | `src/test/resources/application-test.yaml` | `@ActiveProfiles("test")` |

The `test` profile sits in `src/test/resources` so it never ships in the
production jar. Alongside it, `src/test/resources/application.yaml` shadows the
base contract on the test classpath — which is what lets `./gradlew test` pass
with no `.env` and no exported variable.

### 3.6 Running from an IDE

Nothing to configure, and nothing to start beforehand. A Run Configuration on
`KoraCoreApplication` is enough: `application.yaml` imports `.env` itself — no
EnvFile plugin, no variables pasted into the run configuration, no second copy to
drift — and the `dev` profile has Spring bring the Compose stack up on its own.

One condition: the **working directory** must be the project root, which is the
IntelliJ default. Both `spring.config.import` (for `./.env`) and the Compose file
paths resolve relative to it.

The stack **stays up when you stop the application** — `lifecycle-management:
start-only`. Stopping a run must not stop the database; you bring the stack down
with `docker compose down` when you actually mean to.

`COMPOSE_PROFILES` works here exactly as in a terminal: the `docker compose`
subprocess inherits the working directory and reads the same `.env`, so setting
it to `observability` makes the IDE run start InfluxDB and Grafana too.

Two things make this safe, and both are load-bearing — see ADR-006 D7 before
touching either:

- `spring.docker.compose.file` names **both** Compose files. Left to itself the
  integration resolves only `docker-compose.yml` and runs `docker compose -f` on
  it, which suppresses the override where the ports live — the failure is
  `No host port mapping found for container port 5432`.
- The dev `postgres` service carries the label `org.springframework.boot.ignore`.
  Without it, Spring derives `ConnectionDetails` from the container's
  `environment:` block, and those **outrank `spring.datasource.*`** — the
  application would connect as the superuser and bypass the
  `kora_migration` / `kora_app` split, silently.

Tests need even less. `@ActiveProfiles("test")` is self-contained, so running a
test class from the IDE works on a clean clone with no `.env` at all.

### 3.5 When a variable is missing

No configuration entry carries a fallback, so the boot stops. For the signing key:

```
APPLICATION FAILED TO START

Description:

Binding to target com.geekersjoel237.koracore.application.config.SecurityProperties failed:

    Property: kora.security.jwt.secret
    Value: "${JWT_SECRET}"
    Reason: must be at least 32 characters for HMAC-SHA256
```

Read `Value` carefully: Spring binds the unresolved text `${JWT_SECRET}` rather
than failing on the placeholder itself. A bare `${VAR}` is therefore *not* enough
on a `@ConfigurationProperties` target — the `@Size` constraint on
`SecurityProperties` is what turns a 14-character literal into a refusal to start
instead of a weak key discovered at the first token issuance.

`ConfigurationHygieneTest` fails the build if a sensitive property regains a
fallback, or if a `${VAR}` is referenced without being listed in `.env.example`.

---

## 4. Running the application

### Default (development)

```bash
docker compose up -d
./gradlew bootRun
```

The application starts on **http://localhost:8081**.

Spring Boot DevTools is active in development — the application reloads on
class changes without a full restart.

### Perf profile (required for load tests)

```bash
docker compose up -d influxdb grafana
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
(`docker compose up -d`).

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

Tests run with `@ActiveProfiles("test")` and read
`src/test/resources/application.yaml`, which shadows the base file on the test
classpath. That is deliberate: the suite reads no environment variable and needs
no `.env`. This configuration:
- Uses a fixed, literal JWT key, independent from any environment
- Disables real SMTP (`TestMailConfig` replaces `SmtpMailAdapter` with an in-memory stub)
- Sets `ddl-auto: none` — Flyway creates the schema on the Testcontainers database
  and stays its single owner
- Declares no datasource: every integration and E2E class injects one at runtime
  through `@DynamicPropertySource` or `@ServiceConnection`

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

## 9. Database migrations

Flyway owns the schema. Hibernate runs `ddl-auto=validate` in
`application.properties` and `none` in the test properties — it never emits DDL.

### Naming

Migrations are versioned by **timestamp**, not by sequence:

```
V<yyyyMMddHHmm>__snake_case_name.sql
```

- Valid — `V202605270702__initial_schema.sql`
- Invalid — `V2__add_idempotency_keys.sql` (sequential version)
- Invalid — `V202605270702__addIdempotencyKeys.sql` (not `snake_case`)

A sequential version collides as soon as two branches add a migration in
parallel: both claim the next integer, and the clash only surfaces at merge.

Generate the timestamp for a new migration:

```bash
date -u +%Y%m%d%H%M
```

`MigrationNamingConventionTest` scans `src/main/resources/db/migration/` on every
`./gradlew test` and fails on any file that breaks the pattern.

### Never modify an applied migration

Add a new one instead. Flyway stores a checksum of each migration's content in
`flyway_schema_history`; editing a file already applied anywhere invalidates that
checksum and blocks startup on every database that holds it.

### Ordering across branches — `out-of-order` is off

`spring.flyway.out-of-order=false`, on every profile. A migration whose version is
lower than the highest already applied is never inserted retroactively.

**The cost we accept**: a branch opened Monday and merged Friday can carry a
timestamp older than a migration already applied on `develop`. Startup then fails:

```
Detected resolved migration not applied to database: 202605270702
```

**The fix**: before merging, `git mv` the migration to a fresh timestamp so its
version is the highest. On the feature branch — never after the merge.

`out-of-order=true` was rejected: it makes the applied order a property of each
environment's history rather than of the repository, so two databases that
received the same migrations in a different order can diverge with nothing in the
repo to arbitrate.

### Realigning an existing database

`V1__initial_schema.sql` became `V202605270702__initial_schema.sql`. The version
changed, so a database that already applied `V1` hits two problems: an applied
migration missing from disk, and a higher pending version it would replay over
tables that already exist.

One statement fixes both. The checksum covers file **content**, which did not
change, so it stays valid:

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "UPDATE flyway_schema_history
      SET version = '202605270702',
          script  = 'V202605270702__initial_schema.sql'
    WHERE version = '1' AND script = 'V1__initial_schema.sql';"
```

`flyway repair` does not help: it matches by version, and version `1` no longer
exists on disk.

If the database holds nothing worth keeping, drop it instead:

```bash
docker compose down -v && docker compose up -d postgres
```

Testcontainers-backed tests need neither: each run starts an empty database and
replays every migration.

---

## 10. Architecture decisions

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

## 11. Client vs admin surface separation

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
| `db` | PostgreSQL not running | `docker compose up -d` |
| `mail` | MailDev not running | `docker compose up -d` |

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
