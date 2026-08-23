/**
 * Durées entre états — `docs/10-validation-mode.md` §4.
 *
 * **C'est la colonne qui transforme l'inspecteur en outil de diagnostic.** Le
 * backend expose `kora.provider.latency.authorize-ms` et `capture-ms` en
 * configuration ; ces deltas sont la seule façon de vérifier depuis le client
 * que la latence observée correspond à celle qui est censée être injectée.
 *
 * Module pur, sans dépendance à React : il doit rester testable seul.
 */

import type { StateTransition } from '@/types/domain';

export interface InterStateDuration {
  from: string;
  to: string;
  ms: number;
}

/**
 * Les transitions ne sont **pas triées** par le contrat : le backend les
 * renvoie dans l'ordre d'insertion, ce qui coïncide en pratique, mais un tri
 * explicite évite des deltas négatifs le jour où ça ne coïncide plus.
 */
export function interStateDurations(history: StateTransition[]): InterStateDuration[] {
  const ordered = [...history].sort(
    (a, b) => a.occurredAt.getTime() - b.occurredAt.getTime(),
  );

  const durations: InterStateDuration[] = [];
  for (let index = 1; index < ordered.length; index += 1) {
    const previous = ordered[index - 1];
    const current = ordered[index];
    if (!previous || !current) continue;
    durations.push({
      from: previous.to,
      to: current.to,
      ms: current.occurredAt.getTime() - previous.occurredAt.getTime(),
    });
  }
  return durations;
}

/** Durée totale du parcours, du premier au dernier état connu. */
export function totalDuration(history: StateTransition[]): number {
  if (history.length < 2) return 0;
  const times = history.map((transition) => transition.occurredAt.getTime());
  return Math.max(...times) - Math.min(...times);
}

/** `842 ms`, `2,4 s`, `1 min 05 s` — lisible sans conversion mentale. */
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms} ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1).replace('.', ',')} s`;
  const minutes = Math.floor(ms / 60_000);
  const seconds = Math.round((ms % 60_000) / 1000);
  return `${minutes} min ${String(seconds).padStart(2, '0')} s`;
}