# Product backlog

Ordered by the PO. WSJF is the starting point; every departure from it is explained under
*Notes*. Size is a cap (1, 2, 3, 5), not a velocity input. Scores are the panel's
proposal until a `/refine` record and the PO confirm them. Statuses: `New` · `Refining` ·
`Ready` · `Committed (Sprint NN)` · `Done` · `Split` · `Dropped`.

WSJF = (BV + TC + RR) / size, each of BV (business value), TC (time criticality) and RR
(risk reduction / opportunity enablement) on 1, 2, 3, 5, 8, 13, 20.

---

## INC-1 — 2026-11-16 → 2027-01-22 (draft, to confirm at increment planning)

Sprints 01–04 and IP sprint 05. Wave 1 of `ROADMAP.md` starts with Step 2; the
Definition of Done is made enforceable first.

Sized against the attention budget (`README.md` §3, §7): four delivery sprints of at most
five items is about twenty items. The DoD enablers and Step 2 fill most of it, so Step 4
starts here but is delivered in INC-2.

| # | Objective | Type | BV |
|---|---|---|---|
| O-1 | Both pipelines enforce their DoD lines: API CI, mobile CI, Git Flow guardrails, coverage instruments, supply-chain scan, Sonar, BDD acceptance suite (KC-20 to KC-24, KC-29, KC-46, KC-47) | committed | — |
| O-2 | `ModuleBoundariesTest` records no coupling between `auth` and `payment` | committed | — |
| O-3 | One Spring Boot application per module decided by ADR (KC-34) | committed | — |
| O-4 | One trace per request across HTTP, use case, database and provider, with RED and business metrics exported over OTLP (KC-44) | uncommitted | — |
| O-5 | Coverage gaps on money paths closed, critical-path list populated (KC-42, KC-43) | uncommitted | — |
| O-6 | First tagged API release `api/v0.4.0`, changelog and SBOM (KC-27) | uncommitted | — |

Business values are the PO's to set. INC-2 (draft): Step 4 — idempotency (KC-35 split),
OTP and PIN brute force, rate limits, provider timeout policy, stuck-transaction gauge;
observability stack and alerts (KC-45).

---

## Epics

| Epic | Roadmap | Status |
|---|---|---|
| EPIC-DOD | Delivery enablers — the DoD made executable | Active |
| EPIC-02 | Step 2 — disciplined modular monolith | Active (phase A done) |
| EPIC-04 | Step 4 — idempotency and network reality | Next |
| EPIC-10 | Step 10 — observability, baseline pulled forward (state of view L-15) | Active (baseline only) |

---

## Items

| ID | Title | Epic | Product | Type | BV | TC | RR | Size | WSJF | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| KC-24 | Git Flow guardrails: commit lint (Conventional Commits + `Refs: KC-<n>`), branch-name check, PR template with DoD checklist and the three keys, protection on `develop` and `main` | EPIC-DOD | both | enabler | 3 | 13 | 13 | 2 | 14.5 | New |
| KC-20 | API CI: `.github/workflows/api-ci.yml`, path-filtered (everything but `mobile/**`), build with Testcontainers on every PR and on `develop` / `main` | EPIC-DOD | API | enabler | 5 | 13 | 20 | 3 | 12.7 | New |
| KC-46 | Mobile CI: `.github/workflows/mobile-ci.yml`, path-filtered on `mobile/**`, `npm run verify` and `npm run audit`, independent of the API CI | EPIC-DOD | mobile | enabler | 3 | 8 | 13 | 2 | 12.0 | New |
| KC-29 | Upgrade Spring Boot 4.0.3 → 4.0.8 (jackson-databind 2.20.2 CVEs) — KC-23 fails without it | EPIC-DOD | API | bug | 5 | 8 | 13 | 2 | 13.0 | New |
| KC-21 | Coverage instruments: split `test` into `unitTest`, `integrationTest`, `e2eTest`; JaCoCo ratchet from a floor file (D-9), unit-only gate on `domain` and `application` (D-10), critical-path list at 100 % branches (D-11) | EPIC-DOD | API | enabler | 5 | 8 | 13 | 5 | 5.2 | New |
| KC-30 | Fix doc drift: `ROADMAP.md` and `README.md` say eleven classes cross, `ModuleBoundariesTest` records eight | EPIC-02 | API | bug | 2 | 3 | 3 | 1 | 8.0 | New |
| KC-47 | BDD acceptance harness: Cucumber-JVM, black-box over HTTP against a started application, `acceptanceTest` task, feature files owned by QA, `TestPyramidTest` taught the new level | EPIC-DOD | API | enabler | 5 | 13 | 13 | 5 | 6.2 | New |
| KC-23 | Supply-chain scan: dependency vulnerabilities (fail on CVSS ≥ 7) and gitleaks, in each product's CI | EPIC-DOD | both | enabler | 5 | 8 | 13 | 3 | 8.7 | New |
| KC-22 | SonarQube Cloud analysis and quality gate as a required check (needs an organisation and a token — PO action) | EPIC-DOD | both | enabler | 3 | 8 | 13 | 3 | 8.0 | New |
| KC-32 | Close `auth → payment`: publish an `OpenWalletUseCase` used by registration | EPIC-02 | API | story | 3 | 5 | 13 | 5 | 4.2 | New |
| KC-33 | Close `payment → auth`: wallet lookup by phone number owned by `payment` | EPIC-02 | API | story | 3 | 5 | 13 | 5 | 4.2 | New |
| KC-34 | Spike: one Spring Boot application per module — Gradle multi-project vs alternatives; output an ADR | EPIC-02 | API | spike | 2 | 5 | 13 | 3 | 6.7 | New |
| KC-44 | OpenTelemetry baseline: traces across HTTP, use case, JDBC and provider with TX-1 / provider / TX-2 spans; trace id linked to the correlation id; RED and business metrics; structured logs with `trace_id`; OTLP export in every profile, not only `perf` (L-15) | EPIC-10 | API | enabler | 8 | 8 | 13 | 5 | 5.8 | New — to split |
| KC-43 | Close coverage gaps: `RetryPolicy` branches; `AccountBlockedException`, `DuplicateEmailException`, `MailDeliveryException` thrown in `src/main` but reached by no test | EPIC-DOD | API | bug | 3 | 3 | 13 | 2 | 9.5 | New |
| KC-42 | Close coverage gaps on `ReversePaymentService` failure paths (21 lines, 6 branches uncovered by every suite) — merge candidate with KC-31 | EPIC-DOD | API | bug | 5 | 5 | 13 | 3 | 7.7 | New |
| KC-41 | Unit tests for `RegisterService`, `LoginService`, `VerifyOtpService` with the in-memory doubles (`auth/application` at 30 % in the unit suite) | EPIC-DOD | API | bug | 2 | 5 | 8 | 3 | 5.0 | New |
| KC-25 | Release toggles: a `FeatureFlags` port, static per-profile configuration `kora.features.*`, one inventory (name, type, owner, created, removing item) validated at boot — an unknown or ill-typed flag stops the application; read only in `config/` and `adapters/in` | EPIC-DOD | API | enabler | 3 | 5 | 8 | 2 | 8.0 | New |
| KC-35 | Server-side idempotency key bound to the customer, returning the original result (L-4) | EPIC-04 | API | story | 20 | 13 | 20 | 8 | 6.6 | New — to split |
| KC-36 | Bound OTP attempts per challenge (L-8) | EPIC-04 | API | story | 8 | 8 | 13 | 3 | 9.7 | New |
| KC-31 | Reversal ignores `ReverseResult.success` (L-13, open half) | EPIC-04 | API | bug | 5 | 3 | 8 | 2 | 8.0 | New |
| KC-37 | Rate limits on `/auth/**` and on PIN checks (L-9) | EPIC-04 | API | story | 8 | 8 | 13 | 5 | 5.8 | New |
| KC-38 | Provider timeout and retry-with-jitter policy, never across an unknown outcome | EPIC-04 | API | story | 8 | 5 | 13 | 5 | 5.2 | New |
| KC-39 | Provider stub: late and duplicate confirmations, reproducible in tests | EPIC-04 | API | enabler | 3 | 5 | 8 | 3 | 5.3 | New |
| KC-40 | Stuck-transaction gauge: `INITIALIZED` / `AUTHORIZED` older than N minutes (L-3, L-15) | EPIC-04 | API | story | 5 | 5 | 8 | 3 | 6.0 | New |
| KC-45 | Local observability stack (OTel collector + Grafana LGTM as a compose profile), first alert rules and runbook entries | EPIC-10 | API | enabler | 3 | 3 | 8 | 3 | 4.7 | New |
| KC-26 | Mutation testing (PIT) on `*/domain/**`: measure the baseline, then ratchet | EPIC-DOD | API | enabler | 2 | 3 | 8 | 3 | 4.3 | New |
| KC-28 | Spike: contract check between the API and the released app versions (OpenAPI diff or consumer-driven); once enforced, `refinement-mobile` moves to Sonnet | EPIC-DOD | both | spike | 3 | 3 | 8 | 3 | 4.7 | New |
| KC-48 | Spike: mobile black-box acceptance (Maestro or alternative) driven by the same Gherkin scenarios | EPIC-DOD | mobile | spike | 2 | 2 | 5 | 2 | 4.5 | New |
| KC-27 | Release workflows per product: semver and changelog from commits, signed tags `api/v…` and `mobile/v…`, GitHub release with SBOM, API migration check from the last tag | EPIC-DOD | both | enabler | 3 | 3 | 8 | 5 | 2.8 | New |

| KC-49 | Mobile kill switch: the API serves which money screens are available; the app hides a screen in incident without a store release — before the first store release | EPIC-DOD | both | story | 5 | 2 | 8 | 3 | 5.0 | New |

| KC-50 | Flow-measures script: throughput, cycle time, review wait from `git log` and the daily log, for `/sprint-review` — mechanical work kept out of the agents | EPIC-DOD | both | enabler | 2 | 3 | 5 | 2 | 5.0 | New |

| KC-51 | Delivery operating model: `docs/agile/`, refinement panel and QA agents, ceremony skills, DoR and DoD, Git Flow rules in `CONTRIBUTING.md` | EPIC-DOD | both | enabler | — | — | — | 3 | — | In progress (before Sprint 01) |

Next free ID: **KC-52**.

### Notes

- KC-24, KC-20 and KC-46 come first: without them nothing else in the DoD is enforceable.
- KC-21 sits above higher-WSJF items because D-9 to D-11 are the baseline every later PR
  is measured against; delaying it lets coverage drift before the floor is recorded.
- KC-47 sits high for the same reason: until it lands, QA acceptance runs by hand.
- EPIC-02 items sit above EPIC-04 ones of higher WSJF (KC-36, KC-31): the roadmap's
  first wave finishes Step 2 before Step 4.
- KC-35 and KC-44 must be split by `/refine` before planning; KC-44's mobile part
  (`traceparent` from the app, D-39) becomes its own item.
- KC-25 must be done before the first story of KC-35 or KC-44 merges: a feature split
  over several items needs its release toggle, and no long-lived branch replaces it.
- KC-29 is a dependency of KC-23: the scan would fail on the first run.
- Coverage baseline behind D-9 to D-11 measured on 2026-11-13 on `f7cf39d`.
