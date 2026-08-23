/**
 * Types de domaine internes — stables par conception.
 *
 * Le backend peut renommer ses champs : seuls les mappeurs de
 * `src/features/<domaine>/api.ts` changent. Voir `docs/09-api-evolution.md` §5 R1.
 */

import type { CurrencyCode } from '@/lib/money/currency';

// ────────────────────────────────────────────────────────── Montants ────────

/**
 * Un montant est TOUJOURS un entier dans la plus petite unité de sa devise.
 * Aucune arithmétique en virgule flottante n'est autorisée — contrat §5.3.
 */
export interface Money {
  minor: number;
  currency: CurrencyCode;
}

// ──────────────────────────────────────────────────────── Transactions ──────

/** Les 11 états du contrat §3.1. Liste exhaustive à la date du relevé. */
export const TX_STATES = [
  'INITIALIZED',
  'AUTHORIZED',
  'CAPTURED',
  'COMPLETED',
  'SETTLEMENT_PENDING',
  'SETTLED',
  'AUTHORIZATION_FAILED',
  'CAPTURE_FAILED',
  'SETTLEMENT_FAILED',
  'FAILED',
  'REVERSED',
] as const;

export type TxState = (typeof TX_STATES)[number];

/** L'utilisateur voit la famille, pas les 11 états. Contrat §3.1. */
export type OutcomeGroup = 'pending' | 'success' | 'failed' | 'reversed';

const OUTCOME: Readonly<Record<TxState, OutcomeGroup>> = {
  INITIALIZED: 'pending',
  AUTHORIZED: 'pending',
  CAPTURED: 'pending',
  SETTLEMENT_PENDING: 'pending',
  COMPLETED: 'success',
  SETTLED: 'success',
  AUTHORIZATION_FAILED: 'failed',
  CAPTURE_FAILED: 'failed',
  SETTLEMENT_FAILED: 'failed',
  FAILED: 'failed',
  REVERSED: 'reversed',
};

/**
 * Règle R2 — tolérance à l'inconnu.
 *
 * Les étapes 6 et 8 du `ROADMAP.md` introduiront de nouveaux états. Un état non
 * répertorié se rend en famille `pending` : jamais un plantage, jamais un
 * succès affiché à tort. Voir `docs/09-api-evolution.md` §5 R2.
 */
export function outcomeOf(state: string): OutcomeGroup {
  return OUTCOME[state as TxState] ?? 'pending';
}

export function isKnownTxState(state: string): state is TxState {
  return state in OUTCOME;
}

/** Un état terminal ne changera plus. Conditionne l'arrêt du sondage. */
export function isTerminalState(state: string): boolean {
  const outcome = outcomeOf(state);
  return outcome !== 'pending' && isKnownTxState(state);
}

export const TX_TYPES = ['CASH_IN', 'CASH_OUT', 'P2P_TRANSFER'] as const;
export type TxType = (typeof TX_TYPES)[number];

export const DIRECTIONS = ['INBOUND', 'OUTBOUND'] as const;
export type Direction = (typeof DIRECTIONS)[number];

export interface StateTransition {
  /** `null` sur la première transition. */
  from: string | null;
  to: string;
  occurredAt: Date;
}

export interface Transaction {
  id: string;
  /** Référence lisible, affichable et copiable. */
  reference: string;
  /** Chaîne brute : peut valoir une valeur hors de `TxType` — voir R2. */
  type: string;
  direction: Direction;
  /** Chaîne brute : peut valoir un état encore inconnu — voir R2. */
  state: string;
  outcome: OutcomeGroup;
  amount: Money;
  paymentMethod: string;
  /** Numéro masqué par le serveur pour les P2P, `null` pour dépôt et retrait. */
  counterpart: string | null;
  createdAt: Date;
  /** Renseigné uniquement quand la requête demandait `detail=true`. */
  stateHistory: StateTransition[] | null;
}

/** Résultat immédiat d'une opération monétaire — plus pauvre qu'une `Transaction`. */
export interface TransactionReceipt {
  id: string;
  reference: string;
  state: string;
  outcome: OutcomeGroup;
  amount: Money;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

// ────────────────────────────────────────────────────────── Portefeuille ────

export interface Account {
  id: string;
  number: string;
  balance: Money;
}

// ──────────────────────────────────────────────────────────── Session ───────

export interface Tokens {
  accessToken: string;
  accessTokenExpiry: Date;
  refreshToken: string;
  refreshTokenExpiry: Date;
}

export type UserRole = 'CUSTOMER' | 'ADMIN';

/** Reconstitué depuis les claims du jeton — aucun endpoint de profil. Contrat §6.3. */
export interface SessionUser {
  id: string;
  email: string | null;
  role: UserRole | null;
}
