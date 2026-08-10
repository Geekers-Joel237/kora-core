import { t } from '@/i18n';
import { isKoraError } from '@/lib/http';
import type { FlowOutcome } from './flowStore';

/**
 * Traduction des rejets métier — `docs/05-screens.md` §4.6.
 *
 * **Table de données indexée par (statut, motif), jamais une cascade de `if`.**
 * L'étape 8 du `ROADMAP.md` y ajoutera les rejets de vélocité : ajouter un
 * motif doit rester l'ajout d'une ligne.
 */
interface Rule {
  status: number;
  /** Fragment recherché dans le `detail` du serveur, en minuscules. */
  match: string;
  /** Clé i18n — le message est résolu à l'affichage, pas au chargement. */
  key: string;
}

const RULES: Rule[] = [
  { status: 422, match: 'insufficient funds', key: 'errors.insufficientFunds' },
  { status: 422, match: 'self transfer', key: 'errors.selfTransfer' },
  { status: 422, match: 'currency', key: 'errors.currencyMismatch' },
  { status: 422, match: 'blocked', key: 'errors.accountBlocked' },
  { status: 422, match: 'recipient account is suspended', key: 'errors.recipientSuspended' },
  { status: 422, match: 'suspended', key: 'errors.accountSuspended' },
  { status: 422, match: 'state transition', key: 'errors.stateTransition' },
  // ⚠️ Un destinataire inconnu arrive en 404, pas en 422 — contrat §2.
  { status: 404, match: 'no account found for phone', key: 'errors.recipientUnknown' },
  { status: 404, match: 'account not found', key: 'errors.accountNotFound' },
];

/**
 * Un couple inconnu affiche le `detail` du serveur **tel quel** plutôt qu'un
 * message générique : l'utilisateur préfère une phrase imparfaite à
 * « Une erreur est survenue ».
 */
export function paymentErrorMessage(error: unknown): string {
  if (!isKoraError(error)) return t('errors.UNKNOWN');

  const detail = (error.detail ?? '').toLowerCase();
  const rule = RULES.find((entry) => entry.status === error.status && detail.includes(entry.match));
  if (rule) return t(rule.key);

  if (error.code === 'INVALID_PIN') return t('errors.INVALID_PIN');
  if (error.status === 400) return t('errors.VALIDATION');

  return error.detail ?? error.message;
}

/**
 * Classe une issue de paiement — `docs/05-screens.md` §4.6.
 *
 * `isOutcomeUnknown` est calculé par la couche HTTP et couvre `503`, `500` et
 * l'absence de réponse. C'est **la seule** condition qui mène à l'écran
 * « issue incertaine ».
 */
export function outcomeForError(error: unknown): FlowOutcome {
  if (isKoraError(error) && error.isOutcomeUnknown) return 'uncertain';
  return 'failed';
}

/**
 * Un état intermédiaire n'est **jamais** présenté comme un succès.
 * L'écart entre `CAPTURED` et `COMPLETED` est exactement le genre de raccourci
 * qui produit une réclamation client.
 */
export function outcomeForState(outcome: 'pending' | 'success' | 'failed' | 'reversed'): FlowOutcome {
  if (outcome === 'success') return 'success';
  if (outcome === 'pending') return 'pending';
  return 'failed';
}

export interface ResultCopy {
  title: string;
  description: string;
  primary: string;
  secondary: string;
}

/**
 * Textes de l'écran de résultat — `docs/05-screens.md` §4.6.
 *
 * ⚠️ Sur une issue incertaine, l'action **principale** est la vérification de
 * l'historique, jamais le rejeu : sans idempotence serveur, un rejeu peut
 * débiter deux fois. Contrat §6.1.
 */
export function resultCopy(outcome: FlowOutcome): ResultCopy {
  return {
    title: t(`result.${outcome}.title`),
    description: t(`result.${outcome}.description`),
    primary: t(`result.${outcome}.primary`),
    secondary: t(`result.${outcome}.secondary`),
  };
}
