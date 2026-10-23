import { QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react-native';
import type { ReactNode } from 'react';

import { DEFAULT_PAGE_SIZE, getTransactionDetail } from '@/features/history/api';
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

function tx(reference: string, createdAt: Date): Transaction {
  return {
    reference,
    kind: 'DEPOSIT',
    outcome: 'success',
    direction: 'INBOUND',
    amount: { minor: 50000, currency: 'XOF' },
    channel: 'ORANGE_MONEY',
    counterpart: null,
    createdAt,
  };
}

function page(
  cursor: string | null,
  items: Transaction[],
  nextCursor: string | null,
): Page<Transaction> {
  return { items, cursor, nextCursor };
}

const AUG_10 = new Date(2026, 7, 10, 9, 30);
const AUG_9 = new Date(2026, 7, 9, 18, 0);
const AUG_8 = new Date(2026, 7, 8, 7, 15);

describe('groupement par jour — docs/05-screens.md §5', () => {
  it('n’émet qu’un en-tête pour une journée à cheval sur deux pages', () => {
    const { rows, stickyIndices } = toHistoryRows([
      page(null, [tx('a', AUG_10), tx('b', AUG_10), tx('c', AUG_9)], 'c1'),
      page('c1', [tx('d', AUG_9), tx('e', AUG_8)], null),
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

  it('transporte le curseur de la page d’origine sur chaque ligne — contrat §6.7', () => {
    const { rows } = toHistoryRows([
      page(null, [tx('a', AUG_10)], 'c1'),
      page('c1', [tx('b', AUG_9)], null),
    ]);

    const cursors = rows.flatMap((row) => (row.kind === 'row' ? [row.cursor] : []));
    expect(cursors).toEqual([null, 'c1']);
  });

  it('retrouve une opération par sa référence, avec le curseur de sa page', () => {
    const pages = [page(null, [tx('a', AUG_10)], 'c1'), page('c1', [tx('b', AUG_9)], null)];

    expect(findInPages(pages, 'b')).toEqual({ transaction: pages[1]?.items[0], cursor: 'c1' });
    expect(findInPages(pages, 'absent')).toBeNull();
  });

  it('rend une liste vide sans en-tête orphelin', () => {
    expect(toHistoryRows([])).toEqual({ rows: [], stickyIndices: [] });
    expect(toHistoryRows([page(null, [], null)])).toEqual({ rows: [], stickyIndices: [] });
  });
});

describe('modèle de filtres', () => {
  it('compte la période et la plage comme un seul filtre', () => {
    expect(activeFilterCount(NO_FILTERS)).toBe(0);
    expect(activeFilterCount({ ...NO_FILTERS, kind: 'DEPOSIT', period: '7d' })).toBe(2);
    expect(activeFilterCount({ ...NO_FILTERS, status: 'FAILED' })).toBe(1);
    expect(activeFilterCount({ ...NO_FILTERS, from: AUG_8, to: AUG_10 })).toBe(1);
  });

  it('rend période et plage mutuellement exclusives', () => {
    const ranged = withRange(NO_FILTERS, AUG_8, AUG_10);
    expect(withPeriod(ranged, '30d')).toMatchObject({ period: '30d', from: null, to: null });
    expect(withRange({ ...NO_FILTERS, period: '30d' }, AUG_8, AUG_10).period).toBeNull();
  });

  it('fait l’aller-retour par la sérialisation de navigation', () => {
    const filters: ActiveFilters = {
      kind: 'TRANSFER_SENT',
      status: 'REFUNDED',
      period: null,
      from: AUG_8,
      to: AUG_10,
    };

    const parsed = parseFilters(serializeFilters(filters));

    expect(parsed.kind).toBe('TRANSFER_SENT');
    expect(parsed.status).toBe('REFUNDED');
    // Les bornes sont des jours : elles reviennent à minuit, le même jour.
    expect(parsed.from?.getDate()).toBe(8);
    expect(parsed.from?.getHours()).toBe(0);
    expect(parsed.to?.getDate()).toBe(10);
  });

  it('produit une chaîne stable pour deux sélections équivalentes', () => {
    const a: ActiveFilters = { ...NO_FILTERS, kind: 'DEPOSIT', period: '7d' };
    const b: ActiveFilters = { ...NO_FILTERS, period: '7d', kind: 'DEPOSIT' };
    expect(serializeFilters(a)).toBe(serializeFilters(b));
  });

  it('ignore les valeurs inconnues plutôt que de les propager — règle R2', () => {
    const parsed = parseFilters('k=CRYPTO_SWAP&s=SETTLED&p=1y&f=pas-un-jour');
    expect(parsed).toEqual(NO_FILTERS);
  });

  it('fait primer la fenêtre sur la plage quand les deux sont lues', () => {
    const parsed = parseFilters('p=30d&f=2026-08-08&u=2026-08-10');
    expect(parsed.period).toBe('30d');
    expect(parsed.from).toBeNull();
    expect(parsed.to).toBeNull();
  });
});

describe('traduction en paramètres serveur', () => {
  it('fait débuter « 7 j » six jours avant aujourd’hui, aujourd’hui compris', () => {
    const now = new Date(2026, 7, 10, 15, 30);
    const query = toHistoryQuery({ ...NO_FILTERS, period: '7d' }, now);

    expect(query.from?.getDate()).toBe(4);
    expect(query.from?.getHours()).toBe(0);
    expect(query.to).toBeUndefined();
  });

  it('envoie une plage personnalisée en jours, le dernier inclus côté serveur', () => {
    const query = toHistoryQuery({ ...NO_FILTERS, from: AUG_8, to: AUG_10 });

    expect(query.from?.getDate()).toBe(8);
    expect(query.to?.getDate()).toBe(10);
  });

  it('envoie le statut client tel quel : le serveur couvre toute la famille', () => {
    expect(toHistoryQuery({ ...NO_FILTERS, status: 'FAILED' }).status).toBe('FAILED');
  });

  it('n’envoie aucun paramètre sans sélection', () => {
    expect(toHistoryQuery(NO_FILTERS)).toEqual({});
  });
});

// ─────────────────────────────────────────────── Réseau : curseur, rejeu ────

function apiItem(reference: string) {
  return {
    reference,
    kind: 'DEPOSIT',
    status: 'COMPLETED',
    amount: 50000,
    currency: 'XOF',
    counterpart: null,
    channel: 'ORANGE_MONEY',
    date: '2026-08-10T11:42:13Z',
  };
}

/** Première page `TRX-0` puis `cursor=c1` → `TRX-1`, dernière page. */
function pageFor(url: string) {
  return url.includes('cursor=c1')
    ? { items: [apiItem('TRX-1')], nextCursor: null }
    : { items: [apiItem('TRX-0')], nextCursor: 'c1' };
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
    text: async () => JSON.stringify(pageFor(String(url))),
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
  it('demande vingt éléments puis suit le curseur jusqu’à la dernière page', async () => {
    const { result } = await renderHook(() => useHistoryInfinite(NO_FILTERS), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const first = String(fetchMock.mock.calls[0]?.[0]);
    expect(first).toContain(`limit=${DEFAULT_PAGE_SIZE}`);
    expect(first).not.toContain('cursor=');
    expect(result.current.hasNextPage).toBe(true);

    await act(async () => {
      await result.current.fetchNextPage();
    });

    await waitFor(() => expect(result.current.data?.pages).toHaveLength(2));
    expect(result.current.hasNextPage).toBe(false);
    expect(String(fetchMock.mock.calls[1]?.[0])).toContain('cursor=c1');
    expect(result.current.data?.pages[1]?.cursor).toBe('c1');
  });

  it('transmet le statut et la nature au serveur', async () => {
    const filters: ActiveFilters = { ...NO_FILTERS, status: 'PENDING', kind: 'WITHDRAWAL' };
    const { result } = await renderHook(() => useHistoryInfinite(filters), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('status=PENDING');
    expect(url).toContain('kind=WITHDRAWAL');
  });

  it('envoie les bornes de date en jours, jamais en instants', async () => {
    const filters: ActiveFilters = { ...NO_FILTERS, from: AUG_8, to: AUG_10 };
    const { result } = await renderHook(() => useHistoryInfinite(filters), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('from=2026-08-08');
    expect(url).toContain('to=2026-08-10');
    expect(url).not.toContain('T00');
  });

  it('n’ajoute aucune borne de date sans sélection de période', async () => {
    const { result } = await renderHook(() => useHistoryInfinite(NO_FILTERS), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).not.toContain('from=');
    expect(url).not.toContain('to=');
  });
});

describe('détail par rejeu de la page d’origine — contrat §6.7', () => {
  it('rejoue exactement la page désignée par le curseur', async () => {
    const found = await getTransactionDetail('TRX-1', { cursor: 'c1' });

    expect(found?.reference).toBe('TRX-1');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('cursor=c1');
  });

  it('suit une fois le curseur suivant quand la première page a glissé', async () => {
    const found = await getTransactionDetail('TRX-1', { cursor: null });

    expect(found?.reference).toBe('TRX-1');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('abandonne sans parcourir l’historique quand une page stable ne la contient pas', async () => {
    const found = await getTransactionDetail('TRX-absent', { cursor: 'c1' });

    expect(found).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
