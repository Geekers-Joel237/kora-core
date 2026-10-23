import { env } from '@/lib/env';

/**
 * Unique porte d'entrée du mode validation (`docs/10-validation-mode.md`).
 *
 * Tout accès aux devtools passe par un import dynamique gardé par cette
 * constante, afin que le secouage d'arbre élimine intégralement
 * `src/devtools/` du bundle de production.
 *
 * Vérification exigée au lot 10 : l'analyse du bundle de production ne doit
 * contenir aucun module de ce répertoire.
 */
export const DEV_MODE: boolean = __DEV__ || env.appEnv === 'staging';
