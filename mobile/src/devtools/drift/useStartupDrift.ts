/**
 * Détection de dérive **au démarrage** — `docs/10-validation-mode.md` §5.
 *
 * Le §5 prescrit une exécution au lancement, pas à l'ouverture d'un onglet :
 * un écart bloquant doit être annoncé avant que quiconque n'ouvre le panneau,
 * sans quoi il ne sera découvert qu'après le premier symptôme.
 *
 * « Un écart bloquant n'empêche pas l'app de démarrer. Il l'annonce,
 * bruyamment, et laisse continuer — le but est d'informer, pas de bloquer le
 * travail. » L'analyse est donc silencieuse en cas d'échec réseau : un backend
 * éteint n'est pas une dérive de contrat.
 */

import { useEffect, useState } from 'react';

import { env } from '@/lib/env';
import { request } from '@/lib/http';
import { detectDrift, type DriftReport } from './detector';

export const API_DOCS_PATH = '/v3/api-docs';

export async function analyzeContract(): Promise<DriftReport> {
  const document = await request<unknown>(API_DOCS_PATH, { auth: false });
  return detectDrift(document);
}

export function useStartupDrift(): DriftReport | null {
  const [report, setReport] = useState<DriftReport | null>(null);

  useEffect(() => {
    let cancelled = false;

    void analyzeContract()
      .then((result) => {
        if (!cancelled) setReport(result);
      })
      .catch(() => {
        // Backend injoignable : ce n'est pas une dérive, et une bannière rouge
        // au lancement d'un émulateur sans serveur serait du bruit pur.
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return report;
}

export const API_DOCS_URL = `${env.apiUrl}${API_DOCS_PATH}`;
