# REST API 文档

## 1. 通用约定

Base URL:

```text
http://localhost:8080/api
```

通用响应：

```json
{
  "success": true,
  "data": {},
  "errorCode": null,
  "message": null,
  "requestId": "req-20260615120000"
}
```

错误响应：

```json
{
  "success": false,
  "data": null,
  "errorCode": "WORKFLOW_NODE_FAILED",
  "message": "DiagnosisMcpNode failed: diagnosis-service unavailable",
  "requestId": "req-20260615120000"
}
```

## 2. Incident

推荐生产式入口是先调用告警入站接口，由系统记录原始事件、判断是否创建或关联 Incident，再进入 Workflow。`POST /api/incidents` 保留给人工创建或兼容场景。

## 2.1 Alert Ingest

### POST /api/alerts/ingest

记录一条来自监控、APM、日志平台或业务系统的原始告警事件。系统会先写入 `alert_event`，再根据阈值判断是否创建 Incident，或关联已有未关闭 Incident。可通过 `startWorkflow=true` 让高置信告警入站后直接执行诊断工作流。

请求：

```json
{
  "eventId": "payment-alert-20260704-001",
  "source": "payment-gateway-apm",
  "signalName": "支付回调超时",
  "serviceName": "payment-service",
  "endpoint": "/api/payment/callback",
  "traceId": "trace-payment-timeout-001",
  "exceptionType": "TimeoutError",
  "summary": "支付网关回调链路 5 分钟窗口内 500 错误率和超时数同时升高",
  "errorRate": 0.082,
  "p95Latency": 3200,
  "qps": 1260,
  "affectedRequests": 238,
  "severityHint": "P1",
  "rawPayload": {
    "businessOperation": "payment_callback",
    "gateway": "sandbox-pay-gateway",
    "alertWindow": "5m"
  },
  "startWorkflow": true
}
```

响应：

```json
{
  "alertEvent": {
    "eventId": "payment-alert-20260704-001",
    "status": "INCIDENT_CREATED",
    "decisionReason": "Actionable alert event crossed incident thresholds and opened a new incident."
  },
  "incident": {
    "id": 1,
    "incidentNo": "INC-20260704-ABC123",
    "status": "OPEN"
  },
  "workflow": {
    "workflowInstanceId": 10,
    "status": "WAITING_APPROVAL"
  }
}
```

### GET /api/incidents/{id}/alerts

查询某个 Incident 关联的入站告警事件，用于还原事故来源。

### POST /api/alerts/grafana

接收 Grafana webhook payload，并转换为统一的 `AlertIngestRequest`。这是推荐演示入口，适合表达“portfolio 被监控系统发现异常后，把告警推给 Incident Copilot”。

请求示例：

```json
{
  "alerts": [
    {
      "fingerprint": "payment-alert-20260704-001",
      "startsAt": "2026-07-04T00:00:00Z",
      "labels": {
        "alertname": "支付回调超时",
        "service": "payment-service",
        "endpoint": "/api/payment/callback",
        "trace_id": "trace-payment-timeout-001",
        "exception_type": "TimeoutError",
        "severity": "P1",
        "error_rate": "0.082",
        "p95_latency": "3200",
        "qps": "1260",
        "affected_requests": "238"
      },
      "annotations": {
        "summary": "portfolio 支付回调链路错误率和延迟同时升高",
        "description": "5 分钟窗口内 payment-service callback timeout spike"
      }
    }
  ]
}
```

响应结构同 `POST /api/alerts/ingest`。适配器会自动补齐 `source=GRAFANA`、`startWorkflow=true`，并把原始 payload 保存在 `alert_event.raw_payload_json`。

### POST /api/alerts/alertmanager

接收 Alertmanager webhook payload，并转换为统一的 `AlertIngestRequest`。字段优先从 `alerts[0].labels` 和 `alerts[0].annotations` 读取。

### POST /api/incidents

创建 Incident。

请求：

```json
{
  "title": "payment-service 支付回调超时",
  "serviceName": "payment-service",
  "endpoint": "/api/payment/callback",
  "source": "DEMO",
  "traceId": "trace-payment-timeout-001",
  "exceptionType": "TimeoutError",
  "summary": "5 分钟内 500 错误率升高，p95 延迟升至 3200ms"
}
```

响应：

```json
{
  "id": 1,
  "incidentNo": "INC-20260615-0001",
  "title": "payment-service 支付回调超时",
  "serviceName": "payment-service",
  "endpoint": "/api/payment/callback",
  "severity": "P2",
  "status": "OPEN",
  "traceId": "trace-payment-timeout-001",
  "exceptionType": "TimeoutError",
  "createdAt": "2026-06-15T12:00:00"
}
```

### GET /api/incidents

查询 Incident 列表。

Query:

- `status`
- `serviceName`
- `severity`
- `page`
- `size`

### GET /api/incidents/{id}

查询 Incident 详情，包含基础信息、最近指标、诊断摘要、候选处置方案和复盘状态。

### POST /api/incidents/{id}/start-workflow

启动 Workflow。

响应：

```json
{
  "workflowInstanceId": 10,
  "incidentId": 1,
  "workflowType": "IncidentHandlingWorkflow",
  "status": "RUNNING",
  "currentNode": "MetricsCollectorNode"
}
```

### POST /api/incidents/{id}/close

关闭 Incident。关闭时写入 `recovered` 指标快照，表示处理流程已经从恢复观察进入关闭状态。

请求：

```json
{
  "closedBy": "demo-user",
  "comment": "指标恢复，复盘已生成"
}
```

## 3. Workflow

### GET /api/workflows/{instanceId}

查询 Workflow 实例。

### GET /api/workflows/{instanceId}/nodes

查询节点执行记录。

响应：

```json
[
  {
    "id": 101,
    "nodeName": "DiagnosisMcpNode",
    "nodeType": "MCP",
    "status": "SUCCESS",
    "durationMs": 1320,
    "inputJson": {},
    "outputJson": {},
    "errorMessage": null,
    "startedAt": "2026-06-15T12:01:00",
    "finishedAt": "2026-06-15T12:01:02"
  }
]
```

### POST /api/workflows/{instanceId}/retry-node/{nodeExecutionId}

重试失败节点。MVP 可选。

规则：

- 仅允许重试状态为 `FAILED` 的节点。
- 重试会新增一条 `workflow_node_execution`。
- 原失败记录不覆盖。

## 4. Action Proposal

### GET /api/incidents/{id}/actions

查询处置方案列表。

响应：

```json
[
  {
    "id": 201,
    "title": "开启支付回调延迟重试",
    "actionType": "ENABLE_DELAYED_RETRY",
    "riskLevel": "MEDIUM",
    "reason": "延迟重试可以降低瞬时压力",
    "impact": "订单状态可能延迟 5-10 分钟",
    "precheck": "确认支付网关支持延迟重试，确认不会造成重复支付",
    "requiresApproval": true,
    "status": "PENDING"
  }
]
```

### POST /api/actions/{actionId}/approve

批准方案。仅记录审批和处理意图，不自动执行危险动作。

请求：

```json
{
  "approvedBy": "sre-demo",
  "comment": "确认前置检查通过，批准线下执行"
}
```

### POST /api/actions/{actionId}/reject

驳回方案。

请求：

```json
{
  "approvedBy": "sre-demo",
  "comment": "证据不足，暂不处理"
}
```

### POST /api/actions/{actionId}/record-result

记录处置结果。系统不直接执行生产变更，只记录“处理人在线下系统完成了哪项动作、结果是什么”，用于把人工执行和 Incident 生命周期串起来。

请求：

```json
{
  "executor": "sre-demo",
  "resultDetail": "已在线下通过值班系统开启延迟重试，本系统只记录结果"
}
```

效果：

- 新增 `human_approval`，decision 为 `MARK_OFFLINE_EXECUTED`。
- 新增 `action_record`。
- `action_proposal.status` 更新为 `OFFLINE_EXECUTED`。
- Incident 状态更新为 `RECOVERING`。
- Incident 指标快照新增 `recovering` 样本。

### POST /api/actions/{actionId}/mark-offline-executed

兼容旧演示脚本的接口，行为等同于 `POST /api/actions/{actionId}/record-result`。新代码和文档建议使用 `record-result`。

### POST /api/actions/{actionId}/escalate

升级给 SRE。

请求：

```json
{
  "approvedBy": "backend-demo",
  "comment": "涉及回滚风险，升级给 SRE"
}
```

## 5. Reports

### GET /api/incidents/{id}/postmortem

查询复盘报告。

### POST /api/incidents/{id}/generate-postmortem

生成复盘报告。

响应：

```json
{
  "incidentId": 1,
  "summary": "payment-service 支付回调超时导致订单状态更新延迟",
  "rootCause": "order-service 写库耗时升高，导致 payment-service 回调链路超时",
  "impact": "部分用户订单状态延迟 5-10 分钟",
  "actionItems": [
    "补充 order-service 写库耗时监控",
    "为支付回调链路增加延迟重试策略"
  ],
  "reportContent": "..."
}
```

## 6. Demo / Metrics

### POST /api/demo/faults/payment-timeout

触发支付回调超时样例故障。该接口只用于本地演示；真实主线推荐使用 `POST /api/alerts/grafana` 或 `POST /api/alerts/alertmanager`。

请求：

```json
{
  "autoCreateIncident": true
}
```

### POST /api/demo/faults/order-npe

触发订单创建空指针模拟故障。

### GET /api/incidents/{incidentId}/metrics

查询某个 Incident 的指标快照。当前快照来自入站告警 payload 和演示状态机；生产接入时可把 `IncidentMetricsService` 替换为 Prometheus / Grafana 查询适配器。

响应：

```json
[
  {
    "serviceName": "payment-service",
    "errorRate": 6.8,
    "p95Latency": 3200,
    "qps": 160,
    "status": "degraded",
    "snapshotTime": "2026-06-15T12:00:30"
  }
]
```

## 7. Tool Call Audit

### GET /api/workflows/{instanceId}/tool-calls

查询 MCP / LLM 工具调用日志。

响应：

```json
[
  {
    "id": 301,
    "nodeName": "DiagnosisMcpNode",
    "toolName": "generate_report",
    "success": true,
    "durationMs": 880,
    "requestJson": {},
    "responseJson": {},
    "createdAt": "2026-06-15T12:01:02"
  }
]
```
