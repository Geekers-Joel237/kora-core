/**
 * Invariants d'architecture — `docs/08-quality-bar.md` §6, §8 et §11.
 *
 * Ce fichier ne teste aucun comportement : il inspecte la base de code. Chaque
 * règle vérifiée ici a **déjà été enfreinte au moins une fois** dans le projet,
 * et le seul rempart était la relecture.
 *
 * Ce qui est vérifiable par le lint reste au lint. Ne vivent ici que les règles
 * qu'aucune règle ESLint n'exprime : elles portent sur des relations entre
 * fichiers, pas sur la forme d'une expression.
 */

import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(__dirname, '..', '..');

function walk(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    if (statSync(full).isDirectory()) {
      if (entry === 'node_modules' || entry === '__tests__') continue;
      walk(full, out);
    } else if (/\.tsx?$/.test(entry)) {
      out.push(full);
    }
  }
  return out;
}

const SOURCES = [...walk(path.join(ROOT, 'src')), ...walk(path.join(ROOT, 'app'))];

/** Chemin relatif à la racine, en séparateurs POSIX — comparable sur toute plateforme. */
function relative(file: string): string {
  return path.relative(ROOT, file).split(path.sep).join('/');
}

const FILES = SOURCES.map((file) => ({ path: relative(file), text: readFileSync(file, 'utf8') }));

describe('règle R1 — une seule couche de traduction', () => {
  /**
   * Seuls ces fichiers ont le droit de connaître les DTO du backend. Les
   * écrans et les hooks ne manipulent que `src/types/domain.ts`.
   */
  const ALLOWED = [
    'src/features/shared/mappers.ts',
    'src/lib/http/client.ts',
    'src/lib/http/errors.ts',
    'src/lib/jwt.ts',
  ];

  it('n’expose `types/api` qu’aux frontières prévues', () => {
    const importers = FILES.filter(
      (file) => /from '@\/types\/api'/.test(file.text) && !ALLOWED.includes(file.path),
    ).map((file) => file.path);

    expect(importers).toEqual([]);
  });

  it('ne laisse aucun écran importer un mappeur d’API directement', () => {
    const offenders = FILES.filter(
      (file) => file.path.startsWith('app/') && /features\/shared\/(mappers|schemas)/.test(file.text),
    ).map((file) => file.path);

    expect(offenders).toEqual([]);
  });
});

describe('isolation du mode validation — docs/10-validation-mode.md §12', () => {
  it('ne laisse l’application importer que le point d’entrée `@/devtools`', () => {
    // Un import profond contournerait la redirection de `metro.config.js` et
    // ramènerait tout l'arbre dans le bundle de production.
    const offenders = FILES.filter(
      (file) => !file.path.startsWith('src/devtools/') && /@\/devtools\//.test(file.text),
    ).map((file) => file.path);

    expect(offenders).toEqual([]);
  });

  it('garde la même surface publique entre le module réel et son substitut', () => {
    // Une divergence de surface ne se verrait qu'en production, à l'exécution :
    // le substitut ne s'exécute nulle part ailleurs.
    /* eslint-disable @typescript-eslint/no-require-imports */
    const real = require('@/devtools') as Record<string, unknown>;
    const stub = require('@/devtools/index.production') as Record<string, unknown>;
    /* eslint-enable @typescript-eslint/no-require-imports */

    expect(Object.keys(stub).sort()).toEqual(Object.keys(real).sort());
  });
});

describe('sécurité — docs/08-quality-bar.md §6', () => {
  it('n’appelle jamais un endpoint `/test/**` depuis l’application', () => {
    const offenders = FILES.filter(
      (file) => !file.path.startsWith('src/devtools/') && /['"`]\/test\//.test(file.text),
    ).map((file) => file.path);

    expect(offenders).toEqual([]);
  });

  it('ne persiste le PIN dans aucun magasin', () => {
    // NFR-41 : le PIN vit dans un `useRef` et nulle part ailleurs.
    const offenders = FILES.filter((file) =>
      /(secureSet|kvSetString|kvSetJson|setItemAsync)\s*\([^)]*(rawPin|pin\b)/i.test(file.text),
    ).map((file) => file.path);

    expect(offenders).toEqual([]);
  });
});

describe('accessibilité — docs/08-quality-bar.md §8', () => {
  /** Ces jetons dimensionnent des conteneurs qui portent du texte. */
  const TEXT_CONTAINERS = ['layout.rowHeight', 'layout.buttonHeight', 'layout.navBarHeight'];

  it('n’impose aucune hauteur fixe à un conteneur de texte', () => {
    // À 200 % de taille de police, un libellé passe sur deux lignes : une
    // hauteur fixe le rogne. Corrigé au lot 10 — ceci en est le garde-fou.
    const offenders: string[] = [];

    for (const file of FILES) {
      for (const token of TEXT_CONTAINERS) {
        if (new RegExp(`\\bheight:\\s*${token.replace('.', '\\.')}`).test(file.text)) {
          offenders.push(`${file.path} → height: ${token}`);
        }
      }
    }

    expect(offenders).toEqual([]);
  });

  it('donne un libellé ou un rôle d’accessibilité à chaque `Pressable`', () => {
    const offenders: string[] = [];

    for (const file of FILES) {
      for (const match of file.text.matchAll(/<Pressable\b/g)) {
        const block = readJsxOpeningTag(file.text, match.index ?? 0);
        if (!/accessibilityLabel|accessibilityRole/.test(block)) {
          offenders.push(`${file.path}:${file.text.slice(0, match.index).split('\n').length}`);
        }
      }
    }

    expect(offenders).toEqual([]);
  });
});

/** Lit une balise JSX ouvrante en ignorant les `>` imbriqués dans des accolades. */
function readJsxOpeningTag(text: string, start: number): string {
  let depth = 0;
  for (let index = start; index < text.length; index += 1) {
    const char = text[index];
    if (char === '{') depth += 1;
    else if (char === '}') depth -= 1;
    else if (char === '>' && depth === 0) return text.slice(start, index);
  }
  return text.slice(start);
}
