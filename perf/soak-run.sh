#!/usr/bin/env bash
# soak-run.sh — Lance le soak test k6 (stabilité longue durée).
# Usage : ./perf/soak-run.sh [BASE_URL]
#
# Objectif : détecter fuites mémoire, DB pool exhaustion, dégradation latence.
# Profil    : 5 req/sec constant pendant 30 minutes
# Durée     : ~30 min
#
# Surveiller dans Grafana pendant le run :
#   - jvm_memory_used_bytes : doit rester stable
#   - hikaricp_connections_pending : doit rester à 0
#   - http_req_duration p95 : ne doit pas dériver dans le temps

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
  echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
  echo -e "${CYAN}║          Kora — Soak Test (stabilité 30min)      ║${NC}"
  echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
  echo ""
}

print_objectives() {
  echo -e "${YELLOW}  Objectifs du soak test${NC}"
  echo "  ┌─────────────────────────────────────────────────"
  echo "  │  Charge      : 5 req/sec constant"
  echo "  │  Durée       : 30 minutes"
  echo "  │  P95 cible   : < 300ms (tolérance soak)"
  echo "  │  Error rate  : < 1%"
  echo "  │"
  echo "  │  Surveiller dans Grafana :"
  echo "  │    → jvm_memory_used_bytes  (heap drift = fuite mémoire)"
  echo "  │    → hikaricp_connections_pending  (pool exhaustion)"
  echo "  │    → http_req_duration p95  (dégradation progressive)"
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

# ── Run k6 soak ────────────────────────────────────────────────────────────────
run_soak() {
  info "Lancement du soak test (5 req/sec × 30 min)…"
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
  local docker_influx_url="${INFLUX_URL/localhost/host.docker.internal}"

  (
    if [[ "$OSTYPE" == msys* ]] || [[ -n "${MSYSTEM:-}" ]]; then
      export MSYS_NO_PATHCONV=1
    fi
    docker run --rm \
      --add-host=host.docker.internal:host-gateway \
      -v "${perf_mount}:/perf" \
      -e BASE_URL="${docker_base_url}" \
      "${K6_IMAGE}" \
      run --out "influxdb=${docker_influx_url}" /perf/soak.js
  )

  return $?
}

# ── Main ───────────────────────────────────────────────────────────────────────
main() {
  banner
  print_objectives
  print_urls
  start_monitoring
  check_app
  print_urls

  echo ""
  if run_soak; then
    echo ""
    success "Soak test PASSED — pas de dégradation détectée sur 30 min"
    echo ""
    echo -e "${GREEN}  Vérifier dans Grafana :${NC}"
    echo -e "${GREEN}  → heap stable, connexions DB stables, p95 stable${NC}"
    echo -e "${GREEN}  → ${GRAFANA_DASHBOARD}${NC}"
    echo ""
  else
    echo ""
    error "Soak test FAILED — dégradation détectée"
    echo ""
    echo -e "${YELLOW}  Analyser dans Grafana :${NC}"
    echo -e "${YELLOW}  → Chercher le moment de dégradation dans la timeline${NC}"
    echo -e "${YELLOW}  → ${GRAFANA_DASHBOARD}${NC}"
    echo ""
    exit 1
  fi
}

main "$@"