# ADR-007 — Hexagonal Before Modular

**Date**: 2026-09-06
**Status**: Accepted
**Authors**: Kora Core Engineering — Ivan Joël Tchatchoua Bayon
**Related**: ADR-004 — Micro-Transaction Model · ADR-002 — Payment Lifecycle State Machine

---

## Context

`ROADMAP.md` planned Étape 2 as the modular monolith and Étape 3 as the hexagonal
migration. Étape 3 is done. Étape 2 was started after it and is not finished — see
*Consequences* for exactly what was and was not delivered.

Three things pushed the inversion, and one of them was a debt with a number on it.

**ADR-004 left an unpaid boundary.** The micro-transaction model split cash-in and
cash-out into TX-1 / provider I/O / TX-2 and fixed the pool exhaustion, but the split
was implemented with Spring's `TransactionTemplate` held directly by
`PaymentTransactionalExecutor`. The application layer therefore still named the
framework's transaction manager and its persistence exceptions. ADR-004 recorded that
as Step 2 work and deferred it.

**Modules drawn over an unclear layering encode the wrong lines.** A module boundary is
a statement about which types may cross it. Drawing that boundary while
`application/service` still imported `org.springframework.orm` would have frozen the
framework dependency into the module contract — and unpicking it afterwards means
moving files twice.

**The layer rules are cheap to check; the module rules are not.** A test that reads
imports can decide "does `domain/` name Spring?" from the source alone. Deciding
"should this type belong to payment or to auth?" needs the layering to already be
legible, because the answer is usually "it belongs to the port, and the port belongs
to whoever declares it".

## Decision

**Do the hexagonal split first, then the modules.**

The order that was executed:

1. `TransactionBoundary` as a driven port, `SpringTransactionBoundary` as its adapter
2. Domain types for what crossed as strings — `Msisdn`, `Pin`, `Id`
3. Driving ports, one method each, one per use case
4. Explicit interactors replacing `PaymentService` and `PaymentTransactionalExecutor`
5. Result models, so no aggregate reaches a controller
6. The command bus as a *driving adapter*, with explicit registration
7. Three middlewares — correlation, validation, anti-replay
8. `auth` / `payment` / `shared`, with the couplings that remain written down
9. The architecture rules that could not be green before
10. This document

Idempotency stays where the roadmap put it, at Étape 4. It was not folded into the bus.

## Consequences

### The numbering now has one meaning

`CLAUDE.md` and `ROADMAP.md` used to say "Step 2" about two different things:
`CLAUDE.md` numbered the technical debts it tracked, `ROADMAP.md` numbered the product
milestones. A gap marked "Step 2 target" in one file pointed at a milestone in the
other that had nothing to do with it.

**`ROADMAP.md` numbering is authoritative.** `CLAUDE.md` refers to roadmap steps and
numbers nothing of its own. Where a gap has no roadmap home, it says so in words.

### Étape 3 is complete; Étape 2 is half-delivered

`ROADMAP.md` keeps its original numbering — renumbering a document people have read is
worse than recording that two entries were executed out of order. Both entries carry a
note saying so.

**Delivered for Étape 2**: three packages, a kernel that names no module, and the
remaining cross-module couplings recorded as an equality so that a new one fails the
build and closing one fails it too.

**Not delivered, and it is the substance of the étape**: separate Spring Boot
applications per module, and modules that do not share entities. There is one Gradle
project and one `@SpringBootApplication`, so the boundary is asserted by a test rather
than enforced by the build; and eleven classes cross, `Customer` and `Account` among
them. A module whose aggregate the neighbour imports has a folder, not a boundary.

This ADR originally recorded both étapes as complete. That was wrong, and the evidence
was in a test written in the same session — `ModuleBoundariesTest` lists every crossing
by name. Recorded here rather than quietly amended, because the point of the recording
is to make the list shrink deliberately.

### `TransactionBoundary` is not a Unit of Work

It runs a supplier inside one transaction and nothing else. It tracks no dirty
aggregates, queues no writes, and flushes nothing: the work writes through repositories
as it goes, and the boundary decides only whether those writes become permanent.

This is written down because the interface looks like the beginning of a Unit of Work,
and "improving" it into one would be a silent, expensive change. A Unit of Work owns
the identity map and the write order; taking that over from the ORM here would mean
re-implementing what Hibernate already does, and would give the application layer a
second opinion about when a write happens.

Rollback is by throwing. There is no `setRollbackOnly`, no status object, and no way to
ask for a rollback while returning normally. A `BusinessException` — or any other
unchecked throwable — discards the writes and travels to the caller unchanged; a normal
return commits. `SpringTransactionBoundaryTest` pins all of it against a recording
transaction manager, because Spring's default happens to be right and a default nobody
asserts is not a contract.

### Retry wraps the boundary; the boundary never wraps the retry

```java
retry.execute(() -> boundary.execute(() -> settle(...)))   // correct
boundary.execute(() -> retry.execute(() -> settle(...)))   // wrong
```

`ConcurrentUpdateException` is raised when the *commit* fails, so by the time it is
visible the transaction is already dead. Retrying inside it would replay work on a
transaction that can no longer commit, and the second attempt would fail for a reason
that no longer resembles the first.

Retry is also scoped to the phase that is safe to replay. In a saga, that is TX-2 —
after the provider has answered. Retrying TX-1 would re-initiate; retrying across the
provider call would charge twice.

### The application layer names nothing foreign

`HexagonalArchitectureTest` collects every import in `application/` and `ports/` that is
neither JDK nor ours, and asserts the set equals `FRAMEWORK_ALLOW_LIST`. That list is
empty today.

An equality rather than a subset, so that removing the last use of an allowed type fails
too — a list that only grows stops describing anything. And an allow-list rather than a
ban on `org.springframework`, because a validation annotation or a JSON binding would
bind the layer just as firmly, and each of those arrived here before as something nobody
thought of as a framework.

This, not the disappearance of `TransactionTemplate` from the layer, is what settles
ADR-004's debt: a grep would have called it paid while `@Service` and `@Transactional`
were still there.

## Numbering of this ADR

The previous ADR is 006. This one is 007.

An earlier plan proposed 009, reserving 007 and 008 for KC-05 and KC-06, both still at
design stage with nothing in the repository. Two permanent holes in a numbered series
cost more than the reservation saves: every reader asks what is missing. KC-05 and
KC-06 take the next numbers when they are written.
