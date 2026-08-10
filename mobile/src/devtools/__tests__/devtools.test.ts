import {
  detectDrift,
  EXPECTED_ENDPOINTS,
  type EndpointExpectation,
  type FieldExpectation,
} from '@/devtools/drift/detector';
import { toMarkdown, type JournalEntry } from '@/devtools/journal/store';
import {
  formatDuration,
  interStateDurations,
  totalDuration,
} from '@/devtools/transactions/durations';
import type { StateTransition } from '@/types/domain';

// ───────────────────────────────────────── Fabrication d'un `/v3/api-docs` ──

type Json = Record<string, unknown>;

function fieldSchema(field: FieldExpectation): Json {
  if (field.type === 'array') {
    return { type: 'array', items: field.fields ? objectSchema(field.fields) : { type: 'string' } };
  }
  if (field.fields) return objectSchema(field.fields);
  return { type: field.type, ...(field.knownEnum && { enum: [...field.knownEnum] }) };
}

function objectSchema(fields: FieldExpectation[]): Json {
  return {
    type: 'object',
    properties: Object.fromEntries(fields.map((field) => [field.name, fieldSchema(field)])),
    required: fields.filter((field) => field.required).map((field) => field.name),
  };
}

/**
 * Document conforme, construit depuis la table d'attentes elle-même.
 *
 * Le but n'est pas de vérifier que le détecteur se compare à lui-même, mais de
 * disposer d'une **base sans écart** que chaque test mute d'une seule façon :
 * un rapport à une entrée prouve alors que la mutation, et elle seule, a été
 * détectée. Les schémas passent par `components.schemas`, comme springdoc les
 * produit, ce qui exerce la résolution de `$ref`.
 */
function buildDoc(endpoints: readonly EndpointExpectation[] = EXPECTED_ENDPOINTS): Json {
  const schemas: Json = {};
  const paths: Json = {};

  endpoints.forEach((endpoint, index) => {
    const name = `Schema${index}`;
    schemas[name] = endpoint.response ? objectSchema(endpoint.response) : { type: 'object' };
    paths[endpoint.path] = {
      ...(paths[endpoint.path] as Json | undefined),
      [endpoint.method]: {
        responses: {
          '200': {
            content: { 'application/json': { schema: { $ref: `#/components/schemas/${name}` } } },
          },
        },
      },
    };
  });

  return { openapi: '3.0.1', paths, components: { schemas } };
}

function schemaFor(doc: Json, path: string, method: string): Json {
  const paths = doc.paths as Json;
  const operation = (paths[path] as Json)[method] as Json;
  const responses = operation.responses as Json;
  const ref = (((responses['200'] as Json).content as Json)['application/json'] as Json)
    .schema as Json;
  const name = String(ref.$ref).split('/').pop() as string;
  return ((doc.components as Json).schemas as Json)[name] as Json;
}

describe('détecteur de dérive — docs/10-validation-mode.md §5', () => {
  it('ne signale rien face à un document conforme', () => {
    const report = detectDrift(buildDoc());
    expect(report.findings).toEqual([]);
    expect(report.clean).toBe(true);
  });

  it('signale un endpoint disparu comme bloquant', () => {
    const doc = buildDoc();
    delete (doc.paths as Json)['/payments/history'];

    const report = detectDrift(doc);
    const finding = report.findings.find((item) => item.category === 'endpoint-missing');

    expect(finding?.severity).toBe('blocking');
    expect(finding?.subject).toBe('GET /payments/history');
  });

  it('signale un champ obligatoire disparu comme bloquant', () => {
    const doc = buildDoc();
    const schema = schemaFor(doc, '/payments/balance', 'get');
    delete (schema.properties as Json).amount;

    const report = detectDrift(doc);

    expect(report.blocking).toBe(1);
    expect(report.findings[0]?.category).toBe('required-field-missing');
    expect(report.findings[0]?.message).toContain('amount');
  });

  it('signale un changement de type en avertissement', () => {
    const doc = buildDoc();
    const schema = schemaFor(doc, '/payments/balance', 'get');
    (schema.properties as Json).amount = { type: 'string' };

    const report = detectDrift(doc);

    expect(report.warning).toBe(1);
    expect(report.findings[0]?.category).toBe('field-type-changed');
  });

  it('accepte `integer` là où l’application attend un nombre', () => {
    const doc = buildDoc();
    const schema = schemaFor(doc, '/payments/balance', 'get');
    (schema.properties as Json).amount = { type: 'integer', format: 'int64' };

    expect(detectDrift(doc).clean).toBe(true);
  });

  it('signale une nouvelle valeur d’énumération — le cas le plus précieux', () => {
    const doc = buildDoc();
    const schema = schemaFor(doc, '/payments/history', 'get');
    const items = (schema.properties as Json).transactions as Json;
    const item = items.items as Json;
    (item.properties as Json).state = {
      type: 'string',
      enum: ['COMPLETED', 'REVERSED', 'PARTIALLY_REFUNDED'],
    };

    const report = detectDrift(doc);
    const finding = report.findings.find((item2) => item2.category === 'enum-value-new');

    expect(finding?.severity).toBe('warning');
    expect(finding?.message).toContain('PARTIALLY_REFUNDED');
    // Les valeurs déjà connues ne produisent aucun bruit.
    expect(finding?.message).not.toContain('COMPLETED');
  });

  it('descend dans les objets imbriqués de `stateHistory`', () => {
    const doc = buildDoc();
    const schema = schemaFor(doc, '/payments/history', 'get');
    const item = ((schema.properties as Json).transactions as Json).items as Json;
    const history = (item.properties as Json).stateHistory as Json;
    delete ((history.items as Json).properties as Json).newState;

    const report = detectDrift(doc);
    const finding = report.findings.find((entry) => entry.category === 'required-field-missing');

    expect(finding?.message).toContain('stateHistory[].newState');
  });

  it('signale un endpoint inconnu en information', () => {
    const doc = buildDoc();
    (doc.paths as Json)['/test/reset'] = { post: { responses: {} } };

    const report = detectDrift(doc);
    const finding = report.findings.find((item) => item.category === 'endpoint-new');

    expect(finding?.severity).toBe('info');
    expect(finding?.subject).toBe('POST /test/reset');
  });

  it('signale un nouveau champ en information', () => {
    const doc = buildDoc();
    const schema = schemaFor(doc, '/payments/balance', 'get');
    (schema.properties as Json).overdraftLimit = { type: 'number' };

    const report = detectDrift(doc);

    expect(report.info).toBe(1);
    expect(report.findings[0]?.category).toBe('field-new');
  });

  it('classe les écarts du plus grave au moins grave', () => {
    const doc = buildDoc();
    (doc.paths as Json)['/test/reset'] = { post: { responses: {} } };
    delete (doc.paths as Json)['/payments/history'];

    const severities = detectDrift(doc).findings.map((finding) => finding.severity);
    expect(severities[0]).toBe('blocking');
    expect(severities[severities.length - 1]).toBe('info');
  });

  it('constate un document illisible sans lever', () => {
    for (const payload of [null, undefined, 'nope', 42, {}, { paths: 'nope' }]) {
      const report = detectDrift(payload);
      expect(report.findings[0]?.category).toBe('document-unreadable');
      expect(report.blocking).toBe(1);
    }
  });
});

// ──────────────────────────────────────────────────── Durées inter-états ────

function transition(to: string, offsetMs: number, from: string | null = null): StateTransition {
  return { from, to, occurredAt: new Date(1_800_000_000_000 + offsetMs) };
}

describe('durées entre états — docs/10-validation-mode.md §4', () => {
  it('calcule le delta entre chaque transition', () => {
    const durations = interStateDurations([
      transition('INITIALIZED', 0),
      transition('AUTHORIZED', 200, 'INITIALIZED'),
      transition('CAPTURED', 350, 'AUTHORIZED'),
    ]);

    expect(durations).toEqual([
      { from: 'INITIALIZED', to: 'AUTHORIZED', ms: 200 },
      { from: 'AUTHORIZED', to: 'CAPTURED', ms: 150 },
    ]);
  });

  it('trie avant de soustraire — aucun delta négatif', () => {
    const durations = interStateDurations([
      transition('CAPTURED', 350),
      transition('INITIALIZED', 0),
      transition('AUTHORIZED', 200),
    ]);

    expect(durations.every((duration) => duration.ms >= 0)).toBe(true);
    expect(durations[0]?.to).toBe('AUTHORIZED');
  });

  it('ne produit rien sur une frise d’un seul état', () => {
    expect(interStateDurations([transition('INITIALIZED', 0)])).toEqual([]);
    expect(interStateDurations([])).toEqual([]);
    expect(totalDuration([transition('INITIALIZED', 0)])).toBe(0);
  });

  it('mesure la durée totale du parcours', () => {
    expect(
      totalDuration([transition('INITIALIZED', 0), transition('COMPLETED', 4200)]),
    ).toBe(4200);
  });

  it('formate sans conversion mentale', () => {
    expect(formatDuration(842)).toBe('842 ms');
    expect(formatDuration(2400)).toBe('2,4 s');
    expect(formatDuration(65_000)).toBe('1 min 05 s');
  });
});

// ────────────────────────────────────────────────────── Journal Markdown ────

const ENTRY: JournalEntry = {
  id: 'e1',
  at: '2026-08-06T14:22:00.000Z',
  title: 'Transfert 25 000 XOF, réseau coupé à 800 ms',
  scenario: 2,
  correlationId: 'a3f2-0000',
  observed: 'Aucune réponse client. Transaction COMPLETED 4 s plus tard.',
  conclusion: 'L’écran « issue incertaine » est le bon comportement.',
  status: 'expected',
};

describe('export Markdown du journal — docs/10-validation-mode.md §9', () => {
  it('produit un tableau de synthèse et le détail', () => {
    const markdown = toMarkdown([ENTRY], new Date('2026-08-06T15:00:00.000Z'));

    expect(markdown).toContain('# Journal de validation Kora');
    expect(markdown).toContain('| Date | Scénario | Observation | Statut |');
    expect(markdown).toContain('#2');
    expect(markdown).toContain('✅ conforme à l’attendu');
    expect(markdown).toContain('`a3f2-0000`');
    expect(markdown).toContain('**Conclusion**');
  });

  it('reste lisible sans observation', () => {
    const markdown = toMarkdown([], new Date('2026-08-06T15:00:00.000Z'));
    expect(markdown).toContain('_Aucune observation consignée._');
  });

  it('omet les champs facultatifs absents', () => {
    const markdown = toMarkdown(
      [{ ...ENTRY, scenario: null, correlationId: null }],
      new Date('2026-08-06T15:00:00.000Z'),
    );

    expect(markdown).not.toContain('**Corrélation**');
    expect(markdown).toContain('| — |');
  });
});
