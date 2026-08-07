import {
  addMoney,
  formatMinor,
  formatMinorToString,
  MINUS_SIGN,
  subtractMoney,
  THIN_NBSP,
  toApiAmount,
  toMinor,
  setScaleAnomalyListener,
} from '../index';

describe('formatMinor — design system §3.4', () => {
  it('rend le cas de référence du plan de lot 1', () => {
    expect(formatMinor(125000, 'XOF')).toEqual({
      sign: '',
      integer: `125${THIN_NBSP}000`,
      fraction: null,
      symbol: 'F',
    });
  });

  it('utilise une espace fine insécable U+202F, pas une espace normale', () => {
    const { integer } = formatMinor(125000, 'XOF');
    expect(integer).toContain(' ');
    expect(integer).not.toContain(' ');
    expect(integer).not.toContain(',');
    expect(integer).not.toContain('.');
  });

  it('couvre les bornes 0, 1 et 999 999 999', () => {
    expect(formatMinor(0, 'XOF').integer).toBe('0');
    expect(formatMinor(1, 'XOF').integer).toBe('1');
    expect(formatMinor(999999999, 'XOF').integer).toBe(
      `999${THIN_NBSP}999${THIN_NBSP}999`,
    );
  });

  it("n'affiche jamais de décimale en XOF", () => {
    expect(formatMinor(125000, 'XOF').fraction).toBeNull();
    expect(formatMinorToString(125000, 'XOF')).toBe(`125${THIN_NBSP}000${THIN_NBSP}F`);
  });

  it('utilise le signe moins mathématique U+2212, jamais un trait d’union', () => {
    const { sign } = formatMinor(-5000, 'XOF');
    expect(sign).toBe(MINUS_SIGN);
    expect(sign).toBe('−');
    expect(sign).not.toBe('-');
  });

  it('n’ajoute un + que sur demande explicite et sur un montant positif', () => {
    expect(formatMinor(5000, 'XOF', { sign: 'auto' }).sign).toBe('');
    expect(formatMinor(5000, 'XOF', { sign: 'always' }).sign).toBe('+');
    expect(formatMinor(0, 'XOF', { sign: 'always' }).sign).toBe('');
    expect(formatMinor(-5000, 'XOF', { sign: 'never' }).sign).toBe('');
  });

  it('masque en quatre pastilles tout en gardant la devise', () => {
    expect(formatMinor(125000, 'XOF', { hidden: true })).toEqual({
      sign: '',
      integer: '••••',
      fraction: null,
      symbol: 'F',
    });
  });

  it('rend les décimales des devises qui en ont', () => {
    expect(formatMinor(123456, 'EUR')).toEqual({
      sign: '',
      integer: `1${THIN_NBSP}234`,
      fraction: '56',
      symbol: '€',
    });
    expect(formatMinor(5, 'EUR').fraction).toBe('05');
  });

  it('règle R2 — une devise inconnue ne fait pas planter le rendu', () => {
    expect(formatMinor(1500, 'ZZZ')).toEqual({
      sign: '',
      integer: `1${THIN_NBSP}500`,
      fraction: null,
      symbol: 'ZZZ',
    });
  });
});

describe('toMinor — contrat §5.3', () => {
  afterEach(() => setScaleAnomalyListener(null));

  it('traite le franc comme la plus petite unité du XOF', () => {
    expect(toMinor(125000, 'XOF')).toBe(125000);
    expect(toApiAmount(125000, 'XOF')).toBe(125000);
  });

  it('convertit en centimes pour une devise à deux décimales', () => {
    expect(toMinor(1234.56, 'EUR')).toBe(123456);
    expect(toApiAmount(123456, 'EUR')).toBe(1234.56);
  });

  it("signale une échelle incohérente sans rejeter le montant", () => {
    const anomalies: unknown[] = [];
    setScaleAnomalyListener((info) => anomalies.push(info));

    // Le backend n'impose aucune échelle : 100.5 XOF serait accepté.
    expect(toMinor(100.5, 'XOF')).toBe(101);
    expect(anomalies).toHaveLength(1);
  });

  it('ne perd pas de précision sur un montant flottant classique', () => {
    // 0.1 + 0.2 en flottant vaut 0.30000000000000004 : l'arithmétique entière
    // sur les unités mineures est la seule façon d'éviter ce genre de dérive.
    const a = { minor: toMinor(0.1, 'EUR'), currency: 'EUR' as const };
    const b = { minor: toMinor(0.2, 'EUR'), currency: 'EUR' as const };
    expect(addMoney(a, b).minor).toBe(30);
    expect(formatMinorToString(addMoney(a, b).minor, 'EUR')).toBe(`0,30${THIN_NBSP}€`);
  });

  it('rejette une addition entre devises différentes', () => {
    expect(() =>
      addMoney({ minor: 1, currency: 'XOF' }, { minor: 1, currency: 'EUR' }),
    ).toThrow(/Currency mismatch/);
  });

  it('autorise un résultat négatif à la soustraction — c’est un calcul, pas un solde', () => {
    expect(
      subtractMoney({ minor: 100, currency: 'XOF' }, { minor: 250, currency: 'XOF' }).minor,
    ).toBe(-150);
  });
});
