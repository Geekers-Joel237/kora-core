/**
 * Simulation d'échec côté client — `docs/10-validation-mode.md` §7.
 *
 * Le backend expose `kora.provider.behavior` pour ce qu'il sait produire.
 * Ce module produit ce que le serveur **ne peut pas** simuler : la disparition
 * du client au milieu d'une requête.
 *
 * > « La perte de réseau au milieu d'un `POST /payments/transfer` est le
 * > scénario le plus important à valider de toute l'application. »
 *
 * Deux principes de conception :
 *
 * 1. **Les injections ponctuelles sont à usage unique.** Un statut imposé qui
 *    resterait actif transformerait la session de validation en session de
 *    débogage du mode validation lui-même.
 * 2. **La latence, elle, est persistante** tant qu'on ne la remet pas à zéro :
 *    c'est un décor, pas un événement.
 */

import { create } from 'zustand';

import { registerHttpSimulator, type ObservedMethod } from '@/lib/http';

/** Plafond du §7 — au-delà, l'expiration du client HTTP prend le relais. */
export const MAX_LATENCY_MS = 5000;

export interface ForcedResponse {
  /** Fragment de chemin ; `/payments/` couvre tous les paiements. */
  pathFragment: string;
  status: number;
  /** `ProblemDetail` minimal, pour que la normalisation ait de quoi travailler. */
  detail: string;
}

export interface NetworkCut {
  pathFragment: string;
  afterMs: number;
}

interface SimulationState {
  latencyMs: number;
  /** Armé pour **le prochain appel correspondant**, puis désarmé. */
  forced: ForcedResponse | null;
  cut: NetworkCut | null;
  /** Nombre d'injections consommées — retour visible que quelque chose a eu lieu. */
  fired: number;
}

const INITIAL: SimulationState = { latencyMs: 0, forced: null, cut: null, fired: 0 };

export const useSimulation = create<SimulationState>(() => ({ ...INITIAL }));

export function setLatency(ms: number): void {
  useSimulation.setState({ latencyMs: Math.max(0, Math.min(MAX_LATENCY_MS, Math.round(ms))) });
}

export function armForcedResponse(forced: ForcedResponse | null): void {
  useSimulation.setState({ forced });
}

export function armNetworkCut(cut: NetworkCut | null): void {
  useSimulation.setState({ cut });
}

export function resetSimulation(): void {
  useSimulation.setState({ ...INITIAL });
}

function matches(path: string, fragment: string): boolean {
  return fragment.trim() !== '' && path.includes(fragment.trim());
}

/**
 * Branche le simulateur sur la couche HTTP. Renvoie la fonction de retrait.
 */
export function startSimulation(): () => void {
  registerHttpSimulator({
    latencyMs: () => useSimulation.getState().latencyMs,

    forcedResponse: (path: string, _method: ObservedMethod) => {
      const { forced } = useSimulation.getState();
      if (!forced || !matches(path, forced.pathFragment)) return null;

      // Désarmé immédiatement : à usage unique, sans exception.
      useSimulation.setState((state) => ({ forced: null, fired: state.fired + 1 }));

      return {
        status: forced.status,
        body: { status: forced.status, detail: forced.detail, title: 'Réponse imposée' },
      };
    },

    abortAfterMs: (path: string, _method: ObservedMethod) => {
      const { cut } = useSimulation.getState();
      if (!cut || !matches(path, cut.pathFragment)) return null;

      useSimulation.setState((state) => ({ cut: null, fired: state.fired + 1 }));
      return cut.afterMs;
    },
  });

  return () => registerHttpSimulator(null);
}
