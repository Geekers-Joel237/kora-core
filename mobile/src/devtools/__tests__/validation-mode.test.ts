import { toCurl, isReplayable } from '@/devtools/network/curl';
import {
  MAX_ENTRIES,
  signalsOf,
  startNetworkLog,
  useNetworkLog,
  type NetworkEntry,
} from '@/devtools/network/store';
import {
  armForcedResponse,
  armNetworkCut,
  MAX_LATENCY_MS,
  resetSimulation,
  setLatency,
  startSimulation,
  useSimulation,
} from '@/devtools/simulation/store';
import {
  MASK,
  maskHeaders,
  maskSecrets,
  registerHttpObserver,
  registerHttpSimulator,
  registerTokenProvider,
  request,
  __resetRefreshState,
} from '@/lib/http';

const BASE = 'http://localhost:8081';

function entry(overrides: Partial<NetworkEntry> = {}): NetworkEntry {
  return {
    id: 'c-1',
    method: 'GET',
    path: '/payments/history',
    query: { page: 0, size: 20 },
    correlationId: 'c-1',
    headers: { Accept: 'application/json', Authorization: `Bearer ${MASK}` },
    body: undefined,
    startedAt: 1_800_000_000_000,
    response: { status: 200, body: null, durationMs: 120 },
    refreshed: false,
    replayed: false,
    ...overrides,
  };
}

// ─────────────────────────────────────────────────────────── Masquage ───────

describe('masquage — docs/10-validation-mode.md §3', () => {
  it('masque `rawPin` sans exception, même imbriqué', () => {
    const masked = maskSecrets({
      amount: 25000,
      rawPin: '1234',
      nested: { rawPin: '5678', list: [{ rawPin: '9999' }] },
    }) as Record<string, unknown>;

    // « avec `rawPin` masqué en `****`, sans exception, même en développement »
    expect(JSON.stringify(masked)).not.toContain('1234');
    expect(JSON.stringify(masked)).not.toContain('5678');
    expect(JSON.stringify(masked)).not.toContain('9999');
    expect(masked.rawPin).toBe(MASK);
    expect(masked.amount).toBe(25000);
  });

  it('masque aussi les jetons — §8 interdit leur affichage en clair', () => {
    const masked = JSON.stringify(
      maskSecrets({ accessToken: 'ey.secret.value', refreshToken: 'r.secret.value' }),
    );
    expect(masked).not.toContain('secret');
  });

  it('garde le schéma d’autorisation lisible, jamais sa valeur', () => {
    const masked = maskHeaders({
      Authorization: 'Bearer ey.tres.secret',
      'X-Correlation-Id': 'c-1',
    });

    expect(masked.Authorization).toBe(`Bearer ${MASK}`);
    expect(masked['X-Correlation-Id']).toBe('c-1');
  });

  it('est insensible à la casse des en-têtes', () => {
    expect(maskHeaders({ authorization: 'Bearer x' }).authorization).toBe(`Bearer ${MASK}`);
    expect(maskHeaders({ AUTHORIZATION: 'Bearer x' }).AUTHORIZATION).toBe(`Bearer ${MASK}`);
  });

  it('ne boucle pas sur une structure profonde', () => {
    let deep: unknown = { rawPin: '1234' };
    for (let index = 0; index < 20; index += 1) deep = { nested: deep };
    expect(() => maskSecrets(deep)).not.toThrow();
  });
});

// ───────────────────────────────────────────────────────────── cURL ─────────

describe('reproduction en cURL — §3', () => {
  it('reproduit la méthode, l’URL et la chaîne de requête', () => {
    const command = toCurl(entry(), BASE);
    expect(command).toContain('curl -X GET');
    expect(command).toContain(`${BASE}/payments/history?page=0&size=20`);
  });

  it('ne recopie jamais un secret — il n’en a jamais vu', () => {
    const command = toCurl(
      entry({ method: 'POST', path: '/payments/transfer', body: { rawPin: MASK, amount: 25000 } }),
      BASE,
    );
    expect(command).toContain(`Bearer ${MASK}`);
    expect(command).toContain(`\\"rawPin\\":\\"${MASK}\\"`.replace(/\\/g, ''));
  });

  it('produit deux fois le même texte pour le même appel', () => {
    // Sans ordre stable, comparer deux copies devient un exercice de patience.
    expect(toCurl(entry(), BASE)).toBe(toCurl(entry(), BASE));
  });

  it('échappe les guillemets simples plutôt que de casser la commande', () => {
    const command = toCurl(entry({ query: { q: "l'historique" } }), BASE);
    expect(command).not.toMatch(/[^\\]'l'historique/);
  });

  it('n’autorise le rejeu que sur un `GET`', () => {
    // Sans idempotence serveur, rejouer une écriture depuis un outil de
    // diagnostic est le meilleur moyen de créer un second débit.
    expect(isReplayable(entry({ method: 'GET' }))).toBe(true);
    expect(isReplayable(entry({ method: 'POST', path: '/payments/transfer' }))).toBe(false);
    expect(isReplayable(entry({ method: 'POST', path: '/auth/login' }))).toBe(false);
  });
});

// ─────────────────────────────────────────────────────── Signaux du §3 ──────

describe('signaux visuels — §3', () => {
  it('signale toute réponse ≥ 400', () => {
    expect(signalsOf(entry({ response: { status: 422, body: null, durationMs: 10 } }))).toContain(
      'error',
    );
    expect(signalsOf(entry({ response: { status: 200, body: null, durationMs: 10 } }))).not.toContain(
      'error',
    );
  });

  it('signale une absence de réponse comme une erreur', () => {
    expect(signalsOf(entry({ response: { status: 0, body: null, durationMs: 20_000 } }))).toContain(
      'error',
    );
  });

  it('signale une durée supérieure à 1 000 ms', () => {
    expect(signalsOf(entry({ response: { status: 200, body: null, durationMs: 1001 } }))).toContain(
      'slow',
    );
    expect(signalsOf(entry({ response: { status: 200, body: null, durationMs: 999 } }))).not.toContain(
      'slow',
    );
  });

  it('signale le rafraîchissement de jeton et le rejeu', () => {
    const signals = signalsOf(entry({ refreshed: true, replayed: true }));
    // Le signal jaune est le plus précieux : il rend visible une mécanique
    // autrement totalement invisible — architecture §3.4.
    expect(signals).toContain('refresh');
    expect(signals).toContain('replay');
  });
});

// ──────────────────────────────────────────── Journal branché sur le client ─

describe('journal réseau branché sur la couche HTTP', () => {
  let stop: () => void;

  beforeEach(() => {
    __resetRefreshState();
    useNetworkLog.setState({ entries: [] });
    stop = startNetworkLog();
    globalThis.fetch = jest.fn(async () => ({
      status: 200,
      ok: true,
      text: async () => JSON.stringify({ ok: true }),
    })) as unknown as typeof fetch;
    registerTokenProvider({
      getAccessToken: () => 'access-secret-value',
      getRefreshToken: () => 'refresh-secret-value',
      onTokensRefreshed: jest.fn(),
      onSessionExpired: jest.fn(),
    });
  });

  afterEach(() => {
    stop();
    registerTokenProvider(null);
  });

  it('enregistre la requête et sa réponse', async () => {
    await request('/payments/balance');

    const [logged] = useNetworkLog.getState().entries;
    expect(logged?.path).toBe('/payments/balance');
    expect(logged?.response?.status).toBe(200);
  });

  it('n’enregistre jamais le jeton ni le PIN, même en développement', async () => {
    await request('/payments/transfer', {
      method: 'POST',
      body: { rawPin: '1234', amount: 25000 },
    });

    const journal = JSON.stringify(useNetworkLog.getState().entries);
    expect(journal).not.toContain('access-secret-value');
    expect(journal).not.toContain('1234');
    expect(journal).toContain(MASK);
  });

  it('plafonne le journal à 200 entrées', async () => {
    for (let index = 0; index < MAX_ENTRIES + 10; index += 1) {
      await request('/payments/balance');
    }
    expect(useNetworkLog.getState().entries).toHaveLength(MAX_ENTRIES);
  });
});

// ─────────────────────────────────────────────── Simulation d'échec — §7 ────

describe('simulation d’échec — §7', () => {
  let stop: () => void;

  beforeEach(() => {
    resetSimulation();
    stop = startSimulation();
    registerHttpObserver(null);
    __resetRefreshState();
    globalThis.fetch = jest.fn(async () => ({
      status: 200,
      ok: true,
      text: async () => '{}',
    })) as unknown as typeof fetch;
  });

  afterEach(() => {
    stop();
    resetSimulation();
  });

  it('borne la latence forcée au plafond du §7', () => {
    setLatency(99_999);
    expect(useSimulation.getState().latencyMs).toBe(MAX_LATENCY_MS);
    setLatency(-10);
    expect(useSimulation.getState().latencyMs).toBe(0);
  });

  it('impose un statut au prochain appel correspondant, puis se désarme', async () => {
    armForcedResponse({ pathFragment: '/payments/', status: 503, detail: 'Service unavailable' });

    await expect(request('/payments/transfer', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 503,
    });

    // À usage unique : un statut qui resterait armé transformerait la session
    // de validation en débogage du mode validation lui-même.
    expect(useSimulation.getState().forced).toBeNull();
    await expect(request('/payments/balance')).resolves.toEqual({});
  });

  it('ne touche pas aux chemins non ciblés', async () => {
    armForcedResponse({ pathFragment: '/payments/', status: 503, detail: 'x' });
    await expect(request('/auth/refresh', { auth: false })).resolves.toEqual({});
    expect(useSimulation.getState().forced).not.toBeNull();
  });

  it('coupe le réseau au milieu d’un POST de paiement — issue incertaine', async () => {
    armNetworkCut({ pathFragment: '/payments/', afterMs: 1 });

    // Le scénario que le §7 désigne comme le plus important de l'application.
    globalThis.fetch = jest.fn(
      (_url: string, init?: { signal?: AbortSignal }) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new Error('Aborted')));
        }),
    ) as unknown as typeof fetch;

    await expect(
      request('/payments/transfer', { method: 'POST', body: { amount: 1 } }),
    ).rejects.toMatchObject({ isOutcomeUnknown: true });

    // Et **une seule** requête : aucun rejeu automatique sur un paiement.
    expect((globalThis.fetch as unknown as jest.Mock).mock.calls).toHaveLength(1);
  });

  it('se désarme intégralement', () => {
    setLatency(2000);
    armForcedResponse({ pathFragment: '/x', status: 401, detail: 'x' });
    armNetworkCut({ pathFragment: '/x', afterMs: 10 });

    resetSimulation();

    expect(useSimulation.getState()).toMatchObject({ latencyMs: 0, forced: null, cut: null });
  });
});

afterAll(() => {
  registerHttpObserver(null);
  registerHttpSimulator(null);
});
