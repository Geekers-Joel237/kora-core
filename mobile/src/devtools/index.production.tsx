/**
 * Substitut de production de `src/devtools/index.tsx`.
 *
 * **Ce fichier est la seule chose que le bundle de production connaît du mode
 * validation.** `metro.config.js` détourne toute résolution de
 * `src/devtools/index` vers ce module quand `EXPO_PUBLIC_ENV=production` : le
 * graphe de dépendances ne descend alors jamais dans `src/devtools/`, et les
 * onze modules du panneau — inspecteur de transactions, détecteur de dérive,
 * journal, galerie — n'entrent pas dans la build.
 *
 * Pourquoi une redirection de résolution plutôt qu'un simple `if (!DEV_MODE)` :
 * Metro construit son graphe à partir de la sortie de Babel, **avant** toute
 * minification. Un `import()` gardé par une condition fausse reste donc une
 * dépendance, et le module est empaqueté même si l'appel est ensuite éliminé.
 * Mesuré au lot 10 : les 11 modules étaient présents dans le bundle.
 *
 * La surface publique doit rester **exactement** celle de `index.tsx`.
 */

export function DevtoolsHost(): null {
  return null;
}

export function initDevtools(): void {
  // Sans objet en production.
}

export const DEV_MODE = false;

export type DevtoolsTab =
  | 'network'
  | 'transactions'
  | 'session'
  | 'simulation'
  | 'environment'
  | 'drift'
  | 'journal'
  | 'gallery';

export function openDevtools(_tab?: DevtoolsTab): void {
  // Sans objet en production : le panneau n'existe pas.
}

/** Les trois déclencheurs du §2 n'ont rien à déclencher en production. */
export const devtoolsTrigger = {
  logoLongPress(): void {},
  versionTap(): void {},
};
