/**
 * stress.js — Stress test : identification du point de rupture au-delà du Step 3.
 *
 * Objectif : pousser la charge par paliers au-delà de la cible Step 3
 * (80–100 req/s) jusqu'à ce que le système se dégrade. On observe.
 *
 * Profil par paliers (2 min chacun, 1 min de transition) :
 *   25 → 50 → 80 → 100 → 130 → 160 → 200 req/s
 *
 * Plafonds attendus, à confirmer par le run :
 *   - CPU : BCrypt (strength 10) sur chaque paiement, ~60–80ms CPU par appel ;
 *     à 90% de paiements, ~0.9 × débit × 70ms cœurs occupés.
 *   - Threads Tomcat (200) : le stub provider dort ~1.9s en tenant son thread ;
 *     0.5 × débit × 1.9s threads occupés → saturation vers ~200 req/s.
 *   - HikariCP (30) : loin derrière, chaque connexion est tenue < 100ms.
 *
 * Arrêt conditionnel : p(95) > 5 000ms OU error rate > 5% (abortOnFail).
 *
 * Métriques clés à observer dans Grafana, par palier :
 *   - latence p95 / p99 par opération (balance, history, transfer, cash)
 *   - dropped_iterations (VUs épuisés = le système ne suit plus)
 *   - process_cpu_usage, hikaricp_connections_pending, et tomcat_threads_busy
 *     si server.tomcat.mbeanregistry.enabled=true
 *
 * Lancer : ./perf/stress-run.sh
 */

import { BATCH_SIZE, createTestUsers } from './data/setup.js';
import { pickPair, runMix } from './scenarios/mix.js';

// 200 req/s × ~1.0s ≈ 200 VUs en régime sain ; 400 laissent voir la dégradation
// avant que k6 ne commence à abandonner des itérations.
const USER_COUNT = 400;

export const options = {
    setupTimeout:  '10m',
    batchPerHost:  BATCH_SIZE,
    scenarios: {
        stress: {
            executor:        'ramping-arrival-rate',
            startRate:       0,
            timeUnit:        '1s',
            preAllocatedVUs: 150,
            maxVUs:          USER_COUNT,
            stages: [
                { target: 25,  duration: '1m'  },  // warm-up
                { target: 25,  duration: '2m'  },  // palier 1 : 25 req/s
                { target: 50,  duration: '1m'  },
                { target: 50,  duration: '2m'  },  // palier 2 : 50 req/s
                { target: 80,  duration: '1m'  },
                { target: 80,  duration: '2m'  },  // palier 3 : 80 req/s  (bas Step 3)
                { target: 100, duration: '1m'  },
                { target: 100, duration: '2m'  },  // palier 4 : 100 req/s (haut Step 3)
                { target: 130, duration: '1m'  },
                { target: 130, duration: '2m'  },  // palier 5 : 130 req/s
                { target: 160, duration: '1m'  },
                { target: 160, duration: '2m'  },  // palier 6 : 160 req/s
                { target: 200, duration: '1m'  },
                { target: 200, duration: '2m'  },  // palier 7 : 200 req/s
                { target: 0,   duration: '30s' },  // cool-down
            ],
        },
    },
    // Seuils d'arrêt : au-dessus du plafond provider sain (~2 100ms), ils ne se
    // déclenchent qu'en cas de vraie dégradation (queuing, pool, threads).
    //
    // Les seuils « p(95)>=0 » sont toujours vrais : ils n'existent que pour que k6
    // affiche les sous-métriques par opération dans le résumé de fin de run.
    thresholds: {
        http_req_duration: [
            { threshold: 'p(95)<5000', abortOnFail: true, delayAbortEval: '30s' },
        ],
        http_req_failed: [
            { threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' },
        ],
        'http_req_duration{operation:balance}':  ['p(95)>=0'],
        'http_req_duration{operation:history}':  ['p(95)>=0'],
        'http_req_duration{operation:transfer}': ['p(95)>=0'],
        'http_req_duration{operation:cash}':     ['p(95)>=0'],
    },
};

export function setup() {
    return createTestUsers(USER_COUNT, 'stress');
}

export default function (data) {
    const users = data.users;
    if (!users || users.length === 0) return;

    const [userA, userB] = pickPair(users);
    runMix(userA, userB);
}
