/**
 * Modèle de filtres de l'historique — `docs/05-screens.md` §5, contrat §6.8.
 *
 * Trois responsabilités, volontairement séparées de l'interface :
 *
 * 1. décrire une sélection (`ActiveFilters`),
 * 2. la traduire en paramètres serveur (`toHistoryQuery`),
 * 3. la transporter dans une URL de navigation (`serialize` / `parse`).
 *
 * La distinction (1) / (2) est ce qui rend la clé de requête stable : la
 * sélection « 30 derniers jours » ne change pas d'un rendu à l'autre, alors que
 * la borne `from` qu'elle produit dépend de l'instant.
 */

import { shiftDays, startOfLocalDay, endOfLocalDay, parseInstant } from '@/lib/datetime';
import {
  DIRECTIONS,
  TX_STATES,
  TX_TYPES,
  type Direction,
  type TxState,
  type TxType,
} from '@/types/domain';
import type { HistoryFilters } from './api';

/** Fenêtres proposées en puces. `custom` n'en est pas une : c'est une plage. */
export const PERIOD_PRESETS = ['7d', '30d', '90d'] as const;
export type PeriodPreset = (typeof PERIOD_PRESETS)[number];

const PRESET_DAYS: Record<PeriodPreset, number> = { '7d': 7, '30d': 30, '90d': 90 };

export const PERIOD_LABELS: Record<PeriodPreset, string> = {
  '7d': '7 j',
  '30d': '30 j',
  '90d': '90 j',
};

export interface ActiveFilters {
  type: TxType | null;
  direction: Direction | null;
  /**
   * **Un seul état concret**, jamais une famille — contrat §6.8. Un filtre par
   * famille exigerait un filtrage local qui fausserait total et pagination.
   */
  state: TxState | null;
  period: PeriodPreset | null;
  /** Plage personnalisée. Exclusive de `period`. */
  from: Date | null;
  to: Date | null;
}

export const NO_FILTERS: ActiveFilters = {
  type: null,
  direction: null,
  state: null,
  period: null,
  from: null,
  to: null,
};

/** Nombre de filtres actifs — alimente la pastille de l'icône. */
export function activeFilterCount(filters: ActiveFilters): number {
  let count = 0;
  if (filters.type) count += 1;
  if (filters.direction) count += 1;
  if (filters.state) count += 1;
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
 * Les bornes sont ramenées au jour local : la fenêtre « 7 jours » désigne sept
 * journées entières, pas « il y a exactement 168 heures ».
 */
export function toHistoryQuery(
  filters: ActiveFilters,
  now: Date = new Date(),
): HistoryFilters {
  const query: HistoryFilters = {};

  if (filters.type) query.type = filters.type;
  if (filters.state) query.state = filters.state;
  if (filters.direction) query.direction = filters.direction;

  if (filters.period) {
    // `- (n - 1)` : « 7 j » inclut aujourd'hui, sinon la fenêtre en couvre huit.
    query.from = startOfLocalDay(shiftDays(now, -(PRESET_DAYS[filters.period] - 1)));
  } else {
    if (filters.from) query.from = startOfLocalDay(filters.from);
    if (filters.to) query.to = endOfLocalDay(filters.to);
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
  if (filters.type) parts.push(`t=${filters.type}`);
  if (filters.direction) parts.push(`d=${filters.direction}`);
  if (filters.state) parts.push(`s=${filters.state}`);
  if (filters.period) parts.push(`p=${filters.period}`);
  if (filters.from) parts.push(`f=${startOfLocalDay(filters.from).toISOString()}`);
  if (filters.to) parts.push(`u=${endOfLocalDay(filters.to).toISOString()}`);
  return parts.join('&');
}

function isOneOf<T extends string>(values: readonly T[], raw: string): raw is T {
  return (values as readonly string[]).includes(raw);
}

/**
 * Lecture tolérante — règle R2.
 *
 * Une valeur inconnue est **ignorée**, jamais propagée : le serveur répondrait
 * `400` sur un `state` invalide, et l'écran de détail deviendrait inaccessible
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
      case 't':
        if (isOneOf(TX_TYPES, value)) filters.type = value;
        break;
      case 'd':
        if (isOneOf(DIRECTIONS, value)) filters.direction = value;
        break;
      case 's':
        if (isOneOf(TX_STATES, value)) filters.state = value;
        break;
      case 'p':
        if (isOneOf(PERIOD_PRESETS, value)) filters.period = value;
        break;
      case 'f':
        filters.from = parseInstant(value);
        break;
      case 'u':
        filters.to = parseInstant(value);
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