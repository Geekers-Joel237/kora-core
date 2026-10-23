/**
 * Reproduction d'un appel en `cURL` — `docs/10-validation-mode.md` §3.
 *
 * « Reproduction immédiate de l'appel côté backend. » L'intérêt est de passer
 * d'un symptôme observé sur l'appareil à une commande qu'on colle dans un
 * terminal, sans retaper une URL ni des en-têtes.
 *
 * ⚠️ **La commande produite n'est pas rejouable telle quelle sur un appel
 * porteur d'un secret** : `rawPin` y vaut `****` et le Bearer y est masqué,
 * parce que le journal ne les a jamais vus. C'est délibéré, et c'est la
 * contrepartie assumée de la règle « masqué sans exception ». L'opérateur
 * remplace les `****` à la main.
 *
 * Module **pur** : ni React, ni presse-papiers, ni réseau.
 */

import type { NetworkEntry } from './store';

/** Guillemets simples POSIX : la seule séquence à échapper est le guillemet lui-même. */
function shellQuote(value: string): string {
  return `'${value.replace(/'/g, `'\\''`)}'`;
}

function buildQueryString(query: NetworkEntry['query']): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) params.append(key, String(value));
  }
  const serialized = params.toString();
  return serialized ? `?${serialized}` : '';
}

export function toCurl(entry: NetworkEntry, baseUrl: string): string {
  const url = `${baseUrl}${entry.path}${buildQueryString(entry.query)}`;

  const parts = [`curl -X ${entry.method} ${shellQuote(url)}`];

  // Ordre stable : deux copies du même appel doivent donner deux textes
  // identiques, sans quoi les comparer devient un exercice de patience.
  for (const key of Object.keys(entry.headers).sort()) {
    parts.push(`  -H ${shellQuote(`${key}: ${entry.headers[key] ?? ''}`)}`);
  }

  if (entry.body !== undefined) {
    parts.push(`  -d ${shellQuote(JSON.stringify(entry.body))}`);
  }

  return parts.join(' \\\n');
}

/**
 * Le rejeu est réservé aux `GET` — §3.
 *
 * **Jamais sur un `POST` de paiement**, et pas davantage sur les autres `POST` :
 * sans idempotence serveur (contrat §6.1), rejouer une écriture depuis un outil
 * de diagnostic est le meilleur moyen de créer un second débit en croyant
 * observer le premier.
 */
export function isReplayable(entry: NetworkEntry): boolean {
  return entry.method === 'GET';
}
