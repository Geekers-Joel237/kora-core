/**
 * Schémas de validation des réponses backend.
 *
 * Tous en `z.looseObject` : les champs supplémentaires sont conservés et
 * ignorés, jamais rejetés. Voir `docs/09-api-evolution.md` §5 R2.
 */

import { z } from 'zod';

export const tokensSchema = z.looseObject({
  accessToken: z.string(),
  accessTokenExpiry: z.string(),
  refreshToken: z.string(),
  refreshTokenExpiry: z.string(),
});

export const otpMessageSchema = z.looseObject({
  message: z.string(),
});

export const balanceSchema = z.looseObject({
  accountId: z.string(),
  accountNumber: z.string(),
  amount: z.number(),
  currency: z.string(),
});

export const transactionReceiptSchema = z.looseObject({
  transactionId: z.string(),
  transactionNumber: z.string(),
  state: z.string(),
  amount: z.number(),
  currency: z.string(),
});

export const stateEntrySchema = z.looseObject({
  oldState: z.string().nullable(),
  newState: z.string(),
  occurredAt: z.string(),
});

export const transactionItemSchema = z.looseObject({
  transactionId: z.string(),
  transactionNumber: z.string(),
  // `type`, `direction` et `state` restent des chaînes libres : les étapes 6 et 8
  // du ROADMAP en introduiront de nouvelles valeurs. Contraindre par un enum ici
  // ferait planter l'historique le jour de la livraison.
  type: z.string(),
  direction: z.string(),
  state: z.string(),
  amount: z.number(),
  currency: z.string(),
  paymentMethod: z.string(),
  counterpart: z.string().nullable(),
  createdAt: z.string(),
  stateHistory: z.array(stateEntrySchema).nullable().optional(),
});

export const transactionHistorySchema = z.looseObject({
  transactions: z.array(transactionItemSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
  hasNext: z.boolean(),
});
