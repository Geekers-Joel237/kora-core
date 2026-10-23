/**
 * Jetons de mouvement — `docs/03-motion-and-feel.md` §2.
 *
 * **Loi 1 : le mouvement se fait par ressort, pas par durée.**
 * Une durée décrit un mouvement qui ignore son passé. Un ressort possède une
 * vélocité, donc peut être interrompu, redirigé, repris sans discontinuité.
 *
 * `withTiming` n'est autorisé que pour l'opacité, la couleur et une barre de
 * progression déterministe. Tout déplacement, toute mise à l'échelle, toute
 * rotation passe par `withSpring`.
 */

// `import type` est effacé à la compilation : ce module reste de la **donnée
// pure**, sans dépendance au runtime natif de Reanimated. Les courbes
// d'accélération, qui sont des valeurs, vivent dans `./easing.ts` — importé
// uniquement par les composants qui animent réellement.
import type { WithSpringConfig } from 'react-native-reanimated';

export const spring = {
  /** Défaut. Élastique juste ce qu'il faut, sans oscillation visible. */
  standard: { damping: 20, stiffness: 200, mass: 1 },
  /** Réactif et sec. Appuis de bouton, pastilles de PIN, puces. */
  snappy: { damping: 26, stiffness: 340, mass: 0.8 },
  /** Expressif. Écrans de succès, apparitions d'éléments héros. */
  bouncy: { damping: 12, stiffness: 180, mass: 1 },
  /** Ample et posé. Feuilles modales, grandes surfaces. */
  gentle: { damping: 24, stiffness: 120, mass: 1.1 },
  /** Suit le doigt. Aucun rebond toléré sur un geste. */
  gesture: { damping: 30, stiffness: 400, mass: 0.6, overshootClamping: true },
} as const satisfies Record<string, WithSpringConfig>;

export type SpringToken = keyof typeof spring;

export const timing = {
  /** Apparition d'une superposition. Plancher perceptif : en deçà, ça scintille. */
  instant: 120,
  /** Fondu, changement de couleur. */
  fast: 200,
  /** Transition standard. */
  normal: 280,
  /** Révélation d'un élément héros. */
  slow: 400,
  /** Séquence de succès complète. */
  celebration: 650,
} as const;

export type TimingToken = keyof typeof timing;

/**
 * Points de contrôle de la courbe d'accélération unique — emphatique et
 * décélérée, la même que Material 3 et que les transitions système d'iOS.
 * Toute autre courbe est un écart à justifier.
 *
 * La fonction correspondante est `ease` dans `./easing.ts`.
 */
export const EASE_BEZIER = [0.2, 0, 0, 1] as const;

/** Décalage d'entrée en cascade. Ne jamais dépasser 8 éléments. §7.3 */
export const STAGGER_MS = 40;

/** Seuil sous lequel aucun état de chargement ne s'affiche. NFR-07, §6.5 */
export const SKELETON_DELAY_MS = 200;

/** Facteurs de compression au toucher. §4 — un grand élément se comprime moins. */
export const pressScale = {
  /** Bouton principal pleine largeur. */
  button: 0.97,
  /** Carte, ligne de liste. */
  card: 0.98,
  /** Touche de pavé numérique. */
  key: 0.9,
  /** Icône, bouton compact. */
  icon: 0.88,
  /** Carte de solde héros. */
  hero: 0.99,
} as const;

export type PressScaleToken = keyof typeof pressScale;

/** Budget d'animation — §7.3. Dépassé, le rendu décroche sur l'appareil socle. */
export const motionBudget = {
  maxConcurrentAnimations: 6,
  maxStaggerItems: 8,
  maxScreenTransitionMs: 350,
  maxComponentTransitionMs: 250,
  maxLoopsPerScreen: 1,
} as const;
