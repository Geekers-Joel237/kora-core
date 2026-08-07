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
| 1bis | Mode validation | ⬜ |
| **2** | **Design system** | ✅ **fait** — 97 tests |
| **3** | **Mouvement et interaction** | ✅ **fait** — 125 tests |
| **4** | **Composants monétaires** | ✅ **fait** — 156 tests |
| **5** | **Authentification** | ✅ **fait** — 177 tests |
| **6** | **Accueil** | ✅ **fait** — 184 tests |
| **7** | **Parcours monétaires** | ✅ **fait** — 206 tests |
| 8 | Historique et détail | ⬜ |
| 9 | Réglages, i18n, finition | ⬜ |
| 10 | Durcissement | ⬜ |

Détail des lots : [`docs/07-implementation-plan.md`](./docs/07-implementation-plan.md).

---

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
