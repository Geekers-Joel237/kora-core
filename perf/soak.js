/**
 * soak.js — Soak test : détection de fuites mémoire et dégradation progressive.
 *
 * Objectif : maintenir une charge constante pendant 30 minutes pour détecter :
 *   - Fuites mémoire JVM (heap drift)
 *   - DB pool exhaustion (hikaricp_connections_pending croissant)
 *   - Dégradation de latence dans le temps (p95 drift)
 *   - Connexions non libérées, memory leaks dans les caches
 *
 * Profil Step 3 :
 *   50 req/s constant pendant 30 minutes — la moitié du pic Step 3, soit
 *   ~90 000 itérations. 5 req/s (Step 1) ne produisait que 9 000 itérations,
 *   trop peu pour qu'une fuite lente se voie.
 *
 * Thresholds : les mêmes SLOs par opération que load.js. Un threshold agrégé
 * sur 30 min ne voit pas un drift qui commence à la 20e minute : la détection
 * de drift reste dans Grafana (p95 par opération dans le temps).
 *
 * Lancer : ./perf/soak-run.sh
 */

import { BATCH_SIZE, createTestUsers } from './data/setup.js';
import { pickPair, runMix } from './scenarios/mix.js';

// 50 req/s × ~1.0s ≈ 50 VUs en régime ; marge ×2.4 pour la queue provider.
const USER_COUNT = 120;

export const options = {
    setupTimeout:  '5m',
    batchPerHost:  BATCH_SIZE,
    scenarios: {
        soak: {
            executor:        'constant-arrival-rate',
            rate:            50,
            timeUnit:        '1s',
            duration:        '30m',
            preAllocatedVUs: 60,
            maxVUs:          USER_COUNT,
        },
    },
    thresholds: {
        'http_req_duration{operation:balance}':  ['p(95)<100'],
        'http_req_duration{operation:history}':  ['p(95)<150'],
        'http_req_duration{operation:transfer}': ['p(95)<300'],
        'http_req_duration{operation:cash}':     ['p(95)<2500'],
        http_req_failed:    ['rate<0.01'],
        checks:             ['rate>0.99'],
        dropped_iterations: ['count<1'],
    },
};

export function setup() {
    return createTestUsers(USER_COUNT, 'soak');
}

export default function (data) {
    const users = data.users;
    if (!users || users.length === 0) return;

    const [userA, userB] = pickPair(users);
    runMix(userA, userB);
}
