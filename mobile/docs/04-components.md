# Kora — Catalogue de composants

Composants primitifs de l'application. Tout écran se construit **exclusivement** à partir d'eux. Un écran qui introduit une disposition ad hoc que ce catalogue couvre déjà est un défaut de revue.

Aucune bibliothèque tierce. Tout est écrit à la main sur `View`, `Text`, `react-native-svg` et `react-native-reanimated`.

---

## 1. Arborescence

```
src/components/
├── primitives/       Pressable · Text · Surface · Divider · Spacer · Icon
├── feedback/         Skeleton · Toast · EmptyState · ErrorState · OfflineBanner
├── money/            Amount · BalanceHero · AmountKeypad · CurrencyBadge
├── input/            PinPad · OtpInput · TextField · PhoneField · Segmented · Toggle
├── display/          Card · ListRow · TransactionRow · StatusChip · Avatar
│                     · SectionHeader · StateTimeline · MethodPicker
├── action/           Button · IconButton · ActionTile · ActionBar
└── overlay/          Sheet · Dialog · Menu
```

---

## 2. Primitives

### `Pressable`

Le socle de toute interaction. Enveloppe `Gesture.Tap()`, applique la compression du §4 de `03-motion-and-feel.md`, déclenche l'haptique, étend la zone tactile à 44 dp.

```ts
type PressableProps = {
  onPress: () => void;
  hapticStyle?: 'tap' | 'select' | 'press' | 'commit' | 'none';  // défaut 'press'
  pressScale?: number;            // défaut 0.97
  disabled?: boolean;
  children: React.ReactNode;
};
```

`TouchableOpacity` est banni de la base de code.

### `Text`

Seule voie d'accès au texte. Refuse toute propriété `style` contenant `fontSize`, `fontWeight`, `color` ou `letterSpacing` en dur.

```ts
type TextProps = {
  variant: keyof typeof type;                    // 'bodyMd', 'titleLg', ...
  color?: 'primary'|'secondary'|'tertiary'|'disabled'|'onAccent'|'danger'|'success';
  numberOfLines?: number;
  children: React.ReactNode;
};
```

### `Surface`

Conteneur porteur d'un niveau d'élévation. Résout la couleur de fond, le rayon et la bordure éventuelle depuis les tokens.

```ts
type SurfaceProps = {
  elevation?: 0 | 1 | 2 | 3 | 4;   // défaut 1
  radius?: keyof typeof radius;    // défaut 'lg'
  padding?: keyof typeof space;    // défaut 4
  bordered?: boolean;              // défaut false — voir principe 1.5 du design system
};
```

---

## 3. Argent

### `Amount`

Rendu canonique de tout montant dans l'application. Aucun montant ne s'affiche autrement.

```ts
type AmountProps = {
  minor: number;                        // entier, plus petite unité — jamais un flottant
  currency: 'XOF' | 'XAF' | 'EUR';
  size: 'displayXl'|'displayLg'|'displayMd'|'titleLg'|'bodyLg'|'bodyMd';
  sign?: 'auto' | 'always' | 'never';   // 'auto' : − si négatif, rien sinon
  direction?: 'INBOUND' | 'OUTBOUND';   // pilote la couleur, cf. design system §2.5
  hidden?: boolean;                     // rend '•••• F'
  animate?: boolean;                    // compteur animé, cf. motion §6.1
};
```

Applique intégralement les règles de composition du §3.4 du design system : trois blocs typographiques, espace fine insécable, `U+2212`, chiffres tabulaires, symbole à `0,45×`.

### `BalanceHero`

Carte de solde de l'accueil.

| Élément | Spécification |
|---|---|
| Fond | `bg.surface1`, `radius.xl`, padding `space.6` |
| Libellé | « Solde disponible », `labelMd`, `text.secondary` |
| Montant | `Amount` en `displayXl`, `animate` actif |
| Numéro de compte | `mono.md`, `text.tertiary`, copiable par appui long |
| Bouton de masquage | `IconButton` `eye` / `eye-off`, en haut à droite |
| Comportement au défilement | Compression du §6.6 de `03-motion-and-feel.md` |
| État de chargement | Squelette de forme identique — jamais un indicateur |

### `AmountKeypad`

Pavé de saisie de montant plein écran. **Aucun `TextInput` n'est utilisé pour un montant.**

| Élément | Spécification |
|---|---|
| Disposition | Grille 3×4 : `1–9`, `000`, `0`, `⌫` |
| Touche | 72 dp de haut, `radius.md`, texte `titleLg` |
| Appui | Échelle `0,90`, `haptic.tap`, fond `overlay.press` |
| `⌫` appui long | Efface tout, `haptic.press` |
| Affichage du montant | Voir §6.2 de `03-motion-and-feel.md` |
| Montants rapides | Trois puces au-dessus du pavé : `5 000`, `10 000`, `25 000` |
| Contrainte de solde | `maxMinor` optionnel — le dépassement déclenche secousse + coloration |
| Solde restant | Affiché en `bodySm` sous le montant, mis à jour en direct |

---

## 4. Saisie

### `PinPad`

Composant le plus critique de l'application : chaque opération monétaire y passe.

```ts
type PinPadProps = {
  length?: number;                     // défaut 4, max 8
  onComplete: (pin: string) => void;
  error?: string | null;               // non nul → secousse + réinitialisation
  loading?: boolean;
  title: string;
  subtitle?: string;
  biometricEnabled?: boolean;
};
```

| Élément | Spécification |
|---|---|
| Pastilles | Diamètre 14, écart `space.4`, vides = `neutral.500`, pleines = `accent.500` |
| Animations | Voir §6.3 de `03-motion-and-feel.md` — remplissage, pouls, secousse |
| Grille | Identique à `AmountKeypad`, avec `⌫` et éventuellement l'icône biométrique |
| Sécurité | Bloque la capture d'écran, valeur en `useRef`, jamais dans un state persisté |
| Après erreur | Secousse, réinitialisation en cascade, focus conservé |
| Chargement | Les pastilles pulsent doucement en boucle, le pavé se désactive |

### `OtpInput`

Six cellules pour le code reçu par e-mail.

| Élément | Spécification |
|---|---|
| Cellules | 6, largeur 48, hauteur 56, `radius.md`, fond `bg.surface2` |
| Focus | Bordure `accent.500` à `stroke.thick`, échelle `1,04` en `snappy` |
| Avance | Automatique à la saisie, retour arrière automatique à l'effacement |
| Collage | Un collage de 6 chiffres remplit toutes les cellules en cascade de 40 ms |
| Complet | `haptic.press` + soumission automatique, sans bouton |
| Erreur | Secousse, bordures en `danger.500`, cellules vidées |
| Compte à rebours | « Renvoyer dans 0:28 » sous les cellules, actif à 0 |

**Point important** : le code arrivant par e-mail, `autoComplete="sms-otp"` ne sert à rien. L'écran doit à la place proposer un bouton « Ouvrir ma boîte mail » (`Linking.openURL('message://')` sur iOS, intention `ACTION_MAIN` catégorie `APP_EMAIL` sur Android).

### `PhoneField`

| Élément | Spécification |
|---|---|
| Indicatif | Sélecteur avec drapeau, ouvre une `Sheet` de recherche. Défaut `+225` |
| Numéro | Formaté en direct par blocs de 2, `keyboardType="phone-pad"` |
| Validation | `^\d{8,15}$` conformément au contrat |
| Contacts | Bouton d'import depuis le carnet d'adresses *(P1)* |

---

## 5. Affichage

### `TransactionRow`

Ligne d'historique. Hauteur fixe de 68 dp, virtualisée.

```
┌──────────────────────────────────────────────────────┐
│  ╭──╮   Orange Money                       + 50 000 F│
│  │ ↓│   Dépôt · 11:42                      Terminé   │
│  ╰──╯                                                │
└──────────────────────────────────────────────────────┘
   44dp    ← flexible →                    ← aligné à droite
```

| Zone | Contenu |
|---|---|
| Pastille | 44 dp, icône selon `type`, fond selon le lavis de la famille d'état |
| Titre | `counterpart` masqué si P2P, sinon libellé de l'opérateur — `bodyMd`, `text.primary` |
| Sous-titre | Libellé du type · heure locale — `bodySm`, `text.tertiary` |
| Montant | `Amount` en `bodyLg`, coloré selon `direction` |
| État | Uniquement si l'état n'est **pas** `success` — `StatusChip` compact |

Un état de succès n'affiche **aucune** puce. Afficher « Terminé » sur 95 % des lignes revient à n'afficher aucune information. Seules les anomalies méritent un marqueur — c'est le principe qui rend un historique lisible en un coup d'œil.

Appui → transition partagée vers le détail (§5.3 de `03-motion-and-feel.md`).

### `StatusChip`

```ts
type StatusChipProps = {
  state: TxState;                  // l'un des 11 états du contrat
  size?: 'sm' | 'md';
  showIcon?: boolean;
};
```

Résout couleur et icône via l'unique table du §2.4 du design system. Un état `pending` ajoute un point pulsant à gauche du libellé.

### `StateTimeline`

**Le composant signature.** Rend `stateHistory` de `GET /payments/history?detail=true`.

```
   ●  Initiée                                     11:42:13
   │
   ●  Autorisée                                   11:42:13
   │  Fonds réservés chez Orange Money
   ●  Capturée                                    11:42:13
   │
   ◉  Terminée                                    11:42:13
```

| Élément | Spécification |
|---|---|
| Nœud | 12 dp, plein en `accent.500` si franchi, contour `neutral.400` si à venir |
| Nœud courant | 16 dp, halo `accent.wash`, pouls continu si l'état n'est pas terminal |
| Nœud d'échec | `danger.500`, icône croix incrustée |
| Segment | 2 dp de large, `accent.500` si franchi, pointillé animé si en attente |
| Libellé | Traduction lisible de l'état, `bodyMd` — **jamais** l'énumération brute |
| Description | Une ligne d'explication en `bodySm`, `text.tertiary` |
| Horodatage | Heure locale à la seconde, `bodySm`, `text.tertiary`, aligné à droite |
| Animation | Voir §6.7 de `03-motion-and-feel.md` |

Table de traduction imposée — l'utilisateur ne voit jamais `AUTHORIZATION_FAILED` :

| État | Libellé | Description |
|---|---|---|
| `INITIALIZED` | Initiée | Opération enregistrée |
| `AUTHORIZED` | Autorisée | Fonds réservés chez l'opérateur |
| `CAPTURED` | Capturée | Fonds prélevés |
| `COMPLETED` | Terminée | Opération finalisée |
| `SETTLEMENT_PENDING` | Règlement en cours | En attente de l'opérateur |
| `SETTLED` | Réglée | Règlement confirmé |
| `AUTHORIZATION_FAILED` | Autorisation refusée | L'opérateur a refusé l'opération |
| `CAPTURE_FAILED` | Prélèvement échoué | Le prélèvement n'a pas abouti |
| `SETTLEMENT_FAILED` | Règlement échoué | Le règlement n'a pas abouti |
| `FAILED` | Échouée | L'opération n'a pas pu être exécutée |
| `REVERSED` | Annulée | Opération contrepassée |

### `MethodPicker`

Sélection de l'opérateur Mobile Money parmi la liste figée du §4 du contrat.

| Élément | Spécification |
|---|---|
| Disposition | Liste verticale de `ListRow`, une par opérateur |
| Pastille | 40 dp, remplie de la couleur de marque de l'opérateur |
| Sélection | Coche `accent.500` à droite, fond `accent.wash`, `haptic.select` |
| Animation | La coche entre en échelle `0 → 1` en `bouncy` |

---

## 6. Action

### `Button`

```ts
type ButtonProps = {
  variant: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'lg' | 'md' | 'sm';        // 56 / 48 / 40 dp
  loading?: boolean;
  disabled?: boolean;
  icon?: IconName;
  fullWidth?: boolean;
};
```

| Variante | Fond | Texte | Bordure |
|---|---|---|---|
| `primary` | `accent.500` | `text.onAccent` | aucune |
| `secondary` | `bg.surface2` | `text.primary` | aucune |
| `ghost` | transparent | `accent.500` | aucune |
| `danger` | `status.failed.bg` | `status.failed.fg` | aucune |

État de chargement : le libellé sort en fondu, trois points pulsent en cascade de 150 ms, la largeur du bouton **ne change pas**. Un bouton qui rétrécit pendant le chargement fait sauter la mise en page.

État désactivé : opacité `0,4`, aucune haptique, aucune compression.

### `ActionTile`

Les trois actions monétaires de l'accueil.

| Élément | Spécification |
|---|---|
| Disposition | Trois tuiles égales, écart `space.3` |
| Contenu | Icône 24 dp dans une pastille de 44 dp, libellé `labelMd` dessous |
| Fond | `bg.surface1`, `radius.lg`, padding `space.4` |
| Appui | Échelle `0,96`, `haptic.press` |
| Entrée | Cascade de 40 ms au montage de l'écran |

### `ActionBar`

Barre ancrée en bas d'écran, portant l'action principale.

| Élément | Spécification |
|---|---|
| Position | Absolue en bas, respecte l'inset inférieur |
| Fond | `bg.app` + flou d'arrière-plan 20 + filet supérieur `overlay.hairline` |
| Élévation | `elevation.4` |
| Espace réservé | La vue défilante réserve `layout.anchoredBarClearance` en padding bas |

---

## 7. Retour d'état

### `Skeleton`

Voir §6.5 de `03-motion-and-feel.md`. Décliné en variantes prêtes : `SkeletonBalance`, `SkeletonTransactionList`, `SkeletonTimeline`.

### `EmptyState`

| Élément | Spécification |
|---|---|
| Illustration | SVG monochrome en `neutral.400`, 120 dp |
| Titre | `titleSm`, `text.primary` |
| Description | `bodyMd`, `text.secondary`, centré, largeur max 280 dp |
| Action | `Button` `secondary` — **obligatoire.** Un état vide sans issue est un cul-de-sac |

### `ErrorState`

| Élément | Spécification |
|---|---|
| Icône | `alert-triangle` 48 dp en `danger.500` |
| Titre | Message métier lisible, jamais un code d'erreur |
| Description | Ce que l'utilisateur peut faire |
| Actions | `Réessayer` en `primary`, action secondaire contextuelle |
| Détail technique | Repliable, contient le `detail` du `ProblemDetail`. Utile au support, invisible par défaut |

### `OfflineBanner`

| Élément | Spécification |
|---|---|
| Position | Sous la barre de navigation, pousse le contenu vers le bas |
| Fond | `status.reversed.bg`, texte `status.reversed.fg` |
| Entrée | Translation Y `−40 → 0` en `standard` |
| Contenu | « Pas de connexion » + `wifi-off`, avec un indicateur de reconnexion |
| Comportement | **Jamais modal.** L'utilisateur peut continuer à consulter ses données en cache |

### `Toast`

| Élément | Spécification |
|---|---|
| Position | En haut, sous l'inset supérieur |
| Fond | `bg.surface3` + `overlay.border`, `radius.md`, `elevation.4` |
| Entrée | Translation Y `−80 → 0` en `bouncy` |
| Sortie | Automatique après 3 s, ou par balayage vers le haut |
| Emploi | Confirmations non bloquantes uniquement : copie effectuée, réglage enregistré |

Un `Toast` ne sert **jamais** à annoncer le résultat d'une opération monétaire. Ce résultat mérite toujours un écran plein.