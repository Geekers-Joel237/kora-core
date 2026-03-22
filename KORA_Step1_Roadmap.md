# KORA Core — Step 1 : Payment Lifecycle

**Durée estimée** : 3 semaines
**Volume cible** : 15 000 tx/jour · 20–30 req/sec · P95 < 200ms · DB QPS ~100–150
**Prérequis** : Step 0 complété et tous les tests verts

```
./gradlew test   # doit passer à 100% avant de commencer Step 1
```

---

## Objectif

Step 0 savait qu'une transaction réussissait ou échouait.
Step 1 sait **pourquoi**, **où**, et **à quel moment** chaque centime se trouve.

Le cycle de vie réel d'un paiement :

```
INITIALIZED → AUTHORIZED → CAPTURED → SETTLEMENT_PENDING → SETTLED → COMPLETED
                │               │
                └─ AUTHORIZATION_FAILED    └─ CAPTURE_FAILED
                └─ REVERSED (pre-capture)  └─ REVERSED (post-capture)
                                           └─ SETTLEMENT_FAILED
```

---

## Ce qui change par rapport à Step 0

| Dimension | Step 0 | Step 1 |
|---|---|---|
| States | `INITIALIZED → PENDING → COMPLETED / FAILED` | 11 états, lifecycle complet |
| `TransactionState` interface | 4 constantes statiques | 12 constantes, même interface |
| `Transaction` méthodes publiques | `markPending/Completed/Failed` | + `authorize/capture/pendSettlement/settle/reverse/failAuthorization/failCapture/failSettlement` |
| `PaymentUseCase` | `cashIn/cashOut/transfer/getBalance` | + `authorizePayment/capturePayment/reversePayment` |
| `ProviderPort` | `credit/debit/send` | + `authorize/capture/reverse` |
| `TrxStateHistoricEntity` | 4 colonnes | + `triggered_by/correlation_id/provider_ref/actor_id/notes` |
| `AccountEntity` | pas de `@Version` | + `@Version version BIGINT` |
| Concurrence | Lost update accepté (ADR-001 dette) | Optimistic locking résolu |

---

## Règle fondamentale — zéro régression

**Tous les tests Step 0 doivent rester verts à chaque commit.**

Si un test existant casse, corriger l'implémentation, jamais le test.

Les use cases `cashIn`, `cashOut`, `transfer` continuent de passer par
`INITIALIZED → PENDING → COMPLETED/FAILED` sans modification.
`markPending()`, `markCompleted()`, `markFailed()` sont inchangés.

---

## BLOC 1 — Fondation domaine
*Semaine 1 — 3 à 4 jours*

Tout le reste dépend de ce bloc. Tests verts requis avant de passer au Bloc 2.

---

### Tâche 1.1 — Étendre `TransactionState` et `Transaction`

**Fichiers touchés :**

```
src/main/java/.../domain/model/state/
  ├── TransactionState.java          ← modifier : ajouter constantes + fromValue() + isTerminal()
  ├── InitializedState.java          ← modifier : ajouter authorize() dans transitionTo()
  ├── PendingState.java              ← NE PAS TOUCHER
  ├── CompletedState.java            ← modifier : ajouter isTerminal() = true
  ├── FailedState.java               ← modifier : ajouter isTerminal() = true
  ├── AuthorizedState.java           ← créer
  ├── CapturedState.java             ← créer
  ├── SettlementPendingState.java    ← créer
  ├── SettledState.java              ← créer
  ├── AuthorizationFailedState.java  ← créer (terminal)
  ├── CaptureFailedState.java        ← créer (terminal)
  ├── SettlementFailedState.java     ← créer (terminal)
  └── ReversedState.java             ← créer (terminal)

src/main/java/.../domain/model/
  └── Transaction.java               ← modifier : ajouter 8 nouvelles méthodes publiques
```

**`TransactionState.java` — modifications :**

```java
// Ajouter après FAILED
TransactionState AUTHORIZED           = new AuthorizedState();
TransactionState CAPTURED             = new CapturedState();
TransactionState SETTLEMENT_PENDING   = new SettlementPendingState();
TransactionState SETTLED              = new SettledState();
TransactionState AUTHORIZATION_FAILED = new AuthorizationFailedState();
TransactionState CAPTURE_FAILED       = new CaptureFailedState();
TransactionState SETTLEMENT_FAILED    = new SettlementFailedState();
TransactionState REVERSED             = new ReversedState();

// Ajouter à l'interface
boolean isTerminal();

// Mettre à jour fromValue() — ajouter les 8 nouveaux cas
static TransactionState fromValue(String value) {
    return switch (value) {
        case "INITIALIZED"          -> INITIALIZED;
        case "PENDING"              -> PENDING;
        case "COMPLETED"            -> COMPLETED;
        case "FAILED"               -> FAILED;
        case "AUTHORIZED"           -> AUTHORIZED;
        case "CAPTURED"             -> CAPTURED;
        case "SETTLEMENT_PENDING"   -> SETTLEMENT_PENDING;
        case "SETTLED"              -> SETTLED;
        case "AUTHORIZATION_FAILED" -> AUTHORIZATION_FAILED;
        case "CAPTURE_FAILED"       -> CAPTURE_FAILED;
        case "SETTLEMENT_FAILED"    -> SETTLEMENT_FAILED;
        case "REVERSED"             -> REVERSED;
        default -> throw new IllegalArgumentException(
                "Unknown transaction state: " + value);
    };
}
```

**`InitializedState.java` — ajouter la transition vers AUTHORIZED :**

```java
@Override
public TransactionState transitionTo(TransactionState next) {
    if (next instanceof PendingState)    return next;  // Step 0 — inchangé
    if (next instanceof AuthorizedState) return next;  // Step 1 — nouveau
    throw new InvalidStateTransitionException(this, next);
}

@Override
public boolean isTerminal() { return false; }
```

**`PendingState.java` — ajouter seulement `isTerminal()` :**

```java
@Override
public boolean isTerminal() { return false; }
```

**`CompletedState.java` et `FailedState.java` — ajouter `isTerminal()` :**

```java
@Override
public boolean isTerminal() { return true; }
```

**8 nouvelles classes d'état — pattern identique :**

```java
// AuthorizedState.java
class AuthorizedState implements TransactionState {
    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof CapturedState
                || next instanceof AuthorizationFailedState
                || next instanceof ReversedState) return next;
        throw new InvalidStateTransitionException(this, next);
    }
    @Override public boolean isTerminal() { return false; }
    @Override public String name() { return "AUTHORIZED"; }
}

// CapturedState.java
class CapturedState implements TransactionState {
    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof SettlementPendingState
                || next instanceof CaptureFailedState
                || next instanceof ReversedState) return next;
        throw new InvalidStateTransitionException(this, next);
    }
    @Override public boolean isTerminal() { return false; }
    @Override public String name() { return "CAPTURED"; }
}

// SettlementPendingState.java
class SettlementPendingState implements TransactionState {
    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof SettledState
                || next instanceof SettlementFailedState) return next;
        throw new InvalidStateTransitionException(this, next);
    }
    @Override public boolean isTerminal() { return false; }
    @Override public String name() { return "SETTLEMENT_PENDING"; }
}

// SettledState.java
class SettledState implements TransactionState {
    @Override
    public TransactionState transitionTo(TransactionState next) {
        if (next instanceof CompletedState) return next;
        throw new InvalidStateTransitionException(this, next);
    }
    @Override public boolean isTerminal() { return false; }
    @Override public String name() { return "SETTLED"; }
}

// États terminaux — throw sur toute transition
// AuthorizationFailedState, CaptureFailedState,
// SettlementFailedState, ReversedState
class AuthorizationFailedState implements TransactionState {
    @Override
    public TransactionState transitionTo(TransactionState next) {
        throw new InvalidStateTransitionException(this, next);
    }
    @Override public boolean isTerminal() { return true; }
    @Override public String name() { return "AUTHORIZATION_FAILED"; }
}
// Même pattern pour CaptureFailedState ("CAPTURE_FAILED"),
// SettlementFailedState ("SETTLEMENT_FAILED"), ReversedState ("REVERSED")
```

**`Transaction.java` — ajouter les 8 nouvelles méthodes publiques :**

```java
// Step 1 — en continuité avec markPending/Completed/Failed existants
public void authorize()          { transitionTo(TransactionState.AUTHORIZED); }
public void capture()            { transitionTo(TransactionState.CAPTURED); }
public void pendSettlement()     { transitionTo(TransactionState.SETTLEMENT_PENDING); }
public void settle()             { transitionTo(TransactionState.SETTLED); }
public void reverse()            { transitionTo(TransactionState.REVERSED); }
public void failAuthorization()  { transitionTo(TransactionState.AUTHORIZATION_FAILED); }
public void failCapture()        { transitionTo(TransactionState.CAPTURE_FAILED); }
public void failSettlement()     { transitionTo(TransactionState.SETTLEMENT_FAILED); }
```

**Tests TDD — ajouter dans `TransactionStateTest.java` (existant) :**

```
// Transitions légales Step 1
✓ should_allow_initialized_to_authorized
✓ should_allow_authorized_to_captured
✓ should_allow_authorized_to_authorization_failed
✓ should_allow_authorized_to_reversed
✓ should_allow_captured_to_settlement_pending
✓ should_allow_captured_to_capture_failed
✓ should_allow_captured_to_reversed
✓ should_allow_settlement_pending_to_settled
✓ should_allow_settlement_pending_to_settlement_failed
✓ should_allow_settled_to_completed

// Transitions illégales Step 1
✗ should_throw_when_authorized_to_initialized
✗ should_throw_when_authorized_to_pending
✗ should_throw_when_captured_to_authorized
✗ should_throw_when_settled_to_captured
✗ should_throw_when_transitioning_from_authorization_failed
✗ should_throw_when_transitioning_from_capture_failed
✗ should_throw_when_transitioning_from_settlement_failed
✗ should_throw_when_transitioning_from_reversed

// isTerminal()
✓ should_return_true_for_all_terminal_states
✓ should_return_false_for_all_non_terminal_states

// Régression Step 0 — AUCUN des tests existants ne doit être modifié
```

**Tests dans `TransactionTest.java` (existant) :**

```
✓ should_transition_to_authorized_from_initialized
✓ should_transition_to_captured_from_authorized
✓ should_record_historic_for_each_step1_transition
✓ should_allow_full_happy_path_initialized_to_completed_via_step1_states
```

---

### Tâche 1.2 — `AuthorizationRecord` — nouvelle entité domaine

**Fichier à créer :**
`src/main/java/.../domain/model/AuthorizationRecord.java`

```java
public class AuthorizationRecord {

    private final Id id;
    private final Id transactionId;
    private final String providerReference;
    private final Amount authorizedAmount;
    private final Instant authorizedAt;
    private final Instant expiresAt;
    private AuthorizationStatus status; // ACTIVE | EXPIRED | CONSUMED | CANCELLED

    public static AuthorizationRecord create(Id transactionId,
                                              String providerReference,
                                              Amount amount,
                                              Duration ttl) {
        Instant now = Instant.now();
        return new AuthorizationRecord(Id.generate(), transactionId,
                providerReference, amount, now, now.plus(ttl),
                AuthorizationStatus.ACTIVE);
    }

    public boolean isExpired()  { return Instant.now().isAfter(expiresAt); }
    public boolean isActive()   { return status == AuthorizationStatus.ACTIVE && !isExpired(); }
    public void consume()       { this.status = AuthorizationStatus.CONSUMED; }
    public void cancel()        { this.status = AuthorizationStatus.CANCELLED; }
    public void expire()        { this.status = AuthorizationStatus.EXPIRED; }

    public Snapshot snapshot() { ... }
    public record Snapshot(Id id, Id transactionId, String providerReference,
                           Amount authorizedAmount, Instant authorizedAt,
                           Instant expiresAt, AuthorizationStatus status) {}
}
```

**Entité JPA :**
`src/main/java/.../infrastructure/persistence/entities/AuthorizationRecordEntity.java`

Étendre `BaseEntity` (héritage `@SoftDelete`, `createdAt`, `updatedAt`, `deletedAt`).

```sql
-- Table créée automatiquement par ddl-auto=update en dev
-- Index partiel pour le TTL job
CREATE INDEX idx_auth_records_expires_at
    ON authorization_records(expires_at)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
```

**Port domaine :**
`src/main/java/.../domain/port/AuthorizationRecordRepository.java`

```java
public interface AuthorizationRecordRepository {
    void save(AuthorizationRecord record);
    Optional<AuthorizationRecord> findActiveByTransactionId(Id transactionId);
    List<AuthorizationRecord> findExpiredActive(Instant now);
}
```

**Adapter JPA :**
`src/main/java/.../infrastructure/persistence/JpaAuthorizationRecordRepository.java`

**Tests :**
```
✓ create() → expiresAt = now + ttl, status ACTIVE
✓ isExpired() faux dans le TTL
✓ isExpired() vrai après TTL
✓ isActive() faux si ACTIVE mais expiré (double vérification)
✓ consume() → CONSUMED, isActive() faux
✓ cancel() → CANCELLED, isActive() faux
```

---

### Tâche 1.3 — Enrichir `TrxStateHistoric` et `TrxStateHistoricEntity`

**`TrxStateHistoric.java`** — enrichir le record.

Les nouveaux champs sont **nullable** pour maintenir la rétrocompatibilité
avec le code Step 0 qui appelle `TrxStateHistoric.of(txId, old, new)`.

```java
// AVANT (inchangé — ces champs existent déjà)
public record TrxStateHistoric(
    Id id, Id transactionId,
    TransactionState oldState, TransactionState newState,
    Instant occurredAt
    // Step 1 — nouveaux champs nullable
    TriggerSource triggeredBy,
    String correlationId,
    String providerRef,
    String actorId,
    String notes
)
```

**`TriggerSource.java`** — créer dans `domain/enums/` :

```java
public enum TriggerSource {
    USER_ACTION, PROVIDER_CALLBACK, SYSTEM_JOB, OPERATOR_ACTION
}
```

Mettre à jour `TrxStateHistoric.of()` en surcharge rétrocompatible :

```java
// Méthode existante — signature inchangée, passe null pour les nouveaux champs
public static TrxStateHistoric of(Id transactionId,
                                   TransactionState oldState,
                                   TransactionState newState) {
    return new TrxStateHistoric(Id.generate(), transactionId,
            oldState, newState, Instant.now(),
            null, null, null, null, null);
}

// Nouvelle surcharge Step 1
public static TrxStateHistoric of(Id transactionId,
                                   TransactionState oldState,
                                   TransactionState newState,
                                   TriggerSource triggeredBy,
                                   String correlationId,
                                   String providerRef,
                                   String actorId,
                                   String notes) { ... }
```

**Règle domaine dans le constructeur compact :**

```java
if (triggeredBy == TriggerSource.OPERATOR_ACTION
        && (notes == null || notes.isBlank()))
    throw new IllegalArgumentException(
            "Notes are required for OPERATOR_ACTION state transitions");
```

**`TrxStateHistoricEntity.java`** — ajouter les 5 colonnes nullable :

```java
@Column(name = "triggered_by")   private String triggeredBy;
@Column(name = "correlation_id") private String correlationId;
@Column(name = "provider_ref")   private String providerRef;
@Column(name = "actor_id")       private String actorId;
@Column(name = "notes")          private String notes;
```

Mettre à jour `JpaTrxHistoricStatesRepository.java` :
mapper les nouveaux champs dans `save()` et dans la reconstruction domaine de
`findByTransactionId()`.

**`TransactionSummary.StateEntry`** — mettre à jour si on veut exposer
les nouveaux champs dans l'historique visible via l'API.

**Tests :**
```
✓ of(txId, old, new) à 3 args → nullable acceptés, pas d'exception
✓ OPERATOR_ACTION sans notes → IllegalArgumentException
✓ OPERATOR_ACTION avec notes → OK
✓ snapshot() inclut les nouveaux champs
✓ Tous les tests Step 0 qui utilisent TrxStateHistoric passent
```

---

### Tâche 1.4 — Résoudre la dette concurrence : `@Version` sur `AccountEntity`

**`AccountEntity.java`** — ajouter le champ :**

```java
@Version
@Column(name = "version", nullable = false)
private Long version;
```

**`SpringDataAccountRepository.java`** — ajouter la méthode pour le float :**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM AccountEntity a " +
       "WHERE a.resourceType = 'FLOAT_ACCOUNT' " +
       "AND a.resourceId = :providerId " +
       "AND a.deletedAt IS NULL")
Optional<AccountEntity> findFloatAccountForUpdate(
        @Param("providerId") String providerId);
```

**`AccountRepository.java`** (port domaine) — ajouter :**

```java
Optional<Account> findFloatByProviderIdForUpdate(Id providerId);
```

**`JpaAccountRepository.java`** — implémenter le nouveau port.

**`PaymentService.java`** — ajouter la gestion de l'`OptimisticLockException`.

Les méthodes `cashIn()`, `cashOut()`, `transfer()` existantes appellent
`accountRepository.save()`. Ajouter un mécanisme de retry autour des
trois méthodes publiques. Option recommandée : retry manuel dans le service
(évite une dépendance Spring Retry) :

```java
// Wrapper à extraire dans une méthode privée utilitaire
private <T> T withRetry(Supplier<T> action) {
    for (int attempt = 1; attempt <= 3; attempt++) {
        try {
            return action.get();
        } catch (OptimisticLockingFailureException e) {
            if (attempt == 3) throw new TransientPaymentException(
                    "Concurrent update failed after 3 attempts", e);
            sleep(50L * attempt);
        }
    }
    throw new IllegalStateException("unreachable");
}
```

**`TransientPaymentException.java`** — créer dans `domain/exception/` :

```java
public class TransientPaymentException extends BusinessException {
    public TransientPaymentException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
```

**Tests :**
```
✓ @Version présent → OptimisticLockingFailureException sur update concurrent
  (test JPA/Testcontainers — deux transactions concurrentes sur même account)
✓ PaymentService retry → 3 tentatives, toutes échouent → TransientPaymentException
✓ Tous les tests Step 0 passent (AccountRepositoryTest, FinancialInvariantsDbTest)
```

---

## BLOC 2 — Use Cases métier
*Semaine 2 — 4 à 5 jours*

---

### Tâche 2.1 — Étendre `ProviderPort` et `MobileMoneyProviderAdapter`

**`ProviderPort.java`** — ajouter (les 3 méthodes existantes sont inchangées) :

```java
AuthorizationResult authorize(Amount amount, String paymentMethod,
                               String correlationId);
CaptureResult      capture(String authorizationReference, String correlationId);
ReverseResult      reverse(String captureReference, Amount amount,
                           String correlationId);
```

**Value objects à créer dans `domain/vo/` :**

```java
public record AuthorizationResult(
    String providerReference,
    Instant expiresAt,              // now + provider TTL (15–30 min)
    boolean success
) {}

public record CaptureResult(
    String captureReference,
    boolean success
) {}

public record ReverseResult(
    String reversalReference,
    boolean success
) {}
```

**`MobileMoneyProviderAdapter.java`** — implémenter les 3 nouvelles méthodes.
Stub Step 1 : toujours succès, TTL de 15 minutes, références générées
via `UUID.randomUUID()`. Les TODO des méthodes existantes sont mis à jour.

---

### Tâche 2.2 — `AuthorizePayment` use case

**`PaymentUseCase.java`** — ajouter :

```java
Transaction authorizePayment(AuthorizePaymentCommand cmd);
```

**`AuthorizePaymentCommand.java`** (créer dans `application/command/`) :

```java
public record AuthorizePaymentCommand(
    Id customerId,
    String rawPin,
    Amount amount,
    String paymentMethod,
    String correlationId
) {}
```

**`Ledger.java`** — ajouter `initiate()` :

Ce flow Step 1 sépare la création de la transaction de l'écriture des
entrées ledger. Les entrées sont écrites à la capture, pas à l'autorisation.

```java
// Step 1 : crée une transaction sans écrire les opérations ledger
// (les entrées DEBIT/CREDIT sont écrites plus tard, à la capture)
public Transaction initiate(Account fromAccount, Account toAccount,
                             TransactionType type, String paymentMethod,
                             Amount amount) {
    requireActive(fromAccount, "Source account is not active");
    requirePositive(amount);
    requireSufficientFunds(fromAccount, amount);

    return Transaction.create(Id.generate(),
            fromAccount.snapshot().accountId(),
            toAccount.snapshot().accountId(),
            type, paymentMethod, amount);
}
```

**`PaymentService.java`** — implémenter :

```java
@Override
public Transaction authorizePayment(AuthorizePaymentCommand cmd) {
    var customerAccount = validatePayerAndGetAccount(
            cmd.customerId(), cmd.rawPin()); // méthode existante
    var floatAccount = getSystemFloatAccount();             // méthode existante
    var ledger = ledgerRepository.findFirst();              // existant

    // 1. Créer la transaction sans entrées ledger
    var tx = ledger.initiate(customerAccount, floatAccount,
            TransactionType.CASH_OUT, cmd.paymentMethod(), cmd.amount());

    // 2. Persister en INITIALIZED
    persistTransactionState(tx);  // méthode existante

    // 3. Transition → AUTHORIZED
    tx.authorize();

    try {
        // 4. Appel provider
        AuthorizationResult result = provider.authorize(
                cmd.amount(), cmd.paymentMethod(), cmd.correlationId());

        // 5. Créer AuthorizationRecord
        AuthorizationRecord authRecord = AuthorizationRecord.create(
                tx.snapshot().transactionId(),
                result.providerReference(),
                cmd.amount(),
                Duration.ofMinutes(15));
        authorizationRecordRepository.save(authRecord);

        // 6. Persister état AUTHORIZED
        persistTransactionState(tx);

    } catch (ProviderException e) {
        tx.failAuthorization();
        persistTransactionState(tx);
    }

    return tx;
}
```

**Tests :**
```
✓ Autorisation réussie → état AUTHORIZED, AuthorizationRecord créée
✓ Solde insuffisant → InsufficientFundsException, rien persisté
✓ Provider échoue → AUTHORIZATION_FAILED
✓ PIN invalide → PinValidationException, rien persisté
✓ Ledger.initiate() → pas d'entrées ledger dans la transaction
```

---

### Tâche 2.3 — `CapturePayment` use case

**`PaymentUseCase.java`** — ajouter :

```java
Transaction capturePayment(CapturePaymentCommand cmd);
```

**`CapturePaymentCommand.java`** :

```java
public record CapturePaymentCommand(
    Id transactionId,
    Id customerId,
    String correlationId
) {}
```

**`Ledger.java`** — ajouter `writeEntries()` :

```java
// Step 1 : écrire les entrées ledger sur une transaction déjà initiée
public void writeEntries(Transaction tx, Account fromAccount,
                          Account floatAccount, Amount amount) {
    tx.addOperation(Operation.create(Id.generate(), OperationType.DEBIT,
            amount, fromAccount.snapshot().accountId()));
    tx.addOperation(Operation.create(Id.generate(), OperationType.CREDIT,
            amount, floatAccount.snapshot().accountId()));
    verifyDoubleEntry(tx);  // méthode privée existante
}
```

**`PaymentService.java`** — implémenter :

```java
@Override
public Transaction capturePayment(CapturePaymentCommand cmd) {
    var tx = transactionRepository.findById(cmd.transactionId())
            .orElseThrow(() -> new AccountNotFoundException(
                    "Transaction not found: " + cmd.transactionId().value()));

    // Guard : doit être en AUTHORIZED
    if (!(tx.snapshot().state() instanceof AuthorizedState)) {
        throw new InvalidStateTransitionException(
                tx.snapshot().state(), TransactionState.CAPTURED);
    }

    var authRecord = authorizationRecordRepository
            .findActiveByTransactionId(cmd.transactionId())
            .orElseThrow(() -> new IllegalStateException(
                    "No active authorization for transaction: " +
                    cmd.transactionId().value()));

    // Guard : TTL
    if (!authRecord.isActive()) {
        tx.failAuthorization();
        authRecord.expire();
        authorizationRecordRepository.save(authRecord);
        persistTransactionState(tx);
        return tx;
    }

    try {
        CaptureResult result = provider.capture(
                authRecord.snapshot().providerReference(), cmd.correlationId());

        var customerAccount = accountRepository
                .findByCustomerId(cmd.customerId()).orElseThrow();
        var floatAccount = getSystemFloatAccount();
        var ledger = ledgerRepository.findFirst();

        // Écrire les entrées ledger
        ledger.writeEntries(tx, customerAccount, floatAccount,
                tx.snapshot().amount());

        // Mettre à jour le solde (avec @Version → retry si collision)
        customerAccount.debit(tx.snapshot().amount());
        accountRepository.save(customerAccount);

        authRecord.consume();
        authorizationRecordRepository.save(authRecord);

        tx.capture();
        persistTransactionState(tx);

        tx.pendSettlement();
        persistTransactionState(tx);

    } catch (ProviderException e) {
        tx.failCapture();
        authRecord.cancel();
        authorizationRecordRepository.save(authRecord);
        persistTransactionState(tx);
    }

    return tx;
}
```

**Tests :**
```
✓ Capture réussie → 2 entrées ledger, invariant SUM(D)==SUM(C)
✓ État final → SETTLEMENT_PENDING
✓ AuthorizationRecord → CONSUMED
✓ Authorization expirée avant capture → AUTHORIZATION_FAILED, aucune entrée ledger
✓ Provider échoue → CAPTURE_FAILED, authRecord CANCELLED
✓ @Version : OptimisticLockException → retry, une seule capture finale
✓ FinancialInvariantsDbTest : double_entry_invariant_holds_after_capture
```

---

### Tâche 2.4 — `ReversePayment` use case

**`PaymentUseCase.java`** — ajouter :

```java
Transaction reversePayment(ReversePaymentCommand cmd);
```

**`ReversePaymentCommand.java`** :

```java
public record ReversePaymentCommand(
    Id transactionId,
    String actorId,
    String actorRole,     // "OPERATOR" | "ADMIN"
    String reason,        // obligatoire
    String correlationId
) {}
```

**`PaymentService.java`** — implémenter les deux branches :

```java
@Override
public Transaction reversePayment(ReversePaymentCommand cmd) {
    if (cmd.reason() == null || cmd.reason().isBlank())
        throw new IllegalArgumentException("Reason is required for reversal");

    var tx = transactionRepository.findById(cmd.transactionId()).orElseThrow(...);
    var state = tx.snapshot().state();

    // Branche A — pre-capture (AUTHORIZED)
    if (state instanceof AuthorizedState) {
        var authRecord = authorizationRecordRepository
                .findActiveByTransactionId(cmd.transactionId()).orElseThrow();

        provider.reverse(authRecord.snapshot().providerReference(),
                tx.snapshot().amount(), cmd.correlationId());

        authRecord.cancel();
        authorizationRecordRepository.save(authRecord);
        tx.reverse();
        historicRepo.save(TrxStateHistoric.of(
                tx.snapshot().transactionId(),
                state, TransactionState.REVERSED,
                TriggerSource.OPERATOR_ACTION,
                cmd.correlationId(), null, cmd.actorId(), cmd.reason()));
        transactionRepository.save(tx);
        return tx;
    }

    // Branche B — post-capture (CAPTURED ou SETTLEMENT_PENDING)
    if (state instanceof CapturedState || state instanceof SettlementPendingState) {
        var customerAccount = accountRepository
                .findByCustomerId(cmd.transactionId()).orElseThrow();
        var floatAccount = getSystemFloatAccount();
        var ledger = ledgerRepository.findFirst();

        // Méthode reverse() existante dans Ledger — ajoute 2 opérations inverses
        ledger.reverse(tx);

        customerAccount.credit(tx.snapshot().amount());
        accountRepository.save(customerAccount);

        tx.reverse();
        historicRepo.save(TrxStateHistoric.of(
                tx.snapshot().transactionId(),
                state, TransactionState.REVERSED,
                TriggerSource.OPERATOR_ACTION,
                cmd.correlationId(), null, cmd.actorId(), cmd.reason()));
        transactionRepository.save(tx);
        return tx;
    }

    // État non reversible
    throw new InvalidStateTransitionException(state, TransactionState.REVERSED);
}
```

**Tests :**
```
✓ Reversal pre-capture → REVERSED, aucune entrée ledger, authRecord CANCELLED
✓ Reversal post-capture CAPTURED → REVERSED, 4 entrées totales, invariant = 0
✓ Reversal post-capture SETTLEMENT_PENDING → REVERSED, invariant = 0
✓ Reason null → IllegalArgumentException
✓ Transaction SETTLED → InvalidStateTransitionException
✓ Transaction COMPLETED → InvalidStateTransitionException
✓ TrxStateHistoric avec OPERATOR_ACTION et notes = reason
```

---

## BLOC 3 — Infrastructure & Scheduler
*Semaine 3 — début*

---

### Tâche 3.1 — TTL Expiry Job

**Fichier à créer :**
`src/main/java/.../infrastructure/scheduler/AuthorizationTtlExpiryJob.java`

```java
@Component
public class AuthorizationTtlExpiryJob {

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleAuthorizations() {
        authorizationRecordRepository.findExpiredActive(Instant.now())
                .forEach(auth -> {
                    transactionRepository.findById(auth.transactionId())
                            .ifPresent(tx -> {
                                auth.expire();
                                tx.failAuthorization();
                                authorizationRecordRepository.save(auth);
                                transactionRepository.save(tx);
                                historicRepo.save(TrxStateHistoric.of(
                                        tx.snapshot().transactionId(),
                                        TransactionState.AUTHORIZED,
                                        TransactionState.AUTHORIZATION_FAILED,
                                        TriggerSource.SYSTEM_JOB,
                                        null, null,
                                        "ttl-expiry-job",
                                        "Authorization expired — TTL exceeded. " +
                                        "Provider ref: " + auth.snapshot().providerReference()
                                ));
                            });
                });
    }
}
```

Ajouter `@EnableScheduling` sur `KoraCoreApplication.java`.

**Tests :**
```
✓ AuthorizationRecord expirée → EXPIRED, transaction → AUTHORIZATION_FAILED
✓ AuthorizationRecord active → non touchée
✓ Job idempotent : même donnée rejouée → aucun effet supplémentaire
✓ TrxStateHistoric avec SYSTEM_JOB
```

---

### Tâche 3.2 — Nouveaux endpoints REST

Créer dans `web/api/payment/` en suivant exactement le pattern existant
(interface `Api` + classe `Action` + record `Request`) :

```
authorize/
  AuthorizeApi.java      POST /payments/authorize
  AuthorizeAction.java
  AuthorizeRequest.java  { rawPin, amount, currency, paymentMethod, correlationId }

capture/
  CaptureApi.java        POST /payments/{txId}/capture
  CaptureAction.java
  CaptureRequest.java    { correlationId }

reverse/
  ReverseApi.java        POST /payments/{txId}/reverse
  ReverseAction.java
  ReverseRequest.java    { reason, correlationId }
```

Tous retournent `TransactionResponse` (classe existante).
Mettre à jour `SecurityConfig.java` si nécessaire (les nouveaux endpoints
doivent être protégés par `@SecurityRequirement(name = "bearerAuth")`).

---

## BLOC 4 — Tests de charge Step 1
*Semaine 3 — fin*

**SLOs cibles :**

| Métrique | Cible |
|---|---|
| Throughput | 20–30 req/sec |
| P95 latence (authorize + capture) | < 200ms |
| Error rate | < 1% |
| Optimistic lock retry rate | < 2% |
| DB QPS | ~100–150 |

**Scénarios k6 à créer dans `perf/scenarios/` :**

```javascript
// authorize_and_capture.js
// → happy path : authorize → capture → vérifier état SETTLEMENT_PENDING
// → 20 VUs, 3 min, rampe progressive

// concurrent_capture.js
// → 10 requêtes simultanées sur le même compte
// → vérifier : une seule capture réussie, balance correcte, retry < 2%

// ttl_expiry.js
// → créer autorisations avec TTL court (1 min en perf profile)
// → laisser le job tourner
// → vérifier état AUTHORIZATION_FAILED
```

---

## BLOC 5 — Tests E2E Step 1

Créer dans `src/test/java/.../e2e/` en étendant `AbstractE2ETest` :

```java
// AuthorizeAndCaptureE2ETest.java
// → happy path complet : authorize → capture → SETTLEMENT_PENDING
// → vérifier balance débitée après capture
// → vérifier double-entry invariant via jdbcTemplate

// AuthorizationExpiredE2ETest.java
// → forcer TTL court via profil test
// → déclencher le job manuellement
// → vérifier état AUTHORIZATION_FAILED

// ReversalE2ETest.java
// → reversal pre-capture : REVERSED, balance inchangée
// → reversal post-capture : REVERSED, balance restaurée
```

Étendre `FinancialInvariantsDbTest.java` :

```java
@Test
void no_ledger_entries_created_on_authorization_failure()

@Test
void double_entry_invariant_holds_after_capture()

@Test
void reversal_post_capture_produces_net_zero_entries()
```

---

## Ordre d'exécution

```
Semaine 1
─────────
Lun–Mar   Tâche 1.1   Étendre TransactionState + Transaction + tests TDD
           → feat(payment): extend state machine to full payment lifecycle
Mar        Tâche 1.2   AuthorizationRecord + JPA entity + port + adapter
           → feat(payment): add AuthorizationRecord domain entity
Jeu        Tâche 1.3   Enrichir TrxStateHistoric + TrxStateHistoricEntity
           → feat(ledger): add audit fields to TrxStateHistoric
Ven        Tâche 1.4   @Version AccountEntity + float pessimistic lock + retry
           → fix(payment): resolve lost update problem with optimistic locking

Semaine 2
─────────
Lun        Tâche 2.1   Étendre ProviderPort + MobileMoneyProviderAdapter
           → feat(provider): add authorize/capture/reverse operations
Mar–Mer    Tâche 2.2   AuthorizePayment use case + Ledger.initiate() + tests
           → feat(payment): add AuthorizePayment use case
Jeu        Tâche 2.3   CapturePayment use case + Ledger.writeEntries() + tests
           → feat(payment): add CapturePayment use case
Ven        Tâche 2.4   ReversePayment use case + tests
           → feat(payment): add ReversePayment use case

Semaine 3
─────────
Lun        Tâche 3.1   TTL Expiry Job + @EnableScheduling + tests
           → feat(payment): add authorization TTL expiry scheduler
Mar        Tâche 3.2   Endpoints authorize/capture/reverse + tests E2E
           → feat(api): expose payment lifecycle endpoints
Mer–Jeu    Bloc 4      Tests de charge k6
Ven        Bloc 5 + DoD Finalisation, validation, tag v0.2.0
```

---

## Definition of Done — Step 1

**Chaque tâche :**
```
✓ Code dans la bonne couche hexagonale
✓ Aucun test Step 0 ne régresse
✓ Tests unitaires et JPA/Testcontainers passent
```

**Step 1 complété :**
```
✓ Les 5 scénarios réels passent en E2E :
    1. Happy path : authorize → capture → SETTLEMENT_PENDING
    2. TTL expiré avant capture : AUTHORIZATION_FAILED, balance inchangée
    3. Provider échoue sur capture : CAPTURE_FAILED, authRecord CANCELLED
    4. Reversal pre-capture : REVERSED, aucune entrée ledger
    5. Reversal post-capture : REVERSED, entrées inverses, net ledger = 0
✓ Tests de charge : P95 < 200ms à 20–30 req/sec
✓ Optimistic lock retry rate < 2%
✓ docs/adr/ADR-002-payment-lifecycle.md placé dans le repo
✓ CONTRIBUTING.md mis à jour (nouveaux endpoints, nouveaux scénarios de test)
✓ ROADMAP.md : Step 1 marqué complété
✓ git tag v0.2.0 sur main via release/v0.2
```

---

*Step 1 complété → Step 2 : Modular Monolith discipliné*
*github.com/Geekers-Joel237 · linkedin.com/in/geekers-joel237 · geekersjoel237.substack.com*
