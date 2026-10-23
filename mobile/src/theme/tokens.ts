/**
 * Jetons du design system — transcription de `docs/02-design-system.md` §7.
 *
 * **Ce fichier est la seule source légitime de valeurs de style.** La règle de
 * lint `no restricted-syntax` rejette toute couleur, tout espacement, tout
 * rayon et toute taille de police littéraux partout ailleurs.
 *
 * Les composants ne consomment jamais `palette` directement : ils passent par
 * les **rôles sémantiques** de `darkTheme` / `lightTheme`, résolus par
 * `useTheme()`. C'est ce qui permet au thème clair de tout redéfinir sans
 * toucher une ligne de composant.
 */

import type { TextStyle, ViewStyle } from 'react-native';
import { PixelRatio, Platform } from 'react-native';

// ─────────────────────────────────────────────────────────── Palette ────────

export const palette = {
  /** Teinte constante 222°, saturation décroissante avec la clarté. §2.1 */
  neutral: {
    0: '#08090C',
    50: '#0C0E12',
    100: '#13161C',
    200: '#1A1E26',
    300: '#242932',
    400: '#333945',
    500: '#4A5261',
    600: '#6B7484',
    700: '#9AA3B2',
    800: '#C9CFD9',
    900: '#F4F6FA',
  },
  /** Kora Green — vert émeraude électrique. §2.2 */
  accent: {
    100: '#D3FBE4',
    300: '#6EE7A8',
    400: '#34D67F',
    500: '#00C46A',
    600: '#00A557',
    700: '#007F43',
    /**
     * Ajouté au §2.2 d'origine. Nécessaire au thème clair : `accent.600` avec
     * du texte blanc ne donne que 3,22:1, sous le seuil NFR-50. Le palier 700
     * atteint 5,10:1, et il faut donc un cran plus sombre pour l'état pressé.
     */
    800: '#00632F',
  },
  danger: { 300: '#FF8E99', 500: '#FF4D5E' },
  warning: { 300: '#FFCE70', 500: '#FFB020' },
  info: { 300: '#8FBBFF', 500: '#3D8BFF' },
  pending: { 300: '#B4BAC7', 500: '#8B93A5' },
} as const;

/**
 * Texte posé sur `accent.500`. Vert-noir profond, pas du blanc :
 * blanc sur #00C46A donne 2,1:1, sous le seuil. Celui-ci atteint 9,8:1. §2.2
 */
const ON_ACCENT_DARK = '#04140B';

// ─────────────────────────────────────────────────────────── Espacement ─────

/** Base 4, onze crans. Aucune valeur intermédiaire n'existe. §4 */
export const space = {
  0: 0,
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  5: 20,
  6: 24,
  8: 32,
  10: 40,
  12: 48,
  16: 64,
} as const;

export type SpaceToken = keyof typeof space;

// ────────────────────────────────────────────────────────────── Formes ──────

/** Rayon proportionnel à la taille de l'élément. §5.1 */
export const radius = {
  xs: 6,
  sm: 10,
  md: 14,
  lg: 20,
  xl: 28,
  '2xl': 36,
  full: 999,
} as const;

export type RadiusToken = keyof typeof radius;

export const stroke = {
  /** Un pixel physique, pas un dp. §5.2 */
  hairline: 1 / PixelRatio.get(),
  thin: 1,
  regular: 1.5,
  thick: 2,
} as const;

// ──────────────────────────────────────────────────────── Typographie ───────

/**
 * Avec des polices personnalisées, `fontWeight` est **ignoré sur Android** :
 * chaque graisse est un fichier distinct qu'il faut nommer explicitement.
 * Chaque variante porte donc sa `fontFamily`, pas seulement son poids. §3.1
 */
export const fontFamily = {
  regular: 'Inter_400Regular',
  medium: 'Inter_500Medium',
  semibold: 'Inter_600SemiBold',
  bold: 'Inter_700Bold',
} as const;

export type FontFamily = (typeof fontFamily)[keyof typeof fontFamily];

/**
 * Échelle modulaire de rapport 1,25, douze crans. §3.2
 *
 * L'interlettrage négatif sur les grandes tailles est structurel : sans lui, un
 * montant de 56 pt paraît distendu. C'est le détail le plus systématiquement
 * omis, et le plus systématiquement présent dans les interfaces abouties.
 */
export const type = {
  displayXl: t(56, 60, fontFamily.bold, -2.0),
  displayLg: t(44, 48, fontFamily.bold, -1.5),
  displayMd: t(34, 40, fontFamily.bold, -1.0),
  titleLg: t(28, 34, fontFamily.bold, -0.6),
  titleMd: t(22, 28, fontFamily.semibold, -0.4),
  titleSm: t(18, 24, fontFamily.semibold, -0.2),
  bodyLg: t(16, 24, fontFamily.regular, 0),
  bodyMd: t(15, 22, fontFamily.regular, 0),
  bodySm: t(13, 18, fontFamily.regular, 0),
  labelMd: t(13, 16, fontFamily.semibold, 0.2),
  labelSm: t(11, 14, fontFamily.semibold, 0.4),
  monoMd: t(15, 22, fontFamily.medium, 0.4),
} as const;

export type TypeToken = keyof typeof type;

function t(
  fontSize: number,
  lineHeight: number,
  family: FontFamily,
  letterSpacing: number,
): TextStyle {
  return { fontSize, lineHeight, fontFamily: family, letterSpacing };
}

/** Tout montant se compose en chiffres à chasse fixe. §3.4 */
export const tabularNums: Pick<TextStyle, 'fontVariant'> = {
  fontVariant: ['tabular-nums'],
};

/** Le symbole de devise fait 0,45× la taille du montant. §3.4 */
const CURRENCY_SYMBOL_RATIO = 0.45;

/**
 * Style du bloc « symbole » d'un montant — §3.4.
 *
 * Dérivé de la variante du montant, jamais choisi indépendamment : c'est ce qui
 * garantit que le rapport reste constant à toutes les tailles.
 */
export function currencySymbolStyle(variant: TypeToken): TextStyle {
  const base = type[variant];
  return {
    fontSize: Math.round((base.fontSize ?? 0) * CURRENCY_SYMBOL_RATIO),
    fontFamily: fontFamily.semibold,
    letterSpacing: 0,
  };
}

/**
 * Plafonds de mise à l'échelle des polices système. §3.5
 * Un `display` au-delà de 1,3× fait déborder un montant ; un `body` n'a pas
 * de plafond fonctionnel.
 */
export const maxFontScale: Record<TypeToken, number> = {
  displayXl: 1.3,
  displayLg: 1.3,
  displayMd: 1.3,
  titleLg: 1.6,
  titleMd: 1.6,
  titleSm: 1.6,
  bodyLg: 2.0,
  bodyMd: 2.0,
  bodySm: 2.0,
  labelMd: 2.0,
  labelSm: 2.0,
  monoMd: 2.0,
};

// ───────────────────────────────────────────────────── Mise en page ─────────

export const layout = {
  screenPadding: 20,
  cardPadding: 16,
  rowHeight: 68,
  buttonHeight: { lg: 56, md: 48, sm: 40 },
  navBarHeight: 56,
  tabBarHeight: 56,
  minTouchTarget: 44,
  anchoredBarClearance: 88,
  iconBadge: 44,
  iconBadgeLarge: 64,
} as const;

/** Tailles d'icône autorisées. Aucune autre. §6.1 */
export const iconSize = { xs: 16, sm: 20, md: 24, lg: 28, xl: 32 } as const;
export type IconSizeToken = keyof typeof iconSize;

// ─────────────────────────────────────────────────────────── Thèmes ─────────

/**
 * En thème sombre, l'élévation est **une couleur de surface**, pas une ombre :
 * une ombre portée y est invisible. Seul le niveau 4 en porte une, pour
 * détacher un élément flottant du contenu qui défile dessous. §1.2, §5.3
 */
const FLOATING_SHADOW_DARK: ViewStyle = {
  shadowColor: '#000000',
  shadowOpacity: 0.4,
  shadowRadius: 24,
  shadowOffset: { width: 0, height: 8 },
  elevation: 12,
};

/** En thème clair, l'élévation redevient une ombre, très douce et diffuse. §2.7 */
const FLOATING_SHADOW_LIGHT: ViewStyle = {
  shadowColor: '#0C0E12',
  shadowOpacity: 0.06,
  shadowRadius: 24,
  shadowOffset: { width: 0, height: 8 },
  elevation: 8,
};

interface StatusColors {
  fg: string;
  bg: string;
}

/**
 * Forme du thème. Les deux thèmes exposent **exactement** ces clés : aucun
 * composant ne teste jamais le thème actif, il consomme un rôle sémantique
 * qui se résout seul. §2.7
 */
export interface Theme {
  scheme: 'dark' | 'light';
  bg: { app: string; root: string; surface1: string; surface2: string; surface3: string };
  text: {
    primary: string;
    secondary: string;
    tertiary: string;
    disabled: string;
    onAccent: string;
    danger: string;
    success: string;
  };
  accent: { primary: string; pressed: string; soft: string; wash: string; glow: string };
  status: {
    success: StatusColors;
    failed: StatusColors;
    pending: StatusColors;
    reversed: StatusColors;
    info: StatusColors;
  };
  flow: { inbound: string; outbound: string };
  overlay: {
    hairline: string;
    border: string;
    borderStrong: string;
    press: string;
    scrim: string;
  };
  floatingShadow: ViewStyle;
}

export const darkTheme: Theme = {
  scheme: 'dark',
  bg: {
    app: palette.neutral[50],
    root: palette.neutral[0],
    surface1: palette.neutral[100],
    surface2: palette.neutral[200],
    surface3: palette.neutral[300],
  },
  text: {
    primary: palette.neutral[900],
    secondary: palette.neutral[700],
    tertiary: palette.neutral[600],
    disabled: palette.neutral[500],
    onAccent: ON_ACCENT_DARK,
    danger: palette.danger[300],
    success: palette.accent[300],
  },
  accent: {
    primary: palette.accent[500],
    pressed: palette.accent[600],
    soft: palette.accent[300],
    wash: 'rgba(0,196,106,0.12)',
    glow: 'rgba(0,196,106,0.28)',
  },
  /** Unique table faisant autorité. Aucun écran ne définit la sienne. §2.4 */
  status: {
    success: { fg: palette.accent[300], bg: 'rgba(0,196,106,0.12)' },
    failed: { fg: palette.danger[300], bg: 'rgba(255,77,94,0.12)' },
    pending: { fg: palette.pending[300], bg: 'rgba(139,147,165,0.12)' },
    reversed: { fg: palette.warning[300], bg: 'rgba(255,176,32,0.12)' },
    info: { fg: palette.info[300], bg: 'rgba(61,139,255,0.12)' },
  },
  /**
   * Sens du flux monétaire. §2.5
   * Une sortie d'argent **ne se colore pas en rouge** : dépenser est normal,
   * le rouge est réservé à l'échec. Le colorer saturerait le signal d'erreur.
   */
  flow: {
    inbound: palette.accent[300],
    outbound: palette.neutral[900],
  },
  overlay: {
    hairline: 'rgba(255,255,255,0.06)',
    border: 'rgba(255,255,255,0.10)',
    borderStrong: 'rgba(255,255,255,0.16)',
    press: 'rgba(255,255,255,0.08)',
    scrim: 'rgba(8,9,12,0.72)',
  },
  floatingShadow: FLOATING_SHADOW_DARK,
};

/** Thème clair — dérivé systématique du sombre, jamais l'inverse. §1.1, §2.7 */
export const lightTheme: Theme = {
  scheme: 'light',
  bg: {
    app: '#F7F8FA',
    root: '#FFFFFF',
    surface1: '#FFFFFF',
    surface2: '#F0F2F5',
    surface3: '#E4E7EC',
  },
  text: {
    primary: '#0C0E12',
    secondary: '#5A6274',
    tertiary: '#858D9C',
    disabled: '#A8AEBB',
    onAccent: '#FFFFFF',
    danger: '#C62334',
    success: '#00713C',
  },
  accent: {
    // `accent.600` échouerait à 3,22:1 avec du texte blanc. Le palier 700
    // atteint 5,10:1 — vérifié par `__tests__/contrast.test.ts`.
    primary: palette.accent[700],
    pressed: palette.accent[800],
    soft: '#00713C',
    wash: 'rgba(0,127,67,0.12)',
    glow: 'rgba(0,127,67,0.24)',
  },
  status: {
    success: { fg: '#00713C', bg: 'rgba(0,165,87,0.12)' },
    failed: { fg: '#C62334', bg: 'rgba(198,35,52,0.10)' },
    pending: { fg: '#5A6274', bg: 'rgba(90,98,116,0.10)' },
    reversed: { fg: '#8A5A00', bg: 'rgba(255,176,32,0.14)' },
    info: { fg: '#1F5FBF', bg: 'rgba(61,139,255,0.12)' },
  },
  flow: {
    inbound: '#00713C',
    outbound: '#0C0E12',
  },
  overlay: {
    hairline: 'rgba(12,14,18,0.06)',
    border: 'rgba(12,14,18,0.10)',
    borderStrong: 'rgba(12,14,18,0.16)',
    press: 'rgba(12,14,18,0.06)',
    scrim: 'rgba(12,14,18,0.56)',
  },
  floatingShadow: FLOATING_SHADOW_LIGHT,
};

/** Niveaux d'élévation → couleur de surface. §5.3 */
export type ElevationLevel = 0 | 1 | 2 | 3 | 4;

export function surfaceForElevation(theme: Theme, level: ElevationLevel): string {
  switch (level) {
    case 0:
      return theme.bg.app;
    case 1:
      return theme.bg.surface1;
    case 2:
      return theme.bg.surface2;
    default:
      return theme.bg.surface3;
  }
}

/**
 * Règle de composition des rayons imbriqués. §5.1
 * Un enfant prend `rayon du parent − padding`, sinon les coins concentriques
 * se désalignent — défaut discret mais immédiatement perceptible.
 */
export function nestedRadius(parentRadius: number, padding: number): number {
  return Math.max(radius.xs, parentRadius - padding);
}

/** Le flou d'arrière-plan n'existe que sur iOS ; Android retombe sur l'opacité. */
export const supportsBlur = Platform.OS === 'ios';
