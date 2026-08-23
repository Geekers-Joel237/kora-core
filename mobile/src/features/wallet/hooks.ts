import { useQuery } from '@tanstack/react-query';

import { cacheTimes, qk } from '@/lib/queryClient';
import { getBalance } from './api';

/**
 * Solde du portefeuille.
 *
 * **Requête indépendante de l'historique**, délibérément : un échec de l'une
 * ne doit jamais dégrader l'autre section de l'accueil (`docs/05-screens.md` §3).
 * Les regrouper dans une seule requête produirait exactement ce couplage.
 */
export function useBalance() {
  return useQuery({
    queryKey: qk.balance,
    queryFn: getBalance,
    staleTime: cacheTimes.STALE.balance,
    gcTime: cacheTimes.GC.balance,
  });
}
