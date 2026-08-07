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

## Lot 1bis — Mode validation

**Objectif** : rendre le backend observable. Écrit tôt parce qu'il sert à tous les lots suivants.

- [ ] `src/devtools/` avec `DEV_MODE`, import dynamique, exclusion au secouage
- [ ] Ouverture par secousse et par appui long sur le logo
- [ ] **Inspecteur réseau** — 200 entrées, détail requête/réponse, `rawPin` masqué
- [ ] `Copier en cURL` sur chaque entrée
- [ ] Signaux visuels : erreur, lenteur, rafraîchissement de jeton, rejeu
- [ ] Bascule d'environnement avec purge des jetons et test de connectivité
- [ ] Inspecteur de session — claims décodés, compte à rebours d'expiration, actions d'invalidation
- [ ] Simulation client : latence forcée, coupure réseau, statut de réponse imposé

Reporté après le lot 8 : détecteur de dérive de contrat, inspecteur de transactions, journal de scénarios.

**Vérification** :
- L'inspecteur capture un parcours d'authentification complet, PIN masqué partout
- Le `cURL` copié rejoue l'appel à l'identique depuis un terminal
- Le changement d'environnement purge bien SecureStore et le cache
- Le bundle de production ne contient aucun module de `src/devtools/`

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

## Lot 8 — Historique et détail

- [ ] `FlashList` avec groupement par jour et en-têtes collants
- [ ] Défilement infini, `size=20`, déclenchement à 80 %
- [ ] Panneau de filtres en `Sheet`, application en direct
- [ ] Sérialisation des dates en ISO-8601 UTC
- [ ] Écran de détail avec `StateTimeline` animée
- [ ] Transition partagée liste → détail — ou son repli imposé
- [ ] Copie de la référence par appui long
- [ ] Sondage sur les opérations en cours
- [ ] **Devtools** : inspecteur de transactions — états bruts, durées inter-états, suivi forcé à 2 s
- [ ] **Devtools** : détecteur de dérive contre `/v3/api-docs`
- [ ] **Devtools** : journal de scénarios, export Markdown

**Vérification** :
- 200 lignes défilent à 60 fps sur l'appareil socle
- Les filtres combinés produisent la bonne requête, dates comprises
- Une opération en cours voit sa frise se compléter en direct
- La transition partagée ne produit aucune image fixe

---

## Lot 9 — Réglages, i18n, finition

- [ ] Écran de profil, données reconstruites selon le contrat §6.3
- [ ] Bascules de thème, de langue, de masquage par défaut
- [ ] Biométrie *(P1)*
- [ ] Déconnexion avec purge intégrale
- [ ] `fr.json` et `en.json` complets — **zéro chaîne en dur**
- [ ] Libellés d'accessibilité sur tout élément interactif
- [ ] Support de la mise à l'échelle des polices jusqu'à 200 %
- [ ] `OfflineBanner` avec seuil de 2 s
- [ ] Gestion de la session expirée par `Sheet`, avec retour à l'écran quitté
- [ ] Occultation dans le sélecteur d'applications

**Vérification** :
- Le basculement de langue ne laisse apparaître aucune chaîne non traduite
- À 200 % de taille de police, aucun montant n'est tronqué
- Une session expirée pendant un parcours ramène exactement à l'étape quittée

---

## Lot 10 — Durcissement

- [ ] Suppression de `console.*` en production
- [ ] Épinglage de certificat
- [ ] Passage complet de `08-quality-bar.md`
- [ ] Profilage sur l'appareil socle
- [ ] Analyse du bundle, cible < 4 Mo
- [ ] Parcours Maestro de bout en bout
- [ ] Audit d'accessibilité

---

## Ordre de traitement recommandé par lot

1. Types et contrats d'abord.
2. Logique pure et tests unitaires ensuite.
3. Composants sans données.
4. Branchement des données.
5. Animations en dernier, une fois la structure figée.

Animer avant d'avoir figé la structure conduit à refaire les animations. C'est le piège le plus coûteux du projet.
