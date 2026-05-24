#!/usr/bin/env bash
# load-run.sh — Lance le load test k6 (validation SLOs nominaux Étape 0).
# Usage : ./perf/load-run.sh [BASE_URL]
#
# SLOs validés par ce test :
#   P95 latency  < 150ms
#   Error rate   < 1%
#   Throughput   ≥ 10 req/sec au plateau (11 min total)
#
# Ce script :
#   1. Démarre InfluxDB + Grafana + MailDev si nécessaire
#   2. Vérifie (non bloquant) que l'app est joignable
#   3. Lance load.js via le container k6 officiel
#   4. Affiche les URLs et les SLOs avant le run

set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASE_URL="${1:-http://localhost:8081}"
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
  echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
  echo -e "${CYAN}║          Kora — Load Test (k6)                   ║${NC}"
  echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
  echo ""
}

print_slos() {
  echo -e "${YELLOW}  SLOs validés par ce test (Étape 1)${NC}"
  echo "  ┌─────────────────────────────────────────────────"
  echo "  │  balance   p95 < 100ms"
  echo "  │  transfer  p95 < 200ms"
  echo "  │  cash      p95 < 2 500ms"
  echo "  │  Error rate    < 1%"
  echo "  │  Throughput    25 req/sec au plateau"
  echo "  │  Durée         ~11 min (ramp 2m + plateau 8m + down 1m)"
  echo "  └─────────────────────────────────────────────────"
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

# ── 1. Docker Compose ──────────────────────────────────────────────────────────
start_monitoring() {
  info "Vérification des containers de monitoring…"

  local running
  running=$(docker compose -f "${ROOT_DIR}/docker-compose.yml" ps --services --filter status=running 2>/dev/null || true)

  local need_start=false
  for svc in $COMPOSE_SERVICES; do
    if ! echo "$running" | grep -q "^${svc}$"; then
      need_start=true
      break
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

    info "Attente démarrage InfluxDB (port 8086)…"
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

# ── 2. Vérification de l'app (non bloquante) ──────────────────────────────────
check_app() {
  if curl -sf --max-time 3 "${HEALTH_URL}" > /dev/null 2>&1; then
    success "App joignable sur ${BASE_URL}"
  else
    warn "App non joignable sur ${BASE_URL} — k6 échouera si elle n'est pas lancée"
    warn "→ SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun"
  fi
}

# ── 3. Reset DB — état propre avant chaque run ────────────────────────────────
reset_db() {
  info "Reset de la base de données (POST /test/reset)…"
  local http_code
  http_code=$(curl -sf --max-time 10 -o /dev/null -w "%{http_code}" \
    -X POST "${BASE_URL}/test/reset" 2>/dev/null || echo "000")

  if [[ "${http_code}" == "200" ]]; then
    success "DB réinitialisée — état propre garanti"
  else
    warn "Reset DB échoué (HTTP ${http_code}) — le test continuera sur l'état existant"
  fi
}

# ── 4. Run k6 load ─────────────────────────────────────────────────────────────
run_load() {
  info "Lancement du load test (~11 min, 25 req/sec au plateau)…"
  echo ""

  local perf_mount
  if grep -qi microsoft /proc/version 2>/dev/null; then
    perf_mount="$(wslpath -w "${SCRIPT_DIR}")"
  elif [[ "$OSTYPE" == msys* ]] || [[ -n "${MSYSTEM:-}" ]]; then
    perf_mount="$(cd "${SCRIPT_DIR}" && pwd -W)"
  else
    perf_mount="${SCRIPT_DIR}"
  fi

  local docker_base_url="${BASE_URL/localhost/host.docker.internal}"

  (
    if [[ "$OSTYPE" == msys* ]] || [[ -n "${MSYSTEM:-}" ]]; then
      export MSYS_NO_PATHCONV=1
    fi
    docker run --rm \
      --network kora-core_default \
      -v "${perf_mount}:/perf" \
      -e BASE_URL="${docker_base_url}" \
      "${K6_IMAGE}" \
      run --out "influxdb=http://influxdb:8086/k6" /perf/load.js
  )

  return $?
}

# ── Main ───────────────────────────────────────────────────────────────────────
main() {
  banner
  print_slos
  print_urls
  start_monitoring
  check_app
  reset_db
  print_urls

  echo ""
  if run_load; then
    echo ""
    success "Load test PASSED — SLOs validés ✓"
    echo ""
    echo -e "${GREEN}  Résultats dans Grafana :${NC}"
    echo -e "${GREEN}  → ${GRAFANA_DASHBOARD}${NC}"
    echo ""
  else
    echo ""
    error "Load test FAILED — SLOs non atteints"
    echo ""
    echo -e "${YELLOW}  Dashboard pour analyse :${NC}"
    echo -e "${YELLOW}  → ${GRAFANA_DASHBOARD}${NC}"
    echo ""
    exit 1
  fi
}

main "$@"