/**
 * Journal réseau — `docs/10-validation-mode.md` §3.
 *
 * « Le composant le plus utile du mode. » Journal en mémoire, **plafonné à 200
 * entrées**, jamais persisté : ces charges utiles ont beau être masquées, elles
 * décrivent l'activité financière de quelqu'un et n'ont rien à faire sur disque.
 *
 * Le plafond est un tampon circulaire, pas une troncature à la lecture : sans
 * lui, une session de validation d'une heure garderait tout en mémoire sur un
 * appareil à 3 Go.
 */

import { create } from 'zustand';

import {
  registerHttpObserver,
  type ObservedEvent,
  type ObservedRequest,
  type ObservedResponse,
} from '@/lib/http';

export const MAX_ENTRIES = 200;

/** Au-delà, la requête est signalée comme lente — §3. */
export const SLOW_MS = 1000;

export interface NetworkEntry extends ObservedRequest {
  response: ObservedResponse | null;
  /** Un rafraîchissement de jeton a été déclenché pendant cette requête. */
  refreshed: boolean;
  /** La requête a été rejouée après rafraîchissement. */
  replayed: boolean;
}

/** Signaux du §3, du plus grave au plus anodin. */
export type NetworkSignal = 'error' | 'slow' | 'refresh' | 'replay';

export function signalsOf(entry: NetworkEntry): NetworkSignal[] {
  const signals: NetworkSignal[] = [];
  if (entry.response && (entry.response.status >= 400 || entry.response.status === 0)) {
    signals.push('error');
  }
  if (entry.response && entry.response.durationMs > SLOW_MS) signals.push('slow');
  if (entry.refreshed) signals.push('refresh');
  if (entry.replayed) signals.push('replay');
  return signals;
}

interface NetworkState {
  /** La plus récente en tête : c'est l'ordre dans lequel on lit un journal. */
  entries: NetworkEntry[];
  clear: () => void;
}

export const useNetworkLog = create<NetworkState>(() => ({
  entries: [],
  clear: () => useNetworkLog.setState({ entries: [] }),
}));

/**
 * Branche le journal sur la couche HTTP. Idempotent : un second appel remplace
 * l'observateur précédent plutôt que d'en empiler un.
 */
export function startNetworkLog(): () => void {
  registerHttpObserver({
    onRequest: (request: ObservedRequest) => {
      useNetworkLog.setState((state) => ({
        entries: [
          { ...request, response: null, refreshed: false, replayed: false },
          ...state.entries,
        ].slice(0, MAX_ENTRIES),
      }));
    },

    onResponse: (id: string, response: ObservedResponse) => {
      useNetworkLog.setState((state) => ({
        entries: state.entries.map((entry) => (entry.id === id ? { ...entry, response } : entry)),
      }));
    },

    onEvent: (event: ObservedEvent) => {
      // Le signal porte sur la requête logique en cours, donc sur la dernière
      // entrée ouverte — celle qui vient de recevoir son `401`.
      useNetworkLog.setState((state) => {
        const [latest, ...rest] = state.entries;
        if (!latest) return state;
        return {
          entries: [
            {
              ...latest,
              refreshed: latest.refreshed || event === 'refresh',
              replayed: latest.replayed || event === 'replay',
            },
            ...rest,
          ],
        };
      });
    },
  });

  return () => registerHttpObserver(null);
}
