/** API historique — contrat §2. */

import { decode } from '@/lib/decode';
import { request } from '@/lib/http';
import { toApiInstant } from '@/lib/datetime';
import { transactionHistorySchema } from '@/features/shared/schemas';
import { toTransactionPage } from '@/features/shared/mappers';
import type { Direction, Page, Transaction } from '@/types/domain';

/** Plafond appliqué par le serveur : au-delà, `400`. Contrat §2. */
export const MAX_PAGE_SIZE = 100;
export const DEFAULT_PAGE_SIZE = 20;

export interface HistoryFilters {
  type?: string;
  /**
   * **Un seul état**, jamais une famille : `TransactionFilter.state` n'accepte
   * pas de valeur multiple. Contrat §6.8. CONTOURNEMENT(indéterminé).
   */
  state?: string;
  direction?: Direction;
  from?: Date;
  to?: Date;
}

export interface HistoryQuery extends HistoryFilters {
  page?: number;
  size?: number;
  /** N'activer que pour l'écran de détail : la réponse s'alourdit fortement. */
  detail?: boolean;
}

export async function getHistory(query: HistoryQuery = {}): Promise<Page<Transaction>> {
  const size = Math.min(query.size ?? DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);

  const payload = await request<unknown>('/payments/history', {
    query: {
      ...(query.type && { type: query.type }),
      ...(query.state && { state: query.state }),
      ...(query.direction && { direction: query.direction }),
      ...(query.from && { from: toApiInstant(query.from) }),
      ...(query.to && { to: toApiInstant(query.to) }),
      page: query.page ?? 0,
      size,
      ...(query.detail && { detail: true }),
    },
  });

  return toTransactionPage(decode(transactionHistorySchema, payload, '/payments/history'));
}

/**
 * Récupère une opération par rejeu de la page dont elle provient — contrat §6.7.
 *
 * Aucun `GET /payments/{id}` n'existe, et l'historique n'accepte pas de filtre
 * par identifiant. Chercher « dans les 100 premières » serait faux pour une
 * opération ancienne : la navigation transporte donc la page d'origine, et l'on
 * rejoue exactement cette requête avec `detail=true`.
 *
 * CONTOURNEMENT(indéterminé) — drapeau `API_CAPABILITIES.transactionById`.
 */
export async function getTransactionDetail(
  id: string,
  origin: { page: number; size?: number; filters?: HistoryFilters },
): Promise<Transaction | null> {
  const size = origin.size ?? DEFAULT_PAGE_SIZE;

  // La pagination peut avoir glissé si de nouvelles opérations sont arrivées :
  // on élargit d'une page de part et d'autre avant d'abandonner.
  const candidates = [origin.page, origin.page - 1, origin.page + 1].filter((p) => p >= 0);

  for (const page of candidates) {
    const result = await getHistory({ ...origin.filters, page, size, detail: true });
    const found = result.items.find((tx) => tx.id === id);
    if (found) return found;
  }

  return null;
}
