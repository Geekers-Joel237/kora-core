import { isTokenExpiry, normalizeHttpError, normalizeTransportError } from '../errors';

const READ = { path: '/payments/history', isMoneyMovement: false };
const MONEY = { path: '/payments/transfer', isMoneyMovement: true };

describe('isTokenExpiry — contrat §5.2, le point le plus délicat du socle', () => {
  it('traite le code UNAUTHENTICATED comme un jeton expiré → rafraîchissement', () => {
    expect(isTokenExpiry(401, { status: 401, code: 'UNAUTHENTICATED', detail: 'A valid access token is required' })).toBe(true);
  });

  it('traite le code INVALID_PIN comme un PIN erroné → AUCUN rafraîchissement', () => {
    expect(isTokenExpiry(401, { status: 401, code: 'INVALID_PIN', detail: 'Invalid PIN' })).toBe(false);
  });

  it('traite le code INVALID_OTP comme un OTP erroné → AUCUN rafraîchissement', () => {
    expect(isTokenExpiry(401, { status: 401, code: 'INVALID_OTP', detail: 'OTP code does not match' })).toBe(false);
  });

  it('ne déclenche jamais de rafraîchissement hors 401', () => {
    expect(isTokenExpiry(403, { status: 403, code: 'FORBIDDEN' })).toBe(false);
    expect(isTokenExpiry(422, { detail: 'Insufficient funds' })).toBe(false);
    expect(isTokenExpiry(500, null)).toBe(false);
  });

  it('traite un 401 au corps vide comme un jeton expiré', () => {
    expect(isTokenExpiry(401, null)).toBe(true);
    expect(isTokenExpiry(401, '')).toBe(true);
  });
});

describe('normalizeHttpError — les trois formats du contrat §5.1', () => {
  it('format 1 — ProblemDetail avec violations', () => {
    const error = normalizeHttpError(
      400,
      {
        status: 400,
        detail: 'Request validation failed',
        violations: [{ field: 'phoneNumber', message: 'Phone number must contain 8 to 15 digits' }],
      },
      READ,
    );
    expect(error.code).toBe('VALIDATION');
    expect(error.violations).toHaveLength(1);
    expect(error.violations?.[0]?.field).toBe('phoneNumber');
    expect(error.isAuthExpired).toBe(false);
  });

  it('un 401 de la couche de sécurité est un ProblemDetail UNAUTHENTICATED', () => {
    const error = normalizeHttpError(
      401,
      { status: 401, code: 'UNAUTHENTICATED', detail: 'A valid access token is required' },
      READ,
      'application/problem+json',
    );
    expect(error.code).toBe('TOKEN_EXPIRED');
    expect(error.isAuthExpired).toBe(true);
  });

  it('format 3 — corps Spring par défaut sur une exception non mappée', () => {
    const error = normalizeHttpError(
      500,
      { timestamp: '2026-08-06T11:42:13.401+00:00', status: 500, error: 'Internal Server Error' },
      MONEY,
    );
    expect(error.code).toBe('SERVER');
    expect(error.isOutcomeUnknown).toBe(true);
    expect(error.isRetryable).toBe(false);
  });

  it('encaisse un corps non-JSON sans planter', () => {
    const error = normalizeHttpError(502, '<html>Bad Gateway</html>', READ);
    expect(error.code).toBe('SERVER');
    expect(error.status).toBe(502);
  });

  it('distingue un 401 de PIN d’un 401 de jeton', () => {
    const pin = normalizeHttpError(401, { status: 401, code: 'INVALID_PIN', detail: 'Invalid PIN' }, MONEY);
    expect(pin.code).toBe('INVALID_PIN');
    expect(pin.isAuthExpired).toBe(false);
    expect(pin.isOutcomeUnknown).toBe(false);

    const token = normalizeHttpError(401, { status: 401, code: 'UNAUTHENTICATED' }, MONEY);
    expect(token.code).toBe('TOKEN_EXPIRED');
    expect(token.isAuthExpired).toBe(true);
  });

  it('classe un destinataire inconnu en 404, pas en 422 — contrat §2', () => {
    const error = normalizeHttpError(
      404,
      { status: 404, detail: 'No account found for phone: +2250700000000' },
      MONEY,
    );
    expect(error.code).toBe('NOT_FOUND');
    expect(error.isOutcomeUnknown).toBe(false);
  });

  it('classe un 422 métier sans jamais le rendre incertain', () => {
    const error = normalizeHttpError(422, { status: 422, detail: 'Insufficient funds' }, MONEY);
    expect(error.code).toBe('BUSINESS_RULE');
    expect(error.isOutcomeUnknown).toBe(false);
    expect(error.isRetryable).toBe(false);
  });
});

describe('réponses classées comme le contrat Swagger les déclare — api-contract/payments.openapi.json', () => {
  const PROBLEM = 'application/problem+json';

  it('traite un 502 déclaré comme un refus du provider : échec affirmé, jamais incertain', () => {
    const error = normalizeHttpError(
      502,
      { title: 'Bad Gateway', status: 502, detail: 'Provider refused the debit', instance: '/payments/cash-out' },
      MONEY,
      PROBLEM,
    );
    expect(error.code).toBe('PROVIDER_REFUSED');
    expect(error.isOutcomeUnknown).toBe(false);
    expect(error.isRetryable).toBe(false);
  });

  it('reconnaît le 502 déclaré même avec un paramètre de charset', () => {
    const error = normalizeHttpError(502, { status: 502, detail: 'refused' }, MONEY, 'application/problem+json; charset=utf-8');
    expect(error.code).toBe('PROVIDER_REFUSED');
  });

  it('laisse incertain un 502 de passerelle, que le contrat ne déclare pas', () => {
    const error = normalizeHttpError(502, '<html>Bad Gateway</html>', MONEY, 'text/html');
    expect(error.code).toBe('SERVER');
    expect(error.isOutcomeUnknown).toBe(true);
  });

  it('laisse incertain un 502 sans type de contenu, même si son corps ressemble à un ProblemDetail', () => {
    const error = normalizeHttpError(502, { status: 502, detail: 'refused' }, MONEY);
    expect(error.code).toBe('SERVER');
    expect(error.isOutcomeUnknown).toBe(true);
  });

  it('laisse incertain un 503 déclaré : les reprises ont pu s’épuiser après la réponse du provider', () => {
    const error = normalizeHttpError(503, { status: 503, detail: 'Retries exhausted' }, MONEY, PROBLEM);
    expect(error.code).toBe('UNAVAILABLE');
    expect(error.isOutcomeUnknown).toBe(true);
  });

  it('laisse incertaine toute autre réponse 5xx sur un paiement', () => {
    expect(normalizeHttpError(504, null, MONEY).isOutcomeUnknown).toBe(true);
    expect(normalizeHttpError(500, { status: 500 }, MONEY, 'application/json').isOutcomeUnknown).toBe(true);
  });

  it('distingue deux 401 problem+json par leur code, jamais par leur type de contenu', () => {
    const pin = normalizeHttpError(401, { status: 401, code: 'INVALID_PIN', detail: 'Invalid PIN' }, MONEY, PROBLEM);
    expect(pin.code).toBe('INVALID_PIN');
    expect(pin.isAuthExpired).toBe(false);

    const token = normalizeHttpError(401, { status: 401, code: 'UNAUTHENTICATED' }, MONEY, PROBLEM);
    expect(token.code).toBe('TOKEN_EXPIRED');
    expect(token.isAuthExpired).toBe(true);
  });

  it('classe un OTP expiré comme un OTP erroné, sans rafraîchir', () => {
    const error = normalizeHttpError(401, { status: 401, code: 'OTP_EXPIRED' }, READ, PROBLEM);
    expect(error.code).toBe('INVALID_OTP');
    expect(error.isAuthExpired).toBe(false);
  });

  it('expose le code du problème et l’identifiant de corrélation pour le support', () => {
    const error = normalizeHttpError(
      422,
      { status: 422, code: 'INSUFFICIENT_FUNDS', detail: 'Insufficient funds', correlationId: 'corr-001' },
      MONEY,
      PROBLEM,
    );
    expect(error.problemCode).toBe('INSUFFICIENT_FUNDS');
    expect(error.correlationId).toBe('corr-001');
  });
});

describe('politique de reprise — architecture §3.5', () => {
  it('rend une lecture reprenable sur 503', () => {
    expect(normalizeHttpError(503, { status: 503 }, READ).isRetryable).toBe(true);
  });

  it('n’autorise JAMAIS la reprise d’une écriture monétaire', () => {
    expect(normalizeHttpError(503, { status: 503 }, MONEY).isRetryable).toBe(false);
    expect(normalizeHttpError(500, { status: 500 }, MONEY).isRetryable).toBe(false);
    expect(normalizeTransportError(new Error('network'), MONEY).isRetryable).toBe(false);
  });

  it('rend l’issue incertaine sur un 503 de paiement', () => {
    expect(normalizeHttpError(503, { status: 503 }, MONEY).isOutcomeUnknown).toBe(true);
  });

  it('rend l’issue incertaine quand aucune réponse n’arrive sur un paiement', () => {
    const error = normalizeTransportError(new Error('network down'), MONEY);
    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK');
    expect(error.isOutcomeUnknown).toBe(true);
  });

  it('ne rend jamais une lecture incertaine', () => {
    expect(normalizeTransportError(new Error('network down'), READ).isOutcomeUnknown).toBe(false);
  });

  it('distingue un délai dépassé d’une coupure réseau', () => {
    const error = normalizeTransportError(new Error('aborted'), { ...MONEY, timedOut: true });
    expect(error.code).toBe('TIMEOUT');
    expect(error.isOutcomeUnknown).toBe(true);
  });
});
