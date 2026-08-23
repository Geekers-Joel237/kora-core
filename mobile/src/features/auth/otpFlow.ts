/**
 * Contexte volatil du parcours OTP.
 *
 * ⚠️ **Ce module détient temporairement un PIN en clair.** C'est le seul de
 * toute l'application, et c'est une conséquence directe d'un manque du contrat :
 * aucun endpoint de renvoi d'OTP n'existe (contrat §6). Le renvoi doit donc
 * rappeler `/auth/login`, qui exige à nouveau le PIN.
 *
 * Garanties, NFR-41 :
 *  - Portée module, jamais un état React, jamais MMKV, jamais SecureStore.
 *  - Effacé dès la sortie du parcours, quelle qu'en soit l'issue.
 *  - N'apparaît dans aucun journal ni aucune trace.
 *
 * `CONTOURNEMENT(indéterminé)` — disparaît le jour où un endpoint de renvoi
 * d'OTP est ajouté au backend.
 */

export type OtpOrigin = 'login' | 'register';

interface OtpContext {
  origin: OtpOrigin;
  email: string;
  /** Volatile. Uniquement pour le renvoi. */
  rawPin: string;
}

let context: OtpContext | null = null;

export function beginOtpFlow(next: OtpContext): void {
  context = next;
}

export function otpEmail(): string | null {
  return context?.email ?? null;
}

export function otpOrigin(): OtpOrigin | null {
  return context?.origin ?? null;
}

/** Réservé au renvoi. Renvoie `null` hors d'un parcours actif. */
export function otpCredentials(): { email: string; rawPin: string } | null {
  if (!context) return null;
  return { email: context.email, rawPin: context.rawPin };
}

/** À appeler à toute sortie du parcours — succès, abandon, ou démontage. */
export function endOtpFlow(): void {
  context = null;
}

/** Masque un e-mail pour l'affichage : `aminata@kora.ci` → `a•••@kora.ci`. */
export function maskEmail(email: string): string {
  const [local, domain] = email.split('@');
  if (!local || !domain) return email;
  return `${local.slice(0, 1)}•••@${domain}`;
}
