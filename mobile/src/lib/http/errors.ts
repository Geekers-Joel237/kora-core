/**
 * Normalisation des erreurs — contrat §5.1 et §5.2, architecture §3.2.
 *
 * Chaque réponse est classée **comme le contrat Swagger la déclare** :
 * `api-contract/payments.openapi.json` dans `kora-core` décrit, opération par
 * opération, chaque couple statut + type de contenu possible. Une réponse que
 * le contrat ne déclare pas — un `500`, un `502` HTML de passerelle, un `504` —
 * n'est décrite par personne : sur une écriture monétaire, l'app ignore alors
 * si l'argent a bougé.
 *
 * Toute erreur déclarée est un `application/problem+json` portant un `code`
 * stable, que le catalogue `docs/problems.md` de `kora-core` décrit. C'est ce
 * `code`, et non le type de contenu ni le texte de `detail`, qui distingue
 * deux `401`.
 *
 * | Statut | Type de contenu              | Code               | Issue d'un paiement |
 * |--------|------------------------------|--------------------|---------------------|
 * | 400    | application/problem+json     | VALIDATION         | échec               |
 * | 401    | problem+json, UNAUTHENTICATED | TOKEN_EXPIRED     | rafraîchir          |
 * | 401    | problem+json, INVALID_PIN…   | INVALID_PIN / OTP  | échec               |
 * | 403    | application/problem+json     | FORBIDDEN          | échec               |
 * | 404    | application/problem+json     | NOT_FOUND          | échec               |
 * | 409    | application/problem+json     | CONFLICT           | échec               |
 * | 422    | application/problem+json     | BUSINESS_RULE      | échec               |
 * | 502    | application/problem+json     | PROVIDER_REFUSED   | échec : le provider a refusé, rien n'a bougé |
 * | 503    | application/problem+json     | UNAVAILABLE        | incertaine          |
 * | 5xx    | non déclaré                  | SERVER             | incertaine          |
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
  | 'PROVIDER_REFUSED' // 502 déclaré : ProblemDetail, le provider a refusé
  | 'SERVER' // 500, ou toute réponse 5xx que le contrat ne déclare pas
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
  /** `code` stable du ProblemDetail, tel que le catalogue du serveur le nomme. */
  problemCode?: string;
  /** Identifiant de corrélation de la requête, à citer au support. */
  correlationId?: string;
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

/** Type de contenu des erreurs métier dans le contrat Swagger. */
export const PROBLEM_JSON = 'application/problem+json';

/** `application/problem+json; charset=utf-8` → `application/problem+json`. */
function mediaTypeOf(contentType: string | undefined): string | undefined {
  const media = contentType?.split(';')[0]?.trim().toLowerCase();
  return media ? media : undefined;
}

function isProblem(contentType: string | undefined): boolean {
  return mediaTypeOf(contentType) === PROBLEM_JSON;
}

/**
 * ⚠️ LA FONCTION LA PLUS DÉLICATE DU SOCLE RÉSEAU — contrat §5.2.
 *
 * Un `401` a deux causes qui appellent des réactions **opposées** :
 *
 *   code UNAUTHENTICATED             → jeton absent ou expiré → rafraîchir, rejouer une fois
 *   code INVALID_PIN, INVALID_OTP, … → secret erroné         → NE PAS rafraîchir, afficher
 *
 * Les confondre produit soit une boucle de rafraîchissement infinie, soit une
 * déconnexion à chaque PIN erroné. Les deux arrivent en
 * `application/problem+json` : seul le `code` tranche. Un `401` au corps
 * vide ou illisible ne peut venir que d'une passerelle ou de la sécurité : il
 * est traité comme un jeton à rafraîchir.
 */
export function isTokenExpiry(status: number, body: unknown): boolean {
  if (status !== 401) return false;
  if (!isRecord(body)) return true;
  return body.code === 'UNAUTHENTICATED';
}

/** Distingue un `401` d'OTP d'un `401` de PIN par le `code` du serveur. */
function classifyUnauthorized(problemCode: string | undefined): KoraErrorCode {
  if (problemCode === 'INVALID_OTP' || problemCode === 'OTP_EXPIRED') return 'INVALID_OTP';
  return 'INVALID_PIN';
}

function codeForStatus(
  status: number,
  problemCode: string | undefined,
  tokenExpiry: boolean,
  contentType: string | undefined,
) {
  switch (status) {
    case 400:
      return 'VALIDATION' as const;
    case 401:
      return tokenExpiry ? ('TOKEN_EXPIRED' as const) : classifyUnauthorized(problemCode);
    case 403:
      return 'FORBIDDEN' as const;
    case 404:
      return 'NOT_FOUND' as const;
    case 409:
      return 'CONFLICT' as const;
    case 422:
      return 'BUSINESS_RULE' as const;
    case 502:
      return isProblem(contentType) ? ('PROVIDER_REFUSED' as const) : ('SERVER' as const);
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
 * Réduit n'importe quel corps d'erreur — les deux formats du contrat, un corps
 * vide, du HTML, du texte brut — à un `KoraError`.
 *
 * `contentType` est l'en-tête `Content-Type` de la réponse. Absent, la réponse
 * est traitée comme non déclarée : un `502` ne devient un refus affirmé que
 * s'il arrive en `application/problem+json`, comme le contrat le déclare.
 */
export function normalizeHttpError(
  status: number,
  body: unknown,
  context: NormalizeContext,
  contentType?: string,
): KoraError {
  const tokenExpiry = isTokenExpiry(status, body);
  const detail = isRecord(body) && typeof body.detail === 'string' ? body.detail : undefined;
  const problemCode = isRecord(body) && typeof body.code === 'string' ? body.code : undefined;
  const correlationId =
    isRecord(body) && typeof body.correlationId === 'string' ? body.correlationId : undefined;
  const code = codeForStatus(status, problemCode, tokenExpiry, contentType);
  const providerRefused = code === 'PROVIDER_REFUSED';

  const violations =
    isRecord(body) && Array.isArray(body.violations)
      ? (body.violations as ApiViolation[])
      : undefined;

  // Contrat §6.1 — aucune clé d'idempotence côté serveur tant que l'étape 4
  // n'a pas atterri. Une écriture monétaire n'est JAMAIS rejouable d'office.
  const isRetryable = !context.isMoneyMovement && status >= 500 && !providerRefused;

  // Un 502 déclaré est un refus du provider : l'opération a échoué et rien n'a
  // bougé. Un 503 — reprises épuisées après la réponse du provider — et toute
  // réponse 5xx non déclarée laissent l'issue incertaine. Contrat §2 et §6.1.
  const isOutcomeUnknown = context.isMoneyMovement && status >= 500 && !providerRefused;

  return {
    status,
    code,
    message: detail ?? defaultMessage(code),
    ...(detail !== undefined && { detail }),
    ...(problemCode !== undefined && { problemCode }),
    ...(correlationId !== undefined && { correlationId }),
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
