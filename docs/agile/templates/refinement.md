# KC-<n> — <title> · Refinement record

**Epic**: EPIC-<step> · **Product**: API · mobile · both · **Date**: <yyyy-mm-dd> · **Round**: 1 · **Verdict**: Ready | Not ready

## For the PO — one page

**Questions**
1.

**Disagreements**
| Topic | Business | QA | Backend | Mobile | Security | PO decision |
|---|---|---|---|---|---|---|

**Security blockers**:
-

**Agents run this round**: business · QA · backend · mobile · security — and those not relaunched, with why

---

## Outcome
<one sentence from the business agent>

## Acceptance scenarios — owned by QA
```gherkin
@KC-<n> @api
Feature: <title>
  <outcome in business words>

  Scenario: <happy path>
    Given …
    When …
    Then …
```

Not covered, and why:
-

Gaps for QA (business criteria or abuse cases without a scenario — must be empty for Ready):
-

## Money and trust risks
-

## Design — backend
- Where it lands:
- Boundaries, locks, provider calls:
- Data and migrations:
- Developer tests (reproduce first, levels):
- ADR needed:

## Design — mobile
- Impact: none | contract only | app change
- Contract and compatibility with the released app:
- Screens and states:

## Observability
| Signal | Name | Labels / attributes | Why |
|---|---|---|---|
| span | | | |
| metric | | | |
| log event | | | |
| alert + runbook | | | |

## Security and compliance
- Exposure: none | low | material
- Threats and controls:

| Threat (STRIDE) | Actor and abuse | Impact | Control | Blocker? |
|---|---|---|---|---|

- PII and redaction:
- Audit evidence:
- Compliance — to confirm:

## Feature flag (DoD D-30)
- Type: release toggle · none (completes in one item) · forbidden here (invariant, security, audit, eligibility)
- Name · read in `config/` or `adapters/in` · effect when off · removed by KC-<n>
- `@flag-off` scenario:

## Split and estimate
| Item | Product | Points |
|---|---|---|

## Definition of Ready
| # | Holds | Evidence |
|---|---|---|
| R-1 … R-12 | | |

## QA acceptance
<written by /accept: date, commit, scenario results with evidence, exploratory notes, verdict>
