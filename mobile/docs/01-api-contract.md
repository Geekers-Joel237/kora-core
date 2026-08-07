# Kora Mobile — Contrat API

**Source de vérité unique.** Extrait par lecture directe du code de `kora-core` @ `develop`.
Si un besoin d'écran n'apparaît pas ici, il n'existe pas côté backend. Voir §6.

> **⚠️ Contrat daté, pas définitif.** Relevé le **2026-08-06**, backend à l'**étape 3** de `ROADMAP.md`.
> Le backend est en développement actif : ce document se met à jour **avant** le code, jamais après. Un contrat de documentation en retard sur le code ment avec autorité.
> Protocole de synchronisation : `09-api-evolution.md` §6. Journal des changements et anomalies backend : §7 du même document.

Base URL de développement : `http://localhost:8081` — aucun préfixe de contexte, aucun versionnement dans l'URL.
Contrat vivant : `http://localhost:8081/v3/api-docs`.

---

## 1. Authentification

Aucun jeton requis. Toute la branche `/auth/**` est ouverte.

### `POST /auth/register`

```jsonc
// Requête
{
  "fullName":    "Aminata Diallo",   // non vide
  "email":       "aminata@kora.ci",  // format e-mail valide
  "phonePrefix": "+225",             // ^\+\d{1,4}$
  "phoneNumber": "0708091011",       // ^\d{8,15}$
  "rawPin":      "1234"              // 4 à 8 caractères
}
```

| Réponse | Corps |
|---|---|
| `201` | `{ "message": "OTP sent to your email" }` |
| `400` | `ProblemDetail` avec `violations[]` |
| `409` | E-mail déjà enregistré |
| `503` | Échec d'envoi de l'e-mail |

Effets de bord : crée l'utilisateur, le client et **son compte portefeuille** (solde 0, devise `XOF` en dur), puis envoie l'OTP.
**Aucun jeton n'est délivré ici** — il faut passer par `/auth/verify-otp`.

> **Observation vérifiée sur le code.** `User.create(...)` positionne le statut à `VERIFIED` **immédiatement**, avant toute vérification d'OTP. `UserStatus.PENDING` existe dans l'énumération mais n'est jamais attribué. L'OTP garde l'émission des jetons, il ne garde pas l'activation du compte. L'app ne doit donc **jamais** afficher d'état « compte en attente de vérification » — il n'existe pas. À rejouer après chaque livraison touchant `AuthService` : c'est un comportement susceptible de changer.

### `POST /auth/login`

```jsonc
{ "email": "aminata@kora.ci", "rawPin": "1234" }
```

| Réponse | Corps |
|---|---|
| `200` | `{ "message": "OTP sent to your email" }` |
| `401` | PIN invalide |
| `404` | Client inconnu |

> **Piège d'implémentation.** `200` ne signifie pas « connecté ». Le PIN est validé, l'OTP est parti. La session n'existe qu'après `/auth/verify-otp`.

> **Fuite d'information.** Un e-mail inconnu renvoie `404`, un PIN erroné renvoie `401` — l'API distingue les deux cas. L'application doit **rendre le même message** dans les deux situations : « E-mail ou PIN incorrect ». Ne pas propager la distinction à l'utilisateur.

### `POST /auth/verify-otp`

```jsonc
{ "email": "aminata@kora.ci", "code": "483920" }   // code: exactement 6 chiffres
```

| Réponse | Corps |
|---|---|
| `200` | `TokensResponse` |
| `401` | Code invalide, expiré, ou déjà consommé |

```jsonc
// TokensResponse
{
  "accessToken":       "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiry":  "2026-08-06T12:15:00Z",   // ISO-8601 UTC
  "refreshToken":      "eyJhbGciOiJIUzI1NiJ9...",
  "refreshTokenExpiry": "2026-08-13T12:00:00Z"
}
```

L'OTP est **à usage unique** : il est supprimé du magasin dès la vérification réussie. Un second appel avec le même code renvoie `401`.
Durée de vie : **5 minutes**.

### `POST /auth/refresh`

```jsonc
{ "refreshToken": "eyJ..." }
```

`200` → nouveau `TokensResponse` (les deux jetons sont réémis). `401` → jeton invalide ou expiré.

> **Observation vérifiée sur le code.** `AuthService.refresh` parse le jeton avec la même clé HMAC et n'en lit que le `sub`. **Aucune distinction n'est faite entre un jeton d'accès et un jeton de rafraîchissement** : un jeton d'accès encore valide est accepté par `/auth/refresh`. Sans conséquence côté app — elle envoie le bon jeton — mais à ne pas exploiter, et à signaler côté backend.

### Contenu du jeton d'accès

```jsonc
{
  "sub":   "9f2c...",              // identifiant utilisateur — sert aussi de customerId côté /payments
  "jti":   "...",
  "email": "aminata@kora.ci",
  "role":  "CUSTOMER",             // ou "ADMIN"
  "iat":   1754481600,
  "exp":   1754482500
}
```

Durées : **accès 15 minutes**, **rafraîchissement 7 jours**.
Le filtre serveur lit `sub` et l'injecte comme `customerId` dans les requêtes `/payments/**` — l'app n'a donc jamais à transmettre d'identifiant client.

> Le `sub`, l'`email` et le `role` sont la **seule source de données de profil disponible côté serveur**. Voir §6.3.

---

## 2. Paiements

En-tête requis sur toute la branche : `Authorization: Bearer <accessToken>`.
Rôle requis : `CUSTOMER`. Un jeton `ADMIN` reçoit `403` sur `/payments/**`.

### `GET /payments/balance`

```jsonc
// 200
{
  "accountId":     "3a91...",
  "accountNumber": "ACC-20260806-A3F91C2D",   // ACC-yyyyMMdd-<8 hex majuscules>
  "amount":         125000,                    // BigDecimal sérialisé en nombre JSON
  "currency":      "XOF"
}
```

`401` non authentifié · `404` compte introuvable.

> `amount` arrive comme un **nombre JSON**. En JavaScript, il transite par un `double`. Voir §5 pour la règle de manipulation obligatoire.

> **Devise.** `Account` crée tout compte client avec `Balance.zero("XOF")` **en dur**. `XOF` est aujourd'hui la seule devise existante dans le système. Le champ `currency` est donc constant en pratique — mais il doit être traité comme une donnée, jamais supposé.

### `POST /payments/cash-in` — dépôt

```jsonc
{
  "rawPin":        "1234",
  "amount":         50000,           // > 0.01
  "currency":      "XOF",            // exactement 3 caractères
  "paymentMethod": "ORANGE_MONEY"    // chaîne libre — voir §4
}
```

### `POST /payments/cash-out` — retrait

Corps strictement identique à `cash-in`.

### `POST /payments/transfer` — transfert P2P

```jsonc
{
  "rawPin":        "1234",
  "amount":         25000,
  "currency":      "XOF",
  "toPhoneNumber": "+2250708091011"  // non vide
}
```

### Réponse commune aux trois opérations

```jsonc
// 200
{
  "transactionId":     "7b2e...",
  "transactionNumber": "TRX-20260806-A3F91C2D",   // référence lisible, à afficher
  "state":             "COMPLETED",
  "amount":             50000,
  "currency":          "XOF"
}
```

Codes d'erreur possibles :

| Code | Cause | Traitement app |
|---|---|---|
| `400` | Validation de la requête | Marquer le champ fautif via `violations[]` |
| `401` | PIN invalide, **ou** jeton expiré | **Deux cas à distinguer** — voir §5.2 |
| **`404`** | **Destinataire introuvable** (`AccountNotFoundException`), compte de l'appelant introuvable | Message métier. **Ce n'est pas un `422`** |
| `422` | Solde insuffisant, compte bloqué ou suspendu, auto-transfert, devise incompatible, transition d'état interdite | Message métier, l'opération n'a pas eu lieu |
| **`500`** | **`ProviderException`** — non mappée par le `GlobalExceptionHandler` | Corps **non conforme à RFC 7807**. Traiter comme une issue incertaine |
| `503` | Conflit de concurrence après 5 tentatives, ou échec d'envoi de mail | Incident temporaire |

> **⚠️ Un transfert vers un numéro inconnu renvoie `404`, pas `422`.** `validateRecipientAndGetAccount` lève `AccountNotFoundException`, que le `GlobalExceptionHandler` mappe sur `NOT_FOUND`. Brancher ce cas sur `422` produirait un message générique là où l'utilisateur a besoin d'une réponse précise. C'est l'erreur de transfert la plus fréquente en usage réel.

> **⚠️ `ProviderException` n'est mappée nulle part.** `MobileMoneyProviderAdapter` la lève sur échec fournisseur (trois points de levée), et aucun `@ExceptionHandler` ne la couvre. Le résultat est un **`500` avec le corps d'erreur Spring par défaut** — ni `ProblemDetail`, ni `{status, error}`. C'est un **troisième format d'erreur** que le client doit tolérer. À signaler côté backend : c'est vraisemblablement un oubli.

> **`503` sur une opération monétaire est le cas le plus délicat.** Le serveur a épuisé 5 tentatives de verrouillage optimiste (`TransientPaymentException`). L'opération n'a très probablement pas eu lieu — mais l'app ne peut pas l'affirmer. Traitement imposé en §6.1. **Un `500` de fournisseur relève du même traitement** : issue incertaine, jamais un échec affirmé.

### `GET /payments/history`

Paramètres, tous optionnels sauf indication :

| Paramètre | Valeurs | Défaut |
|---|---|---|
| `type` | `CASH_IN` · `CASH_OUT` · `P2P_TRANSFER` | — |
| `state` | l'un des 11 états du §3.1 | — |
| `direction` | `INBOUND` · `OUTBOUND` | — |
| `from` | instant ISO-8601 UTC, ex. `2026-01-15T00:00:00Z` | — |
| `to` | idem | — |
| `page` | entier, base 0 | `0` |
| `size` | entier, maximum 100 | `20` |
| `detail` | booléen — inclut `stateHistory` | `false` |

```jsonc
// 200
{
  "transactions": [
    {
      "transactionId":     "7b2e...",
      "transactionNumber": "TRX-20260806-A3F91C2D",
      "type":              "P2P_TRANSFER",
      "direction":         "OUTBOUND",
      "state":             "COMPLETED",
      "amount":             25000,
      "currency":          "XOF",
      "paymentMethod":     "WALLET",
      "counterpart":       "+225070***011",   // null si CASH_IN / CASH_OUT
      "createdAt":         "2026-08-06T11:42:13Z",
      "stateHistory": [                         // null si detail=false
        { "oldState": null,         "newState": "INITIALIZED", "occurredAt": "2026-08-06T11:42:13Z" },
        { "oldState": "INITIALIZED","newState": "AUTHORIZED",  "occurredAt": "2026-08-06T11:42:13.412Z" },
        { "oldState": "AUTHORIZED", "newState": "CAPTURED",    "occurredAt": "2026-08-06T11:42:13.638Z" },
        { "oldState": "CAPTURED",   "newState": "COMPLETED",   "occurredAt": "2026-08-06T11:42:13.701Z" }
      ]
    }
  ],
  "page": 0, "size": 20, "totalElements": 143, "totalPages": 8, "hasNext": true
}
```

Notes de contrat :

- `from` et `to` mal formatés produisent un `400` avec un message explicite. **Toujours sérialiser en ISO-8601 UTC**, jamais en heure locale.
- `type`, `state` et `direction` invalides déclenchent une `IllegalArgumentException` côté serveur → `400`.
- `size > 100` est **effectivement rejeté** par `TransactionHistoryService` → `IllegalArgumentException` → `400`. Le plafond n'est pas indicatif.
- `counterpart` est **déjà masqué par le serveur** pour les transferts P2P. Ne pas le remasquer. Il vaut `null` pour dépôt et retrait.
  Règle de masquage exacte : `préfixe + 3 premiers chiffres + "***" + 3 derniers`. Un numéro local de 6 chiffres ou moins n'est **pas masqué du tout** — il est renvoyé en clair. Exemple : `+225` + `0708091011` → `+225070***011`.
- `detail=true` alourdit sensiblement la réponse. **Ne l'utiliser que pour l'écran de détail**, jamais pour la liste.
- `state` n'accepte **qu'une seule valeur**. Aucun filtrage multi-états côté serveur. Conséquence directe en §6.8.

---

## 3. Vocabulaire du domaine

### 3.1 États de transaction — les 11 valeurs exhaustives

| État | Terminal | Signification | Rendu app |
|---|---|---|---|
| `INITIALIZED` | non | Opération créée, rien de débité | En cours |
| `AUTHORIZED` | non | Fonds réservés chez le fournisseur | En cours |
| `CAPTURED` | non | Fonds prélevés, écriture au registre faite | En cours |
| `COMPLETED` | **oui** | Opération terminée avec succès | Succès |
| `SETTLEMENT_PENDING` | non | En attente de règlement interbancaire (T+1) | En cours |
| `SETTLED` | **oui** | Réglé côté fournisseur | Succès |
| `AUTHORIZATION_FAILED` | **oui** | Refus à l'autorisation | Échec |
| `CAPTURE_FAILED` | **oui** | Échec au prélèvement | Échec |
| `SETTLEMENT_FAILED` | **oui** | Échec au règlement | Échec |
| `FAILED` | **oui** | Échec générique | Échec |
| `REVERSED` | **oui** | Contrepassé par un administrateur | Annulé |

Regroupement imposé côté app — **trois familles, pas onze** :

```ts
type OutcomeGroup = 'pending' | 'success' | 'failed' | 'reversed';

const OUTCOME: Record<TxState, OutcomeGroup> = {
  INITIALIZED: 'pending',  AUTHORIZED: 'pending',  CAPTURED: 'pending',
  SETTLEMENT_PENDING: 'pending',
  COMPLETED: 'success',    SETTLED: 'success',
  AUTHORIZATION_FAILED: 'failed', CAPTURE_FAILED: 'failed',
  SETTLEMENT_FAILED: 'failed',    FAILED: 'failed',
  REVERSED: 'reversed',
};
```

L'utilisateur voit la famille. Il voit le détail des onze états **uniquement** sur la frise de l'écran de détail — c'est là que la richesse du backend devient une fonctionnalité plutôt qu'un bruit.

### 3.2 Autres énumérations

```
TransactionType   CASH_IN · CASH_OUT · P2P_TRANSFER
Direction         INBOUND · OUTBOUND
Role              CUSTOMER · ADMIN
UserStatus        PENDING · VERIFIED · SUSPENDED
```

---

## 4. Moyens de paiement

Le backend accepte `paymentMethod` comme **chaîne libre non validée**. La liste est donc définie et figée côté application. Elle ne doit pas être rendue configurable par l'utilisateur.

```ts
export const PAYMENT_METHODS = [
  { id: 'ORANGE_MONEY', label: 'Orange Money', brand: '#FF7900' },
  { id: 'MTN_MOMO',     label: 'MTN MoMo',     brand: '#FFCB05' },
  { id: 'MOOV_MONEY',   label: 'Moov Money',   brand: '#0066B3' },
  { id: 'WAVE',         label: 'Wave',         brand: '#1DC8FF' },
] as const;
```

`WALLET` apparaît en lecture dans l'historique pour les transferts P2P. Il n'est jamais envoyé par l'app.

---

## 5. Erreurs et montants

### 5.1 Format d'erreur — RFC 7807

Toute erreur gérée renvoie un `application/problem+json` :

```jsonc
{
  "type":     "about:blank",
  "title":    "Unprocessable Content",
  "status":    422,
  "detail":   "Insufficient funds for account ACC-20260806-A3F91C2D",
  "instance": "/payments/transfer"
}
```

Sur un `400` de validation, un champ supplémentaire apparaît :

```jsonc
{
  "status": 400,
  "detail": "Request validation failed",
  "violations": [
    { "field": "phoneNumber", "message": "Phone number must contain 8 to 15 digits" }
  ]
}
```

**Trois formats coexistent.** Le client HTTP doit tous les normaliser vers une erreur interne unique.

| # | Format | Producteur | Cas |
|---|---|---|---|
| 1 | `ProblemDetail` (RFC 7807) | `GlobalExceptionHandler` | Toute exception métier mappée |
| 2 | `{ "status": n, "error": "…" }` | Couche de sécurité Spring | `401` jeton absent/invalide, `403` rôle insuffisant |
| 3 | Corps d'erreur Spring par défaut | Aucun handler | `500` sur `ProviderException` et toute exception non mappée |

```jsonc
{ "status": 401, "error": "Unauthorized" }   // format 2 — jeton absent, invalide ou expiré
{ "status": 403, "error": "Forbidden" }      // format 2 — rôle insuffisant
```

```jsonc
// format 3 — ProviderException, non mappée
{ "timestamp": "2026-08-06T11:42:13.401+00:00", "status": 500,
  "error": "Internal Server Error", "path": "/payments/cash-in" }
```

Le parseur doit être défensif : un corps **non-JSON** ou vide reste possible, et ne doit jamais faire planter la couche HTTP.

### 5.2 Distinguer les deux `401`

Un `401` sur `/payments/**` a deux causes qui appellent des réactions opposées :

| Corps de la réponse | Cause | Réaction |
|---|---|---|
| `{"status":401,"error":"Unauthorized"}` | Jeton expiré ou invalide | Rafraîchir le jeton, rejouer **une** fois |
| `ProblemDetail` avec `detail: "Invalid PIN"` | PIN erroné | **Ne pas rafraîchir. Ne pas rejouer.** Afficher l'erreur de PIN |

Confondre les deux produit soit une boucle de rafraîchissement infinie, soit une déconnexion injustifiée. C'est le bug le plus probable de l'intercepteur HTTP.

### 5.3 Manipulation des montants — règle non négociable

`amount` est un `BigDecimal` côté Java, sérialisé en nombre JSON. Il devient un `double` en JavaScript.

> **Le backend n'impose aucune échelle.** `Amount` est un `record(BigDecimal value, String currency)` sans normalisation ni contrôle de nombre de décimales. La seule contrainte est `@DecimalMin("0.01")` au niveau de la requête. Un `amount` de `100.5` en `XOF` serait **accepté**, alors que le franc CFA n'a pas de subdivision.
>
> La règle « unité mineure entière » ci-dessous est donc une **convention client**, pas une garantie serveur. Deux conséquences : l'app n'émet jamais de décimale en `XOF`, et elle doit **tolérer** d'en recevoir une — arrondi à l'entier le plus proche à la réception, avec alerte en mode validation. À signaler côté backend : c'est un candidat naturel à un contrôle de domaine.

**Aucune arithmétique en virgule flottante n'est autorisée sur un montant.**

- À la réception : convertir immédiatement en entier de plus petite unité.
- Pour le XOF, la plus petite unité **est le franc** — `exponent = 0`, aucune décimale.
- Toute somme, différence ou comparaison se fait sur des entiers.
- La conversion vers un nombre décimal n'a lieu qu'au moment du formatage pour l'affichage.

```ts
export const CURRENCY = {
  XOF: { exponent: 0, symbol: 'F',  code: 'XOF' },
  XAF: { exponent: 0, symbol: 'FCFA', code: 'XAF' },
  EUR: { exponent: 2, symbol: '€',  code: 'EUR' },
} as const;

// 125000 XOF  →  "125 000 F"   (espace fine insécable U+202F comme séparateur)
```

Le backend ne pratique aucune conversion de devise. Un transfert dont la devise diffère de celle du compte lève `CurrencyMismatchException` → `422`.

---

## 6. Écarts du contrat et stratégies imposées

Ce que l'API ne fournit pas **aujourd'hui**, et comment l'application doit y répondre. Ces stratégies ne sont pas des suggestions.

> Chaque contournement de cette section est **temporaire** et doit être conditionné à un drapeau de `API_CAPABILITIES`, jamais câblé en dur, et étiqueté `CONTOURNEMENT(étape-N)` dans le code. Voir `09-api-evolution.md` §3 et §5, et l'inventaire complet en §4.

### 6.1 Aucune clé d'idempotence sur les paiements

`cash-in`, `cash-out` et `transfer` n'acceptent aucun en-tête `Idempotency-Key`. Deux requêtes identiques produisent **deux opérations distinctes et deux débits réels**.

> **Écart le plus important, et le premier à disparaître.** L'étape 4 du `ROADMAP.md` — la prochaine — introduit `idempotency_log` et les clés d'idempotence. L'app doit **déjà générer et envoyer** l'en-tête `Idempotency-Key` : le serveur l'ignore aujourd'hui, l'honorera demain, et seul le drapeau `API_CAPABILITIES.idempotency` changera. Voir `09-api-evolution.md` §3.

Stratégie imposée :

1. Un verrou en mémoire, hors du cycle de rendu React (`useRef` ou module singleton), interdit toute soumission concurrente.
2. Le bouton de confirmation passe en état verrouillé au premier appui et n'en sort que sur une réponse terminale.
3. Le geste de retour est intercepté et neutralisé pendant l'exécution.
4. **Un délai réseau n'est jamais réessayé automatiquement.** L'écran bascule en état « issue incertaine » qui invite à consulter l'historique.
5. En cas de `503`, ne jamais proposer un simple bouton « Réessayer ». Proposer « Vérifier dans l'historique » en action principale, et « Réessayer » en action secondaire, précédée d'un avertissement explicite.

### 6.2 Aucune résolution de bénéficiaire

Aucun endpoint ne permet d'obtenir le nom associé à un numéro de téléphone. **Il est impossible d'afficher « Vous envoyez à Kwame Mensah » avant confirmation.**

Stratégie imposée : le récapitulatif compense par une confirmation renforcée du numéro — affichage en très grande taille, groupé par blocs de chiffres, avec une case à cocher explicite « J'ai vérifié ce numéro ». Cette friction est délibérée : c'est la seule barrière entre l'utilisateur et un virement irréversible vers un inconnu.

Un numéro inexistant produit un **`404`** — pas un `422`. Le message doit être immédiatement compréhensible : « Aucun compte Kora n'est associé à ce numéro. »

### 6.3 Aucun endpoint de profil

Aucun `GET /me`. Les données de profil se reconstituent ainsi :

| Donnée | Source |
|---|---|
| `email`, `role`, identifiant | Claims du jeton d'accès, décodé localement |
| `fullName`, téléphone | Capturés à l'inscription, persistés localement en clair |
| `accountNumber` | `GET /payments/balance` |

Conséquence : un utilisateur qui se connecte sur un nouvel appareil **n'a pas son nom complet**. Repli imposé : afficher l'e-mail. Ne pas fabriquer de nom.

### 6.4 Aucun canal temps réel

Ni webhook, ni WebSocket, ni notification push. Une opération laissée en état `pending` ne signalera jamais son achèvement d'elle-même.

Stratégie imposée : sondage ciblé. Tant qu'au moins une opération visible est dans la famille `pending`, interroger `GET /payments/history?size=10` toutes les 5 secondes, avec un plafond de 12 tentatives (soit 1 minute), et arrêt immédiat dès que l'application passe en arrière-plan. Ne jamais sonder au-delà.

### 6.5 Aucun endpoint de déconnexion

Aucune invalidation serveur des jetons. La déconnexion est strictement locale : effacement du trousseau, purge du cache de requêtes, purge du stockage local, retour à l'écran d'accueil non authentifié.

Un jeton de rafraîchissement volé reste valide jusqu'à son expiration naturelle. C'est une limite connue du backend, à mentionner dans les notes de sécurité, pas à contourner côté client.

### 6.6 Endpoints d'administration

`POST /admin/payments/{txId}/reverse` exige le rôle `ADMIN`. **Hors périmètre de l'application cliente.** Ne pas l'appeler, ne pas prévoir d'interface. L'app doit en revanche savoir *afficher* une opération arrivée à l'état `REVERSED`.

### 6.7 Aucun accès unitaire à une transaction

Il n'existe **ni `GET /payments/{id}`, ni filtre par `transactionId`**. La seule façon d'obtenir le `stateHistory` d'une opération est de rappeler `GET /payments/history?detail=true` et de la retrouver dans la page.

Une recherche naïve dans « les 100 premières » est **fausse** : une opération ancienne n'y figure pas.

Stratégie imposée : la navigation vers le détail transporte **l'index de page d'où la ligne provient**, ainsi que les filtres actifs. L'écran de détail rejoue exactement cette requête avec `detail=true` et sélectionne par identifiant.

```ts
router.push({
  pathname: '/transaction/[id]',
  params: { id: tx.id, page: String(pageIndex), filters: serialize(activeFilters) },
});
// Détail : GET /payments/history?detail=true&page={page}&size=20&{filters}
//          puis .find(t => t.transactionId === id)
```

La requête reste bornée à une page, quelle que soit l'ancienneté de l'opération. Le `stateHistory` se charge en arrière-plan pendant que le reste de l'écran s'affiche depuis le cache de la liste.

Repli si l'opération est absente de la page rejouée — cas possible si une nouvelle opération a décalé la pagination entre-temps : élargir à `page-1` et `page+1`, puis abandonner avec un `ErrorState` proposant le retour à l'historique.

`CONTOURNEMENT(indéterminé)` — disparaît si `GET /payments/{id}` est ajouté. Drapeau : `API_CAPABILITIES.transactionById`.

### 6.8 Le filtre d'état n'accepte qu'une seule valeur

`TransactionFilter.state` est un `String` unique. **Aucun filtrage multi-états côté serveur.**

Conséquence sur l'interface : le regroupement en trois familles (`pending` / `success` / `failed`) du §3.1 est excellent pour l'**affichage**, mais il n'est **pas utilisable comme filtre serveur** — « En cours » recouvre quatre états distincts.

Un filtrage local de la famille sur la page chargée serait faux : il filtrerait 20 lignes sur 500, en affichant un total et une pagination incohérents.

Stratégie imposée pour la V1 : le filtre d'état est un **sélecteur à choix unique parmi les 11 états concrets**, envoyé tel quel au serveur. Les libellés restent lisibles (`Terminée`, `Autorisée`, `Échouée`…) via la table de traduction de `04-components.md`, mais un choix = un état = un filtre serveur exact.

Le filtre par famille est reporté et conditionné à `API_CAPABILITIES.multiStateFilter`. `CONTOURNEMENT(indéterminé)`.

### 6.9 Endpoints de support de test

`/test/**` est ouvert sans authentification et existe uniquement pour le profil de charge. Ne jamais l'appeler depuis l'application.