/**
 * Stockage local non sensible — MMKV, synchrone.
 *
 * Profil, préférences, destinataires récents, cache de requêtes. Rien de secret :
 * les jetons vont dans `secure.ts`, le PIN nulle part.
 *
 * L'instance est créée paresseusement : MMKV est un module natif, et l'importer
 * au chargement casserait les tests unitaires qui n'ont pas de couche native.
 */

import { createMMKV, type MMKV } from 'react-native-mmkv';

export const KvKey = {
  onboardingSeen: 'kora.onboardingSeen',
  balanceHidden: 'kora.balanceHidden',
  profileFullName: 'kora.profile.fullName',
  profilePhone: 'kora.profile.phone',
  lastEmail: 'kora.lastEmail',
  lastPaymentMethod: 'kora.lastPaymentMethod',
  recentRecipients: 'kora.recentRecipients',
  themePreference: 'kora.theme',
  languagePreference: 'kora.language',
  hapticsEnabled: 'kora.hapticsEnabled',
  biometricsEnabled: 'kora.biometricsEnabled',
  queryCache: 'kora.queryCache',
  apiUrlOverride: 'kora.devtools.apiUrl',
  devtoolsJournal: 'kora.devtools.journal',
} as const;

export type KvKeyName = (typeof KvKey)[keyof typeof KvKey];

let instance: MMKV | undefined;

function store(): MMKV {
  // MMKV 4 expose une fabrique ; `MMKV` n'est plus qu'un type.
  instance ??= createMMKV({ id: 'kora' });
  return instance;
}

export function kvGetString(key: KvKeyName): string | null {
  return store().getString(key) ?? null;
}

export function kvSetString(key: KvKeyName, value: string): void {
  store().set(key, value);
}

export function kvGetBoolean(key: KvKeyName, fallback = false): boolean {
  return store().getBoolean(key) ?? fallback;
}

export function kvSetBoolean(key: KvKeyName, value: boolean): void {
  store().set(key, value);
}

/** Règle R2 — un JSON corrompu renvoie le repli, il ne lève jamais. */
export function kvGetJson<T>(key: KvKeyName, fallback: T): T {
  const raw = store().getString(key);
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

export function kvSetJson(key: KvKeyName, value: unknown): void {
  store().set(key, JSON.stringify(value));
}

export function kvDelete(key: KvKeyName): void {
  // MMKV 4 : `remove`, plus `delete`.
  store().remove(key);
}

/** Purge intégrale. Déconnexion et changement d'environnement. */
export function kvClear(): void {
  store().clearAll();
}
