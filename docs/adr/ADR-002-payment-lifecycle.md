# ADR-002 — Payment Lifecycle & Real-World Transaction States

**Date**: 2026-03-18
**Status**: Accepted
**Authors**: Kora Core Engineering — Ivan Joël Tchatchoua Bayon
**Supersedes**: Partial supersession of Step 0 state machine in ADR-001
**Related**: Step 1 — Payment Lifecycle · ADR-001 — Immutable Double-Entry Ledger

---

## Context

At Step 0, KORA Core implemented a minimal transaction state machine:

```
INITIALIZED → PENDING → COMPLETED
                      → FAILED
```

This model was sufficient for a wallet processing 5,000 transactions per day
on a single JVM instance with stub provider integration.

Step 1 targets 15,000 transactions per day at 20–30 req/sec with real provider
integration and J+1 settlement delays. At this scale and with this operational
reality, the Step 0 state machine fails to model what actually happens between
the moment a user initiates a payment and the moment funds definitively settle.

**The core problem:** in a real payment system, a transaction is not a single
atomic event. It is a lifecycle — a sequence of distinct financial states, each
with its own business meaning, its own ledger implications, and its own failure
mode.

Without this granularity, three operational questions become unanswerable:

1. *"The payment shows successful but the merchant says he received nothing."*
   → Impossible to distinguish CAPTURED (funds debited) from SETTLED
   (funds received by merchant) without explicit states.

2. *"How much outstanding float is KORA carrying right now?"*
   → Impossible to compute treasury exposure without knowing which transactions
   are settled vs. pending settlement.

3. *"Show me the audit trail for this transaction from initiation to settlement."*
   → A two-state machine (PENDING → COMPLETED) cannot satisfy BCEAO/CEMAC
   traceability requirements for electronic money operations.

---

## Decision

**We extend the payment state machine to model the full lifecycle of a real
payment transaction, from user intent to final settlement.**

The new state machine is the authoritative model for all transaction types
in KORA Core from Step 1 onward.

---

## The State Machine

### Happy Path

```
INITIATED
    │
    │  [idempotency check, risk check, balance check]
    ▼
AUTHORIZED
    │
    │  [provider confirms fund reservation]
    ▼
CAPTURED
    │
    │  [provider confirms effective debit, ledger entries committed]
    ▼
SETTLEMENT_PENDING
    │
    │  [awaiting interbank settlement batch — typically T+1]
    ▼
SETTLED
    │
    │  [provider settlement report received, reconciliation passed]
    ▼
COMPLETED
```

### Failure Branches

```
INITIATED         → FAILED                  funds never reserved
AUTHORIZED        → AUTHORIZATION_FAILED    provider refused reservation
AUTHORIZED        → REVERSED               business reversal before capture
CAPTURED          → CAPTURE_FAILED          provider confirmed auth, failed debit
CAPTURED          → REVERSED               business reversal post-capture
SETTLEMENT_PENDING→ SETTLEMENT_FAILED       settlement report mismatch
SETTLED           → REFUNDED               post-settlement refund (Step 6+)
```

### Terminal States

`COMPLETED`, `FAILED`, `AUTHORIZATION_FAILED`, `CAPTURE_FAILED`,
`SETTLEMENT_FAILED`, `REVERSED`, `REFUNDED`

No further transitions are permitted from any terminal state.
`InvalidStateTransitionException` is thrown on any illegal transition attempt.

---

## State Definitions — Business Meaning

### INITIATED

The user's payment intent has been recorded. No money has moved.

The system has validated:
- Transaction amount > 0
- Source account has sufficient available balance
- Idempotency key is unique for this user/session
- Daily and hourly velocity limits are not exceeded
- Risk engine has not flagged the operation for hard stop

**Ledger impact:** none. No entries are written until AUTHORIZED.

**What this means for support:**
A transaction stuck in INITIATED indicates a pre-provider validation failure.
No money left the user's account. No refund is necessary.

---

### AUTHORIZED

The payment provider has verified the source account and reserved the funds.
The money has not moved, but it is no longer available for other operations.

```
balance_real      = unchanged    (money still on account)
balance_available = reduced      (funds locked for this transaction)
```

The authorization carries a TTL — typically 15 to 30 minutes depending on the
provider. If CAPTURED does not occur before TTL expiry, the authorization is
automatically invalidated and the reserved funds are released.

**Ledger impact:** none yet. Entries are written at CAPTURED, not at AUTHORIZED.
The reservation is tracked in `AuthorizationRecord`, not in the ledger.

**What this means for support:**
A transaction stuck in AUTHORIZED means the provider accepted the reservation
but the capture has not yet been triggered — or failed silently. The user's
real balance is unaffected but their available balance is reduced.

---

### CAPTURED

The provider has executed the effective debit. The money has left the source
account. This is the moment of financial commitment.

**Ledger impact:** ledger entries are written at this state transition, not before.
Double-entry invariant is enforced before commit:

```
DEBIT   source account    amount XAF
CREDIT  float account     amount XAF
────────────────────────────────────
Net ledger impact         = 0 ✓
```

`balance_real` on the source account is updated. The denormalized cache is
written atomically with the ledger entries in the same `@Transactional` boundary.

**What this means for support:**
A transaction stuck in CAPTURED means the user has been debited but the
settlement to the destination has not yet completed. This is normal during
the settlement window. It is not a loss — the funds are being carried by KORA
pending interbank settlement.

**Distinction from Step 0 compensating reverse:**
At Step 0, a provider failure after ledger entries were created triggered a
technical compensating reverse — 4 operations bringing the net to zero.
At Step 1, CAPTURED means the provider confirmed the debit successfully.
A CAPTURE_FAILED state (provider confirmed auth but failed the debit) does
not generate ledger entries — there is nothing to reverse.

---

### SETTLEMENT_PENDING

The capture has completed on KORA's side but the interbank settlement has not
yet been confirmed. KORA is carrying the settlement risk during this window.

In African mobile money infrastructure, providers typically run one settlement
batch per day, processed overnight. All transactions captured during the day
move to SETTLED the following morning when the batch report is received.

**Treasury implication:**
The sum of all transactions in SETTLEMENT_PENDING represents KORA's current
float exposure — the amount of capital that has been disbursed but not yet
recovered from the interbank settlement. Monitoring this value is a core
operational requirement.

**Ledger impact:** none at this transition. The ledger is already balanced from
CAPTURED. Settlement affects the accounting between KORA and its banking partners,
not the user's account balance.

---

### SETTLED

The provider's settlement report has been received and the reconciliation engine
has matched this transaction against the report.

```
KORA internal ledger  ←→  provider settlement report
         MATCHED ✓
```

The destination (merchant, recipient wallet) has confirmed receipt.
From a financial perspective, the transaction is definitively closed.

**What happens if the settlement report does not match:**
The reconciliation engine marks the transaction as `SETTLEMENT_FAILED` and
routes it to the manual review queue. This is addressed in Step 6 — the
automated reconciliation engine. At Step 1, `SETTLEMENT_FAILED` is modeled
as a terminal error state requiring manual intervention.

---

### COMPLETED

Administrative closure of a SETTLED transaction. All parties have confirmed,
all accounting entries are final, all audit records are immutable.

This state exists to distinguish *"financially settled"* from *"operationally
closed"* — relevant when KORA introduces post-settlement workflows such as
dispute windows, chargeback deadlines, or reporting aggregation.

---

## Business Reversal vs. Technical Compensation — Critical Distinction

ADR-001 introduced the *compensating reverse* — a technical mechanism triggered
automatically when a provider call fails after ledger entries have been created.

Step 1 introduces *business reversals* — explicit financial operations triggered
by human intent: a merchant requesting a refund, a support agent correcting an
error, a user cancelling a pending payment.

These are fundamentally different and must never be conflated:

| | Technical Compensation (ADR-001) | Business Reversal (ADR-002) |
|---|---|---|
| **Trigger** | Provider failure — automatic | Human intent — explicit request |
| **Timing** | Immediately after failure detection | Anytime before terminal state |
| **Ledger** | 4 mirror operations, net = 0 | New REVERSAL transaction, own ledger entries |
| **Visibility** | Internal, not user-facing | User-facing, appears in transaction history |
| **State** | Original transaction → FAILED | Original transaction → REVERSED |
| **Audit** | Technical recovery record | Business event record |

**Implementation rule:**
A `REVERSED` state is only reachable via an explicit `ReversePaymentCommand`.
It is never set automatically by the system. The compensating reverse at ADR-001
still applies to its original scope (provider failure before state progression).

---

## Transition Guards

Every state transition is conditional. A guard must evaluate to `true` for the
transition to be permitted. Guard failure throws a typed domain exception, never
a silent no-op.

### INITIATED → AUTHORIZED

```
✓ idempotency key is unique for (userId, operationType, correlationId)
✓ amount > 0 and amount ≤ regulatory limit (500,000 XAF per transaction)
✓ source account available balance ≥ amount
✓ daily velocity limit not exceeded
✓ hourly velocity limit not exceeded
✓ risk engine decision = APPROVE or REVIEW (not BLOCK)
```

### AUTHORIZED → CAPTURED

```
✓ authorization TTL not expired
✓ authorization has not been previously cancelled
✓ no concurrent REVERSAL request in progress for this transaction
✓ provider capture call returned success confirmation
```

### CAPTURED → SETTLEMENT_PENDING

```
✓ ledger entries for this transaction exist and double-entry invariant holds
✓ provider capture reference is recorded on the transaction
✓ destination account or merchant reference is confirmed
```

### SETTLEMENT_PENDING → SETTLED

```
✓ settlement report received from provider
✓ transaction reference found in settlement report
✓ settlement amount in report == captured amount (within tolerance)
✓ reconciliation engine has marked transaction as MATCHED
```

### AUTHORIZED → REVERSED (pre-capture reversal)

```
✓ explicit ReversePaymentCommand received with valid authorization
✓ authorization TTL not yet expired
✓ no capture attempt in progress
✓ reversing actor has required permission (OPERATOR or ADMIN role)
```

### CAPTURED → REVERSED (post-capture reversal)

```
✓ explicit ReversePaymentCommand received with valid authorization
✓ transaction has not yet entered SETTLEMENT_PENDING
✓ provider reversal call returned success confirmation
✓ compensating ledger entries created and invariant verified
✓ reversing actor has required permission
```

---

## Ledger Implications by State

The double-entry invariant from ADR-001 is extended to cover all new states.

| Transition | Ledger entries | Invariant check |
|---|---|---|
| Any → AUTHORIZED | None | N/A |
| AUTHORIZED → CAPTURED | 2 entries (DEBIT source, CREDIT float) | Required before commit |
| AUTHORIZED → REVERSED | None (no funds moved) | N/A |
| CAPTURED → REVERSED | 2 entries (DEBIT float, CREDIT source) | Required before commit |
| SETTLEMENT_PENDING → SETTLED | None (interbank settlement) | N/A |
| SETTLED → REFUNDED | New REFUND transaction, own entries | Required before commit |

**Global invariant across all transaction states:**

```
At any point in time, for any account:

SUM(CREDIT operations across all CAPTURED+ transactions)
  - SUM(DEBIT operations across all CAPTURED+ transactions)
= current authoritative balance

This invariant must hold regardless of how many transactions
are in SETTLEMENT_PENDING, SETTLED, or REVERSED states.
```

---

## Concurrency — Resolution of ADR-001 Technical Debt

ADR-001 documented the lost update problem as accepted technical debt for Step 0.

**Step 1 resolves this debt.**

At 15,000 tx/day with 20–30 req/sec, concurrent requests targeting the same
account are no longer statistically negligible. Two concurrent CAPTURE operations
on accounts with shared balance references can produce incorrect `balance_amount`
cache values.

### Decision: Optimistic Locking on AccountEntity

```java
@Entity
public class AccountEntity extends BaseEntity {

    @Version
    private Long version;   // ← resolves ADR-001 concurrency gap

    private BigDecimal balanceAmount;
    // ...
}
```

On concurrent update collision, Spring Data throws `OptimisticLockException`.
The application layer catches this exception and retries the operation with
exponential backoff (max 3 attempts before surfacing as a transient error).

**Why optimistic over pessimistic locking:**
At 20–30 req/sec, contention on any individual account is low. Optimistic locking
avoids the performance overhead of `SELECT FOR UPDATE` row locks on the common
path. Pessimistic locking is reserved for the float account, which is a shared
resource with guaranteed high contention.

**Float account — pessimistic locking:**

```java
// Float account operations use SELECT FOR UPDATE
// to prevent concurrent debits from multiple transactions
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<AccountEntity> findByAccountTypeForUpdate(AccountType type);
```

**Suitability boundary:**
Optimistic locking on `AccountEntity` is suitable up to ~50 req/sec per account.
Beyond this, the retry rate becomes a latency problem. Resolution at Step 3+:
event-sourced balance computation removing `balance_amount` entirely.

---

## AuthorizationRecord — New Domain Entity

Step 1 introduces `AuthorizationRecord` to track the reservation state between
AUTHORIZED and CAPTURED.

```
AuthorizationRecord {
    id                  UUID
    transactionId       UUID (FK → Transaction)
    providerReference   String
    authorizedAmount    BigDecimal
    currency            String
    authorizedAt        Instant
    expiresAt           Instant          // authorizedAt + provider TTL
    status              ACTIVE | EXPIRED | CONSUMED | CANCELLED
    capturedAt          Instant?
    reversedAt          Instant?
}
```

**TTL enforcement:**
A scheduled job runs every minute and scans for `AuthorizationRecord` entries
where `expiresAt < NOW()` and `status = ACTIVE`. Expired records are marked
`EXPIRED` and the parent transaction transitions to `AUTHORIZATION_FAILED`.

This prevents authorization leaks — transactions stuck in AUTHORIZED indefinitely
blocking the user's available balance.

---

## Audit Trail — Enhanced TrxStateHistoric

`TrxStateHistoric` from Step 0 is enriched with Step 1 fields:

```
TrxStateHistoric {
    id                  UUID
    transactionId       UUID
    previousState       TransactionStatus
    newState            TransactionStatus
    occurredAt          Instant
    triggeredBy         TriggerSource      // USER_ACTION | PROVIDER_CALLBACK |
                                           // SYSTEM_JOB | OPERATOR_ACTION
    correlationId       String             // end-to-end request correlation
    providerReference   String?            // provider's own reference for this event
    actorId             String?            // userId or systemComponent
    notes               String?            // mandatory for OPERATOR_ACTION triggers
}
```

Every state transition — including automated ones triggered by TTL expiry or
settlement batch processing — produces an immutable `TrxStateHistoric` entry.

---

## What Step 1 Does NOT Implement

Two concerns are explicitly deferred to maintain a clean scope boundary.

**Automated settlement batch processing (Step 6):**
Step 1 models `SETTLEMENT_PENDING` and `SETTLED` as states, and implements
the manual transition for testing. The automated reconciliation engine that
ingests provider CSV reports and drives batch settlement is Step 6.

At Step 1, the `SETTLEMENT_PENDING → SETTLED` transition is triggered via an
internal command, not by a real provider report. This is sufficient to validate
the state machine and the ledger behavior.

**Chargeback and dispute workflow (Step 6+):**
`REFUNDED` is modeled as a terminal state from `SETTLED`. The full chargeback
workflow — dispute intake, provider notification, evidence collection, resolution
timeline — is out of scope for Step 1.

---

## Consequences

### Positive

- **Full operational visibility**: support can precisely locate any transaction
  in its lifecycle and give users accurate, meaningful status information.
- **Treasury monitoring foundation**: `SETTLEMENT_PENDING` transactions represent
  quantifiable float exposure, enabling real-time treasury reporting.
- **Regulatory compliance**: the extended state machine satisfies BCEAO/CEMAC
  traceability requirements for the full lifecycle of electronic money operations.
- **Clean reversal semantics**: technical compensation (ADR-001) and business
  reversal (ADR-002) are distinct, non-conflatable operations.
- **Concurrency debt resolved**: `@Version` optimistic locking eliminates the
  lost update problem documented in ADR-001 before scaling to Step 1 volume.

### Negative / Trade-offs

- **Increased state space complexity**: 11 states vs. 4 at Step 0. Each new
  state requires test coverage for every legal and illegal transition.
- **Authorization TTL management overhead**: the expiry job adds operational
  complexity — it must be reliable, monitored, and idempotent.
- **Settlement gap**: `SETTLEMENT_PENDING → SETTLED` is manually triggered at
  Step 1. Real automated settlement requires the reconciliation engine at Step 6.
  This means Step 1 does not yet close the full financial loop automatically.

---

## Alternatives Considered

### A — Keep Step 0 state machine, add metadata fields

Add `authorizedAt`, `capturedAt`, `settledAt` timestamp fields to the
`Transaction` entity without introducing new states.

**Rejected**: timestamps without state transitions cannot be used to enforce
business rules, drive guard conditions, or trigger TTL expiry. State transitions
are the mechanism — metadata fields are a consequence of state transitions,
not a replacement for them.

### B — Introduce all states including chargeback and dispute

Model `CHARGEBACK_REQUESTED`, `CHARGEBACK_IN_REVIEW`, `CHARGEBACK_WON`,
`CHARGEBACK_LOST` as part of Step 1.

**Rejected**: chargeback workflows require provider integration for dispute
submission, evidence collection APIs, and resolution callbacks — all of which
are Step 6+ concerns. Introducing them at Step 1 would couple the state machine
to infrastructure that does not yet exist.

### C — Separate transaction types per operation

Model `AuthorizationTransaction`, `CaptureTransaction`, `SettlementTransaction`
as distinct entity types rather than states on a single `Transaction`.

**Rejected**: this increases join complexity for every query that needs the full
transaction history. The single-entity state machine model is operationally
simpler and aligns with how payment systems at Stripe and Adyen model the
lifecycle — one transaction, multiple states.

---

## Performance Targets (Step 1)

| Metric | Target | Baseline (Step 0) |
|---|---|---|
| Throughput | 20–30 req/sec | 10 req/sec |
| p95 latency (payment endpoints) | < 200ms | < 150ms |
| Error rate | < 1% | < 1% |
| DB QPS | ~100–150 | ~30–60 |
| Optimistic lock retry rate | < 2% | N/A |
| Authorization TTL expiry job | runs every 60s | N/A |

---

## What Will Change at Step 2

| Concern | Step 1 approach | Step 2 target |
|---|---|---|
| Settlement trigger | Manual internal command | Automated on provider callback |
| Reconciliation | Not implemented | Matching engine for provider reports |
| Provider integration | Synchronous HTTP stub | Real async HTTP with circuit breaker |
| Reversal workflow | Single-step command | Full reversal lifecycle with provider |
| Float account locking | Pessimistic on all ops | Evaluate per-operation contention |

---

*Living document. Reviewed every Sunday. Updated at each architecture milestone.*
*github.com/Geekers-Joel237 · linkedin.com/in/geekers-joel237 · geekersjoel237.substack.com*