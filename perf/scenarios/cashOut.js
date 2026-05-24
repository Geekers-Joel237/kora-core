/**
 * cashOut.js — Scénario complet cash-out (15% du mix de charge).
 *
 * Prérequis : l'utilisateur doit avoir un solde >= amount.
 *             Le setup garantit un solde seed suffisant.
 *
 * Flow :
 *   1. Réutilisation du token JWT (login uniquement si expiré)
 *   2. POST /payments/cash-out
 *   3. Vérification : status 200, state == "COMPLETED"
 */

import http from 'k6/http';
import { check } from 'k6';
import { getToken, authHeaders } from './auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

/**
 * Exécute un cash-out complet pour un utilisateur.
 *
 * @param {object} user   objet user complet (email, pin, accessToken, tokenExpiresAt)
 * @param {number} amount montant en XOF (défaut : 5 000)
 * @param {string} method méthode de paiement (défaut : ORANGE_MONEY)
 * @returns {object} réponse JSON du cash-out
 */
export function scenarioCashOut(user, amount = 5_000, method = 'ORANGE_MONEY') {
    const accessToken = getToken(user);

    const res = http.post(
        `${BASE_URL}/payments/cash-out`,
        JSON.stringify({ rawPin: user.pin, amount, currency: 'XOF', paymentMethod: method }),
        { headers: authHeaders(accessToken), tags: { operation: 'cash' } }
    );

    check(res, {
        'cash-out 200':       (r) => r.status === 200,
        'cash-out COMPLETED': (r) => {
            try { return JSON.parse(r.body).state === 'COMPLETED'; } catch { return false; }
        },
    });

    return res.status === 200 ? JSON.parse(res.body) : null;
}