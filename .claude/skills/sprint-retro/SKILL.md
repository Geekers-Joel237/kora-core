---
name: sprint-retro
description: Run the KORA Core sprint retrospective from evidence (review, git history, CI, agent behaviour), propose at most two improvement actions as backlog items, and write docs/agile/sprints/sprint-NN/retrospective.md. Use when the user runs /sprint-retro or asks for a retrospective.
---

# /sprint-retro

1. Read the sprint's `sprint-backlog.md` (daily log), `review.md`, and the previous
   retrospective's actions.
2. Gather facts before opinions:
   - previous actions: done or not, and did they change anything measurable
   - flow: the measures in `review.md` against the previous three sprints — throughput,
     cycle time, **review wait** (the constraint), ceremony share; carry-over, unrefined
     entries, WIP breaches
   - maker-checker: checker fails, what they caught, and every PO override with its
     reason — was the override right in hindsight?
   - quality: DoD lines failed at review, reverts, fixes on `develop`, escaped defects
   - agents: refinement findings later proved wrong or missing, per agent; rework caused
     by agent output; plan usage per accepted item and limit hits; whether an agent's
     model or effort should move up (misses) or down (no miss over three sprints)
3. Ask the PO three questions and wait: what helped, what hurt, what to try. Do not fill
   the PO's answers in.
4. Propose **at most two actions**, each specific, owned, sized, with a check that tells
   at the next retro whether it worked. An action that changes an agent, a skill, the DoD
   or the DoR names the file and the edit.
5. Write `retrospective.md` from `docs/agile/templates/retrospective.md` and add the
   accepted actions to `product-backlog.md` as items tagged `retro`.
6. If something learned is durable — a working rule the PO confirms — propose saving it
   to memory or to `CLAUDE.md`; do not save without the PO's yes.
