# 演示脚本

## 1. 启动服务

```bash
docker compose up -d mysql diagnosis-service demo-service incident-copilot-backend incident-copilot-frontend
```

打开：

```text
http://localhost:3000
```

## 2. 注入支付回调超时告警

在前端点击“注入 portfolio 支付告警”，或调用：

```http
POST http://localhost:8080/api/alerts/ingest
Content-Type: application/json

{
  "eventId": "payment-alert-demo-001",
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
  "startWorkflow": false
}
```

讲解点：

- 这里模拟的是上游 APM/监控平台推送业务告警，不是直接创建 Incident。
- Copilot 先写入 `alert_event`，再做阈值判断、幂等和 Incident 关联。
- Incident Copilot 是上层协同系统。

## 3. 查看 Incident

进入 Incident 列表，打开新创建的 `payment-service 支付回调超时`。

讲解点：

- 入站事件面板展示原始告警来源、错误率、p95、影响请求数和入站决策。
- Incident 中记录服务、接口、trace id、异常类型。
- 手动演示入口默认只完成告警入站，方便继续观察“启动 Workflow”按钮的作用。

## 4. 启动 Workflow

点击“启动 Workflow”。如果使用的是 Grafana / Alertmanager webhook 生产入口，适配器可以自动设置 `startWorkflow=true`，此时可直接查看 Workflow 时间线。

讲解点：

- 这是固定节点的 Agent Workflow，不是单轮问答。
- 每个节点都有输入、输出、状态、耗时和错误记录。

## 5. 展示 MetricsCollectorNode

打开 Workflow 时间线，查看 `MetricsCollectorNode`。

示例输出：

```json
{
  "serviceName": "payment-service",
  "errorRate": 0.076,
  "p95Latency": 3200,
  "qps": 160,
  "status": "degraded"
}
```

讲解点：

- 入站时会保存上游告警携带的 errorRate、p95、qps。
- 当前版本用 Incident 指标快照表达故障从 `degraded` 到 `recovering` 的状态变化；真实系统可替换为 Prometheus / Grafana 查询。

## 6. 展示 DiagnosisMcpNode

查看 MCP 调用日志：

- `search_logs`
- `search_tickets`
- `generate_report`

讲解点：

- `diagnosis-service` 是独立 MCP 工具服务。
- 上层 Copilot 通过 MCP 获取只读证据。
- 每次工具调用都写入 `tool_call_log`。

## 7. 展示 RunbookRetrieverNode

查看命中的 `payment-callback-timeout.md`。

讲解点：

- Runbook 提供标准化处置知识。
- LLM 不是凭空生成建议，而是基于诊断报告、指标和 Runbook。

## 8. 展示候选处置方案

示例：

1. 继续观察并补充支付链路日志，风险低。
2. 开启支付回调延迟重试，风险中。
3. 回滚 payment-service 最近版本或切换生产配置，风险高。

讲解点：

- 风险低的建议可以直接记录。
- 中高风险必须人工确认。
- 高风险动作只生成建议，不自动执行。

## 9. 人工记录处置结果

对“开启支付回调延迟重试”点击“记录处理结果”。

填写：

```text
已在线下通过值班系统开启延迟重试，本系统只记录处理结果。
```

讲解点：

- Copilot 不直接改生产配置。
- 人工确认记录写入 `human_approval`。
- 处理结果写入 `action_record`。

## 10. 展示指标恢复

刷新 Incident 指标快照：

```json
{
  "errorRate": 1.2,
  "p95Latency": 600,
  "status": "recovering"
}
```

讲解点：

- 处理动作后进入观察阶段。
- 真实系统可接 Prometheus 查询指标恢复情况。

## 11. 生成复盘报告

点击“生成复盘报告”。

报告包含：

- 故障摘要。
- 影响范围。
- 根因。
- 时间线。
- 处理过程。
- 改进项。
- 预防措施。

## 12. 关闭 Incident

点击“关闭 Incident”。

讲解点：

- 指标快照进入 `recovered`。
- Incident 状态为 `CLOSED`。
- 整个流程形成可审计闭环。
