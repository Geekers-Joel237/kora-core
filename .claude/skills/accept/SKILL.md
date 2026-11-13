---
name: accept
description: Run the QA acceptance of a KORA Core backlog item - the qa-acceptance agent validates the feature black-box against its BDD scenarios and gives the QA verdict, recorded for the sprint review. Use when the user runs /accept KC-<n> or asks for the QA validation of an item.
---

# /accept KC-<n>

1. Check the preconditions, and stop with the reason if one fails:
   - `docs/agile/refinements/KC-<n>.md` exists and holds the scenarios
   - the item's PR or branch exists, and its code checker verdict is **Pass**
     (`/code-review` + `/dod-check` in a session other than the maker's)
2. Launch the `qa-acceptance` agent in **Mode 2**, effort **high**, with: the item ID, the branch, the path
   of the refinement record. Do not pass it the maker's conversation, the diff summary or
   the unit tests — it judges the feature from the outside.
3. Append its verdict to `docs/agile/refinements/KC-<n>.md` under `## QA acceptance`:
   date, branch and commit, scenario-by-scenario result with evidence, exploratory
   notes, verdict **Accepted** or **Rejected**.
4. Update the item's status in the sprint backlog: `PO acceptance` if Accepted, back to
   `In progress` with the failed scenarios if Rejected.
5. Reply with the verdict and the failed scenarios only.
