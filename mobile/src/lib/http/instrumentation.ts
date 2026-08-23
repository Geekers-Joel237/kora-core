/**
 * Points d'ancrage du mode validation dans la couche HTTP —
 * `docs/10-validation-mode.md` §3 et §7.
 *
 * **La flèche de dépendance ne s'inverse jamais.** `lib/http` ne connaît pas
 * `src/devtools` : il expose deux emplacements vides, et les devtools s'y
 * branchent — exactement le motif de `registerTokenProvider`. Sans quoi le
 * bundle de production embarquerait le panneau entier.
 *
 * ⚠️ **Le masquage vit ici, dans le code de production, et nulle part
 * ailleurs.** Le §3 exige que `rawPin` soit masqué « sans exception, même en
 * développement ». Le confier aux devtools reviendrait à parier qu'on n'oubliera
 * jamais : un journal les enregistrerait en clair au premier écart.
 */

export type ObservedMethod = 'GET' | 'POST';

export interface ObservedRequest {
  id: string;
  method: ObservedMethod;
  path: string;
  query: Record<string, string | number | boolean | undefined> | undefined;
  correlationId: string;
  /** En-têtes **déjà masqués** : le Bearer n'y figure jamais en clair. */
  headers: Record<string, string>;
  /** Corps **déjà masqué** : `rawPin` y vaut toujours `****`. */
  body: unknown;
  startedAt: number;
}

export interface ObservedResponse {
  status: number;
  body: unknown;
  durationMs: number;
  /** Erreur de transport : coupure réseau, expiration, abandon. */
  transportError?: string;
}

/** Signaux visuels du §3 — `refresh` en jaune, `replay` en bleu. */
export type ObservedEvent = 'refresh' | 'replay';

export interface HttpObserver {
  onRequest: (request: ObservedRequest) => void;
  onResponse: (id: string, response: ObservedResponse) => void;
  /**
   * Signal portant sur la **requête logique en cours**, pas sur une tentative :
   * l'identifiant de corrélation change à chaque rejeu, et c'est justement le
   * lien entre les deux tentatives que le signal doit rendre visible.
   */
  onEvent: (event: ObservedEvent) => void;
}

/**
 * Injection de défaillance — §7.
 *
 * Chaque méthode renvoie « rien à faire » par défaut. Le simulateur n'est
 * installé qu'en mode validation, et une seule fois.
 */
export interface HttpSimulator {
  /** Latence ajoutée avant l'envoi, en millisecondes. */
  latencyMs: () => number;
  /** Statut imposé au prochain appel du chemin. `null` laisse passer. */
  forcedResponse: (path: string, method: ObservedMethod) => { status: number; body: unknown } | null;
  /**
   * Coupe la connexion après N ms — le client disparaît en cours de requête.
   * **Le scénario le plus important à valider de toute l'application** (§7).
   */
  abortAfterMs: (path: string, method: ObservedMethod) => number | null;
}

let observer: HttpObserver | null = null;
let simulator: HttpSimulator | null = null;

export function registerHttpObserver(next: HttpObserver | null): void {
  observer = next;
}

export function getHttpObserver(): HttpObserver | null {
  return observer;
}

export function registerHttpSimulator(next: HttpSimulator | null): void {
  simulator = next;
}

export function getHttpSimulator(): HttpSimulator | null {
  return simulator;
}

// ─────────────────────────────────────────────────────────── Masquage ───────

export const MASK = '****';

/** Champs dont la valeur ne doit jamais être enregistrée, à aucun niveau. */
const SECRET_FIELDS = ['rawPin', 'pin', 'password', 'accessToken', 'refreshToken'];

/** En-têtes porteurs d'un secret. La comparaison est insensible à la casse. */
const SECRET_HEADERS = ['authorization', 'cookie', 'set-cookie'];

const MAX_MASK_DEPTH = 6;

/**
 * Masque récursivement les champs sensibles d'un corps de requête.
 *
 * Le corps n'est **pas** copié en profondeur pour l'application : la valeur
 * renvoyée est une structure neuve destinée au seul journal. L'original part
 * au serveur intact.
 */
export function maskSecrets(value: unknown, depth = 0): unknown {
  if (depth > MAX_MASK_DEPTH) return value;

  if (Array.isArray(value)) return value.map((item) => maskSecrets(item, depth + 1));

  if (typeof value !== 'object' || value === null) return value;

  const masked: Record<string, unknown> = {};
  for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
    masked[key] = SECRET_FIELDS.includes(key) ? MASK : maskSecrets(child, depth + 1);
  }
  return masked;
}

export function maskHeaders(headers: Record<string, string>): Record<string, string> {
  const masked: Record<string, string> = {};
  for (const [key, value] of Object.entries(headers)) {
    if (!SECRET_HEADERS.includes(key.toLowerCase())) {
      masked[key] = value;
      continue;
    }
    // Le schéma reste lisible — c'est lui qui informe —, la valeur jamais.
    const scheme = value.split(' ')[0];
    masked[key] = scheme && scheme !== value ? `${scheme} ${MASK}` : MASK;
  }
  return masked;
}
