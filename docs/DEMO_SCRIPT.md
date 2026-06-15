# 演示脚本

## 1. 启动服务

```bash
docker compose up -d mysql diagnosis-service demo-service incident-copilot-backend incident-copilot-frontend
```

打开：

```text
http://localhost:3000
```

## 2. 触发支付回调超时

在前端点击“触发支付回调超时”，或调用：

```http
POST http://localhost:8080/api/demo/faults/payment-timeout
Content-Type: application/json

{
  "autoCreateIncident": true
}
```

讲解点：

- demo-service 模拟业务异常。
- 异常日志推送到 diagnosis-service。
- Incident Copilot 是上层协同系统。

## 3. 查看 Incident

进入 Incident 列表，打开新创建的 `payment-service 支付回调超时`。

讲解点：

- Incident 中记录服务、接口、trace id、异常类型。
- 当前状态为 `OPEN`。

## 4. 启动 Workflow

点击“启动 Workflow”。

讲解点：

- 这是固定节点的 Agent Workflow，不是单轮问答。
- 每个节点都有输入、输出、状态、耗时和错误记录。

## 5. 展示 MetricsCollectorNode

打开 Workflow 时间线，查看 `MetricsCollectorNode`。

示例输出：

```json
{
  "serviceName": "payment-service",
  "errorRate": 6.8,
  "p95Latency": 3200,
  "qps": 160,
  "status": "degraded"
}
```

讲解点：

- MVP 先用 mock metrics。
- 后续可替换为 Prometheus / Alertmanager。

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

1. 通知 order-service 负责人介入，风险低。
2. 开启支付回调延迟重试，风险中。
3. 回滚 order-service 最近版本，风险高。

讲解点：

- 风险低的建议可以直接记录。
- 中高风险必须人工确认。
- 高风险动作只生成建议，不自动执行。

## 9. 人工标记线下已执行

对“开启支付回调延迟重试”点击“标记线下已执行”。

填写：

```text
已在线下通过值班系统开启延迟重试，本系统只记录处理结果。
```

讲解点：

- Copilot 不直接改生产配置。
- 人工确认记录写入 `human_approval`。
- 处理结果写入 `action_record`。

## 10. 展示指标恢复

刷新 mock metrics：

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

- mock metrics 进入 `recovered`。
- Incident 状态为 `CLOSED`。
- 整个流程形成可审计闭环。

