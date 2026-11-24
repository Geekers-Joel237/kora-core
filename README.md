# 🚀 KORA Core

**An open-source neobank wallet core — ledger, payments, reconciliation, real-time risk, and a governed AI control plane for payment operations.**

![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot 4.0.3](https://img.shields.io/badge/Spring%20Boot-4.0.3-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![React Native 0.86](https://img.shields.io/badge/React%20Native-0.86%20%C2%B7%20Expo%2057-61DAFB?logo=react&logoColor=black)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

Built in public. Every architecture decision is an [ADR](./docs/adr/README.md). Every limit is written down in the [state of view](./docs/kora-core-state-of-view-v3.md).

KORA Core is designed for Mobile Money and card-ready wallets operating where networks drop, providers answer late or twice, and settlement arrives a day after the customer was told "success". It is built around one question every fintech eventually faces:

> *"Show me every cent, from the beginning, and prove the balance is correct."*

## Contents

- [Scope](#-scope)
- [What runs today](#-what-runs-today)
- [Tech stack](#-tech-stack)
- [Architecture](#-architecture)
- [Getting started](#-getting-started)
- [Repository layout](#-repository-layout)
- [Roadmap and status](#-roadmap-and-status)
- [Target scale](#-target-scale)
- [Security](#-security)
- [Engineering philosophy](#-engineering-philosophy)
- [Documentation](#-documentation)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🧭 Scope

KORA Core covers the full chain a real wallet needs, in a strict dependency order — nothing is built on a layer that is not yet correct:

```text
Financial correctness        double-entry ledger, payment lifecycle, holds, reversals
        ↓
Network reality              idempotency under retry storms, unknown provider outcomes
        ↓
Distributed consistency      transactional outbox, Kafka event contracts, idempotent consumers
        ↓
Provider orchestration       canonical provider contract, circuit breakers, bulkheads, failover
        ↓
Settlement & reconciliation  T+1 settlement, provider-report matching, break case management
        ↓
Identity & compliance        device-bound auth, PIN, recovery, progressive KYC as versioned policy
        ↓
Real-time risk               Kafka Streams features, reproducible ALLOW / STEP_UP / REVIEW / BLOCK
        ↓
Data & ML                    CDC, point-in-time features, graph analytics, governed risk models
        ↓
Cloud & SRE                  AWS, Terraform, Kubernetes, supply-chain controls, SLOs, game days
        ↓
WalletOps                    AI control plane for payment operations
```

### 🤖 WalletOps — AI that never holds financial authority

Operations teams lose hours reconstructing why a payment is stuck: API logs, ledger entries, outbox, Kafka, provider callbacks, risk decisions, settlement files. WalletOps is designed to shorten that investigation **without bypassing a single financial control**:

- **Evidence-backed investigations** — a transaction timeline rebuilt from authoritative sources, every fact linked to its origin
- **Regulatory and operational RAG** — versioned sources, mandatory citations, refusal when evidence is insufficient
- **Safe Text2SQL** — read-only views, AST validation, allowlists, row and time limits, full audit
- **KYC document intelligence** — field-level confidence and evaluation, human review for anything ambiguous
- **MCP tool gateway** — narrow, typed, least-privilege tools; no tool can move money
- **AgentOps** — golden and adversarial eval sets, ship/no-ship thresholds, tracing, rollback, kill switch

The governing rule, enforced by architecture rather than by prompt:

> **AI may observe, retrieve, correlate, explain and propose. Deterministic services authenticate, authorize, validate financial invariants and execute financial operations.**

Autonomy grows one evidenced step at a time: observe → explain → recommend → draft → execute after approval.

---

## ✅ What runs today

A wallet backend with registration, OTP login, cash-in, cash-out, P2P transfer, balance, history and operator reversal — and **Kora Mobile**, its React Native client, in [`mobile/`](./mobile/README.md).

### 1️⃣ Immutable double-entry ledger
- Ledger entries are append-only — enforced **in the database**, not only in the domain. A correction is a compensating entry, never an update.
- `SUM(DEBIT) == SUM(CREDIT)` verified in the domain after every write, and asserted again against the database.
- `accounts.balance_amount` is a **denormalized read cache**, written in the same transaction as the entries — a choice with a real cost, weighed against three alternatives in [ADR-001](./docs/adr/ADR-001-immutable-ledger.md). On divergence, the entries win.

### 2️⃣ Payment lifecycle with an explicit unknown outcome
- Twelve states, one interface, each validating its own successors:
  `INITIALIZED → AUTHORIZED → CAPTURED → SETTLEMENT_PENDING → SETTLED → COMPLETED`,
  plus `FAILED`, `AUTHORIZATION_FAILED`, `CAPTURE_FAILED`, `SETTLEMENT_FAILED`, `REVERSED` — and `UNKNOWN`.
- `UNKNOWN` is what a provider timeout really means: the money may or may not have moved. Nothing resolves it but reconciliation — no job expires it, no blind retry replays it.
- An illegal transition throws; there is no `setState()`.

### 3️⃣ No provider call inside a database transaction
- Cash-in, cash-out and reversal run as TX-1 / provider I/O / TX-2, holding zero connections across ~1.4 s of provider latency.
- The response to a measured p95 of 60 s and a 73.93 % error rate at 25 req/s, caused by a single `@Transactional` spanning the whole operation ([ADR-004](./docs/adr/ADR-004-micro-transaction-optimization.md)). The post-fix baseline has not been re-measured — that run belongs on staging, not a laptop.

### 4️⃣ Funds holds
- A debit awaiting the provider reserves its funds first, so two concurrent cash-outs cannot both pass the balance check and both get paid out ([ADR-008](./docs/adr/ADR-008-funds-holds.md)).
- A hold is resolved with its outcome, never by time: consumed at capture, released on a known failure or an expired authorization.

### 5️⃣ One provider per payment method
- A strategy routes every step of a payment — authorization, capture, reversal — to the provider of its method: Orange Money, MoMo, card. A wallet transfer never leaves Kora.
- Each provider is a latency-simulating stub, not an HTTP client. That is deliberate: it makes every failure mode reproducible in a test.

### 6️⃣ Command bus and safe retries
- A correlation id on every command, read from the request or generated, and stored with the transaction's history; validation before any write; anti-replay over a TTL window. Printing it in every log line, and a trace id on every read and write, are next ([state of view](./docs/kora-core-state-of-view-v3.md) §9.5).
- A command with no registered use case **stops the application from booting**.
- Optimistic clashes and deadlocks retry with jittered backoff. The retry wraps the transaction boundary, never the reverse, and never spans the provider call — replaying that would charge twice.

### 7️⃣ A payment API boundary that cannot change unnoticed
- **Contract tests** on cash-in, cash-out, transfer, balance and history: the real web layer, security filter chain and exception handlers, with the use cases replaced by doubles — no database, about ten seconds. Every status, content type and body is compared to a committed file; a difference fails the build until the change is reviewed.
- **A published contract**, [`api-contract/payments.openapi.json`](./api-contract/payments.openapi.json): every response declared with its content type, required and nullable fields, enumerations taken from the Java sources, whole-unit amounts. The build asserts the declared responses are exactly the ones the contract tests observe.
- **One error contract**: every error is an RFC 9457 problem detail with a stable `code` from [the catalog](./docs/problems.md), and every command answers with its correlation id, in the `X-Correlation-Id` header and in its problem.
- The operator reversal is not under contract yet; its full journey comes later.

### 8️⃣ Quality enforced by the build
- **850+ tests on four levels** — unit, integration, contract, e2e — with no mocking framework and Testcontainers against a real database.
- **Coverage floors** measured on the unit level alone: **100 %** of the payment domain and use cases, at least **90 %** of the rest of the hexagon. Code that only an e2e test reaches is not covered where it counts.
- **Architecture as tests**: the layers, the module couplings, the test pyramid, the configuration hygiene and the migration names fail the build when they drift.

---

## 🧰 Tech stack

### Backend — `kora-core`

| Concern | Technology |
|---|---|
| Language | **Java 21** |
| Framework | **Spring Boot 4.0.3** — Web MVC, Validation, Security, Actuator |
| Persistence | **PostgreSQL** (17 in development, 16 under test) · **Flyway** · Spring Data JPA for writes · `NamedParameterJdbcTemplate` for reads |
| Mapping | **MapStruct 1.6.3** at the two edges · Lombok (+ `lombok-mapstruct-binding`) |
| Security | Spring Security · **jjwt 0.12.6** — signed access and refresh tokens, one key per use · BCrypt PIN hashing · OTP by mail |
| API | REST · **springdoc-openapi 3.0.1** (Swagger UI) · RFC 9457 `ProblemDetail` errors |
| Mail | Spring Mail — SMTP; MailDev in development |
| Build | **Gradle 9.8** (wrapper) |

### Quality and testing

| Concern | Technology |
|---|---|
| Tests | **JUnit 5** · AssertJ · in-memory doubles, no mocking framework |
| Integration | **Testcontainers 1.20.4** — PostgreSQL, reached as the application role |
| Contract | Spring MVC test slice · committed golden files · OpenAPI document under test |
| Coverage | **JaCoCo 0.8.13** — floors per module and per pyramid level |
| Architecture | structural tests: hexagonal layers, module boundaries, test pyramid, configuration hygiene, migration naming |

### Operations

| Concern | Technology |
|---|---|
| Local infrastructure | **Docker Compose** — one file per environment, optional groups by profile |
| Database tooling | pgAdmin |
| Metrics | **Micrometer** → **InfluxDB 1.8** → **Grafana 12** (load campaigns) |
| Load testing | **k6**, containerised — smoke, load, stress and soak |

### Mobile — `mobile/`

| Concern | Technology |
|---|---|
| Framework | **React Native 0.86** · **Expo SDK 57** · React 19 · **TypeScript** (strict) |
| Navigation | Expo Router |
| State and data | **TanStack Query 5** · Zustand 5 · MMKV |
| Forms and validation | React Hook Form · **Zod 4** |
| Device and security | Expo SecureStore · Local Authentication (biometrics) · Screen Capture · Crypto |
| UI | Reanimated 4 · Gesture Handler · FlashList · SVG |
| i18n | i18next |
| Quality | Jest + Testing Library · ESLint 9 · Prettier |

---

## 🏛️ Architecture

Three packages — `auth`, `payment`, `shared` — with a hexagonal interior whose dependency rules are asserted by the build, not by convention:

```
adapters/in → ports/in → application → domain ← ports/out ← adapters/out
                                ↑
                             config wires it all
```

- **`domain/`** names nothing but the JDK and the shared kernel. **`application/`** is organised by intent — `command/<intent>/` and `query/<intent>/` — and names no framework type at all.
- Not yet a modular monolith: one Gradle project, one Spring Boot application, and eight classes still crossing between `auth` and `payment`. Those crossings are *recorded* so they cannot grow unnoticed — the first half of Step 2, not the whole of it.

```
POST /auth/register · /auth/verify-otp · /auth/login · /auth/refresh · /auth/logout
POST /payments/cash-in · /payments/cash-out · /payments/transfer
GET  /payments/balance · /payments/history
POST /admin/payments/{txId}/reverse
```

The full picture — layers, phases, patterns, conventions — is in [docs/engineering-standards.md](./docs/engineering-standards.md).

---

## 🚀 Getting started

**Prerequisites**: Java 21+, Docker, Git.

```bash
git clone https://github.com/Geekers-Joel237/kora-core.git && cd kora-core
cp .env.example .env       # each secret it lists is the command that generates it
docker compose up -d       # PostgreSQL + MailDev
./gradlew bootRun          # :8081 — http://localhost:8081/swagger-ui.html
```

```bash
./gradlew unitTest         # the unit level alone — seconds, no Docker
./gradlew build            # compile + all four test levels + the coverage gate
```

Profiles, optional containers, the IDE and the load tests: [docs/development/local-setup.md](./docs/development/local-setup.md).

---

## 🗂️ Repository layout

```
kora-core/
├── src/main/java/…/koracore/   auth · payment · shared — each: domain, application, ports, adapters, config
├── src/main/resources/         application profiles, Flyway migrations
├── src/test/java/…/koracore/   per module: unit · integration · contract · e2e
├── api-contract/               the published OpenAPI contract of the payment API
├── config/                     coverage floors
├── docker/                     PostgreSQL role script
├── docs/                       standards, development guides, ADRs, reviews
├── mobile/                     Kora Mobile — the React Native client and its specification
├── perf/                       k6 scenarios, Grafana dashboards, runbook
└── docker-compose*.yml         one file per environment
```

---

## 🗺️ Roadmap and status

[`ROADMAP.md`](./ROADMAP.md) is the single authoritative roadmap ([ADR-009](./docs/adr/ADR-009-single-roadmap.md)). Each step has an exit gate; a step is marked delivered only when its gate passes.

| Step | Scope | Status |
|---|---|---|
| 0 | Conscious transactional monolith — double-entry ledger | ✅ Delivered |
| 1 | Real payment lifecycle and reversal (funds holds added since, ADR-008) | ✅ Delivered |
| 2 | Enforced modular monolith | 🟡 Open — boundaries recorded, not yet separated |
| 3 | Hexagonal architecture | ✅ Delivered (before Step 2, [ADR-007](./docs/adr/ADR-007-hexagonal-before-modular.md)) |
| 4 | Idempotency under retry storms | ⏭️ Next |
| 5 | Transactional outbox, event-driven consistency | Planned |
| 6 | Reconciliation engine — resolves `UNKNOWN` | Planned |
| 7 | Reconciliation service extraction — first **Go** component: settlement-file ingestion | Planned |
| 8 | Multi-provider orchestration, risk and velocity | Planned |
| 9 | Containers and Kubernetes on **AWS** | Planned |
| 10 | Observability and business KPIs | Planned |
| 11–15 | Identity, PIN and device binding, sessions, recovery, progressive KYC | Planned |
| 16–19 | Kafka platform, Kafka Streams features, real-time risk, fraud scenarios | Planned |
| 20–22 | CDC and lakehouse, graph analytics, governed risk models — **Scala** feature pipelines | Planned |
| 23–24 | Advanced settlement case management, SecOps and SRE | Planned |
| 25–32 | **WalletOps** — investigation views, document intelligence, RAG, safe Text2SQL, workflows, MCP gateway, evals, LLMOps | Planned |

The limits of what runs today — what holds and what does not, with severities — are in [`docs/kora-core-state-of-view-v3.md`](./docs/kora-core-state-of-view-v3.md).

---

## 📊 Target scale

Simulated growth, not production metrics:

- 5,000 → 500,000+ transactions/day
- 10 → 1,000+ requests/second at peak
- Provider latency between 200 ms and 2 s, retry storms, settlement mismatches, batch reconciliation under heavy I/O

Architecture evolves only when volume and business complexity justify it. No premature microservices. No architectural theater.

---

## 🔐 Security

**In place:**
- Immutable financial records, enforced in the database; strict state-transition validation
- The application connects as a role that cannot change the schema nor rewrite the ledger
- Replay protection on the command bus, over a TTL window
- PINs hashed with BCrypt; access and refresh tokens signed with separate keys, refresh tokens rotated and revocable by family; `ROLE_CUSTOMER` / `ROLE_ADMIN`
- **No sensitive data in logs** — `Pin`, `PhoneNumber`, `OtpCode` and the tokens redact themselves in `toString()`; a test dispatches a command carrying a PIN and asserts that neither the PIN nor the customer id reaches the log output
- Secrets have no fallback value: a clone without `.env` refuses to boot rather than starting with a publicly readable signing key
- Tamper-aware audit trail: every state change writes an immutable row

**Planned:** idempotency-first design (Step 4), rate limiting (state of view L-9), device-bound credentials (Step 12), zero-trust service-to-service communication once anything is distributed.

To report a vulnerability, see [SECURITY.md](./SECURITY.md) — never in a public issue.

In fintech, security failures are trust failures.

---

## 🧠 Engineering philosophy

Correctness beats cleverness.
Resilience beats hype.
Financial integrity beats architectural fashion.

Every decision is justified by transaction volume, operational risk, financial integrity and user trust — and its cost is written down.

---

## 📚 Documentation

| Document | Purpose |
|---|---|
| [docs/README.md](./docs/README.md) | The map of all documentation |
| [ROADMAP.md](./ROADMAP.md) | The single roadmap: steps, exit gates, dependencies |
| [docs/engineering-standards.md](./docs/engineering-standards.md) | Principles, definition of done, testing strategy, architecture, conventions |
| [CLAUDE.md](./CLAUDE.md) | The invariants, the vocabulary, and the rules a review applies to a diff |
| [docs/development/](./docs/README.md#contribute) | Local setup, testing, database, troubleshooting |
| [docs/adr/](./docs/adr/README.md) | Every architecture decision, and what it cost |
| [docs/kora-core-state-of-view-v3.md](./docs/kora-core-state-of-view-v3.md) | What holds today, and what does not |
| [perf/PERF.md](./perf/PERF.md) | Load-testing runbook (k6 + Grafana) |

---

## 🤝 Contributing

Contributions are welcome. Read [CONTRIBUTING.md](./CONTRIBUTING.md) for the workflow, the commit conventions and what a pull request must pass.

---

## 📌 License

[MIT](./LICENSE) — for learning, experimentation and transparency.
