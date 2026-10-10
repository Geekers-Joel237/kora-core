# 🏦 KORA CORE


**Product**: Neobank wallet (Mobile Money + Card-ready):

P2P, cash-in/out, merchant payment, multi-provider, deferred settlement, reconciliation, risk, observability, cloud orchestration.

> **The numbering in this file is authoritative.** `CLAUDE.md` refers to these steps and
> numbers none of its own; the two files used "Step 2" to mean two different things
> long enough for it to cost us. See ADR-007.
>
> **Status**: Steps 0, 1 and 3 delivered — Step 3 before Step 2, see ADR-007.
> **Step 2 is open**: the folder split is done and its boundary is asserted, but the
> modular monolith in the sense of this step is not.

---

# 🧱 STEP 0 — Conscious transactional monolith

**(Weeks 1–2)**

## 🎯 Business context

We are launching the core of a wallet:

- account creation
- deposit (cash-in)
- withdrawal (cash-out)
- P2P transfer

## ✅ Value for a Djamo-type neobank

Without solid financial foundations, everything else is fragile: support, disputes, audits, reputation.

This step guarantees **financial consistency** and **auditability** from the start.

## 📊 Volume targets

- **5,000 tx/day**
- **5–10 req/sec** (peak)
- **P95 < 150 ms**
- **DB QPS ~ 30–60**

## 🔐 Security constraints (strong, from the start)

- Password hashing (Argon2/bcrypt) + password policies
- JWT auth (rotation/expiry) + refresh tokens
- Strict input validation + injection protection
- Secret encryption (no hard-coded secrets)
- Logs **without PII** and without secrets
- OWASP Top 10 baseline

## 🔧 Technical work

- **Double-entry ledger** (immutable entries)
- **No direct "balance update"**: balance = sum of the ledger
- Indexes on account_id, txn_id
- Financial invariant tests (debits==credits)

## 🧠 Questions you must answer

- Why must a balance never be "updated" directly?
- How do you rebuild a balance after an incident?
- Which indexes are essential?
- What is the plan if an entry is duplicated?

## 🎯 Skill acquired

**Financial integrity mindset** (neobank foundation).

---

# 🧭 STEP 1 — Real payment lifecycle

**(Weeks 3–4)**

## 🎯 Business context

A real payment is not instantaneous.

It goes through states:

INITIATED → AUTHORIZED → CAPTURED → SETTLED (+ FAILED/REVERSED).

In Africa, **settlement can be deferred (D+1)** depending on the provider.

## ✅ Value for Djamo

- Reduce disputes ("my debit went through but…")
- Make states explainable to support
- Build a foundation for chargeback/reversal

## 📊 Volume targets

- **15,000 tx/day**
- **20–30 req/sec**
- **P95 < 200 ms**
- **DB QPS ~ 100–150**

## 🔐 Security constraints

- Robust status management (no illegal transitions)
- Anti-replay (nonce/correlation) on sensitive operations
- Audit logging: *who/what/when* (without PII leakage)

## 🔧 Work

- Strict state machine (transition validation)
- Persisted states + timestamps
- Reversal handling

## 🧠 Questions

- Difference between CAPTURED and SETTLED?
- When is a payment "final"?
- How do you model reversal and chargeback?

## 🎯 Skill acquired

**Money lifecycle thinking** (beyond CRUD).

---

# 🧩 STEP 2 — Disciplined modular monolith

> **Open. Started after Step 3, not finished.** See ADR-007 for the order:
> splitting into modules on top of a still-blurry layering would have frozen the
> framework dependency into the module contract.
>
> **Done (phase A)**: the three folders `auth`, `payment`, `shared`; the kernel names
> no module; the remaining couplings are recorded as an **equality** in
> `ModuleBoundariesTest`, so adding one fails the build and so does closing one.
>
> **Still to do, and it is the heart of the step**:
> - **real Spring Boot applications per module.** Today there is a single Gradle
>   project, a single `@SpringBootApplication`, a single classpath: nothing technically
>   prevents an import, only a test flags it.
> - **non-shared entities.** `payment` reaches `auth.domain.model.Customer` and
>   `auth.domain.vo.PhoneNumber`; `auth` reaches `payment.domain.model.Account`.
>   Eleven classes cross, listed in `ModuleBoundariesTest`. A module whose aggregate
>   gets imported has no boundary, it has a folder.
>
> The numbering of this document is kept as is — renumbering a document that has
> already been read costs more than noting the actual order.

**(Weeks 5–6)**

## 🎯 Business context

Features pile up: payment, ledger, provider, risk, reconciliation.

Without an internal split, delivery speed drops.

## ✅ Value for Djamo

- Allow multiple teams without chaos
- Make the system evolvable **before** microservices
- Speed up delivery without breaking the core

## 📊 Volume targets

- **30,000 tx/day**
- **50 req/sec** (peak)
- **P95 < 250 ms**
- **DB QPS ~ 250–350**

## 🔐 Security constraints

- Authorization **per module** (e.g. the risk module is never directly accessible)
- Strict separation of data access (ownership)
- Internal RBAC for admin operations

## 🔧 Work

- Internal modules: Ledger / Payments / Providers / Risk / Reconciliation
- Explicit interfaces between modules
- Module-to-module integration tests

## 🧠 Questions

- Where are the business boundaries?
- Who owns the ledger?
- Which modules are latency-critical?

## 🎯 Skill acquired

**Domain decomposition under growth**.

---

# 🧱 STEP 3 — Hexagonal migration (pragmatic clean architecture)

> **Done first, before Step 2.** See ADR-007. This is also the batch that settles the
> debt left by ADR-004: `application/` and `ports/` no longer name any foreign type,
> which `HexagonalArchitectureTest` checks by equality against an empty allow-list.

**(Weeks 7–9)**

## 🎯 Business context

Multi-provider + business tests + provider simulation.

The infrastructure changes; the domain must not move.

## ✅ Value for Djamo

- Change a provider without breaking the system
- Test the core without depending on the DB
- Reduce regressions on the money flow

## 📊 Volume targets

- **50,000 tx/day**
- **80–100 req/sec**
- **P95 < 300 ms**
- **DB QPS ~ 400–550**

## 🔐 Security constraints

- Zero trust between adapters (validation & signature)
- Clean secrets management (vault/env)
- Hardened configs (CORS, headers, rate limit)

## 🔧 Work

- Pure domain (no Spring)
- Ports & adapters (DB, HTTP, providers)
- Exhaustive business tests + adapter tests

## 🧠 Questions

- Why hexagonal now?
- How do you replace a provider without changing the domain?
- What must stay stable in the core?

## 🎯 Skill acquired

**Architecture oriented toward change & testability**.

---

# 🔁 STEP 4 — Idempotency & network reality (anti double-debit)

**(Weeks 10–11)**

## 🎯 Business context

Unstable network + retries.

Fintech is unforgiving: **double debit = crisis**.

## ✅ Value for Djamo

- Protection against double debit
- Fewer support tickets and disputes
- Robustness under real African conditions

## 📊 Volume targets

- **70,000 tx/day**
- **150 req/sec** peak
- **P95 < 350 ms**
- **DB QPS ~ 700–900**
- **Simulated retry rate: 5–10%**

## 🔐 Security constraints

- Idempotency keys signed/bound to user/session
- Strict rate limiting (anti-abuse)
- Brute-force / credential stuffing protection
- Immutable audit on financial operations

## 🔧 Work

- idempotency_log table + index
- end-to-end correlation IDs
- retry w/ jitter + timeout policies
- "late confirmation" provider simulation

## 🧠 Questions

- What to do if the provider answers after the timeout?
- Exactly-once vs at-least-once?
- How do you prevent client-side replay?

## 🎯 Skill acquired

**Failure-aware payment engineering**.

---

# 🔄 STEP 5 — Internal event-driven + Outbox (without overengineering)

**(Weeks 12–13)**

## 🎯 Business context

A payment triggers: notification, risk update, reporting.

Synchronous calls create coupling and latency.

## ✅ Value for Djamo

- Decouple without multiplying services
- Reduce latency on the payment path
- Prepare for scalability without premature ops complexity

## 📊 Volume targets

- **100,000 tx/day**
- **250 req/sec** peak
- **~1,000 events/sec** (peak)
- **DB QPS ~ 1,200–1,600**
- **P95 < 400 ms**

## 🔐 Security constraints

- Minimal event payload (no PII)
- Event signing/versioning
- Consumer idempotence + anti-replay
- Least privilege for publishers/consumers

## 🔧 Work

- transactional outbox table
- batched publisher
- idempotent consumers + processed_event table
- DLQ + retry strategy

## 🧠 Questions

- Dual-write problem?
- How do you handle backlog/lag?
- How do you version events?

## 🎯 Skill acquired

**Distributed thinking without a microservices explosion**.

---

# 🔍 STEP 6 — Reconciliation engine (institutional)

**(Weeks 14–15)**

## 🎯 Business context

Internal ledger ≠ provider reports (CSV).

D+1 settlement.

This is a neobank's daily routine.

## ✅ Value for Djamo

- Detection of financial discrepancies
- Reduction of losses & fraud
- Foundation for accounting close and audit

## 📊 Volume targets

- **120,000 tx/day**
- Nightly batch of **120k rows**
- Matching time **< 10 min**
- Batch DB QPS **2,000–5,000** (burst)

## 🔐 Security constraints

- Encryption/access control on provider reports
- Audit trail on every resolution action
- Strict "manual review" permissions

## 🔧 Work

- Provider report import
- Matching (provider_ref, amount, timestamp windows)
- States: MATCHED / MISMATCH / MISSING_INTERNAL / MISSING_PROVIDER
- Report API + manual review queue

## 🧠 Questions

- Which rules are automatic vs manual?
- How do you avoid false positives?
- How do you escalate without blocking the product?

## 🎯 Skill acquired

**Fintech operations maturity**.

---

# 🚀 STEP 7 — Reconciliation microservice extraction

**(Weeks 16–18)**

## 🎯 Business context

Reconciliation = heavy, asynchronous, isolatable batch.

It is the right candidate for a microservice.

## ✅ Value for Djamo

- Isolate the batch workload (do not impact real-time payments)
- Independent evolution/scaling
- Operational resilience

## 📊 Volume targets

- **150,000 tx/day**
- **300–400 req/sec** peak (core)
- Isolated reconciliation batch
- Controlled inter-service latency

## 🔐 Security constraints

- Inter-service AuthN/AuthZ (mTLS / service-to-service JWT)
- Network isolation (policies)
- Event contract signing & versioning

## 🔧 Work

- Separate DB
- Versioned event contract
- Distributed tracing
- Lag + retry monitoring

## 🧠 Questions

- How do you avoid breaking changes?
- How do you replay events?
- How do you correlate a cross-service incident?

## 🎯 Skill acquired

**Pragmatic microservices extraction**.

---

# 🌐 STEP 8 — Multi-provider orchestration + Risk/Velocity (business security)

**(Weeks 19–20)**

## 🎯 Business context

Providers are unstable. Fallback is required.

And fraud grows with volume.

## ✅ Value for Djamo

- Service continuity when a provider is down
- Risk control (fraud, abuse)
- Trust and compliance

## 📊 Volume targets

- **200,000 tx/day**
- **500 req/sec** peak
- Provider latency 200ms → 2s
- P95 < 450ms (excluding provider)
- DB QPS ~ 2,500–3,500

## 🔐 Security constraints (strong)

- Velocity limits (daily/hourly)
- Anomaly flags (suspicious patterns)
- Circuit breaker + bulkhead
- Device/session binding (anti-replay)
- Full audit of risk decisions

## 🔧 Work

- Circuit breaker/fallback strategy
- Bulkhead isolation of provider pools
- Risk engine: hard limit / soft limit / manual review

## 🧠 Questions

- When do you trigger the fallback?
- How do you avoid a double debit on fallback?
- Hard vs soft limits?
- How do you reduce fraud false positives?

## 🎯 Skill acquired

**Resilience + risk-aware leadership**.

---

# ☁️ STEP 9 — Containerization & cloud orchestration (K8s)

**(Weeks 21–23)**

## 🎯 Business context

5x growth → horizontal scaling + safe deployments.

## ✅ Value for Djamo

- Fast and safe deployment
- Automatic scaling on peaks
- Reduced MTTR

## 📊 Volume targets

- **300,000 tx/day**
- **700–800 req/sec** peak
- P95 < 450ms
- DB QPS ~ 3,000–5,000

## 🔐 Security constraints

- K8s secrets (sealed/managed)
- Network policies
- Minimal cluster RBAC
- Image scanning (SCA)
- Non-root containers

## 🔧 Work

- Prod-grade Docker
- K8s deploy + HPA
- Readiness/liveness
- CI/CD + rollback

## 🧠 Questions

- DB bottleneck: how do you mitigate it?
- Read replicas / cache / partitioning?
- Rollback strategy?

## 🎯 Skill acquired

**Cloud operational readiness**.

---

# 📊 STEP 10 — Observability + business KPIs (lead mindset)

**(Weeks 24–26)**

## 🎯 Business context

Management does not want "CPU at 80%".

They want to know: *is the money flowing correctly?*

## ✅ Value for Djamo

- Steering by business risk
- Proactive incident detection
- Continuous improvement (SLO/SLA)

## 📊 Volume targets

- **500,000 tx/day**
- **1000+ req/sec** peak
- **P95 < 500ms** (end-to-end, excluding extreme provider cases)
- **DB QPS ~ 5,000–8,000**

## 🔐 Security constraints

- Centralized logs without PII
- Tamper-proof audit trail
- Alerting on fraud/risk patterns
- Access control on dashboards

## 🔧 Work

- Business metrics: settlement delay, mismatch rate, retry rate, provider error rate
- SLOs: success rate, latency budget
- Distributed tracing + correlation IDs
- Incident playbooks

## 🧠 Engineering Lead questions

- Which technical KPI reflects a financial risk?
- What SLO for a "successful payment"?
- When do you declare an incident vs a controlled degradation?
- What capacity plan?

## 🎯 Skill acquired

**Engineering leadership + product/risk alignment**.

---

## 🏁 Final outcome (what Djamo will see)

Not "a dev who loves Kafka".

But a future lead who knows how to:

- build a correct financial core
- introduce modularity, then hexagonal, when justified
- master the network + idempotency (African reality)
- handle reconciliation (institutional)
- extract microservices intelligently
- orchestrate in the cloud when volume demands it
- secure the system at every level
- steer by business KPIs

---
