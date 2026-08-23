import { create } from 'zustand';

import { newIdempotencyKey } from '@/lib/http';
import { KvKey, kvGetJson, kvSetJson } from '@/lib/storage/kv';
import type { TransactionReceipt } from '@/types/domain';

export type FlowKind = 'deposit' | 'withdraw' | 'send';

/** Étapes, dans l'ordre. Le transfert insère « destinataire » avant le montant. */
export const FLOW_STEPS: Record<FlowKind, readonly string[]> = {
  deposit: ['amount', 'method', 'review', 'pin', 'result'],
  withdraw: ['amount', 'method', 'review', 'pin', 'result'],
  send: ['recipient', 'amount', 'review', 'pin', 'result'],
};

export type FlowStep = 'amount' | 'method' | 'recipient' | 'review' | 'pin' | 'result';

export type FlowOutcome = 'success' | 'pending' | 'failed' | 'uncertain';

interface FlowState {
  kind: FlowKind | null;
  amountMinor: number;
  currency: string;
  method: string | null;
  recipientPhone: string;
  /**
   * Case « J'ai vérifié ce numéro », obligatoire sur un transfert.
   * Compense l'absence de résolution de bénéficiaire — contrat §6.2.
   */
  verifiedRecipient: boolean;
  /**
   * Générée à **l'entrée du récapitulatif**, pas au moment de l'envoi : elle
   * doit survivre à un rejeu manuel, sinon le rejeu créerait une seconde
   * opération le jour où l'étape 4 backend atterrit.
   * CONTOURNEMENT(étape-4) — voir `docs/09-api-evolution.md` §3.
   */
  idempotencyKey: string | null;

  /** Piloté par le hook de soumission. **Non autoritaire** — voir `useSubmitPayment`. */
  submitting: boolean;
  outcome: FlowOutcome | null;
  receipt: TransactionReceipt | null;
  errorMessage: string | null;
  error: unknown;

  begin: (kind: FlowKind, currency: string) => void;
  setAmount: (minor: number) => void;
  setMethod: (method: string) => void;
  setRecipient: (phone: string) => void;
  setVerified: (verified: boolean) => void;
  armIdempotency: () => void;
  setSubmitting: (submitting: boolean) => void;
  resolve: (payload: {
    outcome: FlowOutcome;
    receipt?: TransactionReceipt;
    errorMessage?: string;
    error?: unknown;
  }) => void;
  reset: () => void;
}

const EMPTY = {
  kind: null,
  amountMinor: 0,
  currency: 'XOF',
  method: null,
  recipientPhone: '',
  verifiedRecipient: false,
  idempotencyKey: null,
  submitting: false,
  outcome: null,
  receipt: null,
  errorMessage: null,
  error: null,
} as const;

export const usePaymentFlow = create<FlowState>((set) => ({
  ...EMPTY,

  begin: (kind, currency) =>
    set({ ...EMPTY, kind, currency, method: lastMethod(), recipientPhone: '' }),

  setAmount: (amountMinor) => set({ amountMinor }),
  setMethod: (method) => {
    rememberMethod(method);
    set({ method });
  },
  setRecipient: (recipientPhone) => set({ recipientPhone, verifiedRecipient: false }),
  setVerified: (verifiedRecipient) => set({ verifiedRecipient }),

  /** Idempotent : rentrer deux fois dans le récapitulatif ne change pas la clé. */
  armIdempotency: () =>
    set((state) => (state.idempotencyKey ? state : { idempotencyKey: newIdempotencyKey() })),

  setSubmitting: (submitting) => set({ submitting }),

  resolve: ({ outcome, receipt, errorMessage, error }) =>
    set({
      outcome,
      receipt: receipt ?? null,
      errorMessage: errorMessage ?? null,
      error: error ?? null,
      submitting: false,
    }),

  reset: () => set({ ...EMPTY }),
}));

// ── Destinataires récents — locaux, contrat §6.2 ──

const MAX_RECENTS = 5;

export function recentRecipients(): string[] {
  return kvGetJson<string[]>(KvKey.recentRecipients, []);
}

export function rememberRecipient(phone: string): void {
  const existing = recentRecipients().filter((entry) => entry !== phone);
  kvSetJson(KvKey.recentRecipients, [phone, ...existing].slice(0, MAX_RECENTS));
}

function lastMethod(): string | null {
  return kvGetJson<string | null>(KvKey.lastPaymentMethod, null);
}

function rememberMethod(method: string): void {
  kvSetJson(KvKey.lastPaymentMethod, method);
}