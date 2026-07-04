#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"

cd "${ROOT_DIR}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[docker] .env not found; using defaults from .env.example"
  cp .env.example .env
fi

echo "[docker] building and starting services"
docker compose --env-file .env up --build -d backend frontend

echo "[docker] waiting for backend health"
for _ in $(seq 1 60); do
  if curl -fsS "http://localhost:8080/api/health" >/dev/null 2>&1; then
    echo "[docker] backend is ready: http://localhost:8080/api"
    echo "[docker] frontend: http://localhost:3000"
    exit 0
  fi
  sleep 2
done

echo "[docker] backend did not become healthy in time" >&2
echo "[docker] inspect logs with: docker compose logs -f backend" >&2
echo "[docker] last backend logs:" >&2
docker compose logs --tail=120 backend >&2 || true
exit 1
