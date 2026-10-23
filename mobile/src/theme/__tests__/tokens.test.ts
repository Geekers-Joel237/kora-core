import { fontFamily, space, tabularNums, type } from '../tokens';

describe('tokens — sous-ensemble d’amorçage du lot 0', () => {
  it('n’expose que les quatre graisses du design system §3.1', () => {
    expect(Object.keys(fontFamily)).toEqual(['regular', 'medium', 'semibold', 'bold']);
    Object.values(fontFamily).forEach((family) => {
      expect(family).toMatch(/^Inter_(400Regular|500Medium|600SemiBold|700Bold)$/);
    });
  });

  it('respecte l’échelle d’espacement base 4 — design system §4', () => {
    Object.values(space).forEach((value) => {
      expect(value % 4).toBe(0);
    });
  });

  it('applique un interlettrage négatif aux grandes tailles — design system §3.2', () => {
    expect(type.displayLg.letterSpacing).toBeLessThan(0);
    expect(type.displayMd.letterSpacing).toBeLessThan(0);
  });

  it('impose les chiffres tabulaires pour les montants — design system §3.4', () => {
    expect(tabularNums.fontVariant).toContain('tabular-nums');
  });
});
