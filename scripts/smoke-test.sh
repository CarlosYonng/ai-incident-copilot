#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"

# 端到端冒烟用例：注入告警 -> 创建 Incident -> 启动 Workflow ->
# 查询节点/工具审计 -> 记录人工处置结果 -> 生成复盘 -> 关闭 Incident。
# 该脚本只调用 HTTP API，不依赖前端，适合作为后端改动后的第一道回归检查。
require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[smoke] missing required command: $1" >&2
    exit 1
  fi
}

json_field() {
  python3 -c "$1"
}

post_json() {
  local path="$1"
  local body="$2"
  curl -fsS \
    -X POST "${BASE_URL}${path}" \
    -H "Content-Type: application/json" \
    -d "$body"
}

get_json() {
  local path="$1"
  curl -fsS "${BASE_URL}${path}"
}

require_cmd curl
require_cmd python3

echo "[smoke] base url: ${BASE_URL}"

echo "[smoke] health"
get_json "/health" | json_field 'import json,sys; data=json.load(sys.stdin); assert data["data"]["status"] in ("UP", "DEGRADED"); print(data["data"]["status"])'

echo "[smoke] ingest Grafana payment alert and create incident"
EVENT_ID="smoke-payment-$(date +%s)"
CREATE_RESPONSE="$(post_json "/alerts/grafana" "{\"alerts\":[{\"fingerprint\":\"${EVENT_ID}\",\"startsAt\":\"2026-07-04T00:00:00Z\",\"labels\":{\"alertname\":\"支付回调超时\",\"service\":\"payment-service\",\"endpoint\":\"/api/payment/callback\",\"trace_id\":\"trace-payment-timeout-smoke\",\"exception_type\":\"TimeoutError\",\"severity\":\"P1\",\"error_rate\":\"0.082\",\"p95_latency\":\"3200\",\"qps\":\"1260\",\"affected_requests\":\"238\"},\"annotations\":{\"summary\":\"smoke test payment callback timeout spike\",\"description\":\"payment callback error rate and latency crossed threshold\"}}]}")"
INCIDENT_ID="$(printf '%s' "$CREATE_RESPONSE" | json_field 'import json,sys; print(json.load(sys.stdin)["data"]["incident"]["id"])')"
echo "[smoke] incident id: ${INCIDENT_ID}"

echo "[smoke] verify inbound alert event"
get_json "/incidents/${INCIDENT_ID}/alerts" | json_field 'import json,sys; data=json.load(sys.stdin)["data"]; assert len(data) >= 1; print(data[0]["status"])'

echo "[smoke] start workflow"
WORKFLOW_RESPONSE="$(post_json "/incidents/${INCIDENT_ID}/start-workflow" '{}')"
WORKFLOW_ID="$(printf '%s' "$WORKFLOW_RESPONSE" | json_field 'import json,sys; print(json.load(sys.stdin)["data"]["workflowInstanceId"])')"
echo "[smoke] workflow id: ${WORKFLOW_ID}"

echo "[smoke] verify workflow nodes"
get_json "/workflows/${WORKFLOW_ID}/nodes" | json_field 'import json,sys; data=json.load(sys.stdin)["data"]; assert len(data) >= 8; print(len(data))'

echo "[smoke] verify tool calls"
get_json "/workflows/${WORKFLOW_ID}/tool-calls" | json_field 'import json,sys; data=json.load(sys.stdin)["data"]; assert len(data) >= 1; print(len(data))'

echo "[smoke] find approval action"
ACTIONS_RESPONSE="$(get_json "/incidents/${INCIDENT_ID}/actions")"
ACTION_ID="$(printf '%s' "$ACTIONS_RESPONSE" | json_field 'import json,sys; actions=json.load(sys.stdin)["data"]; print(next(a["id"] for a in actions if a["requiresApproval"]))')"
echo "[smoke] action id: ${ACTION_ID}"

echo "[smoke] record action result"
post_json "/actions/${ACTION_ID}/record-result" '{"executor":"sre-demo","resultDetail":"smoke test recorded action result"}' >/dev/null

echo "[smoke] verify recovering metrics"
get_json "/incidents/${INCIDENT_ID}/metrics" | json_field 'import json,sys; data=json.load(sys.stdin)["data"]; assert any(m["status"] == "recovering" for m in data); print("recovering")'

echo "[smoke] generate postmortem"
post_json "/incidents/${INCIDENT_ID}/generate-postmortem" '{}' | json_field 'import json,sys; data=json.load(sys.stdin)["data"]; assert data["summary"]; print("postmortem")'

echo "[smoke] close incident"
post_json "/incidents/${INCIDENT_ID}/close" '{"closedBy":"smoke-test","comment":"smoke test completed"}' >/dev/null

echo "[smoke] verify closed status"
get_json "/incidents/${INCIDENT_ID}" | json_field 'import json,sys; data=json.load(sys.stdin)["data"]["incident"]; assert data["status"] == "CLOSED"; print(data["status"])'

echo "[smoke] OK"
