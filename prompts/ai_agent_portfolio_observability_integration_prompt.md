# Prompt: Upgrade ai-agent-portfolio Observability and Incident Alert Chain

你现在在 `ai-agent-portfolio` 项目中工作。请基于该项目当前真实业务链路接入 Prometheus + Grafana，并把真实告警推送到相邻项目 `ai-incident-copilot` 的告警入口。

这份任务不是让 `ai-agent-portfolio` 强行迎合 Incident Copilot 现有设计，而是以 `ai-agent-portfolio` 的实际接口、实际依赖和实际异常面为主；如果告警语义与 Incident Copilot runbook 不一致，优先优化告警字段表达，其次再在交付说明中提出 Incident Copilot 需要补充的 runbook。

## 0. 分支和工作区硬约束

1. 先执行：

   ```bash
   git branch --show-current
   ```

2. 必须确认当前分支是：

   ```text
   codex-extract-diagnosis-mcp
   ```

3. 如果不是这个分支，立即停止并提醒用户切换，不要继续修改。
4. 只修改 `ai-agent-portfolio` 当前仓库，不要去修改 `ai-incident-copilot` 仓库。
5. 不要破坏当前分支已有的 diagnosis MCP 相关实现；本次是在现有真实诊断链路之上补齐真实监控告警链路。

## 1. 已确认的 ai-agent-portfolio 项目事实

请再次读取代码确认，但当前已知事实如下：

- Java 后端是 Spring Boot 多模块项目，入口模块在 `backend-java/agent-boot`，已有 Actuator health，但还没有 Prometheus registry。
- Java 主业务入口是普通 JSON 接口，不是 SSE：
  - `POST /api/chat/messages`
  - `GET /api/chat/sessions`
  - `GET /api/chat/sessions/{sessionId}/messages`
- Python AI service 是 FastAPI，入口在 `ai-service/app/main.py`：
  - `GET /api/health`
  - `POST /api/agent/ask`
- Java `ChatService` 会把聊天请求转发到 Python `POST /api/agent/ask`。
- Python RAG 链路在 `ai-service/app/agents/rag_agents.py` 和 `ai-service/app/retrieval/hybrid_retriever.py`。
- 检索策略是 Qdrant 优先，Qdrant 无结果后尝试 Neo4j GraphRAG fallback；MySQL 不直接参与检索，只负责元数据和历史。
- 知识库上传入口在 Java `KnowledgeBaseController`：
  - `POST /api/kb/{kbId}/documents/upload`
  - `POST /api/kb/documents/{id}/retry`
- 知识库入库由 `DocumentIngestionService` 异步启动 `scripts/ingest_docs.py`，阶段包括上传、切片、embedding、写 Qdrant、写 Neo4j、更新 MySQL 状态。
- 项目依赖 MySQL、Redis、Qdrant、Neo4j、LLM provider、Embedding provider。
- 项目当前没有对外 SSE 聊天接口。即使 Python `model_client.py` 内部保留 stream 相关方法，也不要为本次告警链路新增 SSE 中断告警。

## 2. 总目标

形成真实业务数据链路：

```text
ai-agent-portfolio real request
  -> Java/FastAPI metrics and logs
  -> Prometheus scrape
  -> Grafana dashboard and alert rule
  -> POST ai-incident-copilot /api/alerts/grafana
  -> alert_event
  -> Incident
  -> diagnosis MCP evidence
  -> runbook retrieval
  -> action proposal / approval / postmortem
```

不要只做静态 demo payload。必须尽量从真实接口、真实异常、真实指标产生告警。

## 3. Incident Copilot 告警入口契约

`ai-incident-copilot` 默认 API base URL：

```text
http://localhost:8080/api
```

如果 `ai-agent-portfolio` 运行在 Docker 容器中，推送宿主机上的 Incident Copilot 通常使用：

```text
http://host.docker.internal:8080/api
```

请在 `ai-agent-portfolio` 中新增环境变量，不要硬编码：

```env
INCIDENT_COPILOT_BASE_URL=http://host.docker.internal:8080/api
INCIDENT_COPILOT_GRAFANA_WEBHOOK_URL=http://host.docker.internal:8080/api/alerts/grafana
```

首选推送入口：

```text
POST /api/alerts/grafana
```

Grafana webhook payload 最终要包含这些可被 Incident Copilot 解析的字段：

```json
{
  "alerts": [
    {
      "fingerprint": "stable-alert-event-id",
      "startsAt": "2026-07-04T00:00:00Z",
      "labels": {
        "alertname": "PortfolioAiServiceTimeout",
        "service": "ai-agent-portfolio",
        "endpoint": "/api/chat/messages",
        "exception_type": "AIServiceTimeout",
        "severity": "P1",
        "error_rate": "0.052",
        "p95_latency": "4200",
        "qps": "180",
        "affected_requests": "34"
      },
      "annotations": {
        "summary": "ai-agent-portfolio chat messages are timing out while calling ai-service",
        "description": "Real Grafana alert generated from portfolio business metrics. Java backend -> ai-service -> RAG chain is degraded."
      }
    }
  ]
}
```

字段映射规则：

- `alertname` -> `signalName`
- `service` 或 `service_name` -> `serviceName`
- `endpoint` 或 `path` -> `endpoint`
- `trace_id` 或 `traceId` -> `traceId`，可为空；不要作为 Prometheus 高基数 label
- `exception_type` 或 `exceptionType` -> `exceptionType`
- `description` 或 `summary` -> `summary`
- `error_rate` -> `errorRate`
- `p95_latency`、`p95Latency` 或 `p95` -> `p95Latency`
- `qps` -> `qps`
- `affected_requests` 或 `affectedRequests` -> `affectedRequests`
- `severity` 或 `severity_hint` -> `severityHint`

Incident Copilot 当前入站阈值：

- `errorRate >= 0.0200`
- 或 `p95Latency >= 1000ms`
- 或 `affectedRequests >= 10`
- 或同时存在 `exceptionType` 和 `summary`

所以 Grafana 告警必须至少提供上述任意一组条件，避免入站后被标记为 `IGNORED`。

## 4. 真实告警类型和 runbook 对齐

请以 `ai-agent-portfolio` 的实际业务链路为主，配置下面这些告警。不要配置 SSE 告警。

| 实际故障面 | 推荐 alertname | 推荐 exception_type | 推荐 service | 推荐 endpoint/operation | 对齐 runbook |
| --- | --- | --- | --- | --- | --- |
| Java 调 Python AI service 超时或 5xx | `PortfolioAiServiceTimeout` | `AIServiceTimeout` | `ai-agent-portfolio` | `/api/chat/messages` | `portfolio-ai-service-timeout.md` |
| LLM provider 超时、限流或连续 5xx | `PortfolioLlmProviderError` | `LLMProviderError` 或 `AIServiceTimeout` | `portfolio-ai-service` | `llm_chat_completion` | `portfolio-ai-service-timeout.md` |
| RAG 检索无可引用证据或引用数异常下降 | `PortfolioRagRetrievalEmptySpike` | `RAGRetrievalEmpty` | `portfolio-ai-service` | `/api/agent/ask` 或 `retrieve_knowledge` | `portfolio-rag-retrieval-empty.md` |
| Qdrant 查询失败、超时或错误率升高 | `PortfolioQdrantUnavailable` | `QdrantUnavailable` | `portfolio-ai-service` | `qdrant_search` | `portfolio-qdrant-unavailable.md` |
| Neo4j GraphRAG fallback 查询失败、超时或命中异常下降 | `PortfolioGraphRagFallbackFailure` | `GraphRagFallbackFailure` | `portfolio-ai-service` | `graphrag_fallback` | `portfolio-graphrag-fallback-failure.md` |
| 知识库入库失败：切片、embedding、Qdrant upsert、Neo4j 写入、状态回写 | `PortfolioKnowledgeIngestionFailure` | `KnowledgeIngestionFailure` | `ai-agent-portfolio` | `/api/kb/{kbId}/documents/upload` 或 `knowledge_ingestion` | `portfolio-knowledge-ingestion-failure.md` |
| Embedding provider 超时、限流、维度不匹配 | `PortfolioEmbeddingProviderError` | `EmbeddingProviderError` 或 `KnowledgeIngestionFailure` | `ai-agent-portfolio` | `embedding` | `portfolio-knowledge-ingestion-failure.md` |
| Redis 缓存、限流、去重、JWT blacklist 或分布式锁异常 | `PortfolioRedisDependencyDegraded` | `RedisUnavailable` | `ai-agent-portfolio` | `redis_cache` | `redis-cache-failure.md` |
| MySQL 慢查询、连接池耗尽、状态写入失败 | `PortfolioDatabaseDegraded` | `DatabaseSlowQuery` 或 `DatabaseUnavailable` | `ai-agent-portfolio` | `mysql` | `database-slow-query.md` 或 `dependency-unavailable.md` |

如果实现过程中发现更准确的实际故障类型，以代码事实为准；但不要重新引入 SSE。

## 5. 指标接入要求

### 5.1 Java backend

在 Spring Boot 侧接入：

- `micrometer-registry-prometheus`
- 暴露 `/actuator/prometheus`
- 在 `management.endpoints.web.exposure.include` 中加入 `prometheus`

建议指标：

```text
portfolio_java_http_requests_total{endpoint,method,status}
portfolio_java_http_request_duration_seconds_bucket{endpoint,method}
portfolio_chat_requests_total{status}
portfolio_chat_ai_service_duration_seconds_bucket
portfolio_chat_ai_service_errors_total{error_type}
portfolio_kb_upload_total{status}
portfolio_kb_ingestion_total{stage,status}
portfolio_kb_ingestion_duration_seconds_bucket{stage}
portfolio_kb_ingestion_failures_total{stage,error_type}
portfolio_redis_operations_total{operation,status}
portfolio_redis_operation_duration_seconds_bucket{operation}
portfolio_mysql_operations_total{operation,status}
portfolio_mysql_operation_duration_seconds_bucket{operation}
```

### 5.2 Python AI service

在 FastAPI 侧接入 `prometheus-client` 或项目一致的 Prometheus middleware，暴露：

```text
GET /metrics
```

建议指标：

```text
portfolio_ai_http_requests_total{endpoint,method,status}
portfolio_ai_http_request_duration_seconds_bucket{endpoint,method}
portfolio_agent_ask_total{status}
portfolio_agent_ask_duration_seconds_bucket
portfolio_llm_requests_total{operation,model,status,error_type}
portfolio_llm_request_duration_seconds_bucket{operation,model}
portfolio_embedding_requests_total{operation,provider,status,error_type}
portfolio_embedding_request_duration_seconds_bucket{operation,provider}
portfolio_rag_retrieve_total{status}
portfolio_rag_retrieve_duration_seconds_bucket
portfolio_rag_retrieved_chunks_bucket
portfolio_rag_retrieval_empty_total{reason}
portfolio_rag_answer_citation_count_bucket
portfolio_qdrant_search_total{status,error_type}
portfolio_qdrant_search_duration_seconds_bucket
portfolio_neo4j_query_total{operation,status,error_type}
portfolio_neo4j_query_duration_seconds_bucket{operation}
portfolio_graphrag_fallback_total{status}
portfolio_graph_context_hit_total{status}
```

不要在 Prometheus label 中放高基数字段，例如完整 `trace_id`、用户输入、prompt、documentId、messageId、sessionId。traceId 留在日志和诊断服务中，需要时放在 Grafana annotation 或 raw payload 样例里。

## 6. Prometheus / Grafana 配置要求

在 `ai-agent-portfolio` 中新增或扩展监控目录，例如：

```text
ops/monitoring/
  prometheus/prometheus.yml
  prometheus/rules/portfolio-alerts.yml
  grafana/provisioning/datasources/prometheus.yml
  grafana/provisioning/dashboards/dashboards.yml
  grafana/dashboards/ai-agent-portfolio-overview.json
  grafana/dashboards/ai-agent-portfolio-rag.json
  grafana/dashboards/ai-agent-portfolio-dependencies.json
```

在 `infra/docker-compose.yml` 中加入或扩展：

- `prometheus`
- `grafana`
- 如采用 Alertmanager，再加入 `alertmanager`；否则优先用 Grafana webhook contact point 直接推送 `INCIDENT_COPILOT_GRAFANA_WEBHOOK_URL`

Grafana dashboard 至少包括：

- 总览：Java API QPS、error rate、p95/p99、AI service 调用耗时。
- Chat/RAG：`/api/chat/messages`、`/api/agent/ask`、检索耗时、引用数、空检索率。
- Dependencies：Qdrant、Neo4j、LLM provider、Embedding provider、Redis、MySQL。
- Knowledge ingestion：上传量、入库阶段、失败率、耗时、当前失败文档数。

## 7. 必须配置的告警规则

至少实现下面 6 类告警。阈值可以结合本地数据微调，但必须能稳定触发演示。

### 7.1 Java -> AI service timeout

- `alertname`: `PortfolioAiServiceTimeout`
- `exception_type`: `AIServiceTimeout`
- `service`: `ai-agent-portfolio`
- `endpoint`: `/api/chat/messages`
- `severity`: `P1` 或 `P2`
- 触发建议：5 分钟窗口 AI service 调用错误率 >= 2%，或 p95 >= 3000ms，或 affected requests >= 10。

### 7.2 LLM provider degraded

- `alertname`: `PortfolioLlmProviderError`
- `exception_type`: `LLMProviderError`
- `service`: `portfolio-ai-service`
- `endpoint`: `llm_chat_completion`
- `severity`: `P1` 或 `P2`
- 触发建议：LLM 超时、429、5xx 或连续重试失败率 >= 2%。

### 7.3 RAG retrieval empty

- `alertname`: `PortfolioRagRetrievalEmptySpike`
- `exception_type`: `RAGRetrievalEmpty`
- `service`: `portfolio-ai-service`
- `endpoint`: `retrieve_knowledge`
- `severity`: `P2`
- 触发建议：5 分钟窗口 empty retrieval rate >= 10%，且 affected requests >= 10。

### 7.4 Qdrant unavailable

- `alertname`: `PortfolioQdrantUnavailable`
- `exception_type`: `QdrantUnavailable`
- `service`: `portfolio-ai-service`
- `endpoint`: `qdrant_search`
- `severity`: `P1`
- 触发建议：Qdrant query error rate >= 2%，或连续连接失败，或 p95 >= 1000ms。

### 7.5 GraphRAG fallback failure

- `alertname`: `PortfolioGraphRagFallbackFailure`
- `exception_type`: `GraphRagFallbackFailure`
- `service`: `portfolio-ai-service`
- `endpoint`: `graphrag_fallback`
- `severity`: `P2`
- 触发建议：Neo4j query error rate >= 2%，GraphRAG fallback failed count >= 3，或 graph context hit rate 明显下降。

### 7.6 Knowledge ingestion failure

- `alertname`: `PortfolioKnowledgeIngestionFailure`
- `exception_type`: `KnowledgeIngestionFailure`
- `service`: `ai-agent-portfolio`
- `endpoint`: `knowledge_ingestion`
- `severity`: `P2`
- 触发建议：入库阶段 parse/chunk/embedding/qdrant_upsert/neo4j_write/mysql_status_write 任一失败数 >= 1。

可选但建议增加：

- `PortfolioEmbeddingProviderError`
- `PortfolioRedisDependencyDegraded`
- `PortfolioDatabaseDegraded`

## 8. 本地可验证的异常触发方式

请给 `ai-agent-portfolio` 增加安全的本地演示触发方式，默认关闭：

```env
PORTFOLIO_DEMO_FAULTS_ENABLED=false
```

可以通过本地 profile、测试脚本或仅 demo 环境启用的 fault injection 来触发：

- AI service sleep 超过 Java read timeout，验证 `PortfolioAiServiceTimeout`。
- 临时指向错误 Qdrant URL，验证 `PortfolioQdrantUnavailable`。
- 提交明确无法命中知识库的问题，验证 `PortfolioRagRetrievalEmptySpike`。
- 临时指向错误 Neo4j URL，验证 `PortfolioGraphRagFallbackFailure`。
- 上传会触发 embedding 或 upsert 失败的本地测试文档，验证 `PortfolioKnowledgeIngestionFailure`。

不要为了演示新增对外 SSE endpoint。

## 9. 端到端验收

先启动 `ai-incident-copilot`：

```bash
cd ../ai-incident-copilot
scripts/start-docker.sh
curl http://localhost:8080/api/health
```

再启动 `ai-agent-portfolio`、Prometheus、Grafana：

```bash
cd ../ai-agent-portfolio
make up
```

验收：

1. 访问 `POST /api/chat/messages`，确认 Java 和 Python metrics 都有数据。
2. 访问知识库上传/重试链路，确认 ingestion metrics 有数据。
3. 在 Grafana dashboard 看到真实指标变化。
4. 触发至少两个真实告警：建议 `PortfolioAiServiceTimeout` 和 `PortfolioRagRetrievalEmptySpike`。
5. Grafana webhook 调用：

   ```text
   http://host.docker.internal:8080/api/alerts/grafana
   ```

6. 在 Incident Copilot 查询新建 Incident：

   ```bash
   curl "http://localhost:8080/api/incidents?serviceName=ai-agent-portfolio"
   ```

7. 查询入站告警：

   ```bash
   curl "http://localhost:8080/api/incidents/{incidentId}/alerts"
   ```

8. 确认 `status` 是 `INCIDENT_CREATED` 或 `CORRELATED`，不是 `IGNORED`。
9. 确认 workflow 已启动，且 runbook 命中对应方向。

如果 Grafana alert provisioning 受版本限制，可以保留真实 Grafana 配置，同时提供一个 `scripts/send-grafana-alert-smoke` 脚本，向 `/api/alerts/grafana` 发送与 Grafana webhook 同构的 JSON，用于验证 Incident Copilot 契约。但最终交付必须保留真实 Prometheus/Grafana 方案。

## 10. 文档和交付物

请在 `ai-agent-portfolio` 中补齐：

- README 或 `docs/observability.md`：如何启动 Prometheus/Grafana，如何配置 Incident Copilot webhook。
- `docs/incident-copilot-integration.md`：告警字段映射、实际故障面、runbook 对齐表、验收步骤。
- `.env.example` / `.env.docker.example`：新增 Incident Copilot、Prometheus/Grafana、demo fault 变量。
- `ops/monitoring`：Prometheus/Grafana 配置。
- smoke test 或本地验证脚本。
- 必要的单元测试或集成测试。

## 11. 最终回复要求

完成后请说明：

1. 当前分支确认结果。
2. 读到的真实项目事实，特别说明没有实现对外 SSE，所以没有配置 SSE 告警。
3. 改了哪些文件。
4. 新增了哪些 metrics。
5. 配置了哪些 Grafana/Prometheus 告警。
6. webhook 推送到 Incident Copilot 的 URL 和 payload 字段映射。
7. 哪些实际告警命中哪些 runbooks。
8. 运行和验收命令。
9. 如果发现当前 Incident Copilot runbook 仍有缺口，只列建议，不要跨仓库擅自修改。

