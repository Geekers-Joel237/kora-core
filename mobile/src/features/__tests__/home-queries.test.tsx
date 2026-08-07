import { QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';
import type { ReactNode } from 'react';

import { useRecentTransactions } from '@/features/history/hooks';
import { useBalance } from '@/features/wallet/hooks';
import { registerTokenProvider } from '@/lib/http';
import { createQueryClient, qk } from '@/lib/queryClient';

const BALANCE = {
  accountId: 'acc-1',
  accountNumber: 'ACC-20260806-A3F91C2D',
  amount: 125000,
  currency: 'XOF',
};

const HISTORY = {
  transactions: [
    {
      transactionId: 'tx-1',
      transactionNumber: 'TRX-20260806-A3F91C2D',
      type: 'CASH_IN',
      direction: 'INBOUND',
      state: 'COMPLETED',
      amount: 50000,
      currency: 'XOF',
      paymentMethod: 'ORANGE_MONEY',
      counterpart: null,
      createdAt: '2026-08-06T11:42:13Z',
      stateHistory: null,
    },
  ],
  page: 0,
  size: 5,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
};

/**
 * Le client HTTP reprend un `GET` jusqu'à 3 fois, avec un recul exponentiel de
 * 300 puis 600 ms plus gigue. Une attente d'une seconde expirerait avant la
 * dernière tentative — et ferait échouer le test sur un comportement correct.
 */
const RETRY_WINDOW = { timeout: 5000 };

let fetchMock: jest.Mock;

function ok(body: unknown) {
  return { status: 200, ok: true, text: async () => JSON.stringify(body) };
}

function fail(status: number, body: unknown) {
  return { status, ok: false, text: async () => JSON.stringify(body) };
}

function wrapper({ children }: { children: ReactNode }) {
  const client = createQueryClient();
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  fetchMock = jest.fn();
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  registerTokenProvider({
    getAccessToken: () => 'access-1',
    getRefreshToken: () => 'refresh-1',
    onTokensRefreshed: jest.fn(),
    onSessionExpired: jest.fn(),
  });
});

afterEach(() => registerTokenProvider(null));

describe('isolation des requêtes de l’accueil — docs §3', () => {
  it('le solde aboutit même si l’historique échoue', async () => {
    fetchMock.mockImplementation(async (url: string) =>
      String(url).includes('/payments/balance')
        ? ok(BALANCE)
        : fail(503, { status: 503, detail: 'Service unavailable' }),
    );

    const balance = await renderHook(() => useBalance(), { wrapper });
    const history = await renderHook(() => useRecentTransactions(), { wrapper });

    await waitFor(() => expect(balance.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(history.result.current.isError).toBe(true), RETRY_WINDOW);

    // La règle d'isolation : deux requêtes, deux frontières d'erreur.
    expect(balance.result.current.data?.balance.minor).toBe(125000);
    expect(balance.result.current.isError).toBe(false);
  });

  it('l’historique aboutit même si le solde échoue', async () => {
    fetchMock.mockImplementation(async (url: string) =>
      String(url).includes('/payments/balance')
        ? fail(404, { status: 404, detail: 'Account not found' })
        : ok(HISTORY),
    );

    const balance = await renderHook(() => useBalance(), { wrapper });
    const history = await renderHook(() => useRecentTransactions(), { wrapper });

    await waitFor(() => expect(balance.result.current.isError).toBe(true), RETRY_WINDOW);
    await waitFor(() => expect(history.result.current.isSuccess).toBe(true));

    expect(history.result.current.data?.items).toHaveLength(1);
  });
});

describe('traduction vers le domaine — frontière R1', () => {
  it('convertit le solde en entier d’unité mineure', async () => {
    fetchMock.mockImplementation(async () => ok(BALANCE));

    const { result } = await renderHook(() => useBalance(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual({
      id: 'acc-1',
      number: 'ACC-20260806-A3F91C2D',
      balance: { minor: 125000, currency: 'XOF' },
    });
  });

  it('renvoie des transactions de domaine, jamais des DTO', async () => {
    fetchMock.mockImplementation(async () => ok(HISTORY));

    const { result } = await renderHook(() => useRecentTransactions(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const transaction = result.current.data?.items[0];
    expect(transaction?.outcome).toBe('success');
    expect(transaction?.amount).toEqual({ minor: 50000, currency: 'XOF' });
    expect(transaction?.createdAt).toBeInstanceOf(Date);
    // Aucun champ d'API ne doit avoir traversé la frontière.
    expect(transaction).not.toHaveProperty('transactionId');
  });

  it('ne demande que cinq opérations à l’accueil', async () => {
    fetchMock.mockImplementation(async () => ok(HISTORY));

    const { result } = await renderHook(() => useRecentTransactions(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('size=5');
  });
});

describe('politique de cache — architecture §4.2 et §4.3', () => {
  it('sépare les clés du solde et de l’historique', () => {
    expect(qk.balance).not.toEqual(qk.history({ size: 5 }));
    // L'invalidation de `historyAll` doit couvrir toutes les variantes filtrées.
    expect(qk.history({ size: 5 })[0]).toBe(qk.historyAll[0]);
  });

  it('n’ajoute aucune reprise par-dessus celle du client HTTP', async () => {
    // Le client HTTP connaît la seule règle qui compte : jamais sur
    // `/payments/*` en écriture. La rejouer ici la multiplierait.
    fetchMock.mockImplementation(async () => fail(500, { status: 500 }));

    const { result } = await renderHook(() => useBalance(), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true), RETRY_WINDOW);

    // 3 tentatives du client HTTP sur un GET, pas 9.
    expect(fetchMock.mock.calls.length).toBeLessThanOrEqual(3);
  });
});
