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
| `FAIL_ON_AUTHORIZE`      | Throws `PaymentProviderException` during authorize → transaction reaches `AUTHORIZATION_FAILED` |
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

Load testing needs the **observability** Compose profile on top of the socle.
`docker compose up -d` alone starts Postgres and MailDev only — the run would
work but produce no dashboard.

```bash
# 1. Infrastructure. Naming the services explicitly enables their profile for
#    this command; no -f is passed, so docker-compose.override.yml still loads
#    and Postgres publishes its port.
docker compose up -d influxdb grafana

#    Or, to keep it on for the whole campaign, set it once in .env:
#        COMPOSE_PROFILES=observability
#    then a bare `docker compose up -d` brings the same four containers, and
#    `docker compose down` stops all of them — which is the reason profiles are
#    preferred here over a separate -f file.

# 2. The application, with realistic provider latencies
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun
```

The `perf/*-run.sh` scripts do step 1 themselves, so in practice you only start
the application and run the script.

Grafana dashboard: [http://localhost:3000](http://localhost:3000) (admin/admin)
k6 dashboard ID: **2587** (auto-provisioned)

### Test Scenarios

Calibrated for **Step 3** (ROADMAP: 80–100 req/s, P95 < 300 ms) — ADR-005, Step 3 amendment.

| Script | Users | Rate / VUs | Scenario duration | Setup time | Purpose |
|--------|-------|------------|-------------------|------------|---------|
| `smoke.js` | 2 | 1 VU | 2 min | ~5s | Sanity check — "does it work?" |
| `load.js` | 200 | 0→100 req/s | 13 min | ~30s | Step 3 SLO validation |
| `stress.js` | 400 | 25→200 req/s | 21 min max | ~1 min | Find the breaking point |
| `soak.js` | 120 | 50 req/s constant | 30 min | ~20s | Stability and memory leaks |

> **Setup time**: users are onboarded in batches of 20 (`http.batch`): register, OTP,
> verify, seed cash-in, each step in parallel. A batch costs ~3s, the seed cash-in
> dominates. Tokens expire 10–14 min after setup, spread at random, and are renewed through
> `POST /auth/refresh` — no BCrypt, no OTP mail in the middle of the measurements.
>
> **Users = maxVUs**: each VU owns its user; the transfer recipient is the neighbouring
> VU's user. Fewer users than VUs would make two VUs fight over the same account lock.

### Business Mix (all four tests) — `scenarios/mix.js`

```
Cash-in   35%  — provider I/O mandatory (~1 400–1 960ms)
Transfer  30%  — no provider; BCrypt PIN check + one TX with two row locks (~150ms)
Cash-out  15%  — provider I/O mandatory (~1 400–1 960ms)
Balance   10%  — simple read, no provider (~5–10ms)
History   10%  — keyset page of 20; one call in four follows nextCursor
```

50% of all operations go through provider I/O. This creates a latency floor of ~2.1s for
the global p95 that cannot be improved by application optimizations — it reflects real
network conditions in sub-Saharan Africa. A mean iteration lasts ~1s, so the VUs needed
roughly equal the target rate (Little's law).

### Run Commands

```bash
./perf/smoke-run.sh
./perf/load-run.sh
./perf/stress-run.sh
./perf/soak-run.sh
```

Or directly via Docker:

```bash
docker run --rm --network host \
  -v $(pwd)/perf:/perf \
  -e BASE_URL=http://localhost:8081 \
  grafana/k6 run --out influxdb=http://localhost:8086/k6 /perf/smoke.js
```

### Thresholds

Thresholds are defined **per operation type**, not globally. The rationale: balance,
history and transfer have no provider I/O and must meet applicative SLOs; cash operations
are bounded by provider latency which is an external constraint. Requests tagged
`operation:auth` (refresh, fallback login) and `operation:setup` stay out of them.

#### Smoke (`smoke.js`)

| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| `http_req_duration` p95 | < 2 500ms | Above provider ceiling (~2 100ms). Catches pool exhaustion or timeouts. |
| `http_req_failed` rate | < 5% | Sanity threshold — not a performance SLO. |

#### Load (`load.js`) — Step 3 SLO gate

| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| `http_req_duration{operation:balance}` p95 | < 100ms | Pure DB read. |
| `http_req_duration{operation:history}` p95 | < 150ms | Keyset read, no lock. |
| `http_req_duration{operation:transfer}` p95 | < 300ms | ROADMAP Step 3 P95. BCrypt + single TX, no provider. |
| `http_req_duration{operation:cash}` p95 | < 2 500ms | Provider ceiling + overhead. |
| `http_req_failed` rate | < 1% | Business correctness gate. |
| `checks` rate | > 99% | All responses must be COMPLETED. |
| `dropped_iterations` count | < 1 | Throughput is part of the SLO: a dropped iteration is a request never served. |

A FAIL on any load threshold means Step 3's volume target is not met.

#### Stress (`stress.js`) — observation only

| Metric | abortOnFail threshold | Rationale |
|--------|----------------------|-----------|
| `http_req_duration` p95 | < 5 000ms | Triggers when the system adds > 3s above the provider floor, signaling true contention. |
| `http_req_failed` rate | < 5% | Clear degradation signal. |

The per-operation thresholds in `stress.js` are `p(95)>=0`: always true, they only make k6
print each operation's latency in the end-of-run summary. The stress test has no pass/fail
verdict — read the p95 per stage and `dropped_iterations` in Grafana.

#### Soak (`soak.js`)

Same per-operation thresholds as the load test, plus `http_req_failed < 1%`, `checks > 99%`
and `dropped_iterations < 1`. An aggregate over 30 min cannot see a drift that starts at
minute 20: drift is read in Grafana, p95 per operation over time.

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