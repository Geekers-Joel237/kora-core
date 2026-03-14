# Kora Core — Runbook Tests de Performance

Stack : **k6** · **InfluxDB 1.8** · **Grafana** · **Micrometer** (Spring Boot)

---

## Table des matières

1. [Prérequis](#1-prérequis)
2. [Architecture](#2-architecture)
3. [SLOs Étape 0](#3-slos-étape-0)
4. [Exécution locale (tout en une machine)](#4-exécution-locale)
5. [Exécution distante (app sur serveur, k6 en local)](#5-exécution-distante)
6. [Ordre des tests et gates de progression](#6-ordre-des-tests-et-gates)
7. [Résultats attendus par test](#7-résultats-attendus)
8. [Que surveiller dans Grafana](#8-grafana)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Prérequis

### Outils requis

| Outil | Rôle | Vérification |
|---|---|---|
| Docker Desktop | InfluxDB, Grafana, MailDev | `docker --version` |
| Java 21 | Runtime Spring Boot | `java --version` |
| Gradle wrapper | Build et lancement | `./gradlew --version` |
| curl, nc | Health checks dans les scripts | `curl --version` |

k6 tourne dans un container Docker — pas d'installation locale requise.

### Ports utilisés

| Port | Service | Utilisé par |
|---|---|---|
| 8081 | Spring Boot | k6, navigateur |
| 8086 | InfluxDB | k6 (`--out influxdb`), Micrometer, Grafana |
| 3000 | Grafana | Navigateur |
| 1025 | MailDev SMTP | Spring Boot (`SmtpMailAdapter`) |
| 1080 | MailDev UI | Navigateur |

S'assurer qu'aucun de ces ports n'est déjà occupé avant de commencer.

---

## 2. Architecture

```
k6 (container Docker)
  │
  ├── HTTP ──────────────────► Spring Boot :8081
  │                             SPRING_PROFILES_ACTIVE=perf
  │                             └── TestSupportAction (@Profile("perf"))
  │                                 GET /test/otp/{email}  ← k6 setup récupère les OTPs
  │
  ├── --out influxdb ────────► InfluxDB :8086  (base: k6)
  │
Spring Boot (Micrometer)
  └── export step=10s ───────► InfluxDB :8086  (base: kora_metrics)

Grafana :3000
  ├── datasource: InfluxDB-k6     (base: k6)
  └── datasource: InfluxDB-kora   (base: kora_metrics)

MailDev :1025 (SMTP)
  └── requis par Spring Actuator MailHealthIndicator
      sans MailDev → health=DOWN → les scripts de santé échouent
```

---

## 3. SLOs Étape 0

Ces valeurs sont les thresholds du **load test**. Tout dépassement est un signal d'architecture.

| SLO | Valeur | Mesuré par |
|---|---|---|
| Latence P95 | < 150ms | k6 `http_req_duration` |
| Error rate | < 1% | k6 `http_req_failed` |
| Throughput plateau | ≥ 10 req/sec | k6 `http_reqs` |
| Check rate (COMPLETED) | > 99% | k6 `checks` |

---

## 4. Exécution locale

> Tout tourne sur la même machine : Spring Boot + Docker (InfluxDB, Grafana, MailDev) + k6.

### Étape 1 — Démarrer le monitoring

```bash
docker compose up -d influxdb grafana maildev
```

Attendre ~10s, puis vérifier :

```bash
curl -s http://localhost:8086/ping   # → pong
curl -s http://localhost:1080        # → page MailDev
```

### Étape 2 — Démarrer l'app Spring Boot

```bash
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun
```

Attendre le message `Started KoraCoreApplication` dans les logs.

Vérifier :

```bash
curl -s http://localhost:8081/actuator/health
# Résultat attendu :
# {"status":"UP","components":{"db":{"status":"UP"},"mail":{"status":"UP"},...}}
```

Si `mail: DOWN` → MailDev non démarré, reprendre depuis l'étape 1.

Vérifier l'export Micrometer (après ~15s) :

```bash
curl -s "http://localhost:8086/query?db=kora_metrics&q=SHOW+MEASUREMENTS"
# Résultat attendu : hikaricp_connections_active, jvm_memory_used_bytes, ...
```

Vérifier l'endpoint OTP perf :

```bash
# Inscrire un user test
curl -s -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Test","email":"check@test.com","phonePrefix":"+237","phoneNumber":"699999998","rawPin":"123456"}'

# Récupérer l'OTP
curl -s "http://localhost:8081/test/otp/check%40test.com"
# Résultat attendu : {"code":"XXXXXX"}
```

Si `404` → l'app n'est pas démarrée avec `SPRING_PROFILES_ACTIVE=perf`.

### Étape 3 — Lancer les tests (dans l'ordre)

```bash
# 1. Smoke (toujours en premier — 2 min)
./perf/smoke-run.sh

# 2. Load (validation SLOs — 11 min)
./perf/load-run.sh

# 3. Stress (point de rupture — 22 min max)
./perf/stress-run.sh

# 4. Soak (stabilité longue durée — 30 min)
./perf/soak-run.sh
```

---

## 5. Exécution distante

> L'app tourne sur un serveur distant. k6 et le monitoring tournent en local ou sur une machine séparée.

### Cas A — App sur serveur, monitoring + k6 en local

```bash
# Démarrer le monitoring en local
docker compose up -d influxdb grafana maildev

# Passer l'URL de l'app à chaque script
./perf/smoke-run.sh http://mon-serveur:8081
./perf/load-run.sh  http://mon-serveur:8081
```

Le script utilise `--network host` pour que k6 (container Docker) atteigne `localhost:8086` (InfluxDB local).
L'app sur le serveur doit être accessible depuis la machine qui lance k6.

**Préconditions serveur :**
- `SPRING_PROFILES_ACTIVE=perf` actif sur le serveur
- Ports 8081 ouvert vers la machine de test
- `GET /test/otp/{email}` accessible depuis la machine de test

### Cas B — App + k6 sur serveur, Grafana en local

Dans ce cas, modifier `INFLUX_URL` dans le script pour pointer vers l'InfluxDB du serveur, puis configurer la datasource Grafana locale vers ce même InfluxDB.

> Pour l'Étape 0 (charge nominale modeste : 10 req/sec), le cas A est suffisant.

---

## 6. Ordre des tests et gates

**Ne jamais sauter une étape. Chaque test est un gate vers le suivant.**

```
┌─────────────────────────────────────────────────────────────┐
│  PRÉCONDITIONS                                               │
│  □ health = UP (db + mail)                                  │
│  □ /test/otp/{email} → {"code":"..."}                       │
│  □ kora_metrics dans InfluxDB                               │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
              ┌─────────────────┐
              │   SMOKE TEST    │  ./perf/smoke-run.sh
              │   1 VU · 2 min  │
              └────────┬────────┘
                       │
              PASS ?   │
          ┌────────────┴────────────┐
         NON                       OUI
          │                         │
    Corriger le bug          ┌──────▼──────────┐
    avant de continuer       │   LOAD TEST     │  ./perf/load-run.sh
                             │  10 req/s · 11m │
                             └──────┬──────────┘
                                    │
                           SLOs OK? │  p95<150ms · errors<1%
                        ┌───────────┴───────────┐
                       NON                      OUI
                        │                        │
                 Analyser Grafana         ┌──────▼──────────┐
                 (section 8)             │  STRESS TEST    │  ./perf/stress-run.sh
                 Corriger                │  5→50 req/s     │
                 Re-lancer load          │  22 min max     │
                                         └──────┬──────────┘
                                                │
                                    Documenter  │  palier de rupture
                                    dans l'ADR  │
                                                │
                                         ┌──────▼──────────┐
                                         │   SOAK TEST     │  ./perf/soak-run.sh
                                         │  5 req/s · 30m  │
                                         └──────┬──────────┘
                                                │
                                       Stable?  │  heap + connexions + p95
                                    ┌───────────┴───────────┐
                                   NON                      OUI
                                    │                        │
                             Chercher fuite           ✓ Étape 0 validée
                             mémoire / connexion
```

---

## 7. Résultats attendus

### Smoke test

| Indicateur | Résultat attendu |
|---|---|
| Exit code | 0 |
| Sortie k6 | `✓ register 200`, `✓ cash-in 200`, `✓ cash-in COMPLETED` |
| `http_req_failed` | 0% |
| `http_req_duration` p95 | < 2 000ms (seuil souple) |
| Grafana | Les 6 panneaux alimentés, courbes visibles |

Si le smoke **échoue** : ne pas lancer le load test. Corriger d'abord.

---

### Load test

| Indicateur | Résultat attendu | Threshold k6 |
|---|---|---|
| `http_req_duration` p95 | < 150ms | strict (`abortOnFail: false`) |
| `http_req_failed` rate | < 1% | strict |
| `checks` rate | > 99% | strict |
| Exit code | 0 = PASS, 1 = FAIL | — |
| Sortie finale | `✓ load test PASSED — SLOs validés` | — |

**Courbe de latence attendue dans Grafana :**
```
Ramp-up (0-2min)  : p95 monte progressivement jusqu'à ~80-120ms
Plateau (2-10min) : p95 stable entre 50 et 120ms
Ramp-down (10-11m): p95 redescend vers 20-40ms
```

Si la courbe monte et ne se stabilise pas au plateau → le système est saturé à 10 req/sec, c'est un bug d'architecture.

---

### Stress test

Le stress test ne PASS/FAIL pas au sens strict. Il s'arrête quand la rupture est atteinte (ou à 50 req/sec si le système tient).

| Palier | p95 attendu (système sain) | Signal de rupture |
|---|---|---|
| 5 req/s | < 100ms | — |
| 10 req/s | < 150ms (=SLO) | — |
| 20 req/s | 150–300ms | p95 > 500ms = rupture |
| 30 req/s | 300–500ms | hikaricp_pending > 0 = pool saturé |
| 50 req/s | > 500ms probable | errors > 5% = arrêt conditionnel |

**Documenter :** à quel palier le p95 dépasse 500ms et quelle métrique dégrade en premier (latence ? pool DB ? heap ?).

---

### Soak test

| Indicateur | Résultat attendu |
|---|---|
| Exit code | 0 |
| `http_req_duration` p95 | < 300ms sur toute la durée |
| `http_req_failed` | < 1% |
| JVM heap | Stable — oscille dans une bande de ±100MB, ne dérive pas à la hausse |
| `hikaricp_connections_pending` | Reste à 0 pendant les 30 min |
| `soak_latency_trend` p95 | Stable — pas de tendance haussière |

**Signal de fuite mémoire :**
```
Normal   : heap  200MB ──▲──▼──▲──▼──  200MB  (cycles GC)
Fuite    : heap  200MB ─────────────────────►  600MB  (dérive monotone)
```

---

## 8. Grafana

URL : `http://localhost:3000/d/kora-load/kora-load-test`
Credentials : `admin / admin`

Le dashboard est auto-provisionné — aucune configuration manuelle.

### Pendant un test : paramètres recommandés

- Fenêtre temporelle : `Last 30 minutes`
- Rafraîchissement : `5s` (menu en haut à droite)

### Checklist des 6 panneaux

| # | Panneau | Source | Ce qu'on doit voir |
|---|---|---|---|
| 1 | Latence p50/p95/p99 | k6 | 3 courbes distinctes, p95 sous la ligne rouge 150ms |
| 2 | Throughput req/sec | k6 | Montée progressive, plateau à 10 req/s pendant 8 min |
| 3 | Error Rate % | k6 | Proche de 0%, sous la ligne rouge 1% |
| 4 | HikariCP connections | kora | `active` entre 0 et 20, `pending` à 0 |
| 5 | JVM Heap MB | kora | Courbe stable avec oscillations GC normales |
| 6 | Latence applicative p95 | kora | Proche du panneau 1, légèrement inférieure (pas de réseau) |

Si un panneau est **vide** :
1. Vérifier que le test est bien en cours (pas encore terminé ?)
2. Vérifier la datasource : `http://localhost:3000/connections/datasources`
3. Vérifier que la base InfluxDB contient des données :
   ```bash
   # Pour les métriques k6
   curl -s "http://localhost:8086/query?db=k6&q=SHOW+MEASUREMENTS"
   # Pour les métriques Micrometer
   curl -s "http://localhost:8086/query?db=kora_metrics&q=SHOW+MEASUREMENTS"
   ```

---

## 9. Troubleshooting

### `health = DOWN` au démarrage

```bash
curl http://localhost:8081/actuator/health | python -m json.tool
```

| Composant DOWN | Cause | Correction |
|---|---|---|
| `mail` | MailDev non démarré | `docker compose up -d maildev` |
| `db` | PostgreSQL non démarré | `docker compose up -d postgres` |

---

### `docker: Error response from daemon: invalid mode: /perf`

Problème MSYS2/Git Bash : conversion automatique des chemins Unix en chemins Windows.
Les scripts gèrent déjà ce cas avec `export MSYS_NO_PATHCONV=1` dans un sous-shell.

Si l'erreur persiste : lancer depuis **PowerShell** ou **WSL** plutôt que Git Bash.

---

### `/test/otp/{email}` retourne 404

Causes et corrections dans l'ordre :

1. **App démarrée sans le profil perf**
   ```bash
   # Mauvais
   ./gradlew bootRun
   # Correct
   SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun
   ```

2. **OTP expiré** (TTL : 5 min) — le setup est trop lent pour le nombre d'users
   → Réduire `USER_COUNT` dans `load.js` / `stress.js`

3. **Email mal encodé dans l'URL**
   ```bash
   # Mauvais (@ non encodé)
   curl http://localhost:8081/test/otp/user@test.com
   # Correct
   curl http://localhost:8081/test/otp/user%40test.com
   ```

---

### `InsufficientFundsException` en masse pendant le test

Le seed de 200 000 XOF a été épuisé. Le cashOut (5 000) ou le transfer (2 000) a été appelé plus de fois que prévu.

Calcul du seed minimum pour un soak test :
```
5 req/sec × 30 min = 9 000 requêtes
cashOut 15% + transfer 35% = 50% de requêtes débitent
9 000 × 0.50 × 5 000 XOF = 22 500 000 XOF / nb_users

Pour 15 users : 22 500 000 / 15 = 1 500 000 XOF par user
```

Corriger dans `data/setup.js` : augmenter `SEED_AMOUNT`.

---

### Grafana — panneaux vides après le test

1. Vérifier que les measurements existent dans InfluxDB :
   ```bash
   curl -s "http://localhost:8086/query?db=k6&q=SHOW+MEASUREMENTS"
   ```

2. Si vide : le `--out influxdb` n'a pas fonctionné → relancer avec les logs k6 visibles :
   ```bash
   # Lancer manuellement sans le script shell pour voir les erreurs k6
   docker run --rm --network host \
     -v "$(pwd)/perf:/perf" \
     -e BASE_URL=http://localhost:8081 \
     grafana/k6:latest \
     run --out influxdb=http://localhost:8086/k6 /perf/smoke.js
   ```

3. Si les measurements existent mais Grafana est vide : ajuster la fenêtre temporelle sur l'heure du run.

---

## Référence rapide

```bash
# ── Démarrage ────────────────────────────────────────────────────
docker compose up -d influxdb grafana maildev
SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun

# ── Sanité avant test ────────────────────────────────────────────
curl http://localhost:8081/actuator/health
curl "http://localhost:8086/query?db=kora_metrics&q=SHOW+MEASUREMENTS"
curl "http://localhost:8081/test/otp/check%40test.com"  # après register

# ── Tests (dans l'ordre) ─────────────────────────────────────────
./perf/smoke-run.sh   [BASE_URL]   # 2 min  — gate obligatoire
./perf/load-run.sh    [BASE_URL]   # 11 min — valide les SLOs
./perf/stress-run.sh  [BASE_URL]   # 22 min — point de rupture
./perf/soak-run.sh    [BASE_URL]   # 30 min — stabilité

# BASE_URL par défaut : http://host.docker.internal:8081

# ── URLs ─────────────────────────────────────────────────────────
# App       http://localhost:8081
# Health    http://localhost:8081/actuator/health
# Grafana   http://localhost:3000   (admin/admin)
# Dashboard http://localhost:3000/d/kora-load/kora-load-test
# InfluxDB  http://localhost:8086
# MailDev   http://localhost:1080
```