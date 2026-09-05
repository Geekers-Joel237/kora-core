# Kora Core — Load Simulation Report

> This document answers the question: **what exactly are we simulating, in numbers?**
> It complements the operational runbook (`PERF.md`) with business context and the
> projection toward a real production load.

---

## The provider latency floor — a constraint that shapes every threshold

The perf profile configures realistic sub-Saharan Africa mobile money network latencies:

```
authorize : 800ms + jitter [0–40%] → [800ms, 1 120ms]
capture   : 600ms + jitter [0–40%] → [600ms,   840ms]
─────────────────────────────────────────────────────
Total provider I/O per cash op     → [1 400ms, 1 960ms]
```

With 55% of the business mix (cashIn + cashOut) going through this I/O, the **global p95 will
always be ~2.1s** at low load regardless of application quality. This is not a performance
problem — it is the correct simulation of an external constraint.

All thresholds in this project are calibrated around this floor:
- Operations without provider I/O (transfer, balance) are measured against strict applicative SLOs.
- Operations with provider I/O (cashIn, cashOut) are measured against a ceiling above the provider floor.

---

## 1. Overview of the four tests

| Test | Users | Setup time | Rate | Scenario duration | Objective |
|---|---|---|---|---|---|
| Smoke | 2 | ~10s | 1 VU sequential | 2 min | Sanity — "it works" |
| Load | 60 | ~3 min | 0→25 req/s | 11 min | Step 1 SLO gate |
| Stress | 200 | ~10 min | 5→50 req/s | 22 min max | Find the breaking point |
| Soak | 40 | ~2 min | 5 req/s constant | 30 min | Stability over time |

**Business mix (identical across all tests):**

```
cashIn 40%  ·  transfer 35%  ·  cashOut 15%  ·  balance 10%
```

**Setup** — each user goes through: `register → OTP → verify-otp → seed cashIn`.
The seed cashIn (100 000 XAF) guarantees a positive initial balance for every VU regardless
of which operation it draws first. It costs ~2.7s per user due to provider I/O.

---

## 2. Smoke test — 2 users · 1 VU · 2 minutes

### What we are testing

Functional correctness at zero concurrency. The smoke test does not measure performance.
It verifies that all four endpoints respond correctly and return `COMPLETED` before engaging
concurrent load. A smoke failure blocks any further test.

### Configuration

```
Concurrent VUs   : 1
Simulated users  : 2  (userA sends, userB receives transfers)
Duration         : 2 min
Sleep per iter   : 1s (deliberate pacing — 1 VU runs sequentially)
```

### Transaction volume

With provider I/O (~1.5s avg) + 1s sleep, each iteration takes ~2.5s on average.

| Operation | Mix share | Estimated transactions |
|---|---|---|
| Cash-in | 40% | ~24 |
| P2P Transfer | 35% | ~21 |
| Cash-out | 15% | ~9 |
| Balance | 10% | ~6 |
| **Total** | 100% | **~60** |

### Thresholds

| Metric | Threshold |
|---|---|
| `http_req_duration` p95 | < 2 500ms |
| `http_req_failed` rate | < 5% |

The p95 threshold of 2 500ms sits above the provider ceiling (~2 100ms measured) with a
~400ms margin. It will only trigger on real regressions: pool exhaustion, DB timeouts, or
application errors that send latencies into the 5–30s range.

---

## 3. Load test — 60 users · 25 req/s · 11 minutes

> **This is the Step 1 gate test. A FAIL blocks progression to Step 2.**

### What we are testing

The system's ability to sustain **25 req/s of mixed payment traffic** without pool exhaustion,
lock contention, or functional errors. This is the direct validation of the micro-transaction
optimization (ADR-004): TX-1 and TX-2 hold DB connections for ~10ms and ~20ms respectively,
and the float account lock is eliminated.

### Configuration

```
Executor         : ramping-arrival-rate
Ramp-up          : 0 → 25 req/s over 2 min
Plateau          : 25 req/s for 8 min
Ramp-down        : 25 → 0 req/s over 1 min
Total duration   : 11 min
Pre-allocated VUs: 60  (1 per user — no VU sharing)
Max VUs          : 100
Setup timeout    : 5 min
```

### Transaction volume

| Phase | Duration | Avg throughput | Requests |
|---|---|---|---|
| Ramp-up | 2 min | ~12 req/s | ~1 500 |
| Plateau | 8 min | 25 req/s | 12 000 |
| Ramp-down | 1 min | ~12 req/s | ~750 |
| **Total** | **11 min** | — | **~16 500** |

By operation type:

| Operation | Share | Volume |
|---|---|---|
| Cash-in | 40% | ~6 600 |
| P2P Transfer | 35% | ~5 775 |
| Cash-out | 15% | ~2 475 |
| Balance | 10% | ~1 650 |
| **Total** | 100% | **~16 500** |

### SLOs validated by this test

Thresholds are **per operation type** because the two categories have fundamentally different
latency profiles: applicative (no provider) vs. provider-bound.

| Operation | SLO | k6 metric |
|---|---|---|
| Balance | p95 < 100ms | `http_req_duration{operation:balance}` |
| Transfer | p95 < 200ms | `http_req_duration{operation:transfer}` |
| Cash-in / Cash-out | p95 < 2 500ms | `http_req_duration{operation:cash}` |
| All operations | error rate < 1% | `http_req_failed` |
| All operations | COMPLETED rate > 99% | `checks` |

A single p95 threshold on the global mix would always be ~2s (dominated by 55% cash ops)
and would be meaningless for detecting applicative regressions.

### What the passing thresholds prove

- **balance p95 < 100ms** → no DB lock contention, read path is fast under concurrent load
- **transfer p95 < 200ms** → single-TX P2P is not queuing at the pool or at the lock
- **cash p95 < 2 500ms** → no pool exhaustion (pre-optimization: p95 = 30–60s); the micro-transaction model is absorbing 25 req/s of provider-bound traffic with pool-30

---

## 4. Stress test — 200 users · 5 → 50 req/s · 22 min max

> This test does not PASS or FAIL in the traditional sense. It runs until it finds
> the system's breaking point, then stops automatically.

### What we are testing

The load level at which the micro-transaction model and HikariCP pool-30 can no longer absorb
the traffic — the point where queuing, lock contention, or thread saturation begins to
compound. This ceiling defines the Step 1 architectural boundary.

### Configuration

```
Executor         : ramping-arrival-rate
Stages           : stepped (see profile below)
Pre-allocated VUs: 80
Max VUs          : 200
Simulated users  : 200
Setup timeout    : 15 min
Auto-stop        : p95 > 5 000ms OR error rate > 5% (abortOnFail, 30s evaluation delay)
```

The auto-stop threshold is set at **5 000ms** — well above the healthy provider ceiling of
~2 100ms. It triggers only when the system adds > 3s of its own queuing on top of provider I/O,
which is the unambiguous signal of true architectural degradation.

### Stepped load profile

| Stage | Throughput | Duration | Estimated requests |
|---|---|---|---|
| Warm-up | 0 → 5 req/s | 1 min | ~150 |
| Level 1 | 5 req/s | 3 min | ~900 |
| Transition | 5 → 10 req/s | 1 min | ~450 |
| Level 2 | 10 req/s | 3 min | ~1 800 |
| Transition | 10 → 20 req/s | 1 min | ~900 |
| Level 3 | 20 req/s | 3 min | ~3 600 |
| Transition | 20 → 30 req/s | 1 min | ~1 500 |
| Level 4 | 30 req/s | 3 min | ~5 400 |
| Transition | 30 → 50 req/s | 1 min | ~2 400 |
| Level 5 | 50 req/s | 3 min | ~9 000 |
| Cool-down | 50 → 0 req/s | 30s | ~750 |
| **Total (full run)** | — | **22 min 30s** | **~26 850** |

### Expected behavior per level

The global p95 is always ~2.1s at healthy levels because 55% of requests are cash ops
with a ~1.96s provider ceiling. The signal to watch is not the absolute p95 value but
**when it starts growing beyond that floor**.

| Level | Throughput | Global p95 (healthy) | Breaking signal in Grafana |
|---|---|---|---|
| 1 | 5 req/s | ~2.1s | — nothing |
| 2 | 10 req/s | ~2.1s | — nothing |
| 3 | 20 req/s | ~2.1–2.3s | `hikaricp_connections_pending` may appear briefly |
| 4 | 30 req/s | ~2.3–3.5s | `hikaricp_connections_pending` > 0 sustained; GC pressure begins |
| 5 | 50 req/s | > 5s → auto-stop | error rate climbs; `hikaricp_connections_active` stays saturated |

### What to record

The level at which auto-stop triggers, and the metric that degrades first:
- `hikaricp_connections_pending` → pool saturation (fix: pool size or micro-tx further)
- `jvm_memory_used_bytes` drift → GC pressure (fix: heap tuning or object pooling)
- `http_req_waiting` spike → thread pool saturation (fix: Tomcat thread count)

This breaking point is the **Step 1 technical ceiling** and seeds the architecture decisions for Step 2.

---

## 5. Soak test — 40 users · 5 req/s · 30 minutes

### What we are testing

Not raw throughput, but **stability over time**: the absence of memory leaks, connection
leaks, and latency drift under a sustained but modest load. 5 req/s is intentionally
well within the healthy zone so that any degradation is attributable to accumulation
over time, not to load itself.

### Configuration

```
Executor         : constant-arrival-rate
Throughput       : 5 req/s strict and constant
Duration         : 30 min
Pre-allocated VUs: 20
Max VUs          : 40
Simulated users  : 40
Custom metric    : soak_latency_trend (Grafana — p95 over time)
Setup timeout    : 4 min
```

### Transaction volume

| Operation | Share | Volume |
|---|---|---|
| Cash-in | 40% | ~3 600 |
| P2P Transfer | 35% | ~3 150 |
| Cash-out | 15% | ~1 350 |
| Balance | 10% | ~900 |
| **Total** | 100% | **~9 000** |

### Thresholds

| Metric | Threshold | Rationale |
|---|---|---|
| `http_req_duration` p95 | < 2 500ms | Aggregate ceiling — catches catastrophic end-of-run degradation |
| `soak_latency_trend` p95 | < 2 500ms | Same, on the custom trend metric |
| `http_req_failed` rate | < 1% | |

The k6 threshold aggregate does not detect drift — it compares the total run to a fixed
ceiling. Drift detection requires Grafana: the `soak_latency_trend` p95 plotted over time
should remain **flat**. An upward slope over 30 minutes is the leak signal.

### Signals to watch in Grafana

| Signal | Grafana metric | Healthy | Leak signal |
|---|---|---|---|
| JVM memory | `jvm_memory_used_bytes` | Stable oscillation (GC cycles) | Monotonic upward drift |
| DB pool | `hikaricp_connections_pending` | 0 for the full 30 min | Any sustained > 0 |
| Latency drift | `soak_latency_trend` p95 | Flat line ~2.1s | Upward trend over time |
| Unreleased connections | `hikaricp_connections_active` | Returns to 0 between bursts | Stays elevated |

```
Healthy memory : ~~~200MB~~▲▼~~▲▼~~▲▼~~~200MB~~~  (normal GC)
Memory leak    : ~~~200MB~~~~~~~~~~~~~~~~~~~~~~~~~~600MB~~~  (drift)
```

---

## 6. Projection toward real production load

### Context

The Step 1 SLOs are calibrated for a **growing regional fintech** operating in sub-Saharan
Africa (Mobile Money / neobank model). The provider stub simulates realistic Orange Money /
MTN Mobile Money network conditions.

### Our tests vs. real production

| Dimension | Smoke | Load | Stress max | Step 1 production target | Mature production |
|---|---|---|---|---|---|
| Concurrent users | 2 | 60 | 200 | 500–2 000 | 50 000–500 000 |
| Throughput (req/s) | ~0.5 | 25 | 50 | 50–200 | 500–5 000 |
| Transactions/hour | ~1 800 | ~90 000 | ~180 000 | ~200 000 | 1–5 M |
| Transactions/day | ~43 000 | ~2 160 000 | ~4.3 M | ~2–5 M | 20–100 M |
| Duration | 2 min | 11 min | 22 min | 24h / 7d | 24h / 7d |
| Provider | Stub | Stub | Stub | Orange Money, MTN | Multi-provider |
| Infrastructure | 1 local JVM | 1 local JVM | 1 local JVM | 1 server JVM | 3–10 instances |

### Reading the load test as a production equivalent

The 25 req/s plateau, extrapolated to 24h:
```
25 req/s × 3 600 s/h × 24 h = 2 160 000 transactions/day
```
This is the daily volume of a regional fintech with ~15 000 active users/day.
It is the **Step 1 nominal target**.

The 50 req/s stress ceiling, extrapolated to 24h:
```
50 req/s × 3 600 × 24 = 4 320 000 transactions/day
```
This is the daily peak of a mid-sized African neobank.
It is the **Step 1 technical ceiling** — beyond this, the architecture must evolve.

### Architecture roadmap by step

| Step | Architecture | Applicative SLO | Provider-bound SLO | Nominal throughput |
|---|---|---|---|---|
| 0 | Monolith + `@Transactional` | — | — (pool exhaustion at ~5 req/s) | ~5 req/s |
| 1 *(current)* | Micro-transaction + float lock removed | transfer/balance p95 < 200ms | cash p95 < 2 500ms | 25 req/s |
| 2 | Modular monolith + dedicated boundaries | p95 < 150ms | p95 < 2 500ms | 50 req/s |
| 3+ | Hexagonal + event-driven + async | p95 < 100ms | async / callback | 200+ req/s |

Step 0 had no meaningful latency SLO because the pool exhausted before any threshold
could be meaningful. Step 1's SLO split reflects the architectural reality: the application
controls applicative latency, not provider network latency.

---

## 7. Summary

| Test | Users | Setup | Rate | Duration | Est. transactions |
|---|---|---|---|---|---|
| Smoke | 2 | ~10s | 1 VU | 2 min | ~60 |
| Load | 60 | ~3 min | 25 req/s | 11 min | ~16 500 |
| Stress | 200 | ~10 min | 5→50 req/s | 22 min max | ~26 850 |
| Soak | 40 | ~2 min | 5 req/s | 30 min | ~9 000 |