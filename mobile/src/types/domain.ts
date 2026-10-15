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

/** L'utilisateur voit une famille, jamais un état technique. Contrat §3.1. */
export type OutcomeGroup = 'pending' | 'success' | 'failed' | 'reversed';

/** Statut client renvoyé par l'historique — contrat §3.1. */
export const CUSTOMER_STATUSES = ['PENDING', 'COMPLETED', 'FAILED', 'REFUNDED'] as const;
export type CustomerStatus = (typeof CUSTOMER_STATUSES)[number];

const OUTCOME_OF_STATUS: Readonly<Record<CustomerStatus, OutcomeGroup>> = {
  PENDING: 'pending',
  COMPLETED: 'success',
  FAILED: 'failed',
  REFUNDED: 'reversed',
};

/** Règle R2 : un statut inconnu se rend `pending`, jamais un succès affiché à tort. */
export function outcomeOfStatus(status: string): OutcomeGroup {
  return OUTCOME_OF_STATUS[status as CustomerStatus] ?? 'pending';
}

/**
 * États techniques — ils n'apparaissent plus que dans la réponse immédiate d'un
 * paiement (`TransactionReceipt.state`). Correspondance **identique** à
 * `CustomerStatus` côté backend : réussi dès la capture. Contrat §3.1.
 */
export const TX_STATES = [
  'INITIALIZED',
  'AUTHORIZED',
  'UNKNOWN',
  'CAPTURED',
  'SETTLEMENT_PENDING',
  'SETTLED',
  'COMPLETED',
  'SETTLEMENT_FAILED',
  'AUTHORIZATION_FAILED',
  'CAPTURE_FAILED',
  'FAILED',
  'REVERSED',
] as const;

export type TxState = (typeof TX_STATES)[number];

const OUTCOME_OF_STATE: Readonly<Record<TxState, OutcomeGroup>> = {
  INITIALIZED: 'pending',
  AUTHORIZED: 'pending',
  UNKNOWN: 'pending',
  CAPTURED: 'success',
  SETTLEMENT_PENDING: 'success',
  SETTLED: 'success',
  COMPLETED: 'success',
  SETTLEMENT_FAILED: 'success',
  AUTHORIZATION_FAILED: 'failed',
  CAPTURE_FAILED: 'failed',
  FAILED: 'failed',
  REVERSED: 'reversed',
};

/** Règle R2 — un état non répertorié se rend `pending`. Voir `docs/09-api-evolution.md` §5. */
export function outcomeOf(state: string): OutcomeGroup {
  return OUTCOME_OF_STATE[state as TxState] ?? 'pending';
}

export function isKnownTxState(state: string): state is TxState {
  return state in OUTCOME_OF_STATE;
}

/** Nature d'une opération, lue du côté du wallet de l'utilisateur. Contrat §2. */
export const TX_KINDS = ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER_SENT', 'TRANSFER_RECEIVED'] as const;
export type TxKind = (typeof TX_KINDS)[number];

export function isKnownTxKind(kind: string): kind is TxKind {
  return (TX_KINDS as readonly string[]).includes(kind);
}

/** Sens de l'argent pour le wallet — pilote le signe et la couleur du montant. */
export type Direction = 'INBOUND' | 'OUTBOUND';

export interface Transaction {
  /** `TRX-…` — identité de l'opération côté client, affichable et copiable. */
  reference: string;
  /** Chaîne brute : peut valoir une valeur hors de `TxKind` — voir R2. */
  kind: string;
  outcome: OutcomeGroup;
  direction: Direction;
  /** Toujours positif ; le sens est porté par `direction`. */
  amount: Money;
  /** Opérateur Mobile Money d'un dépôt ou d'un retrait, `null` pour un transfert. */
  channel: string | null;
  /** Numéro masqué par le serveur pour un transfert, `null` sinon. */
  counterpart: string | null;
  createdAt: Date;
}

/** Résultat immédiat d'une opération monétaire — plus pauvre qu'une `Transaction`. */
export interface TransactionReceipt {
  id: string;
  /** Même valeur que `Transaction.reference` : c'est par elle qu'on retrouve l'opération. */
  reference: string;
  state: string;
  outcome: OutcomeGroup;
  amount: Money;
}

export interface Page<T> {
  items: T[];
  /** Curseur qui a produit cette page ; `null` pour la première. Sert au rejeu §6.7. */
  cursor: string | null;
  /** `null` sur la dernière page. */
  nextCursor: string | null;
}

// ──────────────────────────────────────────────────────────── Téléphone ─────

/** Numéro en deux parties, telles que le backend les attend. */
export interface PhoneNumber {
  /** `+225` */
  prefix: string;
  /** `0708091011`, sans le préfixe. */
  number: string;
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
