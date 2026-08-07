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
export const CREDENTIALS_MESSAGE = 'E-mail ou PIN incorrect.';

export function loginErrorMessage(error: unknown): string {
  if (!isKoraError(error)) return "Une erreur inattendue s'est produite.";

  switch (error.status) {
    case 401:
    case 404:
      return CREDENTIALS_MESSAGE;
    case 503:
      return 'Service temporairement indisponible. Réessayez dans un instant.';
    case 0:
      return 'Pas de connexion internet.';
    default:
      return error.message;
  }
}

export function otpErrorMessage(error: unknown): string {
  if (!isKoraError(error)) return "Une erreur inattendue s'est produite.";

  // Un OTP réutilisé, expiré ou faux produit le même `401` : le backend
  // supprime le code du magasin dès la première vérification réussie.
  if (error.status === 401) return 'Code incorrect ou expiré.';
  if (error.status === 0) return 'Pas de connexion internet.';
  return error.message;
}

export function registerErrorMessage(error: unknown): string {
  if (!isKoraError(error)) return "Une erreur inattendue s'est produite.";

  switch (error.status) {
    case 409:
      return 'Cet e-mail est déjà utilisé.';
    case 400:
      return 'Certaines informations sont incorrectes.';
    case 503:
      return "L'e-mail de vérification n'a pas pu être envoyé. Réessayez.";
    case 0:
      return 'Pas de connexion internet.';
    default:
      return error.message;
  }
}

/** Champ fautif d'un `400` de validation, pour marquer l'étape concernée. */
export function violationField(error: unknown): string | null {
  if (!isKoraError(error)) return null;
  return (error as KoraError).violations?.[0]?.field ?? null;
}
