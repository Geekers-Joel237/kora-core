/**
 * API d'authentification — contrat §1.
 *
 * Frontière R1 : ce module renvoie exclusivement des types de domaine.
 * Aucun `Api*` ne le quitte.
 */

import { decode } from '@/lib/decode';
import { request } from '@/lib/http';
import { otpMessageSchema, tokensSchema } from '@/features/shared/schemas';
import { toTokens } from '@/features/shared/mappers';
import type { Tokens } from '@/types/domain';

export interface RegisterInput {
  fullName: string;
  email: string;
  phonePrefix: string;
  phoneNumber: string;
  rawPin: string;
}

/**
 * `201` — le client, l'utilisateur et le compte portefeuille sont créés, et
 * l'OTP est parti. **Aucun jeton n'est délivré ici.**
 *
 * Note vérifiée sur le code backend : le statut utilisateur vaut `VERIFIED`
 * d'emblée. L'OTP garde l'émission des jetons, pas l'activation du compte.
 */
export async function register(input: RegisterInput): Promise<void> {
  const payload = await request<unknown>('/auth/register', {
    method: 'POST',
    body: input,
    auth: false,
  });
  decode(otpMessageSchema, payload, '/auth/register');
}

/**
 * `200` ne signifie **pas** « connecté » : le PIN est validé, l'OTP est parti.
 * La session n'existe qu'après `verifyOtp`.
 *
 * Sert aussi de renvoi d'OTP : aucun endpoint dédié n'existe — contrat §6.
 */
export async function login(email: string, rawPin: string): Promise<void> {
  const payload = await request<unknown>('/auth/login', {
    method: 'POST',
    body: { email, rawPin },
    auth: false,
  });
  decode(otpMessageSchema, payload, '/auth/login');
}

/** L'OTP est à usage unique et expire au bout de 5 minutes. */
export async function verifyOtp(email: string, code: string): Promise<Tokens> {
  const payload = await request<unknown>('/auth/verify-otp', {
    method: 'POST',
    body: { email, code },
    auth: false,
  });
  return toTokens(decode(tokensSchema, payload, '/auth/verify-otp'));
}

/**
 * Rafraîchissement explicite, au démarrage de l'app.
 *
 * Le rafraîchissement **en cours de session** n'est pas déclenché ici : il est
 * pris en charge par le client HTTP, en vol groupé, de façon transparente.
 * Voir `src/lib/http/client.ts`.
 */
export async function refresh(refreshToken: string): Promise<Tokens> {
  const payload = await request<unknown>('/auth/refresh', {
    method: 'POST',
    body: { refreshToken },
    auth: false,
  });
  return toTokens(decode(tokensSchema, payload, '/auth/refresh'));
}
