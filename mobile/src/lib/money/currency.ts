/**
 * Table des devises — contrat §5.3.
 *
 * `exponent` = nombre de décimales de la devise, donc puissance de 10 séparant
 * l'unité majeure de l'unité mineure.
 *
 * Le franc CFA (XOF, XAF) n'a **aucune subdivision** : sa plus petite unité est
 * le franc lui-même. `exponent: 0`.
 */

export const CURRENCIES = {
  XOF: { exponent: 0, symbol: 'F', code: 'XOF' },
  XAF: { exponent: 0, symbol: 'FCFA', code: 'XAF' },
  EUR: { exponent: 2, symbol: '€', code: 'EUR' },
} as const;

export type CurrencyCode = keyof typeof CURRENCIES;

/**
 * `Account` crée tout compte avec `Balance.zero("XOF")` en dur : c'est
 * aujourd'hui la seule devise du système. Contrat §2.
 */
export const DEFAULT_CURRENCY: CurrencyCode = 'XOF';

export function isKnownCurrency(code: string): code is CurrencyCode {
  return code in CURRENCIES;
}

/**
 * Règle R2 — un code de devise inconnu ne fait jamais planter l'app.
 * Repli : exponent 0 et le code brut en guise de symbole.
 */
export function currencyOf(code: string): {
  exponent: number;
  symbol: string;
  code: string;
} {
  if (isKnownCurrency(code)) return CURRENCIES[code];
  return { exponent: 0, symbol: code, code };
}

/** Espace fine insécable U+202F — séparateur de milliers. Design system §3.4. */
export const THIN_NBSP = ' ';

/** Signe moins mathématique U+2212, jamais le trait d'union. Design system §3.4. */
export const MINUS_SIGN = '−';

export const PLUS_SIGN = '+';
