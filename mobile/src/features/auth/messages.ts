import { t } from '@/i18n';
import { isKoraError, type KoraError } from '@/lib/http';

/**
 * Traduction des erreurs d'authentification — `docs/05-screens.md` §2.
 */

/**
 * ⚠️ **Message unifié, délibérément.**
 *
 * Le backend distingue les deux cas : `404` pour un e-mail inconnu, `401` pour
 * un PIN erroné (contrat §1). Propager cette distinction offrirait un oracle
 * d'énumération de comptes — un attaquant saurait quels e-mails existent.
 *
 * L'application rend donc **exactement le même message** dans les deux cas.
 * Ne jamais « améliorer » ce comportement en le rendant plus précis.
 */
export function credentialsMessage(): string {
  return t('errors.credentials');
}

export function loginErrorMessage(error: unknown): string {
  if (!isKoraError(error)) return t('errors.UNKNOWN');

  switch (error.status) {
    case 401:
    case 404:
      return credentialsMessage();
    case 503:
      return t('errors.unavailableRetry');
    case 0:
      return t('errors.NETWORK');
    default:
      return error.message;
  }
}

export function otpErrorMessage(error: unknown): string {
  if (!isKoraError(error)) return t('errors.UNKNOWN');

  // Un OTP réutilisé, expiré ou faux produit le même `401` : le backend
  // supprime le code du magasin dès la première vérification réussie.
  if (error.status === 401) return t('errors.INVALID_OTP');
  if (error.status === 0) return t('errors.NETWORK');
  return error.message;
}

export function registerErrorMessage(error: unknown): string {
  if (!isKoraError(error)) return t('errors.UNKNOWN');

  switch (error.status) {
    case 409:
      return t('errors.emailTaken');
    case 400:
      return t('errors.VALIDATION');
    case 503:
      return t('errors.mailNotSent');
    case 0:
      return t('errors.NETWORK');
    default:
      return error.message;
  }
}

/** Champ fautif d'un `400` de validation, pour marquer l'étape concernée. */
export function violationField(error: unknown): string | null {
  if (!isKoraError(error)) return null;
  return (error as KoraError).violations?.[0]?.field ?? null;
}

/**
 * Un `409` désigne toujours l'e-mail — c'est la seule contrainte d'unicité du
 * backend. Le test sur le message rendu était fragile dès la traduction : il
 * dépendait de la présence du mot « e-mail » dans une phrase française.
 */
export function isEmailConflict(error: unknown): boolean {
  return (isKoraError(error) && error.status === 409) || violationField(error) === 'email';
}
