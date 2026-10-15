/**
 * mix.js — Mix métier commun aux quatre tests (Step 3).
 *
 *   cashIn 35% | transfer 30% | cashOut 15% | balance 10% | history 10%
 *
 * 50% d'opérations provider-bound (cashIn + cashOut) contre 55% au Step 1 :
 * l'historique entre dans le mix, pris sur cashIn et transfer.
 * Durée moyenne d'une itération ≈ 0.50 × 1.85s + 0.30 × 0.17s ≈ 1.0s,
 * d'où VUs nécessaires ≈ débit cible (loi de Little).
 */

import { scenarioCashIn }   from './cashIn.js';
import { scenarioCashOut }  from './cashOut.js';
import { scenarioTransfer } from './transfer.js';
import { scenarioBalance }  from './balance.js';
import { scenarioHistory }  from './history.js';

const MIX = [
    { weight: 0.35, run: (a)    => scenarioCashIn(a) },
    { weight: 0.30, run: (a, b) => scenarioTransfer(a, b) },
    { weight: 0.15, run: (a)    => scenarioCashOut(a) },
    { weight: 0.10, run: (a)    => scenarioBalance(a) },
    { weight: 0.10, run: (a)    => scenarioHistory(a) },
];

export function runMix(userA, userB) {
    let roll = Math.random();
    for (const op of MIX) {
        if (roll < op.weight) return op.run(userA, userB);
        roll -= op.weight;
    }
    return MIX[MIX.length - 1].run(userA, userB);
}

/**
 * Chaque VU possède son user ; le destinataire est le user du VU voisin.
 * Il faut USER_COUNT >= maxVUs, sinon deux VUs partagent un compte et
 * se disputent son verrou.
 */
export function pickPair(users) {
    const idx = __VU % users.length;
    return [users[idx], users[(idx + 1) % users.length]];
}
