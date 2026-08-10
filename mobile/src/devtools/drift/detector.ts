/**
 * Détecteur de dérive de contrat — `docs/10-validation-mode.md` §5.
 *
 * **Mécanisme central du rôle de harnais.** Le backend est en développement
 * actif ; `docs/01-api-contract.md` est un relevé daté. Ce module compare la
 * description vivante servie par `/v3/api-docs` aux attentes transcrites dans
 * `src/types/api.ts`, et nomme les écarts avant qu'un utilisateur ne les
 * rencontre sous forme de libellé manquant ou de champ `undefined`.
 *
 * Module **pur**, sans dépendance à React ni au réseau : il prend le document
 * OpenAPI déjà téléchargé et rend un rapport. C'est ce qui le rend testable
 * avec des documents fabriqués, y compris malformés.
 *
 * Aucun écart n'empêche l'app de fonctionner — le but est d'informer.
 */

import { DIRECTIONS, TX_STATES, TX_TYPES } from '@/types/domain';

// ──────────────────────────────────────────────── Vocabulaire du rapport ────

export type DriftSeverity = 'blocking' | 'warning' | 'info';

export type DriftCategory =
  | 'endpoint-missing'
  | 'required-field-missing'
  | 'field-type-changed'
  | 'endpoint-new'
  | 'field-new'
  | 'enum-value-new'
  | 'document-unreadable';

/** Table de gravité de `docs/10-validation-mode.md` §5, sans interprétation. */
const SEVERITY: Record<DriftCategory, DriftSeverity> = {
  'endpoint-missing': 'blocking',
  'required-field-missing': 'blocking',
  'field-type-changed': 'warning',
  'enum-value-new': 'warning',
  'endpoint-new': 'info',
  'field-new': 'info',
  'document-unreadable': 'blocking',
};

export const CATEGORY_LABELS: Record<DriftCategory, string> = {
  'endpoint-missing': 'Endpoint disparu',
  'required-field-missing': 'Champ obligatoire disparu',
  'field-type-changed': 'Type d’un champ modifié',
  'enum-value-new': 'Nouvelle valeur d’énumération',
  'endpoint-new': 'Nouvel endpoint',
  'field-new': 'Nouveau champ optionnel',
  'document-unreadable': 'Document illisible',
};

export interface DriftFinding {
  category: DriftCategory;
  severity: DriftSeverity;
  /** `POST /payments/transfer` — ou le nom du document quand il est global. */
  subject: string;
  /** Une phrase, lisible telle quelle dans le panneau. */
  message: string;
}

export interface DriftReport {
  findings: DriftFinding[];
  blocking: number;
  warning: number;
  info: number;
  /** Vrai quand rien ne diverge — l'app suit exactement le contrat servi. */
  clean: boolean;
}

// ───────────────────────────────────────────────────── Attentes locales ─────

export type ExpectedType = 'string' | 'number' | 'boolean' | 'array' | 'object';

export interface FieldExpectation {
  name: string;
  type: ExpectedType;
  /** Un champ obligatoire disparu est bloquant ; un optionnel ne l'est pas. */
  required: boolean;
  /** Valeurs connues localement — sert à repérer les ajouts côté serveur. */
  knownEnum?: readonly string[];
  /** Pour `array`, décrit les objets d'items ; pour `object`, ses propriétés. */
  fields?: FieldExpectation[];
}

export interface EndpointExpectation {
  method: 'get' | 'post';
  path: string;
  /**
   * Champs attendus dans la réponse `200`. Absent quand la réponse n'a pas de
   * corps structuré digne d'être comparé.
   */
  response?: FieldExpectation[];
}

const STATE_ENTRY_FIELDS: FieldExpectation[] = [
  { name: 'oldState', type: 'string', required: false, knownEnum: TX_STATES },
  { name: 'newState', type: 'string', required: true, knownEnum: TX_STATES },
  { name: 'occurredAt', type: 'string', required: true },
];

const TRANSACTION_ITEM_FIELDS: FieldExpectation[] = [
  { name: 'transactionId', type: 'string', required: true },
  { name: 'transactionNumber', type: 'string', required: true },
  { name: 'type', type: 'string', required: true, knownEnum: TX_TYPES },
  { name: 'direction', type: 'string', required: true, knownEnum: DIRECTIONS },
  { name: 'state', type: 'string', required: true, knownEnum: TX_STATES },
  { name: 'amount', type: 'number', required: true },
  { name: 'currency', type: 'string', required: true },
  { name: 'paymentMethod', type: 'string', required: true },
  { name: 'counterpart', type: 'string', required: false },
  { name: 'createdAt', type: 'string', required: true },
  {
    name: 'stateHistory',
    type: 'array',
    required: false,
    fields: STATE_ENTRY_FIELDS,
  },
];

const TOKENS_FIELDS: FieldExpectation[] = [
  { name: 'accessToken', type: 'string', required: true },
  { name: 'accessTokenExpiry', type: 'string', required: true },
  { name: 'refreshToken', type: 'string', required: true },
  { name: 'refreshTokenExpiry', type: 'string', required: true },
];

const TRANSACTION_RESPONSE_FIELDS: FieldExpectation[] = [
  { name: 'transactionId', type: 'string', required: true },
  { name: 'transactionNumber', type: 'string', required: true },
  { name: 'state', type: 'string', required: true, knownEnum: TX_STATES },
  { name: 'amount', type: 'number', required: true },
  { name: 'currency', type: 'string', required: true },
];

/**
 * Les neuf endpoints consommés par l'application — `docs/01-api-contract.md`.
 *
 * Rien d'autre n'y figure volontairement : `/test/**` ne doit jamais être
 * appelé depuis l'app, et son apparition en « nouvel endpoint » est une
 * information utile, pas une attente.
 */
export const EXPECTED_ENDPOINTS: readonly EndpointExpectation[] = [
  { method: 'post', path: '/auth/register', response: [{ name: 'message', type: 'string', required: true }] },
  { method: 'post', path: '/auth/login', response: [{ name: 'message', type: 'string', required: true }] },
  { method: 'post', path: '/auth/verify-otp', response: TOKENS_FIELDS },
  { method: 'post', path: '/auth/refresh', response: TOKENS_FIELDS },
  {
    method: 'get',
    path: '/payments/balance',
    response: [
      { name: 'accountId', type: 'string', required: true },
      { name: 'accountNumber', type: 'string', required: true },
      { name: 'amount', type: 'number', required: true },
      { name: 'currency', type: 'string', required: true },
    ],
  },
  { method: 'post', path: '/payments/cash-in', response: TRANSACTION_RESPONSE_FIELDS },
  { method: 'post', path: '/payments/cash-out', response: TRANSACTION_RESPONSE_FIELDS },
  { method: 'post', path: '/payments/transfer', response: TRANSACTION_RESPONSE_FIELDS },
  {
    method: 'get',
    path: '/payments/history',
    response: [
      { name: 'transactions', type: 'array', required: true, fields: TRANSACTION_ITEM_FIELDS },
      { name: 'page', type: 'number', required: true },
      { name: 'size', type: 'number', required: true },
      { name: 'totalElements', type: 'number', required: true },
      { name: 'totalPages', type: 'number', required: true },
      { name: 'hasNext', type: 'boolean', required: true },
    ],
  },
];

// ─────────────────────────────────────── Lecture tolérante du document ──────

type JsonObject = Record<string, unknown>;

function asObject(value: unknown): JsonObject | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as JsonObject)
    : null;
}

function asStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

/**
 * Résolution d'un `$ref` local.
 *
 * springdoc nomme ses schémas d'après les classes Java : `TransactionResponse`,
 * `PageTransactionItem`… Deviner ces noms serait fragile. On suit donc la
 * référence déclarée dans la réponse plutôt que de chercher un nom attendu.
 *
 * Le suivi est borné : un `$ref` cyclique ne doit pas figer le panneau.
 */
const MAX_REF_HOPS = 8;

function resolveSchema(schema: unknown, doc: JsonObject, hops = 0): JsonObject | null {
  const node = asObject(schema);
  if (!node) return null;

  const ref = node.$ref;
  if (typeof ref !== 'string') return node;
  if (hops >= MAX_REF_HOPS) return null;

  const segments = ref.replace(/^#\//, '').split('/');
  let current: unknown = doc;
  for (const segment of segments) {
    const container = asObject(current);
    if (!container) return null;
    current = container[segment];
  }
  return resolveSchema(current, doc, hops + 1);
}

/** Schéma de la réponse `200` en `application/json`, `$ref` suivi. */
function responseSchema(operation: JsonObject, doc: JsonObject): JsonObject | null {
  const responses = asObject(operation.responses);
  if (!responses) return null;

  const ok = asObject(responses['200']) ?? asObject(responses['201']);
  if (!ok) return null;

  const content = asObject(ok.content);
  if (!content) return null;

  const json = asObject(content['application/json']) ?? asObject(content['*/*']);
  if (!json) return null;

  return resolveSchema(json.schema, doc);
}

/** `integer` et `number` sont le même type pour nous ; `format` est ignoré. */
function matchesType(serverType: string, expected: ExpectedType): boolean {
  if (expected === 'number') return serverType === 'number' || serverType === 'integer';
  return serverType === expected;
}

function schemaType(schema: JsonObject): string | null {
  const declared = schema.type;
  if (typeof declared === 'string') return declared;
  // Un schéma sans `type` mais avec des propriétés est un objet — springdoc
  // l'omet parfois sur les compositions.
  if (asObject(schema.properties)) return 'object';
  return null;
}

// ─────────────────────────────────────────────────────────── Comparaison ────

function compareFields(
  expected: FieldExpectation[],
  schema: JsonObject,
  doc: JsonObject,
  subject: string,
  pathPrefix: string,
  findings: DriftFinding[],
): void {
  const properties = asObject(schema.properties);
  if (!properties) return;

  const required = new Set(asStringArray(schema.required));
  const seen = new Set<string>();

  for (const field of expected) {
    seen.add(field.name);
    const label = `${pathPrefix}${field.name}`;
    const raw = properties[field.name];

    if (raw === undefined) {
      // Le serveur peut déclarer un champ obligatoire sans le décrire ; le
      // signal utile est l'absence de la propriété, pas celle du `required`.
      if (field.required) {
        push(findings, 'required-field-missing', subject, `\`${label}\` n’est plus décrit par le serveur.`);
      }
      continue;
    }

    const resolved = resolveSchema(raw, doc);
    if (!resolved) continue;

    const serverType = schemaType(resolved);
    if (serverType && !matchesType(serverType, field.type)) {
      push(
        findings,
        'field-type-changed',
        subject,
        `\`${label}\` est désormais \`${serverType}\`, attendu \`${field.type}\`.`,
      );
      continue;
    }

    if (field.knownEnum) {
      compareEnum(resolved, field.knownEnum, subject, label, findings);
    }

    if (field.fields) {
      const target =
        field.type === 'array' ? resolveSchema(resolved.items, doc) : resolved;
      if (target) {
        compareFields(field.fields, target, doc, subject, `${label}[].`, findings);
      }
    }

    // Un champ obligatoire chez nous devenu optionnel côté serveur reste un
    // changement de contrat, mais il n'est pas listé par le §5 : le rendre
    // bloquant sur-signalerait. Il ressort de toute façon en `undefined` à
    // l'exécution, et les schémas Zod le tolèrent (règle R2).
  }

  for (const name of Object.keys(properties)) {
    if (seen.has(name)) continue;
    const optional = !required.has(name);
    push(
      findings,
      'field-new',
      subject,
      `\`${pathPrefix}${name}\` est apparu${optional ? '' : ' (déclaré obligatoire)'} côté serveur.`,
    );
  }
}

/**
 * Le cas le plus précieux du §5 : un nouvel état de transaction apparaît dans
 * l'énumération serveur, et l'app le signale **avant** qu'un utilisateur ne
 * rencontre un libellé manquant.
 */
function compareEnum(
  schema: JsonObject,
  known: readonly string[],
  subject: string,
  label: string,
  findings: DriftFinding[],
): void {
  const values = asStringArray(schema.enum);
  if (values.length === 0) return;

  const added = values.filter((value) => !known.includes(value));
  if (added.length === 0) return;

  push(
    findings,
    'enum-value-new',
    subject,
    `\`${label}\` accepte ${added.length > 1 ? 'de nouvelles valeurs' : 'une nouvelle valeur'} : ${added
      .map((value) => `\`${value}\``)
      .join(', ')}.`,
  );
}

function push(
  findings: DriftFinding[],
  category: DriftCategory,
  subject: string,
  message: string,
): void {
  findings.push({ category, severity: SEVERITY[category], subject, message });
}

const SEVERITY_ORDER: Record<DriftSeverity, number> = { blocking: 0, warning: 1, info: 2 };

// ────────────────────────────────────────────────────────────── Entrée ──────

/**
 * Compare un document OpenAPI aux attentes locales.
 *
 * `document` est délibérément typé `unknown` : c'est du JSON réseau, et un
 * document malformé doit produire un constat, pas une exception.
 */
export function detectDrift(
  document: unknown,
  expected: readonly EndpointExpectation[] = EXPECTED_ENDPOINTS,
): DriftReport {
  const findings: DriftFinding[] = [];
  const doc = asObject(document);
  const paths = doc ? asObject(doc.paths) : null;

  if (!doc || !paths) {
    push(
      findings,
      'document-unreadable',
      '/v3/api-docs',
      'Le document ne contient aucun objet `paths` exploitable.',
    );
    return summarize(findings);
  }

  const covered = new Set<string>();

  for (const endpoint of expected) {
    const subject = `${endpoint.method.toUpperCase()} ${endpoint.path}`;
    covered.add(`${endpoint.method} ${endpoint.path}`);

    const item = asObject(paths[endpoint.path]);
    const operation = item ? asObject(item[endpoint.method]) : null;

    if (!operation) {
      push(findings, 'endpoint-missing', subject, 'L’endpoint n’existe plus dans le document servi.');
      continue;
    }

    if (!endpoint.response) continue;

    const schema = responseSchema(operation, doc);
    if (!schema) {
      push(
        findings,
        'field-type-changed',
        subject,
        'La réponse 200 ne décrit plus de corps JSON exploitable.',
      );
      continue;
    }

    compareFields(endpoint.response, schema, doc, subject, '', findings);
  }

  for (const [path, item] of Object.entries(paths)) {
    const operations = asObject(item);
    if (!operations) continue;
    for (const method of Object.keys(operations)) {
      if (method !== 'get' && method !== 'post' && method !== 'put' && method !== 'delete' && method !== 'patch') {
        continue;
      }
      if (covered.has(`${method} ${path}`)) continue;
      push(
        findings,
        'endpoint-new',
        `${method.toUpperCase()} ${path}`,
        'Endpoint présent côté serveur, inconnu de l’application.',
      );
    }
  }

  return summarize(findings);
}

function summarize(findings: DriftFinding[]): DriftReport {
  const sorted = [...findings].sort(
    (a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity],
  );
  const count = (severity: DriftSeverity) =>
    sorted.filter((finding) => finding.severity === severity).length;

  return {
    findings: sorted,
    blocking: count('blocking'),
    warning: count('warning'),
    info: count('info'),
    clean: sorted.length === 0,
  };
}
