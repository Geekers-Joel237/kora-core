import { create } from 'zustand';

import { newIdempotencyKey } from '@/lib/http';
import { KvKey, kvGetJson, kvSetJson } from '@/lib/storage/kv';
import type { PhoneNumber, TransactionReceipt } from '@/types/domain';

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
  /** En deux parties, telles que le backend les attend — contrat §2. */
  recipient: PhoneNumber | null;
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
  setRecipient: (recipient: PhoneNumber) => void;
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
  recipient: null,
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
    set({ ...EMPTY, kind, currency, method: lastMethod(), recipient: null }),

  setAmount: (amountMinor) => set({ amountMinor }),
  setMethod: (method) => {
    rememberMethod(method);
    set({ method });
  },
  setRecipient: (recipient) => set({ recipient, verifiedRecipient: false }),
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

function isPhoneNumber(entry: unknown): entry is PhoneNumber {
  return (
    typeof entry === 'object' &&
    entry !== null &&
    typeof (entry as PhoneNumber).prefix === 'string' &&
    typeof (entry as PhoneNumber).number === 'string'
  );
}

function samePhone(a: PhoneNumber, b: PhoneNumber): boolean {
  return a.prefix === b.prefix && a.number === b.number;
}

/**
 * Les anciennes entrées étaient des numéros complets en une chaîne : un préfixe
 * de 1 à 4 chiffres ne s'en détache pas sans ambiguïté. Elles sont ignorées,
 * jamais devinées — un transfert vers un numéro mal découpé serait pire.
 */
export function recentRecipients(): PhoneNumber[] {
  return kvGetJson<unknown[]>(KvKey.recentRecipients, []).filter(isPhoneNumber);
}

export function rememberRecipient(recipient: PhoneNumber): void {
  const existing = recentRecipients().filter((entry) => !samePhone(entry, recipient));
  kvSetJson(KvKey.recentRecipients, [recipient, ...existing].slice(0, MAX_RECENTS));
}

function lastMethod(): string | null {
  return kvGetJson<string | null>(KvKey.lastPaymentMethod, null);
}

function rememberMethod(method: string): void {
  kvSetJson(KvKey.lastPaymentMethod, method);
}