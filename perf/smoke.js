/**
 * smoke.js — Smoke test : 1 VU, 2 minutes.
 *
 * Objectif : sanité du système, pas de performance.
 * Couvre les 4 endpoints avec le mix réaliste :
 *   cashIn 40% | transfer 35% | cashOut 15% | balance 10%
 *
 * Thresholds souples (smoke = "ça marche" pas "ça performe") :
 *   - p(95) < 2 000ms  (tolérance large, on détecte les pannes)
 *   - error rate < 5%
 *
 * Lancer : ./perf/smoke-run.sh
 */

import { sleep } from 'k6';
import { createTestUsers }  from './data/setup.js';
import { scenarioCashIn }   from './scenarios/cashIn.js';
import { scenarioCashOut }  from './scenarios/cashOut.js';
import { scenarioTransfer } from './scenarios/transfer.js';
import { scenarioBalance }  from './scenarios/balance.js';

// ── Config k6 ─────────────────────────────────────────────────────────────────

export const options = {
    vus:      1,
    duration: '2m',
    thresholds: {
        http_req_duration: ['p(95)<2000'],
        http_req_failed:   ['rate<0.05'],
    },
};

// ── Setup — créer 2 utilisateurs, seed balances ───────────────────────────────

export function setup() {
    return createTestUsers(2, 'smoke');
}

// ── Scénario principal ────────────────────────────────────────────────────────

export default function (data) {
    const users = data.users;
    if (!users || users.length < 2) {
        console.error('Setup incomplet — pas assez d\'utilisateurs');
        return;
    }

    const userA = users[0];
    const userB = users[1];

    // Mix réaliste : tirage pseudo-aléatoire par itération
    const roll = Math.random();

    if (roll < 0.40) {
        // 40% — cashIn
        scenarioCashIn(userA);

    } else if (roll < 0.75) {
        // 35% — transfer (A → B)
        scenarioTransfer(userA, userB.fullPhone);

    } else if (roll < 0.90) {
        // 15% — cashOut
        scenarioCashOut(userA);

    } else {
        // 10% — balance
        scenarioBalance(userA);
    }

    sleep(1);
}