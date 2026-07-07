# 项目描述与职责边界

## 一句话定位

AI Incident Copilot 是一个面向“被关注告警”的 Incident 响应编排系统。它不采集全量异常，也不直接执行生产变更；它负责把已经触发关注的告警转化为可诊断、可决策、可记录、可复盘的故障处理闭环。

## 为什么需要这个项目

真实线上故障处理通常不是缺少某一个工具，而是缺少一条清晰、可审计的协同链路：

- 告警平台知道指标异常，但不知道完整处置过程。
- 日志、代码、历史工单分散在不同系统里，诊断证据容易遗漏。
- Runbook 有知识，但很难自动关联到具体 Incident。
- AI 可以生成建议，但如果没有风险门禁和审计，很难用于生产流程。
- 复盘报告常常依赖人工回忆，缺少完整时间线和证据引用。

AI Incident Copilot 解决的是“告警之后如何协同处理”的问题：接收告警、组织证据、生成建议、记录人工决策、观察恢复并沉淀复盘。

## 与相邻系统的关系

```text
ai-agent-portfolio / business services
  产生真实业务请求、错误、指标和告警条件

Prometheus / Grafana / Alertmanager
  采集指标并判断告警是否需要关注

diagnosis-service
  沉淀日志、代码、历史工单和诊断报告
  暴露 MCP tools:
    search_logs
    search_code
    search_tickets
    generate_report

ai-incident-copilot
  接收被关注告警
  创建或关联 Incident
  调用 diagnosis-service MCP 获取证据
  检索 Runbook
  生成处置方案和风险说明
  记录人工决策和线下处理结果
  观察恢复指标并生成复盘
```

## 标准业务链路

```text
业务系统出现异常
  -> Prometheus 采集指标
  -> Grafana / Alertmanager 触发告警
  -> POST /api/alerts/grafana 或 /api/alerts/alertmanager
  -> AlertIngestService 保存 alert_event
  -> 阈值判断、幂等处理、Incident 关联
  -> IncidentHandlingWorkflow 启动
  -> MetricsCollectorNode 记录指标快照
  -> DiagnosisMcpNode 调用 diagnosis-service
  -> RunbookRetrieverNode 检索处置知识
  -> ActionPlanGeneratorNode 生成候选方案
  -> RiskReviewNode 判断风险等级
  -> HumanApprovalNode 等待或记录人工处理
  -> 恢复观察、复盘生成、关闭 Incident
```

## 系统负责什么

- **告警入站**：接收统一告警、Grafana webhook、Alertmanager webhook。
- **事件治理**：保存原始告警、幂等处理、阈值判断、关联已有 Incident。
- **Workflow 编排**：用固定节点串联告警接收、指标、诊断、Runbook、处置和复盘。
- **诊断证据聚合**：通过 MCP 工具查询日志、代码、历史工单和诊断报告。
- **Runbook 命中**：按服务、接口、异常类型和诊断摘要检索本地 Markdown Runbook。
- **处置建议生成**：基于 Incident 上下文生成低、中、高风险候选方案。
- **风险门禁**：中高风险动作只生成建议，不自动执行。
- **人工协同**：记录批准、驳回、升级、线下执行结果等人工决策。
- **恢复观察**：记录 degraded、recovering、recovered 等指标快照。
- **审计与复盘**：保留 Workflow 节点、工具调用、处理记录和结构化复盘报告。

## 系统不负责什么

- 不做全量异常采集和索引，这属于监控平台和 `diagnosis-service`。
- 不替代 Grafana / Alertmanager 判断是否触发告警。
- 不替代完整工单系统、权限系统或企业审批平台。
- 不直接重启服务、回滚版本、执行 SQL、扩缩容或修改生产配置。
- 不把所有能力都塞进 MCP；MCP 是诊断工具层，Incident Copilot 是响应编排层。

## 安全边界

项目默认遵守三个边界：

1. **只读诊断优先**：系统自动调用的外部能力主要是查询类工具。
2. **中高风险人工确认**：涉及回滚、重启、配置修改、SQL、扩缩容等动作只生成建议。
3. **审计优先于自动化**：MVP 阶段更重视把谁在什么时候基于什么证据做了什么决策记录清楚。

## 当前已完成能力

- Grafana / Alertmanager webhook 入站适配。
- 统一 `AlertIngestService`，支持原始告警落库、幂等、阈值判断和 Incident 关联。
- Incident 创建、查询、关闭和指标快照。
- 固定顺序 `IncidentHandlingWorkflow`。
- MCP JSON-RPC client 和 `tool_call_log` 审计。
- 本地 Markdown Runbook 检索。
- Severity 分类、候选处置方案、风险审核。
- 人工批准、驳回、升级和记录线下处理结果。
- 恢复观察和结构化复盘报告。
- React 控制台完整演示闭环。

## 与 ai-agent-portfolio 的真实链路方向

下一阶段重点是让 `ai-agent-portfolio` 的真实 Prometheus / Grafana 告警进入本系统。告警类型应以 `ai-agent-portfolio` 的实际业务链路为准，而不是强迫它适配本项目已有示例。

推荐对齐的真实故障面：

- Java 调 Python AI service 超时或 5xx。
- LLM provider 超时、限流或连续 5xx。
- RAG 检索无可引用证据或引用数异常下降。
- Qdrant 查询失败、超时或错误率升高。
- Neo4j GraphRAG fallback 查询失败或命中异常下降。
- 知识库入库失败，包括切片、embedding、Qdrant upsert、Neo4j 写入和 MySQL 状态回写。
- Redis 缓存、限流、去重、JWT blacklist 或分布式锁异常。
- MySQL 慢查询、连接池耗尽或状态写入失败。

本项目已有 portfolio 方向 Runbook 会优先覆盖 RAG、Qdrant、AI service、知识库入库和 GraphRAG。如果真实告警暴露出新的稳定故障类型，应补充本项目 Runbook，而不是在 `ai-agent-portfolio` 中伪造不真实的告警语义。

## 后续优化路线

1. 与 `ai-agent-portfolio` 打通 Prometheus / Grafana 真实告警入站。
2. 优化 portfolio 方向 Runbook，移除不再适用或缺少真实业务基础的场景。
3. 将 `DiagnosisMcpNode` 输出进一步结构化，补充根因假设、置信度和证据引用。
4. 将 `ActionPlanGeneratorNode` 升级为“诊断结果 + Runbook + 风险规则”的结构化方案生成节点。
5. 给复盘报告增加 MCP 证据、处理记录和恢复指标引用。
6. 增强重复操作幂等性、错误码枚举、分页响应和测试覆盖。
