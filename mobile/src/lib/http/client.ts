/**
 * Client HTTP — architecture §3.
 *
 * `fetch` natif enveloppé. Ni `ky`, ni `axios` : les intercepteurs nécessaires
 * ici sont trop spécifiques (double `401`, vol groupé, interdiction de reprise
 * sur les paiements) pour qu'une bibliothèque généraliste apporte quoi que ce soit.
 */

import * as Crypto from 'expo-crypto';

import { env } from '@/lib/env';
import { isKoraError, normalizeHttpError, normalizeTransportError } from './errors';
import {
  getHttpObserver,
  getHttpSimulator,
  maskHeaders,
  maskSecrets,
  type ObservedEvent,
} from './instrumentation';
import { getTokenProvider } from './session';
import type { ApiTokensResponse } from '@/types/api';

export type HttpMethod = 'GET' | 'POST';

export interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined>;
  /** Injecte le Bearer. Défaut : `true`. */
  auth?: boolean;
  /**
   * Clé d'idempotence. Générée à l'entrée du récapitulatif de paiement, pas au
   * moment de l'envoi, afin de survivre à un rejeu manuel.
   * CONTOURNEMENT(étape-4) : le serveur l'ignore aujourd'hui, l'honorera demain.
   */
  idempotencyKey?: string;
  timeoutMs?: number;
  signal?: AbortSignal;
}

const DEFAULT_TIMEOUT_MS = 20_000;
const GET_MAX_ATTEMPTS = 3;

/** Une écriture sur ces chemins déplace de l'argent. Aucune reprise, jamais. */
function isMoneyMovement(path: string, method: HttpMethod): boolean {
  return method === 'POST' && path.startsWith('/payments/');
}

export function newCorrelationId(): string {
  return Crypto.randomUUID();
}

/** Alias explicite : une clé d'idempotence est un UUID v4, comme la corrélation. */
export const newIdempotencyKey = newCorrelationId;

/**
 * URL de base effective — `docs/10-validation-mode.md` §6.
 *
 * Une surcharge persistée permet au mode validation de pointer vers un autre
 * environnement sans recompiler. Elle est lue **à chaque requête** : changer
 * d'environnement ne doit pas exiger de relancer le processus JavaScript pour
 * prendre effet. En l'absence de surcharge, `env.apiUrl` fait foi.
 */
let apiBaseOverride: string | null = null;

export function setApiBaseUrl(url: string | null): void {
  apiBaseOverride = url && url.trim() !== '' ? url.trim().replace(/\/+$/, '') : null;
}

export function getApiBaseUrl(): string {
  return apiBaseOverride ?? env.apiUrl;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = `${getApiBaseUrl()}${path}`;
  if (!query) return url;

  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) params.append(key, String(value));
  }
  const serialized = params.toString();
  return serialized ? `${url}?${serialized}` : url;
}

/** Le corps peut être vide, du JSON, ou n'importe quoi d'autre — contrat §5.1. */
async function readBody(response: Response): Promise<unknown> {
  const text = await response.text().catch(() => '');
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

// ── Rafraîchissement en vol groupé — architecture §3.4 ──

let refreshInFlight: Promise<ApiTokensResponse> | null = null;

async function performRefresh(refreshToken: string): Promise<ApiTokensResponse> {
  const response = await fetch(buildUrl('/auth/refresh'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });

  const body = await readBody(response);

  if (!response.ok) {
    throw normalizeHttpError(response.status, body, {
      path: '/auth/refresh',
      isMoneyMovement: false,
    });
  }

  // Un 200 au corps illisible est traité comme un échec de rafraîchissement :
  // continuer avec des jetons inexistants produirait une boucle de 401.
  if (typeof body !== 'object' || body === null || !('accessToken' in body)) {
    throw normalizeHttpError(401, { error: 'Unauthorized' }, {
      path: '/auth/refresh',
      isMoneyMovement: false,
    });
  }

  return body as ApiTokensResponse;
}

/**
 * Trois requêtes recevant `401` simultanément ne déclenchent **qu'un seul**
 * appel à `/auth/refresh`. Sans ce groupage, une session expirée provoque une
 * rafale de rafraîchissements concurrents dont un seul jeu de jetons survit.
 */
async function refreshTokens(): Promise<ApiTokensResponse> {
  const provider = getTokenProvider();
  const refreshToken = provider?.getRefreshToken();

  if (!provider || !refreshToken) {
    throw normalizeHttpError(401, { error: 'Unauthorized' }, {
      path: '/auth/refresh',
      isMoneyMovement: false,
    });
  }

  refreshInFlight ??= performRefresh(refreshToken)
    .then((tokens) => {
      provider.onTokensRefreshed(tokens);
      return tokens;
    })
    .catch((cause: unknown) => {
      provider.onSessionExpired();
      throw cause;
    })
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
}

/** Exposé pour les tests : remet le vol groupé à zéro entre deux scénarios. */
export function __resetRefreshState(): void {
  refreshInFlight = null;
}

// ── Exécution ──

interface Attempt {
  status: number;
  body: unknown;
  ok: boolean;
}

async function executeOnce(
  url: string,
  path: string,
  options: RequestOptions,
): Promise<Attempt> {
  const method = options.method ?? 'GET';
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? DEFAULT_TIMEOUT_MS);

  const correlationId = newCorrelationId();
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Correlation-Id': correlationId,
  };

  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey;

  if (options.auth !== false) {
    const accessToken = getTokenProvider()?.getAccessToken();
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  }

  // ── Mode validation : journal et injection de défaillance ──
  const observer = getHttpObserver();
  const simulator = getHttpSimulator();
  const startedAt = Date.now();

  if (observer) {
    observer.onRequest({
      id: correlationId,
      method,
      path,
      query: options.query,
      correlationId,
      // Masqués **avant** de sortir d'ici : le journal ne voit jamais de secret.
      headers: maskHeaders(headers),
      body: options.body === undefined ? undefined : maskSecrets(options.body),
      startedAt,
    });
  }

  const settle = (attempt: Attempt): Attempt => {
    observer?.onResponse(correlationId, {
      status: attempt.status,
      body: attempt.body,
      durationMs: Date.now() - startedAt,
    });
    return attempt;
  };

  let cutTimer: ReturnType<typeof setTimeout> | null = null;

  try {
    if (simulator) {
      const latency = simulator.latencyMs();
      if (latency > 0) await wait(latency);

      const forced = simulator.forcedResponse(path, method);
      if (forced) {
        return settle({ status: forced.status, body: forced.body, ok: false });
      }

      const cutAfter = simulator.abortAfterMs(path, method);
      // La coupure passe par le même `AbortController` que l'expiration : le
      // client voit exactement ce qu'il verrait sur un réseau qui tombe.
      if (cutAfter !== null) cutTimer = setTimeout(() => controller.abort(), cutAfter);
    }

    const response = await fetch(url, {
      method,
      headers,
      ...(options.body !== undefined && { body: JSON.stringify(options.body) }),
      signal: options.signal ?? controller.signal,
    });
    return settle({ status: response.status, body: await readBody(response), ok: response.ok });
  } catch (cause) {
    const timedOut = controller.signal.aborted;
    observer?.onResponse(correlationId, {
      status: 0,
      body: null,
      durationMs: Date.now() - startedAt,
      transportError: cause instanceof Error ? cause.message : String(cause),
    });
    throw normalizeTransportError(cause, {
      path,
      isMoneyMovement: isMoneyMovement(path, method),
      ...(timedOut && { timedOut: true }),
    });
  } finally {
    clearTimeout(timeout);
    if (cutTimer !== null) clearTimeout(cutTimer);
  }
}

function notify(event: ObservedEvent): void {
  getHttpObserver()?.onEvent(event);
}

function backoffDelay(attempt: number): number {
  const base = 300 * 2 ** (attempt - 1);
  return base + Math.random() * base * 0.5;
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Politique de reprise — architecture §3.5.
 *
 *   GET             → 3 tentatives, recul exponentiel avec gigue
 *   POST /auth/*    → aucune
 *   POST /payments/*→ AUCUNE, JAMAIS, SOUS AUCUNE CONDITION
 *
 * CONTOURNEMENT(étape-4) : aucune clé d'idempotence n'est honorée côté serveur.
 * Un rejeu automatique peut débiter deux fois. Lever cette interdiction
 * uniquement quand `API_CAPABILITIES.idempotency` passe à `true`.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const url = buildUrl(path, options.query);
  const context = { path, isMoneyMovement: isMoneyMovement(path, method) };
  const maxAttempts = method === 'GET' ? GET_MAX_ATTEMPTS : 1;

  let refreshAttempted = false;

  for (let attempt = 1; ; attempt += 1) {
    let result: Attempt;

    try {
      result = await executeOnce(url, path, options);
    } catch (cause) {
      const error = isKoraError(cause)
        ? cause
        : normalizeTransportError(cause, context);
      if (error.isRetryable && attempt < maxAttempts) {
        await wait(backoffDelay(attempt));
        continue;
      }
      throw error;
    }

    if (result.ok) {
      return result.body as T;
    }

    const error = normalizeHttpError(result.status, result.body, context);

    // Un 401 de jeton, et lui seul, déclenche le rafraîchissement. Un 401 de PIN
    // porte un `detail` et ne doit surtout pas passer par ici — contrat §5.2.
    if (error.isAuthExpired && !refreshAttempted && options.auth !== false) {
      refreshAttempted = true;
      try {
        notify('refresh');
        await refreshTokens();
        notify('replay');
        continue; // rejeu unique, transparent pour l'utilisateur
      } catch {
        throw error;
      }
    }

    if (error.isRetryable && attempt < maxAttempts) {
      await wait(backoffDelay(attempt));
      continue;
    }

    throw error;
  }
}
