# Kora Mobile — Co-évolution avec l'API

**Le backend est en développement actif.** L'application mobile n'est pas construite contre un contrat figé : elle est construite contre un contrat qui bouge, et son rôle premier est de **valider le comportement du backend à chaque étape** jusqu'à la version finale.

Ce document définit comment l'app suit cette évolution sans être réécrite à chaque étape.

---

## 1. Le double rôle de l'application

| Rôle | Ce que ça implique |
|---|---|
| **Harnais de validation** (aujourd'hui) | Exercer chaque endpoint dans des conditions réelles — réseau mobile, concurrence, interruptions — et rendre visible ce que le backend fait vraiment. Un test k6 mesure la latence ; l'app révèle les trous de contrat |
| **Produit final** (à terme) | L'app de production, au niveau d'exécution défini dans `00-requirements.md` |

Ces deux rôles ne s'opposent pas, à une condition : **la surface de validation est isolée dans un mode dédié**, jamais dispersée dans le code produit. Voir `10-validation-mode.md`.

### Ce que ça change dans les priorités

| Priorité | Rang aujourd'hui | Rang à terme |
|---|---|---|
| Exactitude du contrat, visibilité des erreurs | **1** | 3 |
| Couverture des cas limites et des échecs | **2** | 2 |
| Raffinement de l'UI/UX | 3 | **1** |

L'inversion est délibérée. Une animation ratée se corrige en une heure. Une hypothèse fausse sur le comportement du backend, enfouie dans dix écrans, coûte une semaine.

Cela ne dispense **jamais** d'appliquer le design system et les lois du mouvement : ils sont le socle, écrits une fois au lot 2 et 3, et ils ne bougent plus. Ce qui est repriorisé, c'est le raffinement des sept moments signature — pas les fondations.

---

## 2. État du backend au moment de la rédaction

Lecture directe du code, croisée avec `ROADMAP.md`.

| Étape | Sujet | Statut | Surface API exposée |
|---|---|---|---|
| 0 | Ledger double entrée, comptes | ✅ fait | `/auth/*`, `/payments/balance` |
| 1 | Cycle de vie du paiement, machine à états | ✅ fait | 11 états, `stateHistory` sur `/payments/history` |
| 2 | Monolithe modulaire | ✅ fait | — interne |
| 3 | Migration hexagonale | ✅ fait | — interne, `ports/adapters` en place |
| **4** | **Idempotency & réalité réseau** | 🔜 **suivante** | **`Idempotency-Key` attendu sur `/payments/*`** |
| 5 | Outbox + événements internes | ⬜ | — interne, peut ouvrir la voie au push |
| 6 | Moteur de réconciliation | ⬜ | Nouveaux états visibles, `/reconciliation/*` (admin) |
| 7 | Extraction microservice | ⬜ | — transparent pour le client |
| 8 | Multi-provider + risque/vélocité | ⬜ | **Nouveaux rejets : limites de vélocité, revue manuelle** |
| 9 | Conteneurisation, K8s | ⬜ | — transparent |
| 10 | Observabilité, KPI | ⬜ | — transparent |

Signaux présents dans le code confirmant l'état actuel :

- `PaymentService.MAX_RETRY_ATTEMPTS = 5` avec recul et gigue, levant `TransientPaymentException` → `503`. C'est un **palliatif de concurrence**, pas de l'idempotence.
- `ReversePaymentCommand` porte déjà un `correlationId` — la corrélation existe côté administration, pas encore côté client.
- `MobileMoneyProviderAdapter` expose `kora.provider.behavior` (`SUCCESS`, `SLOW`, …) : un **levier de simulation d'échec directement exploitable par l'app en validation**.
- `AuthorizationTtlExpiryJob` existe : des transactions peuvent changer d'état **sans action du client**. L'app doit le vérifier.

---

## 3. Impact attendu de chaque étape à venir

### Étape 4 — Idempotency *(prochaine, impact majeur)*

C'est le changement qui affecte le plus l'application.

| Aujourd'hui | Après l'étape 4 |
|---|---|
| Aucune clé d'idempotence | En-tête `Idempotency-Key` sur `/payments/*` |
| Protection double débit **100 % client** | Protection partagée, garantie serveur |
| `503` → écran « issue incertaine », **aucun rejeu** | `503` → rejeu **sûr** avec la même clé |
| Reprise automatique interdite sur les paiements | Reprise automatique redevient légitime |

**Préparation imposée dès maintenant** — le code doit être écrit pour absorber ce changement sans refonte :

```ts
// src/features/payments/api.ts
// La clé est générée et transportée DÈS AUJOURD'HUI, même si le serveur l'ignore.
// Le jour où l'étape 4 atterrit, seul le drapeau change.
await request(path, {
  method: 'POST',
  body: payload,
  headers: { 'Idempotency-Key': idempotencyKey },   // ignoré aujourd'hui, honoré demain
});
```

La clé est **générée à l'entrée dans l'étape de récapitulatif**, pas au moment de l'envoi. Elle survit ainsi à un rejeu manuel : c'est ce qui rendra le rejeu sûr le jour venu.

Le drapeau de bascule vit dans un seul endroit :

```ts
// src/lib/capabilities.ts
export const API_CAPABILITIES = {
  idempotency:      false,   // étape 4 → Idempotency-Key honorée
  velocityLimits:   false,   // étape 8 → nouveaux motifs de rejet
  realtimeEvents:   false,   // étape 5+ → supprime le sondage
  transactionById:  false,   // GET /payments/{id}
  multiStateFilter: false,   // filtre state multi-valeurs
  beneficiaryLookup:false,   // résolution nom ↔ téléphone
  logout:           false,   // invalidation serveur des jetons
  userProfile:      false,   // GET /me
} as const;
```

Tout contournement documenté en `01-api-contract.md` §6 est conditionné à ce drapeau. **Aucun contournement n'est câblé en dur.**

### Étape 5 — Outbox et événements

Impact client : potentiellement nul à court terme, mais ouvre la voie aux notifications push. Si un canal de notification apparaît, le sondage du contrat §6.4 devient superflu — c'est pourquoi il est isolé dans un seul hook, `usePendingTransactionPolling`.

### Étape 6 — Réconciliation

De nouveaux états peuvent devenir visibles côté client (écart détecté, en revue manuelle). L'app doit déjà **tolérer un état inconnu** :

```ts
export function outcomeOf(state: string): OutcomeGroup {
  return OUTCOME[state as TxState] ?? 'pending';   // repli sûr, jamais un crash
}
```

Un état non répertorié se rend en famille `pending`, avec son libellé brut, et **déclenche une alerte en mode validation**. Il ne fait jamais planter l'écran.

### Étape 8 — Risque et vélocité *(second impact majeur)*

Nouveaux motifs de rejet : plafond journalier, plafond horaire, blocage dur, mise en revue manuelle. Vraisemblablement un `429` ou un `422` avec un `detail` spécifique.

Préparation :

- Le mappage des messages métier (`05-screens.md` §4.6) est une **table de données**, pas une cascade de `if`. Ajouter un motif = ajouter une ligne.
- Un rejet inconnu affiche le `detail` du `ProblemDetail` tel quel plutôt qu'un message générique — l'utilisateur préfère une phrase imparfaite à « Une erreur est survenue ».
- Prévoir dès maintenant l'état « en revue manuelle » comme quatrième issue possible d'un paiement, à côté de succès / en cours / échec / incertain.

---

## 4. Inventaire des contournements actifs

Un contournement, un drapeau, une section du contrat. Cette table est le pendant documentaire du `grep CONTOURNEMENT` sur la base de code — si les deux divergent, l'un des deux ment.

| Réf. contrat | Contournement | Drapeau | Levée attendue |
|---|---|---|---|
| §6.1 | Verrou client à 4 couches, aucun rejeu automatique | `idempotency` | **étape 4** |
| §6.2 | Case « J'ai vérifié ce numéro » à la place du nom du destinataire | `beneficiaryLookup` | indéterminé |
| §6.3 | Profil reconstruit depuis les claims + saisie locale | `userProfile` | indéterminé |
| §6.4 | Sondage borné 5 s × 12 | `realtimeEvents` | étape 5+ |
| §6.5 | Déconnexion purement locale | `logout` | indéterminé |
| §6.7 | Rejeu de la page d'origine pour l'écran de détail | `transactionById` | indéterminé |
| §6.8 | Filtre d'état à choix unique | `multiStateFilter` | indéterminé |
| §4 | Liste d'opérateurs figée côté client | `velocityLimits` | étape 8 |

---

## 5. Règles d'anti-fragilité

Sept règles qui permettent à l'app de survivre à un contrat mouvant. Elles sont contraignantes.

### R1 — Une seule couche de traduction

Aucun type de l'API ne remonte au-delà de `src/features/*/api.ts`. Chaque fonction traduit la réponse brute vers un type de domaine interne.

```
ApiTransactionItem  →  [mapper]  →  Transaction   (type interne, stable)
```

Quand le backend renomme un champ, **un seul fichier change**. Sans cette couche, un renommage touche trente composants.

### R2 — Tolérance à l'inconnu partout

| Situation | Comportement imposé |
|---|---|
| État de transaction inconnu | Famille `pending`, libellé brut, alerte en mode validation |
| Type de transaction inconnu | Icône générique, libellé brut |
| Champ absent | Valeur de repli, jamais un plantage |
| Champ supplémentaire | Ignoré silencieusement |
| Code d'erreur inconnu | Affichage du `detail`, action `Réessayer` |

Les schémas `zod` valident en mode **permissif** (`.passthrough()`), jamais en mode strict. Un contrat en développement gagne des champs ; les rejeter briserait l'app à chaque livraison backend.

### R3 — Les capacités pilotent le comportement

Aucune condition sur une version d'API. Uniquement sur `API_CAPABILITIES`. Un seul fichier à modifier quand une étape atterrit.

### R4 — Chaque contournement est étiqueté

```ts
// CONTOURNEMENT(étape-4) : aucune idempotence serveur — verrou client uniquement.
// Retirer la reprise manuelle et réactiver la reprise auto quand API_CAPABILITIES.idempotency = true.
// Réf : 01-api-contract.md §6.1
```

Convention : `CONTOURNEMENT(étape-N)`. Un `grep` sur la base de code produit à tout moment la liste de la dette liée au backend.

### R5 — Le contrat est typé une seule fois

`src/types/api.ts` est la transcription unique de `01-api-contract.md`. Aucune structure de réponse n'est redéclarée ailleurs.

À terme, générer ces types depuis `/v3/api-docs` avec `openapi-typescript`. Tant que le contrat bouge vite, la transcription manuelle reste préférable : elle force à *lire* le diff plutôt qu'à le subir.

### R6 — Détection de dérive de contrat

Le mode validation compare `/v3/api-docs` aux types locaux et signale les écarts au démarrage. Voir `10-validation-mode.md` §5.

C'est le mécanisme qui transforme l'app en véritable harnais : le développeur backend livre un changement, et l'app le signale **avant** qu'un écran ne casse.

### R7 — Les tests d'intégration figent le comportement observé

Chaque comportement backend validé manuellement devient un scénario MSW. Quand le backend change, le test échoue, et l'écart est explicite plutôt que découvert en production.

---

## 6. Protocole de synchronisation

À exécuter à chaque livraison backend touchant `web/api/**` ou un état du domaine.

```
1. Récupérer /v3/api-docs, lancer le détecteur de dérive
2. Si écart → mettre à jour 01-api-contract.md AVANT tout code
3. Mettre à jour src/types/api.ts et les mappers de la couche R1
4. Lancer la suite d'intégration MSW → les échecs localisent l'impact
5. Si une capacité est débloquée → basculer le drapeau, retirer le CONTOURNEMENT correspondant
6. Rejouer le parcours de validation manuelle de 08-quality-bar.md §10
7. Consigner dans le journal de compatibilité ci-dessous
```

**La règle d'or : `01-api-contract.md` se met à jour avant le code, jamais après.** Un contrat de documentation en retard sur le code est pire qu'une absence de documentation — il ment avec autorité.

---

## 7. Journal de compatibilité

Tenu à jour à chaque synchronisation. Une ligne par livraison backend ayant un impact client.

| Date | Livraison backend | Impact mobile | Action |
|---|---|---|---|
| 2026-08-06 | `afd4f78` — Flyway V1 | Aucun | Contrat initial transcrit |
| 2026-08-06 | — revue du contrat contre le code | 8 corrections | Formats d'identifiants, masquage, `404` destinataire, `500` non mappé, statut `VERIFIED`, absence d'échelle sur `Amount`, plafond `size`, filtre d'état unique |
| | | | |

### Anomalies backend à remonter

Relevées lors de la revue du 2026-08-06. Ce sont des observations, pas des blocages — l'app s'en accommode aujourd'hui.

| # | Observation | Gravité |
|---|---|---|
| 1 | `ProviderException` n'est mappée par aucun `@ExceptionHandler` → `500` au corps non conforme à RFC 7807 | moyenne |
| 2 | `/auth/refresh` ne distingue pas jeton d'accès et jeton de rafraîchissement | faible |
| 3 | `User.create` attribue `VERIFIED` d'emblée ; `UserStatus.PENDING` n'est jamais utilisé | faible |
| 4 | `Amount` n'impose aucune échelle — `100.5 XOF` serait accepté | moyenne |
| 5 | `paymentMethod` n'est pas validé — toute chaîne est acceptée | faible |
| 6 | Aucun endpoint de renvoi d'OTP ; le renvoi passe par `/auth/login` | faible |

---

## 8. Ce que la V1 mobile fige, et ce qu'elle laisse ouvert

| Figé — ne bougera plus | Ouvert — évoluera avec le backend |
|---|---|
| Design system, jetons de mouvement | Liste des états de transaction |
| Bibliothèque de composants | Motifs de rejet d'un paiement |
| Architecture, arborescence | Politique de reprise sur `/payments/*` |
| Parcours et navigation | Stratégie de suivi des opérations en cours |
| Traitement des montants et des dates | Contenu du profil utilisateur |
| Modèle de session et de sécurité | Filtres et facettes de l'historique |

Le socle visuel et cinétique est écrit **une fois**, aux lots 2 et 3, et il est indépendant du backend. Il ne doit jamais être différé sous prétexte que l'API bouge : c'est précisément la partie du travail que l'instabilité du contrat n'affecte pas.