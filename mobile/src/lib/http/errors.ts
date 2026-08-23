/**
 * Normalisation des erreurs — contrat §5.1 et §5.2, architecture §3.2.
 *
 * Le backend produit **trois formats d'erreur distincts**, plus le cas du corps
 * vide ou non-JSON. Tous convergent ici vers un `KoraError` unique.
 */

import { t } from '@/i18n';
import type { ApiViolation } from '@/types/api';

export type KoraErrorCode =
  | 'VALIDATION' // 400
  | 'TOKEN_EXPIRED' // 401 — jeton
  | 'INVALID_PIN' // 401 — PIN
  | 'INVALID_OTP' // 401 — OTP
  | 'FORBIDDEN' // 403
  | 'NOT_FOUND' // 404
  | 'CONFLICT' // 409
  | 'BUSINESS_RULE' // 422
  | 'SERVER' // 500
  | 'UNAVAILABLE' // 503
  | 'NETWORK' // pas de réponse
  | 'TIMEOUT' // délai dépassé
  | 'UNKNOWN';

export interface KoraError {
  /** `0` quand aucune réponse n'est parvenue. */
  status: number;
  code: KoraErrorCode;
  /** Message affichable. Traduit au niveau des écrans. */
  message: string;
  /** `detail` brut du serveur — pour le support et le mode validation. */
  detail?: string;
  violations?: ApiViolation[];
  /** Corps brut, tel que reçu. Alimente l'inspecteur réseau. */
  raw?: unknown;
  /** Une reprise automatique est-elle admissible ? Jamais vrai sur `/payments/*`. */
  isRetryable: boolean;
  /** Vrai **uniquement** pour un 401 de jeton. Seul déclencheur du rafraîchissement. */
  isAuthExpired: boolean;
  /** Vrai quand l'app ignore si l'argent a bougé. Seul déclencheur de l'écran « issue incertaine ». */
  isOutcomeUnknown: boolean;
}

export function isKoraError(value: unknown): value is KoraError {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    'isAuthExpired' in value &&
    'isOutcomeUnknown' in value
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/**
 * ⚠️ LA FONCTION LA PLUS DÉLICATE DU SOCLE RÉSEAU — contrat §5.2.
 *
 * Un `401` a deux causes qui appellent des réactions **opposées** :
 *
 *   { status, error }              → jeton expiré  → rafraîchir, rejouer une fois
 *   ProblemDetail avec `detail`    → PIN ou OTP    → NE PAS rafraîchir, afficher
 *
 * Les confondre produit soit une boucle de rafraîchissement infinie, soit une
 * déconnexion à chaque PIN erroné. La discrimination repose sur la forme du
 * corps : la couche de sécurité Spring émet `error` sans `detail`, le
 * `GlobalExceptionHandler` émet `detail` sans `error`.
 */
export function isTokenExpiry(status: number, body: unknown): boolean {
  if (status !== 401) return false;
  if (!isRecord(body)) {
    // 401 au corps vide ou illisible : seule la couche de sécurité en produit.
    return true;
  }
  return 'error' in body && !('detail' in body);
}

/** Distingue un 401 de PIN d'un 401 d'OTP à partir du `detail` du serveur. */
function classifyUnauthorized(detail: string | undefined): KoraErrorCode {
  const text = (detail ?? '').toLowerCase();
  if (text.includes('otp')) return 'INVALID_OTP';
  if (text.includes('pin')) return 'INVALID_PIN';
  return 'INVALID_PIN';
}

function codeForStatus(status: number, detail: string | undefined, tokenExpiry: boolean) {
  switch (status) {
    case 400:
      return 'VALIDATION' as const;
    case 401:
      return tokenExpiry ? ('TOKEN_EXPIRED' as const) : classifyUnauthorized(detail);
    case 403:
      return 'FORBIDDEN' as const;
    case 404:
      return 'NOT_FOUND' as const;
    case 409:
      return 'CONFLICT' as const;
    case 422:
      return 'BUSINESS_RULE' as const;
    case 503:
      return 'UNAVAILABLE' as const;
    default:
      return status >= 500 ? ('SERVER' as const) : ('UNKNOWN' as const);
  }
}

/**
 * Message par défaut d'un code d'erreur.
 *
 * Résolu **à l'appel**, jamais figé dans une table de module : la langue peut
 * changer entre le chargement du module et l'affichage de l'erreur.
 */
function defaultMessage(code: KoraErrorCode): string {
  return t(`errors.${code}`);
}

export interface NormalizeContext {
  /** Chemin appelé — détermine si l'issue peut être incertaine. */
  path: string;
  /** Une écriture monétaire ne peut jamais être rejouée automatiquement. */
  isMoneyMovement: boolean;
}

/**
 * Réduit n'importe quel corps d'erreur — les trois formats du contrat, un corps
 * vide, du HTML, du texte brut — à un `KoraError`.
 */
export function normalizeHttpError(
  status: number,
  body: unknown,
  context: NormalizeContext,
): KoraError {
  const tokenExpiry = isTokenExpiry(status, body);
  const detail = isRecord(body) && typeof body.detail === 'string' ? body.detail : undefined;
  const code = codeForStatus(status, detail, tokenExpiry);

  const violations =
    isRecord(body) && Array.isArray(body.violations)
      ? (body.violations as ApiViolation[])
      : undefined;

  // Contrat §6.1 — aucune clé d'idempotence côté serveur tant que l'étape 4
  // n'a pas atterri. Une écriture monétaire n'est JAMAIS rejouable d'office.
  const isRetryable = !context.isMoneyMovement && (status === 503 || status >= 500);

  // 503, 500 et absence de réponse sur une écriture monétaire : l'app ignore si
  // l'argent a bougé. Contrat §2 et §6.1.
  const isOutcomeUnknown = context.isMoneyMovement && (status === 503 || status >= 500);

  return {
    status,
    code,
    message: detail ?? defaultMessage(code),
    ...(detail !== undefined && { detail }),
    ...(violations !== undefined && { violations }),
    raw: body,
    isRetryable,
    isAuthExpired: tokenExpiry,
    isOutcomeUnknown,
  };
}

/** Échec réseau, abandon ou délai dépassé : aucune réponse n'est parvenue. */
export function normalizeTransportError(
  cause: unknown,
  context: NormalizeContext & { timedOut?: boolean },
): KoraError {
  const code: KoraErrorCode = context.timedOut ? 'TIMEOUT' : 'NETWORK';
  return {
    status: 0,
    code,
    message: defaultMessage(code),
    raw: cause,
    // Une lecture peut être reprise, une écriture monétaire jamais.
    isRetryable: !context.isMoneyMovement,
    isAuthExpired: false,
    // Le cas le plus délicat : la requête est peut-être arrivée au serveur.
    isOutcomeUnknown: context.isMoneyMovement,
  };
}
