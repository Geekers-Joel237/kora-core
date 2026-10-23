/**
 * Préférences d'affichage partagées entre écrans.
 *
 * Le masquage du solde est réglable à deux endroits — l'œil de la carte héros
 * et la ligne des réglages — et **les deux doivent rester d'accord**. Lire MMKV
 * dans un `useState` à chaque montage produisait l'inverse : l'accueil, monté
 * en permanence par la barre d'onglets, gardait la valeur qu'il avait lue au
 * lancement.
 */

import { create } from 'zustand';

import { areHapticsEnabled, reloadHapticsPreference, setHapticsEnabled } from '@/lib/haptics';
import { KvKey, kvGetBoolean, kvSetBoolean } from '@/lib/storage/kv';

interface PreferencesState {
  balanceHidden: boolean;
  /**
   * Miroir réactif de la préférence de `lib/haptics.ts`.
   *
   * Le module haptique est appelé depuis des worklets et des chemins critiques :
   * il garde sa valeur en mémoire et ne peut pas dépendre d'un store React.
   * C'est donc le store qui le reflète, pas l'inverse.
   */
  hapticsEnabled: boolean;
}

function readBalanceHidden(): boolean {
  try {
    return kvGetBoolean(KvKey.balanceHidden);
  } catch {
    return false;
  }
}

export const usePreferences = create<PreferencesState>(() => ({
  balanceHidden: readBalanceHidden(),
  hapticsEnabled: areHapticsEnabled(),
}));

export function setBalanceHidden(hidden: boolean): void {
  kvSetBoolean(KvKey.balanceHidden, hidden);
  usePreferences.setState({ balanceHidden: hidden });
}

export function setHapticsPreference(enabled: boolean): void {
  setHapticsEnabled(enabled);
  usePreferences.setState({ hapticsEnabled: enabled });
}

/** Relit le stockage — après une purge de déconnexion, par exemple. */
export function reloadPreferences(): void {
  reloadHapticsPreference();
  usePreferences.setState({
    balanceHidden: readBalanceHidden(),
    hapticsEnabled: areHapticsEnabled(),
  });
}
