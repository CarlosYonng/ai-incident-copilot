#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/.run"
BACKEND_LOG_DIR="${ROOT_DIR}/logs/backend"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
TAIL_LOGS="${TAIL_LOGS:-true}"
CLEANED_UP=false
BACKEND_STARTED=false
FRONTEND_STARTED=false

mkdir -p "${LOG_DIR}"
mkdir -p "${BACKEND_LOG_DIR}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[local] missing required command: $1" >&2
    exit 1
  fi
}

port_pids() {
  lsof -ti "tcp:$1" -sTCP:LISTEN 2>/dev/null || true
}

print_port_owner() {
  local port="$1"
  echo "[local] port ${port} is already in use:" >&2
  lsof -nP -iTCP:"${port}" -sTCP:LISTEN >&2 || true
}

require_port_free() {
  local port="$1"
  local label="$2"
  if [[ -n "$(port_pids "${port}")" ]]; then
    print_port_owner "${port}"
    echo "[local] ${label} cannot start because port ${port} is occupied." >&2
    echo "[local] If this is a stale local run, stop it with: scripts/stop-local.sh" >&2
    exit 1
  fi
}

kill_pid() {
  local pid="$1"
  local label="$2"
  if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
    echo "[local] stopping ${label} pid=${pid}"
    kill "${pid}" >/dev/null 2>&1 || true
  fi
}

kill_port_listeners() {
  local port="$1"
  local label="$2"
  local pids
  pids="$(port_pids "${port}")"
  if [[ -z "${pids}" ]]; then
    return 0
  fi

  echo "[local] stopping ${label} listener(s) on port ${port}: ${pids}"
  echo "${pids}" | xargs kill >/dev/null 2>&1 || true

  for _ in $(seq 1 8); do
    if [[ -z "$(port_pids "${port}")" ]]; then
      return 0
    fi
    sleep 1
  done

  pids="$(port_pids "${port}")"
  if [[ -n "${pids}" ]]; then
    echo "[local] force stopping ${label} listener(s) on port ${port}: ${pids}"
    echo "${pids}" | xargs kill -9 >/dev/null 2>&1 || true
  fi
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local log_file="$3"
  local attempts="${4:-60}"
  for _ in $(seq 1 "${attempts}"); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      echo "[local] ${label} is ready: ${url}"
      return 0
    fi
    sleep 2
  done
  echo "[local] ${label} did not become ready: ${url}" >&2
  echo "[local] last 80 lines from ${log_file}:" >&2
  tail -80 "${log_file}" >&2 || true
  if [[ "${label}" == "backend" && -n "${BACKEND_LAUNCHER_LOG:-}" ]]; then
    echo "[local] last 80 lines from ${BACKEND_LAUNCHER_LOG}:" >&2
    tail -80 "${BACKEND_LAUNCHER_LOG}" >&2 || true
  fi
  return 1
}

cleanup() {
  if [[ "${CLEANED_UP}" == "true" ]]; then
    return 0
  fi
  CLEANED_UP=true
  trap - EXIT INT TERM

  echo
  echo "[local] stopping backend/frontend processes"
  if [[ "${BACKEND_STARTED}" == "true" ]]; then
    kill_pid "${BACKEND_PID:-}" "backend launcher"
    kill_port_listeners "${BACKEND_PORT}" "backend"
  fi
  if [[ "${FRONTEND_STARTED}" == "true" ]]; then
    kill_pid "${FRONTEND_PID:-}" "frontend launcher"
    kill_port_listeners "${FRONTEND_PORT}" "frontend"
  fi
  if [[ -n "${TAIL_PID:-}" ]] && kill -0 "${TAIL_PID}" >/dev/null 2>&1; then
    kill "${TAIL_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT
trap 'cleanup; exit 130' INT
trap 'cleanup; exit 143' TERM

require_cmd curl
require_cmd lsof
require_cmd mvn
require_cmd npm

require_port_free "${BACKEND_PORT}" "backend"
require_port_free "${FRONTEND_PORT}" "frontend"

export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/incident_copilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
export SPRING_DATASOURCE_USERNAME="incident_copilot"
export SPRING_DATASOURCE_PASSWORD="incident_copilot123"
export SERVER_PORT="${SERVER_PORT:-${BACKEND_PORT}}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:${FRONTEND_PORT},http://127.0.0.1:${FRONTEND_PORT}}"
export DIAGNOSIS_MCP_BASE_URL="${DIAGNOSIS_MCP_BASE_URL:-http://localhost:8200}"
export DIAGNOSIS_MCP_TOKEN="${DIAGNOSIS_MCP_TOKEN:-}"
export DIAGNOSIS_MCP_FALLBACK_ENABLED="${DIAGNOSIS_MCP_FALLBACK_ENABLED:-true}"
export RUNBOOK_DIR="${RUNBOOK_DIR:-${ROOT_DIR}/runbooks}"
export LOG_PATH="${LOG_PATH:-${BACKEND_LOG_DIR}}"
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://localhost:${BACKEND_PORT}/api}"

BACKEND_APP_LOG="${LOG_PATH}/incident-copilot.log"
BACKEND_ERROR_LOG="${LOG_PATH}/incident-copilot-error.log"
BACKEND_LAUNCHER_LOG="${LOG_DIR}/backend-launcher.log"

echo "[local] backend app logs: ${BACKEND_APP_LOG}"
echo "[local] backend error logs: ${BACKEND_ERROR_LOG}"
echo "[local] backend launcher logs: ${BACKEND_LAUNCHER_LOG}"
echo "[local] frontend logs: ${LOG_DIR}/frontend.log"
touch "${BACKEND_APP_LOG}" "${BACKEND_ERROR_LOG}"
: >"${BACKEND_LAUNCHER_LOG}"
: >"${LOG_DIR}/frontend.log"

if [[ "${TAIL_LOGS}" == "true" ]]; then
  tail -n 0 -F "${BACKEND_APP_LOG}" "${BACKEND_ERROR_LOG}" "${LOG_DIR}/frontend.log" &
  TAIL_PID="$!"
fi

(
  cd "${ROOT_DIR}/backend"
  mvn spring-boot:run
) >"${BACKEND_LAUNCHER_LOG}" 2>&1 &
BACKEND_PID="$!"
BACKEND_STARTED=true

wait_for_http "http://localhost:${BACKEND_PORT}/api/health" "backend" "${BACKEND_APP_LOG}"

(
  cd "${ROOT_DIR}/frontend"
  if [[ ! -d node_modules ]]; then
    npm install
  fi
  npm run dev -- --host 0.0.0.0 --port "${FRONTEND_PORT}"
) >"${LOG_DIR}/frontend.log" 2>&1 &
FRONTEND_PID="$!"
FRONTEND_STARTED=true

wait_for_http "http://localhost:${FRONTEND_PORT}" "frontend" "${LOG_DIR}/frontend.log"

echo
echo "[local] services are running"
echo "[local] frontend: http://localhost:${FRONTEND_PORT}"
echo "[local] backend:  http://localhost:${BACKEND_PORT}/api"
echo "[local] logs:     ${BACKEND_APP_LOG}, ${BACKEND_ERROR_LOG}, ${LOG_DIR}/frontend.log"
echo "[local] logs cmd: scripts/logs.sh local"
echo "[local] press Ctrl+C to stop backend/frontend"

wait
