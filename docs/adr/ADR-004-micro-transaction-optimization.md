# ADR-004 — Micro-Transaction Model for Provider-Bound Operations

**Date**: 2026-05-24
**Status**: Accepted
**Authors**: Kora Core Engineering — Ivan Joël Tchatchoua Bayon
**Related**: ADR-001 — Immutable Double-Entry Ledger · ADR-002 — Payment Lifecycle State Machine · ADR-003 — Single-Call Payment API Design

---

## Context

### Load Test Observations

During Step 1 load testing (k6, 25 req/s sustained, business mix: cashIn 40 % / transfer 35 % / cashOut 15 % / balance 10 %, HikariCP pool-size 30), the application failed all SLO thresholds:

| Metric | Target | Observed |
|---|---|---|
| p95 latency | < 150 ms | 60 000 ms |
| p99 latency | < 300 ms | — |
| Error rate | < 1 % | 73.93 % |
| Checks passed | 100 % | 18.42 % |

Application logs showed `HikariPool-1 - Connection is not available, request timed out after 30000ms` under sustained load, confirming connection pool exhaustion well before the 30-connection ceiling.

### Root Cause Analysis

Two compounding problems were identified:

**Root Cause 1 — Monolithic `@Transactional` holding DB connections during provider I/O**

`PaymentTransactionalExecutor` was annotated `@Transactional` at class level. Each cash-in or cash-out request held a live DB connection for the full duration of the call, including the simulated provider round-trip (authorize ≈ 800 ms + jitter, capture ≈ 600 ms + jitter → median 1 400 ms, max ≈ 1 960 ms). At 25 req/s with ~50 % provider-bound operations, Little's Law gives a required concurrency of 25 × 0.5 × 1.4 ≈ 17.5 simultaneous open connections just for provider I/O — leaving almost nothing for transfers and reads. Any burst immediately exhausted the pool.

**Root Cause 2 — Float account pessimistic lock serializing all CASH_IN/CASH_OUT**

`accountRepository.findFloatByProviderIdForUpdate()` issued a `SELECT … FOR UPDATE` on the float account row, which was then held for the full 1 400 ms provider I/O. Because all cash-in and cash-out operations share a single float account, this lock created a global serialization point, capping throughput at approximately 1 / 1.4 s ≈ 0.71 provider-bound operations per second regardless of pool size or thread count.

These two problems are independent but additive. Solving only one would not recover the SLOs.

---

## Decision

### Micro-Transaction Split

Replace the single monolithic transaction with an explicit sequence of short, bounded transactions separated by provider I/O:

```
TX-1 (~10 ms)          Provider I/O (~1 400 ms)    TX-2 (~20 ms)
─────────────          ─────────────────────────    ─────────────
validate payer         authorize(provider)          reload account
check balance          capture(provider)            apply balance
persist INITIALIZED    (zero DB connections)        persist COMPLETED
release connection ─────────────────────────────── acquire connection
```

**TX-1** validates the payer (pin, suspension, sufficient funds via `Ledger.initiate()` domain invariant) and persists the transaction in `INITIALIZED` state. The DB connection is released immediately after.

**Provider I/O** executes with zero open DB connections. Thread is blocked on network, not on DB.

**TX-2** acquires a fresh connection, reloads the customer account with a pessimistic lock (`SELECT … FOR UPDATE`), applies the balance delta, records ledger entries, and persists the final `COMPLETED` state along with all 5 intermediate history entries (AUTHORIZED → CAPTURED → PENDING_SETTLEMENT → SETTLED → COMPLETED).

**Transfers** (`executeTransfer`) do not involve provider I/O and remain a single transaction — their lock is on the sender's customer account only, acquired for ~20 ms.

### Float Account Lock Elimination

The float account pessimistic lock is entirely removed. This is sound because, per ADR-001, the float account balance is derived from immutable ledger entries and never stored as a mutable field. `Account.debit()` and `Account.credit()` are no-ops for `FLOAT_ACCOUNT` type. There is no shared mutable state to protect, so no lock is needed.

### Implementation: `TransactionTemplate` + `PlatformTransactionManager`

Explicit transaction boundaries are implemented using `TransactionTemplate` constructed from an injected `PlatformTransactionManager`. The executor receives `PlatformTransactionManager` via constructor injection and wraps each TX-1 / TX-2 boundary with `txTemplate.execute(status -> { … })`.

This is a pragmatic choice for Step 1. The architectural implication is discussed under [Accepted Architectural Debt](#accepted-architectural-debt) below.

### Audit Trail Preservation

The domain `Transaction` accumulates all state transitions in an internal list (`history()`). TX-1 persists only the first entry (index 0: `INITIALIZED`). TX-2 flushes all subsequent entries in a single pass via `flushHistorySince(tx, 1)`. The 6-entry audit trail tested by unit tests is fully preserved.

---

## Consequences

### Expected Gains

With this model, DB connections are held for ~10 ms (TX-1) and ~20 ms (TX-2) instead of ~1 400 ms. At 25 req/s and 50 % provider-bound operations:

- Required concurrent connections drops from ~18 to < 2 (for provider-bound ops).
- Float account serialization is eliminated entirely.
- HikariCP pool-30 becomes a comfortable ceiling, not a bottleneck.

The theoretical max throughput for cash-in/cash-out is now bounded by provider latency and thread pool depth, not by DB connection count.

### Known Gaps (Accepted for Step 1)

The following failure scenarios exist and are consciously accepted at this stage of the roadmap:

| Gap | Scenario | Consequence | Mitigation (Step 1) | Planned Remedy |
|---|---|---|---|---|
| **G-1: Process crash between TX-1 and TX-2** | JVM or container dies after TX-1 commits but before provider I/O completes | Transaction stays `INITIALIZED` forever | Manual reconciliation; `INITIALIZED` rows with age > N minutes are detectable | Step 3: scheduled reaper + idempotency store |
| **G-2: Provider success + TX-2 failure** | Provider authorizes and captures money but TX-2 fails (DB error, timeout, OOM) | Customer charged but balance not credited | `AuthorizationRecord` persisted in TX-2 allows manual detection | Step 3: `PENDING_RECOVERY` state + reaper |
| **G-3: No TX-2 retry** | TX-2 throws after provider success | Same as G-2 | Rely on `AuthorizationRecord` for audit | Step 3: retry with idempotency key |
| **G-4: Intermediate states not individually persisted** | TX-2 writes INITIALIZED → COMPLETED history in one flush | If TX-2 partially fails mid-flush, partial history saved | Acceptable for audit; `COMPLETED` not written until last | Step 3: transactional outbox |
| **G-5: No distributed idempotency store** | Client retries cash-in after network timeout during TX-1 | Duplicate `INITIALIZED` transactions | Not guaranteed unique at application level | Step 3: idempotency key table with unique constraint |

These gaps are not regressions — they existed in the monolithic transaction model as well (process crash mid-`@Transactional` has the same consequences). The micro-transaction model makes them explicit and auditable via `AuthorizationRecord` and the `INITIALIZED` aging pattern.

---

## Accepted Architectural Debt

### `TransactionTemplate` in the Application Layer

`PaymentTransactionalExecutor` currently imports and uses `org.springframework.transaction.support.TransactionTemplate` — a Spring infrastructure type — directly in the application layer. Per hexagonal architecture principles, the application layer should depend only on domain types and port interfaces, not on framework classes.

**Option C (deferred)**: Define a `@FunctionalInterface TransactionBoundary` port in the application layer:

```java
// application/port/out/TransactionBoundary.java
@FunctionalInterface
public interface TransactionBoundary {
    <T> T execute(Supplier<T> work);
}
```

Infrastructure provides a `SpringTransactionBoundary` implementing this interface with `TransactionTemplate`. Unit tests use `work -> work.get()`. This makes the application layer fully framework-free.

**Why deferred**: The current approach is functionally correct, all tests pass, and the performance problem is solved. Introducing the port abstraction in the same step would increase scope without changing observable behavior. Option C is tracked as a Step 2 refactor target.

---

## Alternatives Considered

| Option | Description | Rejected Because |
|---|---|---|
| **Increase HikariCP pool size** | Raise `maximum-pool-size` to 60–100 | DB server connection limit; float lock bottleneck remains; treats symptom not cause |
| **Async provider calls** | Non-blocking I/O with WebClient | Requires reactive stack or virtual threads; out of scope for Step 1 monolith |
| **Two injected `TransactionTemplate` beans** | Read vs. write templates injected via `@Qualifier` | Externalizes configuration but does NOT remove the infrastructure violation in the application layer; same hexagonal debt as current approach |
| **Separate `@Transactional` adapter** (Option D) | Move executor to infrastructure, call domain service | Inverts the dependency; application layer loses orchestration control; harder to test in isolation |