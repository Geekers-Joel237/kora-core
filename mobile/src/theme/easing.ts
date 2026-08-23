/**
 * Courbes d'accélération — `docs/03-motion-and-feel.md` §2.
 *
 * Séparé de `./motion.ts` à dessein : ce module importe le **runtime** de
 * Reanimated, alors que les jetons de mouvement sont de la donnée pure. La
 * séparation garde `@/theme` importable partout, y compris là où le natif
 * n'existe pas.
 *
 * N'est donc **pas** réexporté par `@/theme` : les composants qui animent
 * l'importent explicitement.
 */

import { Easing } from 'react-native-reanimated';

import { EASE_BEZIER } from './motion';

/** Courbe unique de l'application. Réservée à l'opacité, la couleur, la progression. */
export const ease = Easing.bezier(...EASE_BEZIER);

/** Décélération pure — compteur de solde, §6.1. */
export const easeOutCubic = Easing.out(Easing.cubic);
