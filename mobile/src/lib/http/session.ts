/**
 * Pont entre la couche HTTP et la session, sans dépendance circulaire.
 *
 * Le client HTTP a besoin des jetons et doit pouvoir signaler une session
 * expirée. Importer directement le magasin de session créerait un cycle
 * `http → store → http`. Le magasin s'enregistre donc ici au démarrage.
 */

export interface TokenProvider {
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  /** Appelé après un rafraîchissement réussi. */
  onTokensRefreshed(tokens: {
    accessToken: string;
    accessTokenExpiry: string;
    refreshToken: string;
    refreshTokenExpiry: string;
  }): void;
  /** Appelé quand le rafraîchissement a définitivement échoué. */
  onSessionExpired(): void;
}

let provider: TokenProvider | null = null;

export function registerTokenProvider(next: TokenProvider | null): void {
  provider = next;
}

export function getTokenProvider(): TokenProvider | null {
  return provider;
}
