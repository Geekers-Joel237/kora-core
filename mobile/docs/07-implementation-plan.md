# Kora Mobile — Plan d'implémentation

Onze lots séquencés. Chacun est vérifiable indépendamment. **Ne pas passer au lot suivant tant que les critères du lot courant ne sont pas tous satisfaits.**

L'ordre n'est pas négociable : les lots 2 et 3 construisent le socle visuel et cinétique dont tout le reste dépend. Les écrire après les écrans conduit invariablement à réécrire les écrans.

> **Backend en développement.** Le lot 1bis (mode validation) arrive tôt et délibérément : c'est l'outillage qui rend le backend observable, et il paie son coût dès le lot 5. Les lots 2, 3 et 4 — design system, mouvement, composants monétaires — sont **indépendants de l'API** et ne doivent jamais être différés au motif que le contrat bouge.

---

## Lot 0 — Amorçage ✅

**Objectif** : un projet qui démarre, sans une ligne d'interface.

- [x] `npx create-expo-app@latest . --template blank-typescript` dans `mobile/` — **Expo SDK 57**, RN 0.86, React 19.2, TS 6.0
- [x] TypeScript en mode `strict`, `noUncheckedIndexedAccess` actif
- [x] ESLint + Prettier, avec une **règle bloquante interdisant les littéraux de style** (couleur hexadécimale, `rgba()`, `fontSize`, `margin`/`padding` numérique) hors de `src/theme/`
- [x] Le lint bannit également les bibliothèques de composants tierces, `Touchable*`, `Animated` de RN et `LayoutAnimation`
- [x] Dépendances de la pile — `06-architecture.md` §1
- [x] Arborescence complète — `06-architecture.md` §2
- [x] Alias de chemin `@/*` → `src/*`
- [x] Polices Inter empaquetées dans `assets/fonts/`, chargées dans `app/_layout.tsx`
- [x] `.env` et `.env.example` avec `EXPO_PUBLIC_API_URL` et `EXPO_PUBLIC_ENV`
- [x] `expo-router` câblé, `main` → `expo-router/entry`, routes typées
- [x] Jest + jest-expo, script `npm run verify` (typecheck + lint + test)

**Vérification** — toutes passées :

- `npm run verify` vert
- `npm run export:android` produit un bundle de production complet
- Les quatre graisses d'Inter se rendent à l'écran
- La règle de lint sur les littéraux de style **a effectivement rejeté** l'écran d'amorçage à sa première écriture

**Trois obstacles rencontrés, tous documentés en `06-architecture.md` §9 :**

| Obstacle | Résolution |
|---|---|
| Metro et `kora-core` se disputent le port **8081** | Metro forcé sur **8090** dans les scripts npm |
| `@expo-google-fonts/inter` embarque **18 graisses** (7 Mo d'assets) | Les 4 `.ttf` copiés dans `assets/fonts/`, paquet désinstallé → 2,4 Mo |
| `react-dom@19.2.8` exige `react@^19.2.8`, Expo épingle `19.2.3` | `overrides.react-dom = 19.2.3` |

**Dette assumée** : `src/theme/tokens.ts` ne contient qu'un sous-ensemble d'amorçage — quatre couleurs, l'échelle d'espacement, cinq variantes typographiques. Le fichier porte un en-tête l'indiquant explicitement. Le lot 2 le remplace intégralement.

**À surveiller** : le bundle Hermes de production pèse déjà **3,7 Mo sans code applicatif**, contre un budget `NFR-23` de 4 Mo. Le budget est vraisemblablement à requalifier (il visait le JS brut, pas le bytecode) ou à revoir. À trancher au lot 10, avec l'analyse de bundle. `MaterialSymbols` (962 Ko) est tiré par l'interface de développement d'`expo-router` — à vérifier dans une build de release réelle.

---

## Lot 1 — Socle réseau ✅

**Objectif** : parler au backend, correctement, avant toute interface.

- [x] `src/types/api.ts` — DTO dérivés de `01-api-contract.md`, **sans invention**
- [x] `src/types/domain.ts` — types internes stables, `outcomeOf()`, `isTerminalState()`
- [x] Client HTTP `fetch` enveloppé, injection du Bearer et `X-Correlation-Id`
- [x] Normalisation d'erreur vers `KoraError` — gère les **trois** formats du contrat §5.1, plus corps vide et non-JSON
- [x] **`isTokenExpiry()`** discriminant les deux `401` — contrat §5.2
- [x] Rafraîchissement en vol groupé, un seul rejeu — `06-architecture.md` §3.4
- [x] Politique de reprise : `GET` seulement, **jamais** sur `/payments/*`
- [x] `isOutcomeUnknown` calculé dans la couche HTTP, jamais dans un écran
- [x] `src/lib/money/` — conversion entière, formatage à blocs, XOF sans décimale
- [x] `src/lib/datetime.ts` — ISO-8601 UTC ↔ local, formats d'affichage
- [x] `src/lib/storage/` — enveloppes SecureStore et MMKV
- [x] `src/lib/capabilities.ts` — les 8 drapeaux `API_CAPABILITIES`, tous à `false`
- [x] `src/lib/jwt.ts` — décodage local des claims, contrat §6.3
- [x] `src/lib/decode.ts` — validation permissive + signalement de dérive de contrat
- [x] En-tête `Idempotency-Key` généré et envoyé sur `/payments/*` **dès maintenant**
- [x] Couche de traduction R1 : aucun type d'API ne sort de `features/*/api.ts`
- [x] Schémas `zod` en `z.looseObject`, tolérants aux champs inconnus
- [x] API branchées : `auth`, `wallet`, `payments`, `history` — y compris le rejeu de page du contrat §6.7

**Vérification** — **63 tests**, toutes passées :

- `formatMinor(125000, 'XOF')` → `{ sign: '', integer: '125 000', symbol: 'F', fraction: null }` avec `U+202F` vérifié caractère par caractère
- Bornes `0`, `1`, `999999999` couvertes ; `U+2212` vérifié distinct de `-`
- Un test prouve que `{status:401, error:'Unauthorized'}` déclenche **un** rafraîchissement puis un rejeu, et que `{status:401, detail:'Invalid PIN'}` n'en déclenche **aucun**
- Trois `401` simultanés ne produisent **qu'un seul** `/auth/refresh`
- Un `POST /payments/*` n'est **jamais** rejoué : ni sur `503`, ni sur coupure réseau
- `SOME_FUTURE_STATE` se replie sur `pending` sans faire planter aucun mappeur
- Un champ supplémentaire est conservé sans erreur ; un champ consommé manquant lève une dérive explicite
- `npm run export:android` compile toujours

**Écarts assumés, à traiter au lot où ils deviennent pertinents :**

| Point | Décision |
|---|---|
| `performRefresh` lisait par `response.json()` là où le reste du client passe par `readBody` | Aligné : un `200` au corps illisible est désormais traité comme un échec de rafraîchissement, pas comme un succès silencieux |
| MMKV 4 utilise `createMMKV()` et `remove()` | `MMKV` n'est plus qu'un type ; l'instance est créée paresseusement pour ne pas casser les tests |
| Pas encore de magasin de session | Le client HTTP dialogue via `registerTokenProvider()` — le magasin s'y branchera au lot 5, sans dépendance circulaire |

---

## Lot 1bis — Mode validation ✅

**Objectif** : rendre le backend observable. Écrit tôt parce qu'il sert à tous les lots suivants — *et écrit en dernier, ce qui a coûté exactement ce que l'introduction annonçait.*

- [x] `src/devtools/` avec `DEV_MODE`, import dynamique, exclusion au secouage — *fait au lot 8, vérifié et corrigé au lot 10*
- [x] Ouverture par secousse et par appui long sur le logo — *plus l'appui triple sur la version ; la secousse passe par le menu développeur, voir ci-dessous*
- [x] **Inspecteur réseau** — 200 entrées, détail requête/réponse, `rawPin` masqué
- [x] `Copier en cURL` sur chaque entrée
- [x] Signaux visuels : erreur, lenteur, rafraîchissement de jeton, rejeu
- [x] Bascule d'environnement avec purge des jetons et test de connectivité
- [x] Inspecteur de session — claims décodés, compte à rebours d'expiration, actions d'invalidation
- [x] Simulation client : latence forcée, coupure réseau, statut de réponse imposé

Le détecteur de dérive de contrat, l'inspecteur de transactions et le journal de scénarios ont été faits au lot 8. Le panneau compte désormais **huit onglets**.

**Vérification** — **340 tests**, toutes passées (33 de plus qu'au lot 10) :

- **`rawPin` est masqué sans exception**, y compris imbriqué et dans un tableau ; les jetons aussi. Vérifié sur une vraie requête traversant le client HTTP, pas seulement sur la fonction de masquage
- L'en-tête `Authorization` garde son schéma lisible et perd sa valeur
- Le `cURL` produit deux fois le même texte pour le même appel — sans ordre stable, comparer deux copies devient un exercice de patience
- Le rejeu n'est proposé que sur un `GET` — jamais sur un `POST`, de paiement ou non
- Les quatre signaux du §3 se déclenchent aux bons seuils, et **le rafraîchissement de jeton est observé de bout en bout** : un `401`, un rafraîchissement, un rejeu, deux entrées marquées
- Le journal est plafonné à 200 entrées, en tampon circulaire
- Une coupure réseau armée sur `/payments/` produit une **issue incertaine** et **une seule requête**
- Une réponse imposée se désarme après usage, et ne touche pas les chemins non ciblés
- La bascule d'environnement écrit l'URL **après** la purge — l'écrire avant la ferait emporter par `kvClear()`
- Le test de connectivité rapporte un échec HTTP comme une absence de serveur, sans lever
- `npm run audit:bundle` : **0 module de `src/devtools/`** dans le bundle de production, malgré les onze fichiers ajoutés

**Trois obstacles rencontrés :**

| Obstacle | Résolution |
|---|---|
| **L'inspecteur réseau et le simulateur doivent vivre dans `lib/http`, qui ne doit rien savoir de `src/devtools`** — sous peine de ramener le panneau entier dans le bundle de production | `lib/http/instrumentation.ts` expose deux emplacements vides ; les devtools s'y branchent. Exactement le motif de `registerTokenProvider`, et la flèche de dépendance ne s'inverse jamais |
| **Un branchement par `useEffect` manque le parcours d'authentification.** Les effets d'un enfant se déclenchent avant ceux de son parent, et le portail de session émet son `/auth/refresh` depuis un effet | `initDevtools()` est appelé au **chargement du module racine**, à côté de la réhydratation du cache. L'hôte est monté à la racine et non sous `(app)` : le panneau est atteignable depuis l'écran de connexion, où se joue le scénario 1 |
| **La secousse exigerait `expo-sensors`**, l'accéléromètre en continu et une reconstruction native | La secousse ouvre nativement le menu développeur de React Native : `DevSettings.addMenuItem` y ajoute une entrée Kora. Un appui de plus, zéro dépendance. `CONTOURNEMENT(indéterminé)`. Les deux autres déclencheurs — appui long sur la salutation d'accueil, appui triple sur le numéro de version — sont exacts |

**Deux choix structurants :**

| Point | Décision |
|---|---|
| **Le masquage vit dans le code de production, pas dans les devtools** | Le §3 exige `rawPin` masqué « sans exception, même en développement ». Le confier au panneau reviendrait à parier qu'on n'oubliera jamais : une entrée enregistrée avant masquage serait en clair. `lib/http` masque **avant** que l'entrée n'existe, et le panneau n'a jamais vu un PIN |
| **Le `cURL` copié n'est pas rejouable tel quel sur un appel porteur d'un secret** | `rawPin` y vaut `****`, le Bearer aussi. C'est la contrepartie assumée de la règle précédente : l'opérateur remplace les `****` à la main. La `Vérification` du plan disait « rejoue l'appel à l'identique » — c'est incompatible avec le §3, et c'est le §3 qui gagne |

**Le journal réseau n'est jamais persisté.** Ces charges utiles ont beau être masquées, elles décrivent l'activité financière de quelqu'un : elles restent en mémoire, plafonnées, et disparaissent avec le processus.

**Bundle** : 4,67 Mo, inchangé — le mode validation n'y entre pas.

---

## Lot 2 — Design system ✅

**Objectif** : tout le langage visuel de `02-design-system.md`, exhaustivement, avant tout écran.

- [x] `src/theme/tokens.ts` — transcription intégrale du §7, remplace le sous-ensemble d'amorçage du lot 0
- [x] Thème clair complet, avec les mêmes clés, garanties par un test de parité
- [x] `ThemeProvider` + `useTheme()`, réactif au réglage système, préférence persistée
- [x] `useReduceMotion()` branché sur `AccessibilityInfo` — §7.2
- [x] `src/theme/motion.ts` — jetons de `03-motion-and-feel.md` §2, **en donnée pure**
- [x] `src/theme/easing.ts` — courbes, séparées car elles importent le runtime natif
- [x] Primitives : `Text`, `Surface`, `Divider`, `Spacer`, `Flex`
- [x] `Icon` — 33 icônes SVG du §6.2, trait constant, 5 tailles autorisées
- [x] **Écran de galerie** sous `src/devtools/`, éliminé du bundle de production par `__DEV__`

**Vérification** — **97 tests**, toutes passées :

- La galerie rend les 11 neutres, les 7 accents, les 4 sémantiques, les 12 variantes typographiques, les 11 crans d'espacement, les 6 rayons, les 5 élévations et les 33 icônes — vérifié par rendu réel, pas par inspection visuelle
- **Le contraste est mesuré, pas jugé** : `contrast.test.ts` calcule le ratio WCAG de chaque paire des deux thèmes, lavis translucides composés sur leur fond réel
- Aucun littéral de style hors de `src/theme/` — le lint le prouve, et il a rejeté l'écran d'amorçage à sa première écriture
- `npm run export:android` compile toujours

**Un défaut réel trouvé par le test de contraste, dans le design system lui-même :**

> En thème clair, `onAccent: #FFFFFF` sur `accent.600 #00A557` ne donne que **3,22:1**, sous le seuil de 4,5:1. C'est exactement le piège que le §2.2 décrit pour le thème sombre — et il se referme un cran plus bas en thème clair.
>
> Corrigé dans le code **et dans la doc** : l'accent primaire du thème clair est désormais `accent.700` (5,10:1), et un palier `accent.800` a été ajouté pour l'état pressé. Ce type d'erreur ne se voit pas à l'œil : il se mesure.

**Trois obstacles rencontrés :**

| Obstacle | Résolution |
|---|---|
| `motion.ts` importait le runtime Reanimated, rendant `@/theme` inutilisable en test | Séparé : `motion.ts` devient de la donnée pure (`import type` seul), les courbes passent dans `easing.ts` |
| **RNTL 14 rend `render` asynchrone** avec React 19 | Tous les tests de rendu passent par `await`. L'oublier produit un « getByText is not a function » opaque |
| MMKV natif absent en test | Substitut en mémoire dans `jest.setup.ts` |

**Dette assumée** : la galerie utilise encore le `Pressable` brut de React Native pour la bascule de thème. Le lot 3 le remplace par la primitive maison.

---

## Lot 3 — Mouvement et composants d'interaction ✅

**Objectif** : le ressenti. Le lot le plus déterminant du projet.

- [x] `Pressable` sur `Gesture.Tap()`, compression et opacité **sur le thread UI**, table d'échelles du §4
- [x] Appui long en `Gesture.Simultaneous`, avec `haptic.commit`
- [x] `src/lib/haptics.ts` — les 7 impulsions, étranglement global à 50 ms, préférence utilisateur
- [x] `Button` — 4 variantes, 3 tailles, **chargement à largeur constante**
- [x] `IconButton` — libellé d'accessibilité obligatoire
- [x] `Skeleton` + miroitement, `useDelayedLoading` au seuil de 200 ms
- [x] `Sheet` — glissement au doigt, résistance au dépassement, seuils distance **et** vélocité
- [x] `Dialog`, `Toast` avec `ToastProvider`
- [x] Support de « réduire les animations » sur l'ensemble — fondus au lieu de translations
- [x] Galerie enrichie : boutons, boutons d'icône, squelettes

**Vérification** — **125 tests**, toutes passées :

- Les 7 impulsions mappent le bon effet système ; l'étranglement à 50 ms est vérifié, toutes familles confondues
- L'haptique est intégralement coupable par préférence, et un moteur haptique absent n'interrompt jamais l'interaction
- Le squelette **n'apparaît pas** sur une réponse plus rapide que 200 ms, et se rétracte immédiatement à la fin du chargement
- Les échelles de compression décroissent bien avec la taille de l'élément
- `spring.gesture` interdit tout rebond ; toutes les durées respectent le plancher perceptif de 120 ms
- `npm run export:android` compile toujours

**Deux défauts réels trouvés par les tests :**

| Défaut | Cause | Correction |
|---|---|---|
| La **toute première** impulsion haptique était étranglée | `lastFiredAt = 0` : une sentinelle qui dit « à l'instant zéro » au lieu de « jamais » | `Number.NEGATIVE_INFINITY` |
| `useDelayedLoading` appelait `setState` dans le corps d'un effet | Cascade de rendus, signalée par les règles du React Compiler | État dérivé : `return loading && elapsed` |

**Trois obstacles d'outillage, tous documentés dans le README :**

| Obstacle | Résolution |
|---|---|
| Reanimated 4 refusait de se charger en test (runtime worklets natif) | `resolver: 'react-native-worklets/jest/resolver'` — voie officielle, écarte les implémentations `.native` |
| `react-hooks/immutability` interdit de muter une `SharedValue` utilisée dans un effet | Règle désactivée **pour les composants seulement** : elle ne modélise pas les valeurs partagées, sur lesquelles Reanimated repose entièrement |
| Les faux timers polluaient les rendus des suites voisines | Tests à faux timers isolés dans leur propre fichier, et `act` asynchrone |

> **RNTL 14 : `render`, `renderHook` ET `rerender` sont tous asynchrones.** Oublier un `await` produit soit un « getByText is not a function », soit un `result.current` à `null` — deux symptômes qui ne désignent pas leur cause.

---

## Lot 4 — Composants monétaires ✅

**Objectif** : les composants qui portent l'argent.

- [x] `Amount` — trois blocs, chiffres tabulaires, `U+202F`, `U+2212`, symbole à `0,45×`
- [x] Compteur animé **sur le thread UI**, `Easing.out(cubic)`, 900 ms
- [x] `BalanceHero` avec bascule de masquage et squelette de forme identique
- [x] `Keypad` partagé, `AmountKeypad` — contrainte de solde, secousse au dépassement
- [x] `PinPad` — remplissage, pouls au chargement, secousse à l'erreur, capture d'écran bloquée
- [x] `OtpInput` — 6 cellules, collage en cascade, soumission automatique
- [x] `StatusChip`, `TransactionRow`, `StateTimeline`
- [x] `src/features/shared/labels.ts` — table de traduction des 11 états, opérateurs, types
- [x] Galerie enrichie : montants, puces, lignes d'historique, frise

**Vérification** — **156 tests**, toutes passées :

- Les blocs d'un montant sont rendus séparément, avec le symbole à `0,45×` **calculé depuis la variante** et vérifié par assertion de style
- `U+2212` est présent et `-` absent ; le `+` n'apparaît que sur un flux entrant explicite
- **Aucun décalage horizontal possible** : `111 111` et `999 999` portent tous deux `tabular-nums`
- **La secousse a une amplitude décroissante**, vérifiée terme à terme, alternée, et se termine à `0`
- `StateTimeline` rend les **11 états** et une frise **incomplète encore en cours**, sans jamais exposer un code brut
- Règle R2 : `MANUAL_REVIEW` se rend en « En cours » dans la puce, et en clair dans la frise
- `TransactionRow` n'affiche **aucune puce** sur un succès, et en affiche une sur les autres familles
- `npm run export:android` compile toujours

**Trois décisions d'implémentation notables :**

| Point | Décision |
|---|---|
| **Compteur animé** | Piloté par `animatedProps` sur un `TextInput` non éditable — la seule façon de faire varier un contenu textuel sur le thread UI. Un `useState` remettrait 60 rendus React par seconde sur le pont JS. Le groupement des milliers est recalculé par boucle explicite, sans expression régulière : les worklets tolèrent mal `replace` avec un `RegExp` |
| **Symbole de devise** | `Amount` est le **seul** composant autorisé à composer de la typographie à la main, via `currencySymbolStyle(variant)` exporté des tokens. Le rapport 0,45× reste ainsi constant à toutes les tailles |
| **Couleurs de marque** | Déplacées dans `src/theme/brands.ts` — ce ne sont pas des jetons, mais ce sont des couleurs, et `src/theme/` est le seul endroit où en écrire une littéralement |

**Un défaut de conception corrigé :** `OtpInput` réinitialisait son code dans un effet, ce que les règles du React Compiler signalent comme rendu en cascade — les cellules seraient restées remplies une image après l'erreur. Remplacé par l'ajustement d'état **pendant le rendu**, le motif documenté par React, avec les effets de bord (haptique, secousse) laissés dans l'effet.

---

## Lot 5 — Authentification ✅

- [x] Portail de session `app/index.tsx` — aucun rendu visible avant résolution
- [x] `onboarding` — 3 volets, parallaxe à 0,4×, points qui s'allongent
- [x] `login` — 2 étapes, message d'erreur **unifié** pour `401` et `404`
- [x] `register` — 4 étapes, barre de progression, `409` renvoyant à l'étape e-mail
- [x] `verify-otp` — `Ouvrir ma boîte mail`, renvoi à 30 s, expiration à 5 min, blocage après 3 essais
- [x] Magasin de session Zustand, jetons en SecureStore, `registerTokenProvider` branché
- [x] Garde d'authentification sur `(app)`
- [x] Capture d'écran bloquée sur PIN **et** OTP
- [x] `TextField`, `openMailbox()`, `otpFlow` volatil

**Vérification** — **177 tests**, toutes passées :

- **Un e-mail inconnu et un PIN erroné produisent exactement la même chaîne**, et aucun détail serveur ne fuite dans le message
- Un jeton d'accès expiré au démarrage est rafraîchi **sans aucun rendu intermédiaire** ; deux jetons expirés déconnectent sans appel réseau
- Un OTP faux, expiré ou **réutilisé** produisent le même message — le backend les rend indiscernables
- Les jetons vont dans SecureStore avec `WHEN_UNLOCKED_THIS_DEVICE_ONLY` ; la déconnexion purge tout **sans appel réseau**
- Le PIN volatile de `otpFlow` ne survit ni au succès, ni à l'abandon, ni au démontage
- Les devtools **ne sont pas** dans le bundle de production — vérifié par recherche dans le `.hbc`

**Deux points structurants :**

| Point | Détail |
|---|---|
| **Rafraîchissement au démarrage explicite** | Le client HTTP ne rafraîchit que sur un `401` en vol. Au démarrage, aucune requête n'a encore été émise : sans étape dédiée, le premier appel de l'accueil échouerait puis serait rejoué, et l'utilisateur verrait un aller-retour inutile |
| **`otpFlow` détient un PIN en clair** | Le seul de toute l'application. Conséquence directe de l'absence d'endpoint de renvoi d'OTP (contrat §6) : le renvoi doit rappeler `/auth/login`, qui exige le PIN. Portée module, jamais un état React, effacé à toute sortie. `CONTOURNEMENT(indéterminé)` |

> ### ⚠️ NFR-23 est dépassé, et il faut le trancher
>
> Le bundle Hermes de production est passé de **3,9 à 5,6 Mo** en un seul lot. La cause est identifiée et confirmée par inspection du `.hbc` : le passage de `<Slot>` à `<Stack>` tire `react-navigation` et `NativeStackView`.
>
> Ce n'est ni une fuite des devtools (vérifié : absents), ni du code applicatif — les cinq écrans d'authentification pèsent une fraction du delta. C'est le **coût structurel et incompressible** d'une navigation en pile.
>
> Le budget de 4 Mo de `00-requirements.md` visait manifestement du **JavaScript brut**, pas du bytecode Hermes, qui est sensiblement plus volumineux. Deux options, à arbitrer et non à ignorer :
> 1. Requalifier `NFR-23` en « bytecode Hermes < 8 Mo » et mesurer séparément le JS brut.
> 2. Conserver 4 Mo et renoncer à `expo-router` — ce qui reviendrait à réécrire toute la navigation.
>
> **Recommandation : option 1.** À trancher au lot 10, avec l'analyse de bundle complète. Le budget est faux, pas l'architecture.

**Trois frictions d'outillage :**

| Friction | Résolution |
|---|---|
| Les hrefs typés d'expo-router **élident les groupes de routes** | `/login`, pas `/(public)/login`. Les types sont générés par le serveur de développement — ils étaient périmés et produisaient des erreurs illisibles |
| `Buffer` n'existe pas dans le runtime React Native | Base64 réimplémenté dans le test plutôt qu'ajouter `@types/node`, qui aurait rendu les globales Node visibles au code applicatif |
| `react/no-unescaped-entities` sur les apostrophes françaises | Texte JSX contenant `'` placé entre accolades |

---

## Lot 6 — Accueil ✅

- [x] `GET /payments/balance` et `GET /payments/history?size=5`, en requêtes **indépendantes**
- [x] `QueryClientProvider`, clés et durées de cache d'architecture §4.2–4.3
- [x] `BalanceHero` avec compteur animé et masquage persisté
- [x] `ActionTile` ×3, entrée en cascade, désactivées hors ligne
- [x] Section d'activité récente avec frontière d'erreur distincte
- [x] Compression de la carte au défilement, **entièrement sur le thread UI**
- [x] Tirer-pour-rafraîchir avec haptique au franchissement du seuil
- [x] Revalidation au retour au premier plan, **au-delà de 30 s seulement**
- [x] `OfflineBanner` avec grâce de 2 s, `EmptyState`, `ErrorState`, `SkeletonTransactionList`
- [x] Barre d'onglets à 3 entrées, jalons pour les parcours du lot 7

**Vérification** — **184 tests**, toutes passées :

- **Le solde aboutit quand l'historique échoue, et réciproquement** — les deux sens sont testés
- Les hooks renvoient des types de domaine ; un test vérifie qu'aucun champ d'API n'a traversé la frontière R1
- TanStack Query **n'ajoute aucune reprise** par-dessus celle du client HTTP : 3 tentatives sur un `GET`, pas 9
- L'accueil ne demande que 5 opérations
- `npm run export:android` compile toujours

**Deux décisions notables :**

| Point | Décision |
|---|---|
| **`retry: false` sur TanStack Query** | Le client HTTP connaît la seule règle qui compte — jamais de reprise sur `POST /payments/*`. La rejouer au niveau de la requête la multiplierait et contournerait cette garantie |
| **Revalidation à partir de 30 s** | Revalider à chaque retour au premier plan produirait deux requêtes à chaque bascule d'application, ce qui grèverait `NFR-25` (120 Ko par session de 5 opérations) |

**Une friction de test à connaître :** un `waitFor` d'une seconde expire **avant** les 3 reprises du client HTTP sur un `GET` (recul 300 + 600 ms plus gigue). Les attentes portant sur une erreur de lecture doivent laisser 5 s — sinon le test échoue sur un comportement parfaitement correct.

**Bundle** : 6,0 Mo. La progression depuis 5,6 Mo est cohérente avec l'ajout de TanStack Query et de la barre d'onglets. Voir l'encadré `NFR-23` du lot 5 — le budget reste à requalifier.

**Non vérifié, et non vérifiable ici** : les 60 fps au défilement et le démarrage à froid en moins de 2,5 s sur 3G. Ces deux critères exigent l'appareil socle et appartiennent au lot 10.

**Dette assumée** : le cache n'est pas encore persisté sur MMKV. `staleLabel` fonctionne dans la session, mais un redémarrage hors ligne repart d'un cache vide — `NFR-31` n'est donc que partiellement tenu. À compléter au lot 9.

---

## Lot 7 — Parcours monétaires ✅

Le lot le plus long et le plus risqué.

- [x] Machine de parcours (Zustand), réinitialisée à l'entrée, à la sortie et au changement de type
- [x] Étape « montant » avec `AmountKeypad` et contrainte de solde
- [x] Étape « opérateur » avec `MethodPicker`
- [x] Étape « destinataire » avec `PhoneField` et récents locaux
- [x] Étape « récapitulatif » avec **case de vérification obligatoire** sur les transferts
- [x] Étape « PIN » avec le verrou à quatre couches — `06-architecture.md` §5
- [x] Retour matériel neutralisé pendant l'exécution, `router.replace` sur la pile
- [x] Écran de résultat — **les quatre variantes** : succès, en cours, échec, issue incertaine
- [x] Chorégraphie de succès, anneau et coche dessinés par `strokeDashoffset`
- [x] Traduction des messages métier en **table de données** — `05-screens.md` §4.6
- [x] Sondage borné 5 s × 12, arrêt immédiat en arrière-plan
- [x] Invalidation du solde et de l'historique après succès **et** sur issue incertaine

**Vérification** — **206 tests**, toutes passées :

- **Un double appui produit UNE SEULE requête** — deux `submit` concurrents dans le même `act`, avant tout re-rendu, exactement ce que produit un appui à 50 ms d'intervalle
- La clé d'idempotence est transportée et **reste identique** entre deux passages au récapitulatif
- Un `503`, un `500` de fournisseur et une coupure réseau conduisent tous trois à « issue incertaine », **avec une seule requête émise**
- Un état `CAPTURED` est classé **en cours**, jamais en succès
- Un `422` de solde insuffisant conserve le montant
- Les 6 motifs de rejet sont traduits ; un motif inconnu affiche le `detail` brut
- Le cache est invalidé sur succès et sur issue incertaine, **jamais** sur un rejet métier

**Deux choix structurants :**

| Point | Décision |
|---|---|
| **La clé d'idempotence est armée à l'entrée du récapitulatif** | Pas à l'envoi. Elle doit survivre à un rejeu manuel — sinon le rejeu créerait une seconde opération le jour où l'étape 4 backend atterrit. `armIdempotency()` est idempotent |
| **L'action principale sur « issue incertaine » est `Vérifier l'historique`** | Le rejeu est relégué en secondaire, précédé d'un avertissement explicite. Sans idempotence serveur, un rejeu peut débiter deux fois |

**Un défaut réel corrigé dans du code du lot 3 :**

> `haptic.fire()` ne protégeait que le **rejet de promesse**, pas une exception **synchrone**. Or `haptic.commit()` est appelé avant le `try` de la soumission : une plateforme levant de façon synchrone aurait interrompu le paiement avant même la requête, sans aucune erreur visible. Le commentaire du code promettait déjà que l'haptique n'interrompt jamais l'interaction — il ne le faisait pas. Corrigé par un `try` en plus du `.catch`.

**Deux pièges d'outillage, résolus dans `jest.setup.ts` :**

| Piège | Effet |
|---|---|
| `expo-crypto.randomUUID()` renvoie `undefined` sous l'auto-mock de jest-expo | Aucune clé d'idempotence, aucun identifiant de corrélation — et `useSubmitPayment` sortait **silencieusement** en amont. Seize tests échouaient sans message exploitable |
| Une fabrique `jest.mock()` ne peut référencer aucune variable extérieure | Le compteur d'UUID doit vivre **dans** la fabrique |

**Bundle** : 6,1 Mo. L'encadré `NFR-23` du lot 5 reste d'actualité.

**Non vérifié ici** : l'enchaînement réel des écrans et la chorégraphie de succès. Ils exigent l'appareil et le backend.

---

## Lot 8 — Historique et détail ✅

- [x] `FlashList` avec groupement par jour et en-têtes collants
- [x] Défilement infini, `size=20`, déclenchement à 80 %
- [x] Panneau de filtres en `Sheet`, application en direct
- [x] Sérialisation des dates en ISO-8601 UTC
- [x] Écran de détail avec `StateTimeline` animée
- [x] Transition partagée liste → détail — **repli imposé**, voir ci-dessous
- [x] Copie de la référence par appui long
- [x] Sondage sur les opérations en cours
- [x] **Devtools** : inspecteur de transactions — états bruts, durées inter-états, suivi forcé à 2 s
- [x] **Devtools** : détecteur de dérive contre `/v3/api-docs`
- [x] **Devtools** : journal de scénarios, export Markdown
- [x] **En plus** : hôte de panneau à onglets, faute d'existence du lot 1bis

**Vérification** — **242 tests**, toutes passées (36 de plus qu'au lot 7) :

- Une journée à cheval sur deux pages ne produit **qu'un seul en-tête** — le suivi du dernier jour émis traverse les pages
- La page d'origine est transportée sur chaque ligne jusqu'à l'écran de détail — contrat §6.7
- L'aller-retour de sérialisation conserve la sélection ; les bornes reviennent **ramenées au jour**
- Une valeur inconnue (`t=CRYPTO_SWAP`, `s=REFUNDED`) est ignorée, jamais propagée au serveur — règle R2
- « 7 j » démarre sept journées pleines avant aujourd'hui **inclus**, pas 168 heures en arrière
- La pagination demande `size=20` puis `page=1`, et s'arrête sur `hasNext=false`
- Le détecteur de dérive classe correctement les six catégories du §5 de `10-validation-mode.md`, descend dans `stateHistory[]`, accepte `integer` là où l'app attend un nombre, et **constate un document illisible sans lever**
- Les durées inter-états trient avant de soustraire — aucun delta négatif
- L'export Markdown produit le tableau de synthèse et le détail attendus par `09-api-evolution.md` §7

**Trois obstacles rencontrés :**

| Obstacle | Résolution |
|---|---|
| **Les transitions d'élément partagé sont indisponibles.** Reanimated 4.5.1 les garde derrière le drapeau statique `ENABLE_SHARED_ELEMENT_TRANSITIONS`, à `false` (`lib/module/featureFlags/staticFeatureFlags.js`), et elles restent expérimentales sur la nouvelle architecture — la seule que Reanimated 4 supporte | Repli **assumé et animé** : chaque bloc du détail monte de 12 dp avec 60 ms de décalage. Pas une absence d'animation, une continuité obtenue autrement. `CONTOURNEMENT(indéterminé)` |
| **Une borne de date recalculée à chaque rendu fabrique une clé de requête neuve à chaque rendu**, donc un rechargement en boucle de l'historique filtré | La clé porte la **sélection** (`serializeFilters`), pas les bornes résolues. `toHistoryQuery` ne s'exécute que dans le `queryFn`, à granularité du jour |
| **L'hôte du panneau devtools relève du lot 1bis, non implémenté** — les trois inspecteurs du lot 8 n'avaient nulle part où vivre | Construction de `DevtoolsPanel` (4 onglets) et de `DevtoolsHost` (montage `lazy` gardé par `DEV_MODE`). Les déclencheurs — secousse, appui long sur le logo, triple appui sur la version — restent au lot 1bis ; une entrée provisoire figure dans les réglages |

**Deux choix structurants :**

| Point | Décision |
|---|---|
| **Le filtre d'état est un choix unique parmi les onze états concrets** | `TransactionFilter.state` n'accepte qu'une valeur — contrat §6.8. Filtrer une famille localement fausserait le total **et** la pagination. Les familles restent le mode d'affichage. Drapeau `API_CAPABILITIES.multiStateFilter` |
| **L'écran de détail affiche deux sources superposées** | Le cache de la liste donne montant, contrepartie et date **immédiatement** ; le rejeu de la page d'origine avec `detail=true` apporte l'historique d'états. L'écran ne s'ouvre jamais sur un squelette vide alors que l'app connaît déjà l'essentiel |

**Sur le détecteur de dérive** : les schémas de réponse sont résolus en **suivant le `$ref`** déclaré dans la réponse `200`, jamais en devinant un nom de schéma. springdoc les nomme d'après les classes Java ; toute table de noms attendus serait fausse au premier renommage backend.

**Bundle** : 6,3 Mo. L'encadré `NFR-23` du lot 5 reste d'actualité.

**Non vérifié ici** : les 60 fps sur 200 lignes et la complétion en direct d'une frise. Les deux exigent l'appareil socle et un backend produisant des transitions réelles — c'est précisément ce que l'inspecteur de transactions sert à observer.

---

## Lot 9 — Réglages, i18n, finition ✅

- [x] Écran de profil, données reconstruites selon le contrat §6.3
- [x] Bascules de thème, de langue, de masquage par défaut
- [x] Biométrie *(P1)*
- [x] Déconnexion avec purge intégrale
- [x] `fr.json` et `en.json` complets — **zéro chaîne en dur**
- [x] Libellés d'accessibilité sur tout élément interactif
- [x] Support de la mise à l'échelle des polices jusqu'à 200 %
- [x] `OfflineBanner` avec seuil de 2 s *(déjà en place au lot 3)*
- [x] Gestion de la session expirée par `Sheet`, avec retour à l'écran quitté
- [x] Occultation dans le sélecteur d'applications — **garantie inégale selon la plateforme**, voir ci-dessous
- [x] **En plus** : `Toggle` du design system, verrou au retour au premier plan

**Vérification** — **278 tests**, toutes passées (36 de plus qu'au lot 8) :

- **Les deux catalogues sont structurellement identiques** : aucune clé française sans traduction anglaise, aucune clé anglaise orpheline, aucune chaîne vide non intentionnelle, **et les mêmes variables d'interpolation des deux côtés** — une clé `{{count}}` d'un côté et `{{n}}` de l'autre passerait tous les autres contrôles
- La bascule de langue traduit aussi ce qui vit **hors des composants** : libellés d'états, messages d'erreur HTTP, en-têtes de date
- Un état inconnu reste affiché en clair dans les deux langues — règle R2
- `system` suit l'appareil, ramène `fr-CI` et `fr_CI` à `fr`, et **replie sur le français, jamais sur l'anglais**
- Corps, libellés et mono montent à 200 % ; les tailles d'affichage sont plafonnées à 130 % — c'est ce plafond qui empêche un montant d'être tronqué
- Une session expirée **conserve le statut `authenticated`** : le parcours n'est pas démonté
- Deux `401` concurrents ne réécrivent pas le chemin de reprise ; celui-ci n'est consommé qu'une fois
- La biométrie ne verrouille jamais quand le matériel manque, et une empreinte retirée depuis l'installation **libère** un verrou déjà posé
- Le profil se reconstitue depuis ses trois sources, et se replie sur l'e-mail sans jamais fabriquer de nom
- Aucun `<Pressable>` de l'application n'est dépourvu de rôle ou de libellé d'accessibilité

**Trois obstacles rencontrés :**

| Obstacle | Résolution |
|---|---|
| **`onSessionExpired` appelait `signOut()`**, ce que le §8.1 de `05-screens.md` interdit explicitement : éjecter vers l'écran de connexion détruit la pile, donc le parcours | La session porte désormais un drapeau `expired` **sans changer de statut**. `SessionExpiredSheet` capture la route courante — `lib/http` ne doit rien savoir de la navigation — et `verify-otp` y revient au lieu de l'accueil |
| **`expo-localization` renvoie la langue de la machine de test.** La suite passait en anglais et 18 assertions de texte tombaient | Mock `fr-FR` dans `jest.setup.ts`. La résolution `system` reste testée pour de vrai, sans couche native, par `resolveLanguage` |
| **L'occultation dans le sélecteur d'applications n'a pas la même force sur les deux plateformes.** Sur iOS, l'instantané est pris par le système au moment de la désactivation : un rendu JavaScript déclenché par `AppState` peut arriver après | `PrivacyShield` couvre l'écran dès l'état `inactive`. Sur Android, `FLAG_SECURE` neutralise déjà la vignette. Sur iOS, la garantie stricte exigerait une vue native posée sur `applicationWillResignActive`. `CONTOURNEMENT(indéterminé)` — à trancher au lot 10, profilage sur appareil à l'appui |

**Trois choix structurants :**

| Point | Décision |
|---|---|
| **Les libellés métier lisent i18next à l'appel, pas au chargement du module** | Une table `const STATE_LABELS = {…}` figée à l'import resterait dans la langue de démarrage. En contrepartie, tout composant qui les rend doit appeler `useTranslation()` — même sans utiliser le `t` renvoyé — pour se re-rendre au changement de langue. La règle est écrite en tête de `features/shared/labels.ts` |
| **Les noms de mois et de jours viennent de `date-fns`, pas d'une table** | Une liste écrite en français resterait française en anglais. Et la semaine française commence le lundi là où l'anglaise commence le dimanche : `DateRangePicker` dérive ses en-têtes d'une semaine de référence, avec la locale active |
| **`Toggle` est écrit, pas repris du `Switch` de React Native** | Celui-ci se peint aux couleurs du système sur Android et ignore les jetons du §2. Un réglage qui ne ressemble pas au reste saute aux yeux précisément là où l'utilisateur compare des lignes entre elles |

**Un défaut réel corrigé dans du code des lots 3, 5 et 6** : `height` fixe sur `Button`, `TransactionRow`, les barres de navigation et les cellules d'OTP. À 200 % de taille de police, un libellé passé sur deux lignes était rogné. Remplacé par `minHeight` ; `TextField` a reçu le plafond de mise à l'échelle qui lui manquait.

**Le masquage du solde se règle à deux endroits** — l'œil de la carte héros et la ligne des réglages. Chacun lisait MMKV dans son propre `useState` : l'accueil, monté en permanence par la barre d'onglets, gardait la valeur lue au lancement. Les deux passent maintenant par `lib/preferences.ts`.

**Le mode validation reste en français.** C'est un outil interne, pas un écran produit : le traduire alourdirait les catalogues de clés que personne ne lira dans une autre langue.

**Bundle** : 6,5 Mo. L'encadré `NFR-23` du lot 5 reste d'actualité.

**Non vérifié ici** : le rendu réel à 200 % sur l'appareil socle, et la vignette du sélecteur d'applications sur iOS. Les deux exigent l'appareil.

---

## Lot 10 — Durcissement ✅ *(sauf ce qui exige l'appareil)*

- [x] Suppression de `console.*` en production — *déjà en place au lot 0 ; vérifié et documenté*
- [x] Épinglage de certificat — **mécanisme en place, inactif faute d'empreintes**, voir ci-dessous
- [x] Passage complet de `08-quality-bar.md` — *les 108 lignes relevées, une par une*
- [x] Analyse du bundle — *`npm run audit:bundle`. Cible de 4 Mo **dépassée** (4,67 Mo) : arbitrage `NFR-23` ci-dessous*
- [ ] Profilage sur l'appareil socle — **exige le matériel**
- [ ] Parcours Maestro de bout en bout — **jamais écrit**
- [ ] Audit d'accessibilité — *partiel : contraste, libellés, mise à l'échelle et hauteurs sont vérifiés automatiquement. L'audit au lecteur d'écran exige l'appareil*

**Vérification** — **307 tests**, toutes passées (8 de plus qu'au lot 9), plus deux audits de build :

```
npm run audit:bundle    modules empaquetés 2 976 · bundle 4,67 Mo · modules devtools 0
npm run audit:pinning   mécanisme en place, INACTIF (aucune empreinte configurée)
```

- La surface publique du substitut de production est comparée à celle du module réel : une divergence ne se verrait qu'en production, à l'exécution
- La règle R1 est vérifiée par inspection de la base de code, plus seulement par relecture
- Aucun `<Pressable>` sans libellé ni rôle d'accessibilité, aucune hauteur fixe sur un conteneur de texte, aucun appel à `/test/**`, aucune écriture de PIN dans un magasin
- Le cache persisté restitue le solde **et son instant de mise à jour** après un démarrage à froid ; un cache de plus de 24 h est jeté, pas affiché
- Le plugin d'épinglage refuse une empreinte mal formée, une empreinte unique, un doublon, une URL en guise de domaine

**Quatre défauts réels trouvés par l'audit, tous corrigés :**

| Défaut | Portée |
|---|---|
| **Les onze modules de `src/devtools/` étaient dans le bundle de production.** Mesuré, pas supposé : sources map à l'appui | Violation directe de `10-validation-mode.md` §12. Une garde à l'exécution ne suffit pas — Metro construit son graphe à partir de la sortie de Babel, avant minification, donc un `import()` gardé reste une arête du graphe. Corrigé par une redirection de résolution vers un substitut inerte, et **vérifié par un script** |
| **`useDelayedLoading` était écrit et testé, mais utilisé nulle part.** Tout squelette apparaissait immédiatement | Violation de `03-motion-and-feel.md` §6.5 : une réponse de 80 ms produisait un clignotement de gabarits. Câblé sur l'accueil, l'activité et le détail |
| **Le cache de requêtes n'était pas persisté.** « Réhydraté avant tout appel réseau » et « consultable hors ligne » étaient faux après un démarrage à froid | L'accueil s'ouvrait vide en mode avion. Persistance MMKV écrite à la main — synchrone, donc réhydratable **avant le premier rendu**, ce que le paquet officiel ne permet pas |
| **L'haptique n'était désactivable nulle part.** L'API existait depuis le lot 3, sans interface | Ligne §2 de la barre de qualité. Interrupteur ajouté aux réglages. Écart de table corrigé au passage : la copie de référence appelait `tap` là où `03-motion-and-feel.md` §3 impose `select` |

**Deux choix structurants :**

| Point | Décision |
|---|---|
| **L'épinglage est déclaratif, sans module natif** | Android par `network_security_config.xml`, iOS par `NSPinnedDomains`. Le système applique alors l'épinglage à **toutes** les connexions, y compris celles des bibliothèques tierces — ce qu'un client HTTP épinglé côté JavaScript ne fait pas. Les constructions sont pures et testées ; le plugin exige **au moins deux empreintes**, service et secours : une seule transforme un incident de certificat en panne du parc installé |
| **Le cache persisté ne contient que le solde et l'historique** | Deux domaines déjà masqués par le serveur. Rien d'autre n'est écrit, et la déconnexion le purge. Un cache de plus de 24 h est jeté : un solde de la veille présenté comme courant est pire que pas de solde |

**Arbitrage `NFR-23`, ouvert depuis le lot 0** : le bundle pèse 4,67 Mo de JavaScript minifié pour un budget de 4 Mo. Le socle non applicatif en représentait déjà 3,7 Mo au lot 0, sans une ligne de code produit — l'application n'ajoute que ~0,97 Mo. Proposition portée en `08-quality-bar.md` §4 : requalifier le budget en « ≤ 1,5 Mo au-dessus du socle », mesuré par `npm run audit:bundle`. **Décision produit, laissée ouverte.**

**Ce qui reste, et pourquoi :**

| Point | Blocage |
|---|---|
| Les 7 mesures de performance du §4 | Exigent l'appareil socle, réseau bridé |
| Les 23 scénarios de validation manuelle du §10 | Exigent l'appareil **et** un backend en fonctionnement |
| Les 21 scénarios backend de `10-validation-mode.md` §11 | Exigent un backend en fonctionnement |
| L'activation de l'épinglage | Exige le domaine de production et ses empreintes SPKI |
| Les parcours Maestro de bout en bout | Jamais écrits — seul élément de ce tableau qui ne dépende d'aucun matériel |
| L'audit d'accessibilité au lecteur d'écran | Exige l'appareil, TalkBack et VoiceOver |

Aucun de ces points n'est un défaut de code.

---

## Ordre de traitement recommandé par lot

1. Types et contrats d'abord.
2. Logique pure et tests unitaires ensuite.
3. Composants sans données.
4. Branchement des données.
5. Animations en dernier, une fois la structure figée.

Animer avant d'avoir figé la structure conduit à refaire les animations. C'est le piège le plus coûteux du projet.
