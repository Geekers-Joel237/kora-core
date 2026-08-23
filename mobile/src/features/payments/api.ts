/**
 * API de paiement — contrat §2.
 *
 * ⚠️ Ces trois fonctions déplacent de l'argent réel. Le client HTTP interdit
 * structurellement toute reprise automatique sur `POST /payments/*`
 * (architecture §3.5). La protection contre le double débit est complétée
 * côté appelant par le verrou à quatre couches d'architecture §5.
 */

import { decode } from '@/lib/decode';
import { request } from '@/lib/http';
import { toApiAmount } from '@/lib/money';
import { transactionReceiptSchema } from '@/features/shared/schemas';
import { toReceipt } from '@/features/shared/mappers';
import type { Money, TransactionReceipt } from '@/types/domain';

export interface CashInput {
  rawPin: string;
  amount: Money;
  paymentMethod: string;
  /**
   * Générée à l'entrée du récapitulatif, pas ici : elle doit survivre à un
   * rejeu manuel pour que ce rejeu devienne sûr le jour où l'étape 4 atterrit.
   *
   * CONTOURNEMENT(étape-4) — le serveur ignore cet en-tête aujourd'hui. Le code
   * l'envoie déjà pour absorber l'étape 4 sans refonte. Voir 09-api-evolution §3.
   */
  idempotencyKey: string;
}

export interface TransferInput extends CashInput {
  /** Numéro complet, préfixe inclus, sans séparateur : `+2250708091011`. */
  toPhoneNumber: string;
}

async function postPayment(
  path: '/payments/cash-in' | '/payments/cash-out' | '/payments/transfer',
  body: Record<string, unknown>,
  idempotencyKey: string,
): Promise<TransactionReceipt> {
  const payload = await request<unknown>(path, {
    method: 'POST',
    body,
    idempotencyKey,
  });
  return toReceipt(decode(transactionReceiptSchema, payload, path));
}

/** Alimente le portefeuille depuis un compte Mobile Money externe. */
export function cashIn(input: CashInput): Promise<TransactionReceipt> {
  return postPayment(
    '/payments/cash-in',
    {
      rawPin: input.rawPin,
      amount: toApiAmount(input.amount.minor, input.amount.currency),
      currency: input.amount.currency,
      paymentMethod: input.paymentMethod,
    },
    input.idempotencyKey,
  );
}

/** Transfère du portefeuille vers un compte Mobile Money externe. */
export function cashOut(input: CashInput): Promise<TransactionReceipt> {
  return postPayment(
    '/payments/cash-out',
    {
      rawPin: input.rawPin,
      amount: toApiAmount(input.amount.minor, input.amount.currency),
      currency: input.amount.currency,
      paymentMethod: input.paymentMethod,
    },
    input.idempotencyKey,
  );
}

/**
 * Transfert vers un autre client Kora.
 *
 * Un numéro inexistant produit un **`404`**, pas un `422` — contrat §2.
 */
export function transfer(input: TransferInput): Promise<TransactionReceipt> {
  return postPayment(
    '/payments/transfer',
    {
      rawPin: input.rawPin,
      amount: toApiAmount(input.amount.minor, input.amount.currency),
      currency: input.amount.currency,
      toPhoneNumber: input.toPhoneNumber,
    },
    input.idempotencyKey,
  );
}
