import * as SecureStore from 'expo-secure-store';

import { useSession } from '../session';
import { __resetRefreshState } from '@/lib/http';

jest.mock('expo-secure-store', () => {
  const store = new Map<string, string>();
  return {
    getItemAsync: jest.fn(async (key: string) => store.get(key) ?? null),
    setItemAsync: jest.fn(async (key: string, value: string) => void store.set(key, value)),
    deleteItemAsync: jest.fn(async (key: string) => void store.delete(key)),
    WHEN_UNLOCKED_THIS_DEVICE_ONLY: 'whenUnlockedThisDeviceOnly',
    __store: store,
  };
});

const secureStore = (SecureStore as unknown as { __store: Map<string, string> }).__store;

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

/** Base64 sans `Buffer` : ce dernier est une API Node, absente du runtime RN. */
function base64Encode(input: string): string {
  let out = '';
  for (let i = 0; i < input.length; i += 3) {
    const a = input.charCodeAt(i);
    const b = input.charCodeAt(i + 1);
    const c = input.charCodeAt(i + 2);
    out += B64[a >> 2];
    out += B64[((a & 3) << 4) | (Number.isNaN(b) ? 0 : b >> 4)];
    out += Number.isNaN(b) ? '' : B64[((b & 15) << 2) | (Number.isNaN(c) ? 0 : c >> 6)];
    out += Number.isNaN(c) ? '' : B64[c & 63];
  }
  return out;
}

function base64Decode(input: string): string {
  const clean = input.replace(/[^A-Za-z0-9+/]/g, '');
  let out = '';
  for (let i = 0; i < clean.length; i += 4) {
    const n =
      (B64.indexOf(clean[i]!) << 18) |
      (B64.indexOf(clean[i + 1]!) << 12) |
      (Math.max(0, B64.indexOf(clean[i + 2] ?? '')) << 6) |
      Math.max(0, B64.indexOf(clean[i + 3] ?? ''));
    out += String.fromCharCode((n >> 16) & 255);
    if (clean[i + 2] !== undefined) out += String.fromCharCode((n >> 8) & 255);
    if (clean[i + 3] !== undefined) out += String.fromCharCode(n & 255);
  }
  return out;
}

/** JWT non signé, valide en structure — seul le décodage local est exercé. */
function makeToken(payload: Record<string, unknown>): string {
  const encode = (value: unknown) => base64Encode(JSON.stringify(value));
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.signature`;
}

const CLAIMS = { sub: 'user-1', email: 'aminata@kora.ci', role: 'CUSTOMER' };

const FUTURE = new Date(Date.now() + 3_600_000);
const PAST = new Date(Date.now() - 3_600_000);

let fetchMock: jest.Mock;

beforeEach(() => {
  jest.clearAllMocks();
  secureStore.clear();
  __resetRefreshState();
  useSession.setState({ status: 'unknown', tokens: null, user: null });

  fetchMock = jest.fn();
  globalThis.fetch = fetchMock as unknown as typeof fetch;

  // `atob` n'existe pas dans l'environnement Node de Jest ; Hermes le fournit.
  globalThis.atob = base64Decode;
});

describe('bootstrap — portail de session, docs §1', () => {
  it('reste anonyme sans jeton stocké', async () => {
    await useSession.getState().bootstrap();
    expect(useSession.getState().status).toBe('anonymous');
  });

  it('reprend une session dont le jeton d’accès est encore valide', async () => {
    await useSession.getState().adopt({
      accessToken: makeToken(CLAIMS),
      accessTokenExpiry: FUTURE,
      refreshToken: 'refresh-1',
      refreshTokenExpiry: FUTURE,
    });
    useSession.setState({ status: 'unknown', tokens: null, user: null });

    await useSession.getState().bootstrap();

    expect(useSession.getState().status).toBe('authenticated');
    expect(useSession.getState().user?.email).toBe('aminata@kora.ci');
    // Aucun appel réseau : le jeton était valide.
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('rafraîchit un jeton expiré SANS que l’utilisateur ne voie rien', async () => {
    await useSession.getState().adopt({
      accessToken: makeToken(CLAIMS),
      accessTokenExpiry: PAST,
      refreshToken: 'refresh-1',
      refreshTokenExpiry: FUTURE,
    });
    useSession.setState({ status: 'unknown', tokens: null, user: null });

    fetchMock.mockImplementationOnce(async () => ({
      status: 200,
      ok: true,
      text: async () =>
        JSON.stringify({
          accessToken: makeToken(CLAIMS),
          accessTokenExpiry: FUTURE.toISOString(),
          refreshToken: 'refresh-2',
          refreshTokenExpiry: FUTURE.toISOString(),
        }),
    }));

    await useSession.getState().bootstrap();

    expect(useSession.getState().status).toBe('authenticated');
    expect(useSession.getState().tokens?.refreshToken).toBe('refresh-2');
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/auth/refresh');
  });

  it('déconnecte quand les deux jetons sont expirés, sans appel réseau', async () => {
    await useSession.getState().adopt({
      accessToken: makeToken(CLAIMS),
      accessTokenExpiry: PAST,
      refreshToken: 'refresh-1',
      refreshTokenExpiry: PAST,
    });
    useSession.setState({ status: 'unknown', tokens: null, user: null });

    await useSession.getState().bootstrap();

    expect(useSession.getState().status).toBe('anonymous');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('déconnecte quand le rafraîchissement échoue', async () => {
    await useSession.getState().adopt({
      accessToken: makeToken(CLAIMS),
      accessTokenExpiry: PAST,
      refreshToken: 'refresh-1',
      refreshTokenExpiry: FUTURE,
    });
    useSession.setState({ status: 'unknown', tokens: null, user: null });

    fetchMock.mockImplementationOnce(async () => ({
      status: 401,
      ok: false,
      text: async () => JSON.stringify({ status: 401, error: 'Unauthorized' }),
    }));

    await useSession.getState().bootstrap();

    expect(useSession.getState().status).toBe('anonymous');
    expect(useSession.getState().tokens).toBeNull();
  });
});

describe('stockage des secrets — NFR-40', () => {
  it('place les jetons dans le trousseau matériel, jamais ailleurs', async () => {
    await useSession.getState().adopt({
      accessToken: makeToken(CLAIMS),
      accessTokenExpiry: FUTURE,
      refreshToken: 'refresh-1',
      refreshTokenExpiry: FUTURE,
    });

    expect(SecureStore.setItemAsync).toHaveBeenCalledWith(
      'kora.accessToken',
      expect.any(String),
      expect.objectContaining({ keychainAccessible: 'whenUnlockedThisDeviceOnly' }),
    );
    expect(secureStore.get('kora.refreshToken')).toBe('refresh-1');
  });

  it('purge intégralement le trousseau à la déconnexion — contrat §6.5', async () => {
    await useSession.getState().adopt({
      accessToken: makeToken(CLAIMS),
      accessTokenExpiry: FUTURE,
      refreshToken: 'refresh-1',
      refreshTokenExpiry: FUTURE,
    });

    await useSession.getState().signOut();

    expect(secureStore.size).toBe(0);
    expect(useSession.getState().status).toBe('anonymous');
    expect(useSession.getState().profile.fullName).toBeNull();
    // Aucun appel réseau : le backend n'expose aucun endpoint de déconnexion.
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('profil — contrat §6.3', () => {
  it('reconstitue l’identité depuis les claims du jeton', async () => {
    await useSession.getState().adopt({
      accessToken: makeToken(CLAIMS),
      accessTokenExpiry: FUTURE,
      refreshToken: 'refresh-1',
      refreshTokenExpiry: FUTURE,
    });

    expect(useSession.getState().user).toEqual({
      id: 'user-1',
      email: 'aminata@kora.ci',
      role: 'CUSTOMER',
    });
  });

  it('mémorise localement ce que le serveur ne renvoie jamais', () => {
    useSession.getState().rememberProfile({
      fullName: 'Aminata Diallo',
      phone: '+2250708091011',
    });

    expect(useSession.getState().profile.fullName).toBe('Aminata Diallo');
    expect(useSession.getState().profile.phone).toBe('+2250708091011');
  });

  it('encaisse un jeton illisible sans lever', async () => {
    await useSession.getState().adopt({
      accessToken: 'pas-un-jwt',
      accessTokenExpiry: FUTURE,
      refreshToken: 'refresh-1',
      refreshTokenExpiry: FUTURE,
    });

    expect(useSession.getState().status).toBe('authenticated');
    expect(useSession.getState().user).toBeNull();
  });
});
