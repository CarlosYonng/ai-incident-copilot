#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_COMPOSE="${INFRA_COMPOSE:-${ROOT_DIR}/../ai-agent-infra-stack/docker-compose.yml}"
MYSQL_ROOT_USER="${MYSQL_ROOT_USER:-root}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root123}"

cd "${ROOT_DIR}"

if [[ ! -f "${INFRA_COMPOSE}" ]]; then
  echo "[init-db] infra compose file not found: ${INFRA_COMPOSE}" >&2
  exit 1
fi

echo "[init-db] creating incident_copilot database and dedicated user in infra MySQL"
docker compose -f "${INFRA_COMPOSE}" exec -T mysql \
  mysql -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" < database/init-database.sql

echo "[init-db] database: incident_copilot"
echo "[init-db] user:     incident_copilot"
echo "[init-db] done"
