import { QueryClient } from '@tanstack/react-query';

import { createQueryClient, qk } from '@/lib/queryClient';
import {
  discardQueryCache,
  persistQueryCache,
  restoreQueryCache,
} from '@/lib/queryPersistence';
import { KvKey, kvGetJson, kvSetJson } from '@/lib/storage/kv';

const ACCOUNT = { id: 'acc-1', number: 'ACC-1', balance: { minor: 125000, currency: 'XOF' } };

const WRITE_DEBOUNCE_MS = 1000;

function seed(client: QueryClient): void {
  client.setQueryData(qk.balance, ACCOUNT);
}

/** Force l'écriture groupée sans attendre réellement une seconde. */
function flushWrites(): void {
  jest.advanceTimersByTime(WRITE_DEBOUNCE_MS + 1);
}

beforeEach(() => {
  jest.useFakeTimers();
  discardQueryCache();
});

afterEach(() => {
  jest.useRealTimers();
  discardQueryCache();
});

describe('persistance du cache — docs/08-quality-bar.md §4', () => {
  it('restitue le solde après un démarrage à froid', () => {
    const first = createQueryClient();
    const stop = persistQueryCache(first);
    seed(first);
    flushWrites();
    stop();

    // Nouveau client : exactement ce qui se passe au lancement suivant.
    const second = createQueryClient();
    expect(second.getQueryData(qk.balance)).toBeUndefined();

    expect(restoreQueryCache(second)).toBe(true);
    expect(second.getQueryData(qk.balance)).toEqual(ACCOUNT);
  });

  it('conserve l’instant de mise à jour — c’est lui qui affiche l’ancienneté', () => {
    const first = createQueryClient();
    const stop = persistQueryCache(first);
    seed(first);
    const updatedAt = first.getQueryState(qk.balance)?.dataUpdatedAt;
    flushWrites();
    stop();

    const second = createQueryClient();
    restoreQueryCache(second);

    // Sans cet instant, « Mis à jour il y a X » de l'accueil serait faux.
    expect(second.getQueryState(qk.balance)?.dataUpdatedAt).toBe(updatedAt);
  });

  it('groupe les écritures plutôt que d’en produire une par notification', () => {
    const client = createQueryClient();
    const stop = persistQueryCache(client);

    for (let index = 0; index < 20; index += 1) {
      client.setQueryData(qk.history({ page: index }), { items: [] });
    }
    expect(kvGetJson(KvKey.queryCache, null)).toBeNull();

    flushWrites();
    expect(kvGetJson(KvKey.queryCache, null)).not.toBeNull();
    stop();
  });

  it('ne persiste que le solde et l’historique', () => {
    const client = createQueryClient();
    const stop = persistQueryCache(client);

    seed(client);
    client.setQueryData(['devtools', 'raw-history'], { secret: true });
    client.setQueryData(['session'], { accessToken: 'ne-doit-jamais-être-écrit' });
    flushWrites();
    stop();

    const raw = JSON.stringify(kvGetJson(KvKey.queryCache, null));
    expect(raw).toContain('balance');
    expect(raw).not.toContain('devtools');
    expect(raw).not.toContain('ne-doit-jamais-être-écrit');
  });

  it('jette un cache de plus de 24 h plutôt que de l’afficher', () => {
    const first = createQueryClient();
    const stop = persistQueryCache(first);
    seed(first);
    flushWrites();
    stop();

    const second = createQueryClient();
    const tomorrow = Date.now() + 24 * 60 * 60 * 1000 + 1;

    // Un solde de la veille présenté comme courant est pire que pas de solde.
    expect(restoreQueryCache(second, tomorrow)).toBe(false);
    expect(second.getQueryData(qk.balance)).toBeUndefined();
    expect(kvGetJson(KvKey.queryCache, null)).toBeNull();
  });

  it('jette un cache d’une version antérieure', () => {
    kvSetJson(KvKey.queryCache, { version: 0, savedAt: Date.now(), state: { queries: [] } });

    const client = createQueryClient();
    expect(restoreQueryCache(client)).toBe(false);
  });

  it('ne plante pas sur une charge utile corrompue', () => {
    kvSetJson(KvKey.queryCache, { version: 1, savedAt: Date.now(), state: 'pas un état' });

    const client = createQueryClient();
    expect(restoreQueryCache(client)).toBe(false);
  });

  it('n’a rien à restituer après une purge', () => {
    const first = createQueryClient();
    const stop = persistQueryCache(first);
    seed(first);
    flushWrites();
    stop();

    discardQueryCache();

    expect(restoreQueryCache(createQueryClient())).toBe(false);
  });
});
