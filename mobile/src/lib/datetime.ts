/**
 * Dates — contrat §2, architecture §8.2.
 *
 * Le backend émet **exclusivement en ISO-8601 UTC**. Toute date est convertie
 * dans le fuseau de l'appareil à l'affichage, et reconvertie en UTC à l'envoi.
 * Une date locale envoyée telle quelle produit un `400`.
 */

import { format, isToday, isYesterday, parseISO } from 'date-fns';
import { fr } from 'date-fns/locale';

/** Règle R2 — une date illisible ne fait pas planter un écran. */
export function parseInstant(iso: string): Date | null {
  if (!iso) return null;
  const date = parseISO(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** Repli sur l'époque : un mappeur ne doit jamais lever. */
export function parseInstantOrEpoch(iso: string): Date {
  return parseInstant(iso) ?? new Date(0);
}

/**
 * Sérialisation pour `from` / `to` de `GET /payments/history`.
 * `toISOString()` produit toujours un instant UTC suffixé `Z`, ce que
 * `Instant.parse` attend côté serveur.
 */
export function toApiInstant(date: Date): string {
  return date.toISOString();
}

/**
 * Début du jour local.
 *
 * La granularité au jour n'est pas cosmétique : une borne recalculée à la
 * milliseconde à chaque rendu produirait une clé de requête différente à chaque
 * fois, et donc un rechargement en boucle de l'historique filtré.
 */
export function startOfLocalDay(date: Date): Date {
  const start = new Date(date);
  start.setHours(0, 0, 0, 0);
  return start;
}

/** Fin du jour local, à la milliseconde près. */
export function endOfLocalDay(date: Date): Date {
  const end = new Date(date);
  end.setHours(23, 59, 59, 999);
  return end;
}

/** `date` décalée de `days` jours, sans toucher à l'original. */
export function shiftDays(date: Date, days: number): Date {
  const shifted = new Date(date);
  shifted.setDate(shifted.getDate() + days);
  return shifted;
}

/** Début du jour local, exprimé en UTC — pour un filtre « depuis le … ». */
export function startOfDayUtc(date: Date): string {
  return startOfLocalDay(date).toISOString();
}

/** Fin du jour local, exprimée en UTC. */
export function endOfDayUtc(date: Date): string {
  return endOfLocalDay(date).toISOString();
}

/** Deux instants tombent-ils le même jour local ? */
export function isSameLocalDay(a: Date, b: Date): boolean {
  return dayKey(a) === dayKey(b);
}

/** Borne de plage personnalisée : `6 août`, ou `6 août 2025` si autre année. */
export function formatRangeBound(date: Date, reference: Date = new Date()): string {
  return date.getFullYear() === reference.getFullYear()
    ? format(date, 'd MMM', { locale: fr })
    : format(date, 'd MMM yyyy', { locale: fr });
}

// ── Formats d'affichage — architecture §8.2 ──

/** Ligne d'historique : `11:42` si aujourd'hui, sinon `6 août`. */
export function formatRowTimestamp(date: Date): string {
  return isToday(date) ? format(date, 'HH:mm') : format(date, 'd MMM', { locale: fr });
}

/** En-tête de section : `Aujourd'hui`, `Hier`, ou `6 août 2026`. */
export function formatSectionHeader(date: Date): string {
  if (isToday(date)) return "Aujourd'hui";
  if (isYesterday(date)) return 'Hier';
  return format(date, 'd MMMM yyyy', { locale: fr });
}

/** Écran de détail : `6 août 2026, 11:42`. */
export function formatDetailTimestamp(date: Date): string {
  return format(date, "d MMMM yyyy, HH:mm", { locale: fr });
}

/** Frise des états : `11:42:13`, à la seconde. */
export function formatTimelineTimestamp(date: Date): string {
  return format(date, 'HH:mm:ss');
}

/** Clé de groupement par jour, en heure locale. */
export function dayKey(date: Date): string {
  return format(date, 'yyyy-MM-dd');
}

/** « Mis à jour il y a X » sur les données de cache servies hors ligne. */
export function formatRelativeAge(from: Date, now: Date = new Date()): string {
  const seconds = Math.max(0, Math.floor((now.getTime() - from.getTime()) / 1000));
  if (seconds < 60) return "à l'instant";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `il y a ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `il y a ${hours} h`;
  const days = Math.floor(hours / 24);
  return `il y a ${days} j`;
}
