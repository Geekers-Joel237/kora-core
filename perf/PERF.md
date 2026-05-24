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

| Script | VUs / Rate | Duration | Purpose |
|--------|-----------|----------|---------|
| `perf/smoke.js` | 1 VU | 2 min | Sanity check — no regressions |
| `perf/load.js` | ~8 req/s | 5 min | Steady-state: 60% cash-in, 30% transfer, 10% cash-out |
| `perf/stress.js` | ramp to 100 VUs | stages | Find the breaking point |
| `perf/soak.js` | ~6 req/s | 15 min | Memory leaks and latency drift |

### Run Commands

```bash
# Smoke test
docker run --rm --network host \
  -v $(pwd)/perf:/perf \
  -e BASE_URL=http://localhost:8081 \
  grafana/k6 run --out influxdb=http://localhost:8086/k6 /perf/smoke.js

# Load test
docker run --rm --network host \
  -v $(pwd)/perf:/perf \
  -e BASE_URL=http://localhost:8081 \
  grafana/k6 run --out influxdb=http://localhost:8086/k6 /perf/load.js
```

### Thresholds (`perf/load.js`)

| Metric | Threshold |
|--------|-----------|
| `http_req_duration` p95 | < 200 ms |
| `http_req_duration` p99 | < 400 ms |
| `http_req_failed` rate | < 1% |
| `technical_errors` count | < 5 |

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