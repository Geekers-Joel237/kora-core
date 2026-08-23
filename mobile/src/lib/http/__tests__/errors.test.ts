import { isTokenExpiry, normalizeHttpError, normalizeTransportError } from '../errors';

const READ = { path: '/payments/history', isMoneyMovement: false };
const MONEY = { path: '/payments/transfer', isMoneyMovement: true };

describe('isTokenExpiry — contrat §5.2, le point le plus délicat du socle', () => {
  it('traite { status, error } comme un jeton expiré → rafraîchissement', () => {
    expect(isTokenExpiry(401, { status: 401, error: 'Unauthorized' })).toBe(true);
  });

  it('traite un ProblemDetail « Invalid PIN » comme un PIN erroné → AUCUN rafraîchissement', () => {
    expect(isTokenExpiry(401, { status: 401, detail: 'Invalid PIN' })).toBe(false);
  });

  it('traite un ProblemDetail d’OTP comme un OTP erroné → AUCUN rafraîchissement', () => {
    expect(isTokenExpiry(401, { status: 401, detail: 'OTP code does not match' })).toBe(false);
  });

  it('ne déclenche jamais de rafraîchissement hors 401', () => {
    expect(isTokenExpiry(403, { status: 403, error: 'Forbidden' })).toBe(false);
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

  it('format 2 — couche de sécurité Spring', () => {
    const error = normalizeHttpError(401, { status: 401, error: 'Unauthorized' }, READ);
    expect(error.code).toBe('TOKEN_EXPIRED');
    expect(error.isAuthExpired).toBe(true);
  });

  it('format 3 — corps Spring par défaut sur ProviderException non mappée', () => {
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
    const pin = normalizeHttpError(401, { status: 401, detail: 'Invalid PIN' }, MONEY);
    expect(pin.code).toBe('INVALID_PIN');
    expect(pin.isAuthExpired).toBe(false);
    expect(pin.isOutcomeUnknown).toBe(false);

    const token = normalizeHttpError(401, { status: 401, error: 'Unauthorized' }, MONEY);
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
