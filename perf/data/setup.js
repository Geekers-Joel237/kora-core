/**
 * setup.js — Création et pré-authentification séquentielle des users de test.
 *
 * Flow par user :
 *   register → captureOtp → verify-otp → stocke { email, pin, phonePrefix,
 *   phoneNumber, fullPhone, accessToken, tokenExpiresAt }
 *
 * tokenExpiresAt en secondes depuis epoch (évite corruption des grands
 * entiers par la sérialisation JSON Go/k6 entre setup() et default()).
 *
 * Le seed cashIn N'EST PAS fait ici. Le premier scénario cashIn de chaque
 * VU constitue le seed implicite (montant suffisant).
 */

import http from 'k6/http';
import { sleep } from 'k6';

const BASE_URL     = __ENV.BASE_URL || 'http://localhost:8081';
const HEADERS_JSON = { 'Content-Type': 'application/json' };

const PHONE_PREFIX = '+237';
const PIN          = '123456';

// Plages de numéros isolées par type de test — évite les collisions entre tests parallèles.
// Formule : phoneBase + parseInt(RUN_SUFFIX) × 100 + i
// Chaque plage absorbe max RUN_SUFFIX(99999) × 100 + users_max = 9 999 940 numéros → 10 M.
// Bases espacées de 20 M pour garantir l'absence de chevauchement.
const PHONE_RANGES = {
    smoke:  600_000_000,
    load:   620_000_000,
    stress: 640_000_000,
    soak:   660_000_000,
};

// RUN_SUFFIX — 5 derniers chiffres du timestamp en SECONDES.
// Cycle de 100 000 secondes ≈ 27,8 heures → collision pratiquement impossible.
const RUN_SUFFIX = String(Math.floor(Date.now() / 1000)).slice(-5);

// RUN_ID — unicité des adresses email entre runs.
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

/**
 * Crée N utilisateurs via l'API, les pré-authentifie séquentiellement,
 * et retourne leurs profils avec token JWT prêt à l'emploi.
 *
 * @param {number} n           nombre d'utilisateurs à créer
 * @param {string} [prefix]    préfixe d'email pour ce run (défaut: "user")
 * @returns {{ users: Array }}
 */
export function createTestUsers(n, prefix = 'user') {
    const users     = [];
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

        console.log(`[setup] register ${email} → HTTP ${regRes.status}`);

        // 201 = inscrit, 409 = déjà inscrit (idempotence)
        if (regRes.status !== 201 && regRes.status !== 409) {
            console.error(`[setup] register échoué pour ${email}: ${regRes.status} ${regRes.body}`);
            continue;
        }

        // ── Capture OTP ─────────────────────────────────────────────────────
        const otp = captureOtp(email);
        if (!otp) {
            console.error(`[setup] OTP timeout pour ${email} — /test/otp endpoint retourne 404 ?`);
            console.error(`[setup] Vérifier : SPRING_PROFILES_ACTIVE=perf et curl http://localhost:8081/test/otp/${encodeURIComponent(email)}`);
            continue;
        }

        // ── Verify OTP → token ──────────────────────────────────────────────
        const verifyRes = http.post(
            `${BASE_URL}/auth/verify-otp`,
            JSON.stringify({ email, code: otp }),
            { headers: HEADERS_JSON }
        );

        if (verifyRes.status !== 200) {
            console.error(`[setup] verify-otp échoué pour ${email}: ${verifyRes.status}`);
            continue;
        }

        let accessToken;
        try {
            accessToken = JSON.parse(verifyRes.body).accessToken;
        } catch (e) {
            console.error(`[setup] parse token échoué pour ${email}: ${e}`);
            continue;
        }

        if (!accessToken) {
            console.error(`[setup] accessToken absent dans verify-otp pour ${email}`);
            console.log(`[setup] verify-otp body: ${verifyRes.body}`);
            continue;
        }

        users.push({
            email,
            pin:         PIN,
            phonePrefix: PHONE_PREFIX,
            phoneNumber,
            fullPhone,
            accessToken,
            // secondes depuis epoch — entier 10 chiffres, safe via sérialisation k6
            tokenExpiresAt: Math.floor(Date.now() / 1000) + 14 * 60,
        });

        sleep(0.1);  // légère pause entre users — réduit la pression sur l'OTP store
    }

    console.log(`[setup] ${users.length}/${n} utilisateurs prêts (RUN_ID=${RUN_ID})`);
    return { users };
}

// ── Helpers internes ──────────────────────────────────────────────────────────

function captureOtp(email) {
    for (let i = 0; i < 20; i++) {
        const r = http.get(`${BASE_URL}/test/otp/${encodeURIComponent(email)}`);
        if (r.status === 200) return JSON.parse(r.body).code;
        sleep(0.5);
    }
    return null;
}