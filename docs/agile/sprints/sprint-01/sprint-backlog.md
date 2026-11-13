# Sprint 01 — 2026-11-16 → 2026-11-27

> **Draft.** Proposed, not committed: the items still go through `/refine`, and the PO
> confirms goal and capacity at planning.

**Increment**: INC-1 · **Sprint goal**: *each product has its own pipeline, and the
pipeline — not memory — enforces the first lines of the Definition of Done.*

**PO decision (2026-11-13)**: an enabler-only sprint is accepted. Every later item is
measured against the DoD this sprint makes executable; starting customer work first would
have it merged under rules nobody enforces.

**PO attention**: ~20 h · **Ceremonies**: ~2 h 45 · **Items**: 5 committed (cap 5) + 1 stretch

## Proposed
| ID | Title | Product | Type | Size | Maker | Branch | Status |
|---|---|---|---|---|---|---|---|
| KC-24 | Git Flow guardrails | both | enabler | 2 | Claude | `feature/KC-24-gitflow-guardrails` | To refine |
| KC-20 | API CI | API | enabler | 3 | Claude | `feature/KC-20-api-ci` | To refine |
| KC-46 | Mobile CI | mobile | enabler | 2 | Claude | `feature/KC-46-mobile-ci` | To refine |
| KC-29 | Spring Boot 4.0.8 | API | bug | 2 | Claude | `feature/KC-29-spring-boot-4-0-8` | To refine |
| KC-21 | Coverage instruments | API | enabler | 5 | Claude | `feature/KC-21-coverage-instruments` | To refine |
| KC-30 | Coupling count drift — *stretch* | API | bug | 1 | Joël | `feature/KC-30-coupling-count-docs` | To refine |

KC-23 (supply-chain scan) and KC-47 (BDD harness) open Sprint 02; KC-22 (Sonar) follows
once the SonarQube Cloud organisation and token exist.

Sprint 01 also sets the baseline of plan usage per ceremony on the Pro plan: each
ceremony notes in the daily log the share of usage it consumed and any limit hit.

## Risks and dependencies
- KC-20 needs Docker in the CI runner for Testcontainers (GitHub-hosted Ubuntu runners have it).
- KC-21 records the floors from the 2026-11-13 baseline; if `develop` moves before it
  lands, the floors are re-measured, never guessed.
- Until KC-24 lands, the three keys are recorded by hand in the PR description.
- Until KC-47 lands, `/accept` runs the scenarios by hand against `./gradlew bootRun`.
  Most Sprint 01 items are pipeline enablers: their scenarios are about the pipeline's
  behaviour (a red build blocks a merge), observed on a throwaway PR.

## Daily log
| Date | Done | Next | Blocked | Plan usage (ceremony, % used, limit hit?) |
|---|---|---|---|---|
