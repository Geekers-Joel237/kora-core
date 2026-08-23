/**
 * Aplatissement des pages en lignes de liste — `docs/05-screens.md` §5.
 *
 * La liste virtualisée ne connaît qu'un tableau plat : les en-têtes de jour y
 * sont des éléments comme les autres, repérés par leurs index pour l'épinglage.
 */

import { dayKey, formatSectionHeader } from '@/lib/datetime';
import type { Page, Transaction } from '@/types/domain';

export type HistoryRow =
  | { kind: 'header'; key: string; label: string }
  | {
      kind: 'row';
      key: string;
      transaction: Transaction;
      /**
       * Page d'origine — **transportée jusqu'à l'écran de détail**, qui rejoue
       * exactement cette requête faute de `GET /payments/{id}`. Contrat §6.7.
       */
      page: number;
    };

export interface HistoryRows {
  rows: HistoryRow[];
  /** Index des en-têtes, dans l'ordre — attendu par `stickyHeaderIndices`. */
  stickyIndices: number[];
}

/**
 * Le suivi du dernier jour émis traverse les pages : une journée à cheval sur
 * deux pages ne doit produire qu'un seul en-tête, sans quoi « Aujourd'hui »
 * réapparaîtrait au milieu de la liste à chaque chargement.
 */
export function toHistoryRows(pages: Page<Transaction>[]): HistoryRows {
  const rows: HistoryRow[] = [];
  const stickyIndices: number[] = [];
  let lastDay: string | null = null;

  for (const page of pages) {
    for (const transaction of page.items) {
      const day = dayKey(transaction.createdAt);

      if (day !== lastDay) {
        stickyIndices.push(rows.length);
        rows.push({
          kind: 'header',
          key: `h-${day}`,
          label: formatSectionHeader(transaction.createdAt),
        });
        lastDay = day;
      }

      rows.push({
        kind: 'row',
        key: transaction.id,
        transaction,
        page: page.page,
      });
    }
  }

  return { rows, stickyIndices };
}

/** Retrouve une opération déjà chargée — sert d'affichage immédiat au détail. */
export function findInPages(
  pages: Page<Transaction>[],
  id: string,
): { transaction: Transaction; page: number } | null {
  for (const page of pages) {
    const transaction = page.items.find((item) => item.id === id);
    if (transaction) return { transaction, page: page.page };
  }
  return null;
}