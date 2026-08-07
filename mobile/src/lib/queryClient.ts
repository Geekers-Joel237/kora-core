import { QueryClient } from '@tanstack/react-query';

import { isKoraError } from '@/lib/http';

/** Clés de requête — `docs/06-architecture.md` §4.2. */
export const qk = {
  balance: ['balance'] as const,
  history: (filters: Record<string, unknown> = {}) => ['history', filters] as const,
  historyAll: ['history'] as const,
};

/** Durées de cache — architecture §4.3. */
const STALE = { balance: 30_000, history: 60_000 } as const;
const GC = { balance: 5 * 60_000, history: 10 * 60_000 } as const;

export const cacheTimes = { STALE, GC };

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        /**
         * La reprise est déjà gérée par le client HTTP, qui connaît la seule
         * règle qui compte : jamais sur `/payments/*`. La rejouer ici la
         * multiplierait et contournerait cette garantie.
         */
        retry: false,
        refetchOnWindowFocus: false,
        staleTime: STALE.history,
        gcTime: GC.history,
      },
      mutations: { retry: false },
    },
  });
}

/**
 * Une session expirée ne doit pas remonter comme une erreur d'écran : le client
 * HTTP l'a déjà traitée par rafraîchissement, ou a déconnecté. L'afficher
 * doublerait le signal.
 */
export function isSilentError(error: unknown): boolean {
  return isKoraError(error) && error.isAuthExpired;
}
