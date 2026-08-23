/**
 * Démarrage du mode validation — `docs/10-validation-mode.md`.
 *
 * Trois branchements doivent avoir lieu **avant la première requête**, sinon
 * ils manquent précisément ce qu'il y a de plus intéressant à observer : le
 * parcours d'authentification au lancement.
 *
 * 1. La surcharge d'URL (§6) — sans elle, l'application repart sur l'URL de la
 *    build au redémarrage que la bascule vient de provoquer.
 * 2. Le journal réseau (§3).
 * 3. Le simulateur de défaillance (§7).
 *
 * Appelé une seule fois, depuis `DevtoolsHost`.
 */

import { restoreEnvironment } from './environment/store';
import { startNetworkLog } from './network/store';
import { startSimulation } from './simulation/store';
import { installShakeTrigger } from './triggers';

let started = false;

export function startDevtools(): () => void {
  if (started) return () => undefined;
  started = true;

  restoreEnvironment();
  installShakeTrigger();

  const stopLog = startNetworkLog();
  const stopSimulation = startSimulation();

  return () => {
    stopLog();
    stopSimulation();
    started = false;
  };
}
