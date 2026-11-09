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

# 🗺️ ROADMAP EXTENSION — Identity, real-time risk, data intelligence and WalletOps

> **Append-only extension.** Steps 0–10 above remain unchanged and authoritative.
> The sequence below is dependency-driven rather than purely chronological. A step may be
> started earlier when its prerequisites are satisfied, but its exit gate must not be claimed
> before the listed dependencies are complete.
>
> **Recommended insertion order in the delivery plan:** complete Step 2 first; perform Steps
> 11–15 before exposing the wallet to real customers; perform Steps 16–18 after Step 5; perform
> Steps 19–22 after reliable event contracts and observability exist; introduce WalletOps only
> after stable read models, authorization and audit are available.

---

# 🪪 STEP 11 — Customer identity, registration and progressive KYC

**Depends on:** Steps 0, 2 and 3. **Best scheduled after:** Step 4; before advanced risk and WalletOps.

## 🎯 Business context

A phone number is an alias and communication channel, not the durable identity of a customer.
The wallet must support low-friction entry while increasing verification before financial
exposure increases.

## ✅ Product value

- Resume an interrupted registration safely
- Apply capabilities and limits according to verification level
- Change a phone number without changing the customer identity
- Provide an auditable basis for compliance review

## 🔧 Sub-steps

### 11.1 — Registration state machine

Model `STARTED → PHONE_PENDING → PHONE_VERIFIED → PIN_PENDING → DEVICE_PENDING →
KYC_PENDING → READY_FOR_ACTIVATION → ACTIVE`, plus expiry, rejection and cancellation.
Every transition is idempotent, timestamped and linked to a policy version.

### 11.2 — Stable identity and aliases

Introduce `CustomerId`, `WalletId`, `PhoneAlias`, verified contact status and alias history.
Normalize phone numbers, prevent ambiguous ownership and never use the phone number as the
JWT subject or database aggregate identifier.

### 11.3 — Progressive KYC

Create internal levels such as BASIC, VERIFIED_IDENTITY and ENHANCED. Keep level, current
status and customer risk tier separate. Capabilities and limits come from a versioned policy,
not from hard-coded conditionals.

### 11.4 — KYC case management

Support document submission references, automated checks, manual review, requests for further
information, approval, rejection, expiry and re-verification. Raw documents remain outside
Kafka and outside domain events.

## 🔐 Security and regulatory constraints

- Encrypt identity documents and strictly audit access
- Minimize and classify PII
- Version country, product, customer type and effective-date policies
- Separate KYC approval from wallet authorization and sanctions/fraud controls
- Validate applicable BEAC/BCEAO and national requirements with compliance before production

## 🧪 Exit gate

A customer can stop and resume onboarding; phone replacement does not create a new customer;
all decisions identify their policy version; sensitive documents never appear in logs/events;
and activation is impossible when mandatory checks are incomplete.

## 🧠 Questions

- Which capability is available at each assurance level?
- What happens when a document expires after activation?
- How are policy changes applied to existing customers?

## 🎯 Skill acquired

**Identity lifecycle and regulatory product design**.

---

# 🔑 STEP 12 — PIN and device-bound authentication

**Depends on:** Steps 3, 4 and 11. **Best scheduled after:** Step 11.1–11.2; before Step 13.

## 🎯 Business context

Phone + PIN is familiar but insufficient as the entire security model. On smartphones, the
wallet adds a cryptographic proof of possession generated by the device.

## 🔧 Sub-steps

### 12.1 — PIN credential

Use a six-digit PIN when product constraints permit, adaptive hashing, unique salt, optional
HSM/KMS-backed pepper, server-side counters, progressive cooldown and rejection of trivial
PINs. The PIN is excluded from `toString`, logs, events, traces and replay tools.

### 12.2 — Device enrollment challenge

The server issues a short-lived, one-time challenge. Android Keystore or Apple Secure Enclave
generates the key pair locally. Only the public key, proof-of-possession signature and optional
attestation are returned.

### 12.3 — Credential registry

Store credential identifier, customer, public key, algorithm, attestation level, status,
binding time, last use and revocation metadata. Model ACTIVE, SUSPENDED, REPORTED_LOST,
REVOKED and EXPIRED explicitly.

### 12.4 — Authentication ceremony

Combine phone alias, PIN, signed challenge, device status and risk context. Challenges are
purpose-bound, audience-bound, single-use and replay-protected.

## 🧪 Exit gate

The private key never reaches the backend; a copied public key is insufficient; a replayed
challenge is rejected; a revoked device cannot authenticate; and PIN brute-force controls are
server-enforced.

## 🎯 Skill acquired

**Phishing-resistant device possession layered over usable wallet authentication**.

---

# 🎟️ STEP 13 — Security context, session lifecycle, logout and step-up

**Depends on:** Step 12. **Best scheduled after:** Step 12; before Step 14 and transaction risk.

## 🔧 Sub-steps

### 13.1 — Security context

Represent customer, wallet, session, device credential, authentication methods, assurance
level, KYC level, risk level and authentication time. Authorization still verifies wallet
ownership and transaction context.

### 13.2 — Token lifecycle

Use short-lived access tokens, strict issuer/audience/time validation, refresh-token rotation,
replay detection, absolute session lifetime and cryptographic binding to the registered session
where supported.

### 13.3 — Logout and revocation

Distinguish logout, remove-device, revoke-all-sessions, suspend-account and close-wallet.
Logout revokes the server session and refresh family; access-token residual lifetime is bounded
and documented.

### 13.4 — Risk-based step-up

Require new proof for high-value transfers, new beneficiaries, security-setting changes,
recent recovery and suspicious context. Store the reason, methods and decision version.

## 🧪 Exit gate

Refresh-token reuse revokes its family; logout has a demonstrable effect; valid tokens cannot
access another customer's wallet; old authentication cannot authorize a configured sensitive
action.

## 🎯 Skill acquired

**End-to-end trust maintenance after login**.

---

# 🛟 STEP 14 — Lost device, PIN reset and account recovery

**Depends on:** Steps 11–13. **Best scheduled after:** Step 13; before large-scale customer use.

## 🔧 Sub-steps

### 14.1 — Recovery risk classification

Separate known-device migration, lost-device recovery and total lockout. Evaluate PIN
knowledge, phone control, SIM/device changes, KYC status, wallet exposure and fraud signals.

### 14.2 — Identity re-proofing

Low-risk recovery may combine PIN, verified phone and consistent context. Higher-risk recovery
requires stronger identity evidence or manual/agent review. Security questions are not used.

### 14.3 — Restricted recovery session

Issue a purpose-limited session that can bind a new credential and, when authorized, reset the
PIN. It cannot transfer funds, add beneficiaries or change limits.

### 14.4 — New credential and revocation

Generate a fresh key pair on the new device, revoke old device credentials and refresh-token
families, notify existing channels and apply a configurable post-recovery restricted state.

## 🧪 Exit gate

No private key is copied; recovery cannot directly move money; old sessions and devices are
revoked; recovery decisions and human overrides are fully auditable.

## 🎯 Skill acquired

**Secure re-establishment of trust after authenticator loss**.

---

# 🧾 STEP 15 — Continuous KYC, compliance policy and wallet restrictions

**Depends on:** Steps 11, 13 and 14. **Best scheduled after:** Step 11; before model-based risk.

## 🔧 Sub-steps

### 15.1 — Policy engine

Resolve limits and controls by regulatory zone, country, product, customer type, KYC level,
channel, effective date and policy version.

### 15.2 — Ongoing due diligence

Trigger review for document expiry, risky recovery, changed identity attributes, higher product
access, significant behavioral changes and policy updates.

### 15.3 — Restriction model

Distinguish LIMITED, DEBIT_BLOCKED, CREDIT_BLOCKED, FROZEN and CLOSED. A verified identity does
not imply an unrestricted wallet.

### 15.4 — Decision evidence

Persist inputs, policy version, reason codes, reviewer and effective period without copying
unnecessary PII.

## 🧪 Exit gate

The same historical decision is reproducible with its version; an expired or review-required
profile follows configured restrictions; compliance actions never rewrite ledger history.

## 🎯 Skill acquired

**Policy-as-data and continuous compliance engineering**.

---

# 📨 STEP 16 — Kafka platform engineering and durable event contracts

**Depends on:** Steps 2, 5 and 10. **Best scheduled after:** Step 5; before Kafka Streams.

## 🔧 Sub-steps

### 16.1 — Event envelope and taxonomy

Standardize event id, type, schema version, occurrence time, aggregate id, correlation id and
causation id. Separate domain facts from integration representations.

### 16.2 — Topic and partition design

Create domain topics for customer, authentication, device, KYC, transaction, ledger,
compliance, provider and risk. Document ordering guarantees and keys such as walletId,
customerId or deviceCredentialId.

### 16.3 — Schema evolution

Use a schema registry and compatibility policy. Test additive and breaking changes, consumer
upgrade order and replay from historical versions.

### 16.4 — Operational safety

Enforce ACLs, quotas, encryption, lag monitoring, poison-event handling, idempotent consumers,
replay runbooks and PII minimization. A DLQ without ownership and resolution workflow is not a
solution.

## 🧪 Exit gate

A schema evolves without breaking a supported consumer; replay produces no duplicate business
effect; lag and failed-event ownership are visible; secrets and raw identity documents are absent.

## 🎯 Skill acquired

**Kafka as an governed event backbone, not merely a broker**.

---

# 🌊 STEP 17 — Kafka Streams and real-time feature computation

**Depends on:** Step 16; uses events from Steps 12–15 and payments. **Best scheduled before:** Step 18.

## 🔧 Sub-steps

### 17.1 — Velocity features

Compute failed PINs, logins per device, devices per customer, cumulative amounts, beneficiaries
added, cash-in/cash-out proximity and operations following recovery across explicit windows.

### 17.2 — Stateful processing

Use KStream/KTable joins, window stores, changelog topics and deterministic keying. Document
event time, processing time, grace periods, late-event treatment and state restoration.

### 17.3 — Feature views

Materialize read-optimized current features for the synchronous Risk API while preserving a
historical feature snapshot reference for audit and backtesting.

### 17.4 — Resilience and tests

Test topology results, repartitioning, consumer rebalance, restart, state restoration, duplicate
input and late arrival. Benchmark latency and throughput separately from the payment API.

## 🧪 Exit gate

A restart restores state; late events follow a declared policy; the same bounded input produces
the same feature snapshot; no request-time scan of raw histories is required.

## 🎯 Skill acquired

**Stateful stream processing and real-time feature engineering**.

---

# 🛡️ STEP 18 — Real-time risk decisioning

**Depends on:** Steps 8, 15 and 17. **Best scheduled after:** deterministic limits in Step 8 and before ML.

## 🔧 Sub-steps

### 18.1 — Deterministic controls

Evaluate frozen wallet, revoked device, replay, insufficient KYC, product limits, blocked
beneficiary and expired policy prerequisites.

### 18.2 — Velocity and behavioral rules

Combine real-time features with amount deviation, unusual time/channel, new beneficiary,
recent recovery and device trust.

### 18.3 — Decision contract

Return ALLOW, ALLOW_WITH_MONITORING, STEP_UP, HOLD, MANUAL_REVIEW, DECLINE or FREEZE, together
with reason codes, policy version and feature snapshot id.

### 18.4 — Availability policy

Declare fail-open or fail-closed behavior per operation. Provide cached or deterministic fallback
when streaming features are unavailable; never silently bypass hard financial controls.

## 🧪 Exit gate

Every decision is explainable and reproducible; failure modes are tested; risk cannot write the
ledger; step-up integrates with Step 13.

## 🎯 Skill acquired

**Low-latency, auditable financial risk decisioning**.

---

# 🕵️ STEP 19 — Fraud scenarios and behavioral intelligence

**Depends on:** Steps 17–18. **Best scheduled before:** machine-learning models.

## 🔧 Sub-steps

### 19.1 — Scenario library

Implement testable scenarios for account takeover, suspicious SIM/device replacement, mule
accounts, structuring, device farming, agent fraud, rapid cash-in/cash-out, circular movement
and unusual post-recovery activity.

### 19.2 — Synthetic datasets

Generate documented normal and fraudulent journeys without using production PII. Include event
order changes, duplicates, late events and ambiguous cases.

### 19.3 — Investigation cases

Turn severe or composite signals into cases with timeline, evidence references, current status,
assignee, decision and resolution reason.

### 19.4 — Quality measurement

Track precision, recall, false-positive burden, detection latency, review volume, prevented loss
and customer friction by scenario and policy version.

## 🧪 Exit gate

Each scenario has fixtures, expected decisions and measurable false positives; analysts can trace
an alert to its source events and feature snapshot.

## 🎯 Skill acquired

**Fraud typology translated into measurable software behavior**.

---

# 🏞️ STEP 20 — CDC, lakehouse and historical feature engineering

**Depends on:** Steps 10 and 16. **Best scheduled after:** stable schemas; before ML training.

## 🔧 Sub-steps

### 20.1 — Data zones

Separate raw, validated, curated, feature and serving zones. Kafka is transport and operational
history, not the analytical warehouse.

### 20.2 — CDC and sinks

Capture approved source changes and event streams with explicit contracts, deduplication,
lineage and reconciliation against transactional counts.

### 20.3 — Historical features

Build point-in-time correct datasets for transaction velocity, customer behavior, device
relationships and provider performance. Prevent future-data leakage.

### 20.4 — Governance

Implement classification, encryption, pseudonymization, retention, deletion workflow, access by
purpose, data-quality checks and audited analyst access.

## 🧪 Exit gate

A risk feature can be traced to source events; a historical snapshot can be reconstructed;
quality failures stop promotion; deletion/retention behavior is tested.

## 🎯 Skill acquired

**Governed analytical data engineering for regulated systems**.

---

# 🕸️ STEP 21 — Graph analytics for coordinated fraud

**Depends on:** Steps 19–20. **Best scheduled after:** reliable entity resolution.

## 🔧 Sub-steps

### 21.1 — Graph model

Model customer–wallet ownership, customer–device use, wallet transfers, agent onboarding,
phone aliases, beneficiaries and observed network relationships using pseudonymous identifiers.

### 21.2 — Graph signals

Detect shared devices, beneficiary hubs, dense suspicious communities, cycles, rapid fan-in/fan-
out and agent-centered clusters.

### 21.3 — Streaming-to-graph synchronization

Publish idempotent relationship updates and define consistency expectations. Graph results enrich
risk; they do not become financial source of truth.

## 🧪 Exit gate

A coordinated-fraud scenario is detected where single-wallet rules fail; graph relationships
are explainable, access-controlled and traceable to source events.

## 🎯 Skill acquired

**Relationship-based financial crime detection**.

---

# 🤖 STEP 22 — Model-based risk scoring and model governance

**Depends on:** Steps 19–21. **Best scheduled only after:** rules, labels and evaluation exist.

## 🔧 Sub-steps

### 22.1 — Baselines first

Compare deterministic rules and simple statistical baselines before gradient boosting,
anomaly detection or deep models.

### 22.2 — Training discipline

Use temporal splits, point-in-time features, class-imbalance treatment and calibrated
probabilities. Separate fraud probability from the policy action threshold.

### 22.3 — Safe deployment

Run shadow mode, champion/challenger and canary policies. Store model version, feature snapshot,
score, threshold and reason contributions.

### 22.4 — Operations

Monitor drift, calibration, false positives, segment performance and business outcomes. Support
rollback and a deterministic no-model mode.

## 🧪 Exit gate

The model beats an agreed baseline without unacceptable customer friction; rollback is tested;
no model directly authorizes or executes a ledger movement.

## 🎯 Skill acquired

**Production ML for risk with governance and business accountability**.

---

# 🧮 STEP 23 — Advanced settlement and reconciliation case management

**Depends on:** Steps 6–7, 16 and 20. **Best scheduled after:** the extracted reconciliation service is stable.

## 🔧 Sub-steps

### 23.1 — Rich break taxonomy

Add duplicate provider transaction, amount/currency/fee mismatch, late settlement, reversal
mismatch and orphaned internal/provider records.

### 23.2 — Matching layers

Apply exact matching first, then controlled tolerance/window rules. Probabilistic suggestions
remain reviewable and never rewrite ledger history.

### 23.3 — Case workflow

Support assignment, evidence, maker-checker approval, comments, SLA, escalation and compensating
posting references.

### 23.4 — Close and audit

Produce settlement completeness metrics and a reproducible record of how each break was resolved.

## 🧪 Exit gate

At least five break categories are detected; corrections use compensating entries; every manual
resolution carries actor, evidence, reason and approval.

## 🎯 Skill acquired

**Institution-grade settlement operations and exception management**.

---

# 🧯 STEP 24 — Security operations, SRE and resilience validation

**Depends on:** Steps 9–10 and all deployed critical paths. **Best scheduled continuously; formal gate after:** Step 18.

## 🔧 Sub-steps

### 24.1 — Security supply chain

Add SAST, dependency scanning, SBOM, container scanning, artifact signing, secret scanning and
least-privilege deployment identities.

### 24.2 — Reliability engineering

Define service and business SLOs, error budgets, capacity tests, backup/restore evidence,
database recovery objectives and Kafka recovery runbooks.

### 24.3 — Failure injection

Exercise provider timeout, retry storm, database contention, outbox backlog, broker outage,
consumer poison event, risk-feature outage and reconciliation surge.

### 24.4 — Incident operations

Connect alerts to runbooks, incident roles, evidence preservation, customer impact, regulatory
escalation and post-incident review.

## 🧪 Exit gate

A full incident exercise detects, contains, recovers and reconciles financial state; restoration,
credential rotation and rollback are demonstrated rather than assumed.

## 🎯 Skill acquired

**Operational ownership of regulated financial infrastructure**.

---

# 🧠 STEP 25 — WalletOps foundation and authorized investigation views

**Depends on:** Steps 10, 15, 18, 19, 23 and 24. **Best scheduled after:** deterministic systems and read models are stable.

## 🎯 Business context

Fraud, KYC, support and reconciliation analysts need one evidence-oriented operational surface.
WalletOps assists them without bypassing domain authorization or mutating the ledger.

## 🔧 Sub-steps

### 25.1 — Investigation read model

Expose customer timeline, authentication/device history, KYC cases, transactions, risk decisions,
provider events and reconciliation breaks through scoped read models.

### 25.2 — Case workspace

Support evidence links, notes, assignments, status, SLA, approvals and immutable action audit.

### 25.3 — AI authority boundary

Allow search, extraction, comparison, summarization and recommendation. Forbid direct transfer,
ledger mutation, autonomous KYC approval, unfreeze and audit deletion.

## 🧪 Exit gate

Every displayed fact links to an authoritative source; tenant/customer scope is enforced;
WalletOps can be disabled without affecting payment execution.

## 🎯 Skill acquired

**Safe operational platform design over a financial core**.

---

# 📄 STEP 26 — KYC Document Intelligence

**Depends on:** Steps 11, 15, 20 and 25. **Best scheduled after:** the manual KYC workflow is correct.

## 🔧 Sub-steps

### 26.1 — Secure ingestion

Upload outside the command bus, malware-scan, encrypt, classify and retain by policy. Domain
commands carry opaque document references.

### 26.2 — Extraction pipeline

Perform quality assessment, classification, OCR and typed field extraction with value,
confidence, source region, extractor version and review status.

### 26.3 — Validation and HITL

Run deterministic format/expiry/consistency checks. Route low-confidence, unsupported or
conflicting cases to human review.

### 26.4 — Evaluation

Measure normalized field accuracy, per-document performance, unreadable rate, escalation rate
and failures by capture condition and document type.

## 🧪 Exit gate

No document is approved only because a model is confident; field-level evaluation and reviewer
override exist; raw documents never leak into prompts, events or logs outside approved controls.

## 🎯 Skill acquired

**Evaluated human-supervised intelligent document processing**.

---

# 📚 STEP 27 — Regulatory and operational RAG

**Depends on:** Steps 15, 24 and 25. **Best scheduled before:** agents or autonomous workflows.

## 🔧 Sub-steps

### 27.1 — Governed corpus

Index approved regulations, internal policies, ADRs, runbooks, API contracts, event catalogues and
provider procedures with authority, jurisdiction, version and effective dates.

### 27.2 — Retrieval quality

Implement hybrid retrieval, metadata filters, reranking, source authority and access-control
filtering before text reaches the model.

### 27.3 — Grounded answers

Require citations, distinguish internal policy from external regulation, surface conflicting
versions and refuse answers without sufficient evidence.

### 27.4 — Adversarial evaluation

Test obsolete sources, conflicting policy, indirect prompt injection, missing evidence, ACL
bypass attempts and misleading near-matches.

## 🧪 Exit gate

A golden question set meets retrieval and citation thresholds; unauthorized documents are never
retrieved; the system refuses unsupported compliance conclusions.

## 🎯 Skill acquired

**Evidence-grounded knowledge systems for regulated operations**.

---

# 🗃️ STEP 28 — Safe Text2SQL and analytical investigation

**Depends on:** Steps 20, 25 and 27. **Best scheduled after:** curated read-only views exist.

## 🔧 Sub-steps

### 28.1 — Semantic layer

Publish approved business definitions and read-only investigation views rather than exposing
transactional tables.

### 28.2 — SQL guardrail pipeline

Classify intent, generate SQL, parse/validate the AST, enforce allowlists, customer/tenant scope,
row limits, timeout and cost limits, then execute with a read-only identity.

### 28.3 — Result governance

Redact protected columns, audit query and result metadata, label uncertainty and keep data output
separate from analyst conclusions.

## 🧪 Exit gate

Mutation, DDL, unrestricted joins and cross-scope access fail closed; adversarial prompts cannot
bypass the views; expensive queries are bounded.

## 🎯 Skill acquired

**Controlled natural-language access to financial analytics**.

---

# 🔀 STEP 29 — Persistent investigation workflows

**Depends on:** Steps 25–28. **Best scheduled before:** generalized tool/agent exposure.

## 🔧 Sub-steps

### 29.1 — Explicit state machine

Model CASE_CREATED → CONTEXT_COLLECTED → SIGNALS_ANALYZED → EVIDENCE_REQUESTED → HUMAN_REVIEW
→ RECOMMENDATION_READY → APPROVED → AUTHORIZED_ACTION_EXECUTED → CLOSED.

### 29.2 — Bounded orchestration

Set maximum steps, budgets, timeout, checkpointing, retries, idempotency and resumption. Prefer
workflows over unconstrained agent loops.

### 29.3 — Human authority

Require maker-checker approval for sensitive recommendations. Final actions are performed by
normal deterministic services using their existing authorization and audit.

## 🧪 Exit gate

A workflow survives interruption and resumes exactly once; no unauthorized tool is reached;
recommendations cite evidence and human overrides are retained.

## 🎯 Skill acquired

**Durable human-in-the-loop operational automation**.

---

# 🔌 STEP 30 — Secure MCP tool gateway

**Depends on:** Steps 13, 25 and 29. **Best scheduled after:** tool contracts and authorization are already proven.

## 🔧 Sub-steps

### 30.1 — Narrow tools

Expose small read or draft capabilities such as customer risk summary, transaction timeline,
device history, KYC case, reconciliation break and policy search.

### 30.2 — Identity and authorization

Propagate operator identity, OAuth/OIDC scopes, case/customer context and purpose. Apply RBAC/ABAC,
rate limits, input schemas, output redaction and complete audit.

### 30.3 — Mutation boundary

Do not expose direct ledger mutation, transfer execution, autonomous KYC approval, unrestricted
unfreeze or audit deletion. Sensitive actions remain explicit application commands with approval.

## 🧪 Exit gate

Authorization and replay tests pass; scopes are minimal; each tool invocation records actor,
purpose, arguments, result and policy decision.

## 🎯 Skill acquired

**Enterprise tool integration without surrendering domain authority**.

---

# 🧪 STEP 31 — WalletOps evaluation, governance and human oversight

**Depends on:** Steps 26–30. **Best scheduled before:** any production release of WalletOps AI.

## 🔧 Sub-steps

### 31.1 — Evaluation datasets

Build golden, edge, adversarial, outdated-policy, missing-evidence, conflicting-source, PII-leakage
and unauthorized-tool cases.

### 31.2 — Metrics and release gates

Measure extraction accuracy, retrieval recall, citation correctness, groundedness, tool-selection
accuracy, unauthorized-tool rate, completion, override, latency, cost and leakage.

### 31.3 — Governance artifacts

Maintain model cards, data cards, prompt/tool versions, evaluation reports, approvals, risk
acceptance and ship/no-ship thresholds.

### 31.4 — Human oversight

Record reviewer agreement, disagreement and final action. Measure automation bias and ensure
operators can inspect evidence rather than seeing only a generated narrative.

## 🧪 Exit gate

Security, quality, authorization and cost regressions block release; a model or prompt change
cannot reach production without its evaluation record.

## 🎯 Skill acquired

**Measurable trust and governance for enterprise AI**.

---

# ⚙️ STEP 32 — LLMOps, AgentOps, FinOps and WalletOps production readiness

**Depends on:** Steps 24 and 31. **Best scheduled last; operated continuously thereafter.**

## 🔧 Sub-steps

### 32.1 — Runtime strategy

Route simple classification/redaction to small or local models, document extraction to specialized
models and complex synthesis to a stronger model. Provide deterministic fallback.

### 32.2 — Operational controls

Version prompts/models/tools, trace each workflow, meter tokens/cost, support controlled replay,
feature flags, canary, circuit breaker, rollback and kill switch.

### 32.3 — Sovereignty and privacy

Apply workload-specific data policy, region constraints, encryption, local/offline alternatives
where justified and strict separation of production data from uncontrolled evaluation services.

### 32.4 — End-to-end scenario

Demonstrate recovery on a new device, suspicious beneficiary and transfer, streaming features,
risk step-up, case creation, WalletOps timeline, grounded investigation, human approval, domain
service action and complete audit.

## 🧪 Exit gate

WalletOps can be degraded or disabled without stopping the wallet; cost and quality SLOs are
visible; rollback and kill switch are exercised; no AI component directly moves money.

## 🎯 Skill acquired

**Production operation of governed AI over critical financial infrastructure**.

---

# 🧭 DEPENDENCY SUMMARY

```text
0 → 1 → 4 → 5 → 16 → 17 → 18 → 19
      ↘ 6 → 7 ────────────────→ 23
2 + 3 → 11 → 12 → 13 → 14 → 15 ─┘
9 + 10 → 24
16 + 19 → 20 → 21 → 22
15 + 18 + 19 + 23 + 24 → 25
25 + 11 + 20 → 26
25 + 15 + 24 → 27
20 + 25 + 27 → 28
25 + 26 + 27 + 28 → 29 → 30 → 31 → 32
```

## Recommended delivery waves

1. **Customer-ready security:** finish Step 2, then Steps 11–15.
2. **Streaming risk:** Steps 16–19, after the Step 5 Outbox is reliable.
3. **Historical and coordinated intelligence:** Steps 20–22.
4. **Institutional operations:** Step 23 and continuous Step 24.
5. **WalletOps:** Steps 25–32 only after read models, authorization, audit and evaluation exist.

---

# 🏁 EXTENDED FINAL OUTCOME

Not merely a developer who uses Spring, Kafka or an LLM, but an engineer who can:

- preserve double-entry financial correctness under concurrency and retries;
- model customer identity, device credentials, sessions and recovery end to end;
- implement progressive and continuous KYC with versioned compliance policy;
- use Kafka for contracts, stateful streams and real-time risk features;
- explain and reproduce risk decisions;
- detect individual and coordinated fraud patterns;
- build governed historical, feature and graph data products;
- isolate provider, reconciliation and analytical workloads deliberately;
- operate the system through SLOs, runbooks, failure injection and recovery evidence;
- add WalletOps document intelligence, RAG, Text2SQL and workflows without granting AI financial authority;
- evaluate, govern, observe, roll back and disable every AI capability safely.

---

# 🧩 LANGUAGE, CLOUD AND AI BOUNDARIES

> **Append-only addition (ADR-009).** No step above is renumbered or rewritten.

The core stays on the JVM. A second language enters only where a workload is isolated
behind a versioned contract, so that the choice is reversible and the financial core never
depends on it.

| Where | What | Why there |
|---|---|---|
| Step 7 — reconciliation extraction | **Go** settlement-file ingestion worker: parse, normalize and publish provider reports | I/O-bound, stateless, its own deployable behind an event contract; small memory footprint and fast cold start for batch bursts |
| Steps 20–22 — data and ML | **Scala** feature pipelines (Spark / Kafka Streams Scala DSL) | Point-in-time feature engineering over large histories; the JVM ecosystem of the analytical stack |
| Step 9 onward | **AWS** as the reference cloud: EKS, RDS for PostgreSQL, MSK, KMS, IAM workload identity | One cloud, chosen once, so infrastructure-as-code and runbooks stay concrete |
| Steps 25–32 | **LLM components** behind the WalletOps authority boundary | AI observes, retrieves, correlates, explains and proposes; deterministic services authorize and execute |

A language or cloud service is adopted at its step only with a benchmark or an operational
argument recorded in an ADR, and is removable without touching the ledger.
