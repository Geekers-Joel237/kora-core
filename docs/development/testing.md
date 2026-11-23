# Testing

KORA Core is written test first. The suite has four levels, and the tree states the level
before anything else, so a level is a package selector. The strategy behind it — what each
level is for, and why there is no mocking framework — is in
[engineering-standards.md](../engineering-standards.md#3-testing-strategy).

## Contents

1. [The four levels](#1-the-four-levels)
2. [Running the tests](#2-running-the-tests)
3. [The coverage gate](#3-the-coverage-gate)
4. [The API contract](#4-the-api-contract)
5. [Structural tests](#5-structural-tests)
6. [Test profiles](#6-test-profiles)
7. [Load testing](#7-load-testing)

---

## 1. The four levels

```
src/test/java/com/geekersjoel237/koracore/
└── <module>/            auth · payment · shared
    ├── unit/            domain · application · adapters · doubles
    ├── integration/     persistence · query · security
    ├── contract/        the HTTP boundary of the payment journey
    └── e2e/             the whole application over real HTTP
```

| Level | Runs | Never |
|---|---|---|
| **Unit** | plain JUnit 5; ports faked by the in-memory adapters of `<module>/unit/doubles/` | a Spring context, a container |
| **Integration** | `AbstractRepositoryTest`: `@SpringBootTest` + `@Transactional` + a PostgreSQL Testcontainer | an HTTP client |
| **Contract** | `@ContractTest`: the real web layer, JSON, validation, security filter chain and exception handlers; the use cases replaced by doubles | a database, a server |
| **E2E** | `AbstractE2ETest`: `@SpringBootTest(RANDOM_PORT)` over real HTTP, isolation by `TRUNCATE` | rollback isolation |

`TestPyramidTest` checks that the tree tells the truth: no `@SpringBootTest` under `unit/`,
no HTTP client under `integration/`, no database under `contract/`, and nothing outside the
four levels. `auth` has no contract level yet, by decision.

Every integration and e2e test connects as `kora_app`, the role production uses, through
`KoraPostgres` — never as the superuser ([database.md](./database.md#database-roles)).

## 2. Running the tests

The unit level needs nothing. Every other level starts its own PostgreSQL through
Testcontainers, so Docker must be running — Compose is not used.

```bash
./gradlew unitTest                                                        # unit level alone, ~15 s, no Docker
./gradlew test --tests "com.geekersjoel237.koracore.*.integration.*"      # integration
./gradlew test --tests "com.geekersjoel237.koracore.*.contract.*"         # contract
./gradlew test --tests "com.geekersjoel237.koracore.*.e2e.*"              # end to end
./gradlew test                                                            # every level
./gradlew build                                                           # every level + the coverage gate
```

One module, one class, one test:

```bash
./gradlew test --tests "com.geekersjoel237.koracore.payment.unit.*"
./gradlew test --tests "*CashInE2ETest"
./gradlew test --tests "*PaymentUseCaseTest*ReversalTests*"
```

## 3. The coverage gate

`./gradlew check`, and therefore `build`, ends with `verifyCoverage`. JaCoCo measures twice:
every level together (`test`), and the unit level alone (`unitTest`) over the hexagon only —
every `domain/` and `application/`. Each measure is compared with its floor in
[`config/coverage-floors.properties`](../../config/coverage-floors.properties):

| Key | Measured on | Scope |
|---|---|---|
| `all.total.*`, `all.<module>.*` | every level | the whole of `src/main`, or one module |
| `unit.total.*` | the unit level alone | the hexagon: every `domain/` and `application/` |
| `unit.<module>.domain.*`, `unit.<module>.application.*` | the unit level alone | the domain and the use cases of one module |

The `unit.*` keys are the pyramid gate: code that only an e2e test reaches is not covered
where it counts. Floors are in percent with one decimal, for `lines` and `branches`, and
**below a floor the build fails** and names the key. There are two kinds:

- **The hexagon has fixed minimums.** Payment, `domain` and `application`, is at **100 %**:
  every line and branch that moves money is tested. Every other `unit.*` key, and the hexagon
  as a whole, is at **90 %**. Written test first, the code stays near 100; 90 is what breaks
  the build, not the target.
- **The `all.*` keys are a ratchet.** They start at the measure and only go up: the build
  prints every measure above its floor, and raising one is a one-line diff in the same pull
  request.

Lowering any floor needs the reason in the pull request.

Two things are never measured: `KoraCoreApplication` and the classes MapStruct generates
(`*MapperImpl`); the mappers' behaviour stays covered through the tests of their callers.
Reports, HTML and XML, are in `build/reports/jacoco/test/` and
`build/reports/jacoco/jacocoUnitTestReport/`.

Coverage tells what was run, not what was checked. The architecture tests remain the first
gate.

## 4. The API contract

Each contract case is compared — status, content type and body — to a committed file under
`src/test/resources/contracts/`. A difference writes a `*.received.json` beside it and fails
the build. `PaymentOpenApiContractTest` compares `/v3/api-docs` to
[`api-contract/payments.openapi.json`](../../api-contract/payments.openapi.json) and asserts
that the responses it declares are exactly the ones the contract tests observe.

A boundary therefore changes only through a reviewed diff of those files. To accept an
intended change, replace the committed file with its `*.received.json` and commit both
together with the code that caused it.

## 5. Structural tests

`shared/unit/architecture/` holds the rules that keep the architecture honest, and they fail
the build like any other test:

| Test | Asserts |
|---|---|
| `HexagonalArchitectureTest` | the layers point inward; `application/` and `ports/` import no foreign type beyond an allow-list, which is empty |
| `ModuleBoundariesTest` | `shared` names no module; what `auth` and `payment` take from each other equals a recorded list |
| `TestPyramidTest` | the test tree above |
| `ConfigurationHygieneTest` | no secret fallback, every `${VAR}` listed in `.env.example`, example credentials are generator commands |
| `MigrationNamingConventionTest` | every migration is `V<yyyyMMddHHmm>__snake_case.sql` |

## 6. Test profiles

Tests run with `@ActiveProfiles("test")`. `src/test/resources/application.yaml` shadows the
base file on the test classpath, and `application-test.yaml` layers the profile on top. The
suite therefore reads no environment variable and needs no `.env`. This configuration:

- uses a fixed, literal JWT key, independent of any environment;
- disables real SMTP — `TestMailConfig` replaces `SmtpMailAdapter` with an in-memory stub;
- sets `ddl-auto: none` — Flyway creates the schema on the Testcontainers database and stays
  its single owner;
- declares no datasource — every integration and e2e class injects one at runtime through
  `KoraPostgres` and `@DynamicPropertySource`.

## 7. Load testing

Load tests live in `perf/` and run k6 inside a container. The full runbook is
[perf/PERF.md](../../perf/PERF.md); the calibration behind the gates is
[ADR-005](../adr/ADR-005-load-test-calibration.md).

```bash
docker compose up -d influxdb grafana maildev
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun

./perf/smoke-run.sh     # mandatory gate — never skip it
./perf/load-run.sh      # then load, stress, soak, in that order
./perf/stress-run.sh
./perf/soak-run.sh
```

| Step 0 SLO | Target |
|---|---|
| p95 latency | < 150 ms |
| Error rate | < 1 % |
| Check rate | > 99 % |
