/**
 * Frontière R1 — `docs/09-api-evolution.md` §5.
 *
 * Ces fonctions sont le **seul** endroit où un DTO d'API devient un type de
 * domaine. Quand le backend renomme un champ, ce fichier est le seul à changer.
 */

import { parseInstantOrEpoch } from '@/lib/datetime';
import { toMoney } from '@/lib/money';
import type {
  ApiBalanceResponse,
  ApiTokensResponse,
  ApiTransactionHistoryResponse,
  ApiTransactionItem,
  ApiTransactionResponse,
} from '@/types/api';
import type { Account, Page, Tokens, Transaction, TransactionReceipt } from '@/types/domain';
import { outcomeOf, outcomeOfStatus } from '@/types/domain';

export function toTokens(dto: ApiTokensResponse): Tokens {
  return {
    accessToken: dto.accessToken,
    accessTokenExpiry: parseInstantOrEpoch(dto.accessTokenExpiry),
    refreshToken: dto.refreshToken,
    refreshTokenExpiry: parseInstantOrEpoch(dto.refreshTokenExpiry),
  };
}

export function toAccount(dto: ApiBalanceResponse): Account {
  return {
    id: dto.accountId,
    number: dto.accountNumber,
    balance: toMoney(dto.amount, dto.currency),
  };
}

export function toReceipt(dto: ApiTransactionResponse): TransactionReceipt {
  return {
    id: dto.transactionId,
    reference: dto.transactionNumber,
    state: dto.state,
    outcome: outcomeOf(dto.state),
    amount: toMoney(dto.amount, dto.currency),
  };
}

/**
 * Le montant arrive signé ; le domaine le garde positif et porte le sens à part.
 * Le signe fait foi même pour un `kind` encore inconnu — règle R2.
 */
export function toTransaction(dto: ApiTransactionItem): Transaction {
  return {
    reference: dto.reference,
    kind: dto.kind,
    outcome: outcomeOfStatus(dto.status),
    direction: dto.amount < 0 ? 'OUTBOUND' : 'INBOUND',
    amount: toMoney(Math.abs(dto.amount), dto.currency),
    channel: dto.channel,
    counterpart: dto.counterpart,
    createdAt: parseInstantOrEpoch(dto.date),
  };
}

/** `cursor` est celui qui a produit la page : il sert à la rejouer — contrat §6.7. */
export function toTransactionPage(
  dto: ApiTransactionHistoryResponse,
  cursor: string | null,
): Page<Transaction> {
  return {
    items: dto.items.map(toTransaction),
    cursor,
    nextCursor: dto.nextCursor,
  };
}
