import { darkTheme, lightTheme, type Theme } from '../tokens';

/**
 * Vérification objective de NFR-50 : contraste ≥ 4,5:1 sur le texte courant,
 * ≥ 3:1 sur les éléments d'interface, **dans les deux thèmes**.
 *
 * Les lavis d'état sont translucides : leur contraste réel dépend du fond sur
 * lequel ils sont posés. On les compose donc sur `bg.app` avant de mesurer,
 * exactement comme le fait le moteur de rendu.
 */

interface Rgb {
  r: number;
  g: number;
  b: number;
  a: number;
}

function parseColor(value: string): Rgb {
  const rgba = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/.exec(
    value,
  );
  if (rgba) {
    return {
      r: Number(rgba[1]),
      g: Number(rgba[2]),
      b: Number(rgba[3]),
      a: rgba[4] === undefined ? 1 : Number(rgba[4]),
    };
  }

  const hex = value.replace('#', '');
  const full =
    hex.length === 3
      ? hex
          .split('')
          .map((ch) => ch + ch)
          .join('')
      : hex;

  return {
    r: parseInt(full.slice(0, 2), 16),
    g: parseInt(full.slice(2, 4), 16),
    b: parseInt(full.slice(4, 6), 16),
    a: full.length === 8 ? parseInt(full.slice(6, 8), 16) / 255 : 1,
  };
}

/** Compose une couleur translucide sur son fond, comme le fait le rendu. */
function composite(foreground: string, background: string): Rgb {
  const fg = parseColor(foreground);
  const bg = parseColor(background);
  return {
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a),
    a: 1,
  };
}

function relativeLuminance({ r, g, b }: Rgb): number {
  const channel = (value: number) => {
    const s = value / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

/** Ratio WCAG 2.1. Les deux couleurs sont composées sur `base` au préalable. */
function contrastRatio(foreground: string, background: string, base: string): number {
  const bg = composite(background, base);
  const fg = composite(foreground, `rgba(${bg.r},${bg.g},${bg.b},1)`);
  const l1 = relativeLuminance(fg);
  const l2 = relativeLuminance(bg);
  const [lighter, darker] = l1 > l2 ? [l1, l2] : [l2, l1];
  return (lighter + 0.05) / (darker + 0.05);
}

const THEMES: [string, Theme][] = [
  ['sombre', darkTheme],
  ['clair', lightTheme],
];

describe.each(THEMES)('contraste — thème %s', (_label, theme) => {
  const base = theme.bg.app;

  it('texte primaire sur le fond d’application ≥ 4,5:1', () => {
    expect(contrastRatio(theme.text.primary, theme.bg.app, base)).toBeGreaterThanOrEqual(4.5);
  });

  it('texte primaire sur chaque surface ≥ 4,5:1', () => {
    for (const surface of [theme.bg.surface1, theme.bg.surface2, theme.bg.surface3]) {
      expect(contrastRatio(theme.text.primary, surface, base)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('texte secondaire sur chaque surface ≥ 4,5:1', () => {
    for (const surface of [theme.bg.app, theme.bg.surface1, theme.bg.surface2]) {
      expect(contrastRatio(theme.text.secondary, surface, base)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('texte tertiaire sur le fond d’application ≥ 3:1 — usage non essentiel', () => {
    // Horodatages et mentions légales : seuil d'élément d'interface, pas de
    // texte courant. Aucune information ne repose sur eux seuls (NFR-55).
    expect(contrastRatio(theme.text.tertiary, theme.bg.app, base)).toBeGreaterThanOrEqual(3);
  });

  it('texte sur accent ≥ 4,5:1 — le piège du blanc sur vert', () => {
    // Blanc sur #00C46A donne 2,1:1. C'est précisément pourquoi `onAccent`
    // vaut un vert-noir profond en thème sombre. Design system §2.2.
    expect(contrastRatio(theme.text.onAccent, theme.accent.primary, base)).toBeGreaterThanOrEqual(
      4.5,
    );
  });

  it('accent primaire sur le fond d’application ≥ 3:1', () => {
    expect(contrastRatio(theme.accent.primary, theme.bg.app, base)).toBeGreaterThanOrEqual(3);
  });

  it('chaque famille d’état est lisible sur son propre lavis ≥ 4,5:1', () => {
    for (const family of ['success', 'failed', 'pending', 'reversed', 'info'] as const) {
      const { fg, bg } = theme.status[family];
      expect(contrastRatio(fg, bg, base)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('les couleurs de flux monétaire sont lisibles sur les surfaces ≥ 4,5:1', () => {
    for (const surface of [theme.bg.app, theme.bg.surface1]) {
      expect(contrastRatio(theme.flow.inbound, surface, base)).toBeGreaterThanOrEqual(4.5);
      expect(contrastRatio(theme.flow.outbound, surface, base)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('le texte de danger est lisible sur le fond d’application ≥ 4,5:1', () => {
    expect(contrastRatio(theme.text.danger, theme.bg.app, base)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(theme.text.success, theme.bg.app, base)).toBeGreaterThanOrEqual(4.5);
  });
});

describe('parité des thèmes', () => {
  it('les deux thèmes exposent exactement les mêmes clés', () => {
    const walk = (value: unknown, prefix = ''): string[] => {
      if (typeof value !== 'object' || value === null) return [prefix];
      return Object.entries(value).flatMap(([key, child]) =>
        walk(child, prefix ? `${prefix}.${key}` : key),
      );
    };
    expect(walk(lightTheme).sort()).toEqual(walk(darkTheme).sort());
  });

  it('aucun fond n’est du noir pur ni du blanc pur en texte sombre', () => {
    // Design system §8 — #000000 écrase l'élévation et provoque du smearing OLED.
    expect(darkTheme.bg.app).not.toBe('#000000');
    expect(darkTheme.bg.root).not.toBe('#000000');
    expect(darkTheme.text.primary).not.toBe('#FFFFFF');
  });
});
