/**
 * Stockage sécurisé — Keychain iOS / Keystore Android.
 *
 * NFR-40 : les jetons résident **exclusivement** ici.
 * NFR-41 : le PIN n'y entre jamais. Il vit dans un `useRef` et rien d'autre.
 */

import * as SecureStore from 'expo-secure-store';

export const SecureKey = {
  accessToken: 'kora.accessToken',
  accessTokenExpiry: 'kora.accessTokenExpiry',
  refreshToken: 'kora.refreshToken',
  refreshTokenExpiry: 'kora.refreshTokenExpiry',
} as const;

export type SecureKeyName = (typeof SecureKey)[keyof typeof SecureKey];

/**
 * Une lecture qui échoue renvoie `null` : sur un appareil dont le trousseau est
 * indisponible, l'app doit dégrader vers « non authentifié », pas planter.
 */
export async function secureGet(key: SecureKeyName): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(key);
  } catch {
    return null;
  }
}

export async function secureSet(key: SecureKeyName, value: string): Promise<void> {
  await SecureStore.setItemAsync(key, value, {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

export async function secureDelete(key: SecureKeyName): Promise<void> {
  try {
    await SecureStore.deleteItemAsync(key);
  } catch {
    // Supprimer une clé absente n'est pas une erreur.
  }
}

/** Purge intégrale. Appelée à la déconnexion et au changement d'environnement. */
export async function secureClear(): Promise<void> {
  await Promise.all(Object.values(SecureKey).map((key) => secureDelete(key)));
}
