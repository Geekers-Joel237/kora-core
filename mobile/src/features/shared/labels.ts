/**
 * Traductions métier — `docs/04-components.md` §5.
 *
 * **L'utilisateur ne voit jamais `AUTHORIZATION_FAILED`.** Ces tables sont la
 * frontière entre le vocabulaire du domaine et celui du produit.
 *
 * Provisoirement en français en dur : le lot 9 les déplace vers i18next. Les
 * garder groupées ici rend cette migration mécanique.
 */

import { brandColors } from '@/theme/brands';
import type { OutcomeGroup } from '@/types/domain';

interface StateLabel {
  label: string;
  description: string;
}

/** Les 11 états du contrat §3.1, plus le repli pour tout état futur. */
const STATE_LABELS: Record<string, StateLabel> = {
  INITIALIZED: { label: 'Initiée', description: 'Opération enregistrée' },
  AUTHORIZED: { label: 'Autorisée', description: "Fonds réservés chez l'opérateur" },
  CAPTURED: { label: 'Capturée', description: 'Fonds prélevés' },
  COMPLETED: { label: 'Terminée', description: 'Opération finalisée' },
  SETTLEMENT_PENDING: { label: 'Règlement en cours', description: "En attente de l'opérateur" },
  SETTLED: { label: 'Réglée', description: 'Règlement confirmé' },
  AUTHORIZATION_FAILED: {
    label: 'Autorisation refusée',
    description: "L'opérateur a refusé l'opération",
  },
  CAPTURE_FAILED: { label: 'Prélèvement échoué', description: "Le prélèvement n'a pas abouti" },
  SETTLEMENT_FAILED: { label: 'Règlement échoué', description: "Le règlement n'a pas abouti" },
  FAILED: { label: 'Échouée', description: "L'opération n'a pas pu être exécutée" },
  REVERSED: { label: 'Annulée', description: 'Opération contrepassée' },
};

/**
 * Règle R2 — un état inconnu affiche son code brut plutôt qu'un message
 * générique. L'utilisateur préfère une chaîne imparfaite à « Erreur ».
 */
export function stateLabel(state: string): StateLabel {
  return STATE_LABELS[state] ?? { label: state, description: '' };
}

/** Libellé court des familles, pour les puces d'état. */
export const OUTCOME_LABELS: Record<OutcomeGroup, string> = {
  pending: 'En cours',
  success: 'Terminée',
  failed: 'Échouée',
  reversed: 'Annulée',
};

const TYPE_LABELS: Record<string, string> = {
  CASH_IN: 'Dépôt',
  CASH_OUT: 'Retrait',
  P2P_TRANSFER: 'Transfert',
};

export function transactionTypeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type;
}

/**
 * Opérateurs Mobile Money — contrat §4.
 * `paymentMethod` est une chaîne libre côté backend : la liste est figée ici,
 * et n'est jamais rendue configurable par l'utilisateur.
 */
export const PAYMENT_METHODS = [
  { id: 'ORANGE_MONEY', label: 'Orange Money', brand: brandColors.ORANGE_MONEY },
  { id: 'MTN_MOMO', label: 'MTN MoMo', brand: brandColors.MTN_MOMO },
  { id: 'MOOV_MONEY', label: 'Moov Money', brand: brandColors.MOOV_MONEY },
  { id: 'WAVE', label: 'Wave', brand: brandColors.WAVE },
] as const;

export type PaymentMethodId = (typeof PAYMENT_METHODS)[number]['id'];

/** `WALLET` apparaît en lecture pour les P2P ; l'app ne l'envoie jamais. */
export function paymentMethodLabel(id: string): string {
  if (id === 'WALLET') return 'Kora';
  return PAYMENT_METHODS.find((method) => method.id === id)?.label ?? id;
}

export function paymentMethodBrand(id: string): string | null {
  return PAYMENT_METHODS.find((method) => method.id === id)?.brand ?? null;
}
