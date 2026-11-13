---
name: sprint-review
description: Run the KORA Core sprint review - gather evidence for each committed item (both maker-checker keys, acceptance criteria, Definition of Done), compute flow measures, write docs/agile/sprints/sprint-NN/review.md and update the product backlog. Use when the user runs /sprint-review or asks to review or close a sprint.
---

# /sprint-review

1. Open the current `docs/agile/sprints/sprint-NN/sprint-backlog.md`.
2. For each committed item, collect evidence, never assume it:
   - merged PR or branch: `git log develop --grep "Refs: KC-<n>"`
   - **code checker key**: who checked, in a session distinct from the maker's, and the
     verdict Pass or Fail with its findings
   - **QA key**: the `## QA acceptance` section of `refinements/KC-<n>.md` written by
     `/accept` — scenario results with evidence, verdict Accepted or Rejected
   - an item missing either key is not ready for PO acceptance; say which key is missing
   - DoD Level 1 failures from the `/dod-check` output in the PR
   - for API or app changes, a demo script: curl calls or app steps the PO can replay
3. Run the increment checks per product and report real output: `./gradlew build` and,
   once KC-47 lands, `./gradlew acceptanceTest` on `develop`; `npm run verify` in
   `mobile/` if the app changed.
4. Compute the measures of `docs/agile/README.md` §7 from git dates and the daily log:
   throughput, cycle time and review wait (medians), checker fails, QA rejections,
   overrides, DoD failures, escaped defects, ceremony share, plan usage per accepted
   item and usage-limit hits. Use the flow-measures script once KC-50 lands; never
   estimate a measure you can compute.
5. Write `review.md` from `docs/agile/templates/sprint-review.md`, leaving the **PO
   decision** column empty.
6. Present each item to the PO with its evidence and wait for the decision: Accepted,
   Rejected, or Accepted over a checker Fail or a QA Rejected — the last one requires the
   PO's written reason, which you record verbatim. Never fill a decision yourself.
7. Classify: Done (three keys), Not done (back to the backlog with what remains), Dropped
   (with the PO's reason). There is no "almost done". Update `product-backlog.md`.
8. If an exit gate moved, propose the `ROADMAP.md` and `README.md` status edits; a step is
   marked delivered only by the PO, on evidence (ADR-009).
