/**
 * Conversion des montants à la frontière du réseau — contrat §5.3.
 *
 * Le backend sérialise un `BigDecimal` en nombre JSON, qui devient un `double`
 * en JavaScript. La conversion en entier de plus petite unité a lieu **une
 * seule fois, immédiatement à la réception**, et plus jamais ensuite.
 */

import { currencyOf } from './currency';
import type { Money } from '@/types/domain';
import type { CurrencyCode } from './currency';

/** Signalé quand le backend renvoie une échelle incohérente avec la devise. */
export type ScaleAnomalyListener = (info: {
  raw: number;
  currency: string;
  rounded: number;
}) => void;

let onScaleAnomaly: ScaleAnomalyListener | null = null;

/**
 * Branche le mode validation sur les anomalies d'échelle.
 * `Amount` n'impose aucune échelle côté backend : `100.5 XOF` serait accepté.
 * Voir `docs/10-validation-mode.md` scénario 18.
 */
export function setScaleAnomalyListener(listener: ScaleAnomalyListener | null): void {
  onScaleAnomaly = listener;
}

/**
 * Convertit un montant d'API en entier de plus petite unité.
 *
 * Une valeur incompatible avec l'échelle de la devise est **arrondie**, jamais
 * rejetée : l'utilisateur doit voir son argent même si le backend est incohérent.
 */
export function toMinor(apiAmount: number, currencyCode: string): number {
  if (!Number.isFinite(apiAmount)) return 0;

  const { exponent } = currencyOf(currencyCode);
  const scaled = apiAmount * 10 ** exponent;
  const rounded = Math.round(scaled);

  if (Math.abs(scaled - rounded) > Number.EPSILON * Math.max(1, Math.abs(scaled))) {
    onScaleAnomaly?.({ raw: apiAmount, currency: currencyCode, rounded });
  }

  return rounded;
}

/** Convertit un entier de plus petite unité vers le nombre attendu par l'API. */
export function toApiAmount(minor: number, currencyCode: string): number {
  const { exponent } = currencyOf(currencyCode);
  if (exponent === 0) return Math.round(minor);
  return Math.round(minor) / 10 ** exponent;
}

export function toMoney(apiAmount: number, currencyCode: string): Money {
  return {
    minor: toMinor(apiAmount, currencyCode),
    currency: currencyCode as CurrencyCode,
  };
}

// ── Arithmétique. Entière, toujours. ──

export function addMoney(a: Money, b: Money): Money {
  assertSameCurrency(a, b);
  return { minor: a.minor + b.minor, currency: a.currency };
}

export function subtractMoney(a: Money, b: Money): Money {
  assertSameCurrency(a, b);
  return { minor: a.minor - b.minor, currency: a.currency };
}

export function isGreaterThan(a: Money, b: Money): boolean {
  assertSameCurrency(a, b);
  return a.minor > b.minor;
}

export function isZero(money: Money): boolean {
  return money.minor === 0;
}

function assertSameCurrency(a: Money, b: Money): void {
  if (a.currency !== b.currency) {
    throw new Error(`Currency mismatch: ${a.currency} vs ${b.currency}`);
  }
}
