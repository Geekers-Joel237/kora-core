/**
 * stress.js — Stress test : identification du point de rupture.
 *
 * Objectif : pousser progressivement la charge jusqu'à ce que le système
 * se dégrade significativement. Pas de thresholds stricts — on observe.
 *
 * Profil par paliers (3 min chacun) :
 *   5 → 10 → 20 → 30 → 50 req/sec
 *
 * Arrêt conditionnel k6 : via abortOnFail sur les thresholds d'observation.
 *   p(95) > 500ms OU error rate > 5% → le test s'arrête.
 *   Ces thresholds sont en "abortOnFail" pour ne pas masquer la rupture.
 *
 * Métriques clés à observer dans Grafana :
 *   - latence p95 / p99 par palier
 *   - error rate par palier
 *   - hikaricp_connections_pending (saturation DB pool)
 *   - jvm_memory_used_bytes (GC pressure)
 *
 * Lancer : ./perf/stress-run.sh
 */

import { sleep }            from 'k6';
import { createTestUsers }  from './data/setup.js';
import { scenarioCashIn }   from './scenarios/cashIn.js';
import { scenarioCashOut }  from './scenarios/cashOut.js';
import { scenarioTransfer } from './scenarios/transfer.js';
import { scenarioBalance }  from './scenarios/balance.js';

// 200 = maxVUs — garantit 1 user par VU, élimine tout partage et contention OTP
const USER_COUNT = 200;

// ── Config k6 ─────────────────────────────────────────────────────────────────

export const options = {
    // 200 users × ~2.7s (register + OTP + verify + seed cashIn) ≈ 9min.
    setupTimeout: '15m',
    scenarios: {
        stress: {
            executor:        'ramping-arrival-rate',
            startRate:       0,
            timeUnit:        '1s',
            preAllocatedVUs: 80,
            maxVUs:          200,
            stages: [
                { target: 5,  duration: '1m'  },  // warm-up léger
                { target: 5,  duration: '3m'  },  // palier 1 : 5 req/s
                { target: 10, duration: '1m'  },  // transition
                { target: 10, duration: '3m'  },  // palier 2 : 10 req/s
                { target: 20, duration: '1m'  },  // transition
                { target: 20, duration: '3m'  },  // palier 3 : 20 req/s
                { target: 30, duration: '1m'  },  // transition
                { target: 30, duration: '3m'  },  // palier 4 : 30 req/s
                { target: 50, duration: '1m'  },  // transition
                { target: 50, duration: '3m'  },  // palier 5 : 50 req/s
                { target: 0,  duration: '30s' },  // cool-down
            ],
        },
    },
    // Seuils d'observation (abortOnFail = arrêt conditionnel à la rupture)
    //
    // Provider ceiling normal : ~2 100ms. Le seuil abortOnFail à 5 000ms est au-dessus
    // du comportement sain — il ne se déclenche qu'en cas de vraie dégradation :
    //   sain      : p(95) ≈ 2s  (provider ceiling, pas de contention)
    //   dégradé   : p(95) > 3–5s (queuing pool / lock contention)
    //   rupture   : p(95) > 10s+ (pool exhaustion, timeouts)
    //
    // Un seuil à 500ms s'arrêterait au premier cashIn (1.5s) → le test ne peut
    // jamais atteindre les paliers supérieurs.
    thresholds: {
        http_req_duration: [
            { threshold: 'p(95)<5000', abortOnFail: true, delayAbortEval: '30s' },
        ],
        http_req_failed: [
            { threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' },
        ],
    },
};

// ── Setup ─────────────────────────────────────────────────────────────────────

export function setup() {
    return createTestUsers(USER_COUNT, 'stress');
}

// ── Scénario ──────────────────────────────────────────────────────────────────

export default function (data) {
    const users = data.users;
    if (!users || users.length === 0) return;

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
}