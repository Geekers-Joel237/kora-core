/**
 * Point de montage unique du mode validation — `docs/10-validation-mode.md` §12.
 *
 * L'application n'importe **jamais** un module de `src/devtools/` directement :
 * elle monte `DevtoolsHost` et appelle `devtoolsTrigger`. En production,
 * `metro.config.js` détourne la résolution de ce fichier vers
 * `index.production.tsx`, et le graphe de dépendances ne descend jamais plus
 * bas — vérifié par `npm run audit:bundle`.
 *
 * Toute valeur exportée ici doit exister à l'identique dans le substitut.
 */

import { lazy, Suspense } from 'react';

import { startDevtools } from './boot';
import { DEV_MODE } from './enabled';

/**
 * Branchement du mode validation, **au chargement du module racine**.
 *
 * Pas dans un effet : les effets d'un enfant se déclenchent avant ceux de son
 * parent, et le portail de session émet son `/auth/refresh` depuis un effet.
 * Un branchement par effet manquerait donc précisément le parcours
 * d'authentification au lancement — le premier des 21 scénarios du §11.
 */
export function initDevtools(): void {
  if (!DEV_MODE) return;
  startDevtools();
}

const DevtoolsPanel = lazy(async () => {
  const module = await import('./panel/DevtoolsPanel');
  return { default: module.DevtoolsPanel };
});

const DriftBanner = lazy(async () => {
  const module = await import('./panel/DriftBanner');
  return { default: module.DriftBanner };
});

export function DevtoolsHost() {
  if (!DEV_MODE) return null;

  // Aucun repli visible : le panneau s'ouvre sur demande, et un indicateur de
  // chargement flottant au-dessus des écrans produit serait pire que l'attente.
  return (
    <Suspense fallback={null}>
      {/* Analyse de dérive au **démarrage**, pas à l'ouverture d'un onglet :
          un écart bloquant doit être annoncé avant le premier symptôme. §5 */}
      <DriftBanner />
      <DevtoolsPanel />
    </Suspense>
  );
}

export { DEV_MODE } from './enabled';
export { openDevtools, type DevtoolsTab } from './panel/store';
export { devtoolsTrigger } from './triggers';
