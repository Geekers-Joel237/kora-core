# Kora Core — Performance Test Runbook

Stack: **k6** · **InfluxDB 1.8** · **Grafana** · **Micrometer** (Spring Boot)

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Architecture](#2-architecture)
3. [Step 0 SLOs](#3-step-0-slos)
4. [Local execution (everything on one machine)](#4-local-execution)
5. [Remote execution (app on server, k6 locally)](#5-remote-execution)
6. [Test order and progression gates](#6-test-order-and-gates)
7. [Expected results per test](#7-expected-results)
8. [What to monitor in Grafana](#8-grafana)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Prerequisites

### Required tools

| Tool | Role | Verification |
|---|---|---|
| Docker Desktop | InfluxDB, Grafana, MailDev | `docker --version` |
| Java 21 | Spring Boot runtime | `java --version` |
| Gradle wrapper | Build and launch | `./gradlew --version` |
| curl, nc | Health checks in scripts | `curl --version` |

k6 runs inside a Docker container — no local installation required.

### Ports used

| Port | Service | Used by |
|---|---|---|
| 8081 | Spring Boot | k6, browser |
| 8086 | InfluxDB | k6 (`--out influxdb`), Micrometer, Grafana |
| 3000 | Grafana | Browser |
| 1025 | MailDev SMTP | Spring Boot (`SmtpMailAdapter`) |
| 1080 | MailDev UI | Browser |

Make sure none of these ports are already in use before starting.

---

## 2. Architecture

```
k6 (Docker container)
  │
  ├── HTTP ──────────────────► Spring Boot :8081
  │                             SPRING_PROFILES_ACTIVE=perf
  │                             └── TestSupportAction (@Profile("perf"))
  │                                 GET /test/otp/{email}  ← k6 setup retrieves OTPs
  │
  ├── --out influxdb ────────► InfluxDB :8086  (database: k6)
  │
Spring Boot (Micrometer)
  └── export step=10s ───────► InfluxDB :8086  (database: kora_metrics)

Grafana :3000
  ├── datasource: InfluxDB-k6     (database: k6)
  └── datasource: InfluxDB-kora   (database: kora_metrics)

MailDev :1025 (SMTP)
  └── required by Spring Actuator MailHealthIndicator
      without MailDev → health=DOWN → health check scripts fail
```

---

## 3. Step 0 SLOs

These values are the thresholds for the **load test**. Any breach is an architecture signal.

| SLO | Value | Measured by |
|---|---|---|
| P95 Latency | < 150ms | k6 `http_req_duration` |
| Error rate | < 1% | k6 `http_req_failed` |
| Plateau throughput | ≥ 10 req/sec | k6 `http_reqs` |
| Check rate (COMPLETED) | > 99% | k6 `checks` |

---

## 4. Local execution

> Everything runs on the same machine: Spring Boot + Docker (InfluxDB, Grafana, MailDev) + k6.

### Step 1 — Start monitoring

```bash
docker compose up -d influxdb grafana maildev
```

Wait ~10s, then verify:

```bash
curl -s http://localhost:8086/ping   # → pong
curl -s http://localhost:1080        # → MailDev page
```

### Step 2 — Start the Spring Boot app

```bash
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun
```

Wait for the `Started KoraCoreApplication` message in the logs.

Verify:

```bash
curl -s http://localhost:8081/actuator/health
# Expected result:
# {"status":"UP","components":{"db":{"status":"UP"},"mail":{"status":"UP"},...}}
```

If `mail: DOWN` → MailDev not started, go back to Step 1.

Verify the Micrometer export (after ~15s):

```bash
curl -s "http://localhost:8086/query?db=kora_metrics&q=SHOW+MEASUREMENTS"
# Expected result: hikaricp_connections_active, jvm_memory_used_bytes, ...
```

Verify the OTP perf endpoint:

```bash
# Register a test user
curl -s -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Test","email":"check@test.com","phonePrefix":"+237","phoneNumber":"699999998","rawPin":"123456"}'

# Retrieve the OTP
curl -s "http://localhost:8081/test/otp/check%40test.com"
# Expected result: {"code":"XXXXXX"}
```

If `404` → the app was not started with `SPRING_PROFILES_ACTIVE=perf`.

### Step 3 — Run the tests (in order)

```bash
# 1. Smoke (always first — 2 min)
./perf/smoke-run.sh

# 2. Load (SLO validation — 11 min)
./perf/load-run.sh

# 3. Stress (breaking point — 22 min max)
./perf/stress-run.sh

# 4. Soak (long-term stability — 30 min)
./perf/soak-run.sh
```

---

## 5. Remote execution

> The app runs on a remote server. k6 and monitoring run locally or on a separate machine.

### Case A — App on server, monitoring + k6 locally

```bash
# Start monitoring locally
docker compose up -d influxdb grafana maildev

# Pass the app URL to each script
./perf/smoke-run.sh http://my-server:8081
./perf/load-run.sh  http://my-server:8081
```

The script uses `--network host` so that k6 (Docker container) can reach `localhost:8086` (local InfluxDB).
The app on the server must be reachable from the machine running k6.

**Server preconditions:**
- `SPRING_PROFILES_ACTIVE=perf` active on the server
- Port 8081 open toward the test machine
- `GET /test/otp/{email}` accessible from the test machine

### Case B — App + k6 on server, Grafana locally

In this case, modify `INFLUX_URL` in the script to point to the server's InfluxDB, then configure the local Grafana datasource to point to that same InfluxDB.

> For Step 0 (modest nominal load: 10 req/sec), Case A is sufficient.

---

## 6. Test order and gates

**Never skip a step. Each test is a gate to the next.**

```
┌─────────────────────────────────────────────────────────────┐
│  PRECONDITIONS                                               │
│  □ health = UP (db + mail)                                  │
│  □ /test/otp/{email} → {"code":"..."}                       │
│  □ kora_metrics in InfluxDB                                 │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
              ┌─────────────────┐
              │   SMOKE TEST    │  ./perf/smoke-run.sh
              │   1 VU · 2 min  │
              └────────┬────────┘
                       │
              PASS?    │
          ┌────────────┴────────────┐
          NO                       YES
          │                         │
    Fix the bug                ┌──────▼──────────┐
    before continuing          │   LOAD TEST     │  ./perf/load-run.sh
                               │  10 req/s · 11m │
                               └──────┬──────────┘
                                      │
                             SLOs OK? │  p95<150ms · errors<1%
                          ┌───────────┴───────────┐
                         NO                       YES
                          │                        │
                   Analyze Grafana         ┌──────▼──────────┐
                   (section 8)             │  STRESS TEST    │  ./perf/stress-run.sh
                   Fix                     │  5→50 req/s     │
                   Re-run load             │  22 min max     │
                                           └──────┬──────────┘
                                                  │
                                      Document    │  breaking point
                                      in the ADR  │
                                                  │
                                           ┌──────▼──────────┐
                                           │   SOAK TEST     │  ./perf/soak-run.sh
                                           │  5 req/s · 30m  │
                                           └──────┬──────────┘
                                                  │
                                         Stable?  │  heap + connections + p95
                                      ┌───────────┴───────────┐
                                     NO                       YES
                                      │                        │
                               Look for leak           ✓ Step 0 validated
                               memory / connection
```

---

## 7. Expected results

### Smoke test

| Indicator | Expected result |
|---|---|
| Exit code | 0 |
| k6 output | `✓ register 200`, `✓ cash-in 200`, `✓ cash-in COMPLETED` |
| `http_req_failed` | 0% |
| `http_req_duration` p95 | < 2 000ms (soft threshold) |
| Grafana | All 6 panels populated, curves visible |

If the smoke test **fails**: do not run the load test. Fix the issue first.

---

### Load test

| Indicator | Expected result | k6 threshold |
|---|---|---|
| `http_req_duration` p95 | < 150ms | strict (`abortOnFail: false`) |
| `http_req_failed` rate | < 1% | strict |
| `checks` rate | > 99% | strict |
| Exit code | 0 = PASS, 1 = FAIL | — |
| Final output | `✓ load test PASSED — SLOs validated` | — |

**Expected latency curve in Grafana:**
```
Ramp-up (0-2min)  : p95 rises progressively up to ~80-120ms
Plateau (2-10min) : p95 stable between 50 and 120ms
Ramp-down (10-11m): p95 drops back toward 20-40ms
```

If the curve rises and does not stabilize at the plateau → the system is saturated at 10 req/sec, this is an architecture bug.

---

### Stress test

The stress test does not PASS/FAIL in the strict sense. It stops when the breaking point is reached (or at 50 req/sec if the system holds).

| Level | Expected p95 (healthy system) | Breaking signal |
|---|---|---|
| 5 req/s | < 100ms | — |
| 10 req/s | < 150ms (=SLO) | — |
| 20 req/s | 150–300ms | p95 > 500ms = breaking point |
| 30 req/s | 300–500ms | hikaricp_pending > 0 = pool saturated |
| 50 req/s | > 500ms likely | errors > 5% = conditional stop |

**Document:** at which level p95 exceeds 500ms and which metric degrades first (latency? DB pool? heap?).

---

### Soak test

| Indicator | Expected result |
|---|---|
| Exit code | 0 |
| `http_req_duration` p95 | < 300ms throughout the entire duration |
| `http_req_failed` | < 1% |
| JVM heap | Stable — oscillates within a ±100MB band, does not drift upward |
| `hikaricp_connections_pending` | Stays at 0 for the full 30 min |
| `soak_latency_trend` p95 | Stable — no upward trend |

**Memory leak signal:**
```
Normal : heap  200MB ──▲──▼──▲──▼──  200MB  (GC cycles)
Leak   : heap  200MB ─────────────────────►  600MB  (monotonic drift)
```

---

## 8. Grafana

URL: `http://localhost:3000/d/kora-load/kora-load-test`
Credentials: `admin / admin`

The dashboard is auto-provisioned — no manual configuration needed.

### During a test: recommended settings

- Time window: `Last 30 minutes`
- Refresh: `5s` (top-right menu)

### Checklist of the 6 panels

| # | Panel | Source | What you should see |
|---|---|---|---|
| 1 | Latency p50/p95/p99 | k6 | 3 distinct curves, p95 below the red 150ms line |
| 2 | Throughput req/sec | k6 | Progressive ramp-up, plateau at 10 req/s for 8 min |
| 3 | Error Rate % | k6 | Near 0%, below the red 1% line |
| 4 | HikariCP connections | kora | `active` between 0 and 20, `pending` at 0 |
| 5 | JVM Heap MB | kora | Stable curve with normal GC oscillations |
| 6 | Application latency p95 | kora | Close to panel 1, slightly lower (no network) |

If a panel is **empty**:
1. Verify the test is still running (not finished yet?)
2. Check the datasource: `http://localhost:3000/connections/datasources`
3. Verify the InfluxDB database contains data:
   ```bash
   # For k6 metrics
   curl -s "http://localhost:8086/query?db=k6&q=SHOW+MEASUREMENTS"
   # For Micrometer metrics
   curl -s "http://localhost:8086/query?db=kora_metrics&q=SHOW+MEASUREMENTS"
   ```

---

## 9. Troubleshooting

### `health = DOWN` at startup

```bash
curl http://localhost:8081/actuator/health | python -m json.tool
```

| Component DOWN | Cause | Fix |
|---|---|---|
| `mail` | MailDev not started | `docker compose up -d maildev` |
| `db` | PostgreSQL not started | `docker compose up -d postgres` |

---

### `docker: Error response from daemon: invalid mode: /perf`

MSYS2/Git Bash issue: automatic conversion of Unix paths to Windows paths.
The scripts already handle this case with `export MSYS_NO_PATHCONV=1` in a sub-shell.

If the error persists: run from **PowerShell** or **WSL** instead of Git Bash.

---

### `/test/otp/{email}` returns 404

Causes and fixes in order:

1. **App started without the perf profile**
   ```bash
   # Wrong
   ./gradlew bootRun
   # Correct
   SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun
   ```

2. **OTP expired** (TTL: 5 min) — the setup is too slow for the number of users
   → Reduce `USER_COUNT` in `load.js` / `stress.js`

3. **Email not URL-encoded**
   ```bash
   # Wrong (@ not encoded)
   curl http://localhost:8081/test/otp/user@test.com
   # Correct
   curl http://localhost:8081/test/otp/user%40test.com
   ```

---

### Bulk `InsufficientFundsException` during the test

The 200,000 XOF seed has been exhausted. The cashOut (5,000) or transfer (2,000) was called more times than expected.

Minimum seed calculation for a soak test:
```
5 req/sec × 30 min = 9,000 requests
cashOut 15% + transfer 35% = 50% of requests are debits
9,000 × 0.50 × 5,000 XOF = 22,500,000 XOF / nb_users

For 15 users: 22,500,000 / 15 = 1,500,000 XOF per user
```

Fix in `data/setup.js`: increase `SEED_AMOUNT`.

---

### Grafana — empty panels after the test

1. Verify that measurements exist in InfluxDB:
   ```bash
   curl -s "http://localhost:8086/query?db=k6&q=SHOW+MEASUREMENTS"
   ```

2. If empty: the `--out influxdb` did not work → re-run with k6 logs visible:
   ```bash
   # Run manually without the shell script to see k6 errors
   docker run --rm --network host \
     -v "$(pwd)/perf:/perf" \
     -e BASE_URL=http://localhost:8081 \
     grafana/k6:latest \
     run --out influxdb=http://localhost:8086/k6 /perf/smoke.js
   ```

3. If measurements exist but Grafana is empty: adjust the time window to the run's time range.

---

## Quick Reference

```bash
# ── Startup ───────────────────────────────────────────────────
docker compose up -d influxdb grafana maildev
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun

# ── Pre-test sanity check ─────────────────────────────────────
curl http://localhost:8081/actuator/health
curl "http://localhost:8086/query?db=kora_metrics&q=SHOW+MEASUREMENTS"
curl "http://localhost:8081/test/otp/check%40test.com"  # after register

# ── Tests (in order) ─────────────────────────────────────────
./perf/smoke-run.sh   [BASE_URL]   # 2 min  — mandatory gate
./perf/load-run.sh    [BASE_URL]   # 11 min — validates SLOs
./perf/stress-run.sh  [BASE_URL]   # 22 min — breaking point
./perf/soak-run.sh    [BASE_URL]   # 30 min — stability

# BASE_URL default: http://host.docker.internal:8081

# ── URLs ─────────────────────────────────────────────────────
# App       http://localhost:8081
# Health    http://localhost:8081/actuator/health
# Grafana   http://localhost:3000   (admin/admin)
# Dashboard http://localhost:3000/d/kora-load/kora-load-test
# InfluxDB  http://localhost:8086
# MailDev   http://localhost:1080
```