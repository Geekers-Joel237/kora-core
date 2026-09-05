# ADR-005 — Calibration des Tests de Performance pour l'Étape 1

**Date**: 2026-05-24
**Status**: Accepted
**Authors**: Kora Core Engineering — Ivan Joël Tchatchoua Bayon
**Related**: ADR-004 — Micro-Transaction Model · `perf/smoke.js` · `perf/load.js` · `perf/stress.js` · `perf/soak.js` · `perf/data/setup.js`

---

## Contexte

### Situation initiale

Les thresholds des tests de performance ont été écrits avant que le provider simulator soit configuré avec des latences réalistes (profil `application-perf.properties`). Le premier run du smoke test après l'implémentation du modèle micro-transactionnel (ADR-004) a produit :

```
✗ p(95)<2000  →  p(95)=2.13s   (threshold: 2 000ms)
✓ error rate  →  1.66%         (threshold: < 5%)
```

L'échec du p(95) n'était pas une régression applicative mais une inadéquation entre les thresholds et les latences provider configurées. Par ailleurs, le transfer failure (1/20) révélait un problème structurel dans le setup des données de test.

### Configuration provider (application-perf.properties)

```properties
kora.provider.latency.authorize-ms=800    # + jitter uniform [0%, 40%] → [800ms, 1 120ms]
kora.provider.latency.capture-ms=600      # + jitter uniform [0%, 40%] → [600ms,   840ms]
```

**Plafond théorique par opération provider-bound** :

```
authorize (max) :   800 × 1.4 = 1 120ms
capture   (max) :   600 × 1.4 =   840ms
overhead TX-1/TX-2/réseau     ≈   100ms
─────────────────────────────────────────
Plafond total                ≈ 2 060ms
```

**Mix métier** : cashIn 40% + cashOut 15% = **55% d'opérations provider-bound** par itération.

Conséquence directe : tout threshold global de latence inférieur à ~2 100ms est physiquement impossible à tenir, indépendamment de la qualité du code applicatif.

---

## Problèmes identifiés

### Problème 1 — Thresholds de latence non calibrés au provider

| Fichier | Threshold actuel | Statut | Raison de l'échec |
|---|---|---|---|
| `smoke.js` | `p(95)<2000` | Impossible | 130ms sous le plafond provider mesuré (2 130ms) |
| `load.js` | `p(95)<200` | Impossible | 10× sous le plafond provider |
| `soak.js` | `p(95)<300` | Impossible | 7× sous le plafond provider |
| `soak.js` | `soak_latency_trend p(95)<300` | Impossible | Idem |
| `stress.js` | `abortOnFail p(95)<500` | Impossible | Le premier cashIn (1.5s) déclenche l'abort avant d'atteindre le premier palier |

Tous ces thresholds confondent le SLO applicatif (ce que l'application contrôle : logique métier, DB, locks) avec la latence end-to-end (qui inclut le provider I/O, une contrainte externe non contrôlable par l'application).

### Problème 2 — Absence de granularité par type d'opération dans load.js

Un threshold global `p(95)<200` est inapplicable dès lors que le mix inclut des opérations provider-bound. La solution rigoureuse consiste à séparer les thresholds par nature d'opération :

- **balance** : lecture pure, 0 provider I/O, 0 lock — SLO applicatif = 100ms
- **transfer** : 1 TX + lock customer account, 0 provider I/O — SLO applicatif = 200ms
- **cashIn/cashOut** : TX-1 + provider I/O + TX-2 — SLO = plafond provider + overhead

Les tags k6 (`{ tags: { operation: '...' } }` sur chaque `http.post()` / `http.get()`) permettent de définir des thresholds `http_req_duration{operation:xxx}` ciblés, sans modifier le reporting global.

### Problème 3 — Absence de seed cashIn : échecs non-déterministes

Le design initial supposait que le mix 40% cashIn constituerait un "seed implicite" : le premier cashIn d'un VU chargerait le compte avant tout cashOut ou transfer.

Ce raisonnement est incorrect dans deux cas :

**Smoke (1 VU, séquentiel)** : Si la première itération tire `cashOut` (15%) ou `transfer` (35%), le solde est 0 → `InsufficientFundsException` → HTTP 4xx. Probabilité d'échec sur la première itération : **50%**. L'échec observé (1/20 transfers) correspond exactement à ce scénario.

**Load/stress/soak (N VUs)** : Chaque VU est isolé sur son propre user (`idx = __VU % users.length`). Environ 50% des VUs auront transfer ou cashOut en première itération → pic d'erreurs systématique au démarrage du test, indépendant de la charge.

---

## Décisions

### Décision 1 — Recalibration des thresholds de latence

**Principe directeur** : un threshold doit détecter une régression, pas mesurer une contrainte externe. La contrainte provider est connue, documentée, et assumée (voir ADR-004). Les thresholds de latence doivent être positionnés **au-dessus du plafond provider normal** pour ne se déclencher qu'en cas de vraie dégradation applicative (pool exhaustion, lock contention, timeouts DB).

**Valeur de référence : 2 500ms**

```
Plafond provider mesuré (p(95) smoke)    : 2 130ms
Marge de sécurité                        :  +370ms
─────────────────────────────────────────────────
Threshold calibré                        : 2 500ms
```

Cette marge de 370ms est suffisante pour absorber la variabilité du jitter provider, mais assez serrée pour détecter les dégradations applicatives :

| Situation | p(95) attendu | Threshold 2 500ms |
|---|---|---|
| Système sain | ~2 100ms | ✓ Passe |
| Queuing léger (pool 70% utilisé) | ~3–4s | ✗ Déclenche |
| Pool exhaustion (situation pré-ADR-004) | 30–60s | ✗ Déclenche |

**Stress test — abortOnFail à 5 000ms** :

Le stress test cherche le point de rupture. Le threshold `abortOnFail` doit laisser passer le comportement normal à tous les paliers pour atteindre la rupture réelle :

```
Sain (tous paliers < breaking point)  : p(95) ≈ 2s    → ne déclenche pas à 5 000ms
Dégradé (approche breaking point)     : p(95) 3–5s    → ne déclenche pas encore
Rupture (pool exhaustion)             : p(95) > 10s   → déclenche et arrête le test
```

### Décision 2 — Thresholds per-operation-type dans load.js

Les scénarios ajoutent un tag `operation` sur chaque appel HTTP. `load.js` définit des thresholds séparés par tag.

```
http_req_duration{operation:balance}   p(95)<100ms
http_req_duration{operation:transfer}  p(95)<200ms
http_req_duration{operation:cash}      p(95)<2500ms
```

Ces SLOs reflètent ce que l'application contrôle réellement pour chaque type d'opération.

### Décision 3 — Seed cashIn explicite dans setup()

Chaque user reçoit un cashIn de **100 000 XAF** immédiatement après l'obtention de son token, avant d'entrer dans le pool VU.

**Calcul de l'amount** :

Pire séquence initiale (avant le premier cashIn du scénario) : N iterations consécutives cashOut (5 000 XAF) ou transfer (2 000 XAF). Sur 10 premières itérations full cashOut : 50 000 XAF. La marge 100 000 XAF couvre 20 cashOuts ou 50 transfers consécutifs avant le premier cashIn.

Le mix net par itération est positif (+2 550 XAF en moyenne), donc le solde croît indéfiniment après le seed. Le seed n'est utile que pour le bootstrap initial.

**Coût en temps de setup** (provider I/O ~1.5s par seed) :

| Test | Users | Coût seed |
|---|---|---|
| smoke | 2 | +3s |
| load | 60 | +90s |
| soak | 40 | +60s |
| stress | 200 | +300s (~5min) |

Ce surcoût est acceptable : il rend le comportement initial des VUs **déterministe et reproductible**.

### Décision 4 — Soak : détection de drift dans Grafana, pas dans les thresholds k6

Le soak test cherche la dégradation progressive sur la durée (drift de latence, fuite mémoire). Un threshold k6 agrégé sur 30 minutes ne peut pas détecter un drift qui commence à la 20ème minute. Cette détection repose sur Grafana (tendance croissante du p95 dans le temps via `soak_latency_trend`). Le threshold k6 à 2 500ms sert uniquement à catch une catastrophe terminale (pool exhaustion tardive, OOM).

---

## Tableau de synthèse des changements

| Fichier | Changement | Impact |
|---|---|---|
| `perf/data/setup.js` | Ajout seed cashIn 100 000 XAF après verify-otp | Élimine les échecs 0-solde sur première itération |
| `perf/scenarios/cashIn.js` | `tags: { operation: 'cash' }` sur `http.post()` | Active threshold per-type dans load.js |
| `perf/scenarios/cashOut.js` | `tags: { operation: 'cash' }` sur `http.post()` | Idem |
| `perf/scenarios/transfer.js` | `tags: { operation: 'transfer' }` sur `http.post()` | Threshold transfer 200ms |
| `perf/scenarios/balance.js` | `tags: { operation: 'balance' }` sur `http.get()` | Threshold balance 100ms |
| `perf/smoke.js` | `p(95)<2000` → `p(95)<2500` | Threshold calibré au plafond provider |
| `perf/load.js` | `p(95)<200` global → thresholds per-operation-type | SLOs réalistes et différenciés |
| `perf/soak.js` | `p(95)<300` → `p(95)<2500` (×2) | Threshold calibré au plafond provider |
| `perf/stress.js` | `abortOnFail p(95)<500` → `p(95)<5000` | abortOnFail au-dessus du comportement sain |

---

## Conséquences

### Ce que ces changements garantissent

- Le smoke test passe si et seulement si le système est fonctionnellement sain (0 erreur) et sans dégradation majeure (p(95) < 2.5s)
- Le load test valide indépendamment les SLOs applicatifs (balance, transfer) et la tenue sous charge provider-bound (cash)
- Le stress test peut atteindre ses paliers supérieurs (30 req/s, 50 req/s) sans abort prématuré
- Aucun VU ne démarre avec un solde zéro — les erreurs InsufficientFunds en début de test sont éliminées

### Limites assumées

- Le threshold `p(95)<2500` sur les opérations cash ne peut pas détecter une dégradation subtile (ex. passage de 2.1s à 2.4s). Une telle dérive est détectable uniquement par comparaison de runs dans Grafana.
- Le seed cashIn en setup() augmente le temps de démarrage des tests (jusqu'à +5min pour stress). Acceptable à ce stade.
- Les tags `operation` ne sont pas propagés dans les métriques Actuator JVM — le Grafana dashboard k6 (ID 2587) les expose via InfluxDB uniquement.