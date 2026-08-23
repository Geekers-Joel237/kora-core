/**
 * Persistance du cache de requêtes — `docs/08-quality-bar.md` §4 et §7.
 *
 * « Le cache est réhydraté **avant tout appel réseau** au démarrage » et « les
 * données de cache restent consultables hors ligne, avec l'ancienneté
 * indiquée ». Un cache en mémoire seule ne tient aucune de ces deux promesses :
 * après un démarrage à froid en mode avion, l'accueil est vide.
 *
 * Écrit à la main plutôt qu'avec `@tanstack/react-query-persist-client` : MMKV
 * est **synchrone**, ce qui permet de réhydrater avant le premier rendu, sans
 * la phase asynchrone que le paquet officiel impose. C'est précisément ce que
 * « avant tout appel réseau » exige.
 *
 * Deux garde-fous non négociables :
 *
 * 1. **Rien de sensible n'est persisté.** Seuls le solde et l'historique le
 *    sont — deux domaines déjà masqués par le serveur. Les jetons vivent dans
 *    SecureStore, le PIN nulle part.
 * 2. **Un cache trop vieux est jeté, pas affiché.** Un solde de la semaine
 *    dernière présenté comme courant est pire que pas de solde du tout.
 */

import { dehydrate, hydrate, type QueryClient } from '@tanstack/react-query';

import { KvKey, kvDelete, kvGetJson, kvSetJson } from '@/lib/storage/kv';

/**
 * Incrémenter à chaque changement de forme des données mises en cache. Une
 * ancienne charge utile n'est alors pas réhydratée dans un code qui ne la
 * comprend plus.
 */
const CACHE_VERSION = 1;

/** Au-delà, le cache est jeté : mieux vaut un écran de chargement qu'un chiffre faux. */
const MAX_AGE_MS = 24 * 60 * 60 * 1000;

/** Écriture groupée : un cache réécrit à chaque notification de requête coûterait plus qu'il ne rapporte. */
const WRITE_DEBOUNCE_MS = 1000;

/** Domaines persistés. Tout le reste est volontairement volatil. */
const PERSISTED_KEYS = ['balance', 'history'];

interface PersistedCache {
  version: number;
  savedAt: number;
  state: ReturnType<typeof dehydrate>;
}

/**
 * `hydrate` tolère à peu près n'importe quoi : un état corrompu n'y lève pas,
 * il ne restaure simplement rien — et l'application croirait alors avoir un
 * cache. La forme est donc vérifiée avant, pas après.
 */
function isDehydratedState(value: unknown): value is PersistedCache['state'] {
  return (
    typeof value === 'object' &&
    value !== null &&
    Array.isArray((value as { queries?: unknown }).queries)
  );
}

function isPersistable(queryKey: readonly unknown[]): boolean {
  const root = queryKey[0];
  return typeof root === 'string' && PERSISTED_KEYS.includes(root);
}

/**
 * Réhydrate le client. **Synchrone et à appeler avant le premier rendu.**
 *
 * Renvoie `true` si un cache a été restauré — utile aux tests et au mode
 * validation, pas à l'interface.
 */
export function restoreQueryCache(client: QueryClient, now: number = Date.now()): boolean {
  let stored: PersistedCache | null = null;

  try {
    stored = kvGetJson<PersistedCache | null>(KvKey.queryCache, null);
  } catch {
    return false;
  }

  if (!stored || stored.version !== CACHE_VERSION) {
    discardQueryCache();
    return false;
  }

  if (now - stored.savedAt > MAX_AGE_MS) {
    discardQueryCache();
    return false;
  }

  if (!isDehydratedState(stored.state)) {
    discardQueryCache();
    return false;
  }

  try {
    hydrate(client, stored.state);
    return true;
  } catch {
    // Une charge utile corrompue ne doit pas empêcher l'application de démarrer.
    discardQueryCache();
    return false;
  }
}

/**
 * Branche l'écriture du cache. Renvoie la fonction de désabonnement.
 *
 * L'écriture est **groupée** : une navigation dans l'historique produit des
 * dizaines de notifications, et chacune déclencherait une sérialisation
 * complète du cache.
 */
export function persistQueryCache(client: QueryClient): () => void {
  let timer: ReturnType<typeof setTimeout> | null = null;

  const write = () => {
    timer = null;
    try {
      const state = dehydrate(client, {
        shouldDehydrateQuery: (query) =>
          query.state.status === 'success' && isPersistable(query.queryKey),
      });
      const payload: PersistedCache = { version: CACHE_VERSION, savedAt: Date.now(), state };
      kvSetJson(KvKey.queryCache, payload);
    } catch {
      // Un cache non persisté dégrade le démarrage hors ligne ; il ne casse rien.
    }
  };

  const unsubscribe = client.getQueryCache().subscribe(() => {
    if (timer !== null) return;
    timer = setTimeout(write, WRITE_DEBOUNCE_MS);
  });

  return () => {
    if (timer !== null) clearTimeout(timer);
    unsubscribe();
  };
}

/** Purge explicite — déconnexion, changement d'environnement. */
export function discardQueryCache(): void {
  try {
    kvDelete(KvKey.queryCache);
  } catch {
    // Rien à faire : le stockage est déjà inaccessible.
  }
}
