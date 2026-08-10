/**
 * État du panneau de validation — `docs/10-validation-mode.md` §2.
 *
 * Un store plutôt qu'un état local : le panneau doit pouvoir être ouvert depuis
 * n'importe quel écran, sans que chacun ait à porter un booléen ni à câbler un
 * contexte supplémentaire.
 *
 * `openDevtools()` est le point d'entrée unique des trois déclencheurs du §2 —
 * voir `src/devtools/triggers.ts`.
 */

import { create } from 'zustand';

export const DEVTOOLS_TABS = [
  'network',
  'transactions',
  'session',
  'simulation',
  'environment',
  'drift',
  'journal',
  'gallery',
] as const;
export type DevtoolsTab = (typeof DEVTOOLS_TABS)[number];

export const DEVTOOLS_TAB_LABELS: Record<DevtoolsTab, string> = {
  network: 'Réseau',
  transactions: 'Transactions',
  session: 'Session',
  simulation: 'Simulation',
  environment: 'Environnement',
  drift: 'Dérive',
  journal: 'Journal',
  gallery: 'Galerie',
};

interface DevtoolsState {
  open: boolean;
  tab: DevtoolsTab;
  setTab: (tab: DevtoolsTab) => void;
  close: () => void;
}

export const useDevtools = create<DevtoolsState>((set) => ({
  open: false,
  // L'inspecteur réseau ouvre le panneau : c'est le composant le plus utile
  // du mode, et celui qu'on consulte en premier neuf fois sur dix. §3
  tab: 'network',
  setTab: (tab) => set({ tab }),
  close: () => set({ open: false }),
}));

/** Point d'entrée unique — appelé par les déclencheurs du lot 1bis. */
export function openDevtools(tab?: DevtoolsTab): void {
  useDevtools.setState({ open: true, ...(tab && { tab }) });
}
