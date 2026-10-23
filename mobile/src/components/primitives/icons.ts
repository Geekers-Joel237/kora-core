/**
 * Jeu d'icônes minimal — `docs/02-design-system.md` §6.2.
 *
 * Une icône par concept, jamais deux. Grille 24×24, tracé sur 20×20 avec 2 dp
 * de marge optique, trait constant de 1,5 dp jamais mis à l'échelle,
 * extrémités et jointures arrondies, aucun remplissage par défaut.
 *
 * Géométrie dérivée du jeu Feather (MIT), qui respecte déjà cette grille.
 */

export type IconShape =
  | { kind: 'path'; d: string }
  | { kind: 'circle'; cx: number; cy: number; r: number; filled?: boolean }
  | { kind: 'rect'; x: number; y: number; w: number; h: number; rx: number }
  | { kind: 'line'; x1: number; y1: number; x2: number; y2: number };

const p = (d: string): IconShape => ({ kind: 'path', d });
const c = (cx: number, cy: number, r: number, filled = false): IconShape => ({
  kind: 'circle',
  cx,
  cy,
  r,
  filled,
});
const rect = (x: number, y: number, w: number, h: number, rx: number): IconShape => ({
  kind: 'rect',
  x,
  y,
  w,
  h,
  rx,
});
const line = (x1: number, y1: number, x2: number, y2: number): IconShape => ({
  kind: 'line',
  x1,
  y1,
  x2,
  y2,
});

export const ICONS = {
  // ── Navigation ──
  home: [p('M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'), p('M9 22V12h6v10')],
  activity: [p('M22 12h-4l-3 9L9 3l-3 9H2')],
  card: [rect(1, 4, 22, 16, 2), line(1, 10, 23, 10)],
  settings: [
    c(12, 12, 3),
    p(
      'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z',
    ),
  ],
  'chevron-left': [p('M15 18l-6-6 6-6')],
  'chevron-right': [p('M9 18l6-6-6-6')],
  'chevron-up': [p('M18 15l-6-6-6 6')],
  'chevron-down': [p('M6 9l6 6 6-6')],
  close: [p('M18 6L6 18'), p('M6 6l12 12')],
  back: [line(19, 12, 5, 12), p('M12 19l-7-7 7-7')],

  // ── Monétaire ──
  'arrow-down-circle': [c(12, 12, 10), p('M8 12l4 4 4-4'), line(12, 8, 12, 16)],
  'arrow-up-circle': [c(12, 12, 10), p('M16 12l-4-4-4 4'), line(12, 16, 12, 8)],
  send: [line(22, 2, 11, 13), p('M22 2l-7 20-4-9-9-4 20-7z')],

  // ── États ──
  'check-circle': [p('M22 11.08V12a10 10 0 1 1-5.93-9.14'), p('M22 4L12 14.01l-3-3')],
  'x-circle': [c(12, 12, 10), p('M15 9l-6 6'), p('M9 9l6 6')],
  clock: [c(12, 12, 10), p('M12 6v6l4 2')],
  'rotate-ccw': [p('M1 4v6h6'), p('M3.51 15a9 9 0 1 0 2.13-9.36L1 10')],
  'alert-triangle': [
    p('M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z'),
    line(12, 9, 12, 13),
    line(12, 17, 12.01, 17),
  ],
  info: [c(12, 12, 10), line(12, 16, 12, 12), line(12, 8, 12.01, 8)],

  // ── Actions ──
  eye: [p('M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z'), c(12, 12, 3)],
  'eye-off': [
    p(
      'M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24',
    ),
    line(1, 1, 23, 23),
  ],
  copy: [rect(9, 9, 13, 13, 2), p('M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1')],
  share: [p('M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8'), p('M16 6l-4-4-4 4'), line(12, 2, 12, 15)],
  filter: [p('M22 3H2l8 9.46V19l4 2v-8.54L22 3z')],
  search: [c(11, 11, 8), line(21, 21, 16.65, 16.65)],
  plus: [line(12, 5, 12, 19), line(5, 12, 19, 12)],
  'more-horizontal': [c(12, 12, 1, true), c(19, 12, 1, true), c(5, 12, 1, true)],

  // ── Sécurité ──
  lock: [rect(3, 11, 18, 11, 2), p('M7 11V7a5 5 0 0 1 10 0v4')],
  shield: [p('M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z')],
  fingerprint: [
    p('M12 10a2 2 0 0 0-2 2c0 2.5.5 4.5 1.5 6.5'),
    p('M8 12a4 4 0 0 1 8 0c0 3-.5 5-1.5 7'),
    p('M5 12a7 7 0 0 1 14 0c0 1.5-.2 3-.6 4.5'),
    p('M6.5 6.5A8.9 8.9 0 0 1 12 4.5c2.1 0 4 .7 5.5 2'),
  ],
  'face-id': [
    p('M4 8V6a2 2 0 0 1 2-2h2'),
    p('M16 4h2a2 2 0 0 1 2 2v2'),
    p('M20 16v2a2 2 0 0 1-2 2h-2'),
    p('M8 20H6a2 2 0 0 1-2-2v-2'),
    line(9, 10, 9, 11),
    line(15, 10, 15, 11),
    p('M9 15a4 4 0 0 0 6 0'),
  ],

  // ── Système ──
  'wifi-off': [
    line(1, 1, 23, 23),
    p('M16.72 11.06A10.94 10.94 0 0 1 19 12.55'),
    p('M5 12.55a10.94 10.94 0 0 1 5.17-2.39'),
    p('M10.71 5.05A16 16 0 0 1 22.58 9'),
    p('M1.42 9a15.91 15.91 0 0 1 4.7-2.88'),
    p('M8.53 16.11a6 6 0 0 1 6.95 0'),
    line(12, 20, 12.01, 20),
  ],
  'refresh-cw': [
    p('M23 4v6h-6'),
    p('M1 20v-6h6'),
    p('M3.51 9a9 9 0 0 1 14.85-3.36L23 10'),
    p('M1 14l4.64 4.36A9 9 0 0 0 20.49 15'),
  ],
  calendar: [rect(3, 4, 18, 18, 2), line(16, 2, 16, 6), line(8, 2, 8, 6), line(3, 10, 21, 10)],
} as const satisfies Record<string, readonly IconShape[]>;

export type IconName = keyof typeof ICONS;

export const ICON_NAMES = Object.keys(ICONS) as IconName[];
