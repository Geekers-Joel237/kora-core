import { __resetRefreshState, request } from '../client';
import { registerTokenProvider, type TokenProvider } from '../session';
import { isKoraError } from '../errors';

jest.mock('expo-crypto', () => ({
  randomUUID: () => '00000000-0000-4000-8000-000000000000',
}));

interface StubResponse {
  status: number;
  body: unknown;
}

function jsonResponse({ status, body }: StubResponse): Response {
  return {
    status,
    ok: status >= 200 && status < 300,
    text: async () => (body === null ? '' : JSON.stringify(body)),
  } as unknown as Response;
}

const TOKENS = {
  accessToken: 'access-2',
  accessTokenExpiry: '2026-08-06T12:15:00Z',
  refreshToken: 'refresh-2',
  refreshTokenExpiry: '2026-08-13T12:00:00Z',
};

let fetchMock: jest.Mock;
let provider: TokenProvider & {
  refreshedCount: number;
  expiredCount: number;
};

function queue(...responses: StubResponse[]): void {
  responses.forEach((response) => {
    fetchMock.mockImplementationOnce(async () => jsonResponse(response));
  });
}

function callsTo(path: string): number {
  return fetchMock.mock.calls.filter(([url]) => String(url).includes(path)).length;
}

beforeEach(() => {
  __resetRefreshState();
  fetchMock = jest.fn();
  globalThis.fetch = fetchMock as unknown as typeof fetch;

  provider = {
    refreshedCount: 0,
    expiredCount: 0,
    getAccessToken: () => 'access-1',
    getRefreshToken: () => 'refresh-1',
    onTokensRefreshed() {
      this.refreshedCount += 1;
    },
    onSessionExpired() {
      this.expiredCount += 1;
    },
  };
  registerTokenProvider(provider);
});

afterEach(() => {
  registerTokenProvider(null);
  jest.restoreAllMocks();
});

describe('injection des en-têtes', () => {
  it('ajoute le Bearer et un identifiant de corrélation', async () => {
    queue({ status: 200, body: { ok: true } });
    await request('/payments/balance');

    const headers = fetchMock.mock.calls[0]?.[1]?.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer access-1');
    expect(headers['X-Correlation-Id']).toBeTruthy();
  });

  it('omet le Bearer quand auth vaut false', async () => {
    queue({ status: 200, body: { message: 'OTP sent' } });
    await request('/auth/login', { method: 'POST', body: {}, auth: false });

    const headers = fetchMock.mock.calls[0]?.[1]?.headers as Record<string, string>;
    expect(headers.Authorization).toBeUndefined();
  });

  it("transporte l'Idempotency-Key dès aujourd'hui — CONTOURNEMENT(étape-4)", async () => {
    queue({ status: 200, body: {} });
    await request('/payments/transfer', {
      method: 'POST',
      body: {},
      idempotencyKey: 'key-abc',
    });

    const headers = fetchMock.mock.calls[0]?.[1]?.headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBe('key-abc');
  });
});

describe('les deux 401 — contrat §5.2', () => {
  it('rafraîchit et rejoue UNE fois sur un 401 de jeton', async () => {
    queue(
      { status: 401, body: { status: 401, error: 'Unauthorized' } },
      { status: 200, body: TOKENS }, // /auth/refresh
      { status: 200, body: { amount: 125000 } }, // rejeu
    );

    const result = await request<{ amount: number }>('/payments/balance');

    expect(result.amount).toBe(125000);
    expect(callsTo('/auth/refresh')).toBe(1);
    expect(provider.refreshedCount).toBe(1);
    expect(provider.expiredCount).toBe(0);
  });

  it('ne rafraîchit JAMAIS sur un 401 de PIN', async () => {
    queue({ status: 401, body: { status: 401, detail: 'Invalid PIN' } });

    await expect(
      request('/payments/transfer', { method: 'POST', body: {} }),
    ).rejects.toMatchObject({ code: 'INVALID_PIN', isAuthExpired: false });

    expect(callsTo('/auth/refresh')).toBe(0);
    expect(provider.refreshedCount).toBe(0);
  });

  it('ne rafraîchit JAMAIS sur un 401 d’OTP', async () => {
    queue({ status: 401, body: { status: 401, detail: 'OTP code does not match' } });

    await expect(
      request('/auth/verify-otp', { method: 'POST', body: {}, auth: false }),
    ).rejects.toMatchObject({ code: 'INVALID_OTP' });

    expect(callsTo('/auth/refresh')).toBe(0);
  });

  it('signale la session expirée quand le rafraîchissement échoue', async () => {
    queue(
      { status: 401, body: { status: 401, error: 'Unauthorized' } },
      { status: 401, body: { status: 401, error: 'Unauthorized' } }, // /auth/refresh
    );

    await expect(request('/payments/balance')).rejects.toMatchObject({
      isAuthExpired: true,
    });

    expect(provider.expiredCount).toBe(1);
  });

  it('ne tente qu’un seul rafraîchissement par requête', async () => {
    queue(
      { status: 401, body: { status: 401, error: 'Unauthorized' } },
      { status: 200, body: TOKENS },
      { status: 401, body: { status: 401, error: 'Unauthorized' } }, // rejeu, encore 401
    );

    await expect(request('/payments/balance')).rejects.toMatchObject({
      code: 'TOKEN_EXPIRED',
    });

    expect(callsTo('/auth/refresh')).toBe(1);
  });
});

describe('rafraîchissement en vol groupé — architecture §3.4', () => {
  it('trois 401 simultanés ne déclenchent qu’UN seul /auth/refresh', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (String(url).includes('/auth/refresh')) {
        return jsonResponse({ status: 200, body: TOKENS });
      }
      // Chaque appel métier échoue une fois, puis réussit.
      const key = String(url);
      served[key] = (served[key] ?? 0) + 1;
      return served[key] === 1
        ? jsonResponse({ status: 401, body: { status: 401, error: 'Unauthorized' } })
        : jsonResponse({ status: 200, body: { ok: true } });
    });
    const served: Record<string, number> = {};

    await Promise.all([
      request('/payments/balance'),
      request('/payments/history'),
      request('/payments/history', { query: { page: 1 } }),
    ]);

    expect(callsTo('/auth/refresh')).toBe(1);
    expect(provider.refreshedCount).toBe(1);
  });
});

describe('politique de reprise — architecture §3.5', () => {
  it('reprend un GET jusqu’à trois fois sur 503', async () => {
    queue(
      { status: 503, body: { status: 503 } },
      { status: 503, body: { status: 503 } },
      { status: 200, body: { ok: true } },
    );

    await expect(request('/payments/history')).resolves.toEqual({ ok: true });
    expect(callsTo('/payments/history')).toBe(3);
  });

  it('ne reprend JAMAIS un POST de paiement sur 503', async () => {
    queue({ status: 503, body: { status: 503 } });

    const error = await request('/payments/cash-in', { method: 'POST', body: {} }).catch(
      (e: unknown) => e,
    );

    expect(isKoraError(error) && error.isOutcomeUnknown).toBe(true);
    expect(callsTo('/payments/cash-in')).toBe(1);
  });

  it('ne reprend JAMAIS un POST de paiement sur coupure réseau', async () => {
    fetchMock.mockImplementation(async () => {
      throw new TypeError('Network request failed');
    });

    const error = await request('/payments/transfer', { method: 'POST', body: {} }).catch(
      (e: unknown) => e,
    );

    expect(isKoraError(error) && error.isOutcomeUnknown).toBe(true);
    expect(isKoraError(error) && error.code).toBe('NETWORK');
    expect(callsTo('/payments/transfer')).toBe(1);
  });

  it('ne reprend pas un 422 métier', async () => {
    queue({ status: 422, body: { status: 422, detail: 'Insufficient funds' } });

    await expect(
      request('/payments/transfer', { method: 'POST', body: {} }),
    ).rejects.toMatchObject({ code: 'BUSINESS_RULE', isOutcomeUnknown: false });

    expect(callsTo('/payments/transfer')).toBe(1);
  });
});

describe('construction de la requête', () => {
  it('sérialise les paramètres et omet les valeurs indéfinies', async () => {
    queue({ status: 200, body: {} });
    await request('/payments/history', {
      query: { page: 0, size: 20, type: 'CASH_IN', state: undefined },
    });

    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('page=0');
    expect(url).toContain('size=20');
    expect(url).toContain('type=CASH_IN');
    expect(url).not.toContain('state=');
  });
});
