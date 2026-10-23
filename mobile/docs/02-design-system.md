# Kora — Design System

Document autonome. Il définit l'intégralité du langage visuel de l'application mobile Kora : couleur, typographie, espacement, forme, élévation, iconographie, et les règles de composition qui les lient.

Il ne décrit ni les écrans (`05-screens.md`), ni le mouvement (`03-motion-and-feel.md`), ni les composants (`04-components.md`). Il fournit la matière première dont tout le reste est fait.

**Règle absolue : aucune valeur définie ici ne doit jamais être écrite en dur dans un composant.** Tout passe par le fichier de tokens.

---

## 1. Principes

Sept principes. Ils tranchent tout arbitrage visuel non couvert explicitement par ce document.

### 1.1 Le sombre est la norme, le clair est l'alternative

Kora se conçoit en sombre d'abord. Le thème clair est dérivé, jamais l'inverse. Un wallet se consulte le soir, dans un transport, dans une cour. Le sombre réduit la fatigue, économise la batterie sur OLED, et donne au montant sa gravité.

Le fond n'est **jamais du noir pur**. `#000000` écrase les ombres, provoque du smearing sur OLED, et rend l'interface plate. Le fond de Kora est un anthracite très sombre légèrement bleuté.

### 1.2 L'élévation se lit en luminosité, pas en ombre

En thème sombre, une ombre portée est invisible. La hiérarchie de profondeur se construit par **paliers de luminosité de surface** : plus une surface est proche de l'utilisateur, plus elle est claire. Cinq paliers, pas davantage.

Les ombres portées ne servent qu'à un seul cas : détacher un élément flottant du contenu qui défile dessous (barre d'onglets, barre d'action ancrée). Et encore, elles y sont doublées d'un flou d'arrière-plan.

### 1.3 Un seul accent, employé avec parcimonie

Une seule couleur d'accent dans toute l'application. Elle signale **l'action principale et rien d'autre**. Dès qu'un accent apparaît deux fois sur un écran, il cesse de guider.

Corollaire : les états sémantiques (succès, erreur, avertissement) ne sont pas des accents. Ils ne peuvent jamais être empruntés pour de la décoration.

### 1.4 Le montant est le héros

Sur tout écran, le montant est l'élément typographique dominant. Il est plus grand, plus dense, mieux espacé que tout le reste. Tout ce qui l'entoure lui cède la place.

Les montants se composent **exclusivement en chiffres à chasse fixe**. Un chiffre qui change ne doit jamais provoquer de reflow horizontal.

### 1.5 Le contour est un dernier recours

Une bordure signale une frontière que la couleur de surface ne parvient pas à établir. Une carte sur un fond correctement contrasté n'a pas besoin de bordure. Si une bordure semble nécessaire, le contraste de surface est probablement insuffisant : c'est lui qu'il faut corriger.

Quand une bordure est inévitable, elle fait `1 pixel physique` — pas 1 dp — et sa couleur est une superposition blanche à très faible opacité, jamais une couleur opaque.

### 1.6 Le rayon suit la taille

Un petit élément prend un petit rayon, un grand élément un grand rayon. Un rayon uniforme sur toute l'interface produit des petits éléments mous et des grandes surfaces raides. L'échelle du §5 encode cette relation.

### 1.7 Le vide est une décision

L'espace négatif se choisit, il ne subsiste pas. L'échelle d'espacement est à base 4 et n'admet aucune valeur intermédiaire. `13` n'existe pas. Si une valeur de l'échelle ne convient pas, c'est la composition qui est à revoir.

---

## 2. Couleur

### 2.1 Fondation neutre — thème sombre

Neuf paliers, du fond le plus profond au texte le plus clair. La teinte est constante (`222°`), la saturation décroît avec la clarté : c'est ce qui donne la neutralité froide sans virer au gris mort.

| Token | Hex | Rôle |
|---|---|---|
| `neutral.0` | `#08090C` | Fond d'écran absolu. Feuilles modales plein écran |
| `neutral.50` | `#0C0E12` | **Fond d'application.** Le socle par défaut |
| `neutral.100` | `#13161C` | Surface niveau 1 — cartes, listes |
| `neutral.200` | `#1A1E26` | Surface niveau 2 — carte sur carte, champs de saisie |
| `neutral.300` | `#242932` | Surface niveau 3 — état pressé, sélection |
| `neutral.400` | `#333945` | Séparateurs marqués, contours de composants |
| `neutral.500` | `#4A5261` | Icônes désactivées, texte fantôme |
| `neutral.600` | `#6B7484` | Texte tertiaire, horodatages |
| `neutral.700` | `#9AA3B2` | **Texte secondaire.** Libellés, sous-titres |
| `neutral.800` | `#C9CFD9` | Texte de corps de haute lisibilité |
| `neutral.900` | `#F4F6FA` | **Texte primaire.** Titres, montants |

Superpositions — pour les bordures, séparateurs et voiles, toujours préférées à une couleur opaque :

| Token | Valeur |
|---|---|
| `overlay.hairline` | `rgba(255,255,255,0.06)` |
| `overlay.border` | `rgba(255,255,255,0.10)` |
| `overlay.borderStrong` | `rgba(255,255,255,0.16)` |
| `overlay.press` | `rgba(255,255,255,0.08)` |
| `overlay.scrim` | `rgba(8,9,12,0.72)` |

### 2.2 Accent — Kora Green

L'accent de Kora est un vert émeraude électrique. Choix motivé : le vert porte l'argent et la validation dans presque toutes les cultures ; il se distingue nettement du bleu bancaire générique et du violet fintech désormais banal ; il tient le contraste sur fond sombre là où le bleu s'écrase.

| Token | Hex | Usage |
|---|---|---|
| `accent.100` | `#D3FBE4` | Texte sur fond accent plein |
| `accent.300` | `#6EE7A8` | Icônes accent sur fond sombre |
| `accent.400` | `#34D67F` | Accent clair — survol, mise en évidence |
| `accent.500` | `#00C46A` | **Accent primaire** (thème sombre). Boutons principaux, éléments actifs |
| `accent.600` | `#00A557` | État pressé du bouton primaire, thème sombre |
| `accent.700` | `#007F43` | **Accent primaire en thème clair** |
| `accent.800` | `#00632F` | État pressé du bouton primaire, thème clair |
| `accent.wash` | `rgba(0,196,106,0.12)` | Fond de puce, halo d'icône |
| `accent.glow` | `rgba(0,196,106,0.28)` | Halo de lueur — usage exceptionnel |

Le texte posé sur `accent.500` est `#04140B`, un vert-noir profond. **Jamais du blanc pur** : le contraste blanc sur `#00C46A` est de 2,1:1, sous le seuil ; le vert-noir atteint 9,8:1.

> **Le même piège se referme sur le thème clair, un cran plus bas.** Blanc sur `accent.600` `#00A557` ne donne que **3,22:1** — toujours sous le seuil de 4,5:1. C'est pourquoi l'accent primaire du thème clair est `accent.700` (5,10:1 avec du blanc) et non `accent.600`, et pourquoi un palier `accent.800` existe pour l'état pressé.
>
> Ce genre d'erreur ne se voit pas à l'œil : elle se mesure. `src/theme/__tests__/contrast.test.ts` vérifie chaque paire des deux thèmes, lavis translucides composés sur leur fond réel. Toute nouvelle couleur doit y passer.

### 2.3 Sémantique

| Rôle | Token | Hex | Fond associé |
|---|---|---|---|
| Succès | `success.500` | `#00C46A` | `rgba(0,196,106,0.12)` |
| Succès (texte) | `success.300` | `#6EE7A8` | — |
| Erreur | `danger.500` | `#FF4D5E` | `rgba(255,77,94,0.12)` |
| Erreur (texte) | `danger.300` | `#FF8E99` | — |
| Avertissement | `warning.500` | `#FFB020` | `rgba(255,176,32,0.12)` |
| Avertissement (texte) | `warning.300` | `#FFCE70` | — |
| Information | `info.500` | `#3D8BFF` | `rgba(61,139,255,0.12)` |
| En cours | `pending.500` | `#8B93A5` | `rgba(139,147,165,0.12)` |

Le succès partage volontairement sa valeur avec l'accent : dans un wallet, l'action principale *est* la réussite financière. Aucune ambiguïté ne naît de cette coïncidence car les contextes ne se recouvrent pas.

`pending` est délibérément neutre et désaturé. Un état en cours n'est ni bon ni mauvais — le teinter en jaune induirait une inquiétude injustifiée.

### 2.4 Correspondance état de transaction → couleur

Unique table faisant autorité. Aucun écran ne définit sa propre correspondance.

Les valeurs sont consommées **exclusivement** via `theme.status.<famille>.{fg,bg}` — jamais par un chemin de palette direct. C'est ce qui permet au thème clair de les redéfinir sans toucher un composant.

| Famille | `theme.status.*` | Texte (`fg`) | Fond de puce (`bg`) | Icône |
|---|---|---|---|---|
| `pending` | `status.pending` | `pending.300` | `rgba(139,147,165,0.12)` | horloge |
| `success` | `status.success` | `accent.300` | `rgba(0,196,106,0.12)` | coche |
| `failed` | `status.failed` | `danger.300` | `rgba(255,77,94,0.12)` | croix |
| `reversed` | `status.reversed` | `warning.300` | `rgba(255,176,32,0.12)` | flèche-retour |

Le lavis d'accent générique — pastilles neutres, halos d'icône hors contexte d'état — est `theme.accent.wash`. C'est le **seul** token `wash` qui existe en dehors de `status.*`.

### 2.5 Sens de flux monétaire

| Sens | Couleur du montant | Préfixe |
|---|---|---|
| `INBOUND` | `success.300` | `+` |
| `OUTBOUND` | `neutral.900` | `−` (U+2212, pas un trait d'union) |

> Décision délibérée : **une sortie d'argent ne se colore pas en rouge.** Dépenser est normal ; le rouge est réservé à l'échec. Colorer chaque dépense en rouge produit une interface anxiogène et sature le signal d'erreur. C'est exactement le choix fait par Revolut, Monzo et N26.

### 2.6 Marques des opérateurs

Les couleurs de marque du §4 de `01-api-contract.md` sont **uniquement** admises comme pastille d'identification circulaire de 40 dp. Elles ne teintent jamais un fond, un bouton ou un texte.

### 2.7 Thème clair

Dérivation systématique. La rampe neutre s'inverse, l'accent se fonce d'un cran pour tenir le contraste sur blanc.

| Token | Sombre | Clair |
|---|---|---|
| `bg.app` | `neutral.50` `#0C0E12` | `#F7F8FA` |
| `bg.surface1` | `neutral.100` `#13161C` | `#FFFFFF` |
| `bg.surface2` | `neutral.200` `#1A1E26` | `#F0F2F5` |
| `bg.surface3` | `neutral.300` `#242932` | `#E4E7EC` |
| `text.primary` | `neutral.900` `#F4F6FA` | `#0C0E12` |
| `text.secondary` | `neutral.700` `#9AA3B2` | `#5A6274` |
| `text.tertiary` | `neutral.600` `#6B7484` | `#858D9C` |
| `border` | `overlay.border` | `rgba(12,14,18,0.10)` |
| `accent.primary` | `accent.500` `#00C46A` | `accent.700` `#007F43` |
| `accent.pressed` | `accent.600` `#00A557` | `accent.800` `#00632F` |
| `onAccent` | `#04140B` | `#FFFFFF` |

En thème clair, l'élévation change de mécanisme : elle redevient une ombre portée, très douce et très diffuse (`0 1px 2px rgba(12,14,18,0.04)`, `0 8px 24px rgba(12,14,18,0.06)`). C'est le seul point où les deux thèmes divergent structurellement.

---

## 3. Typographie

### 3.1 Familles

| Rôle | Police | Justification |
|---|---|---|
| Interface | **Inter** — Regular 400, Medium 500, SemiBold 600, Bold 700 | Grande hauteur d'x, excellente lisibilité aux petites tailles, chiffres tabulaires disponibles |
| Montants | **Inter** avec `fontVariantNumeric: 'tabular-nums'` | Largeur de chiffre constante, indispensable à l'animation d'un solde |

Une seule famille. Pas de police d'affichage secondaire : la distinction se fait par la graisse et l'échelle, jamais par un changement de police. C'est ce qui produit la cohérence perçue des applications les plus abouties.

Inter doit être **empaquetée localement**, jamais chargée depuis un CDN.

### 3.2 Échelle

Échelle modulaire de rapport 1,25, arrondie au demi-point, plafonnée à onze crans.

| Token | Taille | Interligne | Graisse | Interlettrage | Usage |
|---|---|---|---|---|---|
| `display.xl` | 56 | 60 | 700 | −2,0 | Solde principal de l'accueil |
| `display.lg` | 44 | 48 | 700 | −1,5 | Montant saisi au pavé numérique |
| `display.md` | 34 | 40 | 700 | −1,0 | Montant du récapitulatif, écran de résultat |
| `title.lg` | 28 | 34 | 700 | −0,6 | Titre d'écran |
| `title.md` | 22 | 28 | 600 | −0,4 | Titre de section, en-tête de feuille |
| `title.sm` | 18 | 24 | 600 | −0,2 | Titre de carte |
| `body.lg` | 16 | 24 | 400 | 0 | Corps de texte principal |
| `body.md` | 15 | 22 | 400 | 0 | Corps par défaut, libellé de liste |
| `body.sm` | 13 | 18 | 400 | 0 | Texte secondaire, aide contextuelle |
| `label.md` | 13 | 16 | 600 | +0,2 | Libellé de bouton, en-tête de champ |
| `label.sm` | 11 | 14 | 600 | +0,4 | Puce d'état, badge |
| `mono.md` | 15 | 22 | 500 | +0,4 | Numéro d'opération, numéro de compte |

L'interlettrage négatif sur les grandes tailles est essentiel : sans lui, un montant de 56 pt paraît distendu. C'est un détail systématiquement omis, et systématiquement présent dans les interfaces de premier plan.

### 3.3 Rôles de couleur du texte

| Rôle | Token | Emploi |
|---|---|---|
| `text.primary` | `neutral.900` | Montants, titres, valeurs |
| `text.secondary` | `neutral.700` | Libellés, descriptions |
| `text.tertiary` | `neutral.600` | Horodatages, mentions légales |
| `text.disabled` | `neutral.500` | Contenu inactif |
| `text.onAccent` | `#04140B` | Texte sur surface d'accent |
| `text.danger` | `danger.300` | Messages d'erreur |
| `text.success` | `success.300` | Montants entrants, confirmations |

### 3.4 Règles de composition des montants

Un montant n'est **jamais** une simple chaîne de caractères. Il se compose en trois parties typographiques distinctes :

```
┌─────────────────────────────────────┐
│  −  125 000  F                      │
│  ▲     ▲     ▲                      │
│  │     │     └─ devise : 0,45× la taille, poids 600,
│  │     │        text.secondary, aligné sur la ligne de base
│  │     └─────── entier : taille pleine, poids 700, tabular-nums
│  └───────────── signe : taille pleine, poids 700,
│                 couleur héritée du sens de flux
└─────────────────────────────────────┘
```

Règles impératives :

1. Séparateur de milliers : **espace fine insécable** `U+202F`. Ni virgule, ni point, ni espace normale.
2. XAF et XAF s'affichent **sans décimale**. Jamais `125 000,00 F`.
3. Le symbole suit le montant en franc CFA, séparé par une espace fine insécable.
4. Le signe négatif est `U+2212` (moins mathématique), jamais `-` (trait d'union).
5. Le signe `+` n'apparaît que sur les flux entrants, jamais sur un solde.
6. Un solde masqué affiche `•••• F` — quatre pastilles, la devise reste visible.

### 3.5 Mise à l'échelle des polices système

L'application respecte le réglage d'accessibilité de l'appareil, avec un plafonnement différencié :

| Catégorie | Plafond |
|---|---|
| `display.*` | 1,3× — au-delà, un montant déborde |
| `title.*` | 1,6× |
| `body.*`, `label.*` | 2,0× — aucun plafond fonctionnel |

Tout conteneur de texte doit rester tolérant à une hauteur variable. **Aucune hauteur fixe sur un élément contenant du texte.**

---

## 4. Espacement

Échelle à base 4. Onze crans. Aucune valeur hors échelle n'est admise.

| Token | dp | Usage type |
|---|---|---|
| `space.0` | 0 | — |
| `space.1` | 4 | Écart icône ↔ texte dans une puce |
| `space.2` | 8 | Écart interne serré |
| `space.3` | 12 | Espacement de liste dense |
| `space.4` | 16 | **Padding par défaut.** Marge latérale d'écran |
| `space.5` | 20 | Padding de carte |
| `space.6` | 24 | Écart entre sections |
| `space.8` | 32 | Séparation de blocs majeurs |
| `space.10` | 40 | Respiration en tête d'écran |
| `space.12` | 48 | Marge autour d'un élément héros |
| `space.16` | 64 | Espacement d'écran de résultat |

### Constantes de mise en page

| Constante | dp |
|---|---|
| Marge latérale d'écran | 20 |
| Padding interne de carte | 16 |
| Écart entre lignes de liste | 0 — séparateur assuré par un filet, pas par une marge |
| Hauteur de ligne d'historique | 68 |
| Hauteur de bouton principal | 56 |
| Hauteur de barre de navigation | 56 + inset supérieur |
| Hauteur de barre d'onglets | 56 + inset inférieur |
| Cible tactile minimale | 44 × 44 |
| Espace réservé sous une barre d'action ancrée | 88 |

---

## 5. Forme

### 5.1 Rayons

| Token | dp | Applicable à |
|---|---|---|
| `radius.xs` | 6 | Puces, badges |
| `radius.sm` | 10 | Petits boutons, champs |
| `radius.md` | 14 | Boutons standard, éléments de liste |
| `radius.lg` | 20 | Cartes |
| `radius.xl` | 28 | Cartes héros, feuilles modales |
| `radius.2xl` | 36 | Conteneurs plein écran |
| `radius.full` | 999 | Pastilles, avatars, boutons flottants |

Règle de composition : le rayon d'un élément imbriqué vaut `rayon du parent − padding`. Une carte à `radius.lg` (20) avec `space.4` (16) de padding contient des éléments à `radius.xs` (≈ 4, arrondi à 6). Ignorer cette règle produit des coins concentriques désalignés — un défaut discret mais immédiatement perceptible.

### 5.2 Épaisseurs de trait

| Token | Valeur |
|---|---|
| `stroke.hairline` | `1 / PixelRatio.get()` — filets et séparateurs |
| `stroke.thin` | 1 dp — bordures de composants |
| `stroke.regular` | 1,5 dp — trait d'icône |
| `stroke.thick` | 2 dp — anneau de focus, indicateur actif |

### 5.3 Élévation

En thème sombre, un niveau d'élévation est **une couleur de surface**, pas une ombre.

| Niveau | Surface | Emploi |
|---|---|---|
| `elevation.0` | `bg.app` | Fond d'écran |
| `elevation.1` | `bg.surface1` | Cartes, éléments de liste |
| `elevation.2` | `bg.surface2` | Carte imbriquée, champ de saisie |
| `elevation.3` | `bg.surface3` | Menus, infobulles |
| `elevation.4` | `bg.surface3` + `overlay.border` + flou d'arrière-plan 20 | Éléments flottants ancrés |

Le niveau 4 est le seul à porter une ombre en thème sombre :
`shadowColor: '#000', shadowOpacity: 0.4, shadowRadius: 24, shadowOffset: { height: 8 }, elevation: 12`.

---

## 6. Iconographie

### 6.1 Spécification

| Attribut | Valeur |
|---|---|
| Format | SVG vectoriel via `react-native-svg` — **jamais** de police d'icônes, jamais de PNG |
| Grille | 24 × 24, tracé sur 20 × 20 avec 2 dp de marge optique |
| Épaisseur | `stroke.regular` (1,5), constante, **jamais mise à l'échelle** |
| Extrémités | arrondies, jointures arrondies |
| Remplissage | aucun par défaut ; le plein est réservé à l'état actif de la barre d'onglets |
| Couleur | héritée du contexte via la propriété `color` |

Tailles autorisées : `16`, `20`, `24`, `28`, `32`. Aucune autre.

### 6.2 Jeu minimal

Une icône par concept, jamais deux.

```
Navigation     home · activity · card · settings · chevron-{left,right,up,down} · close · back
Monétaire      arrow-down-circle (dépôt) · arrow-up-circle (retrait) · send (transfert)
États          check-circle · x-circle · clock · rotate-ccw · alert-triangle · info
Actions        eye · eye-off · copy · share · filter · search · plus · more-horizontal
Sécurité       lock · shield · fingerprint · face-id
Système        wifi-off · refresh-cw · calendar
```

### 6.3 Pastilles d'icône

Une icône monétaire dans une liste est toujours enveloppée dans une pastille circulaire :

| Attribut | Valeur |
|---|---|
| Diamètre | 44 |
| Rayon | `radius.full` |
| Fond | `bg.surface2` en neutre, ou lavis sémantique selon le contexte |
| Icône | 20 dp, centrée |

---

## 7. Fichier de tokens

Implémentation de référence. Toute valeur ci-dessus y est exposée, et rien n'est consommé autrement.

```ts
// src/theme/tokens.ts

export const palette = {
  neutral: {
    0: '#08090C',  50: '#0C0E12', 100: '#13161C', 200: '#1A1E26',
    300: '#242932', 400: '#333945', 500: '#4A5261', 600: '#6B7484',
    700: '#9AA3B2', 800: '#C9CFD9', 900: '#F4F6FA',
  },
  accent: {
    100: '#D3FBE4', 300: '#6EE7A8', 400: '#34D67F',
    500: '#00C46A', 600: '#00A557', 700: '#007F43', 800: '#00632F',
  },
  danger:  { 300: '#FF8E99', 500: '#FF4D5E' },
  warning: { 300: '#FFCE70', 500: '#FFB020' },
  info:    { 300: '#8FBBFF', 500: '#3D8BFF' },
  pending: { 300: '#B4BAC7', 500: '#8B93A5' },
} as const;

export const space = {
  0: 0, 1: 4, 2: 8, 3: 12, 4: 16, 5: 20,
  6: 24, 8: 32, 10: 40, 12: 48, 16: 64,
} as const;

export const radius = {
  xs: 6, sm: 10, md: 14, lg: 20, xl: 28, '2xl': 36, full: 999,
} as const;

export const type = {
  displayXl: { fontSize: 56, lineHeight: 60, fontWeight: '700', letterSpacing: -2.0 },
  displayLg: { fontSize: 44, lineHeight: 48, fontWeight: '700', letterSpacing: -1.5 },
  displayMd: { fontSize: 34, lineHeight: 40, fontWeight: '700', letterSpacing: -1.0 },
  titleLg:   { fontSize: 28, lineHeight: 34, fontWeight: '700', letterSpacing: -0.6 },
  titleMd:   { fontSize: 22, lineHeight: 28, fontWeight: '600', letterSpacing: -0.4 },
  titleSm:   { fontSize: 18, lineHeight: 24, fontWeight: '600', letterSpacing: -0.2 },
  bodyLg:    { fontSize: 16, lineHeight: 24, fontWeight: '400', letterSpacing: 0 },
  bodyMd:    { fontSize: 15, lineHeight: 22, fontWeight: '400', letterSpacing: 0 },
  bodySm:    { fontSize: 13, lineHeight: 18, fontWeight: '400', letterSpacing: 0 },
  labelMd:   { fontSize: 13, lineHeight: 16, fontWeight: '600', letterSpacing: 0.2 },
  labelSm:   { fontSize: 11, lineHeight: 14, fontWeight: '600', letterSpacing: 0.4 },
  monoMd:    { fontSize: 15, lineHeight: 22, fontWeight: '500', letterSpacing: 0.4,
               fontVariant: ['tabular-nums'] as const },
} as const;

/** Appliqué à tout composant Amount, en plus de sa variante typographique. */
export const tabularNums = { fontVariant: ['tabular-nums'] as const };

export const layout = {
  screenPadding: 20,
  cardPadding: 16,
  rowHeight: 68,
  buttonHeight: 56,
  minTouchTarget: 44,
  anchoredBarClearance: 88,
} as const;

export const darkTheme = {
  bg:     { app: palette.neutral[50],  surface1: palette.neutral[100],
            surface2: palette.neutral[200], surface3: palette.neutral[300] },
  text:   { primary: palette.neutral[900], secondary: palette.neutral[700],
            tertiary: palette.neutral[600], disabled: palette.neutral[500],
            onAccent: '#04140B' },
  accent: { primary: palette.accent[500], pressed: palette.accent[600],
            soft: palette.accent[300],   wash: 'rgba(0,196,106,0.12)' },
  status: {
    success: { fg: palette.accent[300],  bg: 'rgba(0,196,106,0.12)' },
    failed:  { fg: palette.danger[300],  bg: 'rgba(255,77,94,0.12)' },
    pending: { fg: palette.pending[300], bg: 'rgba(139,147,165,0.12)' },
    reversed:{ fg: palette.warning[300], bg: 'rgba(255,176,32,0.12)' },
  },
  overlay: {
    hairline: 'rgba(255,255,255,0.06)',
    border:   'rgba(255,255,255,0.10)',
    press:    'rgba(255,255,255,0.08)',
    scrim:    'rgba(8,9,12,0.72)',
  },
} as const;

export type Theme = typeof darkTheme;
```

Le thème clair expose **exactement les mêmes clés**. Aucun composant ne teste jamais le thème actif : il consomme des rôles sémantiques, qui se résolvent seuls.

---

## 8. Ce qui est interdit

Cette section est aussi contraignante que les précédentes.

| Interdit | Raison |
|---|---|
| `#000000` en fond | Écrase l'élévation, smearing OLED |
| Blanc pur `#FFFFFF` en texte sur fond sombre | Éblouissant ; `neutral.900` suffit |
| Un dégradé, sauf halo d'accent explicitement spécifié | Marqueur immédiat d'interface amateur |
| Une ombre portée en thème sombre, hors `elevation.4` | Invisible, coûteuse à rendre |
| Une seconde couleur d'accent | Détruit la hiérarchie de l'action |
| Un montant sans chiffres tabulaires | Provoque du reflow horizontal |
| Une valeur d'espacement hors échelle | Rompt le rythme vertical |
| Une bordure opaque | Utiliser une superposition blanche |
| Une police d'icônes ou une icône en PNG | Flou en haute densité, non teintable |
| Un rouge sur un montant sortant | Sature le signal d'erreur |
| Une hauteur fixe sur un conteneur de texte | Casse la mise à l'échelle d'accessibilité |
| Un émoji en guise d'icône | Rendu incohérent entre plateformes |
| Une bibliothèque de composants tierce | Voir `README.md` §règle 2 |