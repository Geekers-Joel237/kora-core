/**
 * Capacités du backend — `docs/09-api-evolution.md` §3.
 *
 * Le backend est en développement actif. Chaque contournement documenté en
 * `docs/01-api-contract.md` §6 est conditionné à l'un de ces drapeaux, et à
 * aucun autre mécanisme. **Aucun contournement n'est câblé en dur.**
 *
 * Quand une étape du `ROADMAP.md` atterrit : basculer le drapeau ici, puis
 * `grep -r "CONTOURNEMENT(étape-N)" src/` pour trouver le code à retirer.
 */
export const API_CAPABILITIES = {
  /** étape 4 — `Idempotency-Key` honorée sur `/payments/*`. */
  idempotency: false,
  /** étape 8 — rejets pour plafond de vélocité, revue manuelle. */
  velocityLimits: false,
  /** étape 5+ — canal temps réel, rend le sondage superflu. */
  realtimeEvents: false,
  /** `GET /payments/{id}`. */
  transactionById: false,
  /** Filtre `state` acceptant plusieurs valeurs. */
  multiStateFilter: false,
  /** Résolution nom ↔ numéro de téléphone. */
  beneficiaryLookup: false,
  /** Invalidation serveur des jetons. */
  logout: false,
  /** `GET /me`. */
  userProfile: false,
} as const;

export type ApiCapability = keyof typeof API_CAPABILITIES;

export function hasCapability(capability: ApiCapability): boolean {
  return API_CAPABILITIES[capability];
}
