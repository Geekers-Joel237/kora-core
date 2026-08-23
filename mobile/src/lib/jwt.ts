/**
 * Décodage local des claims du jeton d'accès — contrat §6.3.
 *
 * Aucun endpoint de profil n'existe : `sub`, `email` et `role` sont la seule
 * source de données utilisateur côté serveur.
 *
 * ⚠️ Décodage, pas vérification. La signature est vérifiée par le backend.
 * Ne jamais accorder de confiance à ces claims pour une décision de sécurité.
 */

import type { ApiAccessTokenClaims } from '@/types/api';

function base64UrlDecode(segment: string): string | null {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');
  // Hermes fournit `atob`. Aucun repli sur `Buffer` : ce serait une dépendance
  // Node, absente du runtime React Native.
  if (typeof globalThis.atob !== 'function') return null;
  try {
    return globalThis.atob(padded);
  } catch {
    return null;
  }
}

/** Règle R2 — un jeton illisible renvoie `null`, il ne lève jamais. */
export function decodeAccessToken(token: string): ApiAccessTokenClaims | null {
  const segments = token.split('.');
  const payload = segments[1];
  if (segments.length !== 3 || !payload) return null;

  const json = base64UrlDecode(payload);
  if (!json) return null;

  try {
    const claims = JSON.parse(json) as unknown;
    if (typeof claims !== 'object' || claims === null) return null;
    if (!('sub' in claims) || typeof claims.sub !== 'string') return null;
    return claims as ApiAccessTokenClaims;
  } catch {
    return null;
  }
}

/**
 * @param skewSeconds Marge de sécurité : un jeton qui expire dans moins de
 *   30 secondes est traité comme expiré, pour éviter qu'il ne périme en vol.
 */
export function isTokenExpired(expiry: Date | null, skewSeconds = 30): boolean {
  if (!expiry) return true;
  return expiry.getTime() - skewSeconds * 1000 <= Date.now();
}
