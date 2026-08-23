import {
  currentApiUrl,
  ENVIRONMENT_PRESETS,
  HEALTH_PATH,
  isOverridden,
  restoreEnvironment,
  switchEnvironment,
  testConnectivity,
} from '@/devtools/environment/store';
import {
  startNetworkLog,
  useNetworkLog,
} from '@/devtools/network/store';
import { env } from '@/lib/env';
import { registerTokenProvider, request, setApiBaseUrl, __resetRefreshState } from '@/lib/http';
import { KvKey, kvGetString, kvSetString } from '@/lib/storage/kv';

beforeEach(() => {
  setApiBaseUrl(null);
  __resetRefreshState();
});

afterEach(() => {
  setApiBaseUrl(null);
  registerTokenProvider(null);
});

describe('bascule d’environnement — docs/10-validation-mode.md §6', () => {
  it('part de l’URL de la build tant que rien n’est surchargé', () => {
    expect(currentApiUrl()).toBe(env.apiUrl);
    expect(isOverridden()).toBe(false);
  });

  it('restaure une surcharge persistée avant la première requête', () => {
    kvSetString(KvKey.apiUrlOverride, 'http://10.0.2.2:8081');

    restoreEnvironment();

    // Sans cela, la bascule ne survivrait pas au redémarrage qu'elle provoque.
    expect(currentApiUrl()).toBe('http://10.0.2.2:8081');
    expect(isOverridden()).toBe(true);
  });

  it('écrit la surcharge APRÈS la purge, jamais avant', async () => {
    kvSetString(KvKey.lastEmail, 'aminata@kora.ci');

    await switchEnvironment('http://192.168.1.10:8081');

    // `kvClear()` emporterait l'URL si elle était écrite en premier.
    expect(kvGetString(KvKey.apiUrlOverride)).toBe('http://192.168.1.10:8081');
    expect(kvGetString(KvKey.lastEmail)).toBeNull();
    expect(currentApiUrl()).toBe('http://192.168.1.10:8081');
  });

  it('revient à l’URL de la build sans laisser de surcharge', async () => {
    await switchEnvironment('http://10.0.2.2:8081');
    await switchEnvironment(null);

    expect(kvGetString(KvKey.apiUrlOverride)).toBeNull();
    expect(currentApiUrl()).toBe(env.apiUrl);
  });

  it('normalise les barres obliques de fin', () => {
    setApiBaseUrl('http://10.0.2.2:8081///');
    expect(currentApiUrl()).toBe('http://10.0.2.2:8081');
  });

  it('propose au moins l’hôte de l’émulateur Android', () => {
    // `localhost` depuis un émulateur désigne l'émulateur lui-même : c'est le
    // piège de configuration le plus fréquent du projet.
    expect(ENVIRONMENT_PRESETS.map((preset) => preset.url)).toContain('http://10.0.2.2:8081');
  });

  it('adresse effectivement les requêtes à l’URL surchargée', async () => {
    const fetchMock: jest.Mock = jest.fn(async () => ({
      status: 200,
      ok: true,
      text: async () => '{}',
    }));
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    setApiBaseUrl('http://10.0.2.2:8081');
    await request('/payments/balance', { auth: false });

    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('http://10.0.2.2:8081/payments/balance');
  });
});

describe('test de connectivité — §6', () => {
  it('interroge `/actuator/health`', async () => {
    const fetchMock: jest.Mock = jest.fn(async () => ({ ok: true, status: 200 }));
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const result = await testConnectivity('http://localhost:8081/');

    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(`http://localhost:8081${HEALTH_PATH}`);
    expect(result.ok).toBe(true);
  });

  it('rapporte un échec HTTP sans lever', async () => {
    globalThis.fetch = jest.fn(async () => ({ ok: false, status: 503 })) as unknown as typeof fetch;

    const result = await testConnectivity('http://localhost:8081');

    expect(result).toMatchObject({ ok: false, reason: 'HTTP 503' });
  });

  it('rapporte une absence de serveur sans lever', async () => {
    globalThis.fetch = jest.fn(async () => {
      throw new Error('Network request failed');
    }) as unknown as typeof fetch;

    const result = await testConnectivity('http://192.168.1.99:8081');

    // Basculer vers une URL injoignable purgerait la session pour rien.
    expect(result.ok).toBe(false);
  });
});

describe('signal de rafraîchissement de jeton — §3', () => {
  it('marque l’entrée comme rafraîchie puis rejouée', async () => {
    useNetworkLog.setState({ entries: [] });
    const stop = startNetworkLog();

    let call = 0;
    globalThis.fetch = jest.fn(async (url: string) => {
      if (String(url).includes('/auth/refresh')) {
        return {
          status: 200,
          ok: true,
          text: async () =>
            JSON.stringify({
              accessToken: 'a2',
              accessTokenExpiry: new Date(Date.now() + 60_000).toISOString(),
              refreshToken: 'r2',
              refreshTokenExpiry: new Date(Date.now() + 600_000).toISOString(),
            }),
        };
      }
      call += 1;
      return call === 1
        ? { status: 401, ok: false, text: async () => JSON.stringify({ status: 401, error: 'Unauthorized' }) }
        : { status: 200, ok: true, text: async () => '{}' };
    }) as unknown as typeof fetch;

    registerTokenProvider({
      getAccessToken: () => 'a1',
      getRefreshToken: () => 'r1',
      onTokensRefreshed: jest.fn(),
      onSessionExpired: jest.fn(),
    });

    await request('/payments/balance');

    // C'est le seul endroit où la mécanique du §3.4 de `06-architecture.md`
    // devient observable : sans ce signal, elle est invisible.
    const entries = useNetworkLog.getState().entries;
    expect(entries.some((entry) => entry.refreshed)).toBe(true);
    expect(entries.some((entry) => entry.replayed)).toBe(true);

    stop();
  });
});
