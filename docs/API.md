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

关闭 Incident。关闭时 mock metrics 状态更新为 `recovered`。

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

### POST /api/actions/{actionId}/mark-offline-executed

标记线下已执行。用于演示中风险动作处理闭环。

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
- mock metrics 新增 `recovering` 快照。

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

## 6. Mock Demo

### POST /api/demo/faults/payment-timeout

触发支付回调超时模拟故障。可选择直接创建 Incident。

请求：

```json
{
  "autoCreateIncident": true
}
```

### POST /api/demo/faults/order-npe

触发订单创建空指针模拟故障。

### GET /api/demo/metrics/{incidentId}

查询模拟指标快照。

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

