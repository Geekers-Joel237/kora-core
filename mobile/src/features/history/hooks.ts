import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';

import { cacheTimes, qk } from '@/lib/queryClient';
import { isTerminalState, type Page, type Transaction } from '@/types/domain';
import { DEFAULT_PAGE_SIZE, getHistory, getTransactionDetail } from './api';
import { serializeFilters, toHistoryQuery, type ActiveFilters } from './filters';
import { findInPages } from './grouping';

/** Nombre d'opérations affichées sur l'accueil — `docs/05-screens.md` §3. */
export const RECENT_SIZE = 5;

/** Frontière R1 : renvoie des `Transaction` de domaine, jamais des DTO. */
export function useRecentTransactions() {
  return useQuery({
    queryKey: qk.history({ size: RECENT_SIZE }),
    queryFn: () => getHistory({ size: RECENT_SIZE }),
    staleTime: cacheTimes.STALE.history,
    gcTime: cacheTimes.GC.history,
  });
}

/**
 * Historique paginé de l'onglet Activité.
 *
 * **La clé porte la sélection, pas les bornes résolues.** `toHistoryQuery`
 * produit un `from` calculé à partir de l'instant : le mettre dans la clé
 * fabriquerait une clé neuve à chaque rendu, donc un rechargement en boucle.
 * La sélection, elle, est stable — d'où `serializeFilters`.
 */
export function useHistoryInfinite(filters: ActiveFilters, enabled = true) {
  return useInfiniteQuery({
    queryKey: qk.history({ f: serializeFilters(filters) }),
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      getHistory({ ...toHistoryQuery(filters), page: pageParam, size: DEFAULT_PAGE_SIZE }),
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
    staleTime: cacheTimes.STALE.history,
    gcTime: cacheTimes.GC.history,
    enabled,
  });
}

/** Sondage du détail — mêmes bornes que `usePendingPolling`, contrat §6.4. */
const POLL_INTERVAL_MS = 5000;
const POLL_MAX_TICKS = 12;

/**
 * Détail d'une opération, par **rejeu de sa page d'origine** — contrat §6.7.
 *
 * Une opération encore en cours est re-sollicitée toutes les 5 s afin que la
 * frise se complète sous les yeux de l'utilisateur. Le plafond de douze cycles
 * est repris tel quel du sondage post-paiement : aucun canal temps réel
 * n'existe, et sonder au-delà d'une minute ne rapporte rien.
 *
 * `CONTOURNEMENT(indéterminé)` — `API_CAPABILITIES.transactionById`.
 */
export function useTransactionDetail(id: string, page: number, filters: ActiveFilters) {
  return useQuery({
    queryKey: ['transaction', id, page, serializeFilters(filters)] as const,
    queryFn: () =>
      getTransactionDetail(id, {
        page,
        size: DEFAULT_PAGE_SIZE,
        filters: toHistoryQuery(filters),
      }),
    staleTime: cacheTimes.STALE.history,
    gcTime: cacheTimes.GC.history,
    refetchInterval: (query) => {
      const transaction = query.state.data;
      if (!transaction || isTerminalState(transaction.state)) return false;
      // `dataUpdateCount` fait office de compteur de cycles : pas de compteur
      // parallèle à tenir, donc pas de compteur à désynchroniser.
      if (query.state.dataUpdateCount > POLL_MAX_TICKS) return false;
      return POLL_INTERVAL_MS;
    },
  });
}

/**
 * Opération déjà présente dans le cache de la liste.
 *
 * Permet d'afficher montant, contrepartie et date **immédiatement**, pendant
 * que la requête `detail=true` va chercher l'historique d'états. Sans cela,
 * l'écran de détail s'ouvrirait sur un squelette alors que l'application
 * connaît déjà l'essentiel de ce qu'elle doit montrer.
 */
export function useCachedTransaction(id: string): Transaction | null {
  const client = useQueryClient();

  const entries = client.getQueriesData<{ pages: Page<Transaction>[] } | Page<Transaction>>({
    queryKey: qk.historyAll,
  });

  for (const [, data] of entries) {
    if (!data) continue;
    const pages = 'pages' in data ? data.pages : [data];
    const found = findInPages(pages, id);
    if (found) return found.transaction;
  }

  return null;
}