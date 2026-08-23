/**
 * Décodage des réponses réseau — règle R2, `docs/09-api-evolution.md` §5.
 *
 * Les schémas valident en mode **permissif** (`z.looseObject`), jamais strict :
 * un contrat en développement gagne des champs, et les rejeter casserait l'app
 * à chaque livraison backend. Seuls les champs réellement consommés sont exigés.
 */

import type { z } from 'zod';

export interface ContractDrift {
  /** Endpoint concerné, pour le rapport de dérive du mode validation. */
  path: string;
  issues: { path: string; message: string }[];
  received: unknown;
}

export type ContractDriftListener = (drift: ContractDrift) => void;

let onDrift: ContractDriftListener | null = null;

/** Branche le mode validation. Voir `docs/10-validation-mode.md` §5. */
export function setContractDriftListener(listener: ContractDriftListener | null): void {
  onDrift = listener;
}

export class ContractDriftError extends Error {
  readonly drift: ContractDrift;

  constructor(drift: ContractDrift) {
    super(`Réponse inattendue de ${drift.path}`);
    this.name = 'ContractDriftError';
    this.drift = drift;
  }
}

/**
 * Valide une réponse contre son schéma.
 *
 * Un échec signifie que le backend a changé de forme sur un champ dont l'app
 * dépend : c'est une dérive de contrat, signalée bruyamment plutôt que masquée
 * par un repli silencieux qui produirait un écran faux.
 */
export function decode<S extends z.ZodType>(
  schema: S,
  payload: unknown,
  path: string,
): z.infer<S> {
  const result = schema.safeParse(payload);

  if (result.success) return result.data;

  const drift: ContractDrift = {
    path,
    issues: result.error.issues.map((issue) => ({
      path: issue.path.join('.'),
      message: issue.message,
    })),
    received: payload,
  };

  onDrift?.(drift);
  throw new ContractDriftError(drift);
}
