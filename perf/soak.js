/**
 * soak.js — Soak test : détection de fuites mémoire et dégradation progressive.
 *
 * Objectif : maintenir une charge constante pendant 30 minutes pour détecter :
 *   - Fuites mémoire JVM (heap drift)
 *   - DB pool exhaustion (hikaricp_connections_pending croissant)
 *   - Dégradation de latence dans le temps (p95 drift)
 *   - Connexions non libérées, memory leaks dans les caches
 *
 * Profil :
 *   5 req/sec constant pendant 30 minutes
 *
 * Thresholds (plus souples que load — on cherche la dégradation progressive) :
 *   p(95) < 300ms sur la durée totale
 *   error rate < 1%
 *
 * Surveiller dans Grafana pendant le run :
 *   - jvm_memory_used_bytes doit rester stable (pas de tendance haussière)
 *   - hikaricp_connections_pending doit rester à 0
 *   - http_req_duration p95 ne doit pas dériver dans le temps
 *
 * Lancer : ./perf/soak-run.sh
 */

import { sleep }            from 'k6';
import { Trend }            from 'k6/metrics';
import { createTestUsers }  from './data/setup.js';
import { scenarioCashIn }   from './scenarios/cashIn.js';
import { scenarioCashOut }  from './scenarios/cashOut.js';
import { scenarioTransfer } from './scenarios/transfer.js';
import { scenarioBalance }  from './scenarios/balance.js';

// 40 = maxVUs — garantit 1 user par VU, élimine la contention OTP
const USER_COUNT = 40;

// Métrique custom : latence dans le temps (pour détecter drift dans Grafana)
const latencyTrend = new Trend('soak_latency_trend', true);

// ── Config k6 ─────────────────────────────────────────────────────────────────

export const options = {
    scenarios: {
        soak: {
            executor:        'constant-arrival-rate',
            rate:            5,
            timeUnit:        '1s',
            duration:        '30m',
            preAllocatedVUs: 20,
            maxVUs:          40,
        },
    },
    thresholds: {
        // Tolérance plus large que load — on cherche la dégradation sur la durée
        http_req_duration:   ['p(95)<300'],
        http_req_failed:     ['rate<0.01'],
        soak_latency_trend:  ['p(95)<300'],
    },
};

// ── Setup ─────────────────────────────────────────────────────────────────────

export function setup() {
    return createTestUsers(USER_COUNT, 'soak');
}

// ── Scénario ──────────────────────────────────────────────────────────────────

export default function (data) {
    const users = data.users;
    if (!users || users.length === 0) return;

    const idx   = __VU % users.length;
    const userA = users[idx];
    const userB = users[(idx + 1) % users.length];

    const start = Date.now();

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

    latencyTrend.add(Date.now() - start);
}