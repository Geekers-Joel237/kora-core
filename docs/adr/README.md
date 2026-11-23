# Architecture Decision Records

Every significant decision in KORA Core is recorded here: the context, the options weighed,
what was chosen, and **what it cost**. A record is never rewritten once accepted; a decision
that changes gets a new record that supersedes the old one.

| ADR | Decision | Date | Status |
|---|---|---|---|
| [001](./ADR-001-immutable-ledger.md) | Immutable double-entry ledger, and why the balance is a cache rather than a sum | 2026-03-14 | Accepted |
| [002](./ADR-002-payment-lifecycle.md) | The payment lifecycle state machine and its locking | 2026-03-18 | Accepted |
| [003](./ADR-003-payment-api-design.md) | One call per operation, no separate authorize / capture API | 2026-04-13 | Accepted |
| [004](./ADR-004-micro-transaction-optimization.md) | TX-1 / provider I/O / TX-2 — no connection held across a network call | 2026-05-24 | Accepted |
| [005](./ADR-005-load-test-calibration.md) | Load-test calibration and the SLO gates | 2026-05-24 | Accepted |
| [006](./ADR-006-compose-topology.md) | Compose topology: one file per environment, services selected by profile | 2026-08-26 | Accepted |
| [007](./ADR-007-hexagonal-before-modular.md) | Hexagonal before modular; the transaction boundary is not a Unit of Work | 2026-09-06 | Accepted |
| [008](./ADR-008-funds-holds.md) | Funds holds for debits awaiting the provider | 2026-10-17 | Accepted, implemented |
| [009](./ADR-009-single-roadmap.md) | One roadmap, extended append-only; where Go, Scala and AI enter | 2026-11-09 | Accepted |

ADR-005 and ADR-006 are written in French, the others in English.

## When to write one

A decision is significant, and needs a record, if it affects:

- financial correctness or auditability;
- the boundary between a module, a layer or a port;
- the concurrency or transaction isolation strategy;
- a trade-off between performance and correctness.

## How to write one

Take the next number and follow the shape of the existing records:

```markdown
# ADR-0NN — Title, as the decision

**Date**: yyyy-MM-dd
**Status**: Proposed | Accepted | Superseded by ADR-0MM

## Context
What forces the decision, with the measurements behind it.

## Decision
What is chosen, stated so a diff can be checked against it.

## Options considered
Each alternative, and why it lost.

## Consequences
What it costs, what it rules out, and what would make us revisit it.
```

Link the new record from this table in the same pull request.
