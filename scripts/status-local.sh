#!/usr/bin/env bash
set -euo pipefail

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"

show_port() {
  local port="$1"
  local label="$2"
  echo "[status-local] ${label} port ${port}"
  if ! lsof -nP -iTCP:"${port}" -sTCP:LISTEN; then
    echo "[status-local] no listener on port ${port}"
  fi
}

show_port "${BACKEND_PORT}" "backend"
show_port "${FRONTEND_PORT}" "frontend"
