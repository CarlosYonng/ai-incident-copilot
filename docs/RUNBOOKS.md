# Runbook 定义与维护说明

Runbook 是面向 Incident 的标准处置知识，不是把每个异常提前写死成流程。

本项目中的 Runbook 用来回答：

- 这个告警属于哪类真实故障场景。
- 应该先看哪些证据。
- 哪些动作是低风险、中风险、高风险。
- 人工记录处置结果前需要确认什么。
- 恢复观察和复盘应该关注哪些指标。

Workflow 的职责是把 `alert_event`、`diagnosis-service` MCP 诊断证据、指标快照和 Runbook 命中结果放到同一个 Incident 上下文里，再生成处置方案和复盘。Runbook 本身不直接执行生产操作。

## 是否必须全部事先定义

不需要覆盖所有异常，也不应该按每个 exception 写一份。

更合理的方式是按“可报警、可处置、可复盘”的故障类型定义，例如：

- RAG 检索无结果或答案质量下降。
- Qdrant 向量库不可用。
- AI 服务 / LLM 调用超时。
- SSE 流式响应中断。
- 知识库导入或 embedding 失败。
- GraphRAG / Neo4j fallback 异常。

这些是 ai-agent-portfolio 真实业务链路中的稳定故障类型。具体某次异常的细节由 `diagnosis-service` MCP 工具补充，Runbook 只提供安全处置框架。

## 推荐结构

每份 Runbook 应包含：

- `适用场景`：哪些告警、接口、异常类型会命中它。
- `常见症状`：页面、接口、日志、指标表现。
- `排查步骤`：先看哪些只读证据。
- `常见原因`：用于辅助诊断总结。
- `低风险动作`：只读、通知、补充日志、创建后续任务。
- `中风险动作`：降级、限流、切换 fallback、暂停批次等，需要人工确认。
- `高风险动作`：回滚、改配置、重建索引、删除数据等，只能建议，不能自动执行。
- `前置检查`：执行中高风险动作前必须确认的条件。
- `观察指标`：处理后判断恢复的指标。
- `复盘要求`：事后必须补齐的改进项。

## 与 ai-agent-portfolio 的关系

ai-agent-portfolio 是 RAG / Agent 应用，被监控后把被关注告警推给 Incident Copilot。

Incident Copilot 不保存 portfolio 的全量异常，也不替代 diagnosis-service。它只在告警触发后：

1. 接收 Grafana / Alertmanager webhook。
2. 创建或关联 Incident。
3. 调用 diagnosis-service MCP 查询日志、代码、历史工单和诊断报告。
4. 检索 portfolio 相关 Runbook。
5. 生成低/中/高风险处置方案。
6. 记录人工采用的唯一方案。
7. 观察恢复指标并生成复盘。

## 当前 portfolio Runbook

- `portfolio-rag-retrieval-empty.md`：RAG 检索无结果或答案质量下降。
- `portfolio-qdrant-unavailable.md`：Qdrant 向量库不可用或检索超时。
- `portfolio-ai-service-timeout.md`：Python AI service / LLM 调用超时。
- `portfolio-sse-stream-interrupted.md`：SSE 流式回答中断。
- `portfolio-knowledge-ingestion-failure.md`：知识库导入、切片或 embedding 失败。
- `portfolio-graphrag-fallback-failure.md`：Neo4j GraphRAG fallback 异常。
