/**
 * cashIn.js — Scénario complet cash-in (40% du mix de charge).
 *
 * Flow :
 *   1. Login → OTP → token frais
 *   2. POST /payments/cash-in
 *   3. Vérification : status 200, state == "COMPLETED"
 */

import http from 'k6/http';
import { check } from 'k6';
import { login, authHeaders } from './auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

/**
 * Exécute un cash-in complet pour un utilisateur.
 *
 * @param {string} email
 * @param {string} pin
 * @param {number} amount   montant en XOF (défaut : 10 000)
 * @param {string} method   méthode de paiement (défaut : ORANGE_MONEY)
 * @returns {object} réponse JSON du cash-in
 */
export function scenarioCashIn(email, pin, amount = 10_000, method = 'ORANGE_MONEY') {
    const { accessToken } = login(email, pin);

    const res = http.post(
        `${BASE_URL}/payments/cash-in`,
        JSON.stringify({ rawPin: pin, amount, currency: 'XOF', paymentMethod: method }),
        { headers: authHeaders(accessToken) }
    );

    check(res, {
        'cash-in 200':       (r) => r.status === 200,
        'cash-in COMPLETED': (r) => {
            try { return JSON.parse(r.body).state === 'COMPLETED'; } catch { return false; }
        },
    });

    return res.status === 200 ? JSON.parse(res.body) : null;
}