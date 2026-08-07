# Kora — Spécification des écrans

Chaque écran est spécifié avec sa disposition, ses états, ses transitions et ses cas limites.

Rappel de la règle 5 du `README.md` : **tout écran implémente ses quatre états** — `loading`, `empty`, `error`, `success`. L'absence d'un état est un travail incomplet.

---

## 0. Carte de navigation

```
(gate)                          vérification du jeton, hors navigation visible
│
├── (public)
│   ├── onboarding              3 volets, une seule fois
│   ├── login                   e-mail + PIN
│   ├── register                4 étapes
│   └── verify-otp              partagé login / register
│
└── (app)                       protégé par jeton
    ├── (tabs)
    │   ├── home                solde, actions, dernières opérations
    │   ├── activity            historique complet + filtres
    │   └── settings            profil et réglages
    │
    ├── deposit/                [amount] → [method] → [review] → [pin] → [result]
    ├── withdraw/               [amount] → [method] → [review] → [pin] → [result]
    ├── send/                   [recipient] → [amount] → [review] → [pin] → [result]
    └── transaction/[id]        détail + frise des états
```

Les parcours monétaires sont présentés en **pile modale plein écran**, hors des onglets. Ils masquent la barre d'onglets : une opération en cours est un tunnel, on n'en sort pas latéralement.

---

## 1. Portail de session — `(gate)`

Aucun rendu visible autre que l'écran de lancement natif, prolongé.

```
1. Lire les jetons depuis SecureStore
2. Aucun jeton                     → (public)/onboarding ou /login selon le premier lancement
3. Jeton d'accès valide            → (app)/home
4. Accès expiré, rafraîchissement valide
                                   → POST /auth/refresh
                                      succès → (app)/home
                                      échec  → purge + (public)/login
5. Les deux expirés                → purge + (public)/login
```

Contrainte : l'écran de lancement natif reste affiché jusqu'à la résolution. **Aucun clignotement d'un écran intermédiaire.** `expo-splash-screen` avec `preventAutoHideAsync()`.

Budget : 400 ms sans appel réseau, 1,5 s avec rafraîchissement.

---

## 2. Authentification

### 2.1 `onboarding` — 3 volets

| Volet | Message |
|---|---|
| 1 | **Votre argent, sous contrôle.** Envoyez, recevez, suivez — en quelques secondes |
| 2 | **Chaque étape, visible.** Suivez vos opérations état par état, en temps réel |
| 3 | **Sécurisé par conception.** PIN, double authentification, chiffrement matériel |

| Élément | Spécification |
|---|---|
| Disposition | Illustration plein écran, texte ancré en bas, pagination par points |
| Navigation | Balayage horizontal, `Gesture.Pan()`, ressort `gesture` |
| Points | Le point actif s'allonge de 8 à 24 dp en `snappy` |
| Parallaxe | L'illustration se déplace à 0,4× la vitesse du texte |
| Action | `Commencer` en `primary` sur le dernier volet, `Passer` en `ghost` en haut à droite |
| Persistance | Vu une seule fois, marqué dans le stockage local |

### 2.2 `login`

| Élément | Spécification |
|---|---|
| Étape 1 | Champ e-mail, `TextField`, `keyboardType="email-address"`, validation à la sortie |
| Étape 2 | `PinPad` plein écran, titre « Entrez votre PIN » |
| Transition | Glissement horizontal `standard` entre les deux étapes |
| Biométrie | Si activée et un e-mail est mémorisé, proposée d'emblée à l'étape 2 |
| Soumission | `POST /auth/login` → succès → `verify-otp` |

**Traitement des erreurs — point sensible.** Le backend renvoie `404` pour un e-mail inconnu et `401` pour un PIN erroné. L'application affiche **le même message dans les deux cas** : « E-mail ou PIN incorrect ». Propager la distinction offrirait un oracle d'énumération de comptes.

| Cas | Comportement |
|---|---|
| `401` ou `404` | Secousse du `PinPad`, `haptic.error`, message unique, PIN réinitialisé |
| `503` | `ErrorState` « Service temporairement indisponible », bouton `Réessayer` |
| Hors ligne | `OfflineBanner`, bouton de soumission désactivé |

### 2.3 `register` — 4 étapes

Une question par écran. Jamais de formulaire empilé : le taux d'abandon d'un formulaire mobile à 5 champs est double de celui d'un parcours en 4 étapes.

| Étape | Champ | Validation |
|---|---|---|
| 1 | Nom complet | non vide |
| 2 | E-mail | format e-mail |
| 3 | Indicatif + téléphone | `^\+\d{1,4}$` et `^\d{8,15}$` |
| 4 | PIN puis confirmation | 4 à 8 chiffres, les deux saisies identiques |

| Élément | Spécification |
|---|---|
| Progression | Barre fine en tête, remplissage en `standard` |
| Transition | Glissement horizontal, retour possible à tout moment |
| Validation | À la sortie du champ, jamais à chaque frappe |
| Erreur de champ | Le champ vibre latéralement, bordure `danger.500`, message en dessous |
| Étape 4 | Deux `PinPad` successifs. Discordance → secousse, retour à la première saisie |
| Soumission | `POST /auth/register` → `verify-otp` |

`409` sur l'e-mail : retour direct à l'étape 2, champ en erreur, message « Cet e-mail est déjà utilisé », avec un lien `Se connecter`.

### 2.4 `verify-otp`

Écran partagé entre inscription et connexion.

| Élément | Spécification |
|---|---|
| Titre | « Vérifiez votre e-mail » |
| Sous-titre | « Code envoyé à a•••@kora.ci » — e-mail partiellement masqué |
| Saisie | `OtpInput`, 6 cellules |
| Action d'aide | **`Ouvrir ma boîte mail`** en `secondary` — essentiel, le code arrive par e-mail |
| Renvoi | « Renvoyer le code » avec compte à rebours de 30 s |
| Expiration | Compte à rebours de 5 min affiché ; à zéro, les cellules se désactivent et l'action de renvoi devient primaire |
| Soumission | Automatique à la 6ᵉ saisie, sans bouton |

| Cas | Comportement |
|---|---|
| Succès | Jetons stockés, `haptic.success`, transition vers `home` |
| `401` | Secousse, cellules vidées, « Code incorrect ou expiré », 3 essais avant blocage de 60 s |
| Renvoi | Rappelle `POST /auth/login` ou `/auth/register` selon l'origine du parcours |

> **Note.** Le renvoi passe par le même endpoint que l'origine, puisqu'aucun endpoint de renvoi d'OTP n'existe. Sur un parcours d'inscription, un second `register` renverrait `409`. Le parcours d'inscription doit donc, pour le renvoi, appeler `POST /auth/login` avec l'e-mail et le PIN conservés en mémoire volatile pour la durée du parcours.

---

## 3. Accueil — `(tabs)/home`

L'écran le plus consulté. Il doit être lisible en moins de deux secondes.

```
┌─────────────────────────────────────┐
│  Bonjour, Aminata          ⚙        │   ← navigation, se transforme au défilement
│                                     │
│  ┌───────────────────────────────┐  │
│  │  Solde disponible        👁    │  │   ← BalanceHero
│  │                               │  │
│  │  125 000 F                    │  │   ← displayXl, compteur animé
│  │  ACC-20260806-A3F91C2D        │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌────────┐ ┌────────┐ ┌────────┐  │
│  │   ↓    │ │   ↑    │ │   ➤    │  │   ← ActionTile ×3
│  │Déposer │ │Retirer │ │Envoyer │  │
│  └────────┘ └────────┘ └────────┘  │
│                                     │
│  Activité récente          Tout →   │
│  ┌───────────────────────────────┐  │
│  │ ╭─╮ Orange Money   + 50 000 F │  │
│  │ ╭─╮ +225070***011 − 25 000 F  │  │   ← 5 TransactionRow
│  │ ...                           │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

| Aspect | Spécification |
|---|---|
| Données | `GET /payments/balance` + `GET /payments/history?size=5` en parallèle |
| Chargement | Squelettes de forme identique, dès 200 ms |
| Défilement | `Animated.ScrollView`, compression de la carte selon §6.6 de `03-motion-and-feel.md` |
| Rafraîchissement | Tirer-pour-rafraîchir personnalisé, `haptic.select` au franchissement du seuil |
| Retour au premier plan | Revalidation automatique des deux requêtes |
| Salutation | Nom complet issu du stockage local ; repli sur l'e-mail si absent (voir contrat §6.3) |
| Masquage du solde | Bascule locale, persistée, `haptic.select` |
| Entrée | Cascade de 40 ms : carte, tuiles, titre de section, lignes |

| État | Rendu |
|---|---|
| Chargement | `SkeletonBalance` + 5 `SkeletonRow` |
| Aucune opération | La section d'activité affiche un `EmptyState` compact : « Aucune opération pour l'instant » + `Faire un dépôt` |
| Échec du solde | La carte affiche `--- F` avec une icône de rafraîchissement ; les tuiles restent actives |
| Échec de l'historique | Seule la section d'activité passe en erreur ; la carte reste intacte |
| Hors ligne | `OfflineBanner`, données de cache affichées avec la mention « Mis à jour il y a X min » |

**Règle d'isolation** : un échec sur l'une des deux requêtes ne dégrade jamais l'autre section. Deux `useQuery` indépendantes, deux frontières d'erreur.

---

## 4. Parcours monétaires

Les trois parcours partagent la même charpente. Seule l'étape de destination diffère.

```
Déposer   montant → opérateur → récapitulatif → PIN → résultat
Retirer   montant → opérateur → récapitulatif → PIN → résultat
Envoyer   destinataire → montant → récapitulatif → PIN → résultat
```

### 4.1 Étape « montant »

| Aspect | Spécification |
|---|---|
| Composant | `AmountKeypad` plein écran |
| Plafond | Retrait et transfert : `maxMinor` = solde disponible. Dépôt : aucun plafond |
| Affichage complémentaire | « Solde après opération : 75 000 F », mis à jour en direct |
| Dépassement | Secousse, montant en `danger.500`, action `Continuer` désactivée |
| Montants rapides | `5 000` · `10 000` · `25 000`, en puces au-dessus du pavé |
| Action | `Continuer` en `ActionBar`, désactivée tant que le montant est nul |
| Fermeture | `×` en haut à gauche, confirmation demandée si un montant est saisi |

### 4.2 Étape « destinataire » — transfert uniquement

| Aspect | Spécification |
|---|---|
| Saisie | `PhoneField`, indicatif `+225` par défaut |
| Récents | Jusqu'à 5 destinataires récents, stockés localement, en pastilles horizontales |
| Contacts | Import depuis le carnet d'adresses *(P1)* |
| Validation | Format uniquement — **l'existence du compte n'est vérifiable qu'à la soumission** (voir contrat §6.2) |
| Action | `Continuer`, désactivée tant que le format est invalide |

### 4.3 Étape « opérateur » — dépôt et retrait

`MethodPicker` sur la liste figée du §4 du contrat. Le dernier opérateur utilisé est présélectionné.

### 4.4 Étape « récapitulatif »

Dernier écran avant le point de non-retour. Sa densité est délibérément faible : une seule information par ligne.

```
┌─────────────────────────────────────┐
│  ←   Confirmer l'envoi              │
│                                     │
│           25 000 F                  │   ← displayMd, centré
│                                     │
│  ┌───────────────────────────────┐  │
│  │ Destinataire                  │  │
│  │ +225 07 08 09 10 11           │  │   ← titleMd, groupé par blocs de 2
│  │                               │  │
│  │ Frais              Gratuit    │  │
│  │ Total débité       25 000 F   │  │
│  │ Solde après       100 000 F   │  │
│  └───────────────────────────────┘  │
│                                     │
│  ☐ J'ai vérifié ce numéro           │   ← transfert uniquement, obligatoire
│                                     │
│  [        Confirmer        ]        │
└─────────────────────────────────────┘
```

| Aspect | Spécification |
|---|---|
| Montant | `displayMd`, centré, entrée en `bouncy` |
| Numéro du destinataire | `titleMd`, groupé par blocs de deux chiffres, sur sa propre ligne |
| Frais | `Gratuit` en V1 — le backend n'expose aucune commission |
| Case de vérification | **Transfert uniquement, obligatoire.** Compense l'absence de résolution de bénéficiaire |
| Action | `Confirmer` désactivé tant que la case n'est pas cochée |
| Retour | Autorisé, l'état du parcours est conservé |

### 4.5 Étape « PIN »

| Aspect | Spécification |
|---|---|
| Composant | `PinPad`, titre « Entrez votre PIN pour confirmer » |
| Rappel du montant | Affiché en `titleMd` au-dessus des pastilles |
| Haptique | `commit` à la complétion du PIN |
| Sécurité | Capture d'écran bloquée, PIN en mémoire volatile uniquement |
| Verrouillage | Le verrou anti-double-soumission s'arme **avant** l'appel réseau (contrat §6.1) |
| Retour | **Neutralisé** pendant l'exécution de la requête |

Séquence à la complétion :

```
1. haptic.commit
2. Armer le verrou (useRef, hors cycle de rendu)
3. Basculer le PinPad en état de chargement — pastilles pulsantes
4. POST /payments/{cash-in|cash-out|transfer}
5. Effacer le PIN de la mémoire, quelle que soit l'issue
6. Router vers le résultat, remplacer l'entrée de pile (pas d'empilement)
```

### 4.6 Étape « résultat »

Quatre variantes selon l'issue. Toutes en plein écran, toutes sans possibilité de revenir en arrière dans le parcours.

#### Succès — état terminal `COMPLETED` ou `SETTLED`

| Aspect | Spécification |
|---|---|
| Séquence | Chorégraphie intégrale du §6.4 de `03-motion-and-feel.md`, 650 ms |
| Contenu | Coche animée · montant · destinataire ou opérateur · nouveau solde |
| Actions | `Terminé` en `primary`, `Voir le détail` en `ghost` |
| Effets de bord | Invalidation de `balance` et `history`, ajout du destinataire aux récents |

#### En cours — état intermédiaire

| Aspect | Spécification |
|---|---|
| Icône | Horloge pulsante en `pending.500`, pas de coche |
| Titre | « Opération en cours » |
| Description | « Votre opération a été prise en compte et sera finalisée sous peu. » |
| Haptique | `warning` |
| Suivi | Sondage selon le contrat §6.4 — 5 s d'intervalle, 12 tentatives maximum |
| Actions | `Terminé`, `Suivre l'opération` → écran de détail |

**Ne jamais présenter un état intermédiaire comme un succès.** L'écart entre `CAPTURED` et `COMPLETED` est exactement le genre de raccourci qui produit une réclamation client.

#### Échec — `404`, `422`, ou état terminal d'échec

| Aspect | Spécification |
|---|---|
| Icône | Croix en `status.failed.fg` |
| Titre | Message métier traduit — jamais le `detail` brut |
| Actions | `Réessayer` (retour au récapitulatif, montant conservé), `Fermer` |
| Haptique | `error` |

Traductions imposées. **Le statut fait partie de la clé de correspondance** — un destinataire inconnu arrive en `404`, pas en `422` (contrat §2) :

| Statut | `detail` du backend | Message affiché |
|---|---|---|
| `422` | `Insufficient funds…` | Solde insuffisant pour cette opération |
| `422` | `Self transfer…` | Vous ne pouvez pas vous envoyer de l'argent |
| `422` | `Currency mismatch…` | Devise incompatible avec votre compte |
| `422` | `Account blocked…` | Votre compte est bloqué. Contactez le support |
| `422` | `…Recipient account is suspended…` | Le compte du destinataire est suspendu |
| `422` | `Account suspended…` | Votre compte est suspendu |
| **`404`** | `No account found for phone…` | **Aucun compte Kora n'est associé à ce numéro** |
| `404` | `Account not found for customer…` | Votre compte est introuvable. Contactez le support |

La correspondance est une **table de données** indexée par `(statut, motif)`, jamais une cascade de `if` — l'étape 8 y ajoutera les rejets de vélocité. Un couple inconnu affiche le `detail` du `ProblemDetail` tel quel, pas un message générique.

#### Issue incertaine — `503`, `500`, ou expiration réseau

Le cas le plus délicat, et celui qui distingue une application financière sérieuse.

Trois déclencheurs, un seul écran : `503` (`TransientPaymentException`), `500` (`ProviderException` non mappée, corps non conforme), et l'absence de réponse. Dans les trois cas, l'app **ignore si l'argent a bougé**.

| Aspect | Spécification |
|---|---|
| Icône | Point d'interrogation en `warning.500` |
| Titre | « Nous n'avons pas pu confirmer cette opération » |
| Description | « Elle a peut-être été enregistrée. Vérifiez votre historique avant de réessayer. » |
| Action principale | **`Vérifier l'historique`** — et non `Réessayer` |
| Action secondaire | `Réessayer`, précédée d'un avertissement explicite sur le risque de double opération |
| Haptique | `warning` |

Aucun rejeu automatique. Jamais. L'API n'expose pas de clé d'idempotence : un rejeu silencieux peut débiter deux fois.

---

## 5. Historique — `(tabs)/activity`

| Aspect | Spécification |
|---|---|
| Liste | `FlashList`, hauteur de ligne fixe à 68 dp (`estimatedItemSize` si l'API installée l'exige) |
| Groupement | Par jour, en-têtes de section collants — « Aujourd'hui », « Hier », puis date longue |
| Pagination | Défilement infini, déclenché à 80 % de la fin, `size=20` |
| Chargement de page | Trois lignes squelettes en pied de liste |
| Rafraîchissement | Tirer-pour-rafraîchir |
| Filtres | Bouton en tête, ouvre une `Sheet` |
| Indicateur de filtre | Une pastille sur l'icône indique le nombre de filtres actifs |
| Appui sur une ligne | Transition partagée vers le détail |

Panneau de filtres (`Sheet`) :

| Filtre | Contrôle | Valeurs |
|---|---|---|
| Type | Segmenté | Tous · Dépôts · Retraits · Transferts |
| Sens | Segmenté | Tous · Entrants · Sortants |
| État | **Choix unique** dans une liste | Tous, puis les 11 états concrets, libellés en clair |
| Période | Puces + plage personnalisée | 7 j · 30 j · 90 j · Personnalisé |

Le panneau applique les filtres **en direct**, sans bouton de validation. Il affiche le nombre de résultats en pied de feuille et propose `Réinitialiser`.

> **Le filtre d'état est à choix unique, et c'est délibéré.** `TransactionFilter.state` n'accepte qu'une valeur (contrat §6.8). Un filtre par famille — « En cours » recouvrant quatre états — obligerait à filtrer localement la page chargée, ce qui produirait un total et une pagination faux. Un choix = un état = un filtre serveur exact.
>
> Les familles restent le mode d'**affichage** dans la liste et sur les puces. Elles ne deviendront un filtre que si le backend accepte un état multiple. `CONTOURNEMENT(indéterminé)`.

Sérialisation vers l'API : `type`, `direction`, `state` directement, dates converties en ISO-8601 UTC. `size` est plafonné à 100 côté serveur — l'app n'envoie jamais plus.

| État | Rendu |
|---|---|
| Chargement initial | 8 `SkeletonRow` |
| Vide sans filtre | `EmptyState` « Aucune opération » + `Faire un dépôt` |
| Vide avec filtres | `EmptyState` « Aucun résultat » + `Réinitialiser les filtres` |
| Erreur | `ErrorState` + `Réessayer` |
| Hors ligne | Cache affiché, `OfflineBanner`, pagination désactivée |

---

## 6. Détail d'une opération — `transaction/[id]`

L'écran qui matérialise le différenciateur produit.

```
┌─────────────────────────────────────┐
│  ←                            ⋯     │
│                                     │
│              ╭────╮                 │
│              │ ↓  │                 │   ← pastille 64 dp, cible de la transition partagée
│              ╰────╯                 │
│                                     │
│            + 50 000 F               │   ← displayMd
│            Orange Money             │
│         6 août 2026, 11:42          │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  ●  Initiée         11:42:13  │  │
│  │  │                            │  │
│  │  ●  Autorisée       11:42:13  │  │   ← StateTimeline
│  │  │  Fonds réservés            │  │
│  │  ●  Capturée        11:42:13  │  │
│  │  │                            │  │
│  │  ◉  Terminée        11:42:13  │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │ Réf.  TRX-20260806-A3F91C2D ⧉ │  │
│  │ Type                    Dépôt │  │
│  │ Moyen            Orange Money │  │
│  │ Sens                  Entrant │  │
│  └───────────────────────────────┘  │
│                                     │
│  [        Partager le reçu        ] │   ← P2
└─────────────────────────────────────┘
```

| Aspect | Spécification |
|---|---|
| Données | **Rejeu de la page d'origine** : `GET /payments/history?detail=true&page={page}&size=20&{filtres}`, puis sélection par identifiant. Voir contrat §6.7 |
| Paramètres de navigation | `id`, `page` d'origine, filtres actifs sérialisés |
| Repli | Opération absente de la page rejouée → élargir à `page−1` et `page+1`, puis `ErrorState` |
| Cache | L'opération vient du cache de la liste : affichage immédiat, `stateHistory` chargée en arrière-plan |
| Frise | `StateTimeline`, animation du §6.7 de `03-motion-and-feel.md` |
| Référence | Appui long → copie, `haptic.select`, `Toast` de confirmation |
| Opération en cours | Sondage actif, la frise se complète en direct au fur et à mesure |
| Entrée | Transition partagée depuis la ligne, éléments en cascade |

> **Limite du contrat.** Aucun endpoint `GET /payments/{id}` n'existe, ni de filtre par identifiant. Le rejeu de la page d'origine borne le coût à une page quelle que soit l'ancienneté de l'opération — mais reste un contournement. `CONTOURNEMENT(indéterminé)`, drapeau `API_CAPABILITIES.transactionById`.

| État | Rendu |
|---|---|
| Chargement | `SkeletonTimeline` |
| Introuvable | `ErrorState` « Opération introuvable » + retour à l'historique |
| Erreur | `ErrorState` + `Réessayer` |

---

## 7. Réglages — `(tabs)/settings`

| Section | Contenu |
|---|---|
| Profil | Avatar avec initiales, nom, e-mail, téléphone, numéro de compte |
| Sécurité | Déverrouillage biométrique *(P1)* · Changer le PIN *(P2)* |
| Préférences | Thème (Système / Sombre / Clair) · Langue (FR / EN) · Masquer le solde par défaut |
| À propos | Version, conditions, confidentialité |
| Session | `Se déconnecter` en `danger` |

Le nom et le téléphone proviennent du stockage local ; l'e-mail des claims du jeton ; le numéro de compte de `GET /payments/balance`. Voir contrat §6.3.

Déconnexion : `Dialog` de confirmation → purge de SecureStore, du cache de requêtes et du stockage local → retour à `login`. Aucun appel réseau (contrat §6.5).

---

## 8. États transversaux

### 8.1 Session expirée en cours d'action

Le cas qui trahit le plus vite une application mal construite.

```
1. Une requête renvoie 401 avec { error: "Unauthorized" }
2. L'intercepteur tente un rafraîchissement unique, en vol groupé
3. Succès → la requête d'origine est rejouée, l'utilisateur ne voit rien
4. Échec  → purge, une Sheet non bloquante remonte : « Session expirée »
5. L'utilisateur se reconnecte dans la Sheet
6. Retour exact à l'écran quitté, avec l'état du parcours intact
```

**Interdit** : éjecter l'utilisateur vers l'écran de connexion en perdant son parcours. S'il était à l'étape de récapitulatif d'un transfert de 50 000 F, il doit y revenir.

**Exception critique** : un `401` portant `detail: "Invalid PIN"` n'est **jamais** traité par ce mécanisme. Voir contrat §5.2.

### 8.2 Perte de connexion

| Contexte | Comportement |
|---|---|
| Consultation | `OfflineBanner`, données de cache, actions monétaires désactivées avec explication |
| Pendant un parcours | Le parcours reste ouvert, l'action est désactivée, la reconnexion la réactive |
| **Pendant une requête de paiement** | Écran « issue incertaine » du §4.6. Jamais de rejeu automatique |

### 8.3 Retour au premier plan

| Délai depuis la mise en arrière-plan | Comportement |
|---|---|
| < 30 s | Rien |
| 30 s – 5 min | Revalidation du solde et de l'historique |
| > 5 min | Revalidation + verrouillage biométrique si activé |
| Jeton d'accès expiré | Rafraîchissement silencieux avant toute requête |

Le contenu de l'application est occulté dans le sélecteur d'applications, quelle que soit la durée.
