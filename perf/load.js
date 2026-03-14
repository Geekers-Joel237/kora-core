/**
 * load.js — Load test : validation des SLOs nominaux Étape 0.
 *
 * SLOs cibles :
 *   P95 latency  < 150ms
 *   Error rate   < 1%
 *   Throughput   ≥ 10 req/sec au plateau
 *
 * Profil de charge :
 *   Ramp-up  : 0 → 10 req/sec  (2 min)
 *   Plateau  : 10 req/sec       (8 min)
 *   Ramp-down: 10 → 0           (1 min)
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
// 30 users = preAllocatedVUs → chaque VU a son propre user, élimine la contention OTP
const USER_COUNT = 30;

// ── Config k6 — constant arrival rate ────────────────────────────────────────

export const options = {
    scenarios: {
        load: {
            executor:        'ramping-arrival-rate',
            startRate:       0,
            timeUnit:        '1s',
            preAllocatedVUs: 30,    // pool de VUs disponibles
            maxVUs:          60,    // plafond de sécurité
            stages: [
                { target: 10, duration: '2m' },   // ramp-up
                { target: 10, duration: '8m' },   // plateau nominal
                { target: 0,  duration: '1m' },   // ramp-down
            ],
        },
    },
    thresholds: {
        // SLOs stricts Étape 0
        'http_req_duration{scenario:load}': ['p(95)<150'],
        http_req_failed:                    ['rate<0.01'],
        // On vérifie aussi que les checks passent
        checks:                             ['rate>0.99'],
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