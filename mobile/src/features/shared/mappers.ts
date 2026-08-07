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
  ApiStateEntry,
  ApiTokensResponse,
  ApiTransactionHistoryResponse,
  ApiTransactionItem,
  ApiTransactionResponse,
} from '@/types/api';
import type {
  Account,
  Direction,
  Page,
  StateTransition,
  Tokens,
  Transaction,
  TransactionReceipt,
} from '@/types/domain';
import { outcomeOf } from '@/types/domain';

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

/** Règle R2 — une direction inconnue se replie sur `OUTBOUND`, jamais un plantage. */
function toDirection(raw: string): Direction {
  return raw === 'INBOUND' ? 'INBOUND' : 'OUTBOUND';
}

function toStateTransition(dto: ApiStateEntry): StateTransition {
  return {
    from: dto.oldState,
    to: dto.newState,
    occurredAt: parseInstantOrEpoch(dto.occurredAt),
  };
}

export function toTransaction(dto: ApiTransactionItem): Transaction {
  return {
    id: dto.transactionId,
    reference: dto.transactionNumber,
    type: dto.type,
    direction: toDirection(dto.direction),
    state: dto.state,
    outcome: outcomeOf(dto.state),
    amount: toMoney(dto.amount, dto.currency),
    paymentMethod: dto.paymentMethod,
    counterpart: dto.counterpart,
    createdAt: parseInstantOrEpoch(dto.createdAt),
    stateHistory: dto.stateHistory ? dto.stateHistory.map(toStateTransition) : null,
  };
}

export function toTransactionPage(
  dto: ApiTransactionHistoryResponse,
): Page<Transaction> {
  return {
    items: dto.transactions.map(toTransaction),
    page: dto.page,
    size: dto.size,
    totalElements: dto.totalElements,
    totalPages: dto.totalPages,
    hasNext: dto.hasNext,
  };
}
