/**
 * transfer.js — Scénario complet P2P transfer (35% du mix de charge).
 *
 * Prérequis : l'émetteur doit avoir un solde >= amount.
 *             Le setup garantit un solde seed suffisant.
 *
 * Flow :
 *   1. Réutilisation du token JWT (login uniquement si expiré)
 *   2. POST /payments/transfer
 *   3. Vérification : status 200, state == "COMPLETED"
 */

import http from 'k6/http';
import { check } from 'k6';
import { getToken, authHeaders } from './auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

/**
 * Exécute un transfert P2P complet.
 *
 * @param {object} sender         objet user complet (email, pin, accessToken, tokenExpiresAt)
 * @param {string} recipientPhone numéro complet du destinataire, ex: "+237620003141"
 * @param {number} amount         montant en XOF (défaut : 2 000)
 * @param {string} method         méthode de paiement (défaut : ORANGE_MONEY)
 * @returns {object} réponse JSON du transfert
 */
export function scenarioTransfer(sender, recipientPhone, amount = 2_000, method = 'ORANGE_MONEY') {
    const accessToken = getToken(sender);

    const res = http.post(
        `${BASE_URL}/payments/transfer`,
        JSON.stringify({
            rawPin:        sender.pin,
            amount,
            currency:      'XOF',
            paymentMethod: method,
            toPhoneNumber: recipientPhone,
        }),
        { headers: authHeaders(accessToken) }
    );

    check(res, {
        'transfer 200':       (r) => r.status === 200,
        'transfer COMPLETED': (r) => {
            try { return JSON.parse(r.body).state === 'COMPLETED'; } catch { return false; }
        },
    });

    return res.status === 200 ? JSON.parse(res.body) : null;
}