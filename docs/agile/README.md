# KORA Core — Delivery operating model

**Scrum, with SAFe's planning tools, and maker-checker on every item.** One human,
agents as team members with a narrow written role. `ROADMAP.md` stays the only roadmap
(ADR-009); this folder says **how** a step of it gets built, not **what** comes next.

## 1. Why this shape

SAFe exists to coordinate many teams whose dependencies cross; here there is one team and
one human, so its structure (release train, PI planning event, ART roles) would cost
ceremony and buy nothing. Its tools are kept because they fit: epics with exit gates,
WSJF, enablers, an innovation-and-planning sprint, objectives tied to a release.

Two facts shape everything else:

- **The constraint is the PO's attention, not coding speed.** Agents write fast; what
  limits throughput is refinement answers, review and acceptance — about 10 hours a week.
  Plans are made in that unit.
- **The PO is also the developer and the acceptor.** Finance forbids one person being
  maker and checker of the same operation; delivery here follows the same rule (§6).

## 2. Hierarchy of work

| Level | Here | Lives in |
|---|---|---|
| Epic | One `ROADMAP.md` step (`EPIC-04` = Step 4), closed only when its exit gate passes | `ROADMAP.md` |
| Feature / Enabler | A sub-step or exit-gate criterion; an enabler is tooling, architecture or debt with no direct customer value | `product-backlog.md` |
| Story | A vertical slice of at most 5 points, `KC-<n>` | `product-backlog.md`, then a sprint backlog |
| Task | A step inside a story | the story's PR checklist |

IDs continue the existing `KC-` series; new items start at `KC-20`. An ID is never
reused, even for a dropped item.

## 3. Cadence and attention budget

| Unit | Length | Contents |
|---|---|---|
| Sprint | 2 weeks, ~20 hours of PO time | Planning · async daily log · refinement · review · retrospective |
| Increment | 4 sprints + 1 IP sprint (10 weeks) | Increment objectives, one roadmap step or part of one, ends with a release |
| IP sprint | Last 2 weeks of an increment | No committed stories: debt, spikes, DoD tightening, articles, next increment planning |

Ceremonies are prepared by agents so that PO time goes to decisions, not to paperwork:

| Ceremony | PO time per sprint |
|---|---|
| Planning (`/sprint-planning`) | 30 min |
| Refinement (`/refine`, answering the panel's questions) | 60 min |
| Review and acceptance (`/sprint-review`) | 45 min |
| Retrospective (`/sprint-retro`) | 30 min |
| **Total** | **~2 h 45, about 14 %** |

The rest — ~17 hours — is review of diffs and the PO's own development. On a Claude Pro
plan the usage quota is the second budget: refinement is spread over the sprint, at most
two items per five-hour usage window, and a usage-limit hit is noted in the daily log. Above 20 % on
ceremonies for two sprints in a row is a retrospective topic.

## 4. Roles

| Role | Who | Accountable for |
|---|---|---|
| Product Owner | Joël | Backlog order, answers to the panel, final acceptance, every scope trade-off |
| Scrum Master / orchestrator | Claude Code, main session | Running the ceremonies through the skills, keeping artifacts current, surfacing impediments |
| Maker | Whoever implements an item: Joël or a Claude session | The change, its tests, `/dod-check` before the PR |
| Code checker | Whoever did **not** implement it — see §6 | The independent verdict on the change: code, design, DoD |
| QA acceptance | `qa-acceptance` agent | The feature as a whole, black-box, against the BDD scenarios it wrote |
| Refinement panel | Five subagents, §5 | An item leaves refinement Ready or with a written reason why not |

A human decides; an agent proposes and evidences. No agent reorders the backlog, accepts
a story or merges a PR.

## 5. Refinement panel — business, QA, backend, mobile, security

Five agents read the same item independently, in parallel, then the orchestrator
consolidates. Independence matters: an agent that reads another's answer anchors on it.

| Agent | File | Asks |
|---|---|---|
| **Business** — `refinement-business` | `.claude/agents/refinement-business.md` | Which customer or operator outcome? Which money flow, which trust risk? Acceptance criteria in Given / When / Then. Smallest valuable slice? |
| **QA acceptance** — `qa-acceptance` | `.claude/agents/qa-acceptance.md` | How does the feature behave, seen from outside? Writes the Gherkin scenarios — happy path, refusals, network reality, money integrity seen through balances, abuse — which become the acceptance criteria. Ignores how the developer builds or unit-tests it |
| **Senior backend** — `refinement-backend` | `.claude/agents/refinement-backend.md` | Module, layer, port, migration, ADR; `CLAUDE.md` rules at stake; boundaries and provider calls; split, estimate, feature flag |
| **Senior mobile** — `refinement-mobile` | `.claude/agents/refinement-mobile.md` | Contract change and compatibility with the released app; screens and states (loading, offline, `PENDING`, `UNKNOWN`); secure storage; quality bar lines |
| **Security & compliance** — `refinement-security` | `.claude/agents/refinement-security.md` | Threat model on the boundaries crossed, abuse cases as testable criteria, PII and redaction, audit evidence, AML/CFT and BEAC / BCEAO impact to confirm with compliance |

The mobile and security agents run on every item, not only on the obvious ones: the
costly misses are a backend change that silently breaks a released app, and a harmless
looking endpoint that opens another customer's wallet. When nothing is at stake, each says
so in three lines and the record keeps that answer.

**Model and effort follow the cost of a miss**, not the prestige of the role. The PO is
on a Claude Pro plan, so the limit is the usage quota and the PO's reading time, not
money:

| Agent | Model | Effort | Why |
|---|---|---|---|
| Security | Opus | high | The most expensive miss: another customer's wallet, replay |
| Backend | Opus | high | Invariants and review rules need judgement |
| QA acceptance | Opus | high | Its scenarios are the acceptance criteria; exploration needs judgement |
| Business | Sonnet | medium | Its criteria are rewritten as scenarios by QA, abuse cases added by security |
| Mobile | Opus until KC-28 lands, then Sonnet | medium | Once the contract is checked by machine, its miss has a backstop |

Mechanical work — commit lint, flow measures — goes to scripts, never to a model. Each
agent reads only the sections it needs and answers in at most 600 words; a re-refinement
relaunches only the agents an answer changes. An agent moved to a cheaper model goes back
up if the panel-accuracy measure shows it missing what it should have caught.

Consolidation produces one record (`templates/refinement.md`): the BDD scenarios — the
QA's, extended with the business agent's criteria and the security agent's abuse cases,
all rewritten as scenarios the QA agrees to own — backend, mobile and security notes,
estimates, open questions for the PO, and a verdict **Ready / Not ready**.
Disagreements are kept, not averaged away; the PO settles them.

Two kinds of test, two owners. The developer's tests — TDD, unit, integration,
component e2e — prove the code and are judged by the code checker against the DoD. The
acceptance scenarios prove the feature and belong to QA; the maker makes them pass and
never edits them.

## 6. Maker-checker

An item is accepted only with **three keys, in this order**:

1. **Code checker — Pass.** The change is right: code, design, tests, DoD.
2. **QA acceptance — Accepted** (`/accept KC-<n>`). The feature is right, seen from outside.
3. **PO — Accepted** at the review. The feature is the one we wanted.

| Maker | Code checker |
|---|---|
| A Claude session | Joël reviews the PR diff **and** a fresh checker session runs `/code-review` and `/dod-check` |
| Joël | A fresh Claude session runs `/code-review` and `/dod-check` on the PR |

- **Fresh** means a session that did not write the code and starts from the PR, not from
  the maker's conversation: no shared context, no shared blind spots.
- The checker's verdict is **Pass** or **Fail** with findings, recorded in the PR.
- Items whose security exposure is *material* (money, authentication, authorization, PII)
  always need a human reading of the diff, whoever the maker is.
- The PO may accept over a checker Fail or a QA Rejected only with a written reason in
  `review.md`. Each such override is counted, and reviewed at the retrospective.
- Acceptance is on evidence: every scenario passed with its output attached. "It looks
  right" is not evidence.

## 7. Flow rules and measures

- **Definition of Ready** gates entry into a sprint: [`definition-of-ready.md`](./definition-of-ready.md).
- **Definition of Done** gates every merge: [`definition-of-done.md`](./definition-of-done.md).
- **WIP limit**: two items in progress, one of them at most in review. Finish before starting.
- **Size**: story points, 1, 2, 3, 5, as a **size cap only**. An item above 5 is split.
  Points are not summed into a velocity to plan with.
- **No long-lived branch**: a feature branch carries one item and merges within the
  sprint. A feature split over several items merges each one into `develop` behind a
  release toggle (DoD D-30), so `develop` stays integrated and releasable.
- **Capacity**: planned in items against the attention budget — about 2 hours of PO time
  per item (questions, review, acceptance), so **five items per sprint at most** until
  the measures say otherwise.
- **Prioritisation**: WSJF = (business value + time criticality + risk reduction) / size;
  departures are written down. Ties go to the item that closes a limit of the state of view.
- **Bugs and limits**: a reproduced defect enters the backlog as a story whose first task
  is the failing test.
- **Spikes**: time-boxed, output is a written answer or an ADR, never production code.

Measured at every review, compared at every retrospective:

| Measure | Meaning |
|---|---|
| Throughput | Items accepted per sprint |
| Cycle time | First commit to acceptance, per item |
| Review wait | Time an item waits for the PO — the constraint, made visible |
| Checker fails | Items failed by the code checker |
| QA rejections | Items rejected at acceptance — what the developer's tests did not catch |
| Overrides | PO acceptances over a Fail or a Rejected |
| DoD failures | DoD lines failed at review |
| Escaped defects | Defects found after acceptance |
| Ceremony share | PO time on ceremonies over total |
| Panel accuracy | Refinement findings later proved wrong or missing, per agent |
| Plan usage | Share of the Pro plan's usage consumed per ceremony, and usage-limit hits — per accepted item |

## 8. Git workflow, two products, two pipelines

Git Flow and Conventional Commits, both bound to a backlog ID. Details in
[`CONTRIBUTING.md` §8](../../CONTRIBUTING.md#81-commit-conventions).

The API and the mobile app share the repository, not their rhythm. Each has its own CI
workflow, triggered only by its own paths, its own version and its own releases. What
binds them is the HTTP contract, guarded by the contract check (D-28) — not a shared
pipeline.

| | API | Mobile |
|---|---|---|
| Paths | everything but `mobile/**` | `mobile/**` |
| CI | `.github/workflows/api-ci.yml` (KC-20) | `.github/workflows/mobile-ci.yml` (KC-46) |
| Version | `build.gradle` | `mobile/package.json` |
| Release branch | `release/api-v0.4` | `release/mobile-v0.2` |
| Tag | `api/v0.4.0` | `mobile/v0.2.0` |

```
feature/KC-23-supply-chain-scan        from develop, back to develop
release/api-v0.4                       from develop, to main and develop
hotfix/KC-31-reversal-ignores-result   from main, to main and develop

feat(payment): return the original result on a replayed idempotency key

Refs: KC-35
```

A change that touches both products is two items, or one item with two PRs: the API one
merges first, backward compatible, and the app follows at its own pace.

## 9. Where things live

```
docs/agile/
├── README.md                  this file
├── definition-of-ready.md
├── definition-of-done.md
├── product-backlog.md         increment objectives, epics, items
├── refinements/KC-<n>.md      one consolidated record per refined item
├── sprints/sprint-NN/         sprint-backlog.md · review.md · retrospective.md
└── templates/                 refinement · sprint-backlog · sprint-review · retrospective
.claude/agents/                business · qa-acceptance · backend · mobile · security
.claude/skills/                /refine · /accept · /sprint-planning · /sprint-review · /sprint-retro · /dod-check
```

The backlog is versioned Markdown so that agents read it without a connector and every
reorder is a reviewable diff. The files stay the source of truth until an ADR says otherwise.
