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
  message: string;
}

const RULES: Rule[] = [
  { status: 422, match: 'insufficient funds', message: 'Solde insuffisant pour cette opération.' },
  { status: 422, match: 'self transfer', message: 'Vous ne pouvez pas vous envoyer de l’argent.' },
  { status: 422, match: 'currency', message: 'Devise incompatible avec votre compte.' },
  { status: 422, match: 'blocked', message: 'Votre compte est bloqué. Contactez le support.' },
  {
    status: 422,
    match: 'recipient account is suspended',
    message: 'Le compte du destinataire est suspendu.',
  },
  { status: 422, match: 'suspended', message: 'Votre compte est suspendu.' },
  {
    status: 422,
    match: 'state transition',
    message: 'Cette opération ne peut plus être modifiée.',
  },
  // ⚠️ Un destinataire inconnu arrive en 404, pas en 422 — contrat §2.
  {
    status: 404,
    match: 'no account found for phone',
    message: 'Aucun compte Kora n’est associé à ce numéro.',
  },
  {
    status: 404,
    match: 'account not found',
    message: 'Votre compte est introuvable. Contactez le support.',
  },
];

/**
 * Un couple inconnu affiche le `detail` du serveur **tel quel** plutôt qu'un
 * message générique : l'utilisateur préfère une phrase imparfaite à
 * « Une erreur est survenue ».
 */
export function paymentErrorMessage(error: unknown): string {
  if (!isKoraError(error)) return 'Une erreur inattendue est survenue.';

  const detail = (error.detail ?? '').toLowerCase();
  const rule = RULES.find((entry) => entry.status === error.status && detail.includes(entry.match));
  if (rule) return rule.message;

  if (error.code === 'INVALID_PIN') return 'PIN incorrect.';
  if (error.status === 400) return 'Certaines informations sont incorrectes.';

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

export const RESULT_COPY: Record<
  FlowOutcome,
  { title: string; description: string; primary: string; secondary: string }
> = {
  success: {
    title: 'Opération réussie',
    description: '',
    primary: 'Terminé',
    secondary: 'Voir le détail',
  },
  pending: {
    title: 'Opération en cours',
    description:
      'Votre opération a été prise en compte et sera finalisée sous peu.',
    primary: 'Terminé',
    secondary: 'Suivre l’opération',
  },
  failed: {
    title: 'Opération refusée',
    description: '',
    primary: 'Réessayer',
    secondary: 'Fermer',
  },
  uncertain: {
    title: 'Nous n’avons pas pu confirmer cette opération',
    description:
      'Elle a peut-être été enregistrée. Vérifiez votre historique avant de réessayer.',
    // ⚠️ L'action principale est la VÉRIFICATION, jamais le rejeu — contrat §6.1.
    primary: 'Vérifier l’historique',
    secondary: 'Réessayer',
  },
};