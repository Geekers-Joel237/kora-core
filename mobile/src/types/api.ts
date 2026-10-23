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
  /** `\+\d{1,4}`, ex. `+225`. */
  toPhonePrefix: string;
  /** `\d{8,15}`, **sans** le préfixe : `0708091011`. */
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

/** Ligne de `GET /payments/history` — vue client, aucun état technique. Contrat §2. */
export interface ApiTransactionItem {
  /** `TRX-yyyyMMdd-<8 hex>` — identité de l'opération côté client. */
  reference: string;
  /** `DEPOSIT` · `WITHDRAWAL` · `TRANSFER_SENT` · `TRANSFER_RECEIVED`, lu du côté de l'appelant. */
  kind: string;
  /** `PENDING` · `COMPLETED` · `FAILED` · `REFUNDED` — contrat §3.1. */
  status: string;
  /** Signé : négatif quand l'argent sort du wallet. */
  amount: number;
  currency: string;
  /** Déjà masqué par le serveur. Transferts uniquement, sinon `null`. */
  counterpart: string | null;
  /** Opérateur Mobile Money. Dépôt et retrait uniquement, sinon `null`. */
  channel: string | null;
  /** Instant ISO-8601 UTC. */
  date: string;
}

export interface ApiTransactionHistoryResponse {
  items: ApiTransactionItem[];
  /** Opaque. `null` sur la dernière page. */
  nextCursor: string | null;
}

/** Paramètres de `GET /payments/history` — contrat §2. */
export interface ApiHistoryQuery {
  status?: string;
  kind?: string;
  /** Jour `YYYY-MM-DD`, inclus. */
  from?: string;
  /** Jour `YYYY-MM-DD`, inclus. */
  to?: string;
  /** `nextCursor` de la page précédente, tel quel. */
  cursor?: string;
  /** 1 à 100 : au-delà, `400`. */
  limit?: number;
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
