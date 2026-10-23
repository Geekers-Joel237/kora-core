# Kora-Core Wallet — System Design Final Step 0
> Java 21 | Spring Boot 3.x | DDD | Double-entry Ledger | TDD

---

## 1. Business Scope

| Use Case | Description |
|---|---|
| Account creation | Passwordless OTP mail registration + PIN |
| Cash-in | Deposit via simulated provider |
| Cash-out | Withdrawal via simulated provider |
| P2P Transfer | Transfer between internal accounts |

**Fundamental invariant**: `SUM(debits) == SUM(credits)` at all times in the Ledger.

---

## 2. Domain Model

### Id (Value Object — record)
```java
public record Id(String value) {

    // compact constructor
    public Id {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Id cannot be blank");
    }

    public static Id generate() {
        return new Id(UUID.randomUUID().toString());
    }
}

// Usage in tests : new Id("001")
// Usage in prod  : Id.generate()
// All entities share this type
```

### User
```java
id       : Id
fullName : String
email    : String        // unique
role     : Role          // CUSTOMER | ADMIN
status   : UserStatus    // PENDING | VERIFIED | SUSPENDED
```

### Customer
```java
customerId  : Id          // == user.id
phoneNumber : PhoneNumber // Value Object
hashedPin   : String      // Argon2 — never in plain text
```

### PhoneNumber (Value Object — record)
```java
prefix : String
number : String

static normalize(prefix, number) : PhoneNumber
fullNumber() : String
prefix()     : String
number()     : String
```
> Invariants: prefix not blank, number numeric, valid length.
> Immutable by construction.

### Account
```java
accountId    : Id
accountNumber: String      // generated, business-readable
accountType  : AccountType // Value Object
balance      : Balance     // Value Object — Ledger cache
```

### AccountType (Value Object — record)
```java
resourceId   : Id
resourceType : ResourceType // CUSTOMER_ACCOUNT | FLOAT_ACCOUNT
```
> No FK in DB — integrity managed at domain level (pure DDD).
> One FLOAT_ACCOUNT per provider. Created at system bootstrap.

### Balance (Value Object — record)
```java
amount : Amount

credit(Amount) : Balance   // returns new VO
debit(Amount)  : Balance   // returns new VO
                           // InsufficientFundsException if result is negative
solde()        : Amount
```
> Materialized cache of the Ledger for reads.
> Source of truth = sum of Operations in the Ledger.

### Amount (Value Object — record)
```java
value    : BigDecimal   // NEVER Float or Double
currency : String       // e.g.: "XAF"

add(Amount)          : Amount
subtract(Amount)     : Amount
isGreaterThan(Amount): boolean
```
> Invariants: value >= 0, currency not null and not blank.
> CurrencyMismatchException if currencies differ on any operation.

---

### Ledger (Domain Service — single entity in DB)
```java
ledgerId : Id   // 1 single entry, created at bootstrap

cashIn(customerAccount, floatAccount, Amount)  : Transaction
cashOut(customerAccount, floatAccount, Amount) : Transaction
transfer(accountFrom, accountTo, Amount)       : Transaction
```

**Responsibilities:**
- Validate financial invariants (balance, active accounts)
- Guarantee exactly 2 Operations per Transaction
- Return an **unpersisted** Transaction (pure domain)
- No Spring imports
- Does not know the Provider
- Does not know the repositories

### Transaction
```java
transactionId     : Id
transactionNumber : String           // readable business reference, unique
fromId            : Id               // source accountId
toId              : Id               // destination accountId
state             : TransactionState
type              : TransactionType  // sealed
paymentMethod     : String
amount            : Amount
createdAt         : Instant
operations        : List<Operation>  // always 2, immutable
```

```java
// sealed hierarchy — Java 21
sealed interface TransactionType
    permits TransactionType.CashIn,
            TransactionType.CashOut,
            TransactionType.P2pTransfer {}

enum TransactionState {
    INITIALIZED, PENDING, COMPLETED, FAILED
}
```

**Valid transitions:**
```
INITIALIZED → PENDING
PENDING     → COMPLETED
PENDING     → FAILED
any other   → InvalidStateTransitionException
```

### TrxHistoricStates
```java
id         : Id
trxId      : Id
oldState   : TransactionState
newState   : TransactionState
occurredAt : Instant
```
> Immutable audit trail. Never modified after creation.

### Operation
```java
operationId : Id
type        : OperationType  // DEBIT | CREDIT
amount      : Amount
accountId   : Id
createdAt   : Instant
```
> Immutable. Never updated after creation.
> No own state — state is carried by the parent Transaction.

---

### Auth

### CustomerOtp (Redis — not in DB)
```java
// Redis key: "otp:{customerId}"
code : String    // 6 digits
ttl  : Duration  // 5 minutes — single use
```

### AuthUser (DTO — not persisted)
```java
isLoggedIn : boolean
status     : UserStatus
profile    : Profile
tokens     : Tokens
```

### Tokens
```java
accessToken  : TokenValue(value: String, expiredAt: Instant)  // ~15 min
refreshToken : TokenValue(value: String, expiredAt: Instant)  // ~7 days
```

### Profile (DTO — record)
```java
fullName : String
prefix   : String
number   : String
email    : String
role     : Role
```

---

## 3. Application Architecture

```
┌──────────────────────────────────────────────────────────┐
│                     API Layer                            │
│          AuthController | PaymentController              │
│        Spring annotations here only                      │
└───────────────────────┬──────────────────────────────────┘
                        │  Commands / DTOs
┌───────────────────────▼──────────────────────────────────┐
│                 Application Layer                        │
│                                                          │
│   AuthService                  PaymentService            │
│   ├── register(cmd)            ├── cashIn(cmd)           │
│   ├── verifyOtp(cmd)           ├── cashOut(cmd)          │
│   ├── login(cmd)               └── transfer(cmd)         │
│   └── refreshToken(cmd)                                  │
│                                                          │
│   Ports (interfaces):                                    │
│   CustomerRepository | AccountRepository                 │
│   TransactionRepository | LedgerRepository               │
│   OtpStore | MailPort | ProviderPort                     │
└───────────────────────┬──────────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────────┐
│                   Domain Layer                           │
│   Ledger | Account | Customer | User                     │
│   Transaction | Operation | TrxHistoricStates            │
│   Id | PhoneNumber | Amount | Balance | AccountType      │
│                                                          │
│   Absolute rule: no Spring imports                       │
└───────────────────────┬──────────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────────┐
│               Infrastructure Layer                       │
│   PostgresCustomerRepository                             │
│   PostgresAccountRepository                              │
│   PostgresTransactionRepository                          │
│   RedisOtpStore                                          │
│   SmtpMailAdapter                                        │
│   InMemoryProviderSimulator  ← Step 0                    │
└──────────────────────────────────────────────────────────┘
```

---

## 4. Detailed Flows

### 4.1 Account Creation
```
1.  POST /auth/register { email, phoneNumber }
2.  Validate email + phoneNumber format
3.  CustomerRepository.existsByEmail() → 409 if duplicate
4.  Generate 6-digit OTP
5.  OtpStore.save("otp:{email}", code, ttl=5min)
6.  MailPort.send(email, code)
7.  HTTP 200

8.  POST /auth/verify { email, otp, pin }
9.  OtpStore.get("otp:{email}") → InvalidOtpException if absent/expired
10. OTP matches? → otherwise InvalidOtpException
11. OtpStore.delete("otp:{email}")   ← single use
12. PIN hashed with Argon2
13. Atomic DB Transaction:
    └── Create User    [VERIFIED]
    └── Create Customer(id=userId, phoneNumber, hashedPin)
    └── Create Account (CUSTOMER_ACCOUNT, resourceId=customerId)
14. Generate accessToken + refreshToken
15. HTTP 201 { AuthUser }
```

### 4.2 Cash-in
```
1.  POST /payments/cash-in { amount, currency, paymentMethod, pin }
    Header: Authorization: Bearer {accessToken}
2.  AuthService.validatePin(customerId, pin)
3.  AccountRepository.findByCustomerId()    → customerAccount
    AccountRepository.findFloatByProvider() → floatAccount
    customerAccount null?   → AccountNotFoundException
    customer.isActive()?    → AccountSuspendedException if not
4.  Ledger.cashIn(customerAccount, floatAccount, Amount(value, currency))
    └── accounts active?         → otherwise InvalidAccountException
    └── amount > 0?              → otherwise IllegalArgumentException
    └── Create Transaction [INITIALIZED] type=CASH_IN
    └── Op #1: DEBIT  floatAccount    amount
    └── Op #2: CREDIT customerAccount amount
    └── assert ops.size() == 2
    └── assert SUM(debits) == SUM(credits)
    └── Return Transaction (unpersisted)
5.  Atomic DB Transaction:
    └── Persist Transaction [PENDING] + Op#1 + Op#2
    └── TrxHistoricState (INITIALIZED → PENDING)
6.  Provider.credit(amount, paymentMethod)
7a. Success:
    └── Transaction [COMPLETED] + TrxHistoricState + Balance update
7b. Failure:
    └── Transaction [FAILED] + TrxHistoricState
    └── Op#3 DEBIT customerAccount + Op#4 CREDIT floatAccount
    └── Balance unchanged
8.  HTTP 200 { transactionId, transactionNumber, state }
```

### 4.3 Cash-out
```
1.  POST /payments/cash-out { amount, currency, paymentMethod, pin }
2.  AuthService.validatePin(customerId, pin)
3.  Resolve customerAccount + floatAccount
4.  Ledger.cashOut(customerAccount, floatAccount, Amount)
    └── balance >= amount? → otherwise InsufficientFundsException
    └── Create Transaction [INITIALIZED] type=CASH_OUT
    └── Op#1: DEBIT  customerAccount amount
    └── Op#2: CREDIT floatAccount    amount
5.  Atomic DB Transaction:
    └── Persist Transaction [PENDING] + 2 Ops + Historic
6.  Provider.debit(amount, paymentMethod)
7a. Success → [COMPLETED] + Balance update
7b. Failure → [FAILED] + reversal (Op#3 DEBIT float / Op#4 CREDIT customer)
```

### 4.4 P2P Transfer
```
1.  POST /payments/transfer { toPhoneNumber, amount, currency, pin }
2.  AuthService.validatePin(customerId, pin)
3.  Application (existence + status):
    AccountRepository.findByCustomerId()       → accountFrom
    CustomerRepository.findByPhone(toPhone)
      null?                 → AccountNotFoundException
    customerTo.isSuspended()? AccountSuspendedException
    accountTo.isBlocked()?    AccountBlockedException
4.  Ledger.transfer(accountFrom, accountTo, Amount)
    └── from == to?            → SelfTransferException
    └── balance >= amount?     → InsufficientFundsException
    └── accountTo.isActive()?  → InvalidAccountException
    └── Create Transaction [INITIALIZED] type=P2P_TRANSFER
    └── Op#1: DEBIT  accountFrom amount
    └── Op#2: CREDIT accountTo   amount
5.  Atomic DB Transaction:
    └── Persist Transaction [PENDING] + 2 Ops + Historic
6.  Provider.send(amount, paymentMethod)
7a. Success → [COMPLETED]
7b. Failure → [FAILED] + reversal
```

---

## 5. TDD Testing Strategy

### Philosophy
```
RED          → Test fails for the right reason
CLEAN GREEN  → Test passes + DDD conventions respected
               (naming, domain/infra separation, immutability)
MAYBE REFACTOR → Triggered only by a concrete signal:
                 visible duplication, coupling, degraded readability
```

### Level matrix

| Level | Scope | Collaborators | Spring | DB |
|---|---|---|---|---|
| Unit | VO, Domain, Application | InMemory | ✗ | ✗ |
| Integration | Repositories, Adapters | Real | Slice | Testcontainers |
| E2E | Complete use cases | Real | Full | Testcontainers |

> **Principle**: we test a precise use case that goes through
> all real collaborators,
> AND collaborators specifically to validate precise rules.

### Structure
```
src/test/java/com/koracore/
├── domain/
│   ├── vo/
│   │   ├── IdTest
│   │   ├── AmountTest
│   │   ├── PhoneNumberTest
│   │   └── BalanceTest
│   ├── model/
│   │   ├── AccountTest
│   │   └── CustomerTest
│   └── service/
│       └── LedgerTest
├── application/
│   ├── AuthServiceTest          ← InMemory repositories
│   └── PaymentServiceTest       ← InMemory repositories + InMemoryProvider
├── infrastructure/
│   ├── persistence/
│   │   ├── AccountRepositoryTest
│   │   ├── CustomerRepositoryTest
│   │   ├── TransactionRepositoryTest
│   │   └── FinancialInvariantsDbTest
│   └── provider/
│       └── InMemoryProviderTest
└── e2e/
    ├── AuthE2ETest
    ├── CashInE2ETest
    ├── CashOutE2ETest
    ├── TransferE2ETest
    └── MoneyIntegrityE2ETest
```

```
src/test/java/com/koracore/shared/inmemory/
├── InMemoryCustomerRepository
├── InMemoryAccountRepository
├── InMemoryTransactionRepository
└── InMemoryOtpStore
```

---

### LEVEL 1 — Unit Tests
> No Spring. No DB. No network.
> Real services and business objects.
> InMemory repositories as collaborators.

#### IdTest
```java
[ ] new Id("001")              → valid
[ ] new Id("abc-123")          → valid
[ ] Id.generate()              → valid, UUID format
[ ] Id.generate() != Id.generate() → always different
[ ] new Id(null)               → IllegalArgumentException
[ ] new Id("")                 → IllegalArgumentException
[ ] new Id("   ")              → IllegalArgumentException
[ ] new Id("001").equals(new Id("001")) → true
[ ] new Id("001").equals(new Id("002")) → false
[ ] Id is immutable            → value() always returns the same result
```

#### AmountTest
```java
[ ] new Amount(BigDecimal.valueOf(100), "XAF")  → valid
[ ] new Amount(BigDecimal.ZERO, "XAF")          → valid
[ ] new Amount(BigDecimal.valueOf(-1), "XAF")   → IllegalArgumentException
[ ] new Amount(null, "XAF")                     → IllegalArgumentException
[ ] new Amount(BigDecimal.valueOf(100), null)   → IllegalArgumentException
[ ] new Amount(BigDecimal.valueOf(100), "")     → IllegalArgumentException
[ ] new BigDecimal("0.1").add(new BigDecimal("0.2"))
    stored in Amount → value exactly 0.3

[ ] Amount(100).add(Amount(50, "XAF"))          → Amount(150, "XAF")
[ ] Amount(100).subtract(Amount(50, "XAF"))     → Amount(50, "XAF")
[ ] Amount(100).subtract(Amount(150))           → IllegalArgumentException
[ ] Amount(100,"XAF").add(Amount(50,"EUR"))     → CurrencyMismatchException
[ ] add() → original unchanged (immutability)

[ ] Amount(100).isGreaterThan(Amount(50))        → true
[ ] Amount(50).isGreaterThan(Amount(100))        → false
[ ] Amount(100,"XAF").equals(Amount(100,"XAF")) → true
[ ] Amount(100,"XAF").equals(Amount(100,"EUR")) → false
```

#### PhoneNumberTest
```java
[ ] new PhoneNumber("+225", "0700000000")  → valid
[ ] new PhoneNumber("", "0700000000")      → IllegalArgumentException
[ ] new PhoneNumber("+225", "")            → IllegalArgumentException
[ ] new PhoneNumber("+225", "070000")      → IllegalArgumentException
[ ] new PhoneNumber("+225", "ABCDEFGHIJ")  → IllegalArgumentException

[ ] normalize("+225","0700000000").fullNumber() → "+2250700000000"
[ ] .prefix()  → "+225"
[ ] .number()  → "0700000000"
[ ] normalize returns new VO — immutable
```

#### BalanceTest
```java
[ ] new Balance(Amount(0, "XAF"))         → valid
[ ] new Balance(Amount(-1, "XAF"))        → IllegalArgumentException

[ ] balance.credit(Amount(100,"XAF"))     → Balance(100) — original unchanged
[ ] balance.debit(Amount(50,"XAF"))       → Balance(50)  — original unchanged
[ ] Balance(100).debit(Amount(200,"XAF")) → InsufficientFundsException
[ ] balance.solde()                       → Amount(100,"XAF")
```

#### AccountTest
```java
[ ] Account CUSTOMER with resourceId=customerId
    → accountType.resourceType == CUSTOMER_ACCOUNT
[ ] Account FLOAT with resourceId=providerId
    → accountType.resourceType == FLOAT_ACCOUNT
[ ] Account CUSTOMER without resourceId   → IllegalArgumentException
[ ] Account FLOAT without resourceId      → IllegalArgumentException
[ ] accountId generated not null
[ ] accountNumber generated not null

[ ] account.isActive()   → true by default
[ ] account.isBlocked()  → false by default
[ ] CUSTOMER_ACCOUNT.debit() insufficient balance → InsufficientFundsException
[ ] FLOAT_ACCOUNT.debit()    → no balance check
```

#### CustomerTest
```java
[ ] Customer with valid userId    → customerId == userId
[ ] Customer without userId       → IllegalArgumentException
[ ] Customer without phoneNumber  → IllegalArgumentException
[ ] Customer without hashedPin    → IllegalArgumentException
[ ] customer.isActive()    → true  if status VERIFIED
[ ] customer.isSuspended() → true  if status SUSPENDED
```

#### LedgerTest — Most critical tests
```java
// Common fixtures
Id customerId  = new Id("cust-001");
Id providerId  = new Id("prov-001");
Account customerAccount = Account.customer(Id.generate(), customerId, ...);
Account floatAccount    = Account.float_(Id.generate(), providerId, ...);
Account accountA        = Account.customer(..., balance: Amount(200,"XAF"));
Account accountB        = Account.customer(..., balance: Amount(0,"XAF"));

// ── cashIn ──────────────────────────────────────────
[ ] cashIn(customerAccount, floatAccount, Amount(100,"XAF"))
    → tx.type       == CASH_IN
    → tx.state      == INITIALIZED
    → tx.operations.size() == 2
    → Op#1 type==DEBIT,  accountId==floatAccount.id,     amount==100 XAF
    → Op#2 type==CREDIT, accountId==customerAccount.id,  amount==100 XAF
    → SUM(debits)==SUM(credits)==100 XAF
    → tx.fromId == floatAccount.id
    → tx.toId   == customerAccount.id

[ ] cashIn inactive customerAccount  → InvalidAccountException
[ ] cashIn inactive floatAccount     → InvalidAccountException
[ ] cashIn Amount(0,"XAF")           → IllegalArgumentException
[ ] cashIn negative Amount           → IllegalArgumentException

// ── cashOut ─────────────────────────────────────────
[ ] cashOut(accountA[200], floatAccount, Amount(100,"XAF"))
    → tx.type == CASH_OUT
    → Op#1 DEBIT  accountA     100 XAF
    → Op#2 CREDIT floatAccount 100 XAF
    → SUM(debits)==SUM(credits)

[ ] cashOut exact balance (200-200)  → valid, resulting balance == 0
[ ] cashOut insufficient balance     → InsufficientFundsException
[ ] cashOut inactive account         → InvalidAccountException

// ── transfer ────────────────────────────────────────
[ ] transfer(accountA[200], accountB, Amount(100,"XAF"))
    → tx.type == P2P_TRANSFER
    → Op#1 DEBIT  accountA 100 XAF
    → Op#2 CREDIT accountB 100 XAF
    → SUM(debits)==SUM(credits)

[ ] transfer from==to                → SelfTransferException
[ ] transfer insufficient balance    → InsufficientFundsException
[ ] transfer inactive accountB       → InvalidAccountException
[ ] transfer different currency      → CurrencyMismatchException

// ── Cross-cutting invariants ──────────────────────────
[ ] Every produced Transaction → exactly 2 Operations
[ ] transactionId not null
[ ] transactionNumber not null
[ ] Operations immutable after creation
[ ] SUM(debits)==SUM(credits) on every produced Transaction
```

#### AuthServiceTest (InMemory)
```java
// Collaborators: InMemoryCustomerRepository, InMemoryOtpStore

// validatePin
[ ] Correct PIN                   → no exception
[ ] Incorrect PIN                 → PinValidationException
[ ] Null PIN                      → IllegalArgumentException
[ ] Non-existent Customer         → CustomerNotFoundException

// generateOtp + verifyOtp
[ ] generateOtp → 6-digit numeric code stored in OtpStore
[ ] Two successive calls          → different codes
[ ] verifyOtp valid non-expired code  → no exception
[ ] verifyOtp invalid code            → InvalidOtpException
[ ] verifyOtp expired code            → OtpExpiredException
[ ] verifyOtp after use               → OtpAlreadyUsedException
[ ] verifyOtp OK → OTP deleted from store (single use verified)

// generateTokens
[ ] accessToken.expiredAt  ≈ now + 15min
[ ] refreshToken.expiredAt ≈ now + 7d
[ ] Two calls              → distinct tokens
```

#### PaymentServiceTest (InMemory)
```java
// Real collaborators:
//   InMemoryAccountRepository  (pre-loaded)
//   InMemoryCustomerRepository (pre-loaded)
//   InMemoryTransactionRepository
//   InMemoryOtpStore
//   InMemoryProviderSimulator  (configurable OK | FAIL)
//   Ledger (real instance)
//   AuthService (real instance)

// ── cashIn ──────────────────────────────────────────
[ ] cashIn nominal provider OK
    → Transaction COMPLETED persisted in InMemoryRepo
    → 2 Operations persisted
    → TrxHistoricStates: INITIALIZED→PENDING→COMPLETED
    → customerAccount balance increased by amount

[ ] cashIn provider configured FAIL
    → Transaction FAILED persisted
    → 4 Operations (2 initial + 2 reversal)
    → customerAccount balance unchanged
    → SUM(debits)==SUM(credits)

[ ] cashIn incorrect PIN          → PinValidationException, 0 tx persisted
[ ] cashIn non-existent account   → AccountNotFoundException, 0 tx persisted
[ ] cashIn suspended account      → AccountSuspendedException, 0 tx persisted
[ ] cashIn amount=0               → IllegalArgumentException, 0 tx persisted

// ── cashOut ─────────────────────────────────────────
[ ] cashOut nominal provider OK
    → Transaction COMPLETED, balance decreased

[ ] cashOut provider FAIL
    → Transaction FAILED, balance restored, double-entry maintained

[ ] cashOut insufficient balance  → InsufficientFundsException, 0 tx
[ ] cashOut incorrect PIN         → PinValidationException, 0 tx

// ── transfer ────────────────────────────────────────
[ ] transfer nominal provider OK
    → Transaction COMPLETED
    → balance A decreased, balance B increased
    → Global SUM of both accounts unchanged

[ ] transfer provider FAIL
    → Transaction FAILED, balances restored

[ ] transfer non-existent recipient  → AccountNotFoundException
[ ] transfer suspended account       → AccountSuspendedException
[ ] transfer to self                 → SelfTransferException
[ ] transfer insufficient balance    → InsufficientFundsException
```

---

### LEVEL 2 — Integration Tests
> Real adapters. Testcontainers PostgreSQL.
> Spring slice (@DataJpaTest or minimal).
> Each test in its own rolled-back transaction.

#### AccountRepositoryTest
```java
[ ] save(customerAccount) → findById returns intact entity
[ ] save(floatAccount)    → persisted with correct resourceId
[ ] findById(non-existent)→ Optional.empty()
[ ] findByCustomerId()    → customer's account
[ ] findFloatByProviderId()→ provider's float account
[ ] accountNumber unique  → DataIntegrityViolationException if duplicate
[ ] 2 accounts same customerId → exception

[ ] Amount(0.1+0.2) persisted → read back from DB → exactly 0.3
[ ] Amount(999999999.99)      → no truncation
```

#### CustomerRepositoryTest
```java
[ ] save + findById                       → complete round-trip
[ ] findByPhoneNumber(fullNumber)         → customer or empty
[ ] findByEmail(email)                    → customer or empty
[ ] phoneNumber unique                    → exception if duplicate
[ ] email unique                          → exception if duplicate
[ ] hashedPin persisted — hashed value, not plain text
```

#### TransactionRepositoryTest
```java
// Atomicity
[ ] save(transaction + 2 operations) atomic
    → transaction + 2 ops in DB
[ ] save with corrupted operation
    → full rollback → 0 trace in DB

// Queries
[ ] findById()           → Transaction with Operations loaded
[ ] findByAccountId()    → all transactions for the account (from + to)
[ ] findByState(PENDING) → PENDING only

// Historic States
[ ] save(TrxHistoricState) → persisted
[ ] findByTrxId()          → list ordered by occurredAt ASC
[ ] 3 transitions          → 3 entries retrieved in order
```

#### FinancialInvariantsDbTest
```java
// Direct SQL queries — verification at DB level

[ ] After cashIn COMPLETED:
    SELECT SUM(amount) FROM operations WHERE type='DEBIT'
    == SELECT SUM(amount) FROM operations WHERE type='CREDIT'

[ ] Every transaction has exactly 2 operations:
    SELECT COUNT(*) FROM operations WHERE transaction_id=X → 2

[ ] No operation without a valid parent transaction

[ ] After cashOut FAILED + reversal:
    SUM(debits) == SUM(credits) maintained

[ ] FLOAT_ACCOUNT balance ==
    SUM(cashIn CREDIT ops) - SUM(cashOut DEBIT ops)
```

---

### LEVEL 3 — E2E Tests
> Full stack. Testcontainers PostgreSQL + Redis.
> WebClient on RANDOM_PORT.
> In-memory simulated provider.
> Truncate DB @BeforeEach.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

#### AuthE2ETest
```java
[ ] POST /auth/register { email, phoneNumber }
    → 200, OTP in Redis, mail sent

[ ] POST /auth/register existing email
    → 409 CONFLICT

[ ] POST /auth/verify { email, valid_otp, pin }
    → 201 { accessToken, refreshToken }
    → User + Customer + Account in DB
    → OTP deleted from Redis

[ ] POST /auth/verify invalid otp   → 401
[ ] POST /auth/verify expired otp   → 401

[ ] POST /auth/login { email }      → 200, new OTP
[ ] POST /auth/verify after login   → 200 { tokens }

[ ] POST /auth/refresh valid token  → 200 { new accessToken }
[ ] POST /auth/refresh expired token→ 401
```

#### CashInE2ETest
```java
[ ] POST /payments/cash-in { amount:10000, currency:"XAF", pin:correct }
    → 200
    → Transaction COMPLETED in DB
    → 2 Operations in DB
    → balance +10000 in DB
    → TrxHistoricStates INITIALIZED→PENDING→COMPLETED
    → SUM(debits)==SUM(credits) verified SQL

[ ] Incorrect PIN              → 401, 0 tx in DB
[ ] Negative amount            → 400
[ ] Without accessToken        → 401
[ ] Provider configured FAIL
    → 200, Transaction FAILED, 4 ops, balance unchanged, invariant maintained
```

#### CashOutE2ETest
```java
[ ] cashOut sufficient balance    → 200, COMPLETED, balance decreased
[ ] cashOut insufficient balance  → 422, 0 tx created
[ ] cashOut suspended account     → 403
[ ] cashOut provider FAIL
    → Transaction FAILED, balance restored, double-entry maintained
```

#### TransferE2ETest
```java
[ ] transfer nominal
    → 200, COMPLETED
    → balance A decreased, balance B increased
    → global SUM unchanged

[ ] non-existent number        → 404
[ ] suspended account          → 403
[ ] insufficient balance       → 422
[ ] to self                    → 400
[ ] provider FAIL              → FAILED, balances restored, invariant OK
```

#### MoneyIntegrityE2ETest
```java
// SCENARIO 1 — Cash-in then complete Cash-out
[ ] cash-in 10000 → balance=10000
    cash-out 10000 → balance=0
    SUM(debits)==SUM(credits) across the entire Ledger

// SCENARIO 2 — P2P and conservation of mass
[ ] A cash-in 20000
    A transfer 8000 → B
    → A=12000, B=8000, FLOAT=20000
    → global SUM = 20000 unchanged

// SCENARIO 3 — Rejection with no side effects
[ ] A balance=5000, attempts transfer 6000
    → 422
    → A=5000, B=0 unchanged
    → 0 tx in DB

// SCENARIO 4 — Invariant after N operations
[ ] 3 customers cash-in 10000 each
    2 P2P transfers between them
    1 cash-out 5000
    → SUM(all DEBIT)==SUM(all CREDIT)
    → FLOAT balance == 25000

// SCENARIO 5 — Concurrency on same account
[ ] A and B attempt cash-out 8000 on account with balance=10000 simultaneously
    → exactly 1 COMPLETED, 1 FAILED or rejected
    → final balance >= 0, never negative
    → double-entry maintained in both cases
```

---

## 6. TDD Implementation Order

```
WEEK 1

  Day 1 — Value Objects
    RED→CLEAN GREEN: IdTest
    RED→CLEAN GREEN: AmountTest
    RED→CLEAN GREEN: PhoneNumberTest
    RED→CLEAN GREEN: BalanceTest

  Day 2 — Domain entities
    RED→CLEAN GREEN: AccountTest
    RED→CLEAN GREEN: CustomerTest
    MAYBE REFACTOR : common factories if duplication

  Day 3 — Ledger cashIn + cashOut
    RED→CLEAN GREEN: LedgerCashInTest
    RED→CLEAN GREEN: LedgerCashOutTest
    RED→CLEAN GREEN: LedgerInvariantsTest

  Day 4 — Ledger transfer + error cases
    RED→CLEAN GREEN: LedgerTransferTest
    RED→CLEAN GREEN: LedgerErrorCasesTest
    MAYBE REFACTOR : extract test fixtures if duplication

  Day 5 — Repositories (Integration)
    RED→CLEAN GREEN: AccountRepositoryTest
    RED→CLEAN GREEN: CustomerRepositoryTest

WEEK 2

  Day 1 — Repositories continued
    RED→CLEAN GREEN: TransactionRepositoryTest
    RED→CLEAN GREEN: FinancialInvariantsDbTest

  Day 2 — Application Services
    RED→CLEAN GREEN: AuthServiceTest
    RED→CLEAN GREEN: PaymentServiceCashInTest

  Day 3 — Application Services continued
    RED→CLEAN GREEN: PaymentServiceCashOutTest
    RED→CLEAN GREEN: PaymentServiceTransferTest
    MAYBE REFACTOR : PaymentService if visible duplication

  Day 4 — E2E Auth + Cash-in
    RED→CLEAN GREEN: AuthE2ETest
    RED→CLEAN GREEN: CashInE2ETest

  Day 5 — Complete E2E + Money Integrity
    RED→CLEAN GREEN: CashOutE2ETest
    RED→CLEAN GREEN: TransferE2ETest
    RED→CLEAN GREEN: MoneyIntegrityE2ETest (scenarios 1→5)
```

---

## 7. Financial invariants — Permanent checklist

```
[ ] SUM(DEBIT ops) == SUM(CREDIT ops) across the entire Ledger
[ ] Every Transaction has exactly 2 Operations
[ ] No Operation modified after creation
[ ] No negative CUSTOMER_ACCOUNT balance possible
[ ] FAILED Transaction always has its reversal Operations
[ ] TrxHistoricStates contains all transitions without gaps
[ ] FLOAT balance == SUM(cashIn) - SUM(cashOut) per provider
[ ] transactionNumber globally unique
```

---

## 8. Security constraints Step 0

| Constraint | Implementation |
|---|---|
| PIN | Argon2, never in plain text, never logged |
| OTP | Redis TTL 5min, single use |
| JWT | Access ~15min / Refresh ~7d |
| Amounts | BigDecimal + mandatory currency |
| Logs | No PII, no sensitive amounts |
| Domain | No Spring imports in the domain |

---

## 9. Accepted technical debt

```java
// TODO Step 5: extract to Outbox Pattern
// Risk: Provider call in main thread after PENDING persistence
// Acceptable < 10 req/sec — must revisit at Step 4
```

---

## 10. Step 0 volume targets

| Metric | Target |
|---|---|
| Transactions/day | 5,000 |
| Req/sec peak | 5 – 10 |
| P95 latency | < 150 ms |
| DB QPS | ~ 30 – 60 |