# Local setup

Everything needed to run KORA Core on your machine: the tools, the environment file,
the containers, the Spring profiles and the IDE. For the tests, see
[testing.md](./testing.md); for the schema, [database.md](./database.md).

## Contents

1. [Prerequisites](#1-prerequisites)
2. [The environment file](#2-the-environment-file)
3. [Infrastructure containers](#3-infrastructure-containers)
4. [Compose files and Compose profiles](#4-compose-files-and-compose-profiles)
5. [Compose profiles and Spring profiles](#5-compose-profiles-and-spring-profiles)
6. [Where configuration lives](#6-where-configuration-lives)
7. [When a variable is missing](#7-when-a-variable-is-missing)
8. [Running the application](#8-running-the-application)
9. [Running from an IDE](#9-running-from-an-ide)

---

## 1. Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 21+ | Runtime and compilation |
| Docker Desktop | Latest | PostgreSQL, MailDev, the monitoring stack, Testcontainers |
| Gradle | via the wrapper (`./gradlew`) | Build, test, run |
| Git | Any | Version control |

Gradle is wrapped and k6 runs inside a container, so neither needs a global installation.

```bash
java --version        # must show 21+
docker --version
./gradlew --version
```

## 2. The environment file

```bash
cp .env.example .env
```

`.env.example` is the authoritative list of every variable the application and Docker
Compose can consume. It is versioned; `.env` is not, and must never be committed.

Every password and secret in `.env.example` is the command that generates it, in angle
brackets: `<openssl rand -hex 16>`, `<openssl rand -base64 48>`. Run each command and put
its output in `.env` — one run per value, so no two values are the same. That includes the
JWT signing keys, one per token use, as `JWT_ACCESS_SECRET` and `JWT_REFRESH_SECRET`:

```bash
openssl rand -hex 16       # each database and pgAdmin password
openssl rand -base64 48    # each JWT signing key
```

> **Security note** — each key must be at least 32 characters, since HMAC-SHA256 rejects
> anything shorter, and the two must differ. `SecurityProperties` refuses to start on either
> violation, so an untouched copy of `.env.example`, whose two keys are the same placeholder,
> does not start. `ConfigurationHygieneTest` fails the build if an example value is anything
> but such a placeholder. Never commit a real secret; use a secrets manager outside local
> development.

## 3. Infrastructure containers

```bash
docker compose up -d
```

Or press **Run** in your IDE and skip this step: the `dev` profile lets Spring bring the
same stack up itself ([§9](#9-running-from-an-ide)).

That starts **two** containers: PostgreSQL and MailDev. MailDev is not optional in
development — registration and login send an OTP by mail, so without it those flows fail
and `/actuator/health` reports `mail` as DOWN.

Everything else is an opt-in **Compose profile**, off by default:

| Profile | Services | When |
|---|---|---|
| *(none)* | postgres, maildev | always, in development |
| `tooling` | pgadmin | when you want a database GUI |
| `observability` | influxdb, grafana | load-test campaigns only |

```bash
# For a single command — naming a service enables its profile automatically
docker compose up -d pgadmin
docker compose up -d influxdb grafana

# For a whole session — set it once in .env and forget it
#     COMPOSE_PROFILES=tooling,observability
docker compose up -d
```

`COMPOSE_PROFILES` lives in `.env`, so `up`, `ps`, `logs` and `down` all agree on the same
set. That is why an optional group is a profile and not a separate `-f` file: with files
you must repeat the identical `-f` list on the way down, and forgetting one silently leaves
those containers running.

Keeping the optional groups off by default saves about 100 seconds every time you start
work — measured with the images already pulled, waiting for every service to report
healthy:

| | Containers | Time to healthy |
|---|---|---|
| Default | 2 | **12 s** |
| `tooling,observability` | 5 | **115 s** |

Every service declares a healthcheck, so `healthy` means ready, not merely started:

```bash
docker compose ps                 # STATUS column must read "healthy"
open http://localhost:1080        # MailDev — read the OTP mails here
```

pgAdmin takes about a minute on a first boot; `health: starting` until then is expected.

All ports are published on `127.0.0.1` only. The database is reachable from this machine
and from nowhere else on the network — the same posture production uses, where operators
reach it through an SSH tunnel onto that loopback port.

## 4. Compose files and Compose profiles

> **One file = one environment. One profile = one optional group.**

The two axes are independent: `tooling` is not an environment, and `prod` is not something
you add on top of `dev`. The reasoning is in [ADR-006](../adr/ADR-006-compose-topology.md).

| File | Loaded | Answers | Holds | Never holds |
|---|---|---|---|---|
| `docker-compose.yml` | always | *What does the application need to exist at all?* | postgres: image and tag, `environment`, `healthcheck`, named volume, network | `ports`, `restart`, `container_name`, host bind mounts, any service only some environments run |
| `docker-compose.override.yml` | automatically, unless `-f` is used | *How do I run it on my machine?* | loopback port bindings, `container_name`, `restart: "no"`, and the development-only services — maildev, pgadmin, influxdb, grafana | anything a server would need |
| `docker-compose.prod.yml` | only when named with `-f` | *How does it run on a server?* | loopback binding, `restart: unless-stopped`, `shm_size`, `stop_grace_period`, log rotation | any secret |

```bash
docker compose up -d                                                    # base + dev
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d   # base + prod
```

Naming any file with `-f` suppresses the automatic pickup of `docker-compose.override.yml`.
That is the mechanism that keeps development port bindings off a server, and it is the one
mistake worth guarding against: a deploy runbook must carry the full command, never a bare
`docker compose up`.

It is also what makes production safe by construction. pgAdmin and Grafana are declared
*only* in the development file, so the production command never sees them — there is no
service definition to activate, with or without a profile.

Two rules keep the files from drifting:

- **The base file holds only what every environment runs**, and stays
  environment-agnostic. That is testable:

  ```bash
  docker compose -f docker-compose.yml config | grep -E "^\s+(ports|restart|container_name):"
  ```

  It must print nothing. Anything it prints is a decision every environment now inherits.
- **A service that only one environment runs is declared in that environment's file.** An
  environment file may introduce services; it must not redefine the image, healthcheck or
  volumes of a base service.

## 5. Compose profiles and Spring profiles

They share a name and nothing else. One decides which **containers** run, the other which
**application configuration** applies, and neither reads the other.

| | Compose profile | Spring profile |
|---|---|---|
| Decides | which **containers** run | which **application configuration** applies |
| Selected by | `COMPOSE_PROFILES` in `.env`, or naming a service on the command line | `SPRING_PROFILES_ACTIVE`, or `spring.profiles.default: dev` |
| Values | *(none)*, `tooling`, `observability` | `dev`, `perf`, `prod`, `test` |
| Declared in | `docker-compose*.yml` | `src/main/resources/application-<profile>.yaml` |

Nothing enforces agreement between them, so a Spring profile that needs a container starts
happily without it and fails later:

| Spring profile | Requires which containers | Because |
|---|---|---|
| `dev` (default) | the default set | `SmtpMailAdapter` sends OTP mail to `MAIL_HOST:MAIL_SMTP_PORT` — MailDev, already running |
| `perf` | default **+ `observability`** | Micrometer pushes to InfluxDB every 10 s, and `perf/*-run.sh` waits on `/actuator/health`, whose `mail` component probes SMTP |
| `prod` | PostgreSQL only | a real SMTP relay, no metrics export, no database GUI |
| `test` | *(none — Compose is not used at all)* | every test starts its own PostgreSQL through Testcontainers and reads no `.env` |

What a mismatch looks like:

- `perf` without `observability` — `InfluxMeterRegistry` logs a failed push every ten
  seconds and the Grafana panels stay empty, while the load test itself runs and reports
  normal-looking results.
- A stack started with `-f` without naming `docker-compose.override.yml` — PostgreSQL
  publishes no port, so the application on the host cannot reach the database.

Switching to a load test therefore means moving **both** axes together:

```bash
docker compose up -d influxdb grafana          # Compose axis
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun  # Spring axis
```

The two meet in exactly one place: `.env`. Compose and the application both read it
directly, so there is a single source of truth rather than a convention kept aligned by
hand. A real environment variable still overrides the file, which is how CI and a secrets
manager inject values.

## 6. Where configuration lives

Every value has exactly one legitimate home. `ConfigurationHygieneTest` enforces it.

| Support | Holds | Never holds |
|---|---|---|
| `.env` (git-ignored, mirrored by `.env.example`) | secrets, credentials, host-specific values: passwords, the JWT keys, exposed ports | anything that is the same on every machine |
| `src/main/resources/application.yaml` | the environment contract (`${VAR}` bindings) and policy identical in every environment | monitoring, mail transport, pool sizing, provider behaviour |
| `src/main/resources/application-<profile>.yaml` | technical configuration for that environment only | secrets, and any fallback for one |

The base file answers *what must exist*; a profile answers *how it behaves here*. That is
why monitoring is never in the base file: `management.endpoint.health.show-details` is
`always` in dev and `never` in prod, so no single value could be correct for both.

| Variable | Consumed by | Profile |
|---|---|---|
| `POSTGRES_DB` `DB_HOST` `DB_PORT` | Compose + `application.yaml` | all |
| `POSTGRES_USER` `POSTGRES_PASSWORD` | Compose only — superuser, initialises a fresh volume | — |
| `POSTGRES_MIGRATION_USER` `POSTGRES_MIGRATION_PASSWORD` | Compose (role script) + `application.yaml` → `spring.flyway.*` | all |
| `POSTGRES_APP_USER` `POSTGRES_APP_PASSWORD` | Compose (role script) + `application.yaml` → `spring.datasource.*` and the `app_role` Flyway placeholder | all |
| `JWT_ACCESS_SECRET` `JWT_REFRESH_SECRET` | `application.yaml` | all |
| `SERVER_PORT` | `application.yaml` | all |
| `MAIL_HOST` `MAIL_SMTP_PORT` | Compose + `application.yaml` | all |
| `MAIL_UI_PORT` | Compose | — |
| `MAIL_FROM` `MAIL_USERNAME` `MAIL_PASSWORD` | `application-prod.yaml` | prod |
| `INFLUXDB_HOST` `INFLUXDB_PORT` | Compose + `application-perf.yaml` | perf |
| `GRAFANA_PORT` `DB_ADMIN_PORT` | Compose | — |
| `PGADMIN_DEFAULT_EMAIL` `PGADMIN_DEFAULT_PASSWORD` | Compose | — |

Four profiles, one file each:

| Profile | File | Activated by |
|---|---|---|
| `dev` | `src/main/resources/application-dev.yaml` | nothing — `spring.profiles.default` |
| `perf` | `src/main/resources/application-perf.yaml` | `SPRING_PROFILES_ACTIVE=perf` |
| `prod` | `src/main/resources/application-prod.yaml` | `SPRING_PROFILES_ACTIVE=prod` |
| `test` | `src/test/resources/application-test.yaml` | `@ActiveProfiles("test")` |

The `test` profile sits in `src/test/resources` so it never ships in the production jar.
Alongside it, `src/test/resources/application.yaml` shadows the base contract on the test
classpath — which is what lets `./gradlew test` pass with no `.env` and no exported variable.

## 7. When a variable is missing

No configuration entry carries a fallback, so the boot stops. For the signing key:

```
APPLICATION FAILED TO START

Description:

Binding to target com.geekersjoel237.koracore.auth.config.SecurityProperties failed:

    Property: kora.security.jwt.access-secret
    Value: "${JWT_ACCESS_SECRET}"
    Reason: must be at least 32 characters for HMAC-SHA256
```

Read `Value` carefully: Spring binds the unresolved text `${JWT_ACCESS_SECRET}` rather than
failing on the placeholder itself. A bare `${VAR}` is therefore *not* enough on a
`@ConfigurationProperties` target — the `@Size` constraint on `SecurityProperties` is what
turns a 20-character literal into a refusal to start instead of a weak key discovered at the
first token issuance.

`ConfigurationHygieneTest` fails the build if a sensitive property regains a fallback, or if
a `${VAR}` is referenced without being listed in `.env.example`.

## 8. Running the application

### Development

```bash
docker compose up -d
./gradlew bootRun
```

The application starts on **http://localhost:8081**. No `--spring.profiles.active` is
needed: `spring.profiles.default` is `dev`. Both Compose and the application read `.env` —
Compose natively, the application through `spring.config.import` in `application.yaml`.

```bash
curl -s http://localhost:8081/actuator/health | python -m json.tool
# Expected: "status": "UP", "db": { "status": "UP" }, "mail": { "status": "UP" }
```

Spring Boot DevTools is active in development: the application reloads on class changes
without a full restart. Stop a background run before running the tests:

```bash
pkill -f "KoraCoreApplication" 2>/dev/null || true
```

### Perf profile, for load tests

```bash
docker compose up -d influxdb grafana
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun
```

The `perf` profile activates:

- `GET /test/otp/{email}` — exposes OTP codes for the k6 setup (never in production)
- Micrometer → InfluxDB export every 10 seconds
- Reduced logging, so as not to bias the latency measurements

```bash
curl -s "http://localhost:8081/test/otp/anyone%40test.com"
# Returns 404 (no OTP exists yet) — the endpoint is reachable, so the perf profile is on
```

The load-test procedure itself is in [perf/PERF.md](../../perf/PERF.md).

### Useful URLs

| URL | Purpose |
|---|---|
| http://localhost:8081 | Application |
| http://localhost:8081/actuator/health | Health check |
| http://localhost:8081/swagger-ui.html | OpenAPI documentation |
| http://localhost:1080 | MailDev — inspect OTP emails |
| http://localhost:5050 | pgAdmin — inspect the database |
| http://localhost:3000 | Grafana (perf profile only) |

## 9. Running from an IDE

Nothing to configure, and nothing to start beforehand. A Run Configuration on
`KoraCoreApplication` is enough: `application.yaml` imports `.env` itself — no EnvFile
plugin, no variables pasted into the run configuration — and the `dev` profile has Spring
bring the Compose stack up on its own.

One condition: the **working directory** must be the project root, which is the IntelliJ
default. Both `spring.config.import` (for `./.env`) and the Compose file paths resolve
relative to it.

The stack **stays up when you stop the application** — `lifecycle-management: start-only`.
Stopping a run must not stop the database; bring the stack down with `docker compose down`
when you actually mean to.

`COMPOSE_PROFILES` works here exactly as in a terminal: the `docker compose` subprocess
inherits the working directory and reads the same `.env`.

Two settings make this safe, and both are load-bearing — read ADR-006 D7 before touching
either:

- `spring.docker.compose.file` names **both** Compose files. Left to itself the integration
  resolves only `docker-compose.yml` and runs `docker compose -f` on it, which suppresses the
  override where the ports live — the failure is
  `No host port mapping found for container port 5432`.
- The dev `postgres` service carries the label `org.springframework.boot.ignore`. Without it,
  Spring derives `ConnectionDetails` from the container's `environment:` block, and those
  **outrank `spring.datasource.*`** — the application would connect as the superuser and
  bypass the `kora_migration` / `kora_app` split, silently.

Tests need even less. `@ActiveProfiles("test")` is self-contained, so running a test class
from the IDE works on a clean clone with no `.env` at all.
