#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-local}"

cd "${ROOT_DIR}"

case "${MODE}" in
  local)
    mkdir -p .run
    mkdir -p logs/backend
    touch logs/backend/incident-copilot.log logs/backend/incident-copilot-error.log .run/frontend.log
    echo "[logs] following local logs"
    echo "[logs] backend:       ${ROOT_DIR}/logs/backend/incident-copilot.log"
    echo "[logs] backend error: ${ROOT_DIR}/logs/backend/incident-copilot-error.log"
    echo "[logs] frontend:      ${ROOT_DIR}/.run/frontend.log"
    tail -n 120 -F logs/backend/incident-copilot.log logs/backend/incident-copilot-error.log .run/frontend.log
    ;;
  docker)
    echo "[logs] following docker compose logs"
    docker compose logs -f --tail=200 backend frontend
    ;;
  backend)
    mkdir -p logs/backend
    touch logs/backend/incident-copilot.log
    tail -n 160 -F logs/backend/incident-copilot.log
    ;;
  error)
    mkdir -p logs/backend
    touch logs/backend/incident-copilot-error.log
    tail -n 160 -F logs/backend/incident-copilot-error.log
    ;;
  frontend)
    mkdir -p .run
    touch .run/frontend.log
    tail -n 160 -F .run/frontend.log
    ;;
  *)
    echo "Usage: scripts/logs.sh [local|docker|backend|error|frontend]" >&2
    exit 1
    ;;
esac
