/**
 * Formatage monétaire — design system §3.4, contrat §5.3.
 *
 * `formatMinor` ne renvoie **jamais une chaîne unique** : il renvoie les blocs
 * typographiques séparés attendus par le composant `Amount`, qui les compose
 * avec des tailles et des graisses différentes (le symbole à 0,45× la taille).
 */

import { currencyOf, MINUS_SIGN, PLUS_SIGN, THIN_NBSP } from './currency';

export type SignPolicy = 'auto' | 'always' | 'never';

export interface FormattedAmount {
  /** `−`, `+` ou chaîne vide. Jamais un trait d'union. */
  sign: string;
  /** Partie entière groupée par milliers avec U+202F. */
  integer: string;
  /** Décimales sans séparateur, ou `null` si la devise n'en a pas. */
  fraction: string | null;
  /** Symbole de la devise. */
  symbol: string;
}

export interface FormatOptions {
  sign?: SignPolicy;
  /** Rend `••••` à la place des chiffres. La devise reste visible. */
  hidden?: boolean;
}

/** Design system §3.4 — un solde masqué affiche quatre pastilles. */
const HIDDEN_DIGITS = '••••';

function groupThousands(digits: string): string {
  // Regroupe par 3 depuis la droite. Insensible à la locale : le séparateur
  // est imposé par le design system, pas par le système d'exploitation.
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, THIN_NBSP);
}

function resolveSign(minor: number, policy: SignPolicy): string {
  if (policy === 'never') return '';
  if (minor < 0) return MINUS_SIGN;
  if (policy === 'always' && minor > 0) return PLUS_SIGN;
  return '';
}

/**
 * @param minor Montant entier dans la plus petite unité de la devise.
 *              Un non-entier est une erreur d'appel : il est arrondi et signalé.
 */
export function formatMinor(
  minor: number,
  currencyCode: string,
  options: FormatOptions = {},
): FormattedAmount {
  const { sign: signPolicy = 'auto', hidden = false } = options;
  const currency = currencyOf(currencyCode);

  const safeMinor = Number.isFinite(minor) ? Math.round(minor) : 0;

  if (hidden) {
    return {
      sign: '',
      integer: HIDDEN_DIGITS,
      fraction: null,
      symbol: currency.symbol,
    };
  }

  const sign = resolveSign(safeMinor, signPolicy);
  const absolute = Math.abs(safeMinor);

  if (currency.exponent === 0) {
    return {
      sign,
      integer: groupThousands(String(absolute)),
      fraction: null,
      symbol: currency.symbol,
    };
  }

  const divisor = 10 ** currency.exponent;
  const integerPart = Math.floor(absolute / divisor);
  const fractionPart = absolute % divisor;

  return {
    sign,
    integer: groupThousands(String(integerPart)),
    fraction: String(fractionPart).padStart(currency.exponent, '0'),
    symbol: currency.symbol,
  };
}

/**
 * Rendu en chaîne unique. Réservé aux libellés d'accessibilité, aux journaux de
 * développement et aux tests. **Les écrans passent par le composant `Amount`.**
 */
export function formatMinorToString(
  minor: number,
  currencyCode: string,
  options: FormatOptions = {},
): string {
  const { sign, integer, fraction, symbol } = formatMinor(minor, currencyCode, options);
  const decimals = fraction !== null ? `,${fraction}` : '';
  return `${sign}${integer}${decimals}${THIN_NBSP}${symbol}`;
}
