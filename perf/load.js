/**
 * load.js — Load test : validation des SLOs nominaux Step 3.
 *
 * Cible Step 3 (ROADMAP) : 80–100 req/s, P95 < 300ms.
 * Le P95 roadmap s'applique aux opérations sans provider ; les opérations cash
 * gardent le plafond provider d'ADR-005 (voir l'amendement Step 3).
 *
 * Profil de charge :
 *   Ramp-up  : 0 → 50 → 100 req/s  (2m + 2m)
 *   Plateau  : 100 req/s            (8 min)
 *   Ramp-down: 100 → 0              (1 min)
 *   Total    : ~13 min
 *
 * Mix métier : scenarios/mix.js
 *   cashIn 35% | transfer 30% | cashOut 15% | balance 10% | history 10%
 *
 * Lancer : ./perf/load-run.sh
 */

import { BATCH_SIZE, createTestUsers } from './data/setup.js';
import { pickPair, runMix } from './scenarios/mix.js';

// Loi de Little : 100 req/s × ~1.0s par itération ≈ 100 VUs en régime,
// le double pour absorber la queue provider (p95 ≈ 2s).
// USER_COUNT = maxVUs : chaque VU possède son propre user.
const USER_COUNT = 200;

export const options = {
    setupTimeout:  '5m',
    batchPerHost:  BATCH_SIZE,
    scenarios: {
        load: {
            executor:        'ramping-arrival-rate',
            startRate:       0,
            timeUnit:        '1s',
            preAllocatedVUs: 120,
            maxVUs:          USER_COUNT,
            stages: [
                { target: 50,  duration: '2m' },   // ramp-up
                { target: 100, duration: '2m' },   // ramp-up
                { target: 100, duration: '8m' },   // plateau nominal
                { target: 0,   duration: '1m' },   // ramp-down
            ],
        },
    },
    thresholds: {
        // Per-operation SLOs — calibrés par nature d'opération, pas par endpoint.
        //
        // balance   : lecture pure, 0 provider I/O, 0 lock               → p(95) < 100ms
        // history   : lecture keyset paginée, 0 lock                      → p(95) < 150ms
        // transfer  : BCrypt PIN + 1 TX avec 2 locks, 0 provider I/O      → p(95) < 300ms (ROADMAP Step 3)
        // cash      : TX-1 + provider I/O (~1 400–1 960ms) + TX-2         → p(95) < 2 500ms
        'http_req_duration{operation:balance}':  ['p(95)<100'],
        'http_req_duration{operation:history}':  ['p(95)<150'],
        'http_req_duration{operation:transfer}': ['p(95)<300'],
        'http_req_duration{operation:cash}':     ['p(95)<2500'],
        http_req_failed: ['rate<0.01'],
        checks:          ['rate>0.99'],
        // Le débit fait partie du SLO : une itération abandonnée faute de VU libre
        // est une requête que le système n'a pas servie.
        dropped_iterations: ['count<1'],
    },
};

export function setup() {
    return createTestUsers(USER_COUNT, 'load');
}

export default function (data) {
    const users = data.users;
    if (!users || users.length === 0) return;

    const [userA, userB] = pickPair(users);
    runMix(userA, userB);
}
