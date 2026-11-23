# Troubleshooting

The failures people actually hit, and what fixes each.

| Symptom | Cause | Fix |
|---|---|---|
| `/actuator/health` reports `db` DOWN | PostgreSQL is not running | `docker compose up -d` |
| `/actuator/health` reports `mail` DOWN | MailDev is not running | `docker compose up -d` |
| `APPLICATION FAILED TO START` on `kora.security.jwt.*` | a signing key is shorter than 32 characters, missing, or equal to the other one | generate both keys again — [local-setup.md §7](./local-setup.md#7-when-a-variable-is-missing) |
| `No host port mapping found for container port 5432` | the IDE resolved only `docker-compose.yml` | keep both files in `spring.docker.compose.file` — [local-setup.md §9](./local-setup.md#9-running-from-an-ide) |
| `Migration checksum mismatch` | a migration was edited after the dev database applied it | [database.md](./database.md#never-modify-an-applied-migration) |
| `Detected resolved migration not applied to database` | a branch carries a migration older than one already applied | rename it to a fresh timestamp — [database.md](./database.md#ordering-across-branches) |
| Grafana panels stay empty during a load test | `perf` profile without the `observability` containers | `docker compose up -d influxdb grafana` |
| `GET /test/otp/{email}` returns 404 for a real OTP | the `perf` profile is not active | `SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun` |
| Testcontainers fails to start | Docker is not running, or the user cannot reach its socket | `docker run --rm hello-world`; restart Docker Desktop if it fails |
| A `*.received.json` appears under `src/test/resources/contracts/` | the HTTP boundary changed | review it — [testing.md §4](./testing.md#4-the-api-contract) |
| The build fails on `verifyCoverage` | a change left code untested | the message names the key — [testing.md §3](./testing.md#3-the-coverage-gate) |

## Checking the health in detail

```bash
curl -s http://localhost:8081/actuator/health | python -m json.tool
```

## Checking which profile is active

```bash
curl -s http://localhost:8081/actuator/info | grep -i profile
```
