/**
 * Bascule d'environnement — `docs/10-validation-mode.md` §6.
 *
 * « Un changement d'environnement purge SecureStore, le cache de requêtes et le
 * stockage local, puis relance l'app. **Mélanger des jetons entre environnements
 * produit des symptômes incompréhensibles.** »
 *
 * L'ordre des opérations n'est pas arbitraire : la purge efface le stockage
 * local, donc la nouvelle URL doit être écrite **après**, sans quoi elle
 * partirait avec le reste.
 */

import { DevSettings } from 'react-native';

import { discardQueryCache } from '@/lib/queryPersistence';
import { env } from '@/lib/env';
import { getApiBaseUrl, setApiBaseUrl } from '@/lib/http';
import { KvKey, kvClear, kvGetString, kvSetString } from '@/lib/storage/kv';
import { secureClear } from '@/lib/storage/secure';

export interface EnvironmentPreset {
  id: string;
  label: string;
  hint: string;
  url: string;
}

/** Sonde de connectivité — le seul endpoint non authentifié toujours présent. */
export const HEALTH_PATH = '/actuator/health';

const HEALTH_TIMEOUT_MS = 3000;

export const ENVIRONMENT_PRESETS: EnvironmentPreset[] = [
  {
    id: 'local',
    label: 'Local',
    hint: 'Simulateur iOS, web',
    url: 'http://localhost:8081',
  },
  {
    id: 'emulator',
    label: 'Émulateur Android',
    hint: '10.0.2.2 est l’hôte vu depuis l’émulateur',
    url: 'http://10.0.2.2:8081',
  },
];

/**
 * Restaure la surcharge persistée. **À appeler avant la première requête.**
 *
 * Sans cela, l'application repartirait sur `EXPO_PUBLIC_API_URL` au prochain
 * lancement, et la bascule ne survivrait pas au redémarrage qu'elle provoque.
 */
export function restoreEnvironment(): void {
  try {
    setApiBaseUrl(kvGetString(KvKey.apiUrlOverride));
  } catch {
    // Stockage indisponible : `env.apiUrl` fait foi.
  }
}

export function currentApiUrl(): string {
  return getApiBaseUrl();
}

export function isOverridden(): boolean {
  return getApiBaseUrl() !== env.apiUrl;
}

export type ConnectivityResult =
  | { ok: true; status: number; durationMs: number }
  | { ok: false; reason: string; durationMs: number };

/**
 * Teste une URL **avant** de basculer dessus.
 *
 * `fetch` direct plutôt que le client HTTP : celui-ci reprend trois fois un
 * `GET`, ce qui transformerait un test de connectivité de 3 s en attente de 10.
 */
export async function testConnectivity(url: string): Promise<ConnectivityResult> {
  const startedAt = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HEALTH_TIMEOUT_MS);

  try {
    const response = await fetch(`${url.replace(/\/+$/, '')}${HEALTH_PATH}`, {
      signal: controller.signal,
    });
    const durationMs = Date.now() - startedAt;

    return response.ok
      ? { ok: true, status: response.status, durationMs }
      : { ok: false, reason: `HTTP ${response.status}`, durationMs };
  } catch (cause) {
    return {
      ok: false,
      reason: controller.signal.aborted ? 'Délai dépassé' : String(cause),
      durationMs: Date.now() - startedAt,
    };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Bascule et relance.
 *
 * `DevSettings.reload()` n'existe qu'en développement — c'est précisément le
 * seul contexte où ce module est monté. En staging, l'utilisateur relance
 * l'application à la main ; la surcharge est déjà persistée.
 */
export async function switchEnvironment(url: string | null): Promise<void> {
  await secureClear();
  discardQueryCache();
  kvClear();

  // Après la purge, jamais avant : `kvClear()` emporterait la surcharge.
  if (url) kvSetString(KvKey.apiUrlOverride, url);
  setApiBaseUrl(url);

  try {
    DevSettings.reload();
  } catch {
    // Pas de rechargement disponible : la surcharge prendra effet au prochain
    // lancement, et la purge a déjà eu lieu.
  }
}
