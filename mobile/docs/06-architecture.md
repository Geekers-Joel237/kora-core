# Kora Mobile — Architecture technique

---

## 1. Pile

| Domaine | Choix | Justification |
|---|---|---|
| Cadre | **Expo SDK 54+**, workflow managé, `expo-dev-client` | Modules natifs disponibles sans éjection, mises à jour OTA |
| Langage | **TypeScript**, mode `strict` | `any` interdit hors déclaration de module tierce |
| Navigation | **expo-router**, version imposée par le SDK | Routage par fichiers, cohérent avec la carte de `05-screens.md`. **Ne pas épingler de version majeure à la main** — chaque SDK Expo est apparié à une majeure d'`expo-router` ; prendre celle que `npx expo install expo-router` résout |
| État serveur | **TanStack Query v5** | Cache, revalidation, invalidation, déduplication |
| État client | **Zustand** | Session, préférences, état de parcours. Pas de Redux |
| HTTP | **`fetch` natif enveloppé** dans `src/lib/http/` | Zéro dépendance, intercepteurs écrits à la main — ils sont de toute façon spécifiques (double `401`, vol groupé). Ni `ky`, ni `axios` |
| Animation | **Reanimated v3** + **Gesture Handler v2** | Thread UI obligatoire |
| Listes | **@shopify/flash-list** | Virtualisation supérieure à `FlatList`. L'API de dimensionnement diffère entre v1 (`estimatedItemSize` requis) et v2 (automatique) — se conformer à celle installée |
| Stockage sécurisé | **expo-secure-store** | Keychain iOS / Keystore Android |
| Stockage local | **react-native-mmkv** | Synchrone, 30× plus rapide qu'AsyncStorage |
| Biométrie | **expo-local-authentication** | |
| Haptique | **expo-haptics** | |
| SVG | **react-native-svg** | Icônes et frises |
| i18n | **i18next** + **expo-localization** | |
| Formulaires | **react-hook-form** + **zod** | Validation typée, alignée sur les contraintes du contrat |

**Explicitement bannis** : NativeBase, Tamagui, gluestack, React Native Paper, React Native Elements, Redux, MobX, `Animated` de React Native, `TouchableOpacity`, moment.js, lodash.

---

## 2. Arborescence

```
mobile/
├── app/                              expo-router — routes uniquement
│   ├── _layout.tsx                   fournisseurs racine, chargement des polices, splash
│   ├── (gate)/index.tsx
│   ├── (public)/
│   │   ├── onboarding.tsx
│   │   ├── login.tsx
│   │   ├── register.tsx
│   │   └── verify-otp.tsx
│   ├── (app)/
│   │   ├── _layout.tsx               garde d'authentification
│   │   ├── (tabs)/
│   │   │   ├── _layout.tsx
│   │   │   ├── home.tsx
│   │   │   ├── activity.tsx
│   │   │   └── settings.tsx
│   │   ├── deposit/[step].tsx
│   │   ├── withdraw/[step].tsx
│   │   ├── send/[step].tsx
│   │   └── transaction/[id].tsx
│   └── +not-found.tsx
│
├── src/
│   ├── theme/                        tokens · motion · ThemeProvider · useTheme
│   ├── components/                   voir 04-components.md
│   ├── features/                     découpage vertical par domaine
│   │   ├── auth/                     api · hooks · store · schemas
│   │   ├── wallet/                   api · hooks (balance)
│   │   ├── payments/                 api · hooks · flow-store · schemas
│   │   └── history/                  api · hooks · filters
│   ├── lib/
│   │   ├── http/                     client · interceptors · errors
│   │   ├── money/                    format · parse · currency
│   │   ├── storage/                  secure · mmkv
│   │   ├── haptics.ts
│   │   └── datetime.ts
│   ├── i18n/                         fr.json · en.json
│   └── types/                        api.ts · domain.ts
│
├── assets/                           fonts/Inter-*.ttf · icons/*.svg · illustrations/
└── docs/                             ce répertoire
```

**Règle de dépendance** : `components/` ne connaît jamais `features/`. Un composant reçoit des données par ses propriétés. `features/` compose les composants. `app/` ne contient que du routage et de la composition — aucune logique métier.

---

## 3. Couche HTTP

### 3.1 Client

```ts
// src/lib/http/client.ts
const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8081';

// Chaîne d'intercepteurs
//  requête  : injection du Bearer si présent, X-Correlation-Id (UUID v4)
//  réponse  : normalisation de l'erreur → KoraError
//  401      : rafraîchissement en vol groupé, un seul rejeu
```

### 3.2 Erreur normalisée

Le backend produit **trois formats d'erreur distincts** (contrat §5.1) — `ProblemDetail`, `{status, error}` de la couche sécurité, et le corps Spring par défaut sur les exceptions non mappées comme `ProviderException`. Un corps vide ou non-JSON reste possible. Le client les ramène tous à un type unique, sans jamais planter.

```ts
export type KoraError = {
  status: number;
  code: KoraErrorCode;         // dérivé du couple (statut, detail)
  message: string;             // traduit, prêt à l'affichage
  detail?: string;             // brut, pour le support
  violations?: { field: string; message: string }[];
  isRetryable: boolean;
  isAuthExpired: boolean;      // vrai UNIQUEMENT pour un 401 de jeton
  isOutcomeUnknown: boolean;   // 503, 500, ou absence de réponse sur /payments/*
};
```

`isOutcomeUnknown` est la seule condition qui route vers l'écran « issue incertaine ». Elle est calculée dans la couche HTTP, jamais dans un écran.

### 3.3 Le point le plus délicat — discriminer les deux `401`

```ts
function isTokenExpiry(status: number, body: unknown): boolean {
  if (status !== 401) return false;
  // Forme produite par la couche de sécurité Spring : { status, error }
  // Forme produite par le GlobalExceptionHandler : ProblemDetail avec 'detail'
  return typeof body === 'object' && body !== null
    && 'error' in body && !('detail' in body);
}
```

Un `401` portant un `detail` provient d'une `PinValidationException`, d'une `InvalidOtpException` ou d'une `OtpExpiredException`. **Il ne doit jamais déclencher de rafraîchissement de jeton.** Se tromper ici produit soit une boucle de rafraîchissement infinie, soit une déconnexion à chaque PIN erroné.

### 3.4 Rafraîchissement en vol groupé

Trois requêtes qui reçoivent `401` en même temps ne doivent déclencher **qu'un seul** `POST /auth/refresh`.

```ts
let refreshInFlight: Promise<Tokens> | null = null;

async function refreshTokens(): Promise<Tokens> {
  refreshInFlight ??= doRefresh().finally(() => { refreshInFlight = null; });
  return refreshInFlight;
}
```

Un rejeu unique par requête. Un second `401` après rafraîchissement déclenche la déconnexion.

### 3.5 Politique de reprise

| Type de requête | Reprise |
|---|---|
| `GET` | 3 tentatives, recul exponentiel `300 · 2ⁿ` ms + gigue |
| `POST /auth/*` | Aucune |
| **`POST /payments/*`** | **Aucune. Jamais. Sous aucune condition** |

Aucune clé d'idempotence n'existe côté serveur : un rejeu automatique peut débiter deux fois. Le rejeu est toujours une décision explicite de l'utilisateur, prise sur l'écran de résultat.

---

## 4. État

### 4.1 Répartition

| Nature | Outil | Persistance |
|---|---|---|
| Jetons | Zustand + SecureStore | Trousseau matériel |
| Profil (nom, téléphone) | Zustand + MMKV | Local, en clair |
| Préférences | Zustand + MMKV | Local |
| Solde | TanStack Query | Cache mémoire + MMKV |
| Historique | TanStack Query | Cache mémoire + MMKV |
| État de parcours de paiement | Zustand, éphémère | Aucune |
| PIN | `useRef` | **Aucune. Jamais persisté** |

### 4.2 Clés de requête

```ts
export const qk = {
  balance: ['balance'] as const,
  history: (f: HistoryFilters) => ['history', f] as const,
  transaction: (id: string) => ['transaction', id] as const,
};
```

Après toute opération monétaire réussie : invalidation de `qk.balance` et de tout `['history', …]`.

### 4.3 Durées de cache

| Requête | `staleTime` | `gcTime` |
|---|---|---|
| Solde | 30 s | 5 min |
| Historique | 60 s | 10 min |

### 4.4 État de parcours de paiement

```ts
type PaymentFlowState = {
  kind: 'deposit' | 'withdraw' | 'send' | null;
  amountMinor: number;
  currency: string;
  method: string | null;
  recipientPhone: string | null;
  verifiedRecipient: boolean;      // case cochée du récapitulatif
  submitting: boolean;             // verrou d'affichage, NON autoritaire
  reset(): void;
};
```

Le parcours se réinitialise à l'entrée et à la sortie. `submitting` pilote l'interface, mais **le verrou faisant autorité est un `useRef` hors du cycle de rendu** — voir §5.

---

## 5. Prévention du double débit

Aucune clé d'idempotence côté serveur (contrat §6.1). La protection est intégralement client, et elle a quatre couches.

```ts
// src/features/payments/useSubmitPayment.ts
const lock = useRef(false);

const submit = useCallback(async (pin: string) => {
  if (lock.current) return;              // couche 1 — verrou hors rendu
  lock.current = true;
  setSubmitting(true);                   // couche 2 — verrouillage visuel

  try {
    const tx = await postPayment(payload);
    router.replace(resultRoute(tx));     // couche 3 — remplacement de pile
  } catch (e) {
    router.replace(errorRoute(e));
  } finally {
    lock.current = false;
    setSubmitting(false);
  }
}, [payload]);
```

Quatrième couche : le geste de retour est neutralisé pendant l'exécution.

```ts
useEffect(() => {
  const sub = BackHandler.addEventListener('hardwareBackPress', () => submitting);
  navigation.setOptions({ gestureEnabled: !submitting });
  return () => sub.remove();
}, [submitting]);
```

Le `useRef` est indispensable : un `useState` est asynchrone, et deux appuis à 50 ms d'intervalle passent tous les deux avant le re-rendu.

---

## 6. Sécurité

### 6.1 Stockage des secrets

| Donnée | Emplacement |
|---|---|
| Jeton d'accès, jeton de rafraîchissement | `expo-secure-store` — Keychain / Keystore |
| PIN | **Nulle part.** `useRef`, effacé dans le `finally` |
| Profil | MMKV en clair — données non sensibles |
| Préférences | MMKV en clair |

### 6.2 Mesures d'écran

```ts
// Écrans de PIN et d'OTP
useEffect(() => {
  ScreenCapture.preventScreenCaptureAsync();
  return () => { ScreenCapture.allowScreenCaptureAsync(); };
}, []);
```

Occultation dans le sélecteur d'applications : `expo-screen-capture` sur Android, superposition floutée déclenchée sur `AppState` `inactive` sur iOS.

### 6.3 Journalisation

En compilation de production : `console.*` est retiré par `babel-plugin-transform-remove-console`.
Ne jamais journaliser, même en développement : un jeton, un PIN, un montant, un identifiant client.

### 6.4 Production

| Mesure | Statut |
|---|---|
| Épinglage de certificat | Requis (`expo-build-properties`) |
| HTTPS exclusif | Requis — `http` autorisé uniquement pour `localhost` en développement |
| Détection de root / jailbreak | P1 |
| Obscurcissement du code | P1 |

---

## 7. Réseau et hors-ligne

### 7.1 Détection

`@react-native-community/netinfo` alimente un magasin Zustand. `OfflineBanner` s'affiche lorsque l'état est hors ligne **depuis plus de 2 secondes** — en dessous, un micro-changement d'état produirait un clignotement.

### 7.2 Lecture hors ligne

Le persisteur de TanStack Query s'appuie sur MMKV. Au démarrage, le cache est réhydraté avant tout appel réseau. L'utilisateur voit ses dernières données connues immédiatement, avec la mention « Mis à jour il y a X ».

### 7.3 Écriture hors ligne

**Non prise en charge.** Aucune file d'attente d'opérations. Les actions monétaires sont désactivées hors connexion, avec une explication claire.

Justification : une file d'attente d'opérations financières sans idempotence côté serveur est un risque de double débit. Le refus est ici la position correcte.

---

## 8. Formatage

### 8.1 Montants

```ts
// src/lib/money/format.ts
export function formatMinor(minor: number, code: CurrencyCode, opts?: {
  sign?: 'auto' | 'always' | 'never';
  hidden?: boolean;
}): { sign: string; integer: string; symbol: string };
```

Retourne les **trois blocs séparés** attendus par le composant `Amount` — le formatage ne produit jamais une chaîne unique. Séparateur : espace fine insécable `U+202F`. Signe négatif : `U+2212`.

### 8.2 Dates

Le backend émet exclusivement en ISO-8601 UTC. Toute date est convertie dans le fuseau de l'appareil à l'affichage, et reconvertie en UTC à l'envoi.

| Contexte | Format |
|---|---|
| Ligne d'historique | `11:42` si aujourd'hui, sinon `6 août` |
| En-tête de section | `Aujourd'hui` · `Hier` · `6 août 2026` |
| Détail | `6 août 2026, 11:42` |
| Frise | `11:42:13` |
| Paramètre d'API | `2026-08-06T00:00:00Z` |

`date-fns` avec la locale `fr` et l'import sélectif. Pas de moment.js.

---

## 9. Variables d'environnement

```
EXPO_PUBLIC_API_URL=http://localhost:8081
EXPO_PUBLIC_ENV=development
```

Émulateur Android : `http://10.0.2.2:8081`. Appareil physique : l'adresse IP locale de la machine hôte.
Aucun secret dans les variables `EXPO_PUBLIC_*` — elles sont incluses dans le bundle.

> **⚠️ Collision de port.** `kora-core` écoute sur **8081**, qui est aussi le port par défaut de Metro. Les deux ne peuvent pas coexister. **Metro tourne sur 8090**, forcé par les scripts npm. Lancer `npx expo start` sans `--port 8090` bloque le serveur de développement sur un prompt non interactif.

### Notes d'empaquetage

| Point | Décision |
|---|---|
| Polices | Les 4 graisses d'Inter sont copiées dans `assets/fonts/` et chargées par `require()`. **Ne jamais importer depuis la racine de `@expo-google-fonts/inter`** : ce paquet réexporte 18 graisses et Metro les embarque toutes — 7 Mo d'assets au lieu de 2,4 Mo |
| `react-dom` | Override à `19.2.3`. `expo-router` tire transitivement `react-dom@19.2.8` qui exige `react@^19.2.8`, alors qu'Expo SDK 57 épingle `react@19.2.3`. À retirer quand l'amont sera cohérent |
| `babel.config.js` | Requis pour `transform-remove-console` en production, donc `babel-preset-expo` doit être une devDependency explicite — le template SDK 57 ne livre plus de fichier Babel |
| Plugin Reanimated | Reanimated 4 : le plugin Babel est `react-native-worklets/plugin`, **plus** `react-native-reanimated/plugin`. Il doit rester le dernier de la liste |

---

## 10. Tests

| Niveau | Outil | Portée |
|---|---|---|
| Unitaire | Jest | Formatage monétaire, dates, normalisation d'erreur, transitions d'état |
| Composant | React Native Testing Library | `PinPad`, `AmountKeypad`, `TransactionRow`, `StateTimeline` |
| Intégration | MSW | Parcours complets contre des réponses simulées, y compris les erreurs |
| Bout en bout | Maestro | Inscription → OTP → dépôt → transfert → historique |

Scénarios de défaillance obligatoirement couverts :

- Double appui sur `Confirmer` → **une seule** requête émise
- `401` de jeton pendant un paiement → rafraîchissement, rejeu unique, l'utilisateur ne voit rien
- `401` de PIN → **aucun** rafraîchissement, erreur affichée
- `503` sur un paiement → écran « issue incertaine », aucun rejeu
- Perte de réseau en plein transfert → écran « issue incertaine »
- `422` solde insuffisant → message traduit, montant conservé au retour
