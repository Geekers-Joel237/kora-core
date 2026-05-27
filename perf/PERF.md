# Kora Core — Performance & Load Testing Guide

## Provider Simulation

`MobileMoneyProviderAdapter` is a configurable stub that simulates real Mobile Money provider
behavior. All settings are controlled by `kora.provider.*` properties — no code changes or
mocking needed between scenarios.

### Behaviors

| `kora.provider.behavior` | Description |
|--------------------------|-------------|
| `SUCCESS` *(default)*    | Every authorize/capture call succeeds immediately |
| `SLOW`                   | Succeeds but with 2× base latency (simulates congested network) |
| `FAIL_ON_AUTHORIZE`      | Throws `ProviderException` during authorize → transaction reaches `AUTHORIZATION_FAILED` |
| `FAIL_ON_CAPTURE`        | Authorizes OK, throws during capture → transaction reaches `CAPTURE_FAILED` |
| `TIMEOUT`                | Simulates a network timeout on every call (sleeps `timeoutThresholdMs + 500ms` then throws) |

> **Note:** `COLLECTION` (cash-in) and `DISBURSEMENT` (cash-out) operations use the same
> behavior configuration — `FAIL_ON_AUTHORIZE` applies to both. P2P transfers are unaffected
> by any provider behavior setting: they never call the provider.

### Latency Properties

| Property | Dev default | Perf profile | Description |
|----------|-------------|--------------|-------------|
| `kora.provider.simulate-latency` | `true` | `true` | Disable in tests (`false`) to keep CI fast |
| `kora.provider.latency.authorize-ms` | `200` | `800` | Base authorize latency (ms) |
| `kora.provider.latency.capture-ms` | `150` | `600` | Base capture/reverse latency (ms) |
| `kora.provider.latency.timeout-threshold-ms` | `3000` | `5000` | Threshold for TIMEOUT behavior |

Latency is computed as `baseMs + random jitter (0–40% of base)`. With `SLOW` behavior the
result is multiplied by 2.

With the perf profile, a cash-in or cash-out always incurs:

```
authorize : 800ms + jitter → [800ms, 1 120ms]
capture   : 600ms + jitter → [600ms,   840ms]
──────────────────────────────────────────────
Total     :               → [1 400ms, 1 960ms]
```

P2P transfers and balance checks hit no provider — their latency is purely applicative.

---

## Running k6 Load Tests

### Prerequisites

```bash
# Start the application with the perf profile (realistic latencies)
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun

# Start InfluxDB + Grafana for metrics
docker compose up -d influxdb grafana
```

Grafana dashboard: [http://localhost:3000](http://localhost:3000) (admin/admin)
k6 dashboard ID: **2587** (auto-provisioned)

### Test Scenarios

| Script | Users | Rate / VUs | Scenario duration | Setup time | Purpose |
|--------|-------|------------|-------------------|------------|---------|
| `smoke.js` | 2 | 1 VU | 2 min | ~10s | Sanity check — "does it work?" |
| `load.js` | 60 | 0→25 req/s | 11 min | ~3 min | Step 1 SLO validation |
| `stress.js` | 200 | 5→50 req/s | 22 min max | ~10 min | Find the breaking point |
| `soak.js` | 40 | 5 req/s constant | 30 min | ~2 min | Stability and memory leaks |

> **Setup time** includes sequential user registration + OTP + token + seed cash-in per user
> (~2.7s each). k6 starts the scenario only after setup completes. The `setupTimeout` in each
> script is sized accordingly.

### Business Mix (all four tests)

```
Cash-in   40%  — provider I/O mandatory (~1 400–1 960ms)
Transfer  35%  — no provider, purely applicative (~20–50ms)
Cash-out  15%  — provider I/O mandatory (~1 400–1 960ms)
Balance   10%  — simple read, no provider (~10–30ms)
```

55% of all operations go through provider I/O. This creates a latency floor of ~2.1s for
the global p95 that cannot be improved by application optimizations — it reflects real
network conditions in sub-Saharan Africa.

### Run Commands

```bash
# Smoke test
./perf/smoke-run.sh

# Load test
./perf/load-run.sh
```

Or directly via Docker:

```bash
docker run --rm --network host \
  -v $(pwd)/perf:/perf \
  -e BASE_URL=http://localhost:8081 \
  grafana/k6 run --out influxdb=http://localhost:8086/k6 /perf/smoke.js
```

### Thresholds

Thresholds are defined **per operation type**, not globally. The rationale: balance and
transfer have no provider I/O and must meet strict applicative SLOs; cash operations are
bounded by provider latency which is an external constraint.

#### Smoke (`smoke.js`)

| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| `http_req_duration` p95 | < 2 500ms | Above provider ceiling (~2 100ms). Catches pool exhaustion or timeouts. |
| `http_req_failed` rate | < 5% | Sanity threshold — not a performance SLO. |

#### Load (`load.js`) — Step 1 SLO gate

| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| `http_req_duration{operation:balance}` p95 | < 100ms | Pure DB read. |
| `http_req_duration{operation:transfer}` p95 | < 200ms | Single TX, no provider. |
| `http_req_duration{operation:cash}` p95 | < 2 500ms | Provider ceiling + overhead. |
| `http_req_failed` rate | < 1% | Business correctness gate. |
| `checks` rate | > 99% | All responses must be COMPLETED. |

A FAIL on any load threshold **blocks progression to Step 2**.

#### Stress (`stress.js`) — observation only

| Metric | abortOnFail threshold | Rationale |
|--------|----------------------|-----------|
| `http_req_duration` p95 | < 5 000ms | Triggers when the system adds > 3s above the provider floor, signaling true contention. |
| `http_req_failed` rate | < 5% | Clear degradation signal. |

The stress test has no pass/fail verdict — it runs until it finds the breaking point.

#### Soak (`soak.js`)

| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| `http_req_duration` p95 | < 2 500ms | Same ceiling as smoke — catches catastrophic drift. |
| `soak_latency_trend` p95 | < 2 500ms | Custom metric. Drift is monitored via Grafana trend, not this aggregate. |
| `http_req_failed` rate | < 1% | |

---

## Simulating Failures During a Load Test

To inject authorization failures into a running test, restart the app with the desired behavior:

```bash
SPRING_PROFILES_ACTIVE=perf \
  JAVA_OPTS="-Dkora.provider.behavior=FAIL_ON_AUTHORIZE" \
  ./gradlew bootRun
```

Or via environment variable if running as a JAR:

```bash
KORA_PROVIDER_BEHAVIOR=FAIL_ON_CAPTURE java -jar kora-core.jar \
  --spring.profiles.active=perf
```

---

## E2E Failure Path Tests

`ProviderFailureE2ETest` exercises both failure modes through the real Spring context
(no mocks). Each nested class overrides `kora.provider.behavior` via
`@SpringBootTest(properties = {...})`:

- `WhenProviderRefusesAuthorization` — asserts `AUTHORIZATION_FAILED` state for cash-in and cash-out
- `WhenProviderFailsOnCapture` — asserts `CAPTURE_FAILED` state for cash-in and cash-out only.
  P2P transfers are internal wallet operations — no provider call is made, therefore no
  provider failure scenario exists for transfer.