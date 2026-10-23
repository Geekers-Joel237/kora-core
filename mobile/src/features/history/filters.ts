/**
 * Modèle de filtres de l'historique — `docs/05-screens.md` §5, contrat §2.
 *
 * Trois responsabilités, volontairement séparées de l'interface :
 *
 * 1. décrire une sélection (`ActiveFilters`),
 * 2. la traduire en paramètres serveur (`toHistoryQuery`),
 * 3. la transporter dans une URL de navigation (`serialize` / `parse`).
 *
 * La distinction (1) / (2) est ce qui rend la clé de requête stable : la
 * sélection « 30 derniers jours » ne change pas d'un rendu à l'autre, alors que
 * la borne `from` qu'elle produit dépend du jour courant.
 */

import { parseApiDay, shiftDays, startOfLocalDay, toApiDay } from '@/lib/datetime';
import {
  CUSTOMER_STATUSES,
  TX_KINDS,
  type CustomerStatus,
  type TxKind,
} from '@/types/domain';
import type { HistoryFilters } from './api';

/** Fenêtres proposées en puces. `custom` n'en est pas une : c'est une plage. */
export const PERIOD_PRESETS = ['7d', '30d', '90d'] as const;
export type PeriodPreset = (typeof PERIOD_PRESETS)[number];

/** Nombre de jours de chaque fenêtre. Le libellé se compose à partir de là. */
export const PERIOD_DAYS: Record<PeriodPreset, number> = { '7d': 7, '30d': 30, '90d': 90 };

export interface ActiveFilters {
  kind: TxKind | null;
  /** Statut client : filtré côté serveur sur toute la famille — contrat §3.1. */
  status: CustomerStatus | null;
  period: PeriodPreset | null;
  /** Plage personnalisée, en jours locaux. Exclusive de `period`. */
  from: Date | null;
  to: Date | null;
}

export const NO_FILTERS: ActiveFilters = {
  kind: null,
  status: null,
  period: null,
  from: null,
  to: null,
};

/** Nombre de filtres actifs — alimente la pastille de l'icône. */
export function activeFilterCount(filters: ActiveFilters): number {
  let count = 0;
  if (filters.kind) count += 1;
  if (filters.status) count += 1;
  // Période et plage comptent pour un seul filtre : elles s'excluent.
  if (filters.period || filters.from || filters.to) count += 1;
  return count;
}

export function hasAnyFilter(filters: ActiveFilters): boolean {
  return activeFilterCount(filters) > 0;
}

/** Choisir une fenêtre efface la plage personnalisée, et réciproquement. */
export function withPeriod(filters: ActiveFilters, period: PeriodPreset | null): ActiveFilters {
  return { ...filters, period, from: null, to: null };
}

export function withRange(
  filters: ActiveFilters,
  from: Date | null,
  to: Date | null,
): ActiveFilters {
  return { ...filters, period: null, from, to };
}

/**
 * Traduction vers les paramètres de `GET /payments/history`.
 *
 * Le serveur raisonne en jours entiers, bornes incluses : la fenêtre « 7 jours »
 * part donc d'il y a six jours, et aujourd'hui compte pour le septième.
 */
export function toHistoryQuery(
  filters: ActiveFilters,
  now: Date = new Date(),
): HistoryFilters {
  const query: HistoryFilters = {};

  if (filters.kind) query.kind = filters.kind;
  if (filters.status) query.status = filters.status;

  if (filters.period) {
    query.from = startOfLocalDay(shiftDays(now, -(PERIOD_DAYS[filters.period] - 1)));
  } else {
    if (filters.from) query.from = startOfLocalDay(filters.from);
    if (filters.to) query.to = startOfLocalDay(filters.to);
  }

  return query;
}

// ─────────────────────────────────────────────── Transport de navigation ────

/**
 * Sérialisation compacte pour les paramètres de route.
 *
 * Sert aussi de **clé de cache** : deux sélections équivalentes doivent produire
 * la même chaîne, d'où l'ordre fixe des champs.
 */
export function serializeFilters(filters: ActiveFilters): string {
  const parts: string[] = [];
  if (filters.kind) parts.push(`k=${filters.kind}`);
  if (filters.status) parts.push(`s=${filters.status}`);
  if (filters.period) parts.push(`p=${filters.period}`);
  if (filters.from) parts.push(`f=${toApiDay(filters.from)}`);
  if (filters.to) parts.push(`u=${toApiDay(filters.to)}`);
  return parts.join('&');
}

function isOneOf<T extends string>(values: readonly T[], raw: string): raw is T {
  return (values as readonly string[]).includes(raw);
}

/**
 * Lecture tolérante — règle R2.
 *
 * Une valeur inconnue est **ignorée**, jamais propagée : le serveur répondrait
 * `400` sur un `status` invalide, et l'écran de détail deviendrait inaccessible
 * après une simple mise à jour du backend.
 */
export function parseFilters(raw: string | undefined | null): ActiveFilters {
  if (!raw) return NO_FILTERS;

  const filters: ActiveFilters = { ...NO_FILTERS };

  for (const part of raw.split('&')) {
    const separator = part.indexOf('=');
    if (separator < 0) continue;
    const key = part.slice(0, separator);
    const value = part.slice(separator + 1);

    switch (key) {
      case 'k':
        if (isOneOf(TX_KINDS, value)) filters.kind = value;
        break;
      case 's':
        if (isOneOf(CUSTOMER_STATUSES, value)) filters.status = value;
        break;
      case 'p':
        if (isOneOf(PERIOD_PRESETS, value)) filters.period = value;
        break;
      case 'f':
        filters.from = parseApiDay(value);
        break;
      case 'u':
        filters.to = parseApiDay(value);
        break;
      default:
        break;
    }
  }

  // Une plage lue en même temps qu'une fenêtre : la fenêtre l'emporte, comme à
  // la saisie. Laisser les deux produirait une requête que l'interface ne sait
  // pas représenter.
  if (filters.period) {
    filters.from = null;
    filters.to = null;
  }

  return filters;
}
