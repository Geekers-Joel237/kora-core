# Kora — Mouvement et ressenti

C'est ici que se joue l'écart entre une application correcte et une application de niveau Revolut. La palette et la typographie s'imitent en une journée. Le ressenti, non.

Prérequis : `react-native-reanimated` v3, `react-native-gesture-handler` v2, `expo-haptics`.

---

## 1. Les quatre lois

### Loi 1 — Le mouvement se fait par ressort

Une durée décrit un mouvement qui ignore son passé. Un ressort décrit un mouvement qui possède une **vélocité**, et qui peut donc être interrompu, redirigé, repris en cours de route sans discontinuité.

`withTiming` n'est autorisé que pour trois choses : l'opacité, la couleur, et une barre de progression déterministe. **Tout déplacement, toute mise à l'échelle, toute rotation passe par `withSpring`.**

### Loi 2 — Le mouvement naît de l'endroit où on l'a touché

Un élément ne surgit pas du néant. Il grandit depuis le point d'appui, il s'ouvre depuis la ligne de liste sur laquelle on a tapé, il retourne d'où il vient. Cette continuité spatiale est ce qui rend une navigation compréhensible sans y penser.

Corollaire opérationnel : les transitions partagées ne sont pas un ornement, elles sont le mécanisme de navigation par défaut entre une liste et son détail.

### Loi 3 — Le retour est immédiat, l'achèvement peut attendre

La réaction visuelle à un appui survient en **moins de 100 ms, sans exception**, y compris pendant une requête réseau. La compression sous le doigt et l'impulsion haptique sont locales, synchrones, et ne dépendent d'aucun état distant.

### Loi 4 — Rien n'apparaît instantanément, rien ne disparaît instantanément

Un changement d'opacité brutal se lit comme un scintillement. Le seuil de perception se situe autour de 80 ms : en dessous, on perçoit un défaut ; au-dessus, on perçoit une intention. Tout changement de visibilité dure au moins 120 ms.

---

## 2. Jetons de mouvement

```ts
// src/theme/motion.ts
import { Easing } from 'react-native-reanimated';

export const spring = {
  /** Défaut. Élastique juste ce qu'il faut, sans oscillation visible. */
  standard: { damping: 20, stiffness: 200, mass: 1 },

  /** Réactif et sec. Appuis de bouton, pastilles de PIN, puces. */
  snappy:   { damping: 26, stiffness: 340, mass: 0.8 },

  /** Expressif. Entrées d'écran de succès, apparitions d'éléments héros. */
  bouncy:   { damping: 12, stiffness: 180, mass: 1 },

  /** Ample et posé. Feuilles modales, grandes surfaces. */
  gentle:   { damping: 24, stiffness: 120, mass: 1.1 },

  /** Suit le doigt. Aucun rebond toléré sur un geste. */
  gesture:  { damping: 30, stiffness: 400, mass: 0.6, overshootClamping: true },
};

export const timing = {
  instant: 120,   // apparition d'une superposition
  fast:    200,   // fondu, changement de couleur
  normal:  280,   // transition standard
  slow:    400,   // révélation d'un élément héros
  celebr:  650,   // séquence de succès
};

/** Courbe d'accélération unique. Emphatique et décélérée — la même que Material 3
 *  et que les transitions système d'iOS. Toute autre courbe est un écart à justifier. */
export const ease = Easing.bezier(0.2, 0, 0, 1);

/** Décalage d'entrée en cascade pour une liste. Ne jamais dépasser 8 éléments. */
export const STAGGER_MS = 40;
```

### Comment choisir

| Situation | Configuration |
|---|---|
| Appui, relâchement, bascule | `snappy` |
| Navigation d'écran, apparition de carte | `standard` |
| Feuille modale, panneau de filtres | `gentle` |
| Coche de succès, montant qui apparaît | `bouncy` |
| Tout ce qui suit un doigt | `gesture` |
| Opacité, couleur, progression | `withTiming(v, { duration: timing.fast, easing: ease })` |

---

## 3. Haptique

Le retour haptique est **prescriptif**, pas décoratif. Une impulsion mal calibrée est pire que son absence : elle apprend à l'utilisateur à ignorer le canal.

```ts
// src/lib/haptics.ts
import * as Haptics from 'expo-haptics';

export const haptic = {
  /** Appui sur une touche du pavé numérique ou du pavé PIN. Le plus fréquent — doit rester léger. */
  tap:      () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),

  /** Bascule, sélection dans une liste, changement d'onglet, cran de filtre. */
  select:   () => Haptics.selectionAsync(),

  /** Appui sur une action significative : ouvrir une feuille, valider une étape. */
  press:    () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium),

  /** Point de non-retour : confirmation d'un paiement, appui long. */
  commit:   () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy),

  /** Opération réussie. Uniquement sur un état terminal de succès. */
  success:  () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),

  /** Opération en cours ou issue incertaine. */
  warning:  () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning),

  /** Échec : PIN erroné, solde insuffisant, opération rejetée. */
  error:    () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error),
};
```

### Table de correspondance intégrale

| Interaction | Impulsion | Instant |
|---|---|---|
| Touche du pavé numérique | `tap` | au `pressIn` |
| Effacement du pavé | `tap` | au `pressIn` |
| Pastille de PIN remplie | `tap` | au `pressIn` |
| PIN complet, 4 pastilles remplies | `press` | à la 4ᵉ saisie |
| PIN erroné | `error` | au début de l'animation de secousse |
| Bouton principal | `press` | au `pressIn` |
| Bouton secondaire ou texte | `tap` | au `pressIn` |
| Changement d'onglet | `select` | au changement |
| Sélection d'un opérateur | `select` | à la sélection |
| Bascule d'un filtre | `select` | à la bascule |
| Masquer / afficher le solde | `select` | à la bascule |
| **Confirmation de paiement** | `commit` | au relâchement, avant la requête |
| Ouverture de feuille modale | `press` | au début de l'animation |
| Fermeture par geste | *aucune* | — |
| Tirer-pour-rafraîchir déclenché | `select` | au franchissement du seuil |
| Solde rafraîchi | *aucune* | — |
| **Succès de paiement** | `success` | à l'apparition de la coche |
| **Échec de paiement** | `error` | à l'apparition de l'écran |
| Paiement en cours | `warning` | à l'apparition de l'écran |
| Erreur de validation d'un champ | `error` | à l'apparition de l'erreur |
| Copie du numéro d'opération | `select` | à la copie |
| Chargement de page suivante | *aucune* | — |

Deux interdits :

- **Jamais d'haptique sur un événement non provoqué par l'utilisateur** — arrivée de données, fin de rafraîchissement en arrière-plan, revalidation de cache.
- **Jamais d'haptique en rafale.** Deux impulsions à moins de 50 ms d'intervalle se ressentent comme une vibration parasite. Étrangler globalement.

L'haptique se désactive intégralement si le réglage système « retour haptique » est coupé, ou si l'utilisateur l'a désactivé dans les réglages de l'app.

---

## 4. Le comportement de pression

C'est le geste le plus fréquent de l'application. Sa qualité conditionne le ressenti général plus que n'importe quelle transition d'écran.

```ts
// src/components/primitives/Pressable.tsx  (extrait de comportement)
const scale = useSharedValue(1);
const opacity = useSharedValue(1);

const gesture = Gesture.Tap()
  .maxDuration(10_000)
  .onBegin(() => {
    scale.value = withSpring(0.96, spring.snappy);
    opacity.value = withTiming(0.9, { duration: 80 });
    runOnJS(haptic.press)();
  })
  .onFinalize(() => {
    scale.value = withSpring(1, spring.snappy);
    opacity.value = withTiming(1, { duration: 120 });
  });
```

Facteur d'échelle par catégorie — un grand élément doit se comprimer **moins** qu'un petit pour produire la même sensation :

| Élément | Échelle pressée |
|---|---|
| Bouton principal pleine largeur | `0,97` |
| Carte, ligne de liste | `0,98` |
| Touche du pavé numérique | `0,90` |
| Icône, bouton compact | `0,88` |
| Carte de solde héros | `0,99` |

Trois exigences non négociables :

1. **Le geste est piloté par `Gesture.Tap()` de gesture-handler, pas par `TouchableOpacity`.** Le composant RN de base passe par le pont JS : sous charge, le retour arrive avec 200 ms de retard, ce qui est immédiatement perceptible.
2. **Le relâchement rebondit vers 1, il ne revient pas linéairement.** C'est ce léger dépassement qui donne la sensation de matière.
3. **La zone tactile est étendue** à 44 dp minimum via `hitSlop`, indépendamment de la taille visuelle.

---

## 5. Transitions d'écran

### 5.1 Navigation en pile

Entrée depuis la droite, ressort `standard`, avec parallaxe sur l'écran sortant.

| Écran | Translation X | Opacité | Échelle |
|---|---|---|---|
| Entrant | `100 % → 0` | `1` (constante) | `1` |
| Sortant | `0 → −25 %` | `1 → 0,6` | `1 → 0,96` |

L'écran sortant se déplace au quart de la vitesse de l'entrant. Ce décalage est ce qui crée la perception de profondeur. Sans lui, la navigation paraît plate.

Le geste de retour par balayage suit le doigt en `gesture`, avec un seuil de validation à **35 % de la largeur ou 800 dp/s de vélocité**. La vélocité doit être prise en compte : un balayage rapide et court doit valider, sinon le geste paraît lourd.

### 5.2 Feuilles modales

Entrée depuis le bas en `gentle`. Le voile passe de `0` à `0,72` en `timing.normal`.

Points d'ancrage : `[0,5 · hauteur, 0,92 · hauteur]` selon le contenu. Le glissement suit le doigt en `gesture`, avec résistance progressive au-delà du point le plus haut — la feuille ralentit à mesure qu'on tire, elle ne se bloque pas net.

Fermeture si le déplacement dépasse 25 % de la hauteur **ou** si la vélocité descendante dépasse 500 dp/s.

### 5.3 Transition partagée liste → détail

**La transition signature de l'application.** Elle s'applique entre une ligne d'historique et son écran de détail.

| Élément | Comportement |
|---|---|
| Pastille d'icône | Interpole sa position et son diamètre, de 44 dp dans la liste vers 64 dp en tête du détail |
| Montant | Interpole sa position et sa taille, de `bodyLg` vers `displayMd` |
| Fond de ligne | S'étend jusqu'au plein écran en `standard` |
| Reste du détail | Entrée en cascade, décalage `STAGGER_MS`, translation Y de 12 dp, opacité `0 → 1` |

Implémentation : mesure de la ligne source via `measure()` sur le thread UI, valeurs partagées transmises via le contexte de navigation. Reanimated 3 le fait sans repasser par JS.

Si la transition partagée ne peut pas être réalisée dans le budget de la V1, le repli **imposé** est un fondu-échelle (`0,94 → 1`, opacité `0 → 1`, `standard`). Jamais une transition de pile latérale : elle contredit la relation de contenance entre la liste et son détail.

### 5.4 Changement d'onglet

Fondu croisé en `timing.fast`, sans translation. L'icône de l'onglet actif passe de contour à plein, avec une mise à l'échelle `1 → 1,12 → 1` en `bouncy`. L'indicateur actif glisse sous les onglets en `snappy`.

---

## 6. Les moments signature

Sept séquences travaillées au-delà du strict nécessaire. Elles portent la perception de qualité de toute l'application.

### 6.1 L'apparition du solde

Au premier affichage de l'accueil, ou après toute opération réussie, le solde **compte** jusqu'à sa valeur.

| Attribut | Valeur |
|---|---|
| Durée | 900 ms |
| Courbe | `Easing.out(Easing.cubic)` — rapide puis décélération marquée |
| Départ | `0` au premier chargement ; **valeur précédente** après une opération |
| Rendu | Chiffres tabulaires, séparateurs de milliers recalculés à chaque image |
| Exécution | Intégralement sur le thread UI via `useDerivedValue` |
| Haptique | Aucune |

Partir de la valeur précédente après un paiement, et non de zéro, est essentiel : l'utilisateur **voit** son argent bouger. C'est la différence entre un chiffre qui se met à jour et une transaction qui se ressent.

### 6.2 Le pavé de saisie de montant

Chaque chiffre entre par le bas.

| Attribut | Valeur |
|---|---|
| Entrée du chiffre | Translation Y `+20 → 0`, opacité `0 → 1`, `snappy` |
| Effacement | Translation Y `0 → +20`, opacité `1 → 0`, `snappy` |
| Repositionnement | Le montant entier se recentre en `standard` à chaque changement de largeur |
| Réduction de taille | Au-delà de 7 chiffres, le corps passe de `displayLg` à `displayMd` en `standard` |
| Dépassement du solde | Secousse horizontale + le montant passe en `danger.500` pendant 400 ms, puis revient |

### 6.3 Le pavé de saisie du PIN

Le composant le plus utilisé de l'application, puisque chaque opération l'exige.

| Événement | Animation |
|---|---|
| Pastille remplie | Échelle `0 → 1,25 → 1` en `bouncy` + couleur `neutral.500 → accent.500` |
| Pastille effacée | Échelle `1 → 0` en `snappy` |
| PIN complet | Les pastilles pulsent ensemble une fois, échelle `1 → 1,08 → 1` |
| **PIN erroné** | Secousse horizontale : `0 → −10 → 10 → −6 → 6 → 0` en séquence `snappy`, ≈ 400 ms au total, pastilles en `danger.500`, `haptic.error` |
| Réinitialisation après échec | Les pastilles disparaissent une à une de droite à gauche, décalage 30 ms |

La secousse est le détail le plus reconnaissable d'iOS. La reproduire exactement — amplitude décroissante, pas d'amplitude constante — est ce qui la rend crédible.

### 6.4 La séquence de succès de paiement

Séquence chorégraphiée de 650 ms. Chaque étape a son instant précis.

```
  0 ms   L'écran entre en fondu + échelle 0,96 → 1                    [standard]
120 ms   L'anneau de la coche se dessine, 0 → 360°                    [timing.slow, ease]
280 ms   Le tracé de la coche se dessine                              [timing.normal, ease]
280 ms   ↳ haptic.success — synchronisé sur le début du tracé
380 ms   Le montant monte de +16 dp et apparaît en fondu              [bouncy]
460 ms   Le libellé du destinataire apparaît                          [standard]
520 ms   Le nouveau solde apparaît en fondu + compte                  [voir §6.1]
650 ms   Les boutons d'action montent de +20 dp                       [standard]
```

Le dessin de l'anneau et de la coche se fait par `strokeDashoffset` animé sur un `Path` SVG, sur le thread UI.

Ce qui est **interdit** ici : confettis, particules, animation Lottie, mise à l'échelle exagérée. Un paiement réussi est un fait sobre. La qualité vient de la précision du minutage, pas de l'exubérance.

### 6.5 Le squelette de chargement

Aucun indicateur circulaire centré dans toute l'application. Sans exception.

| Attribut | Valeur |
|---|---|
| Forme | Réplique exacte de la disposition finale — mêmes rectangles, mêmes positions |
| Fond | `bg.surface2` |
| Miroitement | Dégradé linéaire balayant de gauche à droite, cycle de 1200 ms |
| Largeur du miroitement | 40 % de la largeur du conteneur |
| Opacité | `0 → 0,08 → 0` sur le passage |
| Seuil d'apparition | **200 ms.** En dessous, ne rien afficher du tout |
| Sortie | Fondu croisé de 200 ms vers le contenu réel, jamais une substitution brutale |

### 6.6 La compression de la carte de solde au défilement

Sur l'accueil, la carte héros se transforme progressivement à mesure que la liste défile.

| Décalage | Effet |
|---|---|
| `0 → 120` | Échelle de la carte `1 → 0,92`, opacité `1 → 0` |
| `60 → 120` | Le solde apparaît en compact dans la barre de navigation, opacité `0 → 1`, translation Y `+8 → 0` |
| `> 120` | Un filet apparaît sous la barre de navigation, opacité `0 → 1` |

Le tout est piloté par `useAnimatedScrollHandler`, entièrement sur le thread UI. **Aucun `onScroll` JavaScript.**

### 6.7 La frise des états de transaction

Sur l'écran de détail, la frise des transitions se dessine à l'ouverture. C'est la manifestation visible du différenciateur produit.

| Élément | Animation |
|---|---|
| Nœuds | Apparition en cascade de haut en bas, échelle `0 → 1` en `bouncy`, décalage 80 ms |
| Segments | La ligne verticale se dessine entre deux nœuds en `timing.normal`, entre leurs apparitions |
| Nœud terminal | Pulse une fois, échelle `1 → 1,15 → 1` |
| **Opération en cours** | Le dernier nœud pulse en boucle, `1 → 1,1 → 1` en 1600 ms, et le segment suivant est en pointillés animés |
| Horodatages | Fondu à `0,6` d'opacité, 100 ms après leur nœud |

Le pouls continu du nœud courant est ce qui transforme une donnée d'audit en information vivante.

---

## 7. Règles d'exécution

### 7.1 Le thread UI ou rien

| Interdit | À la place |
|---|---|
| `Animated` de React Native | `react-native-reanimated` v3 |
| `useState` piloté par `onScroll` | `useAnimatedScrollHandler` + `useSharedValue` |
| `LayoutAnimation` | `Layout` de Reanimated ou animations explicites |
| `TouchableOpacity` / `TouchableHighlight` | `Pressable` de gesture-handler |
| `setInterval` pour animer | `useFrameCallback` ou `withRepeat` |
| Recalculer un style dans le rendu | `useAnimatedStyle` |

### 7.2 Réduire les animations

Quand `AccessibilityInfo.isReduceMotionEnabled()` est vrai :

- Toute translation et toute mise à l'échelle sont remplacées par un fondu de `timing.fast`.
- Les animations en boucle (pouls, miroitement) s'arrêtent sur leur état de repos.
- Les cascades deviennent simultanées.
- L'haptique est **conservée** — c'est un canal distinct, et le supprimer retirerait du retour à qui en a le plus besoin.
- Le compteur de solde affiche directement la valeur finale.

Aucun retour visuel ne disparaît jamais : il change de forme.

### 7.3 Budget d'animation

| Contrainte | Valeur |
|---|---|
| Animations simultanées à l'écran | ≤ 6 |
| Éléments dans une cascade | ≤ 8 — au-delà, la dernière entrée paraît en retard |
| Durée d'une transition d'écran | ≤ 350 ms |
| Durée d'une transition de composant | ≤ 250 ms |
| Boucles actives sur un écran | ≤ 1 |

Toute animation en boucle s'arrête lorsque son écran perd le premier plan.