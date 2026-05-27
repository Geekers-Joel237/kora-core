# ADR-001 — Immutable Double-Entry Ledger

**Date**: 2026-03-14  
**Status**: Accepted  
**Authors**: Kora Core Engineering — Ivan Joël Tchatchoua Bayon  
**Supersedes**: None  
**Related**: Step 0 — Transactional Monolith

---

## Context

Kora Core is a production-grade digital wallet engine for an African neobank.
Every financial movement — cash-in, cash-out, P2P transfer — must be auditable,
reconstructible after incident, and compliant with BCEAO/CEMAC traceability
requirements for electronic money operations.

The foundational question at Step 0: **how should account balances be stored?**

Two approaches exist in the industry:

1. Store a running balance in the `accounts` table and mutate it on every operation:
   `UPDATE accounts SET balance = balance + delta WHERE id = ?`

2. Record every movement as an immutable entry in a double-entry ledger, and derive
   the balance from those entries.

---

## Decision

**We use an immutable double-entry ledger. No financial operation directly updates
`balance_amount` via a ledger entry mutation.**

The authoritative balance for any account is always the algebraic sum of its
`Operation` entries in the ledger.

### Core Invariant

For any coherent set of operations, across any time window:
```
SUM(DEBIT operations) == SUM(CREDIT operations)
```

Every transaction generates at minimum two mirror entries. A 10,000 XOF cash-in produces:
```
CREDIT  10,000 XOF  → customer account
DEBIT   10,000 XOF  → system float account
────────────────────────────────────────
Net ledger impact   = 0
```

This invariant is enforced in code at `Ledger.verifyDoubleEntry()` and validated
at the DB level by `FinancialInvariantsDbTest` and `MoneyIntegrityE2ETest` after
every scenario.

### Denormalized Balance Cache on `AccountEntity`

Reconstructing a balance from operations on every read is O(n) over the account's
lifetime — unacceptable for synchronous APIs.

We maintain a denormalized `balance_amount` field on `AccountEntity` as a
**read-optimized cache only**. It is updated in memory on the `Account` aggregate
via `credit()` / `debit()` and persisted in the same transaction as the ledger write.

**This field is a read optimization. It is never the source of truth.**

In case of divergence — detected by audit or incident — the `Operation` entries
always win. The reconstruction procedure is documented in the *Incident Recovery*
section below.

### Compensating Reverse on Provider Failure

If an external provider call fails after ledger entries have been created, we do
**not** roll back or delete those entries. We create a compensating `reverse`
transaction that generates four mirror operations cancelling the original two:
```
Original transaction (FAILED):
  DEBIT  float account  +  CREDIT  customer account   → net = 0

Compensating reverse:
  DEBIT  customer account  +  CREDIT  float account   → net = 0

─────────────────────────────────────────────────────
Global ledger across both transactions               → net = 0 ✓
```

This design means a FAILED transaction always carries 4 operations, not 2.
The double-entry invariant holds across the full lifecycle of every transaction,
including failures.

**Distinction from Step 1+ business reversals:**
The current reverse is a *technical compensation* triggered by provider failure
at Step 0. Business reversals (chargebacks, merchant refunds) will be modeled
as distinct transaction types in Step 1 with their own lifecycle states
(`REVERSAL_REQUESTED` → `REVERSAL_COMPLETED`).

### Float Account — Intentional Design

The `FLOAT_ACCOUNT` represents the system treasury. It is considered unbounded:
it can always honour a debit regardless of its nominal balance.

`Account.debit()` is a **no-op** for float accounts: it returns the current balance
unchanged and never throws `InsufficientFundsException`.

**Consequence**: `balance_amount` on the float account row is always 0 in the database,
even after thousands of cash-in operations.

**Float account auditing must go through `Operation` entries in the ledger**, not
through `balance_amount`. This is not a bug — it is the intended design, documented
in `Account.debit()` Javadoc and enforced by `AccountTest.should_not_track_balance_for_float_account_debit()`.

---

## Architecture Boundaries

### What enforces the invariant

| Layer | Mechanism |
|---|---|
| Domain | `Ledger.verifyDoubleEntry()` — throws `IllegalStateException` on any violation before persisting |
| Application | `PaymentService.executePayment()` — all state transitions go through a single method |
| Infrastructure | `@Transactional` — ledger writes and balance cache updates are atomic |
| Tests (unit) | `PaymentUseCaseTest.assertDoubleEntryInvariant()` — checks SUM(DEBIT)==SUM(CREDIT) after every scenario |
| Tests (DB) | `FinancialInvariantsDbTest` — verifies invariant via native SQL after flush |
| Tests (E2E) | `MoneyIntegrityE2ETest` — verifies invariant via JDBC against real PostgreSQL |

### Transaction state machine

All transactions follow a strict state machine enforced by the State pattern:
```
INITIALIZED → PENDING → COMPLETED
                      → FAILED
```

No other transitions are permitted. `InvalidStateTransitionException` is thrown
on any illegal transition. Terminal states (`COMPLETED`, `FAILED`) are immutable.

Every state transition produces an immutable `TrxStateHistoric` entry, providing
a full audit trail of *who changed what, and when*, for every transaction.

### Concurrency — Known Limitation (Technical Debt)

The current implementation reads an account balance, applies a delta in memory,
and writes back the result:
```java
var customerAccount = accountRepository.findByCustomerId(customerId);  // READ
customerAccount.credit(amount);                                         // MUTATE
accountRepository.save(customerAccount);                                // WRITE
```

This is a **lost update problem** under concurrent requests targeting the same
account. Two threads reading the same balance (0 XOF) and both crediting 1,000 XOF
may produce a final balance of 1,000 XOF instead of 2,000 XOF.

**Why this is accepted at Step 0:**
- Volume target: 5,000 tx/day → ~0.06 tx/sec nominal
- Architecture: single JVM instance, no horizontal scaling
- Probability of collision on the same account is statistically negligible
- The double-entry ledger remains correct regardless — `Operation` entries
  are written atomically and the invariant holds even if the denormalized
  `balance_amount` diverges temporarily

**This must be resolved at Step 1** before moving to 15,000 tx/day with concurrent
load. Resolution options in order of complexity:

| Option | Mechanism | Suitable up to |
|---|---|---|
| A — Optimistic Locking | `@Version` on `AccountEntity`, retry on `OptimisticLockException` | ~50 req/s per account |
| B — Pessimistic Locking | `SELECT FOR UPDATE` on account row | High-contention accounts (float) |
| C — Event-sourced balance | Remove `balance_amount` entirely; compute from `Operation` SUM | Unlimited, ledger-native |

Option C is architecturally aligned with the ledger model and will be the
natural evolution once the `Operation` table has proper aggregate indexes.

---

## Consequences

### Positive

- **Full auditability**: every cent is traced to a specific transaction and operation.
- **Reconstructibility**: after any incident (denormalized cache corruption,
  partial rollback), the real balance is recomputed from `Operation` entries.
  No financial data is ever lost.
- **Correctness enforced structurally**: `Ledger.verifyDoubleEntry()` makes it
  impossible to persist an unbalanced state, regardless of what the application layer does.
- **Non-repudiation**: ledger entries are immutable at the application level
  (`INSERT` only, no `UPDATE`, no `DELETE`). Combined with `@SoftDelete` on
  `BaseEntity`, even soft-deleted records remain auditable.
- **Regulatory compliance**: satisfies BCEAO/CEMAC traceability requirements
  for electronic money operations in the CEMAC zone.
- **Test coverage**: financial invariants are validated at every layer —
  unit, JPA/Testcontainers, E2E — with real SQL assertions against PostgreSQL.

### Negative / Trade-offs

- **Two sources of truth**: ledger (authoritative) + denormalized balance (cache).
  A bug in the update path creates silent divergence detectable only at audit time,
  not at read time.
- **Write amplification**: every transaction writes N `Operation` rows, N `TrxStateHistoric`
  rows, 1 `Transaction` row, and 1-2 `Account` rows. At high volume (>10k TPS),
  this becomes a bottleneck — addressed in Step 5+ with async writes via Outbox pattern.
- **Reconstruction cost**: recomputing balance from operations for a long-lived account
  is O(n) without aggregation indexes. Mitigated at Step 0 by the denormalized cache.
  Snapshot support (periodic balance snapshots) is planned for Step 6.
- **Concurrency gap**: as documented above, the lost update problem is a known and
  accepted technical debt for Step 0 only.

---

## Alternatives Considered

### A — Direct balance mutation
```sql
UPDATE accounts SET balance_amount = balance_amount + :delta WHERE id = :id
```

**Rejected**: no history, impossible to audit or reconstruct after incident.
Race condition without explicit `SELECT FOR UPDATE`. Does not satisfy BCEAO/CEMAC
traceability requirements.

### B — Pure event sourcing

Store domain events (`MoneyDeposited`, `MoneyWithdrawn`) and reconstruct balance
at read time by replaying the event stream.

**Rejected for Step 0**: disproportionate operational complexity (event store,
snapshots, projections, eventual consistency) for a nascent system. Targeted as
a future evolution for the extracted Ledger microservice in Step 7.

### C — Ledger without denormalized cache

Remove `balance_amount` from `AccountEntity` entirely. Always compute from `Operation` SUM.

**Rejected**: every balance read becomes an aggregation query over the account's
full history — unacceptable latency for synchronous payment APIs.
Will be revisited in Step 9 with Redis balance cache as an optional L1 cache layer.

---

## Incident Recovery Procedure

If `balance_amount` is found to have diverged from the ledger (detected by audit
or monitoring):
```sql
-- Step 1: Compute authoritative balance from ledger
SELECT
    SUM(CASE WHEN o.type = 'CREDIT' THEN o.amount ELSE -o.amount END) AS authoritative_balance
FROM operations o
WHERE o.account_id = :account_id
  AND o.deleted_at IS NULL;

-- Step 2: Apply correction
-- IMPORTANT: only permitted via direct DB access by an authorized operator,
-- never through the application layer.
UPDATE accounts
SET balance_amount = :authoritative_balance,
    updated_at     = NOW()
WHERE id = :account_id
  AND deleted_at IS NULL;
```

This procedure is the **only circumstance** in which a direct `UPDATE` on
`balance_amount` is authorized. It must be logged, reviewed, and linked to an
incident report.

---

## Performance Baseline (Step 0)

Validated by the k6 load test suite (`perf/load.js`):

| Metric | Target | Status |
|---|---|---|
| Throughput | 10 req/s plateau | ✓ Validated |
| p95 latency (payment endpoints) | < 150ms | ⚠ Pending (OTP auth overhead under concurrent load) |
| Error rate | < 1% | ⚠ Pending (OTP timeout under 30 VU concurrency) |
| DB pool | hikaricp_pending = 0 | ✓ Validated (smoke test) |

The payment endpoints themselves perform at ~15ms median (measured during stress
test setup phase). The p95 SLO breach in the load test is caused by concurrent
OTP authentication at ramp-up, not by the ledger or payment logic.
This is a test infrastructure concern, not an architecture concern.

**Step 1 SLO target**: p95 < 200ms at 20-30 req/s with optimistic locking
and connection pool tuning.

---

## What Will Change at Step 1

| Concern | Step 0 approach | Step 1 target |
|---|---|---|
| Concurrency | Lost update (accepted) | `@Version` optimistic locking on `AccountEntity` |
| Float account | No balance tracking | `SELECT FOR UPDATE` on float operations |
| Reverse semantics | Technical compensation only | Business reversal type + lifecycle states |
| Payment lifecycle | INITIALIZED → PENDING → COMPLETED/FAILED | Add AUTHORIZED, CAPTURED, SETTLED, REVERSAL_* |
| Provider integration | Stub (always succeeds) | Real async HTTP client with circuit breaker |

---

*Living document. Reviewed every Sunday. Updated at each architecture milestone.*  
*github.com/Geekers-Joel237 · linkedin.com/in/geekers-joel237 · geekersjoel237.substack.com*