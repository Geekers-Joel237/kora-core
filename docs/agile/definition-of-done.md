# Definition of Done

Three levels, each including the one before. A criterion is either **enforced** by a
machine — the build, the pipeline, a branch rule — or **checked** by a person or an agent
on the PR. The aim is that every line moves to *enforced*; a line enforced by nobody is a
wish, and the *Status* column says honestly which is which.

`/dod-check` walks Level 1 against the current branch and produces the checklist the PR
carries. `/accept` produces the QA verdict.

Statuses: ✅ in place · 🔧 enabler planned (backlog ID) · 👁 checked at review.

---

## Level 1 — Story (every PR into `develop`)

### A. Feature acceptance — owned by QA

| # | Criterion | Enforced by | Status |
|---|---|---|---|
| D-1 | Every BDD scenario of the refinement record passes, run black-box against the running application; the QA verdict is **Accepted** | Acceptance suite (`acceptanceTest`) + `/accept` | 🔧 KC-47 / 👁 |
| D-2 | The feature files are the QA's: the maker made them pass without editing them, or the QA agreed to the change in writing | Review of the diff on feature files | 👁 |
| D-3 | The `@money` and `@security` scenario sets of the whole product still pass | Acceptance suite in CI | 🔧 KC-47 |

### B. Developer tests — owned by the maker, judged by the code checker

| # | Criterion | Enforced by | Status |
|---|---|---|---|
| D-4 | A defect fix starts with a test that reproduces it, committed red before the fix | Commit history of the PR | 👁 |
| D-5 | New domain behaviour has a Spring-free unit test (`CLAUDE.md` rule 16); new use-case behaviour too, with the in-memory doubles | `TestPyramidTest` + review | ✅ / 👁 |
| D-6 | Concurrency, retry, timeout and replay are tested at the lowest level that can show them, wherever the change crosses a provider call, a lock or the command bus | Review | 👁 |
| D-7 | The API suite is green, zero test skipped without an `@Disabled` reason pointing to a backlog ID | API CI | 🔧 KC-20 |
| D-8 | Test names state behaviour as a sentence (`a_replayed_key_returns_the_original_result`) | Review | 👁 |

### C. Coverage — four instruments, never a single global percentage

Baseline measured on 2026-11-13 on `develop` (`f7cf39d`): all suites 91.9 % lines /
83.9 % branches; unit suite alone on `domain` 93.1 / 83.4. A global percentage would have
let seven points of regression through and hidden that `auth/application` is covered at
30 % by unit tests: the instruments below are built from that measure.

| # | Criterion | Enforced by | Status |
|---|---|---|---|
| D-9 | **Ratchet**: line and branch coverage of all suites, per module, never fall below the recorded floor. The floor starts at today's values rounded down (global 91 / 83) and is raised at each IP sprint, never lowered | JaCoCo verification against a floor file in the repository | 🔧 KC-21 |
| D-10 | **Pyramid gate**: `*/domain/**` and `*/application/**` measured on the **unit suite alone**. Domain floor 93 / 83, target 95 / 90 at the end of INC-1; application floor at today's value per module, raised as KC-41 lands | Separate `unitTest` task + JaCoCo verification | 🔧 KC-21 |
| D-11 | **Critical paths at 100 % branches** (all suites): the classes named in the build's critical-path list — use cases calling the provider, `Ledger`, `Transaction`, the transaction states, `RetryPolicy`. A class enters the list when its gaps are closed (KC-42, KC-43); leaving it needs a written reason in the same diff | JaCoCo per-class rule over the list | 🔧 KC-21 |
| D-12 | **New code** ≥ 90 % lines and branches | SonarQube Cloud quality gate | 🔧 KC-22 |
| D-13 | **Mutation score** on `*/domain/**` at or above the floor measured by KC-26, then ratcheted | PIT on PRs touching `domain/` | 🔧 KC-26 |

Excluded from coverage: `KoraCoreApplication.main` only. `config/` and `adapters/` are
measured like the rest.

### D. Code quality and architecture

| # | Criterion | Enforced by | Status |
|---|---|---|---|
| D-14 | Sonar quality gate on new code: 0 bugs, 0 vulnerabilities, 0 unreviewed hotspots, maintainability A, duplication < 3 %, cognitive complexity ≤ 15 per method | SonarQube Cloud, required check | 🔧 KC-22 |
| D-15 | Architecture rules hold: hexagonal layers, empty framework allow-list, kernel purity, recorded couplings | `HexagonalArchitectureTest`, `ModuleBoundariesTest` | ✅ |
| D-16 | The review rules of `CLAUDE.md` §7 hold on the diff | `/code-review` + human review | 👁 |
| D-17 | Names come from the domain vocabulary (`CLAUDE.md` §5); no new synonym for an existing concept; no explanatory comment or Javadoc that restates the code | Review | 👁 |
| D-18 | No dead code, no production code that exists only for a test | Review + Sonar unused-code rules | 👁 / 🔧 KC-22 |

### E. Security

| # | Criterion | Enforced by | Status |
|---|---|---|---|
| D-19 | No new dependency with a known vulnerability of CVSS ≥ 7 | Dependency scan in CI | 🔧 KC-23 |
| D-20 | No secret in the diff | gitleaks in CI | 🔧 KC-23 |
| D-21 | No PII or secret in logs, spans, metrics labels, events, exceptions or the `toString()` of a type carrying one | Log-capture test pattern + review | ✅ / 👁 |
| D-22 | New endpoint: authentication, authorization of the resource owner, input validation, `ProblemDetail` errors without internals | Acceptance scenarios + review | 👁 |
| D-23 | Every abuse case of the refinement record is a passing scenario, and every action listed under audit evidence writes its audit row | `/accept` + review | 👁 |

### F. Observability — OpenTelemetry, metrics, logs, traces

A feature that cannot be seen in production is not done: support cannot answer "where is
my money?" without it. The signals are designed in refinement (R-12) and checked here.

| # | Criterion | Enforced by | Status |
|---|---|---|---|
| D-24 | **Traces**: a request produces one trace across HTTP, the use case, the database and the provider call; TX-1, provider I/O and TX-2 are distinct spans; the trace id and the correlation id are linked; incoming `traceparent` is honoured | OTel instrumentation + an integration test asserting the span tree | 🔧 KC-44 |
| D-25 | **Metrics**: RED (rate, errors, duration) on every new endpoint and provider operation; a business metric for every new money flow or state (operations by outcome, `UNKNOWN` count, active holds, retries); bounded label cardinality — never a customer id or an amount as a label | Micrometer → OTLP + review | 🔧 KC-44 |
| D-26 | **Logs**: structured, carrying `trace_id`, `span_id` and the correlation id; one log line per business decision at the right level; no PII (D-21) | Log-capture test + review | ✅ correlation id / 🔧 KC-44 |
| D-27 | **Alerting**: a new failure mode that can strand money or customers (stuck state, provider errors, retry exhaustion) has an alert rule and a runbook entry | Alert rules in the repository + review | 🔧 KC-45 |

### G. Data, flags and delivery

| # | Criterion | Enforced by | Status |
|---|---|---|---|
| D-28 | Schema change only through a new `V<yyyyMMddHHmm>__snake_case.sql`, backward compatible with the previous API release (expand / contract) | `MigrationNamingConventionTest` + review | ✅ / 👁 |
| D-29 | A change to a request, response, status code or error body stays readable by the released app, and updates `mobile/docs/01-api-contract.md` and `09-api-evolution.md` in the same PR | Contract check in API CI + review | 🔧 KC-28 / 👁 |
| D-30 | **Feature flags**, per the refinement's decision. (a) A feature split over several items and merged before it is complete sits behind a **release toggle**, default off, removed by the item that completes the feature. (b) **No flag** on a financial invariant, a security control, the audit trail or a KYC eligibility rule: protections are on from their merge, eligibility is versioned policy. (c) The flag is read only in `config/` (wiring the old or the new use case) or in `adapters/in` (an endpoint off answers 404) — never in `domain/` or `application/`. (d) Every flag has its line in the flag inventory: name, type, owner, creation date, removing item. (e) A release toggle has one `@flag-off` scenario proving the feature invisible | `FeatureFlags` port, boot-time validation of the inventory + review | 🔧 KC-25 / 👁 |
| D-31 | Branch `feature/KC-<n>-<slug>` (or `hotfix/`, `release/api-…`, `release/mobile-…`) from the right base; every commit a Conventional Commit with `Refs: KC-<n>` | Commit and branch lint in CI | 🔧 KC-24 |
| D-32 | PR template filled: link to the item, `/dod-check` output, evidence | PR template | 🔧 KC-24 |
| D-33 | Docs moved with the code: `CLAUDE.md` when an invariant, rule or vocabulary changes; an ADR for any decision with a rejected alternative; the state of view when a limit changes status | Review | 👁 |
| D-34 | **Three keys**: code checker **Pass** (fresh session, plus a human reading of the diff when security exposure is material), QA **Accepted**, then PO acceptance; all conversations resolved; no squash, so commits stay traceable | Branch protection + PR record + `review.md` | 🔧 KC-24 / 👁 |

### H. Mobile (any PR touching `mobile/`)

The mobile quality bar, `mobile/docs/08-quality-bar.md`, stays the reference; this
section only says when it applies.

| # | Criterion | Enforced by | Status |
|---|---|---|---|
| D-35 | `npm run verify` (typecheck, lint at zero warnings, Jest) and `npm run audit` green | Mobile CI, independent of the API CI | 🔧 KC-46 |
| D-36 | No line of the quality bar moves from ✅ or ☑︎ to ⏳; a new behaviour adds its own line | Review against `08-quality-bar.md` | 👁 |
| D-37 | Every screen state the change can reach is handled: loading, empty, error, offline, `PENDING`, `UNKNOWN` | Jest + mobile acceptance scenarios | 👁 / 🔧 KC-48 |
| D-38 | No token, PIN or PII outside secure storage, in logs or in telemetry; `transform-remove-console` intact | Lint + review | ✅ / 👁 |
| D-39 | The app sends `traceparent` and the correlation id on every API call, so a customer complaint leads to the backend trace | Jest + review | 🔧 KC-44 |

---

## Level 2 — Sprint increment

| # | Criterion | Enforced by | Status |
|---|---|---|---|
| S-1 | Every item counted as done meets Level 1 and carries the three keys; an override is written in `review.md`; a partially done item is not done and returns to the backlog | Sprint review | 👁 |
| S-2 | `develop` green on each product's CI at the end of the sprint | API CI, mobile CI | 🔧 KC-20 / KC-46 |
| S-3 | Each item demonstrated at review from its accepted scenarios, with evidence | `review.md` | 👁 |
| S-4 | Product backlog reordered; roadmap and README status updated if an exit gate moved | Review | 👁 |
| S-5 | Retrospective actions are backlog items, owned and sized | `retrospective.md` | 👁 |

## Level 3 — Release (per product: `release/api-vX.Y` or `release/mobile-vX.Y` → `main`)

The API and the app release independently. Each line applies to the product released.

| # | Criterion | Enforced by | Status |
|---|---|---|---|
| RL-1 | Semantic version from the product's commits; its `CHANGELOG` generated from Conventional Commits | Release workflow | 🔧 KC-27 |
| RL-2 | Signed tag `api/vX.Y.Z` or `mobile/vX.Y.Z`; GitHub release with the artifact and its SBOM | Release workflow | 🔧 KC-27 |
| RL-3 | The product's CI, quality gate, vulnerability scan and full acceptance suite green on the release commit | CI | 🔧 KC-20/22/23/46/47 |
| RL-4 | API: migrations applied to a copy of the previous release's schema without error | CI job migrating from the last API tag | 🔧 KC-27 |
| RL-5 | API: the released app versions still supported pass the contract check against the new API | Contract check | 🔧 KC-28 |
| RL-6 | API: load smoke on the payment path within the SLO gates of ADR-005 when the release touches it | `perf/*-run.sh`, result attached | 👁 |
| RL-7 | Dashboards show the new signals; alerts of D-27 deployed with the release | Observability stack + review | 🔧 KC-45 |
| RL-8 | Rollback written and tried: previous version on the new schema works (expand / contract), flags can switch new behaviour off | Release checklist | 👁 |
| RL-9 | Roadmap exit gate evidence attached when the release closes a step; a step is marked delivered only then (ADR-009) | Review | 👁 |
| RL-10 | Flag inventory audited: no release toggle survives the release after its feature completed; every kill switch has an owner and was exercised once | Release checklist | 👁 |
| RL-11 | From the first production release: a kill switch on every provider call and every money flow, inventoried, switchable without a redeploy, and its effect covered by a scenario | Release checklist + acceptance suite | 👁 |

---

## Changing this document

The DoD only tightens. Lowering a threshold or dropping a line needs a retrospective
action and a line below explaining why.

| Date | Change |
|---|---|
| 2026-11-13 | First version |
| 2026-11-13 | Maker-checker |
| 2026-11-13 | Coverage rebuilt from the measured baseline (ratchet, pyramid gate, critical paths, new code, mutation) instead of global percentages; BDD acceptance owned by QA; OpenTelemetry observability; API and mobile pipelines and releases separated |
| 2026-11-13 | Feature flags typed: release toggles mandatory for multi-item features, no flag on invariants, security, audit or eligibility; flags read only at the composition root or the driving adapter; kill switches from the first production release (RL-11); no long-lived feature branch |
