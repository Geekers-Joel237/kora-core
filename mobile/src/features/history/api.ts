/** API historique — contrat §2. */

import { decode } from '@/lib/decode';
import { request } from '@/lib/http';
import { toApiDay } from '@/lib/datetime';
import { transactionHistorySchema } from '@/features/shared/schemas';
import { toTransactionPage } from '@/features/shared/mappers';
import type { CustomerStatus, Page, Transaction, TxKind } from '@/types/domain';

/** Plafond appliqué par le serveur : au-delà, `400`. Contrat §2. */
export const MAX_PAGE_SIZE = 100;
export const DEFAULT_PAGE_SIZE = 20;

export interface HistoryFilters {
  kind?: TxKind;
  /** Statut client : le serveur recouvre tous les états de la famille. Contrat §3.1. */
  status?: CustomerStatus;
  /** Premier jour inclus, sur le calendrier local. */
  from?: Date;
  /** Dernier jour inclus, sur le calendrier local. */
  to?: Date;
}

export interface HistoryQuery extends HistoryFilters {
  /** `nextCursor` de la page précédente ; absent pour la première page. */
  cursor?: string | null;
  limit?: number;
}

export async function getHistory(query: HistoryQuery = {}): Promise<Page<Transaction>> {
  const limit = Math.min(query.limit ?? DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  const cursor = query.cursor ?? null;

  const payload = await request<unknown>('/payments/history', {
    query: {
      ...(query.kind && { kind: query.kind }),
      ...(query.status && { status: query.status }),
      ...(query.from && { from: toApiDay(query.from) }),
      ...(query.to && { to: toApiDay(query.to) }),
      ...(cursor && { cursor }),
      limit,
    },
  });

  return toTransactionPage(decode(transactionHistorySchema, payload, '/payments/history'), cursor);
}

/**
 * Retrouve une opération par **rejeu de la page dont elle provient** — contrat §6.7.
 *
 * Aucun `GET /payments/{id}` n'existe. Une page obtenue avec un curseur est
 * stable : la rejouer redonne les mêmes lignes. Seule la première page peut
 * glisser quand de nouvelles opérations arrivent — d'où un unique pas de plus
 * le long de `nextCursor` avant d'abandonner.
 *
 * CONTOURNEMENT(indéterminé) — drapeau `API_CAPABILITIES.transactionById`.
 */
export async function getTransactionDetail(
  reference: string,
  origin: { cursor: string | null; filters?: HistoryFilters },
): Promise<Transaction | null> {
  const page = await getHistory({ ...origin.filters, cursor: origin.cursor });
  const found = page.items.find((tx) => tx.reference === reference);
  if (found) return found;

  if (origin.cursor !== null || !page.nextCursor) return null;

  const next = await getHistory({ ...origin.filters, cursor: page.nextCursor });
  return next.items.find((tx) => tx.reference === reference) ?? null;
}
