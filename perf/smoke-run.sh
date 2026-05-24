#!/usr/bin/env bash
# smoke-run.sh — lance le smoke test k6 en une commande.
# Usage : ./perf/smoke-run.sh [BASE_URL]
#
# Ce script :
#   1. Démarre InfluxDB + Grafana + MailDev si nécessaire (docker compose up -d)
#   2. Attend que l'app Spring soit prête (GET /actuator/health)
#   3. Lance smoke.js via le container k6 officiel
#   4. Affiche les URLs utiles avant et après le run
#
# Pourquoi MailDev est nécessaire :
#   SmtpMailAdapter est actif avec le profil "perf" (@Profile("!test")).
#   Spring Actuator MailHealthIndicator teste la connexion SMTP à chaque /health.
#   Si MailDev n'est pas up, health = DOWN et le script n'attend jamais l'app.

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
  echo -e "${CYAN}║          Kora — Smoke Test (k6)                  ║${NC}"
  echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
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

# ── 1. Docker Compose — influxdb + grafana ─────────────────────────────────────
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

    # MailDev SMTP doit être prêt avant que l'app démarre.
    # Spring Actuator MailHealthIndicator teste la connexion SMTP à chaque /health.
    info "Attente démarrage MailDev SMTP (port 1025)…"
    local retries=0
    until nc -z localhost 1025 > /dev/null 2>&1; do
      retries=$((retries + 1))
      if [[ $retries -ge 30 ]]; then
        error "MailDev n'a pas démarré en 30s"
        exit 1
      fi
      sleep 1
    done
    success "MailDev prêt (SMTP:1025  UI:1080)"

    info "Attente démarrage InfluxDB (port 8086)…"
    retries=0
    until curl -sf "http://localhost:8086/ping" > /dev/null 2>&1; do
      retries=$((retries + 1))
      if [[ $retries -ge 30 ]]; then
        error "InfluxDB n'a pas démarré en 30s"
        exit 1
      fi
      sleep 1
    done
    success "InfluxDB prêt"
  else
    success "Monitoring déjà démarré (influxdb, grafana, maildev)"
  fi
}

# ── 2. Vérification rapide de l'app (non bloquante) ───────────────────────────
check_app() {
  if curl -sf --max-time 3 "${HEALTH_URL}" > /dev/null 2>&1; then
    success "App joignable sur ${BASE_URL}"
  else
    echo -e "${YELLOW}  ⚠ App non joignable sur ${BASE_URL} — k6 échouera si elle n'est pas lancée${NC}"
    echo -e "${YELLOW}    → SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun${NC}"
  fi
}

# ── 3. Run k6 smoke ────────────────────────────────────────────────────────────
run_smoke() {
  info "Lancement du smoke test (1 VU, 2 min)…"
  echo ""

  # Résolution du chemin perf/ selon l'environnement.
  #
  # MSYS2/Git Bash : MSYS convertit "-v /e/foo:/perf" en "-v E:\foo" + mode "/perf"
  # → "invalid mode: /perf". Il faut :
  #   1. pwd -W  → chemin Windows natif avec / (E:/projects/...)
  #   2. export MSYS_NO_PATHCONV=1 dans un sous-shell AVANT d'appeler docker
  #      (l'inline "VAR=val cmd" applique la var après l'expansion des args)
  local perf_mount
  if grep -qi microsoft /proc/version 2>/dev/null; then
    # WSL
    perf_mount="$(wslpath -w "${SCRIPT_DIR}")"
  elif [[ "$OSTYPE" == msys* ]] || [[ -n "${MSYSTEM:-}" ]]; then
    # MSYS2 / Git Bash
    perf_mount="$(cd "${SCRIPT_DIR}" && pwd -W)"
  else
    perf_mount="${SCRIPT_DIR}"
  fi

  # k6 rejoint le réseau Docker Compose pour atteindre InfluxDB par son nom de service.
  # host.docker.internal (fourni nativement par Docker Desktop) reste valide pour l'app.
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
      run --out "influxdb=http://influxdb:8086/k6" /perf/smoke.js
  )

  local exit_code=$?
  return $exit_code
}

# ── Main ───────────────────────────────────────────────────────────────────────
main() {
  banner
  print_urls
  start_monitoring
  check_app
  print_urls

  echo ""
  if run_smoke; then
    echo ""
    success "Smoke test PASSED"
    echo ""
    echo -e "${GREEN}  Consulte les résultats dans Grafana :${NC}"
    echo -e "${GREEN}  → ${GRAFANA_DASHBOARD}${NC}"
    echo ""
  else
    echo ""
    error "Smoke test FAILED — consulte les logs k6 ci-dessus"
    echo ""
    echo -e "${YELLOW}  Dashboard Grafana pour analyse :${NC}"
    echo -e "${YELLOW}  → ${GRAFANA_DASHBOARD}${NC}"
    echo ""
    exit 1
  fi
}

main "$@"