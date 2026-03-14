#!/usr/bin/env bash
# stress-run.sh — Lance le stress test k6 (identification du point de rupture).
# Usage : ./perf/stress-run.sh [BASE_URL]
#
# Profil : paliers progressifs 5 → 10 → 20 → 30 → 50 req/sec (3 min chacun)
# Arrêt conditionnel : p(95) > 500ms OU error rate > 5%
# Durée max : ~22 min (si aucune rupture)
#
# IMPORTANT : ce test va intentionnellement stresser le système.
#             Ne pas lancer en production.

set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASE_URL="${1:-http://localhost:8081}"
INFLUX_URL="http://localhost:8086/k6"
GRAFANA_URL="http://localhost:3000"
GRAFANA_DASHBOARD="${GRAFANA_URL}/d/kora-load/kora-load-test"
MAILDEV_URL="http://localhost:1080"
HEALTH_URL="${BASE_URL}/actuator/health"
K6_IMAGE="grafana/k6:latest"
COMPOSE_SERVICES="influxdb grafana maildev"

# ── Helpers ────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; RED='\033[0;31m'; NC='\033[0m'

info()    { echo -e "${CYAN}  ▸ $*${NC}"; }
success() { echo -e "${GREEN}  ✓ $*${NC}"; }
warn()    { echo -e "${YELLOW}  ⚠ $*${NC}"; }
error()   { echo -e "${RED}  ✗ $*${NC}" >&2; }

banner() {
  echo ""
  echo -e "${RED}╔══════════════════════════════════════════════════╗${NC}"
  echo -e "${RED}║       Kora — Stress Test (point de rupture)      ║${NC}"
  echo -e "${RED}╚══════════════════════════════════════════════════╝${NC}"
  echo ""
}

print_profile() {
  echo -e "${YELLOW}  Profil de charge${NC}"
  echo "  ┌─────────────────────────────────────────────────"
  echo "  │  Palier 1  :   5 req/sec  ×  3 min"
  echo "  │  Palier 2  :  10 req/sec  ×  3 min"
  echo "  │  Palier 3  :  20 req/sec  ×  3 min"
  echo "  │  Palier 4  :  30 req/sec  ×  3 min"
  echo "  │  Palier 5  :  50 req/sec  ×  3 min"
  echo "  │"
  echo "  │  Arrêt si  :  p(95) > 500ms  OU  error rate > 5%"
  echo "  │  Durée max :  ~22 min"
  echo "  └─────────────────────────────────────────────────"
  echo ""
  echo -e "${RED}  ⚠ Ce test va intentionnellement saturer le système.${NC}"
  echo -e "${RED}    Ne pas lancer sur un environnement partagé.${NC}"
  echo ""
}

print_urls() {
  echo ""
  echo -e "${YELLOW}  URLs utiles${NC}"
  echo "  ┌─────────────────────────────────────────────────"
  echo "  │  App         ${BASE_URL}"
  echo "  │  Health      ${HEALTH_URL}"
  echo "  │  Grafana     ${GRAFANA_URL}  (user: admin / pass: admin)"
  echo "  │  Dashboard   ${GRAFANA_DASHBOARD}"
  echo "  │  InfluxDB    http://localhost:8086"
  echo "  │  MailDev     ${MAILDEV_URL}"
  echo "  └─────────────────────────────────────────────────"
  echo ""
}

# ── Docker Compose ─────────────────────────────────────────────────────────────
start_monitoring() {
  info "Vérification des containers de monitoring…"

  local running
  running=$(docker compose -f "${ROOT_DIR}/docker-compose.yml" ps --services --filter status=running 2>/dev/null || true)

  local need_start=false
  for svc in $COMPOSE_SERVICES; do
    if ! echo "$running" | grep -q "^${svc}$"; then
      need_start=true; break
    fi
  done

  if $need_start; then
    info "Démarrage de InfluxDB + Grafana + MailDev…"
    docker compose -f "${ROOT_DIR}/docker-compose.yml" up -d $COMPOSE_SERVICES

    info "Attente démarrage MailDev SMTP (port 1025)…"
    local retries=0
    until nc -z localhost 1025 > /dev/null 2>&1; do
      retries=$((retries + 1))
      if [[ $retries -ge 30 ]]; then error "MailDev n'a pas démarré en 30s"; exit 1; fi
      sleep 1
    done
    success "MailDev prêt"

    info "Attente démarrage InfluxDB…"
    retries=0
    until curl -sf "http://localhost:8086/ping" > /dev/null 2>&1; do
      retries=$((retries + 1))
      if [[ $retries -ge 30 ]]; then error "InfluxDB n'a pas démarré en 30s"; exit 1; fi
      sleep 1
    done
    success "InfluxDB prêt"
  else
    success "Monitoring déjà démarré"
  fi
}

check_app() {
  if curl -sf --max-time 3 "${HEALTH_URL}" > /dev/null 2>&1; then
    success "App joignable sur ${BASE_URL}"
  else
    warn "App non joignable — k6 échouera si elle n'est pas lancée"
    warn "→ SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun"
  fi
}

# ── Run k6 stress ──────────────────────────────────────────────────────────────
run_stress() {
  info "Lancement du stress test (arrêt conditionnel si rupture)…"
  echo ""

  local perf_mount
  if grep -qi microsoft /proc/version 2>/dev/null; then
    perf_mount="$(wslpath -w "${SCRIPT_DIR}")"
  elif [[ "$OSTYPE" == msys* ]] || [[ -n "${MSYSTEM:-}" ]]; then
    perf_mount="$(cd "${SCRIPT_DIR}" && pwd -W)"
  else
    perf_mount="${SCRIPT_DIR}"
  fi

  (
    if [[ "$OSTYPE" == msys* ]] || [[ -n "${MSYSTEM:-}" ]]; then
      export MSYS_NO_PATHCONV=1
    fi
    docker run --rm \
      --network host \
      -v "${perf_mount}:/perf" \
      -e BASE_URL="${BASE_URL}" \
      "${K6_IMAGE}" \
      run --out "influxdb=${INFLUX_URL}" /perf/stress.js
  )

  return $?
}

# ── Main ───────────────────────────────────────────────────────────────────────
main() {
  banner
  print_profile
  print_urls
  start_monitoring
  check_app
  print_urls

  echo ""
  if run_stress; then
    echo ""
    success "Stress test terminé sans rupture détectée à 50 req/sec"
    echo ""
    echo -e "${GREEN}  → Analyse dans Grafana : ${GRAFANA_DASHBOARD}${NC}"
    echo ""
  else
    echo ""
    warn "Stress test arrêté — seuil de rupture atteint"
    echo ""
    echo -e "${YELLOW}  → Point de rupture visible dans Grafana : ${GRAFANA_DASHBOARD}${NC}"
    echo -e "${YELLOW}    Analyser : p95 par palier, hikaricp_connections_pending, heap JVM${NC}"
    echo ""
    # Sortie 0 : c'est l'objectif du stress test de trouver la rupture
    exit 0
  fi
}

main "$@"