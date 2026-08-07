# Kora Mobile — Mode validation

Surface d'inspection permettant à l'application de servir de **harnais de validation du backend** pendant son développement.

Deux principes gouvernent ce document :

1. **Isolation totale.** Tout ce qui est décrit ici vit sous `src/devtools/`, derrière un unique drapeau. Aucune trace dans les écrans produit.
2. **Exclusion à la compilation.** En production, l'arbre entier est éliminé par secouage. Zéro octet dans le bundle, zéro branche à l'exécution.

```ts
// src/devtools/enabled.ts
export const DEV_MODE = __DEV__ || process.env.EXPO_PUBLIC_ENV === 'staging';
```

---

## 1. Pourquoi une app plutôt que k6 ou Postman

Le projet dispose déjà de tests de charge (`perf/`) et de tests de bout en bout côté serveur. Ils mesurent ce qu'un client synthétique peut mesurer. L'application révèle ce qu'ils ne peuvent pas voir :

| Ce que révèle uniquement un vrai client mobile |
|---|
| Le comportement réel sous coupure réseau au milieu d'un `POST` — pas une expiration simulée |
| Les états de transaction effectivement atteignables en usage normal, par opposition à ceux que le modèle autorise |
| Le délai réellement perçu entre `INITIALIZED` et `COMPLETED`, du point de vue de l'utilisateur |
| Les transitions produites par les tâches planifiées (`AuthorizationTtlExpiryJob`) sans action client |
| Les incohérences de contrat qu'aucun test serveur ne détecte, puisqu'il utilise les mêmes types |
| La justesse des messages d'erreur pour un humain, pas pour une assertion |
| Le comportement du rafraîchissement de jeton lors d'une expiration réelle en pleine action |
| Ce qui se passe quand deux appareils touchent le même compte en même temps |

Ce dernier point mérite d'être souligné : `PaymentService` gère le verrouillage optimiste avec 5 tentatives. Deux téléphones envoyant simultanément depuis le même compte constituent le seul moyen honnête de l'observer.

---

## 2. Accès

| Méthode | Contexte |
|---|---|
| Secousse de l'appareil | Ouvre le panneau depuis n'importe quel écran |
| Appui long 3 s sur le logo de l'accueil | Alternative sans capteur |
| Appui triple sur le numéro de version dans les réglages | Alternative discrète |

Le panneau s'ouvre en `Sheet` plein écran, avec des onglets. Il utilise le design system — la validation ne dispense pas du soin.

---

## 3. Inspecteur réseau

Le composant le plus utile du mode. Journal de toutes les requêtes, en mémoire, plafonné à 200 entrées.

| Colonne | Contenu |
|---|---|
| Méthode et chemin | `POST /payments/transfer` |
| Statut | Coloré selon la famille |
| Durée | Millisecondes, du départ à la réception |
| Corrélation | `X-Correlation-Id`, copiable |
| Horodatage | Heure locale à la milliseconde |

Le détail d'une entrée affiche :

- Requête complète — en-têtes et corps, **avec `rawPin` masqué en `****`, sans exception, même en développement**
- Réponse complète, corps brut non traduit
- `ProblemDetail` intégral quand il y en a un
- Bouton **`Copier en cURL`** — reproduction immédiate de l'appel côté backend
- Bouton **`Rejouer`** — uniquement sur les `GET`. Jamais sur un `POST` de paiement

L'inspecteur signale visuellement :

| Signal | Déclencheur |
|---|---|
| 🔴 | Toute réponse ≥ 400 |
| 🟠 | Durée > 1 000 ms |
| 🟡 | Rafraîchissement de jeton déclenché |
| 🔵 | Rejeu après rafraîchissement |

Le signal jaune est particulièrement précieux : il rend visible la mécanique du §3.4 de `06-architecture.md`, qui est autrement totalement invisible et donc impossible à déboguer.

---

## 4. Inspecteur de transactions

Vue brute, sans traduction ni regroupement, de ce que le backend renvoie réellement.

| Fonction | Détail |
|---|---|
| Liste brute | Les 11 états affichés tels quels, sans regroupement en familles |
| Frise complète | `stateHistory` avec les instants à la milliseconde |
| **Durée entre états** | Delta calculé entre chaque transition — révèle où le temps passe réellement |
| Suivi en direct | Sondage forcé à 2 s sur une transaction choisie, sans plafond |
| JSON brut | Réponse intégrale, copiable |

La colonne des durées inter-états est ce qui transforme cet écran en outil de diagnostic : elle rend visible que l'autorisation prend 200 ms et la capture 150 ms, conformément à `kora.provider.latency.*`, et fait ressortir immédiatement toute anomalie.

---

## 5. Détecteur de dérive de contrat

Mécanisme central du rôle de harnais. Exécuté au démarrage en mode validation.

```
1. GET /v3/api-docs
2. Comparaison avec les types locaux de src/types/api.ts
3. Rapport des écarts, par catégorie
```

| Catégorie d'écart | Gravité | Rendu |
|---|---|---|
| Endpoint disparu | 🔴 bloquant | Bannière rouge persistante |
| Champ obligatoire disparu | 🔴 bloquant | Bannière rouge persistante |
| Type d'un champ modifié | 🟠 avertissement | Entrée dans le panneau |
| Nouvel endpoint | 🔵 information | Entrée dans le panneau |
| Nouveau champ optionnel | 🔵 information | Entrée dans le panneau |
| Nouvelle valeur d'énumération | 🟠 avertissement | Entrée + alerte au premier rendu |

Le dernier cas est le plus précieux : un nouvel état de transaction apparaît dans l'énumération serveur, et l'app le signale **avant** qu'un utilisateur ne rencontre un libellé manquant.

Un écart bloquant n'empêche pas l'app de démarrer. Il l'annonce, bruyamment, et laisse continuer — le but est d'informer, pas de bloquer le travail.

---

## 6. Bascule d'environnement

Changement d'URL d'API sans recompilation.

| Environnement | URL |
|---|---|
| Local | `http://localhost:8081` |
| Émulateur Android | `http://10.0.2.2:8081` |
| Appareil physique | IP locale saisissable, mémorisée |
| Staging | selon déploiement |

Un changement d'environnement purge SecureStore, le cache de requêtes et le stockage local, puis relance l'app. Mélanger des jetons entre environnements produit des symptômes incompréhensibles.

Un test de connectivité ping `/actuator/health` et affiche le résultat avant validation.

---

## 7. Simulation d'échec

Le backend expose `kora.provider.behavior` (`SUCCESS`, `SLOW`, `FAILURE`…) sur `MobileMoneyProviderAdapter`. Le mode validation documente comment l'exploiter, et simule côté client ce que le serveur ne peut pas produire.

### Côté serveur — à la charge du développeur backend

```properties
kora.provider.behavior=SLOW
kora.provider.latency.authorize-ms=2000
```

Le panneau affiche la configuration active lue depuis `/actuator/env` quand elle est exposée, à titre de rappel.

### Côté client — injection locale

| Simulation | Effet |
|---|---|
| Latence forcée | Ajoute 0 à 5 000 ms sur toute réponse |
| Perte de réseau | Coupe le réseau au bout de N ms sur la **prochaine** requête |
| Réponse forcée | Impose un statut (`401`, `422`, `503`) sur le prochain appel d'un chemin donné |
| Expiration de jeton | Invalide le jeton d'accès pour tester le rafraîchissement |
| Expiration des deux jetons | Teste le parcours de session expirée |

La perte de réseau au milieu d'un `POST /payments/transfer` est **le scénario le plus important à valider de toute l'application**. Il est la seule façon d'observer ce que le backend fait vraiment quand un client disparaît en cours de transaction — et le seul moyen de vérifier que l'écran « issue incertaine » se comporte correctement.

---

## 8. Inspecteur de session

| Donnée | Rendu |
|---|---|
| Jeton d'accès | Décodé : `sub`, `email`, `role`, `iat`, `exp` |
| Expiration | Compte à rebours en direct, en rouge sous 60 s |
| Jeton de rafraîchissement | Décodé, avec sa propre expiration |
| Actions | Forcer un rafraîchissement · Invalider l'accès · Tout invalider |

Le compte à rebours visible transforme la validation du rafraîchissement de jeton, qui est autrement une attente aveugle de quinze minutes, en observation directe.

**Les jetons ne sont jamais affichés en clair, ni copiables.** Uniquement leurs claims décodés.

---

## 9. Journal des scénarios

Consignation manuelle des observations, avec export en Markdown.

```
[2026-08-06 14:22]  Transfert 25 000 XOF, réseau coupé à 800 ms
  Corrélation  a3f2-...
  Observé      Aucune réponse client. Transaction visible en COMPLETED
               dans l'historique 4 s plus tard.
  Conclusion   L'écran « issue incertaine » est le bon comportement.
               Confirme que l'étape 4 (idempotency) est nécessaire pour
               autoriser un rejeu sûr.
  Statut       ✅ conforme à l'attendu
```

L'export alimente directement le journal de compatibilité de `09-api-evolution.md` §7 et remonte au backend sous forme d'issue.

---

## 10. Écrans de démonstration

Deux écrans qui n'existent qu'en mode validation.

| Écran | Contenu |
|---|---|
| **Galerie du design system** | Tous les jetons, toutes les variantes typographiques, tous les composants dans tous leurs états, avec bascule de thème |
| **Banc d'animation** | Chaque moment signature de `03-motion-and-feel.md` §6, déclenchable à la demande, avec ralenti ×4 |

Le ralenti est ce qui permet de vérifier une chorégraphie de 650 ms autrement que par intuition.

La galerie n'est pas un gadget : elle est l'unique moyen de vérifier qu'un changement de token n'a rien cassé ailleurs, et elle est exigée par le lot 2 de `07-implementation-plan.md`.

---

## 11. Scénarios de validation prioritaires

Ce que l'application doit permettre de vérifier sur le backend, par ordre de valeur.

| # | Scénario | Ce qu'il valide côté backend |
|---|---|---|
| 1 | Deux appareils, transfert simultané depuis le même compte | Le verrouillage optimiste et ses 5 tentatives |
| 2 | Réseau coupé au milieu d'un `POST` de paiement | Le comportement serveur quand le client disparaît |
| 3 | Double appui rapide sur `Confirmer` | **L'absence d'idempotence — reproduit le double débit** |
| 4 | Transaction laissée en `AUTHORIZED` au-delà du TTL | `AuthorizationTtlExpiryJob` |
| 5 | Jeton expiré exactement pendant la saisie du PIN | La séquence rafraîchissement puis rejeu |
| 6 | OTP réutilisé après consommation | La suppression du magasin d'OTP |
| 7 | OTP utilisé à la 5ᵉ minute et 1 seconde | La limite exacte d'expiration |
| 8 | Transfert vers soi-même | `SelfTransferException` → `422` |
| 9 | Montant supérieur au solde, envoyé malgré le garde-fou client | `InsufficientFundsException` → `422` |
| 10 | Devise différente de celle du compte | `CurrencyMismatchException` → `422` |
| 11 | `paymentMethod` inconnu, injecté depuis le mode validation | **Confirme l'absence de validation de ce champ** |
| 12 | Historique paginé sur 500 opérations | La pagination et le temps de réponse réel |
| 13 | `from` postérieur à `to` | La gestion des plages incohérentes |
| 14 | `size=1000` | Le rejet effectif au-delà de 100 → `400` |
| 15 | Provider en mode `SLOW` puis coupure client | Le comportement combiné |
| 16 | Provider en mode `FAILURE` | **`ProviderException` non mappée → `500` au corps non conforme** |
| 17 | Transfert vers un numéro inexistant | Confirme que c'est un `404`, pas un `422` |
| 18 | Montant `100.5` en `XOF`, injecté depuis le mode validation | **Confirme l'absence de contrôle d'échelle sur `Amount`** |
| 19 | Jeton d'**accès** envoyé à `/auth/refresh` | **Confirme qu'il est accepté** — les deux types ne sont pas distingués |
| 20 | Compte créé, OTP jamais vérifié, puis `/auth/login` | Confirme que le statut est `VERIFIED` dès l'inscription |
| 21 | Détail d'une opération figurant en page 12 de l'historique | Le rejeu de page du contrat §6.7 |

Les scénarios **3, 11, 16, 18, 19, 20** sont des **validations d'absence** : ils confirment un manque ou un comportement non intentionnel du backend. Ils doivent être rejoués après chaque étape concernée pour vérifier qu'ils **échouent désormais** — c'est leur valeur de non-régression.

Les scénarios 16, 18, 19 et 20 méritent une remontée backend immédiate : ce sont vraisemblablement des oublis, pas des décisions.

---

## 12. Exclusion en production

```js
// babel.config.js — production
plugins: [
  ['transform-remove-console'],
  ['transform-define', { 'process.env.EXPO_PUBLIC_ENV': 'production' }],
]
```

Tout accès aux devtools passe par un import dynamique conditionné par `DEV_MODE`, ce qui garantit l'élimination de l'arbre entier au secouage.

Vérification obligatoire au lot 10 : l'analyse du bundle de production ne contient **aucun module** de `src/devtools/`.