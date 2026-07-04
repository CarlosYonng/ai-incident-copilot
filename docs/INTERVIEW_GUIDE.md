# 面试讲解稿

## 1. 项目一句话介绍

AI Incident Copilot 是一个面向被关注告警的智能故障协同处理系统。portfolio 被监控系统发现异常后，Grafana / Alertmanager 通过 webhook 把告警推给本项目；本项目再通过 Agent Workflow 收集证据、调用 MCP 诊断服务、检索 Runbook、生成处置建议，并把中高风险动作交给人工确认，最后生成审计记录和复盘报告。

## 2. 为什么把 diagnosis-service 设计成独立 MCP 工具服务

因为诊断能力和故障协同流程是两个不同边界。

`diagnosis-service` 负责日志、代码、历史工单和诊断报告这些“只读证据能力”。它通过 MCP tools 暴露标准化工具接口，便于不同上层 Agent 复用。

Incident Copilot 不直接耦合底层日志和代码检索实现，而是通过 MCP 调用工具，这能体现工具协议化、职责解耦和可替换性。

## 3. 为什么 Incident Copilot 是上层 Agent Workflow 系统

普通 chatbot 通常是一次输入、一次输出。Incident Copilot 是多节点、有状态、有输入输出、有失败处理、有人工确认和审计记录的业务流程。

它不仅回答“可能是什么原因”，还管理一次故障从告警、诊断、决策、记录、观察到复盘的完整闭环。

## 4. 为什么不让 AI 自动执行重启、回滚、改配置

生产操作有副作用，错误执行可能扩大故障。AI 的价值在于加速证据收集、辅助分析和标准化流程，而不是绕过人的责任边界。

所以系统明确区分：

- 只读动作：可自动化。
- 低风险动作：可生成记录或建议。
- 中高风险动作：必须人工确认。
- 高风险动作：系统只生成建议和审批卡片，不自动执行。

## 5. 人工确认到底确认什么

人工确认的不是一句“AI 建议”，而是一张处置卡片。卡片包含建议动作、风险等级、建议原因、证据来源、影响范围、前置检查和可选操作。

确认人要判断证据是否充分、影响是否可接受、前置检查是否满足，以及是否需要升级给 SRE。

## 6. Workflow 节点状态如何落库

每个节点执行都会写入 `workflow_node_execution`：

- 节点名称。
- 节点类型。
- 状态。
- 输入 JSON。
- 输出 JSON。
- 错误信息。
- 开始和结束时间。
- 耗时。

这样前端可以展示时间线，失败节点可以重试，面试时也能清楚说明系统可观测性。

## 7. 工具调用日志如何审计

所有 MCP 和 LLM 调用都写入 `tool_call_log`，包括工具名、请求、响应、成功状态、错误信息和耗时。

这解决了两个问题：

- 事后能追踪 AI 根据哪些证据生成建议。
- 工具服务失败时能定位是哪个工具、哪次调用、什么错误。

## 8. Runbook 的作用

Runbook 是标准化处理经验。系统检索 Runbook 后，把它和诊断报告、指标一起作为 LLM 输入，避免 AI 凭空生成方案。

Runbook 还显式区分低、中、高风险动作，帮助系统做风险分级和人工确认。

## 9. diagnosis-service 调用失败怎么办

`DiagnosisMcpNode` 标记为 `FAILED`，错误信息写入节点记录和工具调用日志。Workflow 整体进入 `FAILED`，但已完成节点保留。前端展示失败原因，并允许重试失败节点。

这样不会因为外部工具不可用而丢失已有上下文。

## 10. LLM 生成的处置方案不可靠怎么办

系统做了几层约束：

- Prompt 要求输出结构化 JSON。
- 后端做 JSON Schema 校验。
- 风险等级由规则二次校验。
- 中高风险动作必须人工确认。
- 所有建议都有证据来源和 Runbook 支撑。
- 不允许 LLM 直接执行危险操作。

## 11. 后续如何接入真实 Prometheus / Alertmanager

当前 Incident 指标快照由告警 payload 和演示状态机驱动。生产化时可以替换为 `MetricsProvider` 接口实现，新增 `PrometheusMetricsProvider` 后，`MetricsCollectorNode` 不需要改变业务流程，只需要切换实现。

Grafana / Alertmanager 可以作为告警入站来源，把 webhook payload 转换成统一的 `AlertIngestRequest`，再由系统判断创建或关联 Incident。

## 12. 和普通 RAG chatbot 的区别

普通 RAG chatbot 主要是检索知识后回答问题。这个项目的重点是业务流程：

- 有 Incident 状态。
- 有 Workflow 节点。
- 有 MCP 工具调用。
- 有 Runbook 检索。
- 有风险分级。
- 有人工确认。
- 有审计日志。
- 有复盘报告。

它展示的是 AI Agent 在真实工程流程中的落地，而不是单纯问答。

## 13. 后续优化方向

- 接入真实 Prometheus 和 Alertmanager。
- 引入异步 Workflow 调度和节点重试策略。
- 增加 Slack / 飞书审批通知。
- Runbook 改为向量检索。
- 增加 RBAC 权限和操作审计导出。
- 支持更多故障类型和服务拓扑。
- 对 LLM 输出引入更严格的 JSON Schema 和安全策略。
