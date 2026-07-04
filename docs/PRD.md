# PRD：AI Incident Copilot 智能故障协同处理系统

## 1. 产品定位

AI Incident Copilot 是一个面向研发、SRE 和面试展示场景的故障协同处理系统。它将告警、诊断、Runbook 检索、处置建议、人工确认、处理记录和复盘报告串成一个可审计的 Agent Workflow。

系统的核心价值不是替代人处理生产故障，而是让 AI 在只读证据收集、诊断总结和流程标准化上提高效率；对于中高风险动作，系统只生成建议和审批卡片，由人做最终判断。

## 2. 用户与场景

### 2.1 目标用户

- 后端开发工程师：需要快速定位服务异常原因。
- SRE / 运维工程师：需要标准化故障处理、审计和复盘。
- 技术面试官：关注候选人是否理解 Agent Workflow、MCP、工程落地和安全边界。

### 2.2 核心场景

场景一：支付回调超时。

- `payment-service /api/payment/callback` 5 分钟内 500 错误率升高。
- p95 延迟从 120ms 升到 3200ms。
- 日志出现 `TimeoutError: order-service update payment status timeout`。
- Grafana / Alertmanager 将被关注告警推送给系统，系统创建或关联 Incident，调用 MCP 诊断工具、查询指标快照、检索 Runbook、生成处置方案，人工记录处置结果后观察恢复并生成复盘。

场景二：订单创建空指针。

- `order-service /api/orders` 创建订单时报 `NullPointerException`。
- 日志出现 `NullPointerException: userProfile is null when creating order`。
- 系统调用 `search_logs`、`search_code`、`search_tickets`、`generate_report`，检索 `order-create-npe` Runbook，生成修复建议和后续任务。

## 3. 产品目标

- 缩短故障排查时间。
- 减少证据遗漏。
- 将故障处理过程标准化。
- 将 AI 输出变成可审核、可追踪、可复盘的结构化流程。
- 展示 Agent Workflow、MCP 工具调用、RAG / Runbook 检索、Human-in-the-loop 和审计日志能力。

## 4. 功能范围

### 4.1 MVP 必须做

- Incident 创建、查询、关闭。
- Alert webhook 入站、幂等、阈值判断和 Incident 关联。
- 固定 Workflow 自动执行。
- 调用 `diagnosis-service` MCP tools。
- 查询 Incident 指标快照。
- 检索本地 Markdown Runbook。
- 生成诊断摘要和候选处置方案。
- 对处置方案做风险分级。
- 中高风险方案生成审批卡片。
- 记录人工决策。
- 记录处置结果。
- 观察指标恢复。
- 生成复盘报告。
- 展示 Workflow 时间线。
- 记录工具调用日志。
- Docker Compose 启动设计。

### 4.2 明确不做

- 不做真实服务器重启、回滚、扩缩容、生产 SQL、生产配置修改。
- 不做复杂拖拽式 Workflow 画布。
- 不做复杂权限系统。
- 不做华丽但无用的营销式前端。
- 不做完整企业级告警平台。

## 5. 关键对象

### 5.1 Incident

表示一次故障协同处理流程，包含服务名、接口、等级、状态、异常类型、trace id、摘要和关闭时间。

状态建议：

- `OPEN`
- `WORKFLOW_RUNNING`
- `WAITING_APPROVAL`
- `MITIGATING`
- `RECOVERING`
- `RESOLVED`
- `CLOSED`
- `FAILED`

### 5.2 Workflow Instance

表示一次 `IncidentHandlingWorkflow` 执行实例，记录当前节点、整体状态、开始和结束时间。

### 5.3 Workflow Node Execution

记录每个节点的输入、输出、状态、耗时和错误信息，用于页面时间线、排障和审计。

### 5.4 Tool Call Log

记录 MCP 工具调用和 LLM 调用的请求、响应、是否成功、耗时和错误信息。

### 5.5 Action Proposal

AI 生成的候选处置方案。低风险动作可直接记录建议，中高风险动作必须等待人工确认。

### 5.6 Human Approval

人工对处置方案的决策，包括批准、驳回、要求补充证据、升级 SRE、记录处置结果、标记无效、关闭 Incident。

## 6. Workflow 节点

1. `AlertReceiverNode`：接收告警并创建 Incident。
2. `MetricsCollectorNode`：查询 Incident 指标快照。
3. `DiagnosisMcpNode`：调用 `diagnosis-service` MCP tools。
4. `RunbookRetrieverNode`：检索本地 Runbook。
5. `SeverityClassifierNode`：判断 P0 / P1 / P2 / P3。
6. `ActionPlanGeneratorNode`：生成候选处置方案。
7. `RiskReviewNode`：判断风险等级和是否需要人工确认。
8. `HumanApprovalNode`：等待或记录人工确认。
9. `ActionRecordNode`：记录处理动作，不执行危险操作。
10. `ObserveRecoveryNode`：观察 Incident 指标恢复。
11. `PostmortemNode`：生成结构化复盘报告。

## 7. 人工确认卡片

字段：

- action_id
- incident_id
- 建议动作
- 风险等级
- 建议原因
- 证据来源
- 影响范围
- 前置检查
- 执行人建议
- 可选操作

可选操作：

- 批准方案
- 驳回方案
- 要求补充证据
- 升级给 SRE
- 记录处置结果
- 标记方案无效
- 关闭 Incident

## 8. 成功指标

- 一次 demo 故障可以在 3 分钟内完成从创建 Incident 到生成复盘。
- 每个 Workflow 节点都有可查看的输入、输出和状态。
- 每次 MCP 调用都有审计记录。
- 中高风险处置方案不会自动执行。
- 面试讲解可以清楚区分普通 RAG chatbot 与 Agent Workflow 系统。
