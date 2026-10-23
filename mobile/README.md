# Kora Mobile

Application mobile du portefeuille Kora, cliente de [`kora-core`](../README.md).
**React Native + Expo SDK 57**, TypeScript strict.

La spécification complète vit dans [`docs/`](./docs/README.md) — commencer par là.

---

## Démarrer

```bash
npm install
cp .env.example .env     # ajuster EXPO_PUBLIC_API_URL si besoin
npm start
```

> ⚠️ **Metro tourne sur le port 8090, pas 8081.**
> Le backend `kora-core` occupe le 8081, qui est aussi le port par défaut de Metro.
> Les scripts npm forcent `--port 8090` — ne pas les contourner en lançant `npx expo start` nu.

### Adresse du backend selon la cible

| Cible | `EXPO_PUBLIC_API_URL` |
|---|---|
| Simulateur iOS, web | `http://localhost:8081` |
| Émulateur Android | `http://10.0.2.2:8081` |
| Appareil physique | `http://<IP-LAN-de-la-machine>:8081` |

Le backend doit tourner : voir [`../CONTRIBUTING.md`](../CONTRIBUTING.md).
Les OTP arrivent dans MailDev sur <http://localhost:1080>.

---

## Commandes

| Commande | Effet |
|---|---|
| `npm start` | Serveur de développement Metro sur 8090 |
| `npm run android` / `ios` / `web` | Démarre et ouvre la cible |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run lint` | ESLint, zéro avertissement toléré |
| `npm test` | Jest |
| **`npm run verify`** | **typecheck + lint + test — à passer avant tout commit** |
| `npm run format` | Prettier en écriture |
| `npm run export:android` | Bundle de production Android dans `dist/` |
| `npm run audit:bundle` | Vérifie qu'aucun module de `src/devtools/` n'est empaqueté, et pèse le bundle |
| `npm run audit:pinning` | Rapporte l'état réel de l'épinglage de certificat |
| **`npm run audit`** | **Les deux audits — à passer avant toute livraison** |

---

## Règles structurantes

Détail complet dans [`docs/README.md`](./docs/README.md) §Règles d'exécution. Les trois qui mordent au quotidien :

1. **Aucune valeur de style littérale hors de `src/theme/`.** Couleurs hexadécimales, `rgba()`, espacements, rayons, tailles de police : le lint les rejette en erreur. `src/theme/` est le seul répertoire exempté.

2. **Aucune bibliothèque de composants tierce.** NativeBase, Tamagui, gluestack, RN Paper, RN Elements sont bannis par le lint. Le design system est écrit à la main — c'est ce qui sépare une app propre d'une app de niveau Revolut.

3. **`TouchableOpacity`, `Animated` de React Native et `LayoutAnimation` sont bannis par le lint.** Utiliser `Pressable` de gesture-handler et Reanimated : toute animation doit tourner sur le thread UI.

---

## État d'avancement

| Lot | Sujet | Statut |
|---|---|---|
| **0** | **Amorçage** | ✅ **fait** |
| **1** | **Socle réseau** | ✅ **fait** — 63 tests |
| **1bis** | **Mode validation** | ✅ **fait** — 8 onglets, 340 tests |
| **2** | **Design system** | ✅ **fait** — 97 tests |
| **3** | **Mouvement et interaction** | ✅ **fait** — 125 tests |
| **4** | **Composants monétaires** | ✅ **fait** — 156 tests |
| **5** | **Authentification** | ✅ **fait** — 177 tests |
| **6** | **Accueil** | ✅ **fait** — 184 tests |
| **7** | **Parcours monétaires** | ✅ **fait** — 206 tests |
| **8** | **Historique et détail** | ✅ **fait** — 242 tests |
| **9** | **Réglages, i18n, finition** | ✅ **fait** — 278 tests |
| **10** | **Durcissement** | ✅ **fait** — 2 audits de build ; profilage appareil, Maestro et audit lecteur d'écran en attente |

Détail des lots : [`docs/07-implementation-plan.md`](./docs/07-implementation-plan.md).

---

## Notes d'implémentation du lot 1bis — mode validation

**Le masquage des secrets vit dans `src/lib/http/instrumentation.ts`, pas dans les devtools.** Le §3 exige `rawPin` masqué « sans exception, même en développement ». La couche HTTP masque **avant** de transmettre l'entrée au journal : le panneau n'a jamais vu un PIN ni un jeton. Ne jamais déplacer cette logique vers `src/devtools/`.

**`lib/http` ne connaît pas `src/devtools`.** Il expose deux emplacements vides — `registerHttpObserver` et `registerHttpSimulator` — et les devtools s'y branchent, comme `registerTokenProvider`. Inverser cette flèche ramènerait le panneau entier dans le bundle de production.

**`initDevtools()` est appelé au chargement du module racine, pas dans un effet.** Les effets d'un enfant se déclenchent avant ceux de son parent, et le portail de session émet son `/auth/refresh` depuis un effet : un branchement par effet manquerait le parcours d'authentification au lancement.

**Le `cURL` copié n'est pas rejouable tel quel** sur un appel porteur d'un secret : `rawPin` y vaut `****`. C'est la contrepartie assumée de la règle de masquage — l'opérateur remplace à la main.

**Le rejeu est réservé aux `GET`.** Sans idempotence serveur, rejouer une écriture depuis un outil de diagnostic est le meilleur moyen de créer un second débit en croyant observer le premier.

**La secousse passe par le menu développeur de React Native.** Un vrai détecteur exigerait `expo-sensors` et une reconstruction native pour économiser un appui. `CONTOURNEMENT(indéterminé)`. L'appui long sur la salutation d'accueil et l'appui triple sur le numéro de version sont exacts.

**Le journal réseau n'est jamais persisté** — 200 entrées en mémoire, et rien sur disque.

## Notes d'implémentation du lot 10

**Une garde à l'exécution ne suffit pas à exclure les devtools du bundle.** Metro construit son graphe de dépendances à partir de la sortie de Babel, **avant** minification : un `import()` derrière `if (!DEV_MODE)` reste une arête du graphe. Mesuré au lot 10 — les onze modules étaient dans le bundle de production. La seule méthode fiable est la redirection de résolution de `metro.config.js` vers `src/devtools/index.production.tsx`. Ne jamais importer `@/devtools/quelque-chose` en profondeur : cela contournerait la redirection. `npm run audit:bundle` le vérifie.

**L'épinglage de certificat est en place mais INACTIF.** Aucune empreinte n'est configurée — le domaine de production n'existe pas encore. C'est une **condition bloquante avant livraison**, rappelée par `npm run audit:pinning`. Les empreintes se posent dans `app.json` → `extra.certificatePinning`, au format SPKI SHA-256 base64, **au minimum deux** : une en service, une de secours.

**Le cache de requêtes est persisté dans MMKV, à la main.** MMKV est synchrone : c'est ce qui permet de réhydrater **avant le premier rendu**, donc avant tout appel réseau. `@tanstack/react-query-persist-client` impose une phase asynchrone qui ne tiendrait pas cette promesse. Seuls le solde et l'historique sont écrits, jamais les jetons ; un cache de plus de 24 h est jeté.

**`npm run verify` ne suffit plus avant une livraison.** Il ne voit pas le bundle. `npm run audit` exporte réellement et inspecte les sources maps.

**Le budget `NFR-23` est dépassé et sa requalification est proposée**, pas décidée : voir `docs/08-quality-bar.md` §4.

## Notes d'implémentation du lot 9

**Les libellés métier lisent i18next à l'appel, pas au chargement du module.** Une table figée à l'import resterait dans la langue de démarrage. Contrepartie : tout composant qui rend `stateLabel`, `outcomeLabel` ou `transactionTypeLabel` doit appeler `useTranslation()` — même sans utiliser le `t` renvoyé — pour se re-rendre au changement de langue. La règle est écrite en tête de `src/features/shared/labels.ts`.

**`fr.json` et `en.json` sont vérifiés structurellement, pas à l'œil.** Un test compare les deux jeux de clés **et les variables d'interpolation de chaque chaîne** : une clé `{{count}}` d'un côté et `{{n}}` de l'autre passerait tous les autres contrôles et casserait à l'exécution.

**Une session expirée ne déconnecte pas.** `markExpired()` pose un drapeau **sans changer le statut** : la pile de navigation et le parcours en cours restent montés derrière la feuille. Ne jamais rétablir `signOut()` dans `onSessionExpired` — c'est exactement ce que `docs/05-screens.md` §8.1 interdit.

**Le mode validation reste en français.** Outil interne, pas écran produit : le traduire alourdirait les catalogues sans lecteur.

**`expo-localization` est simulé en `fr-FR` dans `jest.setup.ts`.** Sans ce mock, la suite passerait en anglais sur un poste anglophone et les assertions de texte deviendraient dépendantes de l'environnement.

**Aucune hauteur fixe sur un conteneur de texte.** À 200 % de taille de police un libellé passe sur deux lignes : `Button`, `TransactionRow`, les barres de navigation et les cellules d'OTP utilisent `minHeight`. Les tailles d'affichage, elles, sont plafonnées à 130 % — c'est ce plafond qui empêche un montant d'être tronqué.

**L'occultation dans le sélecteur d'applications n'est pas garantie sur iOS.** `PrivacyShield` couvre l'écran dès l'état `inactive`, mais l'instantané système peut être pris avant le rendu JavaScript. Android est couvert par `FLAG_SECURE`. `CONTOURNEMENT(indéterminé)`.

## Notes d'implémentation du lot 8

**La clé de requête de l'historique porte la sélection de filtres, pas les bornes résolues.** `toHistoryQuery` calcule `from` à partir de l'instant : le mettre dans la clé fabriquerait une clé neuve à chaque rendu, donc un rechargement en boucle. La clé passe par `serializeFilters`, et les bornes sont résolues dans le `queryFn`, à granularité du jour.

**Le filtre d'état est un choix unique parmi les onze états concrets.** `TransactionFilter.state` n'accepte qu'une valeur — contrat §6.8. Ne jamais filtrer une famille localement : le total et la pagination viennent du serveur et deviendraient faux. Drapeau `API_CAPABILITIES.multiStateFilter`.

**L'écran de détail rejoue la page d'origine.** Faute de `GET /payments/{id}`, la navigation transporte `page` et `filters`, et le détail rejoue exactement cette requête avec `detail=true`, en élargissant à `page-1` et `page+1` avant d'abandonner. Contrat §6.7, drapeau `API_CAPABILITIES.transactionById`.

**Les transitions d'élément partagé sont indisponibles**, et ce n'est pas un choix : Reanimated 4.5.1 les garde derrière le drapeau statique `ENABLE_SHARED_ELEMENT_TRANSITIONS`, à `false`, et elles restent expérimentales sur la nouvelle architecture. Le repli est une entrée en cascade — 12 dp, 60 ms de décalage par bloc. `CONTOURNEMENT(indéterminé)`.

**Le mode validation s'ouvre depuis les réglages, provisoirement.** Les vrais déclencheurs — secousse, appui long sur le logo, triple appui sur la version — relèvent du lot 1bis. `openDevtools()` est le point d'entrée qu'ils appelleront ; rien d'autre n'est à changer côté panneau.

**Le détecteur de dérive suit les `$ref`, il ne devine aucun nom de schéma.** springdoc nomme ses schémas d'après les classes Java : toute table de noms attendus serait fausse au premier renommage backend.

## Notes d'implémentation du lot 7

**Le verrou anti-double-débit a quatre couches**, et une seule fait autorité : le `useRef` de `useSubmitPayment`. Un `useState` est asynchrone — deux appuis à 50 ms d'intervalle passeraient tous les deux avant le re-rendu. Ne jamais remplacer ce `ref` par un état.

**La clé d'idempotence est armée à l'entrée du récapitulatif**, jamais à l'envoi, et `armIdempotency()` est idempotent. Elle doit survivre à un rejeu manuel, sinon ce rejeu créera une seconde opération le jour où l'étape 4 backend atterrit.

**Sur « issue incertaine », l'action principale est `Vérifier l'historique`.** Le rejeu est secondaire et précédé d'un avertissement. C'est délibéré : sans idempotence serveur, un rejeu peut débiter deux fois.

**`expo-crypto` et `expo-haptics` sont mockés globalement dans `jest.setup.ts`.** Sans le premier, `randomUUID()` renvoie `undefined` sous l'auto-mock de jest-expo : plus de clé d'idempotence, et `useSubmitPayment` sort silencieusement en amont. Le symptôme — des tests qui échouent sans message exploitable — ne désigne pas sa cause.

## Notes d'implémentation du lot 6

**Le solde et l'historique sont deux requêtes indépendantes.** Ne jamais les fusionner : un échec de l'une dégraderait l'autre section, ce que `docs/05-screens.md` §3 interdit explicitement. Deux `useQuery`, deux frontières d'erreur.

**TanStack Query est configuré avec `retry: false`.** La reprise appartient au client HTTP, qui connaît la seule règle qui compte : jamais sur `POST /payments/*`. La rejouer ici la multiplierait et contournerait la garantie.

**La revalidation au premier plan ne se déclenche qu'au-delà de 30 s** d'arrière-plan. Revalider à chaque bascule grèverait le budget data de `NFR-25`.

**En test, un `waitFor` sur une erreur de lecture a besoin de 5 s** : le client HTTP reprend un `GET` 3 fois avec un recul de 300 + 600 ms. Une attente d'une seconde échoue sur un comportement correct.

## Notes d'implémentation du lot 5

**L'e-mail inconnu et le PIN erroné rendent le même message.** Le backend les distingue (`404` contre `401`), l'application non — propager cette distinction offrirait un oracle d'énumération de comptes. Ne jamais « améliorer » `CREDENTIALS_MESSAGE` en le rendant plus précis.

**`src/features/auth/otpFlow.ts` détient un PIN en clair.** C'est le seul endroit de l'application, et c'est imposé par le contrat : aucun endpoint de renvoi d'OTP n'existe, donc le renvoi doit rappeler `/auth/login`. Portée module, jamais un état React, effacé à toute sortie du parcours.

**Le rafraîchissement au démarrage est explicite**, dans `session.bootstrap()`. Le client HTTP ne rafraîchit que sur un `401` en vol ; au démarrage aucune requête n'a encore eu lieu.

**Les hrefs typés d'expo-router élident les groupes** : `router.push('/login')`, jamais `'/(public)/login'`. Les types viennent de `.expo/types/router.d.ts`, régénéré par le serveur de développement — s'ils sont périmés, les erreurs TypeScript sont illisibles.

> ⚠️ **Le bundle de production dépasse `NFR-23`** : 5,6 Mo contre 4 Mo de budget, à cause de `react-navigation` tiré par `<Stack>`. Ce n'est pas une régression à corriger mais un budget à requalifier — voir `docs/07-implementation-plan.md`, lot 5.

## Notes d'implémentation du lot 4

**`Amount` est le rendu canonique de tout montant.** Aucun montant ne s'affiche autrement. C'est aussi le seul composant autorisé à composer de la typographie à la main — le symbole de devise à `0,45×` vient de `currencySymbolStyle(variant)`, jamais d'une taille choisie à part.

**Le compteur de solde tourne sur le thread UI**, via `animatedProps` sur un `TextInput` non éditable. Ne pas le réécrire avec un `useState` : 60 rendus React par seconde décrocheraient sur l'appareil socle. Le groupement des milliers y est fait par boucle explicite — `String.replace` avec un `RegExp` n'est pas fiable dans un worklet.

**Le PIN ne quitte jamais un `useRef`.** `PinPad` bloque la capture d'écran tant qu'il est monté, et son état React ne porte que le *nombre* de pastilles remplies, jamais un chiffre.

**`StateTimeline` est le différenciateur produit.** Le pouls continu du nœud courant est ce qui transforme une donnée d'audit en information vivante — sans lui, la frise n'est qu'un tableau.

**Ajuster un état quand une propriété change se fait pendant le rendu**, pas dans un effet (`OtpInput`). Les règles du React Compiler le signalent, et elles ont raison : un effet ferait afficher l'ancien état une image de trop.

## Notes d'implémentation du lot 3

**`Pressable` est le socle de toute interaction.** Il passe par `Gesture.Tap()` de gesture-handler, jamais par un `Touchable*` : ces derniers passent par le pont JS et, sous charge, le retour arrive avec 200 ms de retard. Ici la compression et l'opacité vivent sur le thread UI et répondent même si JavaScript est bloqué.

**L'haptique est prescriptive, pas décorative.** `src/lib/haptics.ts` étrangle globalement à 50 ms — deux impulsions plus rapprochées se ressentent comme une vibration parasite. Ne jamais déclencher d'haptique sur un événement non provoqué par l'utilisateur.

**Aucun indicateur circulaire dans l'application.** `useDelayedLoading` impose le seuil de 200 ms : en deçà, un squelette qui apparaît puis disparaît se lit comme un clignotement, et l'attente perçue devient pire que l'attente réelle.

### Pièges d'outillage à connaître

- **RNTL 14 : `render`, `renderHook` et `rerender` sont asynchrones** (React 19). Il faut `await` les trois. Un oubli donne « getByText is not a function » ou `result.current === null`.
- **Les faux timers polluent les rendus des suites voisines.** Tout test à `jest.useFakeTimers()` vit dans son propre fichier, et avance via `await act(async () => …)`.
- **`react-hooks/immutability` est désactivée pour les composants.** Elle ne modélise pas les `SharedValue` de Reanimated, qui sont écrites depuis un effet et depuis un worklet — l'usage prévu par leur conception. Ne pas la réactiver globalement.
- **Reanimated en test passe par `react-native-worklets/jest/resolver`**, déclaré dans `package.json`. Sans lui, le runtime natif est requis et tout échoue au chargement.

## Notes d'implémentation du lot 2

**`src/theme/tokens.ts` est désormais le design system complet.** C'est la seule source légitime de valeurs de style : le lint rejette en erreur toute couleur, tout espacement, tout rayon et toute taille de police écrits ailleurs.

**Le contraste est vérifié par le test, pas par l'œil.** `src/theme/__tests__/contrast.test.ts` calcule le ratio WCAG de chaque paire des deux thèmes, en composant les lavis translucides sur leur fond réel. Il a trouvé un vrai défaut à sa première exécution — blanc sur l'accent clair à 3,22:1. Toute nouvelle couleur doit y passer.

**`motion.ts` est de la donnée pure, `easing.ts` porte le runtime.** Les courbes d'accélération sont des valeurs Reanimated, donc dépendantes du natif ; les jetons de ressort et de durée ne le sont pas. Les séparer garde `@/theme` importable partout. Ne pas réintroduire d'import de valeur Reanimated dans `motion.ts`.

**`render` de RNTL 14 est asynchrone** (React 19). Tout test de rendu doit `await`.

## Notes d'implémentation du lot 1

**Les trois invariants que le code fait respecter structurellement**, et qu'il ne faut jamais contourner :

1. **Un `401` de PIN ne déclenche jamais de rafraîchissement de jeton.** `isTokenExpiry()` (`src/lib/http/errors.ts`) discrimine sur la forme du corps : `{status, error}` = jeton, `ProblemDetail` avec `detail` = PIN ou OTP. Les confondre produit une boucle de rafraîchissement infinie ou une déconnexion à chaque PIN raté.

2. **`POST /payments/*` n'est jamais rejoué automatiquement.** La politique de reprise du client refuse structurellement toute écriture monétaire, quelle que soit la cause. Tant que `API_CAPABILITIES.idempotency` vaut `false`, un rejeu peut débiter deux fois.

3. **Aucun type `Api*` ne sort de `src/features/*/api.ts`.** Les écrans et les hooks ne connaissent que `src/types/domain.ts`. Quand le backend renomme un champ, seuls les mappeurs changent.

`src/lib/http/session.ts` expose `registerTokenProvider()` : c'est le point où le magasin de session se branchera au lot 5, sans créer de cycle `http → store → http`.

## Notes d'implémentation du lot 0

**`app/index.tsx` reste provisoire** : il affiche la galerie du design system en développement, et sera remplacé par le portail de session `(gate)` au lot 5.

**Polices.** Les quatre graisses d'Inter sont copiées dans `assets/fonts/` et chargées par `require()`. Ne pas revenir à un import depuis la racine de `@expo-google-fonts/inter` : ce paquet réexporte 18 graisses et le graphe Metro les embarque toutes (7 Mo d'assets au lieu de 2,4 Mo).

**`overrides.react-dom`.** Le paquet `react-dom@19.2.8` tiré transitivement par `expo-router` exige `react@^19.2.8`, alors qu'Expo SDK 57 épingle `react@19.2.3`. L'override aligne `react-dom` sur `19.2.3`. À retirer quand l'amont sera cohérent.
