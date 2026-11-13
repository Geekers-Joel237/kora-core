---
name: refine
description: Refine a KORA Core backlog item with the five-agent panel (business, QA acceptance, senior backend, senior mobile, security & compliance) and write the consolidated refinement record, BDD scenarios included, with a Ready / Not ready verdict. Use when the user runs /refine KC-<n> or asks to refine a backlog item.
---

# /refine KC-<n>

Argument: one backlog ID, e.g. `KC-29`. Without one, take the first item of
`docs/agile/product-backlog.md` whose status is `New` or `Refining`, and say which.

The PO runs on a Claude Pro plan: the usage quota, not money, is the limit. Refine at most
two items per five-hour usage window, and spread refinement over the sprint.

## Steps

1. Read the item in `docs/agile/product-backlog.md`. If it does not exist, stop and say so.
2. **First refinement**: launch the five agents **in one message, in parallel**, each
   with the effort set here (`docs/agile/README.md` §5 explains the routing):

   | Agent | Effort |
   |---|---|
   | `refinement-security` | high |
   | `refinement-backend` | high |
   | `qa-acceptance` (Mode 1 — refinement) | high |
   | `refinement-business` | medium |
   | `refinement-mobile` | medium |

   Give each the same prompt: the item ID, its full text, its epic. Never pass one
   agent's output to another.

   **Re-refinement** (`docs/agile/refinements/KC-<n>.md` exists): relaunch **only** the
   agents whose section an answer of the PO changes, with the previous record's
   questions and the PO's answers. Keep the other sections as they are and say which
   agents were not relaunched.
3. Consolidate into `docs/agile/refinements/KC-<n>.md` from
   `docs/agile/templates/refinement.md`:
   - **For the PO first**, on one page: the questions, the disagreements with each
     position (never resolved by you), the security blockers, the verdict
   - **scenarios**: the QA's Gherkin. A business criterion or a security abuse case no
     scenario covers goes under *Gaps for QA* and back to `qa-acceptance` (one call,
     effort high, only those gaps). The final scenarios are all written by QA
   - design notes from backend and mobile side by side; observability; security
     exposure, threats, audit evidence, compliance points *to confirm*
   - estimate per product; an item touching both products is split per product
   - the Definition of Ready, line by line, with evidence
   - verdict **Ready** only if every DoR line holds, no disagreement is open, and no
     security blocker is open
4. Update the item in `product-backlog.md`: status `Ready` or `Refining`, size, WSJF
   recomputed from the business scores, link to the record. If the panel proposed a
   split, add the new items with fresh IDs (next free `KC-<n>`) and mark the parent
   `Split`.
5. Add a line to the sprint's daily log: item refined, agents run, and the plan usage
   consumed if the PO reports it.
6. Reply with: verdict, estimate, the PO section of the record, and its path. Nothing else.

## Rules

- The PO decides. You never reorder the backlog beyond recomputing WSJF, and never mark
  an item Ready with an open question.
- Keep agent outputs out of the main context where possible: the record is the artifact.
