/**
 * balance.js — Scénario balance (10% du mix de charge).
 *
 * Flow :
 *   1. Login → OTP → token frais
 *   2. GET /payments/balance
 *   3. Vérification : status 200, balance.amount >= 0
 */

import http from 'k6/http';
import { check } from 'k6';
import { login, authHeaders } from './auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

/**
 * Consulte le solde d'un utilisateur.
 *
 * @param {string} email
 * @param {string} pin
 * @returns {object} réponse JSON du solde
 */
export function scenarioBalance(email, pin) {
    const { accessToken } = login(email, pin);

    const res = http.get(
        `${BASE_URL}/payments/balance`,
        { headers: authHeaders(accessToken) }
    );

    check(res, {
        'balance 200':        (r) => r.status === 200,
        'balance amount >= 0': (r) => {
            try { return JSON.parse(r.body).amount >= 0; } catch { return false; }
        },
    });

    return res.status === 200 ? JSON.parse(res.body) : null;
}