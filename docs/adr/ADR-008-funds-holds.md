# ADR-008 — Funds Holds for Debits Awaiting the Provider

**Date**: 2026-10-17
**Status**: Accepted — implemented 2026-10-17
**Authors**: Kora Core Engineering — Ivan Joël Tchatchoua Bayon
**Related**: ADR-001 — Immutable Ledger · ADR-002 — Payment Lifecycle State Machine ·
ADR-004 — Micro-Transaction Model · `docs/kora-core-state-of-view-v3.md` L-1

---

## Context

A cash-out runs in three phases (ADR-004). TX-1 reads the wallet **without a lock** and
checks that its balance covers the amount. The provider is then called and pays the
customer out, for about 1.4 s. TX-2 locks the wallet, writes the ledger entries and debits
the balance.

The check happens in TX-1 and the debit in TX-2. Between them, nothing sets the funds
aside. Two debits on the same wallet can both pass TX-1:

```
time      Cash-out A                        Cash-out B                      balance
──────────────────────────────────────────────────────────────────────────────────
t0        TX-1: 10 000 ≥ 10 000 ✓                                           10 000
t0+5ms                                      TX-1: 10 000 ≥ 10 000 ✓         10 000
t0+10ms   provider pays 10 000              provider pays 10 000
t0+1.4s   TX-2: lock, debit → 0 ✓                                           0
t0+1.4s                                     TX-2: waits, reads 0
                                            → InsufficientFundsException, rolled back
```

The provider pays twice from Kora's float. B ends `INITIALIZED` with no ledger entry and no
`AuthorizationRecord`, and its client receives a 422 although the money left. Nothing
detects it.

The TX-2 lock keeps the **ledger** right — the balance never goes negative — but it acts
after the payout. It protects the books, not the money. A cash-out racing a P2P transfer
fails the same way: the transfer is safe on its own (one transaction, under lock), but it
can drain the wallet while the cash-out waits for the provider.

**Idempotency (roadmap Step 4) does not close this.** It makes one intent run once. Here,
two distinct intents compete for the same funds, each with its own key — two devices, a
cash-out and a transfer, or someone exploiting the window on purpose.

The real-world answer is the one every wallet and card network uses: the **available
balance** is not the **ledger balance**. An outgoing operation first places a **hold** on
the funds, and the debit is posted only once confirmed.

## Decision

**A debit that waits for the provider places a hold on the customer's wallet in TX-1,
under the wallet lock. Holds live in their own table, `funds_holds`. `ledger_entries`
keeps recording only movements that were actually consumed.**

### Vocabulary

| Term | Meaning | Not to be confused with |
|---|---|---|
| `FundsHold` | An amount set aside on a customer wallet while a debit awaits the provider. Kora's side | `AuthorizationRecord`: the provider's authorization, with its reference and TTL |
| Ledger balance | `accounts.balance_amount`, the ledger's cache (ADR-001). Unchanged | |
| Available balance | Ledger balance − Σ `ACTIVE` holds on the account. Computed, never stored | |

### Model

`FundsHold` is an aggregate: `id`, `accountId`, `transactionId`, `amount` (`Amount`),
`status`, `createdAt`, `resolvedAt`, with `snapshot()` and `createFromSnapshot(...)`.

```
ACTIVE ──consume()──▶ CONSUMED    the debit was posted to the ledger
   └────release()───▶ RELEASED    the debit will not happen: a known failure
```

`CONSUMED` and `RELEASED` are final. A row is never deleted.

### The available-balance rule

- A debit or a hold is accepted only if
  **amount ≤ ledger balance − Σ `ACTIVE` holds on the account**.
- The rule is domain logic, and it belongs to `Account`.

### Responsibilities

| Object | Owns | Knows |
|---|---|---|
| `Account` | A view on a customer's funds: ledger balance (the cache), reserved amount, `available()`. `reserve`, `release`, `debitReserved`, `debit` and `credit` move amounts and enforce the rule above | Amounts only. No transaction, no hold |
| `Ledger` | Everything a transaction does to money: `initiate`, `hold`, `post`, `capture`, `release`, `reverse`. Each call writes the entries and moves the accounts together | `Transaction`, `FundsHold`, and the `Account`s it moves |
| `FundsHold` | The hold of one transaction, and its lifecycle | Its transaction, its account, its amount |

`JpaAccountRepository` loads an `Account` with its two figures: `balance_amount`, and the
sum of the account's `ACTIVE` holds as its reserved amount, read under the same lock. The
reserved amount is never stored. `BalanceCache`, which moved the account caches apart from
the entries, is removed: `Ledger.post`, `capture` and `reverse` do it.
- A float account is never held: it is unbounded (ADR-001).

### Schema

One migration, `V<yyyyMMddHHmm>__create_funds_holds.sql`:

| Column | Type | Constraint |
|---|---|---|
| `id` | `VARCHAR(255)` | primary key |
| `account_id` | `VARCHAR(255)` | `NOT NULL`, foreign key to `accounts` |
| `transaction_id` | `VARCHAR(255)` | `NOT NULL`, foreign key to `transactions` |
| `amount` | `NUMERIC(19,4)` | `NOT NULL`, `CHECK (amount > 0)` |
| `currency` | `VARCHAR(3)` | `NOT NULL` |
| `status` | `VARCHAR(32)` | `NOT NULL`, `ck_funds_holds_status` over `ACTIVE`, `CONSUMED`, `RELEASED` |
| `created_at`, `resolved_at`, `version` | | as the other tables |

- A unique partial index on `transaction_id WHERE status = 'ACTIVE'`: at most one active
  hold per transaction.
- A partial index on `account_id WHERE status = 'ACTIVE'`: the held total is read on every
  debit.
- `EnumColumnConstraintsDbTest` in `payment` asserts the constraint equals `HoldStatus`
  (review rules 18 and 19).

### Flows

| Use case | Change |
|---|---|
| Cash-out, TX-1 | **Locks the wallet** (today it reads it without a lock), reads the held total, checks the available balance, creates the transaction `INITIALIZED` **and** an `ACTIVE` hold. A concurrent debit waits on the lock, then sees the funds taken |
| Cash-out, provider call | Unchanged |
| Cash-out, TX-2 on success | Locks the wallet, marks the hold `CONSUMED`, writes the ledger entries and debits the ledger balance, **in one transaction**. The available balance does not move: the amount goes from held to debited |
| Cash-out, failure at authorization or capture | Marks the hold `RELEASED`, in the transaction that records the failure |
| P2P transfer | No hold (one transaction), but the check is against the available balance of the payer |
| Cash-in | Unchanged: a credit |
| Reversal of an `AUTHORIZED` transaction | Marks the hold `RELEASED` once the provider has released its own |
| Reversal that debits a customer wallet (a posted cash-in, the payee of a posted P2P) | Checked against the available balance, like any debit |
| `GET /payments/balance` | Returns `available` as the main figure, and the ledger balance beside it. Additive for the mobile client |

### Invariants

Recorded in `CLAUDE.md` §6:

1. On every customer account, Σ `ACTIVE` holds ≤ ledger balance: the available balance is
   never negative.
2. A hold is created in the same transaction as the available-balance check, under the
   wallet lock. Every hold creation and every wallet debit takes that lock first.
3. A hold becomes `CONSUMED` in the same transaction as the ledger entries and the balance
   debit, for the same amount.
4. A hold is released only on a known failure. **Time alone never releases it.**
5. `ledger_entries` receives consumed movements only. Holds never write to it.

## Consequences

**Closed**: L-1. The second debit is refused in TX-1, before the provider pays.

**Prepared, not closed**: L-2 and L-3. If TX-2 fails after the provider succeeded, or the
application crashes in between, the hold stays `ACTIVE`. The funds stay blocked, so there
is no double spend, and the row proves a debit was in flight. Deciding its fate — consume
or release — belongs to the L-3 reaper and to `UNKNOWN` (L-12).

**Cost**:

- Cash-outs and transfers on the **same** wallet are serialised by the wallet lock for the
  length of TX-1 (about 10 ms). Different wallets are unaffected. The float stays unlocked.
- Every debit reads the held total: one indexed `SUM` over the account's active holds.
- A hold left `ACTIVE` by a crash blocks the customer's funds until someone resolves it.
  That is the safe side of the trade, and it is why invariant 4 holds.

### Test plan

1. **Reproduce first.** An e2e test fires two concurrent cash-outs of the whole balance.
   Today it must show the orphaned `INITIALIZED` transaction and the lying 422. The provider
   stub answers instantly in tests, so the race window is too narrow to hit reliably: this
   test class needs the stub's latency switched on.
2. Unit tests for `FundsHold` and for the available-balance rule in `Ledger`.
3. Unit tests for every cash-out branch (success, authorization failure, capture failure)
   and for a transfer against an active hold.
4. An integration test for the repository and for `ck_funds_holds_status`.
5. The e2e test of step 1 passes: one payout, and the second cash-out refused with 422
   before any transaction is created.
6. An e2e test for a cash-out racing a P2P transfer — the case idempotency cannot cover.

## Accepted Architectural Debt

- **Stuck holds need L-3.** Until the reaper exists, an `ACTIVE` hold whose transaction
  never reached TX-2 is resolved by hand.
- **No partial capture.** A hold is consumed whole, for the transaction's amount.
- **No operator override.** An operator cannot debit past the holds. If that need appears,
  it is a separate decision.

## Alternatives Considered

**A reserved amount on the account** (`accounts.reserved_amount`). The smallest change: one
column, three methods on `Account`. Rejected: the reservation is a number with no history.
Nothing says which transaction holds it, so a crash leaves funds blocked that cannot be
attributed, and it is a second cache beside `balance_amount` that nothing checks (L-7).

**A suspense account in the ledger** (wallet → suspense in TX-1, suspense → float in
TX-2). Everything stays in the ledger. Rejected: it writes entries for money that has not
moved, so `ledger_entries` would no longer record consumed movements only, and every
failure would need compensating entries for a movement that never happened.

**Two-phase ledger entries** (pending, then posted or voided — the model of TigerBeetle and
of modern ledger services). Auditable without a suspense account. Rejected for now: it
changes the meaning of `LedgerEntry`, the balance cache and the double-entry check — the
core of ADR-001 — to solve what a separate table solves without touching them.

**Idempotency alone** (roadmap Step 4). Rejected as a fix for L-1: it makes one intent run
once, and L-1 is two distinct intents. It remains planned for its own purpose.
