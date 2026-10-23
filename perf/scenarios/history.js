/**
 * history.js — Scénario historique (10% du mix de charge).
 *
 * Flow :
 *   1. GET /payments/history (première page, 20 lignes)
 *   2. Une fois sur quatre, page suivante via nextCursor — le keyset est le
 *      chemin qu'emprunte l'app mobile en défilant.
 *
 * Les deux requêtes portent le tag operation:history.
 */

import http from 'k6/http';
import { check } from 'k6';
import { getToken, authHeaders } from './auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const NEXT_PAGE_RATIO = 0.25;

export function scenarioHistory(user) {
    const accessToken = getToken(user);
    const params = { headers: authHeaders(accessToken), tags: { operation: 'history' } };

    const first = http.get(`${BASE_URL}/payments/history?limit=20`, params);
    const page = checkPage(first);

    if (page && page.nextCursor && Math.random() < NEXT_PAGE_RATIO) {
        const next = http.get(
            `${BASE_URL}/payments/history?limit=20&cursor=${encodeURIComponent(page.nextCursor)}`,
            params
        );
        checkPage(next);
    }

    return page;
}

function checkPage(res) {
    let body = null;
    try { body = JSON.parse(res.body); } catch { /* body stays null */ }

    check(res, {
        'history 200':         (r) => r.status === 200,
        'history items array': () => body !== null && Array.isArray(body.items),
    });

    return res.status === 200 ? body : null;
}
