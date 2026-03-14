/**
 * setup.js — Création des utilisateurs de test et seed des soldes.
 *
 * Appelé par la fonction k6 `setup()` de chaque script de test.
 * Idempotent : un utilisateur déjà inscrit retourne une erreur 409 ignorée.
 *
 * Résultat retourné à k6 :
 * {
 *   users: [
 *     {
 *       email: string,
 *       pin: string,
 *       phonePrefix: string,
 *       phoneNumber: string,
 *       fullPhone: string,    // préfixe + numéro local — utilisé pour les transferts
 *     },
 *     ...
 *   ]
 * }
 *
 * Chaque utilisateur reçoit un seed cashIn de 200 000 XOF
 * pour garantir que cashOut (5 000) et transfer (2 000) ne s'arrêtent jamais
 * sur un solde insuffisant pendant toute la durée du test.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const HEADERS_JSON = { 'Content-Type': 'application/json' };

// ── Constantes ────────────────────────────────────────────────────────────────

const PHONE_PREFIX  = '+237';
const PHONE_BASE    = 600_000_000;   // 600000001, 600000002, …
const PIN           = '123456';
const SEED_AMOUNT   = 200_000;       // XOF — suffisant pour toute la durée d'un soak test
const PAYMENT_METHOD = 'ORANGE_MONEY';

// RUN_ID — différencie les runs pour éviter les conflits de comptes entre les runs
// On utilise une graine fixe passée par variable d'env pour pouvoir rejouer un run.
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

/**
 * Crée N utilisateurs via l'API, seed leur solde, et retourne leurs profils.
 *
 * @param {number} n           nombre d'utilisateurs à créer
 * @param {string} [prefix]    préfixe d'email pour ce run (défaut: "user")
 * @returns {{ users: Array }}
 */
export function createTestUsers(n, prefix = 'user') {
    const users = [];

    for (let i = 1; i <= n; i++) {
        const phoneNumber = String(PHONE_BASE + i);
        const email       = `${prefix}-${RUN_ID}-${i}@kora.perf`;
        const fullPhone   = `${PHONE_PREFIX}${phoneNumber}`;

        // ── Register ────────────────────────────────────────────────────────
        const regRes = http.post(
            `${BASE_URL}/auth/register`,
            JSON.stringify({
                fullName:    `Perf User ${i}`,
                email,
                phonePrefix: PHONE_PREFIX,
                phoneNumber,
                rawPin:      PIN,
            }),
            { headers: HEADERS_JSON }
        );

        // 200 = inscrit, 409 = déjà inscrit (idempotence)
        if (regRes.status !== 200 && regRes.status !== 409) {
            console.error(`[setup] register échoué pour ${email}: ${regRes.status} ${regRes.body}`);
            continue;
        }

        // ── Capture OTP ─────────────────────────────────────────────────────
        const otp = captureOtp(email);
        if (!otp) {
            console.error(`[setup] OTP non trouvé pour ${email}`);
            continue;
        }

        // ── Verify OTP → tokens ─────────────────────────────────────────────
        const verifyRes = http.post(
            `${BASE_URL}/auth/verify-otp`,
            JSON.stringify({ email, code: otp }),
            { headers: HEADERS_JSON }
        );

        if (verifyRes.status !== 200) {
            console.error(`[setup] verify-otp échoué pour ${email}: ${verifyRes.status}`);
            continue;
        }

        const { accessToken } = JSON.parse(verifyRes.body);

        // ── Seed balance ─────────────────────────────────────────────────────
        const cashInRes = http.post(
            `${BASE_URL}/payments/cash-in`,
            JSON.stringify({
                rawPin:        PIN,
                amount:        SEED_AMOUNT,
                currency:      'XOF',
                paymentMethod: PAYMENT_METHOD,
            }),
            {
                headers: {
                    'Content-Type':  'application/json',
                    'Authorization': `Bearer ${accessToken}`,
                },
            }
        );

        if (cashInRes.status !== 200) {
            console.warn(`[setup] seed cashIn échoué pour ${email}: ${cashInRes.status}`);
        }

        users.push({ email, pin: PIN, phonePrefix: PHONE_PREFIX, phoneNumber, fullPhone });

        // Légère pause pour ne pas saturer le setup
        sleep(0.05);
    }

    console.log(`[setup] ${users.length}/${n} utilisateurs prêts (RUN_ID=${RUN_ID})`);
    return { users };
}

// ── Helpers internes ──────────────────────────────────────────────────────────

function captureOtp(email) {
    for (let i = 0; i < 15; i++) {
        const r = http.get(`${BASE_URL}/test/otp/${encodeURIComponent(email)}`);
        if (r.status === 200) {
            return JSON.parse(r.body).code;
        }
        sleep(0.3);
    }
    return null;
}