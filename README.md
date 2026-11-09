# 🚀 KORA Core

**An open-source neobank wallet core — ledger, payments, reconciliation, real-time risk, and a governed AI control plane for payment operations.**

Built in public. Every architecture decision is an [ADR](./docs/adr). Every limit is written down in the [state of view](./docs/kora-core-state-of-view-v3.md).

KORA Core is designed for Mobile Money and card-ready wallets operating where networks drop, providers answer late or twice, and settlement arrives a day after the customer was told "success". It is built around one question every fintech eventually faces:

> *"Show me every cent, from the beginning, and prove the balance is correct."*

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

Java 21 · Spring Boot 4.0.3 · PostgreSQL + Flyway · 650+ tests, no mocking framework, Testcontainers against a real database.

Three packages — `auth`, `payment`, `shared` — with a hexagonal interior whose dependency rules are asserted by the build, not by convention. Not yet a modular monolith: one Gradle project, one Spring Boot application, and eleven classes still crossing between `auth` and `payment`. Those crossings are *recorded* so they cannot grow unnoticed — the first half of Step 2, not the whole of it.

```
POST /auth/register · /auth/verify-otp · /auth/login · /auth/refresh
POST /payments/cash-in · /payments/cash-out · /payments/transfer
GET  /payments/balance · /payments/history
POST /admin/payments/{txId}/reverse
```

### 1️⃣ Immutable double-entry ledger
- Ledger entries are append-only — enforced **in the database**, not only in the domain. A correction is a compensating entry, never an update.
- `SUM(DEBIT) == SUM(CREDIT)` verified in the domain after every write, and asserted again against the database.
- `accounts.balance_amount` is a **denormalized read cache**, written in the same transaction as the entries — a choice with a real cost, weighed against three alternatives in [ADR-001](./docs/adr/ADR-001-immutable-ledger.md). On divergence, the entries win.

### 2️⃣ Payment lifecycle with an explicit unknown outcome
- Twelve states, one interface, each validating its own successors:
  `INITIALIZED → AUTHORIZED → CAPTURED → SETTLEMENT_PENDING → SETTLED → COMPLETED`,
  plus `FAILED`, `AUTHORIZATION_FAILED`, `CAPTURE_FAILED`, `SETTLEMENT_FAILED`, `REVERSED` — and `UNKNOWN`.
- `UNKNOWN` is what a provider timeout really means: the money may or may not have moved. It stays terminal until reconciliation resolves it. Blind retry after an unknown outcome is forbidden.
- An illegal transition throws; there is no `setState()`.

### 3️⃣ No provider call inside a database transaction
- Cash-in, cash-out and reversal run as TX-1 / provider I/O / TX-2, holding zero connections across ~1.4 s of provider latency.
- The response to a measured p95 of 60 s and a 73.93 % error rate at 25 req/s, caused by a single `@Transactional` spanning the whole operation ([ADR-004](./docs/adr/ADR-004-micro-transaction-optimization.md)). The post-fix baseline has not been re-measured — that run belongs on staging, not a laptop.

### 4️⃣ Funds holds
- A debit awaiting the provider reserves its funds first, so two concurrent cash-outs cannot both pass the balance check and both get paid out ([ADR-008](./docs/adr/ADR-008-funds-holds.md)).

### 5️⃣ Command bus and safe retries
- Correlation id born at the HTTP edge and propagated into every log line; validation before any write; anti-replay over a TTL window.
- A command with no registered use case **stops the application from booting**.
- Optimistic clashes and deadlocks retry with jittered backoff. The retry wraps the transaction boundary, never the reverse, and never spans the provider call — replaying that would charge twice.

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

The provider today is a latency-simulating stub, not an HTTP client. That is deliberate: it makes the failure modes reproducible in a test.

The limits of what runs today — what holds and what does not, with severities — are in [`docs/kora-core-state-of-view-v3.md`](./docs/kora-core-state-of-view-v3.md).

---

## 📊 Target scale

Simulated growth, not production metrics:

- 5,000 → 500,000+ transactions/day
- 10 → 1,000+ requests/second at peak
- Provider latency between 200 ms and 2 s, retry storms, settlement mismatches, batch reconciliation under heavy I/O

Architecture evolves only when volume and business complexity justify it. No premature microservices. No architectural theater.

---

## 🔐 Security principles

**In place:**
- Immutable financial records, enforced in the database; strict state-transition validation
- Replay protection on the command bus, over a TTL window
- PINs hashed with bcrypt; JWT with refresh tokens; `ROLE_CUSTOMER` / `ROLE_ADMIN`
- **No sensitive data in logs** — `Pin`, `PhoneNumber` and `OtpCode` redact themselves in `toString()`; a test dispatches a command carrying a PIN and asserts that neither the PIN nor the customer id reaches the log output
- Secrets have no fallback value: a clone without `.env` refuses to boot rather than starting with a publicly readable signing key
- Tamper-aware audit trail: every state change writes an immutable row

**Planned:** idempotency-first design (Step 4), rate limiting (state of view L-9), device-bound credentials (Step 12), zero-trust service-to-service communication once anything is distributed.

In fintech, security failures are trust failures.

---

## 🧠 Engineering philosophy

Correctness beats cleverness.
Resilience beats hype.
Financial integrity beats architectural fashion.

Every decision is justified by transaction volume, operational risk, financial integrity and user trust — and its cost is written down.

---

## 🚀 Getting started

```bash
cp .env.example .env
docker compose up -d
./gradlew bootRun          # :8081 — http://localhost:8081/swagger-ui.html
./gradlew build            # compile + the full suite
```

| Document | Purpose |
|---|---|
| [ROADMAP.md](./ROADMAP.md) | The single roadmap: steps, exit gates, dependencies |
| [docs/kora-core-state-of-view-v3.md](./docs/kora-core-state-of-view-v3.md) | What holds today, and what does not |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | Local setup, running the app and the tests, conventions |
| [CLAUDE.md](./CLAUDE.md) | The invariants, the vocabulary, and the rules a review applies to a diff |
| [HELP.md](./HELP.md) | Engineering standards: testing strategy, DDD and SOLID as practised here |
| [perf/PERF.md](./perf/PERF.md) | Load-testing runbook (k6 + Grafana) |

**Architecture decisions** — the reasoning, and what each one cost:

| ADR | Decision |
|---|---|
| [001](./docs/adr/ADR-001-immutable-ledger.md) | Immutable ledger, and why the balance is a cache rather than a sum |
| [002](./docs/adr/ADR-002-payment-lifecycle.md) | The lifecycle state machine and its locking |
| [003](./docs/adr/ADR-003-payment-api-design.md) | One call per operation, no separate authorize/capture API |
| [004](./docs/adr/ADR-004-micro-transaction-optimization.md) | TX-1 / provider I/O / TX-2 — the p95 60 s post-mortem |
| [005](./docs/adr/ADR-005-load-test-calibration.md) | Load-test calibration and the SLO gates |
| [006](./docs/adr/ADR-006-compose-topology.md) | One compose file per environment, services chosen by profile |
| [007](./docs/adr/ADR-007-hexagonal-before-modular.md) | Hexagonal before modular; the transaction boundary is not a Unit of Work |
| [008](./docs/adr/ADR-008-funds-holds.md) | Funds holds for debits awaiting the provider |
| [009](./docs/adr/ADR-009-single-roadmap.md) | One roadmap, extended append-only; where Go, Scala and AI enter |

---

## 📌 License

MIT — for learning, experimentation and transparency.
