# Definition of Ready

An item enters a sprint only when every line holds. `/refine` checks them and writes the
verdict in `refinements/KC-<n>.md`.

| # | Criterion |
|---|---|
| R-1 | Written as an outcome: *as a … I want … so that …*, or for an enabler, the capability it unlocks and for whom |
| R-2 | Linked to its epic (roadmap step) and, where relevant, to the limit (`L-<n>`) or ADR it touches |
| R-3 | Acceptance criteria are BDD scenarios written by QA, black-box, including at least one failure path, and owned by QA from now on |
| R-4 | Financial invariants at stake are named (`CLAUDE.md` §6), or the item states that none is |
| R-5 | The developer's test intent is known: what is reproduced first for a defect, which levels the change needs |
| R-6 | Design impact known: product (API, mobile or both, and in which order), module, layer, ports, migrations, new ADR or not |
| R-7 | Feature flag decision recorded, typed per D-30: a release toggle (name, the item that removes it) when the feature spans several items; none when the item completes in one; never on an invariant, a security control, the audit trail or an eligibility rule |
| R-8 | Estimated at 5 points or less by the panel; anything larger is split |
| R-9 | No open question left for the PO, or each one is answered in the record |
| R-10 | Dependencies on other items are done or committed in the same sprint |
| R-11 | Security exposure classified; for a material one, threats have controls, abuse cases are scenarios, and no blocker raised by the security agent is open |
| R-12 | Observability designed: the spans, the metrics (with their labels) and the log events the feature emits, and the alert if it can strand money or customers — or "none needed" with the reason |
