/**
 * auth.js — Helpers d'authentification réutilisables entre tous les scénarios.
 *
 * Tous les flows passent par l'OTP store exposé sur /test/otp/{email}
 * (endpoint @Profile("perf") — TestSupportAction).
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

const HEADERS_JSON = { 'Content-Type': 'application/json' };

/**
 * Inscrit un utilisateur et renvoie les tokens (accessToken, refreshToken).
 * Flow : POST /auth/register → GET /test/otp/{email} (retry) → POST /auth/verify-otp
 *
 * @param {string} fullName
 * @param {string} email
 * @param {string} phonePrefix  ex: "+237"
 * @param {string} phoneNumber  ex: "600000001"
 * @param {string} pin
 * @returns {{ accessToken: string, refreshToken: string }}
 */
export function register(fullName, email, phonePrefix, phoneNumber, pin) {
    const regRes = http.post(
        `${BASE_URL}/auth/register`,
        JSON.stringify({ fullName, email, phonePrefix, phoneNumber, rawPin: pin }),
        { headers: HEADERS_JSON }
    );
    check(regRes, { 'register 200': (r) => r.status === 200 });

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

/**
 * Login + verify-otp → retourne les tokens frais.
 * Utilisé dans les scénarios de test (le token d'accès expire après 15 min).
 *
 * @param {string} email
 * @param {string} pin
 * @returns {{ accessToken: string, refreshToken: string }}
 */
export function login(email, pin) {
    const loginRes = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({ email, rawPin: pin }),
        { headers: HEADERS_JSON }
    );
    check(loginRes, { 'login 200': (r) => r.status === 200 });

    const otp = captureOtp(email);

    const verifyRes = http.post(
        `${BASE_URL}/auth/verify-otp`,
        JSON.stringify({ email, code: otp }),
        { headers: HEADERS_JSON }
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
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
    };
}

/**
 * Récupère le code OTP depuis /test/otp/{email} avec retry (max 10 × 200ms).
 * L'endpoint est exposé uniquement avec le profil "perf".
 *
 * @param {string} email
 * @returns {string} code OTP
 */
function captureOtp(email) {
    for (let i = 0; i < 10; i++) {
        const r = http.get(`${BASE_URL}/test/otp/${encodeURIComponent(email)}`);
        if (r.status === 200) {
            return JSON.parse(r.body).code;
        }
        sleep(0.2);
    }
    // Dernier essai — si echec, le check suivant échouera
    const r = http.get(`${BASE_URL}/test/otp/${encodeURIComponent(email)}`);
    check(r, { 'otp retrieved': (res) => res.status === 200 });
    return JSON.parse(r.body).code;
}