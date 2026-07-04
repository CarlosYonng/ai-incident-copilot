# 7 天开发计划

## 第 1 天：项目骨架与基础数据

目标：后端可启动，数据库可初始化，Incident CRUD 可用。

任务：

- 创建 Spring Boot 项目。
- 创建 React + TypeScript + Vite 前端项目。
- 配置 MySQL 连接、Flyway 或初始化 SQL。
- 建立 `incident`、`workflow_instance`、`workflow_node_execution` 等核心表。
- 实现 Incident 创建、列表、详情、关闭接口。
- 输出 README 初版和本地启动说明。

验收：

- `POST /api/incidents` 能创建 Incident。
- `GET /api/incidents` 能查询列表。
- MySQL 中能看到数据。

## 第 2 天：Workflow 引擎

目标：固定顺序 Workflow 能运行并记录节点执行。

任务：

- 定义 `WorkflowNode`、`WorkflowContext`、`NodeResult`。
- 实现 `WorkflowEngine`。
- 实现 `AlertReceiverNode` 和 `MetricsCollectorNode`。
- 每个节点执行写入 `workflow_node_execution`。
- 实现 `POST /api/incidents/{id}/start-workflow`。
- 实现 `GET /api/workflows/{instanceId}` 和节点列表接口。

验收：

- 启动 Workflow 后能看到节点时间线。
- 节点输入、输出、状态和耗时可查询。

## 第 3 天：DiagnosisMcpClient

目标：上层系统能调用已有 `diagnosis-service` MCP tools。

任务：

- 配置 `diagnosis-service` 地址。
- 实现 `DiagnosisMcpClient`。
- 封装 `search_logs`、`search_code`、`search_tickets`、`generate_report`、`get_report`。
- 实现 `DiagnosisMcpNode`。
- 写入 `tool_call_log`。
- 处理 MCP 服务不可用、调用超时、响应格式异常。

验收：

- Workflow 中能看到 MCP 工具调用日志。
- MCP 失败时节点失败，页面可看到错误原因。

## 第 4 天：Runbook、Metrics 和 Severity

目标：Workflow 能结合指标和 Runbook 给出诊断上下文。

任务：

- 编写 5 份 Markdown Runbook。
- 实现 `RunbookRetriever`。
- 实现 Incident 指标快照状态流转。
- 实现 `SeverityClassifierNode`。
- 编写 `severity_prompt.md`。
- 可选：用 LLM 生成诊断摘要；时间紧则用模板生成。

验收：

- payment-timeout 能命中 `payment-callback-timeout.md`。
- order-npe 能命中 `order-create-npe.md`。
- Incident severity 能更新为 P1 / P2 等级。

## 第 5 天：处置方案与人工确认

目标：生成候选处置方案并完成 Human-in-the-loop。

任务：

- 编写 `action_plan_prompt.md`。
- 实现 `ActionPlanGeneratorNode`。
- 实现 `RiskReviewNode`。
- 保存 `action_proposal`。
- 实现审批接口：approve、reject、record-result、escalate。
- 实现 `ActionRecordNode`。
- 记录处置结果后 Incident 指标快照进入 `recovering`。

验收：

- payment-timeout 生成低、中、高 3 类建议。
- 中高风险方案必须人工操作。
- 记录处置结果后产生审批记录和处理记录。

## 第 6 天：前端控制台

目标：演示闭环可视化。

任务：

- 实现 Incident 列表页。
- 实现 Incident 详情页。
- 实现 Workflow 时间线页。
- 实现处置方案审核页。
- 实现前端告警入站触发入口。
- 对节点输入输出 JSON 做折叠展示。

验收：

- 面试演示不需要 Postman 即可完成主流程。
- Workflow、MCP 调用、人工审批都能在页面解释清楚。

## 第 7 天：复盘、Docker Compose 和面试材料

目标：项目可一键演示，可讲清楚架构。

任务：

- 编写 `postmortem_prompt.md`。
- 实现 `PostmortemNode` 和复盘接口。
- 完成 Docker Compose。
- 写演示脚本和面试讲解稿。
- 补充 README 截图和启动顺序。
- 做一次端到端演练。

验收：

- 能完成 payment-timeout 全流程演示。
- 能生成结构化复盘报告。
- README、技术设计、API、DDL、演示脚本齐全。
