/**
 * auth.js — Helpers d'authentification réutilisables entre tous les scénarios.
 *
 * getToken() utilise le token pré-obtenu dans setup() tant qu'il est valide,
 * puis le renouvelle par POST /auth/refresh — pas de BCrypt ni d'OTP, donc le
 * renouvellement ne pèse pas sur les latences mesurées. login() ne sert plus
 * que de repli si le refresh est refusé.
 *
 * Les requêtes d'auth portent le tag operation:auth : elles restent hors des
 * thresholds par opération.
 *
 * tokenExpiresAt en secondes depuis epoch (pas en ms) — évite la corruption des
 * grands entiers par la sérialisation JSON entre setup() et default() dans k6.
 *
 * Tous les flows OTP passent par /test/otp/{email}
 * (endpoint @Profile("perf") — TestSupportAction).
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL     = __ENV.BASE_URL || 'http://localhost:8081';
const HEADERS_JSON = { 'Content-Type': 'application/json' };

/**
 * Retourne un accessToken valide.
 *
 * Priorité :
 *   1. Token de setup() si non expiré (nowSec < user.tokenExpiresAt)
 *   2. refresh() si expiré
 *   3. login() si le refresh échoue
 *
 * Mutations sur user.* restent locales au VU (k6 désérialise setup() data par
 * VU — pas de partage entre VUs). Le refresh token tourne à chaque appel : seul
 * le VU propriétaire du user le renouvelle.
 *
 * @param {object} user  { email, pin, accessToken, refreshToken, tokenExpiresAt }
 * @returns {string}     accessToken valide
 */
export function getToken(user) {
    const nowSec = Math.floor(Date.now() / 1000);
    if (user.accessToken && nowSec < user.tokenExpiresAt) {
        return user.accessToken;
    }
    const tokens = (user.refreshToken && refresh(user.refreshToken)) || login(user.email, user.pin);
    user.accessToken    = tokens.accessToken;
    user.refreshToken   = tokens.refreshToken;
    user.tokenExpiresAt = Math.floor(Date.now() / 1000) + 14 * 60;
    return user.accessToken;
}

/**
 * POST /auth/refresh. Retourne null si refusé, pour laisser login() prendre le relais.
 *
 * @param {string} refreshToken
 * @returns {{ accessToken: string, refreshToken: string } | null}
 */
export function refresh(refreshToken) {
    const res = http.post(
        `${BASE_URL}/auth/refresh`,
        JSON.stringify({ refreshToken }),
        { headers: HEADERS_JSON, tags: { operation: 'auth' } }
    );
    check(res, { 'refresh 200': (r) => r.status === 200 });
    if (res.status !== 200) return null;

    const body = JSON.parse(res.body);
    return { accessToken: body.accessToken, refreshToken: body.refreshToken };
}

/**
 * Login complet : POST /auth/login → captureOtp → POST /auth/verify-otp.
 *
 * @param {string} email
 * @param {string} pin
 * @returns {{ accessToken: string, refreshToken: string }}
 */
export function login(email, pin) {
    const loginRes = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({ email, rawPin: pin }),
        { headers: HEADERS_JSON, tags: { operation: 'auth' } }
    );
    check(loginRes, { 'login 200': (r) => r.status === 200 });

    const otp = captureOtp(email);

    const verifyRes = http.post(
        `${BASE_URL}/auth/verify-otp`,
        JSON.stringify({ email, code: otp }),
        { headers: HEADERS_JSON, tags: { operation: 'auth' } }
    );
    check(verifyRes, { 'verify-otp (login) 200': (r) => r.status === 200 });

    const body = JSON.parse(verifyRes.body);
    return { accessToken: body.accessToken, refreshToken: body.refreshToken };
}

/**
 * Construit le header Authorization pour les endpoints protégés.
 * @param {string} token
 * @returns {object}
 */
export function authHeaders(token) {
    return {
        'Content-Type':  'application/json',
        'Authorization': `Bearer ${token}`,
    };
}

/**
 * Récupère le code OTP depuis /test/otp/{email} avec retry (max 20 × 500ms).
 * L'endpoint est exposé uniquement avec le profil "perf".
 *
 * @param {string} email
 * @returns {string} code OTP
 */
function captureOtp(email) {
    for (let i = 0; i < 20; i++) {
        const r = http.get(`${BASE_URL}/test/otp/${encodeURIComponent(email)}`, { tags: { operation: 'auth' } });
        if (r.status === 200) return JSON.parse(r.body).code;
        sleep(0.5);
    }
    // Dernier essai — si echec, le check suivant échouera
    const r = http.get(`${BASE_URL}/test/otp/${encodeURIComponent(email)}`, { tags: { operation: 'auth' } });
    check(r, { 'otp retrieved': (res) => res.status === 200 });
    return JSON.parse(r.body).code;
}

// register() conservé pour compatibilité — non utilisé dans les scénarios perf courants.
export function register(fullName, email, phonePrefix, phoneNumber, pin) {
    const regRes = http.post(
        `${BASE_URL}/auth/register`,
        JSON.stringify({ fullName, email, phonePrefix, phoneNumber, rawPin: pin }),
        { headers: HEADERS_JSON }
    );
    check(regRes, { 'register 201': (r) => r.status === 201 });
    const otp = captureOtp(email);
    const verifyRes = http.post(
        `${BASE_URL}/auth/verify-otp`,
        JSON.stringify({ email, code: otp }),
        { headers: HEADERS_JSON }
    );
    check(verifyRes, { 'verify-otp 200': (r) => r.status === 200 });
    const body = JSON.parse(verifyRes.body);
    return { accessToken: body.accessToken, refreshToken: body.refreshToken };
}