---
name: sprint-planning
description: Run KORA Core sprint planning (or increment planning with the argument "inc"): propose a sprint goal and a sprint backlog of Ready items within the PO's attention budget, and write docs/agile/sprints/sprint-NN/sprint-backlog.md. Use when the user runs /sprint-planning or asks to plan a sprint or an increment.
---

# /sprint-planning [inc]

Capacity is the PO's attention, not coding speed (`docs/agile/README.md` §3, §7).

## Sprint planning

1. Find the next sprint number from `docs/agile/sprints/`. Read the previous sprint's
   `review.md` and `retrospective.md`: unfinished items and retro actions come first.
2. Capacity: ask the PO for hours available this sprint if it differs from ~20. Item cap
   is 5 by default; adjust only from measures (throughput and review wait of the last
   three sprints), and say which measure justifies it. Points are not summed.
3. If the sprint is the IP sprint (fifth of an increment), commit no stories: propose
   debt, spikes, DoD tightening, articles and the next increment's planning.
4. Propose **one sprint goal**: one sentence, an outcome, tied to an increment objective.
5. Select items in backlog order that serve the goal and are `Ready`, up to the cap, plus
   at most one stretch item. Retro actions count against the cap. An item that is not
   Ready is not selected — list it as a candidate for `/refine`.
6. Propose a maker per item (Claude or Joël). Prefer Joël as maker on items whose
   security exposure is material only when he wants hands-on work; the checker rule of
   README §6 applies either way.
7. Write `docs/agile/sprints/sprint-NN/sprint-backlog.md` from
   `docs/agile/templates/sprint-backlog.md`; mark items `Committed (Sprint NN)` in
   `product-backlog.md`.
8. Reply with the goal, the committed list, the stretch item, what was left out and why.
   The PO confirms before anything starts.

## Increment planning (`inc`)

1. Read `ROADMAP.md` waves and status, `product-backlog.md`, the last two retrospectives
   and the measures of the last increment.
2. Estimate the increment's room: delivery sprints × item cap. Propose 3 to 5 objectives
   that fit it, measurable, each committed or uncommitted, business value left for the PO.
3. Sketch the four sprints at feature level, with dependencies and ROAM risks (resolved,
   owned, accepted, mitigated); the fifth is the IP sprint.
4. Write the increment section at the top of `product-backlog.md`.
