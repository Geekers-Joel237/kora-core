/**
 * load.js — Load test : validation des SLOs nominaux Étape 1.
 *
 * SLOs cibles :
 *   P95 latency  < 200ms
 *   Error rate   < 1%
 *   Throughput   ≥ 25 req/sec au plateau
 *
 * Profil de charge :
 *   Ramp-up  : 0 → 25 req/sec  (2 min)
 *   Plateau  : 25 req/sec       (8 min)
 *   Ramp-down: 25 → 0           (1 min)
 *   Total    : ~11 min
 *
 * Mix métier :
 *   cashIn 40% | transfer 35% | cashOut 15% | balance 10%
 *
 * Lancer : ./perf/load-run.sh
 */

import { sleep }           from 'k6';
import { SharedArray }     from 'k6/data';
import { createTestUsers } from './data/setup.js';
import { scenarioCashIn }  from './scenarios/cashIn.js';
import { scenarioCashOut } from './scenarios/cashOut.js';
import { scenarioTransfer } from './scenarios/transfer.js';
import { scenarioBalance } from './scenarios/balance.js';

// ── Nombre d'utilisateurs de test ─────────────────────────────────────────────
// USER_COUNT = preAllocatedVUs = maxVUs : chaque VU possède son propre user.
// maxVUs > USER_COUNT ferait partager des comptes entre VUs → OLFE storm.
const USER_COUNT = 60;

// ── Config k6 — constant arrival rate ────────────────────────────────────────

export const options = {
    // 60 users × ~2.7s (register + OTP + verify + seed cashIn) ≈ 2m42s.
    // Default k6 setupTimeout is 60s — raises it to 5m for safety margin.
    setupTimeout: '5m',
    scenarios: {
        load: {
            executor:        'ramping-arrival-rate',
            startRate:       0,
            timeUnit:        '1s',
            preAllocatedVUs: 60,
            maxVUs:          60,    // = USER_COUNT — interdit le partage de compte entre VUs
            stages: [
                { target: 25, duration: '2m' },   // ramp-up
                { target: 25, duration: '8m' },   // plateau nominal
                { target: 0,  duration: '1m' },   // ramp-down
            ],
        },
    },
    thresholds: {
        // Per-operation SLOs — calibrés par nature d'opération, pas par endpoint.
        //
        // balance   : lecture pure, 0 provider I/O, 0 lock → p(95) < 100ms
        // transfer  : 1 TX avec lock customer account, 0 provider I/O → p(95) < 200ms
        // cash      : TX-1 + provider I/O (~1 400–1 960ms) + TX-2 → p(95) < 2 500ms
        //
        // La borne cash (2 500ms) est au-dessus du plafond provider mesuré (2 130ms)
        // mais assez serrée pour détecter une pool exhaustion (p(95) monterait à 5–30s).
        'http_req_duration{operation:balance}':  ['p(95)<100'],
        'http_req_duration{operation:transfer}': ['p(95)<200'],
        'http_req_duration{operation:cash}':     ['p(95)<2500'],
        http_req_failed:                          ['rate<0.01'],
        checks:                                   ['rate>0.99'],
    },
};

// ── Setup — créer les users et seed les soldes ────────────────────────────────

export function setup() {
    return createTestUsers(USER_COUNT, 'load');
}

// ── Scénario principal ────────────────────────────────────────────────────────

export default function (data) {
    const users = data.users;
    if (!users || users.length === 0) return;

    // Sélection déterministe du user par VU pour éviter les collisions
    const idx   = __VU % users.length;
    const userA = users[idx];
    const userB = users[(idx + 1) % users.length];

    const roll = Math.random();

    if (roll < 0.40) {
        scenarioCashIn(userA);

    } else if (roll < 0.75) {
        scenarioTransfer(userA, userB.fullPhone);

    } else if (roll < 0.90) {
        scenarioCashOut(userA);

    } else {
        scenarioBalance(userA);
    }

    // Pas de sleep fixe avec ramping-arrival-rate : k6 contrôle le débit
}