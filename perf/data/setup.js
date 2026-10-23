/**
 * setup.js — Création et pré-authentification des users de test, par lots.
 *
 * Flow par lot de BATCH_SIZE users, chaque étape en parallèle via http.batch :
 *   register → captureOtp → verify-otp → seed cashIn → stocke { email, pin,
 *   phonePrefix, phoneNumber, fullPhone, accessToken, refreshToken, tokenExpiresAt }
 *
 * Pourquoi par lots : à ~2.7s par user en séquentiel, 400 users coûtaient 18 min
 * de setup, et les premiers tokens expiraient pendant le run — une vague de
 * re-login (BCrypt + OTP + mail) au milieu des mesures. En lots de 20, un lot
 * coûte ~3s (le seed cashIn domine), soit ~1 min pour 400 users.
 *
 * tokenExpiresAt en secondes depuis epoch (évite corruption des grands
 * entiers par la sérialisation JSON Go/k6 entre setup() et default()).
 * Il est fixé entre 10 et 14 min (access token : 15 min) avec une part
 * aléatoire, pour étaler les refresh au lieu de les synchroniser.
 *
 * Seed cashIn (100 000 XAF) : garantit un solde initial suffisant pour toute
 * séquence aléatoire de cashOut/transfer avant le premier cashIn du scénario.
 * Sans ce seed, un VU qui tire cashOut ou transfer en première itération échoue
 * systématiquement (solde = 0 → InsufficientFundsException).
 */

import http from 'k6/http';
import { sleep } from 'k6';

const BASE_URL     = __ENV.BASE_URL || 'http://localhost:8081';
const HEADERS_JSON = { 'Content-Type': 'application/json' };
const SETUP_PARAMS = { headers: HEADERS_JSON, tags: { operation: 'setup' } };

const PHONE_PREFIX = '+237';
const PIN          = '123456';
const SEED_AMOUNT  = 100_000;
export const BATCH_SIZE = 20;

// Plages de numéros isolées par type de test — évite les collisions entre tests parallèles.
// Formule : phoneBase + parseInt(RUN_SUFFIX) × 100 + i. Bases espacées de 20 M.
const PHONE_RANGES = {
    smoke:  600_000_000,
    load:   620_000_000,
    stress: 640_000_000,
    soak:   660_000_000,
};

// RUN_SUFFIX — 5 derniers chiffres du timestamp en SECONDES.
const RUN_SUFFIX = String(Math.floor(Date.now() / 1000)).slice(-5);

// RUN_ID — unicité des adresses email entre runs.
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

/**
 * Crée N utilisateurs via l'API, les pré-authentifie par lots,
 * et retourne leurs profils avec token JWT prêt à l'emploi.
 *
 * @param {number} n           nombre d'utilisateurs à créer
 * @param {string} [prefix]    préfixe d'email pour ce run (défaut: "user")
 * @returns {{ users: Array }}
 */
export function createTestUsers(n, prefix = 'user') {
    const phoneBase = PHONE_RANGES[prefix] || 600_080_000;
    const users     = [];

    for (let start = 1; start <= n; start += BATCH_SIZE) {
        const candidates = [];
        for (let i = start; i < start + BATCH_SIZE && i <= n; i++) {
            const phoneNumber = String(phoneBase + parseInt(RUN_SUFFIX) * 100 + i);
            candidates.push({
                index: i,
                email: `${prefix}-${RUN_ID}-${i}@kora.perf`,
                phoneNumber,
                fullPhone: `${PHONE_PREFIX}${phoneNumber}`,
            });
        }
        users.push(...onboard(candidates));
        console.log(`[setup] ${users.length}/${n} utilisateurs prêts`);
    }

    console.log(`[setup] ${users.length}/${n} utilisateurs prêts (RUN_ID=${RUN_ID})`);
    return { users };
}

function onboard(candidates) {
    const registered = register(candidates);
    const withOtp    = captureOtps(registered);
    const verified   = verify(withOtp);
    seed(verified);

    const nowSec = Math.floor(Date.now() / 1000);
    return verified.map((c) => ({
        email:          c.email,
        pin:            PIN,
        phonePrefix:    PHONE_PREFIX,
        phoneNumber:    c.phoneNumber,
        fullPhone:      c.fullPhone,
        accessToken:    c.accessToken,
        refreshToken:   c.refreshToken,
        tokenExpiresAt: nowSec + 10 * 60 + Math.floor(Math.random() * 4 * 60),
    }));
}

function register(candidates) {
    const responses = http.batch(candidates.map((c) => ({
        method: 'POST',
        url:    `${BASE_URL}/auth/register`,
        body:   JSON.stringify({
            fullName:    `Perf User ${c.index}`,
            email:       c.email,
            phonePrefix: PHONE_PREFIX,
            phoneNumber: c.phoneNumber,
            rawPin:      PIN,
        }),
        params: SETUP_PARAMS,
    })));

    // 201 = inscrit, 409 = déjà inscrit (idempotence)
    return candidates.filter((c, i) => {
        const status = responses[i].status;
        if (status === 201 || status === 409) return true;
        console.error(`[setup] register échoué pour ${c.email}: ${status} ${responses[i].body}`);
        return false;
    });
}

function captureOtps(candidates) {
    let pending = candidates;
    const ready = [];

    for (let attempt = 0; attempt < 20 && pending.length > 0; attempt++) {
        const responses = http.batch(pending.map((c) => ({
            method: 'GET',
            url:    `${BASE_URL}/test/otp/${encodeURIComponent(c.email)}`,
            params: SETUP_PARAMS,
        })));

        const stillPending = [];
        pending.forEach((c, i) => {
            if (responses[i].status === 200) ready.push({ ...c, otp: JSON.parse(responses[i].body).code });
            else stillPending.push(c);
        });
        pending = stillPending;
        if (pending.length > 0) sleep(0.5);
    }

    pending.forEach((c) => {
        console.error(`[setup] OTP timeout pour ${c.email} — /test/otp endpoint retourne 404 ?`);
        console.error(`[setup] Vérifier : SPRING_PROFILES_ACTIVE=perf et curl ${BASE_URL}/test/otp/${encodeURIComponent(c.email)}`);
    });
    return ready;
}

function verify(candidates) {
    const responses = http.batch(candidates.map((c) => ({
        method: 'POST',
        url:    `${BASE_URL}/auth/verify-otp`,
        body:   JSON.stringify({ email: c.email, code: c.otp }),
        params: SETUP_PARAMS,
    })));

    const verified = [];
    candidates.forEach((c, i) => {
        const res = responses[i];
        if (res.status !== 200) {
            console.error(`[setup] verify-otp échoué pour ${c.email}: ${res.status}`);
            return;
        }
        let body;
        try { body = JSON.parse(res.body); } catch (e) {
            console.error(`[setup] parse token échoué pour ${c.email}: ${e}`);
            return;
        }
        if (!body.accessToken) {
            console.error(`[setup] accessToken absent dans verify-otp pour ${c.email}: ${res.body}`);
            return;
        }
        verified.push({ ...c, accessToken: body.accessToken, refreshToken: body.refreshToken });
    });
    return verified;
}

// 100 000 XAF : couvre N itérations cashOut (5 000) + transfer (2 000) avant le
// premier cashIn du scénario. Le mix net est positif, le solde croît ensuite.
function seed(candidates) {
    const responses = http.batch(candidates.map((c) => ({
        method: 'POST',
        url:    `${BASE_URL}/payments/cash-in`,
        body:   JSON.stringify({ rawPin: PIN, amount: SEED_AMOUNT, currency: 'XAF', paymentMethod: 'ORANGE_MONEY' }),
        params: {
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${c.accessToken}` },
            tags:    { operation: 'setup' },
        },
    })));

    candidates.forEach((c, i) => {
        if (responses[i].status !== 200) {
            console.error(`[setup] seed cashIn échoué pour ${c.email}: ${responses[i].status} ${responses[i].body}`);
        }
    });
}
