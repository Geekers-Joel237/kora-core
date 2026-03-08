# Kora-Core Wallet — System Design Final Step 0
> Java 21 | Spring Boot 3.x | DDD | Double-entry Ledger | TDD

---

## 1. Scope métier

| Use Case | Description |
|---|---|
| Création compte | Inscription passwordless OTP mail + PIN |
| Cash-in | Dépôt via provider simulé |
| Cash-out | Retrait via provider simulé |
| P2P Transfer | Transfert entre comptes internes |

**Invariant fondamental** : `SUM(débits) == SUM(crédits)` à tout instant dans le ledger.

---

## 2. Domain Model

### Id (Value Object — record)
```java
public record Id(String value) {

    // constructeur compact
    public Id {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Id cannot be blank");
    }

    public static Id generate() {
        return new Id(UUID.randomUUID().toString());
    }
}

// Usage en test  : new Id("001")
// Usage en prod  : Id.generate()
// Toutes les entités partagent ce type
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
hashedPin   : String      // Argon2 — jamais en clair
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
> Invariants : prefix non vide, number numérique, longueur valide.
> Immuable par construction.

### Account
```java
accountId    : Id
accountNumber: String      // généré, lisible métier
accountType  : AccountType // Value Object
balance      : Balance     // Value Object — cache ledger
```

### AccountType (Value Object — record)
```java
resourceId   : Id
resourceType : ResourceType // CUSTOMER_ACCOUNT | FLOAT_ACCOUNT
```
> Pas de FK en DB — intégrité gérée au niveau domaine (DDD pur).
> Un FLOAT_ACCOUNT par provider. Créé au bootstrap système.

### Balance (Value Object — record)
```java
amount : Amount

credit(Amount) : Balance   // retourne nouveau VO
debit(Amount)  : Balance   // retourne nouveau VO
                           // InsufficientFundsException si résultat négatif
solde()        : Amount
```
> Cache matérialisé du ledger en lecture.
> Source de vérité = somme des Operations du ledger.

### Amount (Value Object — record)
```java
value    : BigDecimal   // JAMAIS Float ni Double
currency : String       // ex: "XOF"

add(Amount)          : Amount
subtract(Amount)     : Amount
isGreaterThan(Amount): boolean
```
> Invariants : value >= 0, currency non nulle non vide.
> CurrencyMismatchException si devises différentes sur toute opération.

---

### Ledger (Domain Service — entité unique en DB)
```java
ledgerId : Id   // 1 seule entrée, créée au bootstrap

cashIn(customerAccount, floatAccount, Amount)  : Transaction
cashOut(customerAccount, floatAccount, Amount) : Transaction
transfer(accountFrom, accountTo, Amount)       : Transaction
```

**Responsabilités :**
- Valider les invariants financiers (solde, comptes actifs)
- Garantir exactement 2 Operations par Transaction
- Retourner une Transaction **non persistée** (domaine pur)
- Aucun import Spring
- Ne connaît pas le Provider
- Ne connaît pas les repositories

### Transaction
```java
transactionId     : Id
transactionNumber : String           // référence métier lisible, unique
fromId            : Id               // accountId source
toId              : Id               // accountId destination
state             : TransactionState
type              : TransactionType  // sealed
paymentMethod     : String
amount            : Amount
createdAt         : Instant
operations        : List<Operation>  // toujours 2, immuable
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

**Transitions valides :**
```
INITIALIZED → PENDING
PENDING     → COMPLETED
PENDING     → FAILED
toute autre → InvalidStateTransitionException
```

### TrxHistoricStates
```java
id         : Id
trxId      : Id
oldState   : TransactionState
newState   : TransactionState
occurredAt : Instant
```
> Audit trail immuable. Jamais modifié après création.

### Operation
```java
operationId : Id
type        : OperationType  // DEBIT | CREDIT
amount      : Amount
accountId   : Id
createdAt   : Instant
```
> Immuable. Jamais mise à jour après création.
> Pas de state propre — état porté par la Transaction parente.

---

### Auth

### CustomerOtp (Redis — pas en DB)
```java
// clé Redis : "otp:{customerId}"
code : String    // 6 chiffres
ttl  : Duration  // 5 minutes — usage unique
```

### AuthUser (DTO — pas persisté)
```java
isLoggedIn : boolean
status     : UserStatus
profile    : Profile
tokens     : Tokens
```

### Tokens
```java
accessToken  : TokenValue(value: String, expiredAt: Instant)  // ~15 min
refreshToken : TokenValue(value: String, expiredAt: Instant)  // ~7 jours
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

## 3. Architecture Applicative

```
┌──────────────────────────────────────────────────────────┐
│                     API Layer                            │
│          AuthController | PaymentController              │
│        Spring annotations ici uniquement                 │
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
│   Ports (interfaces) :                                   │
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
│   Règle absolue : aucun import Spring                    │
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

## 4. Flows détaillés

### 4.1 Création de compte
```
1.  POST /auth/register { email, phoneNumber }
2.  Validation format email + phoneNumber
3.  CustomerRepository.existsByEmail() → 409 si doublon
4.  Génère OTP 6 chiffres
5.  OtpStore.save("otp:{email}", code, ttl=5min)
6.  MailPort.send(email, code)
7.  HTTP 200

8.  POST /auth/verify { email, otp, pin }
9.  OtpStore.get("otp:{email}") → InvalidOtpException si absent/expiré
10. OTP correspond ? → sinon InvalidOtpException
11. OtpStore.delete("otp:{email}")   ← usage unique
12. PIN hashé Argon2
13. DB Transaction atomique :
    └── Crée User    [VERIFIED]
    └── Crée Customer(id=userId, phoneNumber, hashedPin)
    └── Crée Account (CUSTOMER_ACCOUNT, resourceId=customerId)
14. Génère accessToken + refreshToken
15. HTTP 201 { AuthUser }
```

### 4.2 Cash-in
```
1.  POST /payments/cash-in { amount, currency, paymentMethod, pin }
    Header: Authorization: Bearer {accessToken}
2.  AuthService.validatePin(customerId, pin)
3.  AccountRepository.findByCustomerId()    → customerAccount
    AccountRepository.findFloatByProvider() → floatAccount
    customerAccount null ?  → AccountNotFoundException
    customer.isActive() ?   → AccountSuspendedException si non
4.  Ledger.cashIn(customerAccount, floatAccount, Amount(value, currency))
    └── accounts actifs ?        → sinon InvalidAccountException
    └── amount > 0 ?             → sinon IllegalArgumentException
    └── Crée Transaction [INITIALIZED] type=CASH_IN
    └── Op #1 : DEBIT  floatAccount    amount
    └── Op #2 : CREDIT customerAccount amount
    └── assert ops.size() == 2
    └── assert SUM(debits) == SUM(credits)
    └── Retourne Transaction (non persistée)
5.  DB Transaction atomique :
    └── Persiste Transaction [PENDING] + Op#1 + Op#2
    └── TrxHistoricState (INITIALIZED → PENDING)
6.  Provider.credit(amount, paymentMethod)
7a. Succès :
    └── Transaction [COMPLETED] + TrxHistoricState + Balance update
7b. Échec :
    └── Transaction [FAILED] + TrxHistoricState
    └── Op#3 DEBIT customerAccount + Op#4 CREDIT floatAccount
    └── Balance inchangée
8.  HTTP 200 { transactionId, transactionNumber, state }
```

### 4.3 Cash-out
```
1.  POST /payments/cash-out { amount, currency, paymentMethod, pin }
2.  AuthService.validatePin(customerId, pin)
3.  Résout customerAccount + floatAccount
4.  Ledger.cashOut(customerAccount, floatAccount, Amount)
    └── balance >= amount ? → sinon InsufficientFundsException
    └── Crée Transaction [INITIALIZED] type=CASH_OUT
    └── Op#1 : DEBIT  customerAccount amount
    └── Op#2 : CREDIT floatAccount    amount
5.  DB Transaction atomique :
    └── Persiste Transaction [PENDING] + 2 Ops + Historic
6.  Provider.debit(amount, paymentMethod)
7a. Succès → [COMPLETED] + Balance update
7b. Échec  → [FAILED] + reversal (Op#3 DEBIT float / Op#4 CREDIT customer)
```

### 4.4 P2P Transfer
```
1.  POST /payments/transfer { toPhoneNumber, amount, currency, pin }
2.  AuthService.validatePin(customerId, pin)
3.  Application (existence + statut) :
    AccountRepository.findByCustomerId()       → accountFrom
    CustomerRepository.findByPhone(toPhone)
      null ?                → AccountNotFoundException
    customerTo.isSuspended()? AccountSuspendedException
    accountTo.isBlocked() ?   AccountBlockedException
4.  Ledger.transfer(accountFrom, accountTo, Amount)
    └── from == to ?           → SelfTransferException
    └── balance >= amount ?    → InsufficientFundsException
    └── accountTo.isActive() ? → InvalidAccountException
    └── Crée Transaction [INITIALIZED] type=P2P_TRANSFER
    └── Op#1 : DEBIT  accountFrom amount
    └── Op#2 : CREDIT accountTo   amount
5.  DB Transaction atomique :
    └── Persiste Transaction [PENDING] + 2 Ops + Historic
6.  Provider.send(amount, paymentMethod)
7a. Succès → [COMPLETED]
7b. Échec  → [FAILED] + reversal
```

---

## 5. Stratégie de Test TDD

### Philosophie
```
RED          → Test échoue pour la bonne raison
CLEAN GREEN  → Test passe + conventions DDD respectées
               (naming, séparation domaine/infra, immutabilité)
MAYBE REFACTOR → Déclenché par signal concret uniquement :
                 duplication visible, couplage, lisibilité dégradée
```

### Matrice des niveaux

| Niveau | Scope | Collaborateurs | Spring | DB |
|---|---|---|---|---|
| Unit | VO, Domain, Application | InMemory | ✗ | ✗ |
| Integration | Repositories, Adapteurs | Réels | Slice | Testcontainers |
| E2E | Use cases complets | Réels | Full | Testcontainers |

> **Principe** : on teste un cas d'utilisation précis qui parcourt
> l'ensemble des collaborateurs réels,
> ET les collaborateurs de façon spécifique pour valider des règles précises.

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

### NIVEAU 1 — Tests Unitaires
> Pas de Spring. Pas de DB. Pas de réseau.
> Services et objets métier réels.
> InMemory repositories comme collaborateurs.

#### IdTest
```java
[ ] new Id("001")              → valide
[ ] new Id("abc-123")          → valide
[ ] Id.generate()              → valide, format UUID
[ ] Id.generate() != Id.generate() → toujours différents
[ ] new Id(null)               → IllegalArgumentException
[ ] new Id("")                 → IllegalArgumentException
[ ] new Id("   ")              → IllegalArgumentException
[ ] new Id("001").equals(new Id("001")) → true
[ ] new Id("001").equals(new Id("002")) → false
[ ] Id est immuable            → value() toujours même résultat
```

#### AmountTest
```java
[ ] new Amount(BigDecimal.valueOf(100), "XOF")  → valide
[ ] new Amount(BigDecimal.ZERO, "XOF")          → valide
[ ] new Amount(BigDecimal.valueOf(-1), "XOF")   → IllegalArgumentException
[ ] new Amount(null, "XOF")                     → IllegalArgumentException
[ ] new Amount(BigDecimal.valueOf(100), null)   → IllegalArgumentException
[ ] new Amount(BigDecimal.valueOf(100), "")     → IllegalArgumentException
[ ] new BigDecimal("0.1").add(new BigDecimal("0.2"))
    stocké dans Amount → value exactement 0.3

[ ] Amount(100).add(Amount(50, "XOF"))          → Amount(150, "XOF")
[ ] Amount(100).subtract(Amount(50, "XOF"))     → Amount(50, "XOF")
[ ] Amount(100).subtract(Amount(150))           → IllegalArgumentException
[ ] Amount(100,"XOF").add(Amount(50,"EUR"))     → CurrencyMismatchException
[ ] add() → original inchangé (immuabilité)

[ ] Amount(100).isGreaterThan(Amount(50))        → true
[ ] Amount(50).isGreaterThan(Amount(100))        → false
[ ] Amount(100,"XOF").equals(Amount(100,"XOF")) → true
[ ] Amount(100,"XOF").equals(Amount(100,"EUR")) → false
```

#### PhoneNumberTest
```java
[ ] new PhoneNumber("+225", "0700000000")  → valide
[ ] new PhoneNumber("", "0700000000")      → IllegalArgumentException
[ ] new PhoneNumber("+225", "")            → IllegalArgumentException
[ ] new PhoneNumber("+225", "070000")      → IllegalArgumentException
[ ] new PhoneNumber("+225", "ABCDEFGHIJ")  → IllegalArgumentException

[ ] normalize("+225","0700000000").fullNumber() → "+2250700000000"
[ ] .prefix()  → "+225"
[ ] .number()  → "0700000000"
[ ] normalize retourne nouveau VO — immuable
```

#### BalanceTest
```java
[ ] new Balance(Amount(0, "XOF"))         → valide
[ ] new Balance(Amount(-1, "XOF"))        → IllegalArgumentException

[ ] balance.credit(Amount(100,"XOF"))     → Balance(100) — original inchangé
[ ] balance.debit(Amount(50,"XOF"))       → Balance(50)  — original inchangé
[ ] Balance(100).debit(Amount(200,"XOF")) → InsufficientFundsException
[ ] balance.solde()                       → Amount(100,"XOF")
```

#### AccountTest
```java
[ ] Account CUSTOMER avec resourceId=customerId
    → accountType.resourceType == CUSTOMER_ACCOUNT
[ ] Account FLOAT avec resourceId=providerId
    → accountType.resourceType == FLOAT_ACCOUNT
[ ] Account CUSTOMER sans resourceId       → IllegalArgumentException
[ ] Account FLOAT sans resourceId          → IllegalArgumentException
[ ] accountId généré non null
[ ] accountNumber généré non null

[ ] account.isActive()   → true par défaut
[ ] account.isBlocked()  → false par défaut
[ ] CUSTOMER_ACCOUNT.debit() solde insuffisant → InsufficientFundsException
[ ] FLOAT_ACCOUNT.debit()    → pas de vérification solde
```

#### CustomerTest
```java
[ ] Customer avec userId valide    → customerId == userId
[ ] Customer sans userId           → IllegalArgumentException
[ ] Customer sans phoneNumber      → IllegalArgumentException
[ ] Customer sans hashedPin        → IllegalArgumentException
[ ] customer.isActive()    → true  si status VERIFIED
[ ] customer.isSuspended() → true  si status SUSPENDED
```

#### LedgerTest — Tests les plus critiques
```java
// Fixtures communes
Id customerId  = new Id("cust-001");
Id providerId  = new Id("prov-001");
Account customerAccount = Account.customer(Id.generate(), customerId, ...);
Account floatAccount    = Account.float_(Id.generate(), providerId, ...);
Account accountA        = Account.customer(..., balance: Amount(200,"XOF"));
Account accountB        = Account.customer(..., balance: Amount(0,"XOF"));

// ── cashIn ──────────────────────────────────────────
[ ] cashIn(customerAccount, floatAccount, Amount(100,"XOF"))
    → tx.type       == CASH_IN
    → tx.state      == INITIALIZED
    → tx.operations.size() == 2
    → Op#1 type==DEBIT,  accountId==floatAccount.id,     amount==100 XOF
    → Op#2 type==CREDIT, accountId==customerAccount.id,  amount==100 XOF
    → SUM(debits)==SUM(credits)==100 XOF
    → tx.fromId == floatAccount.id
    → tx.toId   == customerAccount.id

[ ] cashIn customerAccount inactif  → InvalidAccountException
[ ] cashIn floatAccount inactif     → InvalidAccountException
[ ] cashIn Amount(0,"XOF")          → IllegalArgumentException
[ ] cashIn Amount négatif           → IllegalArgumentException

// ── cashOut ─────────────────────────────────────────
[ ] cashOut(accountA[200], floatAccount, Amount(100,"XOF"))
    → tx.type == CASH_OUT
    → Op#1 DEBIT  accountA     100 XOF
    → Op#2 CREDIT floatAccount 100 XOF
    → SUM(debits)==SUM(credits)

[ ] cashOut solde exact (200-200)   → valide, balance résultante == 0
[ ] cashOut solde insuffisant       → InsufficientFundsException
[ ] cashOut compte inactif          → InvalidAccountException

// ── transfer ────────────────────────────────────────
[ ] transfer(accountA[200], accountB, Amount(100,"XOF"))
    → tx.type == P2P_TRANSFER
    → Op#1 DEBIT  accountA 100 XOF
    → Op#2 CREDIT accountB 100 XOF
    → SUM(debits)==SUM(credits)

[ ] transfer from==to               → SelfTransferException
[ ] transfer solde insuffisant      → InsufficientFundsException
[ ] transfer accountB inactif       → InvalidAccountException
[ ] transfer devise différente      → CurrencyMismatchException

// ── Invariants transversaux ──────────────────────────
[ ] Toute Transaction produite → exactement 2 Operations
[ ] transactionId non null
[ ] transactionNumber non null
[ ] Operations immuables après création
[ ] SUM(debits)==SUM(credits) sur toute Transaction produite
```

#### AuthServiceTest (InMemory)
```java
// Collaborateurs : InMemoryCustomerRepository, InMemoryOtpStore

// validatePin
[ ] PIN correct                   → pas d'exception
[ ] PIN incorrect                 → PinValidationException
[ ] PIN null                      → IllegalArgumentException
[ ] Customer inexistant           → CustomerNotFoundException

// generateOtp + verifyOtp
[ ] generateOtp → code 6 chiffres numériques stocké dans OtpStore
[ ] Deux appels successifs        → codes différents
[ ] verifyOtp code valide non expiré  → pas d'exception
[ ] verifyOtp code invalide           → InvalidOtpException
[ ] verifyOtp code expiré             → OtpExpiredException
[ ] verifyOtp après usage             → OtpAlreadyUsedException
[ ] verifyOtp OK → OTP supprimé du store (usage unique vérifié)

// generateTokens
[ ] accessToken.expiredAt  ≈ now + 15min
[ ] refreshToken.expiredAt ≈ now + 7j
[ ] Deux appels             → tokens distincts
```

#### PaymentServiceTest (InMemory)
```java
// Collaborateurs réels :
//   InMemoryAccountRepository  (pré-chargé)
//   InMemoryCustomerRepository (pré-chargé)
//   InMemoryTransactionRepository
//   InMemoryOtpStore
//   InMemoryProviderSimulator  (configurable OK | FAIL)
//   Ledger (instance réelle)
//   AuthService (instance réelle)

// ── cashIn ──────────────────────────────────────────
[ ] cashIn nominal provider OK
    → Transaction COMPLETED persistée dans InMemoryRepo
    → 2 Operations persistées
    → TrxHistoricStates : INITIALIZED→PENDING→COMPLETED
    → balance customerAccount augmentée du montant

[ ] cashIn provider configuré FAIL
    → Transaction FAILED persistée
    → 4 Operations (2 initiales + 2 reversal)
    → balance customerAccount inchangée
    → SUM(debits)==SUM(credits)

[ ] cashIn PIN incorrect          → PinValidationException, 0 tx persistée
[ ] cashIn compte inexistant      → AccountNotFoundException, 0 tx persistée
[ ] cashIn compte suspendu        → AccountSuspendedException, 0 tx persistée
[ ] cashIn amount=0               → IllegalArgumentException, 0 tx persistée

// ── cashOut ─────────────────────────────────────────
[ ] cashOut nominal provider OK
    → Transaction COMPLETED, balance diminuée

[ ] cashOut provider FAIL
    → Transaction FAILED, balance restaurée, double-entry maintenu

[ ] cashOut solde insuffisant     → InsufficientFundsException, 0 tx
[ ] cashOut PIN incorrect         → PinValidationException, 0 tx

// ── transfer ────────────────────────────────────────
[ ] transfer nominal provider OK
    → Transaction COMPLETED
    → balance A diminuée, balance B augmentée
    → SUM global des 2 comptes inchangé

[ ] transfer provider FAIL
    → Transaction FAILED, balances restaurées

[ ] transfer destinataire inexistant  → AccountNotFoundException
[ ] transfer compte suspendu          → AccountSuspendedException
[ ] transfer vers soi-même            → SelfTransferException
[ ] transfer solde insuffisant        → InsufficientFundsException
```

---

### NIVEAU 2 — Tests d'Intégration
> Adapteurs réels. Testcontainers PostgreSQL.
> Spring slice (@DataJpaTest ou minimal).
> Chaque test dans sa propre transaction rollbackée.

#### AccountRepositoryTest
```java
[ ] save(customerAccount) → findById retourne entité intacte
[ ] save(floatAccount)    → persisté avec resourceId correct
[ ] findById(inexistant)  → Optional.empty()
[ ] findByCustomerId()    → account du customer
[ ] findFloatByProviderId()→ float account du provider
[ ] accountNumber unique  → DataIntegrityViolationException si doublon
[ ] 2 accounts même customerId → exception

[ ] Amount(0.1+0.2) persisté → relu depuis DB → exactement 0.3
[ ] Amount(999999999.99)     → pas de troncature
```

#### CustomerRepositoryTest
```java
[ ] save + findById                       → round-trip complet
[ ] findByPhoneNumber(fullNumber)         → customer ou empty
[ ] findByEmail(email)                    → customer ou empty
[ ] phoneNumber unique                    → exception si doublon
[ ] email unique                          → exception si doublon
[ ] hashedPin persisté — valeur hashée, pas en clair
```

#### TransactionRepositoryTest
```java
// Atomicité
[ ] save(transaction + 2 operations) atomique
    → transaction + 2 ops en DB
[ ] save avec opération corrompue
    → rollback complet → 0 trace en DB

// Queries
[ ] findById()           → Transaction avec Operations chargées
[ ] findByAccountId()    → toutes transactions du compte (from + to)
[ ] findByState(PENDING) → uniquement PENDING

// Historic States
[ ] save(TrxHistoricState) → persisté
[ ] findByTrxId()          → liste ordonnée par occurredAt ASC
[ ] 3 transitions          → 3 entrées récupérées dans l'ordre
```

#### FinancialInvariantsDbTest
```java
// Requêtes SQL directes — vérification au niveau DB

[ ] Après cashIn COMPLETED :
    SELECT SUM(amount) FROM operations WHERE type='DEBIT'
    == SELECT SUM(amount) FROM operations WHERE type='CREDIT'

[ ] Toute transaction a exactement 2 operations :
    SELECT COUNT(*) FROM operations WHERE transaction_id=X → 2

[ ] Aucune operation sans transaction parente valide

[ ] Après cashOut FAILED + reversal :
    SUM(debits) == SUM(credits) maintenu

[ ] FLOAT_ACCOUNT balance ==
    SUM(cashIn CREDIT ops) - SUM(cashOut DEBIT ops)
```

---

### NIVEAU 3 — Tests E2E
> Stack complète. Testcontainers PostgreSQL + Redis.
> WebClient sur RANDOM_PORT.
> Provider simulé in-memory.
> Truncate DB @BeforeEach.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

#### AuthE2ETest
```java
[ ] POST /auth/register { email, phoneNumber }
    → 200, OTP en Redis, mail envoyé

[ ] POST /auth/register email existant
    → 409 CONFLICT

[ ] POST /auth/verify { email, otp_valide, pin }
    → 201 { accessToken, refreshToken }
    → User + Customer + Account en DB
    → OTP supprimé de Redis

[ ] POST /auth/verify otp invalide   → 401
[ ] POST /auth/verify otp expiré     → 401

[ ] POST /auth/login { email }       → 200, nouvel OTP
[ ] POST /auth/verify après login    → 200 { tokens }

[ ] POST /auth/refresh token valide  → 200 { nouveau accessToken }
[ ] POST /auth/refresh token expiré  → 401
```

#### CashInE2ETest
```java
[ ] POST /payments/cash-in { amount:10000, currency:"XOF", pin:correct }
    → 200
    → Transaction COMPLETED en DB
    → 2 Operations en DB
    → balance +10000 en DB
    → TrxHistoricStates INITIALIZED→PENDING→COMPLETED
    → SUM(debits)==SUM(credits) vérifié SQL

[ ] PIN incorrect              → 401, 0 tx en DB
[ ] amount négatif             → 400
[ ] sans accessToken           → 401
[ ] provider configuré FAIL
    → 200, Transaction FAILED, 4 ops, balance inchangée, invariant maintenu
```

#### CashOutE2ETest
```java
[ ] cashOut solde suffisant    → 200, COMPLETED, balance diminuée
[ ] cashOut solde insuffisant  → 422, 0 tx créée
[ ] cashOut compte suspendu    → 403
[ ] cashOut provider FAIL
    → Transaction FAILED, balance restaurée, double-entry maintenu
```

#### TransferE2ETest
```java
[ ] transfer nominal
    → 200, COMPLETED
    → balance A diminuée, balance B augmentée
    → SUM global inchangé

[ ] numéro inexistant          → 404
[ ] compte suspendu            → 403
[ ] solde insuffisant          → 422
[ ] vers soi-même              → 400
[ ] provider FAIL              → FAILED, balances restaurées, invariant OK
```

#### MoneyIntegrityE2ETest
```java
// SCÉNARIO 1 — Cash-in puis Cash-out complet
[ ] cash-in 10000 → balance=10000
    cash-out 10000 → balance=0
    SUM(debits)==SUM(credits) sur tout le ledger

// SCÉNARIO 2 — P2P et conservation de la masse
[ ] A cash-in 20000
    A transfer 8000 → B
    → A=12000, B=8000, FLOAT=20000
    → SUM global = 20000 inchangé

// SCÉNARIO 3 — Rejet sans effet de bord
[ ] A balance=5000, tente transfer 6000
    → 422
    → A=5000, B=0 inchangé
    → 0 tx en DB

// SCÉNARIO 4 — Invariant après N opérations
[ ] 3 customers cash-in 10000 chacun
    2 P2P transfers entre eux
    1 cash-out 5000
    → SUM(all DEBIT)==SUM(all CREDIT)
    → FLOAT balance == 25000

// SCÉNARIO 5 — Concurrence sur même compte
[ ] A et B tentent cash-out 8000 sur compte solde=10000 simultanément
    → exactement 1 COMPLETED, 1 FAILED ou rejeté
    → solde final >= 0, jamais négatif
    → double-entry maintenu dans les 2 cas
```

---

## 6. Ordre d'implémentation TDD

```
SEMAINE 1

  Day 1 — Value Objects
    RED→CLEAN GREEN : IdTest
    RED→CLEAN GREEN : AmountTest
    RED→CLEAN GREEN : PhoneNumberTest
    RED→CLEAN GREEN : BalanceTest

  Day 2 — Entités domaine
    RED→CLEAN GREEN : AccountTest
    RED→CLEAN GREEN : CustomerTest
    MAYBE REFACTOR  : factories communes si duplication

  Day 3 — Ledger cashIn + cashOut
    RED→CLEAN GREEN : LedgerCashInTest
    RED→CLEAN GREEN : LedgerCashOutTest
    RED→CLEAN GREEN : LedgerInvariantsTest

  Day 4 — Ledger transfer + cas d'erreur
    RED→CLEAN GREEN : LedgerTransferTest
    RED→CLEAN GREEN : LedgerErrorCasesTest
    MAYBE REFACTOR  : extraction test fixtures si duplication

  Day 5 — Repositories (Intégration)
    RED→CLEAN GREEN : AccountRepositoryTest
    RED→CLEAN GREEN : CustomerRepositoryTest

SEMAINE 2

  Day 1 — Repositories suite
    RED→CLEAN GREEN : TransactionRepositoryTest
    RED→CLEAN GREEN : FinancialInvariantsDbTest

  Day 2 — Application Services
    RED→CLEAN GREEN : AuthServiceTest
    RED→CLEAN GREEN : PaymentServiceCashInTest

  Day 3 — Application Services suite
    RED→CLEAN GREEN : PaymentServiceCashOutTest
    RED→CLEAN GREEN : PaymentServiceTransferTest
    MAYBE REFACTOR  : PaymentService si duplication visible

  Day 4 — E2E Auth + Cash-in
    RED→CLEAN GREEN : AuthE2ETest
    RED→CLEAN GREEN : CashInE2ETest

  Day 5 — E2E complet + Money Integrity
    RED→CLEAN GREEN : CashOutE2ETest
    RED→CLEAN GREEN : TransferE2ETest
    RED→CLEAN GREEN : MoneyIntegrityE2ETest (scénarios 1→5)
```

---

## 7. Invariants financiers — Checklist permanente

```
[ ] SUM(DEBIT ops) == SUM(CREDIT ops) sur tout le ledger
[ ] Toute Transaction a exactement 2 Operations
[ ] Aucune Operation modifiée après création
[ ] Aucun solde CUSTOMER_ACCOUNT négatif possible
[ ] Transaction FAILED a toujours ses Operations de reversal
[ ] TrxHistoricStates contient toutes transitions sans gap
[ ] FLOAT balance == SUM(cashIn) - SUM(cashOut) par provider
[ ] transactionNumber unique globalement
```

---

## 8. Contraintes sécurité Step 0

| Contrainte | Implémentation |
|---|---|
| PIN | Argon2, jamais en clair, jamais loggué |
| OTP | Redis TTL 5min, usage unique |
| JWT | Access ~15min / Refresh ~7j |
| Montants | BigDecimal + currency obligatoire |
| Logs | Pas de PII, pas de montants sensibles |
| Domain | Aucun import Spring dans le domaine |

---

## 9. Dette technique assumée

```java
// TODO Step 5 : extraire vers Outbox Pattern
// Risque : appel Provider dans thread principal après persistance PENDING
// Acceptable < 10 req/sec — revoir impérativement à Step 4
```

---

## 10. Objectifs volumétriques Step 0

| Métrique | Cible |
|---|---|
| Transactions/jour | 5 000 |
| Req/sec peak | 5 – 10 |
| P95 latence | < 150 ms |
| DB QPS | ~ 30 – 60 |