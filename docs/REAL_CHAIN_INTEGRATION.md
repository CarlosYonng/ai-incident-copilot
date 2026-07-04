# 真实链路联调说明

## 当前真实链路能力

AI Incident Copilot 当前可以真实调用相邻 `diagnosis-service` 的 MCP JSON-RPC 接口：

```text
incident-copilot-backend -> POST /mcp -> diagnosis-service
```

已接入 MCP tools：

- `search_logs`
- `search_code`
- `search_tickets`
- `generate_report`

调用结果会写入 `tool_call_log`，可通过接口查看：

```bash
curl http://localhost:8080/api/workflows/1/tool-calls
```

## Demo 模式与严格模式

默认本地 demo 使用 fallback：

```env
DIAGNOSIS_MCP_FALLBACK_ENABLED=true
```

这意味着 `diagnosis-service` 未启动时，Workflow 仍会生成模板证据并继续演示。

真实链路验收时必须关闭 fallback：

```env
DIAGNOSIS_MCP_FALLBACK_ENABLED=false
```

关闭后，只要 MCP 不可用、鉴权失败或返回错误，Workflow 会失败并保留失败节点与工具调用审计。

## 真实 MCP 联调步骤

1. 启动诊断服务。

```bash
cd ../diagnosis-service
docker compose up -d --build
curl http://localhost:8200/health
```

2. 导入或推送诊断数据。

```bash
curl -X POST http://localhost:8200/api/logs \
  -H 'Content-Type: application/json' \
  -d '{"service":"payment-service","level":"ERROR","trace_id":"trace-payment-timeout-001","endpoint":"/api/payment/callback","exception_type":"TimeoutError","message":"order-service update payment status timeout"}'
```

3. 启动本项目，关闭 fallback。

```bash
cp .env.example .env
# edit .env: DIAGNOSIS_MCP_FALLBACK_ENABLED=false
docker compose up -d --build
scripts/smoke-test.sh
```

4. 确认工具调用为真实成功。

```bash
curl http://localhost:8080/api/workflows/1/tool-calls
```

期望：

- `success=true`
- `toolName` 包含 `search_logs`、`search_code`、`search_tickets`、`generate_report`
- `responseJson` 不是 fallback 模板

## 当前仍需外部系统接入的部分

以下内容不是当前仓库单独能完成的，需要外部真实系统或新适配器：

1. **真实生产指标**
   当前 `metrics/` 模块用入站告警 payload 和演示状态机保存 Incident 指标快照。若要读取真实时序数据，需要接入 Prometheus、Datadog、Grafana Mimir 或内部监控系统，并实现 `MetricsProvider` 适配器。

2. **真实告警来源**
   当前已提供 Grafana / Alertmanager webhook 适配器；生产环境需要在监控平台配置 webhook URL，并确认 label / annotation 映射规则。`/api/demo/faults/*` 只保留为本地兼容入口。

3. **真实执行系统**
   当前系统只记录人工线下执行结果，不调用发布、回滚、扩缩容、SQL 或配置平台。这是安全边界，不建议在 MVP 内打通真实写操作。

4. **真实 LLM Provider**
   当前处置方案和复盘是模板化生成。若要真实 LLM，需要增加 LLM Client、密钥配置、JSON Schema 校验、重试和脱敏。

## 建议扩展接口

后续可以抽象以下接口：

```java
interface MetricsProvider
interface AlertSourceAdapter
interface LlmClient
interface RemediationRecordSink
```

保持核心 Workflow 不依赖具体外部供应商。
