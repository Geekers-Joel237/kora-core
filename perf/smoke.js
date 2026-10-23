/**
 * smoke.js — Smoke test : 1 VU, 2 minutes.
 *
 * Objectif : sanité du système, pas de performance.
 * Couvre les 5 endpoints du mix (scenarios/mix.js) :
 *   cashIn 35% | transfer 30% | cashOut 15% | balance 10% | history 10%
 *
 * Thresholds souples (smoke = "ça marche" pas "ça performe") :
 *   - p(95) < 2 500ms  (au-dessus du plafond provider, on détecte les pannes)
 *   - error rate < 5%
 *
 * Lancer : ./perf/smoke-run.sh
 */

import { sleep } from 'k6';
import { createTestUsers } from './data/setup.js';
import { runMix } from './scenarios/mix.js';

export const options = {
    vus:      1,
    duration: '2m',
    thresholds: {
        // Provider ceiling: authorize(800ms+40%) + capture(600ms+40%) + overhead ≈ 2 100ms max.
        http_req_duration: ['p(95)<2500'],
        http_req_failed:   ['rate<0.05'],
    },
};

export function setup() {
    return createTestUsers(2, 'smoke');
}

export default function (data) {
    const users = data.users;
    if (!users || users.length < 2) {
        console.error('Setup incomplet — pas assez d\'utilisateurs');
        return;
    }

    runMix(users[0], users[1]);
    sleep(1);
}
