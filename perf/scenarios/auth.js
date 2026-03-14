/**
 * auth.js — Helpers d'authentification réutilisables entre tous les scénarios.
 *
 * Architecture token (VU-local) :
 *   - Les tokens sont stockés dans des variables de module (_vuToken, _vuTokenExpires).
 *   - Dans k6, les variables de module sont isolées par VU — chaque VU a sa propre copie.
 *   - Le token est obtenu par login() au premier appel de getToken(), puis réutilisé.
 *   - tokenExpiresAt en secondes depuis epoch — évite la corruption des grands entiers
 *     lors d'une éventuelle sérialisation k6.
 *   - Aucun token dans setup() → élimine définitivement le problème inter-contextes k6.
 *
 * Tous les flows passent par l'OTP store exposé sur /test/otp/{email}
 * (endpoint @Profile("perf") — TestSupportAction).
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL     = __ENV.BASE_URL || 'http://localhost:8081';
const HEADERS_JSON = { 'Content-Type': 'application/json' };

// Variables VU-local — chaque VU k6 a sa propre copie isolée de ces variables.
let _vuToken        = null;
let _vuTokenExpires = 0;  // secondes depuis epoch

/**
 * Retourne un accessToken valide pour ce VU.
 * Fait un login si le VU n'est pas encore authentifié ou si le token est expiré.
 *
 * Durée de vie token : 15 min côté serveur — fenêtre d'utilisation : 14 min.
 * Seul le soak test (30m) déclenche un renouvellement en cours de run.
 *
 * @param {object} user  { email, pin }
 * @returns {string}     accessToken valide
 */
export function getToken(user) {
    const nowSec = Math.floor(Date.now() / 1000);
    if (_vuToken && nowSec < _vuTokenExpires) {
        return _vuToken;
    }
    // Login — token obtenu directement dans ce VU, pas de sérialisation inter-contextes
    const { accessToken } = login(user.email, user.pin);
    _vuToken        = accessToken;
    _vuTokenExpires = Math.floor(Date.now() / 1000) + 14 * 60;
    return _vuToken;
}

/**
 * Login + verify-otp → retourne les tokens frais.
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
        const r = http.get(`${BASE_URL}/test/otp/${encodeURIComponent(email)}`);
        if (r.status === 200) return JSON.parse(r.body).code;
        sleep(0.5);
    }
    // Dernier essai — si echec, le check suivant échouera
    const r = http.get(`${BASE_URL}/test/otp/${encodeURIComponent(email)}`);
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