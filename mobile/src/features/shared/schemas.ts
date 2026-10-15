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

export const transactionItemSchema = z.looseObject({
  reference: z.string(),
  // `kind` et `status` restent des chaînes libres : un enum ici ferait planter
  // l'historique le jour où le backend ajoute une valeur. Règle R2.
  kind: z.string(),
  status: z.string(),
  amount: z.number(),
  currency: z.string(),
  counterpart: z.string().nullable(),
  channel: z.string().nullable(),
  date: z.string(),
});

export const transactionHistorySchema = z.looseObject({
  items: z.array(transactionItemSchema),
  nextCursor: z.string().nullable(),
});
