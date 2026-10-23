/**
 * Retour haptique — `docs/03-motion-and-feel.md` §3.
 *
 * **Prescriptif, pas décoratif.** Une impulsion mal calibrée est pire que son
 * absence : elle apprend à l'utilisateur à ignorer le canal.
 *
 * Deux interdits absolus :
 *  1. Jamais d'haptique sur un événement non provoqué par l'utilisateur —
 *     arrivée de données, revalidation de cache, fin de rafraîchissement.
 *  2. Jamais deux impulsions à moins de 50 ms d'intervalle : elles se
 *     ressentent comme une vibration parasite. L'étranglement est global.
 */

import * as Haptics from 'expo-haptics';

import { KvKey, kvGetBoolean, kvSetBoolean } from '@/lib/storage/kv';

export type HapticStyle =
  | 'tap'
  | 'select'
  | 'press'
  | 'commit'
  | 'success'
  | 'warning'
  | 'error'
  | 'none';

/** Deux impulsions à moins de 50 ms se ressentent comme un défaut. §3 */
const THROTTLE_MS = 50;

/**
 * `-Infinity` et non `0` : avec `0`, la toute première impulsion serait
 * étranglée si l'horloge est proche de l'époque. Une sentinelle doit dire
 * « jamais », pas « à l'instant zéro ».
 */
let lastFiredAt = Number.NEGATIVE_INFINITY;
let enabled: boolean | null = null;

function isEnabled(): boolean {
  // `kvGetBoolean` renvoie le repli quand la clé n'existe pas : l'haptique est
  // active par défaut, l'utilisateur peut la couper dans les réglages.
  enabled ??= kvGetBoolean(KvKey.hapticsEnabled, true);
  return enabled;
}

export function setHapticsEnabled(next: boolean): void {
  enabled = next;
  kvSetBoolean(KvKey.hapticsEnabled, next);
}

export function areHapticsEnabled(): boolean {
  return isEnabled();
}

/**
 * Relit la préférence depuis le stockage.
 *
 * Indispensable après une déconnexion : `kvClear()` efface la clé, mais la
 * valeur mise en cache dans ce module survivrait au changement de compte.
 */
export function reloadHapticsPreference(): void {
  enabled = null;
}

/** Réinitialise l'étranglement et le cache de préférence. Réservé aux tests. */
export function __resetHaptics(): void {
  lastFiredAt = Number.NEGATIVE_INFINITY;
  enabled = null;
}

function fire(effect: () => Promise<void>): void {
  if (!isEnabled()) return;

  const now = Date.now();
  if (now - lastFiredAt < THROTTLE_MS) return;
  lastFiredAt = now;

  // Une impulsion qui échoue — appareil sans moteur haptique, permission
  // refusée, module natif absent — ne doit **jamais** interrompre
  // l'interaction en cours.
  //
  // Le `try` est indispensable en plus du `.catch` : certaines plateformes
  // lèvent de façon *synchrone*, avant même de renvoyer une promesse. Un
  // `.catch` seul laisserait alors l'exception remonter jusqu'à l'appelant —
  // et interromprait, par exemple, une soumission de paiement.
  try {
    void effect().catch(() => undefined);
  } catch {
    // ignoré délibérément
  }
}

export const haptic = {
  /** Touche de pavé numérique ou de PIN. Le plus fréquent — doit rester léger. */
  tap: () => fire(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light)),

  /** Bascule, sélection, changement d'onglet, cran de filtre. */
  select: () => fire(() => Haptics.selectionAsync()),

  /** Action significative : ouvrir une feuille, valider une étape. */
  press: () => fire(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium)),

  /** Point de non-retour : confirmation d'un paiement, appui long. */
  commit: () => fire(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy)),

  /** Uniquement sur un état terminal de succès. */
  success: () =>
    fire(() => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success)),

  /** Opération en cours ou issue incertaine. */
  warning: () =>
    fire(() => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning)),

  /** Échec : PIN erroné, solde insuffisant, opération rejetée. */
  error: () => fire(() => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error)),
} as const;

/** Déclenche par nom. `'none'` est un no-op explicite, pas un oubli. */
export function triggerHaptic(style: HapticStyle): void {
  if (style === 'none') return;
  haptic[style]();
}
