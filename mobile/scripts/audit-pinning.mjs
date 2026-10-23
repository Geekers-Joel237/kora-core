#!/usr/bin/env node
/**
 * État de l'épinglage de certificat — `docs/08-quality-bar.md` §6.
 *
 * La case « l'épinglage est actif en production » ne se coche pas en lisant le
 * code : le mécanisme peut être en place et **inactif** faute d'empreintes.
 * Ce script tranche, et sort en échec quand la configuration est présente mais
 * invalide — le seul cas réellement dangereux, parce qu'il donne l'illusion
 * d'une protection.
 */

import { execFileSync } from 'node:child_process';
import path from 'node:path';

import { normalizePinningConfig } from '../plugins/certificate-pinning.js';

const output = execFileSync(
  process.execPath,
  [path.join('node_modules', 'expo', 'bin', 'cli'), 'config', '--type', 'public', '--json'],
  { encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'] },
);

const appConfig = JSON.parse(output);
const raw = appConfig.extra?.certificatePinning;

let pinning;
try {
  pinning = normalizePinningConfig(raw);
} catch (error) {
  console.error(`\n✖ Configuration d’épinglage invalide : ${error.message}`);
  console.error('  Une configuration fausse est pire que pas d’épinglage : elle rassure à tort.');
  process.exit(1);
}

if (!pinning) {
  console.log('\n○ Épinglage de certificat : mécanisme en place, INACTIF.');
  console.log('  Aucune empreinte configurée dans `app.json` → `extra.certificatePinning`.');
  console.log('  Condition bloquante avant toute livraison en production.');
  console.log('  Empreintes attendues : SPKI SHA-256 base64, au moins deux (service + secours).');
  process.exit(0);
}

console.log('\n✓ Épinglage de certificat : ACTIF.');
console.log(`  domaine            ${pinning.domain}`);
console.log(`  sous-domaines      ${pinning.includeSubdomains ? 'inclus' : 'exclus'}`);
console.log(`  empreintes         ${pinning.pins.length}`);
console.log(`  expiration         ${pinning.expiration ?? 'aucune'}`);
