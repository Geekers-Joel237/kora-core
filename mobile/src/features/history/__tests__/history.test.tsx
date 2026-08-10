import { QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react-native';
import type { ReactNode } from 'react';

import { DEFAULT_PAGE_SIZE } from '@/features/history/api';
import {
  activeFilterCount,
  NO_FILTERS,
  parseFilters,
  serializeFilters,
  toHistoryQuery,
  withPeriod,
  withRange,
  type ActiveFilters,
} from '@/features/history/filters';
import { findInPages, toHistoryRows } from '@/features/history/grouping';
import { useHistoryInfinite } from '@/features/history/hooks';
import { registerTokenProvider } from '@/lib/http';
import { createQueryClient } from '@/lib/queryClient';
import type { Page, Transaction } from '@/types/domain';

// ────────────────────────────────────────────────────────────── Fixtures ────

function tx(id: string, createdAt: Date): Transaction {
  return {
    id,
    reference: `TRX-${id}`,
    type: 'CASH_IN',
    direction: 'INBOUND',
    state: 'COMPLETED',
    outcome: 'success',
    amount: { minor: 50000, currency: 'XOF' },
    paymentMethod: 'ORANGE_MONEY',
    counterpart: null,
    createdAt,
    stateHistory: null,
  };
}

function page(index: number, items: Transaction[], hasNext: boolean): Page<Transaction> {
  return {
    items,
    page: index,
    size: DEFAULT_PAGE_SIZE,
    totalElements: 5,
    totalPages: 2,
    hasNext,
  };
}

const AUG_10 = new Date(2026, 7, 10, 9, 30);
const AUG_9 = new Date(2026, 7, 9, 18, 0);
const AUG_8 = new Date(2026, 7, 8, 7, 15);

describe('groupement par jour — docs/05-screens.md §5', () => {
  it('n’émet qu’un en-tête pour une journée à cheval sur deux pages', () => {
    const { rows, stickyIndices } = toHistoryRows([
      page(0, [tx('a', AUG_10), tx('b', AUG_10), tx('c', AUG_9)], true),
      page(1, [tx('d', AUG_9), tx('e', AUG_8)], false),
    ]);

    expect(rows.map((row) => row.kind)).toEqual([
      'header',
      'row',
      'row',
      'header',
      'row',
      'row',
      'header',
      'row',
    ]);
    // Trois journées, trois en-têtes — pas quatre malgré la coupure de page.
    expect(stickyIndices).toEqual([0, 3, 6]);
  });

  it('transporte la page d’origine sur chaque ligne — contrat §6.7', () => {
    const { rows } = toHistoryRows([
      page(0, [tx('a', AUG_10)], true),
      page(1, [tx('b', AUG_9)], false),
    ]);

    const pages = rows.filter((row) => row.kind === 'row').map((row) => row.page);
    expect(pages).toEqual([0, 1]);
  });

  it('retrouve une opération et sa page d’origine', () => {
    const pages = [page(0, [tx('a', AUG_10)], true), page(1, [tx('b', AUG_9)], false)];

    expect(findInPages(pages, 'b')).toEqual({ transaction: pages[1]?.items[0], page: 1 });
    expect(findInPages(pages, 'absent')).toBeNull();
  });

  it('rend une liste vide sans en-tête orphelin', () => {
    expect(toHistoryRows([])).toEqual({ rows: [], stickyIndices: [] });
    expect(toHistoryRows([page(0, [], false)])).toEqual({ rows: [], stickyIndices: [] });
  });
});

describe('modèle de filtres — contrat §6.8', () => {
  it('compte la période et la plage comme un seul filtre', () => {
    expect(activeFilterCount(NO_FILTERS)).toBe(0);
    expect(activeFilterCount({ ...NO_FILTERS, type: 'CASH_IN', period: '7d' })).toBe(2);
    expect(activeFilterCount({ ...NO_FILTERS, from: AUG_8, to: AUG_10 })).toBe(1);
  });

  it('rend période et plage mutuellement exclusives', () => {
    const ranged = withRange(NO_FILTERS, AUG_8, AUG_10);
    expect(withPeriod(ranged, '30d')).toMatchObject({ period: '30d', from: null, to: null });
    expect(withRange({ ...NO_FILTERS, period: '30d' }, AUG_8, AUG_10).period).toBeNull();
  });

  it('fait l’aller-retour par la sérialisation de navigation', () => {
    const filters: ActiveFilters = {
      type: 'P2P_TRANSFER',
      direction: 'OUTBOUND',
      state: 'AUTHORIZATION_FAILED',
      period: null,
      from: AUG_8,
      to: AUG_10,
    };

    const parsed = parseFilters(serializeFilters(filters));

    expect(parsed.type).toBe('P2P_TRANSFER');
    expect(parsed.direction).toBe('OUTBOUND');
    expect(parsed.state).toBe('AUTHORIZATION_FAILED');
    // Les bornes reviennent ramenées au jour, pas à l'heure d'origine.
    expect(parsed.from?.getHours()).toBe(0);
    expect(parsed.to?.getHours()).toBe(23);
  });

  it('produit une chaîne stable pour deux sélections équivalentes', () => {
    const a: ActiveFilters = { ...NO_FILTERS, type: 'CASH_IN', period: '7d' };
    const b: ActiveFilters = { ...NO_FILTERS, period: '7d', type: 'CASH_IN' };
    expect(serializeFilters(a)).toBe(serializeFilters(b));
  });

  it('ignore les valeurs inconnues plutôt que de les propager — règle R2', () => {
    const parsed = parseFilters('t=CRYPTO_SWAP&s=REFUNDED&d=SIDEWAYS&p=1y');
    expect(parsed).toEqual(NO_FILTERS);
  });

  it('fait primer la fenêtre sur la plage quand les deux sont lues', () => {
    const parsed = parseFilters(`p=30d&f=${AUG_8.toISOString()}&u=${AUG_10.toISOString()}`);
    expect(parsed.period).toBe('30d');
    expect(parsed.from).toBeNull();
    expect(parsed.to).toBeNull();
  });
});

describe('traduction en paramètres serveur', () => {
  it('fait débuter « 7 j » sept jours pleins avant aujourd’hui inclus', () => {
    const now = new Date(2026, 7, 10, 15, 30);
    const query = toHistoryQuery({ ...NO_FILTERS, period: '7d' }, now);

    expect(query.from?.getDate()).toBe(4);
    expect(query.from?.getHours()).toBe(0);
    expect(query.to).toBeUndefined();
  });

  it('borne une plage personnalisée au jour entier', () => {
    const query = toHistoryQuery({ ...NO_FILTERS, from: AUG_8, to: AUG_10 });

    expect(query.from?.getHours()).toBe(0);
    expect(query.to?.getHours()).toBe(23);
    expect(query.to?.getMinutes()).toBe(59);
  });

  it('n’envoie qu’un seul état — `TransactionFilter.state` n’en accepte pas plus', () => {
    const query = toHistoryQuery({ ...NO_FILTERS, state: 'CAPTURED' });
    expect(query.state).toBe('CAPTURED');
    expect(typeof query.state).toBe('string');
  });

  it('n’envoie aucun paramètre sans sélection', () => {
    expect(toHistoryQuery(NO_FILTERS)).toEqual({});
  });
});

// ───────────────────────────────────────────────── Pagination infinie ───────

function apiPage(index: number, hasNext: boolean) {
  return {
    transactions: [
      {
        transactionId: `tx-${index}`,
        transactionNumber: `TRX-2026081${index}-A3F91C2D`,
        type: 'CASH_IN',
        direction: 'INBOUND',
        state: 'COMPLETED',
        amount: 50000,
        currency: 'XOF',
        paymentMethod: 'ORANGE_MONEY',
        counterpart: null,
        createdAt: '2026-08-10T11:42:13Z',
        stateHistory: null,
      },
    ],
    page: index,
    size: DEFAULT_PAGE_SIZE,
    totalElements: 2,
    totalPages: 2,
    hasNext,
  };
}

let fetchMock: jest.Mock;

function wrapper({ children }: { children: ReactNode }) {
  const client = createQueryClient();
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  fetchMock = jest.fn(async (url: string) => ({
    status: 200,
    ok: true,
    text: async () => JSON.stringify(apiPage(String(url).includes('page=1') ? 1 : 0, !String(url).includes('page=1'))),
  }));
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  registerTokenProvider({
    getAccessToken: () => 'access-1',
    getRefreshToken: () => 'refresh-1',
    onTokensRefreshed: jest.fn(),
    onSessionExpired: jest.fn(),
  });
});

afterEach(() => registerTokenProvider(null));

describe('pagination infinie de l’historique', () => {
  it('demande vingt éléments et enchaîne sur la page suivante', async () => {
    const { result } = await renderHook(() => useHistoryInfinite(NO_FILTERS), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain(`size=${DEFAULT_PAGE_SIZE}`);
    expect(result.current.hasNextPage).toBe(true);

    await act(async () => {
      await result.current.fetchNextPage();
    });

    await waitFor(() => expect(result.current.data?.pages).toHaveLength(2));
    expect(result.current.hasNextPage).toBe(false);
    expect(String(fetchMock.mock.calls[1]?.[0])).toContain('page=1');
  });

  it('transmet le filtre d’état au serveur', async () => {
    const filters: ActiveFilters = { ...NO_FILTERS, state: 'CAPTURED', type: 'CASH_OUT' };
    const { result } = await renderHook(() => useHistoryInfinite(filters), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('state=CAPTURED');
    expect(url).toContain('type=CASH_OUT');
  });

  it('n’ajoute aucune borne de date sans sélection de période', async () => {
    const { result } = await renderHook(() => useHistoryInfinite(NO_FILTERS), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).not.toContain('from=');
    expect(url).not.toContain('to=');
  });
});
