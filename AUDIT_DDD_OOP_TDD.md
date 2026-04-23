# Audit DDD / OOP / TDD — Kora Core

> Date : 2026-04-23  
> Branche auditée : `feature/payment-lifecycle-state-machine`  
> Périmètre : ensemble de la codebase (domaine, application, infrastructure, web, tests)  
> Mode : lecture seule — aucune modification de code dans cette session

---

## Résumé exécutif

| Couche | CRITIQUE | MAJEURE | MINEURE | Total |
|--------|----------|---------|---------|-------|
| DDD    | 2        | 6       | 3       | 11    |
| OOP    | 1        | 2       | 2       | 5     |
| TDD    | 0        | 2       | 6       | 8     |
| **Total** | **3** | **10** | **11** | **24** |

**Points forts notables :** machine à états `TransactionState` (pattern State exemplaire), double-entrée
vérifiée dans `Transaction.recordDoubleEntry()`, pattern Snapshot appliqué uniformément, tests
financiers DB (`FinancialInvariantsDbTest`) avec SQL natif, stratégie 3 couches (unit / intégration /
E2E) et doubles de test in-memory bien structurés.

---

## SECTION 1 — Incohérences DDD

### DDD-1 — `MailProviderException` étend `Throwable` *(CRITIQUE)*

**Fichier :** `domain/exception/MailProviderException.java`

`MailProviderException` étend `Throwable` directement — ni `Exception`, ni `RuntimeException`.
Conséquence : elle ne peut pas être attrapée dans un bloc `catch (Exception e)`, Spring ne peut pas la
propager correctement, et le `GlobalExceptionHandler` n'a aucun handler pour `Throwable`. Tout appel à
`MailPort.sendOtp()` qui lance cette exception produit un comportement indéfini au runtime.

**Correction :** Faire étendre `BusinessException` (ou au minimum `RuntimeException`).

---

### DDD-2 — `GlobalExceptionHandler` : incompatibilité de type pour `InvalidStateTransitionException` *(CRITIQUE)*

**Fichier :** `web/exception/GlobalExceptionHandler.java:28-33`

```java
@ExceptionHandler({..., InvalidStateTransitionException.class})
ProblemDetail onUnprocessable(BusinessException ex) {   // ← paramètre BusinessException
```

`InvalidStateTransitionException` n'étend pas `BusinessException` (elle étend `RuntimeException`
directement — cf. DDD-3). Spring MVC tente de binder l'instance à `BusinessException ex`, ce qui échoue
avec un `ClassCastException` ou cause un fallback vers un handler générique 500. Résultat : toute
transition invalide produit une erreur 500 au lieu du 422 attendu.

**Correction :** Faire étendre `BusinessException` à `InvalidStateTransitionException` (cf. DDD-3).

---

### DDD-3 — Exceptions métier n'étendant pas `BusinessException` *(MAJEURE)*

**Fichiers :**
- `domain/exception/InvalidStateTransitionException.java`
- `domain/exception/InvalidOtpException.java`
- `domain/exception/OtpExpiredException.java`
- `domain/exception/DuplicateEmailException.java`

Ces quatre exceptions sont des exceptions métier (invariants du domaine, règles fonctionnelles) mais
étendent `RuntimeException` directement, contournant la hiérarchie établie
`BusinessException → RuntimeException`. L'impact direct de cette incohérence est documenté en DDD-2 pour
`InvalidStateTransitionException`.

**Correction :** Toutes doivent étendre `BusinessException`.

---

### DDD-4 — `AuthService` importe une classe d'infrastructure *(MAJEURE)*

**Fichier :** `application/service/AuthService.java`

```java
import com.geekersjoel237.koracore.infrastructure.config.SecurityProperties;
```

La couche application ne doit pas connaître la couche infrastructure. Ce couplage rend `AuthService`
non testable sans charger le contexte Spring et viole la règle de dépendance de l'architecture
hexagonale (les couches internes ne dépendent jamais des couches externes).

**Correction :** Extraire les propriétés JWT dans un record de configuration du domaine, ou passer les
valeurs (secret, durée) directement comme paramètres du constructeur.

---

### DDD-5 — Bibliothèque JWT dans la couche application *(MAJEURE)*

**Fichier :** `application/service/AuthService.java`

```java
import io.jsonwebtoken.*;
```

La génération de JWT est une préoccupation d'infrastructure (sécurité, cryptographie, format de token).
La couche application ne doit pas dépendre de `jjwt`. `AuthService` effectue une responsabilité qui
appartient à un adaptateur infrastructure.

**Correction :** Déplacer la génération de tokens dans un `JwtTokenAdapter` en infrastructure.
L'application reçoit un port `TokenGenerator` (ou similaire) en injection.

---

### DDD-6 — `AuthUseCase.generateTokens(User)` exposé dans un port applicatif *(MAJEURE)*

**Fichier :** `application/port/in/AuthUseCase.java`

```java
Tokens generateTokens(User user);
```

Le port applicatif expose `generateTokens()`, ce qui laisse filtrer le concept infrastructure de "JWT
token" dans la définition du contrat fonctionnel. Un port DDD ne doit exposer que des opérations métier
(`register`, `login`, `verifyOtp`). La génération de tokens est un détail d'implémentation qui ne
devrait pas apparaître dans ce contrat.

**Correction :** Retirer `generateTokens` du port `AuthUseCase`. La déléguer à un port infrastructure
ou l'encapsuler dans l'implémentation.

---

### DDD-7 — `AuthorizationRecord.isExpired()` utilise `Instant.now()` sans injection de `Clock` *(MAJEURE)*

**Fichier :** `domain/model/AuthorizationRecord.java`

```java
public boolean isExpired() {
    return Instant.now().isAfter(this.expiresAt);
}
```

L'horloge système est fixée en dur. Le domaine n'est pas contrôlable dans les tests. Sans `Clock`, il
est impossible d'écrire un test déterministe pour les cas limites (TTL exactement expiré, timezone,
drift d'horloge). `AuthorizationTtlExpiryJob` appelle cette méthode en production — sans Clock, la
régression silencieuse n'est pas détectable.

**Correction :** Injecter un `Clock` dans `AuthorizationRecord.create()` et le stocker, ou le passer
en paramètre : `isExpired(Clock clock)`.

---

### DDD-8 — Création du compte client absente du flux d'enregistrement *(MAJEURE)*

**Fichier :** `application/service/AuthService.java` + `e2e/AbstractE2ETest.java:136-148`

`AuthService.register()` crée un `Customer` mais ne crée pas de `Account`. Le helper E2E
`setupCustomerWithAccount()` compense en créant manuellement le compte après registration :

```java
Id accountId = accountRepository.findByCustomerId(customerId)
        .map(a -> a.snapshot().accountId())
        .orElseGet(() -> {
            Id newId = Id.generate();
            accountRepository.save(Account.createCustomerAccount(newId, customerId));
            return newId;
        });
```

C'est une lacune du domaine : un client sans compte ne peut pas effectuer de paiement, et cette
cohérence n'est pas assurée par le modèle.

**Correction :** Inclure la création du `Account` dans le cas d'utilisation `register()`, ou documenter
explicitement que c'est une opération séparée avec son propre endpoint.

---

### DDD-9 — `Balance.solde()` : nomenclature française dans une base anglaise *(MINEURE)*

**Fichier :** `domain/vo/Balance.java`

`solde()` est en français dans une codebase entièrement en anglais. Référencé dans
`AccountEntity.update()`, `JpaAccountRepository.toEntity()`, `JpaAccountRepository.toDomain()`, etc.

**Correction :** Renommer en `amount()` ou `total()`.

---

### DDD-10 — `ProviderPort` expose des méthodes Step 0 héritées *(MINEURE)*

**Fichier :** `domain/port/ProviderPort.java`

```java
void credit(Amount amount, String paymentMethod);
void debit(Amount amount, String paymentMethod);
void send(Amount amount, String paymentMethod);
```

Ces méthodes sont un héritage du Step 0 (avant la refactorisation lifecycle deux phases). Elles ne sont
pas utilisées par le flux de paiement `initiate → authorize → capture → settle`. Leur présence dans le
port du domaine pollue le contrat.

**Correction :** Les supprimer du port une fois la migration Step 0 → lifecycle complète.

---

### DDD-11 — `HistoryAction` : `DateTimeParseException` non interceptée *(MINEURE)*

**Fichier :** `web/api/payment/history/HistoryAction.java:37-38`

```java
from != null ? Instant.parse(from) : null,
to   != null ? Instant.parse(to)   : null,
```

`Instant.parse()` lance `DateTimeParseException` (extends `RuntimeException`) pour tout format invalide.
`GlobalExceptionHandler` n'a pas de handler pour `DateTimeParseException` → Spring retourne un 500 au
lieu d'un 400 Bad Request.

**Correction :** Ajouter `@ExceptionHandler(DateTimeParseException.class)` → HTTP 400 dans
`GlobalExceptionHandler`, ou valider le format en amont avec Bean Validation.

---

## SECTION 2 — Incohérences OOP

### OOP-1 — `Amount` : double sémantique d'égalité, contrat `equals`/`hashCode` brisé *(CRITIQUE)*

**Fichier :** `domain/vo/Amount.java`

`Amount` est un `record` Java. En plus de l'`equals(Object)` auto-généré par le record (comparaison
`BigDecimal` scale-sensitive), il existe un overload :

```java
public boolean equals(Amount other) {
    return this.value.compareTo(other.value) == 0 && this.currency.equals(other.currency);
}
```

Deux sémantiques coexistent :
- `amount1.equals(amount2)` avec les deux de type `Amount` → `Amount.equals(Amount)` : **scale-insensitive** (`100.00 == 100.0`)
- `assertEquals(amount1, amount2)` → `Object.equals(Object)` (record) : **scale-sensitive** (`100.00 ≠ 100.0`)

Le `hashCode()` du record est basé sur `BigDecimal.hashCode()` qui est lui aussi scale-sensitive.
`Amount.of(new BigDecimal("100.00"), "XOF")` et `Amount.of(new BigDecimal("100.0"), "XOF")` ont :
- `equals(Amount)` → `true`
- `equals(Object)` → `false`
- `hashCode()` → **différents**

Cela viole le contrat `equals`/`hashCode` dès que des instances `Amount` sont insérées dans une `Set`
ou une `HashMap`. Le test `AmountTest` valide les deux comportements séparément, rendant l'incohérence
invisible en apparence mais réelle structurellement.

**Correction :** Supprimer l'overload `equals(Amount)`. Faire un vrai override de `Object.equals(Object)`
avec la logique scale-insensitive et un override cohérent de `hashCode()` (ex. normaliser la scale via
`stripTrailingZeros()` avant de hasher).

---

### OOP-2 — `AuthorizationStatus` définie en enum imbriquée dans le modèle *(MAJEURE)*

**Fichier :** `domain/model/AuthorizationRecord.java`

`AuthorizationStatus` (ACTIVE, CONSUMED, CANCELLED, EXPIRED) est une enum imbriquée dans
`AuthorizationRecord`. L'infrastructure (`JpaAuthorizationRecordRepository`, mapping entité) doit la
référencer via le modèle parent. Cela alourdit les imports et expose le modèle entier là où seule
l'enum est nécessaire.

**Correction :** Déplacer `AuthorizationStatus` dans `domain/enums/` pour qu'elle soit accessible
indépendamment.

---

### OOP-3 — `AuthService.generateOtp()` est `public` *(MAJEURE)*

**Fichier :** `application/service/AuthService.java`

```java
public String generateOtp(String email) { ... }
```

`generateOtp()` est un détail d'implémentation interne à `register()` et `login()`. Elle n'est pas dans
le port `AuthUseCase`. La rendre `public` expose une API interne qui peut être appelée hors contexte
et couple les tests à l'implémentation (cf. TDD-5).

**Correction :** Passer en package-private. Les tests doivent passer par `register()` puis lire le
store OTP directement.

---

### OOP-4 — `Transaction.addOperation()` public et `@Deprecated` sans horizon de suppression *(MINEURE)*

**Fichier :** `domain/model/Transaction.java:91-94`

```java
@Deprecated
public void addOperation(Operation op) { this.operations.add(op); }
```

L'API dépréciée reste publique et accessible. Seul `TransactionRepositoryTest.buildTransaction()`
l'utilise encore, ce qui bloque la suppression. Ce couplage impose de maintenir indéfiniment une API
interne non souhaitée.

**Correction :** Refactoriser `TransactionRepositoryTest.buildTransaction()` pour utiliser
`recordDoubleEntry()` (cf. TDD-3), puis supprimer `addOperation()`.

---

### OOP-5 — `PaymentUseCaseTest` importe `BCryptCustomerPinEncoder` depuis l'infrastructure *(MINEURE)*

**Fichier :** `application/PaymentUseCaseTest.java:20, 50`

```java
import com.geekersjoel237.koracore.infrastructure.security.BCryptCustomerPinEncoder;
private final CustomerPinEncoder pinEncoder = new BCryptCustomerPinEncoder();
```

Un test de la couche application ne devrait pas connaître la classe concrète de l'infrastructure. Ce
couplage crée une dépendance inter-couches dans les tests unitaires.

**Correction :** Fournir un `FakeCustomerPinEncoder` (retourne le pin tel quel) dans
`shared/inmemory/`, et l'utiliser dans les tests applicatifs.

---

## SECTION 3 — Incohérences TDD

### TDD-1 — Test `isExpired` contourne l'absence d'injection de `Clock` *(MAJEURE)*

**Fichier :** `test/domain/model/AuthorizationRecordTest.java:52-63`

```java
AuthorizationRecord record = AuthorizationRecord.createFromSnapshot(
    new AuthorizationRecord.Snapshot(
        ...,
        Instant.now().minus(Duration.ofMinutes(16)),
        Instant.now().minus(Duration.ofMinutes(1)),  // expiresAt dans le passé
        AuthorizationStatus.ACTIVE
    )
);
assertTrue(record.isExpired());
```

Le test manipule les timestamps pour contourner l'appel à `Instant.now()` dans `isExpired()`. Ce n'est
pas un test du comportement de production — il ne teste pas la logique de comparaison dans le contexte
réel (instance fraîchement créée dont le TTL vient juste d'expirer). Sans `Clock`, on ne peut pas
tester le cas limite à la frontière du TTL ni se prémunir contre un drift.

**Correction :** Injecter `Clock` (cf. DDD-7), réécrire les tests avec `Clock.fixed(...)`.

---

### TDD-2 — Branche UPDATE de `JpaTransactionRepository.save()` non testée *(MAJEURE)*

**Fichier :** `test/infrastructure/persistence/TransactionRepositoryTest.java`

`JpaTransactionRepository.save()` a deux branches : INSERT (première sauvegarde) et UPDATE (ajout des
opérations après capture, mise à jour du state). Les tests couvrent uniquement le INSERT. La branche
UPDATE — qui contient la logique complexe de fusion des opérations avec la guard `orphanRemoval` —
n'a pas de test dédié.

**Correction :** Ajouter des tests pour :
1. `save()` après `tx.authorize()` → vérifier que seul le `state` est mis à jour
2. `save()` après `ledger.writeEntries()` → vérifier que les deux opérations sont ajoutées sans
   duplication
3. Vérifier que les opérations existantes ne sont pas supprimées (guard orphanRemoval)

---

### TDD-3 — `TransactionRepositoryTest.buildTransaction()` utilise l'API dépréciée *(MINEURE)*

**Fichier :** `test/infrastructure/persistence/TransactionRepositoryTest.java:41-43`

```java
tx.addOperation(Operation.create(Id.generate(), OperationType.DEBIT, AMOUNT_10K, fromId));
tx.addOperation(Operation.create(Id.generate(), OperationType.CREDIT, AMOUNT_10K, toId));
```

L'API `@Deprecated` est appelée dans un test d'infrastructure. C'est précisément ce couplage qui
bloque la suppression de `addOperation()` (cf. OOP-4).

**Correction :** Remplacer par `tx.recordDoubleEntry(AMOUNT_10K, fromId, toId)`.

---

### TDD-4 — `AbstractE2ETest.waitAndGetOtpCode()` : polling avec `Thread.sleep` *(MINEURE)*

**Fichier :** `test/e2e/AbstractE2ETest.java:155-168`

```java
for (int i = 0; i < 20; i++) {  // up to ~1s total
    if (otpOpt.isPresent()) return otpOpt.get().code();
    Thread.sleep(50);
}
throw new AssertionError("OTP not found...");
```

Le polling avec sleeps est fragile sous charge (CI lent, machine surchargée) et ralentit la suite E2E
inutilement. Le test peut échouer aléatoirement si le OTP prend plus d'une seconde à apparaître.

**Correction :** Utiliser `Awaitility`, ou synchroniser via un `CountDownLatch` dans
`InMemoryMailPort`.

---

### TDD-5 — `AuthUseCaseTest` : tests couplés à une méthode d'implémentation non portée *(MINEURE)*

**Fichier :** `test/application/AuthUseCaseTest.java:98-146`

```java
String code = authService.generateOtp(EMAIL);
```

`generateOtp()` n'est pas dans le port `AuthUseCase`. Les tests appellent directement un détail
d'implémentation sur `AuthService`. Si la méthode est renommée ou encapsulée (cf. OOP-3), les tests
cassent sans que le comportement observable ait changé.

**Correction :** Passer par `authService.register(command)` puis lire depuis
`otpStore.get("otp:" + email)` — pattern déjà utilisé dans `AbstractE2ETest`.

---

### TDD-6 — `Ledger.reverse()` non testé dans `LedgerTest` *(MINEURE)*

**Fichier :** `test/domain/model/LedgerTest.java` (absence)

La méthode `Ledger.reverse()` utilisée dans le flux de reversal est absente de `LedgerTest`. Cette
logique est couverte indirectement via `PaymentUseCaseTest` mais pas au niveau du domaine, là où les
invariants doivent être vérifiés en premier.

**Correction :** Ajouter dans `LedgerTest` :
- `should_reverse_transaction_by_writing_inverted_double_entry()`
- `should_maintain_double_entry_invariant_after_reversal()`

---

### TDD-7 — `HistoryAction` : paramètres invalides non testés *(MINEURE)*

**Fichier :** `web/api/payment/history/HistoryAction.java` (aucun test)

`HistoryAction` n'a pas de test couvrant les paramètres invalides (`type=INVALID_VALUE`,
`from=not-a-date`). Ces cas produisent des exceptions non gérées retournant HTTP 500 (cf. DDD-11).

**Correction :** Ajouter des cas dans `TransactionHistoryE2ETest` avec des valeurs invalides, ou un
test unitaire mockant `TransactionHistoryUseCase`.

---

### TDD-8 — `ProviderFailureE2ETest` : scénario `FAIL_ON_CAPTURE` sur transfer absent *(MINEURE)*

**Fichier :** `test/e2e/ProviderFailureE2ETest.java`

Les tests `WhenProviderFailsOnCapture` couvrent uniquement `cashIn`. Le scénario `FAIL_ON_CAPTURE` pour
`transfer` n'est pas couvert, alors que ce flux implique deux comptes et une logique de restauration de
balance plus complexe.

**Correction :** Ajouter :
```java
@Test
void should_restore_both_balances_when_capture_fails_on_transfer() { ... }
```

---

## SECTION 4 — Matrice de priorité

| ID     | Titre court                                          | Sévérité  | Impact runtime                         | Effort |
|--------|------------------------------------------------------|-----------|----------------------------------------|--------|
| DDD-1  | `MailProviderException extends Throwable`            | CRITIQUE  | OUI — propagation incorrecte           | Faible |
| DDD-2  | `GlobalExceptionHandler` ClassCastException ISE      | CRITIQUE  | OUI — HTTP 500 au lieu de 422          | Faible |
| OOP-1  | `Amount` double égalité, `hashCode` brisé            | CRITIQUE  | Latent — bombe en collections          | Moyen  |
| DDD-3  | 4 exceptions n'étendent pas `BusinessException`      | MAJEURE   | OUI partiel (amplifie DDD-2)           | Faible |
| DDD-7  | `AuthorizationRecord.isExpired()` sans `Clock`       | MAJEURE   | Non — mais non testable                | Faible |
| DDD-8  | Compte absent du flux register                       | MAJEURE   | OUI — customer sans account possible   | Moyen  |
| TDD-2  | Branche UPDATE de `JpaTransactionRepository` non testée | MAJEURE | Risque réel en production             | Moyen  |
| DDD-4  | `AuthService` importe infra `SecurityProperties`     | MAJEURE   | Non — couplage structurel              | Moyen  |
| DDD-5  | `AuthService` importe `io.jsonwebtoken`              | MAJEURE   | Non — violation hexagonale             | Élevé  |
| DDD-6  | `generateTokens` dans port `AuthUseCase`             | MAJEURE   | Non — conception                       | Faible |
| TDD-1  | Test `isExpired` contourne l'horloge                 | MAJEURE   | Non — fragilité test                   | Faible |
| OOP-2  | `AuthorizationStatus` enum imbriquée dans modèle     | MAJEURE   | Non — design                           | Faible |
| OOP-3  | `generateOtp()` publique                             | MAJEURE   | Non — encapsulation                    | Faible |
| DDD-11 | `HistoryAction` : `DateTimeParseException` non gérée | MINEURE  | OUI — HTTP 500 pour date invalide      | Faible |
| OOP-4  | `addOperation()` public `@Deprecated`                | MINEURE   | Non — pollution API                    | Faible |
| OOP-5  | Test appli importe `BCryptCustomerPinEncoder`         | MINEURE   | Non — couplage inter-couches           | Faible |
| TDD-3  | `buildTransaction` utilise API dépréciée             | MINEURE   | Non — tech debt                        | Faible |
| TDD-4  | Polling `Thread.sleep` dans E2E                      | MINEURE   | Non — fragilité CI                     | Faible |
| TDD-5  | Tests couplés à `generateOtp()` non porté            | MINEURE   | Non — fragilité                        | Faible |
| TDD-6  | `Ledger.reverse()` non testé                         | MINEURE   | Non — couverture domaine               | Faible |
| TDD-7  | `HistoryAction` paramètres invalides non testés      | MINEURE   | Non — couverture web                   | Faible |
| TDD-8  | `FAIL_ON_CAPTURE` transfer non testé                 | MINEURE   | Non — couverture E2E                   | Faible |
| DDD-9  | `Balance.solde()` nomenclature française             | MINEURE   | Non — lisibilité                       | Faible |
| DDD-10 | `ProviderPort` méthodes Step 0 orphelines            | MINEURE   | Non — nettoyage                        | Faible |

---

## SECTION 5 — Observations transversales

### 5.1 Architecture hexagonale — 85 % conforme

Le découpage domaine / application / infrastructure / web est solide. Les ports sont définis dans le
domaine. Les adaptateurs JPA (`JpaAccountRepository`, `JpaTransactionRepository`) utilisent le pattern
Snapshot correctement et n'exposent pas JPA dans le domaine. La seule brèche systémique est
`AuthService` qui a absorbé des responsabilités JWT appartenant à l'infrastructure (DDD-4, DDD-5).

### 5.2 Pattern Snapshot — bien appliqué, friction résiduelle

Le pattern `snapshot()` / `createFromSnapshot()` est appliqué uniformément (Account, Transaction,
Customer, Ledger, AuthorizationRecord). La sérialisation/désérialisation JPA est correcte. La friction
vient de `addOperation()` dépréciée dans `Transaction`, maintenue à cause d'un test d'infrastructure —
couplage à sens inverse (test → domaine) qui doit être résolu (OOP-4 + TDD-3).

### 5.3 Machine à états — conception exemplaire

L'implémentation State Pattern via `TransactionState` (interface + implémentations package-private +
constantes statiques) est la partie la plus soignée du projet. Les transitions sont déclaratives, les
états terminaux identifiés. Une question ouverte : `AUTHORIZED → AUTHORIZATION_FAILED` est autorisé
par `AuthorizedState`. Sémantiquement, une transaction déjà autorisée ne peut pas échouer en phase
d'autorisation. Si ce cas couvre un timeout post-autorisation, un commentaire explicite ou un état
`AUTHORIZATION_TIMEOUT` serait plus expressif.

### 5.4 Tests financiers — pratique avancée

`FinancialInvariantsDbTest` avec requêtes SQL natives pour valider les invariants de double-entrée
directement en base est une pratique rarement vue et particulièrement adaptée à une codebase fintech.
Vérifier `SUM(DEBIT) = SUM(CREDIT)` à la granularité de chaque transaction ET globalement, ainsi que
l'absence d'opérations orphelines, constitue un filet de sécurité fort.

### 5.5 Soft spots à surveiller pour les prochaines étapes

1. **Idempotence** : aucune clé d'idempotence implémentée (mentionnée dans CLAUDE.md / ROADMAP.md
   mais absente du code). Les appels HTTP répétés dupliquent les transactions — critique en production.

2. **Validation des entrées HTTP** : les records de requête (`CashInRequest`, `RegisterRequest`) n'ont
   pas de Bean Validation (`@NotBlank`, `@DecimalMin`). Un montant null ou une devise vide remonte
   jusqu'au domaine en `IllegalArgumentException`.

3. **Soft-delete** : les requêtes SQL de `FinancialInvariantsDbTest` filtrent
   `WHERE deleted_at IS NULL`, impliquant un schéma soft-delete. Aucune logique de soft-delete n'est
   visible dans les entités JPA ni dans les repositories. À vérifier : `BaseEntity` le gère-t-il
   silencieusement ?

4. **Pagination history** : `HistoryAction` parse `type` et `state` comme strings bruts via
   `valueOf()`. Toute valeur invalide produit une `IllegalArgumentException` → HTTP 400 (géré), mais
   sans message d'erreur explicite listant les valeurs acceptées.
