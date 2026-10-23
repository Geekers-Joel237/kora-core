/**
 * Traductions métier — `docs/04-components.md` §5.
 *
 * **L'utilisateur ne voit jamais `AUTHORIZATION_FAILED`.** Ces fonctions sont la
 * frontière entre le vocabulaire du domaine et celui du produit.
 *
 * ⚠️ Elles lisent l'instance i18next **au moment de l'appel**. Tout composant
 * qui les rend doit donc appeler `useTranslation()` — même sans utiliser le `t`
 * renvoyé — afin de se re-rendre au changement de langue. Sans cette
 * souscription, un écran garderait ses libellés dans l'ancienne langue.
 */

import { t } from '@/i18n';
import { brandColors } from '@/theme/brands';
import { isKnownTxKind, type OutcomeGroup } from '@/types/domain';

/** Libellé court des familles, pour les puces d'état. */
export function outcomeLabel(outcome: OutcomeGroup): string {
  return t(`outcomes.${outcome}`);
}

/**
 * Règle R2 — une nature inconnue affiche son code brut plutôt qu'un message
 * générique. L'utilisateur préfère une chaîne imparfaite à « Erreur ».
 */
export function kindLabel(kind: string): string {
  return isKnownTxKind(kind) ? t(`txKinds.${kind}`) : kind;
}

/**
 * Opérateurs Mobile Money — contrat §4.
 *
 * `paymentMethod` est une chaîne libre côté backend : la liste est figée ici,
 * et n'est jamais rendue configurable par l'utilisateur. **Ces libellés ne sont
 * pas traduits** : « Orange Money » est un nom de marque, pas du texte.
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
