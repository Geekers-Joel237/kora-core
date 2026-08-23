/**
 * DTO bruts du backend `kora-core`.
 *
 * Transcription littérale de `docs/01-api-contract.md`. Rien n'est inventé ici :
 * si un champ n'apparaît pas dans le contrat, il n'existe pas.
 *
 * ⚠️ Règle R1 (`docs/09-api-evolution.md` §5) — aucun de ces types ne doit
 * franchir la frontière de `src/features/<domaine>/api.ts`. Les écrans et les
 * hooks ne manipulent que les types de `src/types/domain.ts`.
 */

// ─────────────────────────────────────────────────────────────── Auth ───────

export interface ApiRegisterRequest {
  fullName: string;
  email: string;
  phonePrefix: string;
  phoneNumber: string;
  rawPin: string;
}

export interface ApiLoginRequest {
  email: string;
  rawPin: string;
}

export interface ApiVerifyOtpRequest {
  email: string;
  code: string;
}

export interface ApiRefreshRequest {
  refreshToken: string;
}

/** Réponse de `/auth/register` et `/auth/login`. Ne contient AUCUN jeton. */
export interface ApiOtpResponse {
  message: string;
}

export interface ApiTokensResponse {
  accessToken: string;
  /** Instant ISO-8601 UTC. */
  accessTokenExpiry: string;
  refreshToken: string;
  /** Instant ISO-8601 UTC. */
  refreshTokenExpiry: string;
}

/** Claims du jeton d'accès — contrat §1. Seule source de profil côté serveur. */
export interface ApiAccessTokenClaims {
  sub: string;
  jti?: string;
  email?: string;
  role?: string;
  iat?: number;
  exp?: number;
}

// ─────────────────────────────────────────────────────────── Paiements ──────

export interface ApiCashRequest {
  rawPin: string;
  amount: number;
  currency: string;
  paymentMethod: string;
}

export interface ApiTransferRequest {
  rawPin: string;
  amount: number;
  currency: string;
  /** Numéro complet, préfixe inclus, sans séparateur : `+2250708091011`. */
  toPhoneNumber: string;
}

export interface ApiBalanceResponse {
  accountId: string;
  /** Format `ACC-yyyyMMdd-<8 hex majuscules>`. */
  accountNumber: string;
  /** `BigDecimal` sérialisé en nombre JSON. Aucune échelle garantie — contrat §5.3. */
  amount: number;
  currency: string;
}

export interface ApiTransactionResponse {
  transactionId: string;
  /** Format `TRX-yyyyMMdd-<8 hex majuscules>`. */
  transactionNumber: string;
  state: string;
  amount: number;
  currency: string;
}

export interface ApiStateEntry {
  /** `null` sur la toute première transition. */
  oldState: string | null;
  newState: string;
  occurredAt: string;
}

export interface ApiTransactionItem {
  transactionId: string;
  transactionNumber: string;
  type: string;
  direction: string;
  state: string;
  amount: number;
  currency: string;
  paymentMethod: string;
  /** Déjà masqué par le serveur. `null` pour CASH_IN et CASH_OUT. */
  counterpart: string | null;
  createdAt: string;
  /** `null` quand `detail=false`. Absent si le serveur omet le champ. */
  stateHistory?: ApiStateEntry[] | null;
}

export interface ApiTransactionHistoryResponse {
  transactions: ApiTransactionItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

/** Paramètres de `GET /payments/history` — contrat §2. */
export interface ApiHistoryQuery {
  type?: string;
  state?: string;
  direction?: string;
  /** Instant ISO-8601 UTC. */
  from?: string;
  /** Instant ISO-8601 UTC. */
  to?: string;
  page?: number;
  /** Plafonné à 100 côté serveur : au-delà, `400`. */
  size?: number;
  detail?: boolean;
}

// ────────────────────────────────────────────────────── Formats d'erreur ────

/** Format 1 — `ProblemDetail` RFC 7807, produit par le `GlobalExceptionHandler`. */
export interface ApiProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  violations?: ApiViolation[];
}

export interface ApiViolation {
  field: string;
  message: string;
}

/** Format 2 — couche de sécurité Spring. Non conforme à RFC 7807. */
export interface ApiSecurityError {
  status: number;
  error: string;
}

/** Format 3 — corps d'erreur Spring par défaut, sur exception non mappée. */
export interface ApiSpringDefaultError {
  timestamp?: string;
  status: number;
  error?: string;
  path?: string;
}
