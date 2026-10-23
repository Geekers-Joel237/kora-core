#!/usr/bin/env node
/**
 * Audit du bundle de production — `docs/08-quality-bar.md` §4 et §11.
 *
 * Deux questions auxquelles seule une build réelle répond :
 *
 * 1. Le bundle contient-il un module de `src/devtools/` ? Le §12 de
 *    `10-validation-mode.md` exige que non, sans exception.
 * 2. Combien pèse-t-il, face au budget `NFR-23` ?
 *
 * L'audit exporte avec sources maps **externes** et sans bytecode : c'est la
 * seule façon d'obtenir la liste exacte des modules empaquetés. Le bytecode
 * Hermes, lui, ne se lit pas.
 *
 * Le bundle mesuré ici est le JavaScript minifié, pas le `.hbc` livré : les
 * deux tailles sont rapportées séparément par `expo export`.
 */

import { execFileSync } from 'node:child_process';
import { mkdtempSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

/** Budget `NFR-23`, en octets. Voir l'arbitrage du lot 10 dans le README. */
const BUNDLE_BUDGET_BYTES = 4 * 1024 * 1024;

/** Répertoires dont aucun module ne doit survivre à la build de production. */
const FORBIDDEN_PREFIXES = ['/src/devtools/'];

/**
 * Le substitut inerte, lui, doit être là : c'est ce qui remplace l'arbre entier.
 * Sa présence prouve que la redirection de résolution a bien eu lieu.
 */
const EXPECTED_STUB = '/src/devtools/index.production.tsx';

const outputDir = mkdtempSync(path.join(tmpdir(), 'kora-audit-'));

function fail(message) {
  console.error(`\n✖ ${message}`);
  process.exitCode = 1;
}

try {
  console.log('› Export de production avec sources maps…');
  execFileSync(
    process.execPath,
    [
      path.join('node_modules', 'expo', 'bin', 'cli'),
      'export',
      '--platform',
      'android',
      '--source-maps',
      'external',
      '--no-bytecode',
      '--output-dir',
      outputDir,
    ],
    {
      stdio: ['ignore', 'ignore', 'inherit'],
      env: { ...process.env, NODE_ENV: 'production', EXPO_PUBLIC_ENV: 'production' },
    },
  );

  const bundleDir = path.join(outputDir, '_expo', 'static', 'js', 'android');
  const files = readdirSync(bundleDir);

  const mapName = files.find((file) => file.endsWith('.map'));
  const bundleName = files.find((file) => file.endsWith('.js'));
  if (!mapName || !bundleName) throw new Error('Aucun bundle exporté.');

  const bundleBytes = statSync(path.join(bundleDir, bundleName)).size;
  const sourceMap = JSON.parse(readFileSync(path.join(bundleDir, mapName), 'utf8'));
  const sources = sourceMap.sources ?? [];

  const normalized = sources.map((source) => source.replace(/\\/g, '/'));
  const leaked = normalized.filter(
    (source) =>
      source !== EXPECTED_STUB && FORBIDDEN_PREFIXES.some((prefix) => source.includes(prefix)),
  );

  console.log(`\n  modules empaquetés   ${sources.length}`);
  console.log(
    `  bundle JS minifié    ${(bundleBytes / 1024 / 1024).toFixed(2)} Mo` +
      `  (budget NFR-23 : ${(BUNDLE_BUDGET_BYTES / 1024 / 1024).toFixed(0)} Mo)`,
  );
  console.log(`  modules devtools     ${leaked.length}`);

  if (leaked.length > 0) {
    for (const source of leaked) console.error(`    ${source}`);
    fail(
      `${leaked.length} module(s) de mode validation dans le bundle de production — ` +
        '`docs/10-validation-mode.md` §12.',
    );
  }

  if (bundleBytes > BUNDLE_BUDGET_BYTES) {
    // Dépassement signalé, pas bloquant : l'arbitrage de `NFR-23` est
    // documenté dans le README et attend une décision produit.
    console.warn(
      `\n⚠ Budget NFR-23 dépassé de ${((bundleBytes - BUNDLE_BUDGET_BYTES) / 1024 / 1024).toFixed(2)} Mo.`,
    );
  }

  if (process.exitCode !== 1) console.log('\n✓ Aucun module de mode validation dans le bundle.');
} finally {
  rmSync(outputDir, { recursive: true, force: true });
}
