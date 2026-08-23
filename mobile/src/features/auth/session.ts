import { create } from 'zustand';

import { decodeAccessToken, isTokenExpired } from '@/lib/jwt';
import { registerTokenProvider } from '@/lib/http';
import { reloadPreferences } from '@/lib/preferences';
import { discardQueryCache } from '@/lib/queryPersistence';
import { KvKey, kvClear, kvGetString, kvSetString } from '@/lib/storage/kv';
import { SecureKey, secureClear, secureGet, secureSet } from '@/lib/storage/secure';
import { parseInstantOrEpoch } from '@/lib/datetime';
import type { SessionUser, Tokens, UserRole } from '@/types/domain';
import { refresh as refreshApi } from './api';

export type SessionStatus = 'unknown' | 'anonymous' | 'authenticated';

interface SessionState {
  status: SessionStatus;
  tokens: Tokens | null;
  user: SessionUser | null;
  /** Reconstitué localement : aucun endpoint de profil n'existe. Contrat §6.3. */
  profile: { fullName: string | null; phone: string | null };

  /**
   * Rafraîchissement définitivement échoué — `docs/05-screens.md` §8.1.
   *
   * **Le statut reste `authenticated`.** Éjecter vers l'écran de connexion
   * détruirait la pile de navigation, et donc le parcours en cours : quelqu'un
   * au récapitulatif d'un transfert de 50 000 F doit y revenir. Un panneau
   * non bloquant remonte par-dessus l'écran, qui reste monté.
   */
  expired: boolean;
  /** Écran à retrouver après reconnexion. */
  resumePath: string | null;

  bootstrap: () => Promise<void>;
  adopt: (tokens: Tokens) => Promise<void>;
  rememberProfile: (profile: { fullName?: string; phone?: string; email?: string }) => void;
  markExpired: (resumePath: string | null) => Promise<void>;
  consumeResumePath: () => string | null;
  signOut: () => Promise<void>;
}

function toUser(accessToken: string): SessionUser | null {
  const claims = decodeAccessToken(accessToken);
  if (!claims) return null;
  return {
    id: claims.sub,
    email: claims.email ?? null,
    role: (claims.role as UserRole | undefined) ?? null,
  };
}

async function persist(tokens: Tokens): Promise<void> {
  await Promise.all([
    secureSet(SecureKey.accessToken, tokens.accessToken),
    secureSet(SecureKey.accessTokenExpiry, tokens.accessTokenExpiry.toISOString()),
    secureSet(SecureKey.refreshToken, tokens.refreshToken),
    secureSet(SecureKey.refreshTokenExpiry, tokens.refreshTokenExpiry.toISOString()),
  ]);
}

async function readPersisted(): Promise<Tokens | null> {
  const [accessToken, accessExpiry, refreshToken, refreshExpiry] = await Promise.all([
    secureGet(SecureKey.accessToken),
    secureGet(SecureKey.accessTokenExpiry),
    secureGet(SecureKey.refreshToken),
    secureGet(SecureKey.refreshTokenExpiry),
  ]);

  if (!accessToken || !refreshToken || !accessExpiry || !refreshExpiry) return null;

  return {
    accessToken,
    accessTokenExpiry: parseInstantOrEpoch(accessExpiry),
    refreshToken,
    refreshTokenExpiry: parseInstantOrEpoch(refreshExpiry),
  };
}

export const useSession = create<SessionState>((set, get) => ({
  status: 'unknown',
  tokens: null,
  user: null,
  expired: false,
  resumePath: null,
  profile: {
    fullName: kvGetString(KvKey.profileFullName),
    phone: kvGetString(KvKey.profilePhone),
  },

  /**
   * Portail de session — `docs/05-screens.md` §1.
   *
   * Le rafraîchissement au démarrage est **explicite** : le client HTTP ne le
   * déclenche que sur un `401` en vol, or ici aucune requête n'a encore été
   * émise. Sans cette étape, le premier appel de l'accueil échouerait puis
   * serait rejoué — l'utilisateur verrait un aller-retour inutile.
   */
  bootstrap: async () => {
    const stored = await readPersisted();

    if (!stored) {
      set({ status: 'anonymous', tokens: null, user: null });
      return;
    }

    if (!isTokenExpired(stored.accessTokenExpiry)) {
      set({ status: 'authenticated', tokens: stored, user: toUser(stored.accessToken) });
      return;
    }

    if (isTokenExpired(stored.refreshTokenExpiry)) {
      await get().signOut();
      return;
    }

    try {
      const renewed = await refreshApi(stored.refreshToken);
      await get().adopt(renewed);
    } catch {
      await get().signOut();
    }
  },

  adopt: async (tokens) => {
    await persist(tokens);
    set({
      status: 'authenticated',
      tokens,
      user: toUser(tokens.accessToken),
      expired: false,
    });
  },

  /**
   * Les jetons morts sont effacés, mais la session **n'est pas fermée** : le
   * profil, la pile de navigation et le parcours en cours survivent.
   */
  markExpired: async (resumePath) => {
    if (get().expired) return;
    await secureClear();
    set({ expired: true, resumePath, tokens: null });
  },

  consumeResumePath: () => {
    const path = get().resumePath;
    if (path !== null) set({ resumePath: null });
    return path;
  },

  rememberProfile: ({ fullName, phone, email }) => {
    if (fullName) kvSetString(KvKey.profileFullName, fullName);
    if (phone) kvSetString(KvKey.profilePhone, phone);
    if (email) kvSetString(KvKey.lastEmail, email);
    set((state) => ({
      profile: {
        fullName: fullName ?? state.profile.fullName,
        phone: phone ?? state.profile.phone,
      },
    }));
  },

  /**
   * Déconnexion purement locale — contrat §6.5.
   *
   * Aucun endpoint n'invalide les jetons côté serveur : un jeton de
   * rafraîchissement volé reste valide jusqu'à son expiration naturelle.
   * Limite connue du backend, à ne pas contourner côté client.
   */
  signOut: async () => {
    await secureClear();
    kvClear();
    // Les préférences vivaient dans le stockage qui vient d'être purgé : les
    // laisser en mémoire ferait réapparaître les choix du compte précédent.
    reloadPreferences();
    // Contrat §6.5 — la déconnexion purge SecureStore, MMKV **et** le cache de
    // requêtes. Un solde persisté survivant à la déconnexion serait une fuite.
    discardQueryCache();
    set({
      status: 'anonymous',
      tokens: null,
      user: null,
      expired: false,
      resumePath: null,
      profile: { fullName: null, phone: null },
    });
  },
}));

/**
 * Branche le client HTTP sur le magasin — `src/lib/http/session.ts`.
 *
 * Cette indirection existe pour éviter le cycle `http → store → http`. Elle est
 * installée une seule fois, au chargement du module.
 */
registerTokenProvider({
  getAccessToken: () => useSession.getState().tokens?.accessToken ?? null,
  getRefreshToken: () => useSession.getState().tokens?.refreshToken ?? null,
  onTokensRefreshed: (raw) => {
    void useSession.getState().adopt({
      accessToken: raw.accessToken,
      accessTokenExpiry: parseInstantOrEpoch(raw.accessTokenExpiry),
      refreshToken: raw.refreshToken,
      refreshTokenExpiry: parseInstantOrEpoch(raw.refreshTokenExpiry),
    });
  },
  /**
   * ⚠️ **Pas de `signOut()` ici.** Le §8.1 de `docs/05-screens.md` interdit
   * explicitement d'éjecter l'utilisateur en perdant son parcours. Le chemin de
   * reprise est renseigné par `SessionExpiredSheet`, qui seul connaît la route
   * courante.
   */
  onSessionExpired: () => {
    void useSession.getState().markExpired(null);
  },
});
