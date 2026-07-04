#!/usr/bin/env bash
set -euo pipefail

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"

stop_port() {
  local port="$1"
  local label="$2"
  local pids
  pids="$(lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -z "${pids}" ]]; then
    echo "[stop-local] ${label} is not listening on port ${port}"
    return 0
  fi

  echo "[stop-local] stopping ${label} on port ${port}: ${pids}"
  echo "${pids}" | xargs kill >/dev/null 2>&1 || true

  for _ in $(seq 1 10); do
    if [[ -z "$(lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null || true)" ]]; then
      echo "[stop-local] ${label} stopped"
      return 0
    fi
    sleep 1
  done

  pids="$(lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "${pids}" ]]; then
    echo "[stop-local] force stopping ${label}: ${pids}"
    echo "${pids}" | xargs kill -9 >/dev/null 2>&1 || true
  fi
}

stop_port "${BACKEND_PORT}" "backend"
stop_port "${FRONTEND_PORT}" "frontend"

echo "[stop-local] done"
