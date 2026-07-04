# 项目职责与优化路线

## 一句话定位

AI Incident Copilot 是“被关注告警”的 Incident 响应编排服务。它不接管全量异常信息，也不替代 diagnosis-service 做诊断索引；它只处理 Grafana、Alertmanager、portfolio 监控等来源推送过来的、已经需要关注和闭环处理的告警。

## 服务边界

```text
portfolio / 业务服务
  产生业务请求、错误、指标和告警条件

diagnosis-service
  接收和沉淀全量异常证据
  暴露 MCP tools: search_logs / search_code / search_tickets / generate_report
  回答“发生了什么，证据是什么，可能原因是什么”

ai-incident-copilot
  接收被关注告警
  创建或关联 Incident
  调用 diagnosis-service MCP 获取诊断证据
  编排处置方案、人工记录、恢复观察、复盘和关闭
  回答“这条告警如何被处理、审计和闭环”
```

## 合理业务链路

```text
portfolio 被监控到异常
 -> Grafana / Alertmanager 触发告警
 -> POST /api/alerts/grafana 或 /api/alerts/alertmanager
 -> AlertIngestService 做幂等、阈值判断、Incident 关联
 -> Workflow 调用 diagnosis-service MCP 获取证据和诊断报告
 -> Runbook + 风险规则生成处置方案
 -> 人工采纳低风险方案或记录中高风险线下处理结果
 -> 指标进入恢复观察
 -> 生成复盘
 -> 关闭 Incident
```

## 必要 Agent / Workflow 能力

- MCP 工具调用：通过 diagnosis-service 查询日志、代码、历史工单和诊断报告。
- 多源证据聚合：把告警指标、MCP 诊断、Runbook 命中、人工处理记录放进同一个 Incident 上下文。
- 可解释 Workflow：每个节点有输入、输出、耗时、状态和关键结果。
- 风险门禁：低风险可采纳记录，中高风险只记录外部处理结果，不自动执行生产动作。
- Human-in-the-loop：系统生成方案，人类选择或记录处理结果。
- 审计闭环：保留 alert_event、workflow_node_execution、tool_call_log、action_record、postmortem_report。
- 复盘生成：基于完整 Incident 上下文生成总结、根因、影响和改进项。

## 不做什么

- 不做全量异常采集和索引，这属于 diagnosis-service。
- 不做完整工单系统、权限系统和飞书审批平台。
- 不直接执行重启、回滚、SQL、扩缩容或生产配置修改。
- 不把所有功能塞进 MCP；MCP 是诊断工具层，Incident Copilot 是响应编排层。

## 当前优化完成项

- 增加 Grafana / Alertmanager webhook 入站适配。
- 统一所有告警入口到 `AlertIngestService`。
- 支持低风险方案“采纳并记录”，不再只允许中高风险线下执行进入闭环。
- 前端文案收敛为“被关注告警响应编排台”。
- 页面展示告警来源、Incident 生命周期、诊断工具审计、处理记录和复盘归档。

## 后续优化步骤

1. 把 `DiagnosisMcpNode` 的输出进一步结构化，明确 diagnosis-service 返回的证据摘要、根因假设和置信度。
2. 将 `ActionPlanGeneratorNode` 从固定模板升级为“诊断结果 + Runbook + 风险规则”的结构化方案生成节点。
3. 给复盘报告增加 MCP 证据引用、处理记录引用和恢复指标引用。
4. 增加真实 Grafana / Alertmanager 示例 payload 和本地 curl 验收脚本。
5. 可选增加 Feishu 出站通知，但只作为通知工具，不扩展为完整任务平台。
