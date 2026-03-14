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
 *       fullPhone: string,      // préfixe + numéro local — utilisé pour les transferts
 *       accessToken: string,    // token JWT frais issu du seed — réutilisé par getToken()
 *       tokenExpiresAt: number, // timestamp ms d'expiry (14 min) — getToken() renouvelle si dépassé
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

const PHONE_PREFIX   = '+237';
const PIN            = '123456';
const SEED_AMOUNT    = 200_000;       // XOF — suffisant pour toute la durée d'un soak test
const PAYMENT_METHOD = 'ORANGE_MONEY';

// Plages de numéros isolées par type de test — évite les collisions entre tests parallèles.
// Formule : phoneBase + parseInt(RUN_SUFFIX) × 100 + i
// Chaque plage absorbe max RUN_SUFFIX(99999) × 100 + users_max = 9 999 940 numéros → 10 M.
// Bases espacées de 20 M pour garantir l'absence de chevauchement.
// Vérification (9 chiffres, contrainte 8-15 respectée) :
//   smoke  : 600000001 → 609999902   load   : 620000001 → 629999920
//   stress : 640000001 → 649999940   soak   : 660000001 → 669999915
const PHONE_RANGES = {
    smoke:  600_000_000,
    load:   620_000_000,
    stress: 640_000_000,
    soak:   660_000_000,
};

// RUN_SUFFIX — 5 derniers chiffres du timestamp en SECONDES (évalué à l'init du module).
// Cycle de 100 000 secondes ≈ 27,8 heures → collision pratiquement impossible.
// Multiplié par 100 pour réserver 2 chiffres à l'index utilisateur (max 40).
const RUN_SUFFIX = String(Math.floor(Date.now() / 1000)).slice(-5);

// RUN_ID — unicité des adresses email entre runs (passez __ENV.RUN_ID pour rejouer).
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

/**
 * Crée N utilisateurs via l'API, seed leur solde, et retourne leurs profils.
 *
 * @param {number} n           nombre d'utilisateurs à créer
 * @param {string} [prefix]    préfixe d'email pour ce run (défaut: "user")
 * @returns {{ users: Array }}
 */
export function createTestUsers(n, prefix = 'user') {
    const users    = [];
    const phoneBase = PHONE_RANGES[prefix] || 600_080_000;

    for (let i = 1; i <= n; i++) {
        const phoneNumber = String(phoneBase + parseInt(RUN_SUFFIX) * 100 + i);
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

        console.log(`[setup] register ${email} phone=${phoneNumber} → HTTP ${regRes.status}`);

        // 201 = inscrit, 409 = déjà inscrit (idempotence)
        if (regRes.status !== 201 && regRes.status !== 409) {
            console.error(`[setup] register échoué pour ${email}: ${regRes.status} ${regRes.body}`);
            continue;
        }

        // ── Capture OTP ─────────────────────────────────────────────────────
        const otp = captureOtp(email);
        if (!otp) {
            console.error(`[setup] OTP non trouvé pour ${email} — /test/otp endpoint retourne 404 ?`);
            console.error(`[setup] Vérifier : SPRING_PROFILES_ACTIVE=perf et curl http://localhost:8081/test/otp/${encodeURIComponent(email)}`);
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
            console.error(`[setup] seed cashIn échoué pour ${email}: ${cashInRes.status} — user exclu`);
            continue;  // ne pas pousser un user sans solde seed
        }

        users.push({
            email, pin: PIN, phonePrefix: PHONE_PREFIX, phoneNumber, fullPhone,
            accessToken,
            tokenExpiresAt: Math.floor(Date.now() / 1000) + 14 * 60,  // secondes — safe via sérialisation k6
        });

        // Légère pause pour ne pas saturer le setup
        sleep(0.05);
    }

    console.log(`[setup] ${users.length}/${n} utilisateurs prêts (RUN_ID=${RUN_ID})`);
    return { users };
}

// ── Helpers internes ──────────────────────────────────────────────────────────

function captureOtp(email) {
    for (let i = 0; i < 20; i++) {
        const r = http.get(`${BASE_URL}/test/otp/${encodeURIComponent(email)}`);
        if (r.status === 200) {
            return JSON.parse(r.body).code;
        }
        sleep(0.5);
    }
    return null;
}