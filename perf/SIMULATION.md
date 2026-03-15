# Kora Core — Load Simulation Report

> This document answers the question: **what exactly are we simulating, in numbers?**
> It complements the operational runbook (`PERF.md`) with business context and the
> projection toward a real production load.

---

## 1. Overview of the four tests

| Test | Simulated users | Throughput | Duration | Objective |
|---|---|---|---|---|
| Smoke | 2 | ~1 req/s | 2 min | Sanity check — "it works" |
| Load | 30 | 10 req/s plateau | 11 min | Step 0 SLO validation |
| Stress | 200 | 5 → 50 req/s | 22 min max | Identify the breaking point |
| Soak | 40 | 5 req/s constant | 30 min | Stability and absence of memory leaks |

---

## 2. Smoke test — 2 users · 1 VU · 2 minutes

### Configuration

```
Concurrent VUs   : 1
Simulated users  : 2  (userA sends, userB receives transfers)
Duration         : 2 min
Sleep per iter   : 1 fixed second
```

### Estimated transaction volume

| Operation | Mix share | Estimated transactions |
|---|---|---|
| Cash-in | 40% | ~48 |
| P2P Transfer | 35% | ~42 |
| Cash-out | 15% | ~18 |
| Balance | 10% | ~12 |
| **Total** | 100% | **~120** |

### Reading

A single VU runs sequentially with a 1-second pause between each
iteration → ~60 requests/minute, ~120 over 2 minutes.

This test does not measure performance. It verifies that the four endpoints
respond correctly before engaging concurrent load.

---

## 3. Load test — 30 users · 10 req/s · 11 minutes

> This is the Step 0 reference test. A FAIL here blocks progression.

### Configuration

```
Executor         : ramping-arrival-rate
Ramp-up          : 0 → 10 req/s over 2 min
Plateau          : 10 req/s for 8 min
Ramp-down        : 10 → 0 req/s over 1 min
Pre-allocated VUs: 30
Max VUs          : 60
Simulated users  : 30
```

### Transaction volume

| Phase | Duration | Average throughput | Requests |
|---|---|---|---|
| Ramp-up | 2 min | ~5 req/s | ~600 |
| Plateau | 8 min | 10 req/s | 4 800 |
| Ramp-down | 1 min | ~5 req/s | ~300 |
| **Total** | **11 min** | — | **~5 700** |

Breakdown by operation over the total duration:

| Operation | Share | Volume |
|---|---|---|
| Cash-in | 40% | ~2 280 |
| P2P Transfer | 35% | ~1 995 |
| Cash-out | 15% | ~855 |
| Balance | 10% | ~570 |
| **Total** | 100% | **~5 700** |

### SLOs validated by this test

| SLO | Threshold | Measurement |
|---|---|---|
| P95 Latency | < 150ms | `http_req_duration{scenario:load}` |
| Error rate | < 1% | `http_req_failed` |
| Check rate (COMPLETED) | > 99% | `checks` |

---

## 4. Stress test — 200 users · 5 → 50 req/s · 22 min max

> This test does not PASS/FAIL in the strict sense. It stops when the system degrades.

### Configuration

```
Executor         : ramping-arrival-rate
Pre-allocated VUs: 80
Max VUs          : 200
Simulated users  : 200
Auto-stop        : p95 > 500ms OR error rate > 5% (abortOnFail, 30s delay)
```

### Stepped load profile

| Level | Throughput | Level duration | Level requests |
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

| Level | Throughput | Expected p95 (healthy) | Breaking signal |
|---|---|---|---|
| 1 | 5 req/s | < 80ms | — |
| 2 | 10 req/s | < 150ms | — |
| 3 | 20 req/s | 150–300ms | hikaricp_pending > 0 |
| 4 | 30 req/s | 300–500ms | GC pressure visible |
| 5 | 50 req/s | > 500ms likely | errors > 5% → stop |

### What we are looking for

The level at which p95 crosses 500ms AND the metric that degrades first:
DB latency (hikaricp_connections_pending)? GC pressure (jvm_memory_used_bytes)?
thread pool saturation (http_req_waiting)?

This breaking point defines the **Step 0 technical ceiling** and guides
architecture decisions for Step 1 (cache, pool sizing, modular monolith).

---

## 5. Soak test — 40 users · 5 req/s · 30 minutes

### Configuration

```
Executor         : constant-arrival-rate
Throughput       : 5 req/s strict and constant
Duration         : 30 min
Pre-allocated VUs: 20
Max VUs          : 40
Simulated users  : 40
Custom metric    : soak_latency_trend (p95 over time, visible in Grafana)
```

### Transaction volume

| Operation | Share | Volume |
|---|---|---|
| Cash-in | 40% | ~3 600 |
| P2P Transfer | 35% | ~3 150 |
| Cash-out | 15% | ~1 350 |
| Balance | 10% | ~900 |
| **Total** | 100% | **~9 000** |

### What we are looking for

Not raw performance, but **stability over time**:

| Signal | Grafana indicator | Expected result |
|---|---|---|
| JVM memory leak | `jvm_memory_used_bytes` | Stable curve, normal GC oscillations, no monotonic drift |
| DB pool exhaustion | `hikaricp_connections_pending` | Stays at 0 for 30 min |
| Latency degradation | `soak_latency_trend` p95 | Flat — no upward trend |
| Unreleased connections | `hikaricp_connections_active` | Returns to 0 between bursts |

Memory leak signal:

```
Normal : heap  ~~~200MB~~~▲~~▼~~▲~~▼~~~200MB~~~  (GC cycles)
Leak   : heap  ~~~200MB~~~~~~~~~~~~~~~~~~~~~~~~~~~~600MB~~~  (drift)
```

---

## 6. Projection toward real production load

### Context

The Step 0 SLOs are calibrated for a **nascent regional fintech** operating
in sub-Saharan Africa (Mobile Money / neobank model).

### Comparison: our tests vs real production

| Dimension | Smoke | Load | Stress max | Target production Step 0 | Mature production |
|---|---|---|---|---|---|
| Simultaneous active users | 2 | 30 | 200 | 500–2 000 | 50 000–500 000 |
| Throughput (req/s) | ~1 | 10 | 50 | 50–200 | 500–5 000 |
| Transactions/hour | ~3 600 | ~31 000 | ~180 000 | ~200 000 | 1–5 M |
| Transactions/day (extrapolated 24h) | ~86 000 | ~864 000 | ~4.3 M | ~2–5 M | 20–100 M |
| Load duration | 2 min | 11 min | 22 min | 24h/7d | 24h/7d |
| Real providers | Stub | Stub | Stub | Orange Money, MTN | Multi-provider |
| Infrastructure | 1 local JVM | 1 local JVM | 1 local JVM | 1 server JVM | 3–10 instances |

### Reading the load test as a production equivalent

The **10 req/s** plateau of the load test represents, extrapolated over 24h:

```
10 req/s × 3 600 s/h × 24 h = 864 000 transactions/day
```

This is the off-peak volume of a small regional fintech with ~5,000 active users/day.
It is the **minimum traffic** that the Step 0 architecture must absorb without degradation.

The **50 req/s** ceiling of the stress test represents, extrapolated over 24h:

```
50 req/s × 3 600 × 24 = 4 320 000 transactions/day
```

This is the daily peak of a mid-sized African neobank.
This is the **Step 0 technical ceiling** — beyond this, the architecture must evolve
(Step 1: distributed cache, pool tuning, Ledger service extraction).

### Load roadmap by step

| Step | Architecture | Target SLO | Nominal throughput | Max throughput |
|---|---|---|---|---|
| 0 (current) | Transactional monolith | p95 < 150ms | 10 req/s | ~50 req/s |
| 1 | Modular monolith + cache | p95 < 100ms | 50 req/s | ~200 req/s |
| 2 | Hexagonal + event-driven | p95 < 80ms | 200 req/s | ~500 req/s |
| 3+ | Extracted microservices | p95 < 50ms | 500+ req/s | > 1 000 req/s |

---

## 7. Numeric summary

| Test | Users | Max VUs | Throughput | Duration | Total transactions |
|---|---|---|---|---|---|
| Smoke | 2 | 1 | ~1 req/s | 2 min | ~120 |
| Load | 30 | 60 | 10 req/s | 11 min | ~5 700 |
| Stress | 200 | 200 | 5→50 req/s | 22 min max | ~26 850 |
| Soak | 40 | 40 | 5 req/s | 30 min | ~9 000 |

Identical business mix across all four tests: **cashIn 40% · transfer 35% · cashOut 15% · balance 10%**