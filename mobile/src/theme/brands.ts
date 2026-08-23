/**
 * Couleurs de marque des opérateurs Mobile Money.
 *
 * Ce ne sont **pas** des jetons du design system : ce sont des données
 * externes, imposées par des tiers. Elles vivent ici uniquement parce que
 * `src/theme/` est le seul répertoire où une valeur de couleur peut être
 * écrite littéralement.
 *
 * Emploi strictement limité — design system §2.6 : une pastille circulaire
 * d'identification de 40 dp. Elles ne teintent **jamais** un fond, un bouton,
 * un texte ou une bordure.
 */
export const brandColors = {
  ORANGE_MONEY: '#FF7900',
  MTN_MOMO: '#FFCB05',
  MOOV_MONEY: '#0066B3',
  WAVE: '#1DC8FF',
} as const;

export type BrandId = keyof typeof brandColors;
