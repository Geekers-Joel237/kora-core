# ADR-009 — One Roadmap, Extended Append-Only

**Date**: 2026-11-09
**Status**: Accepted
**Authors**: Kora Core Engineering — Ivan Joël Tchatchoua Bayon
**Related**: ADR-007 — Hexagonal Before Modular · `ROADMAP.md` · `README.md`

---

## Context

Three roadmaps described KORA Core at the same time, and they disagreed.

| Document | Steps | Idempotency | Outbox | Reconciliation | AI |
|---|---|---|---|---|---|
| `ROADMAP.md` (committed) | 0–10 | Step 4 | Step 5 | Step 6 | absent |
| Extension draft (`ROADMAP_updated`) | 0–10 unchanged + 11–32 | Step 4 | Step 5 | Step 6 (+23) | Steps 25–32 |
| Rewrite draft (`ROADMAP_EXTENDED_FINAL`) | 0–13, renumbered | Step 0, marked delivered | Step 6 | Step 8 | Step 13 |

The rewrite draft is the more readable document, but adopting its numbering has two costs.

**It contradicts the code.** It marks Step 0 delivered while listing idempotency keys in
Step 0. The state of view records idempotency as absent (L-4), and `README.md` lists it as
not built. A reader comparing the two would conclude that the roadmap cannot be trusted.

**It breaks every existing reference.** `CLAUDE.md`, `HELP.md`, `CONTRIBUTING.md` and
the ADRs refer to steps by the current numbering — 129 references at the time of this
decision. ADR-007 already paid for two documents meaning different things
by "Step 2". Renumbering would repeat that failure across the whole repository.

## Decision

1. **`ROADMAP.md` is the single authoritative roadmap.** Steps 0–10 keep their numbers and
   their text.
2. **Steps 11–32 are added as an append-only extension**, with explicit dependencies. No
   existing step is renumbered or rewritten.
3. **The rewrite draft is not adopted as a numbering.** Its narrative — the dependency chain
   from financial correctness to WalletOps, and the AI governing rule — is carried into
   `README.md`.
4. **A step is marked delivered only when its exit gate passes.** Status lives in two places
   only: the status line at the top of `ROADMAP.md` and the status table in `README.md`.
5. **Language, cloud and AI boundaries are recorded** in an append-only section of
   `ROADMAP.md`:
   - **Go** enters at Step 7, as the settlement-file ingestion worker of the extracted
     reconciliation service: I/O-bound, stateless, behind a versioned event contract.
   - **Scala** enters at Steps 20–22, for feature pipelines over historical data.
   - **AWS** is the reference cloud from Step 9.
   - **LLM components** enter at Steps 25–32, behind the WalletOps authority boundary.

## The AI governing rule

> AI may observe, retrieve, correlate, explain and propose. Deterministic services
> authenticate, authorize, validate financial invariants and execute financial operations.

Concretely: no model, prompt or tool holds a database identity that can write the ledger;
every consequential action goes through an existing application command, with its
authorization, validation and audit; WalletOps can be disabled without affecting payment
execution.

## Consequences

**Positive**
- One numbering across the code, the docs and the ADRs; no reference changes.
- The README, the roadmap and the state of view now tell the same story, so a reader can
  check any claim against the code.
- A second language cannot reach the financial core: it enters only behind a contract, at a
  named step, with an ADR.

**Negative**
- `ROADMAP.md` is long — about 1,500 lines. The README status table is the entry point; the
  roadmap is the reference.
- Steps 11–32 are dependency-ordered rather than chronological, so the step number is not
  the delivery order. The dependency summary at the end of the extension is the delivery
  order.

**Follow-ups**
- Delete the two drafts from any working copy so that they cannot be cited again.
- Each future step that introduces Go, Scala, an AWS-managed service or an LLM component
  records its benchmark or operational argument in its own ADR.
