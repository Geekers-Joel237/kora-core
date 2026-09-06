# 🚀 KORA Core

**KORA Core** is a production-grade digital wallet engine designed to engage the real engineering challenges faced by modern neobanks operating in emerging markets.

It is built under real constraints:

- Regulatory transaction limits  
- Network instability and retry storms  
- Multi-provider payment integrations  
- Settlement delays (T+1 scenarios)  
- Reconciliation mismatches  
- Fraud & velocity risks  
- High transaction throughput  
- Strict security and audit requirements  

This is not a tutorial project.  
It is a deliberate engineering journey toward building resilient financial infrastructure.

---

## 🎯 Why this project exists

KORA Core exists to push my engineering capabilities to the level required by leading fintech companies in Africa and globally.

### 🎯 Target Engineering Standard

**Africa:**
- Djamo  
- Wave  
- Flutterwave  
- Paystack  
- Moniepoint  
- Chipper Cash  
- Interswitch  
- OPay  

**Europe:**
- Revolut  
- N26  
- Wise  
- Adyen  
- Klarna  

**United States:**
- Stripe  
- Square (Block)  
- Chime  
- Robinhood  
- Brex  
- Plaid  

The objective is not imitation.  
It is technical readiness.

KORA Core is built to reach:

- Engineering-lead level ownership  
- Financial correctness at scale  
- Distributed system resilience  
- Product-aware architecture decisions  
- Risk-aware system design  
- Cloud-native operational maturity  

---

## ✅ What runs today

Java 21 · Spring Boot 4.0.3 · PostgreSQL + Flyway · 547 tests, no mocking framework.
Three packages — `auth`, `payment`, `shared` — with a hexagonal interior whose
dependency rules are asserted by the build, not by convention. Not yet a modular
monolith: one Gradle project, one Spring Boot application, and eleven classes still
crossing between `auth` and `payment`. Those crossings are *recorded* so they cannot
grow unnoticed, which is the first half of Étape 2, not the whole of it.

```
POST /auth/register · /auth/verify-otp · /auth/login · /auth/refresh
POST /payments/cash-in · /payments/cash-out · /payments/transfer
GET  /payments/balance · /payments/history
POST /admin/payments/{txId}/reverse
```

### 1️⃣ Immutable double-entry ledger
- Ledger entries are append-only; a correction is a compensating entry, never an update
- `SUM(DEBIT) == SUM(CREDIT)` verified in the domain after every write, and asserted
  again against the database
- `accounts.balance_amount` is a **denormalized read cache**, written in the same
  transaction as the entries. It is deliberately *not* recomputed on read — a choice
  with a real cost, weighed against three alternatives in
  [ADR-001](./docs/adr/ADR-001-immutable-ledger.md). On divergence the entries win.
- Snapshot pattern on every aggregate; an immutable audit row on every state change

### 2️⃣ Full payment lifecycle
- Eleven states, one interface, each validating its own successors:
  `INITIALIZED → AUTHORIZED → CAPTURED → SETTLEMENT_PENDING → SETTLED → COMPLETED`
- Plus `FAILED`, `AUTHORIZATION_FAILED`, `CAPTURE_FAILED`, `SETTLEMENT_FAILED`,
  `REVERSED`. An illegal transition throws; there is no `setState()`.
- No provider call inside a database transaction. Cash-in, cash-out and reversal run
  as TX-1 / provider I/O / TX-2, holding zero connections across the ~1.4 s of provider
  latency. The response to a measured p95 of 60 s and a 73.93 % error rate at 25 req/s,
  caused by a single `@Transactional` spanning the whole operation
  ([ADR-004](./docs/adr/ADR-004-micro-transaction-optimization.md)). The post-fix
  baseline has not been re-measured — that run belongs on staging, not a laptop.

### 3️⃣ Command bus with three middlewares
- Correlation id propagated into every log line, born at the HTTP edge
- Command validation before any write
- Anti-replay over a TTL window
- A command with no registered use case **stops the application from booting**,
  rather than failing on the one request that needs it

### 4️⃣ Retry scoped to what is safe to replay
- Optimistic clashes and Postgres deadlocks arrive as one retryable type
- Backoff is jittered, so two transactions that collided do not collide again on the
  same schedule
- The retry wraps the transaction boundary, never the reverse, and never spans the
  provider call — replaying that would charge twice

---

## 🚧 On the roadmap

Not built. Listed here because the architecture is shaped to receive them, and
`ROADMAP.md` says when.

| | | Étape |
|---|---|---|
| **Idempotency** | duplicate-charge protection under retry storms | 4 |
| **Event-driven consistency** | outbox, at-least-once delivery, idempotent consumers | 5 |
| **Reconciliation engine** | provider report ingestion, mismatch and orphan detection, manual review | 6 |
| **Multi-provider orchestration** | fallback, circuit breaker, bulkhead isolation | 8 |
| **Risk & velocity controls** | daily and hourly limits, suspicious activity, hard stop vs review | 8 |
| **Cloud-native scaling** | Kubernetes, HPA, metrics-driven scaling | 9 |

The provider today is a latency-simulating stub, not an HTTP client. That is
deliberate: it makes the failure modes reproducible in a test.

---

## 📊 Target Scale Evolution

KORA Core simulates progressive growth from:

- 5,000 transactions/day  
to  
- 500,000+ transactions/day  

With realistic operational constraints:

- 10 → 1000+ requests/second peak
- Increasing DB QPS under contention
- Retry storm scenarios
- Provider latency between 200ms and 2s
- Settlement mismatch detection
- Batch reconciliation under heavy IO

Architecture evolves only when transaction volume and business complexity justify it.

No premature microservices.  
No architectural theater.

---

## 🔐 Security Principles

Security is embedded from day one.

**In place:**
- Immutable financial records; strict state transition validation
- Replay protection on the command bus, over a TTL window
- PINs hashed with bcrypt; JWT with refresh tokens; `ROLE_CUSTOMER` / `ROLE_ADMIN`
- **No sensitive data in logs** — `Pin`, `Msisdn`, `PhoneNumber` and `OtpCode` each
  redact themselves in `toString()`, so a record that happens to hold one is covered
  without anyone remembering to be careful. A test dispatches a command carrying a PIN
  through the bus, captures the log output, and asserts the type is named and neither
  the PIN nor the customer id appears.
- Secrets have no fallback value: a clone without `.env` refuses to boot rather than
  starting with a publicly readable signing key. A test enforces that.
- Tamper-aware audit trail: every state change writes an immutable row

**Planned:** idempotency-first design (Étape 4), rate limiting and abuse mitigation,
zero-trust inter-service communication once anything is distributed.

In fintech, security failures are trust failures.

---

## 🧠 Engineering Philosophy

In fintech:

Correctness beats cleverness.  
Resilience beats hype.  
Financial integrity beats architectural fashion.

KORA Core grows in complexity only when business reality demands it.

Every architectural decision is justified by:

- Transaction volume
- Operational risk
- Financial integrity
- User trust

---

## 🌍 Vision

KORA Core represents my commitment to building:

- African-rooted
- Globally competitive
- Institution-grade financial systems

The long-term ambition is clear:

To operate at the engineering standard required by the best fintech companies in Africa, Europe, and the United States.

---

## 🚧 Status

Active development.  
Architecture evolves alongside simulated scale and operational constraints.

---

## 📌 License

MIT (for learning, experimentation, and transparency).

## 🚀 Getting Started

```bash
cp .env.example .env
docker compose up -d
./gradlew bootRun          # :8081 — http://localhost:8081/swagger-ui.html
./gradlew build            # compile + the full suite
```

| Document | Purpose |
|---|---|
| [CONTRIBUTING.md](./CONTRIBUTING.md) | Local setup, running the app, running the tests, conventions |
| [CLAUDE.md](./CLAUDE.md) | The invariants, the vocabulary, and the rules a review applies to a diff |
| [HELP.md](./HELP.md) | Engineering standards: testing strategy, DDD and SOLID as practised here |
| [ROADMAP.md](./ROADMAP.md) | The ten étapes, and where the project stands in them |
| [perf/PERF.md](./perf/PERF.md) | Load testing runbook (k6 + Grafana) |

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