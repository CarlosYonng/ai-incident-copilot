# AI Incident Copilot 智能故障协同处理系统

AI Incident Copilot 是一个面试展示用的 AI Agent Workflow 项目。它不是“AI 自动修复线上故障”，而是“AI 故障处理 Copilot”：在模拟业务服务出现异常后，自动创建 Incident，收集日志、指标、历史工单、代码线索和 Runbook，调用 `diagnosis-service` MCP 诊断工具生成诊断报告，再由上层 Agent Workflow 生成处置方案、风险分级、人工确认卡片、执行记录和复盘报告。

## 项目边界

系统自动执行：

- 创建 Incident。
- 查询 mock metrics。
- 调用 `diagnosis-service` MCP tools。
- 检索 Runbook。
- 生成诊断摘要、候选处置方案、风险说明和复盘报告。
- 记录 Workflow 节点、工具调用、人工决策和处理记录。

系统不会自动执行：

- 真实重启服务器。
- 真实回滚发布。
- 真实扩容缩容。
- 真实执行 SQL。
- 真实修改生产配置。

中高风险动作必须进入 Human-in-the-loop 人工确认流程。

## 目录结构

```text
ai-incident-copilot/
  README.md
  docs/
    PRD.md
    TECH_DESIGN.md
    API.md
    FRONTEND_DESIGN.md
    DEVELOPMENT_PLAN.md
    DEMO_SCRIPT.md
    INTERVIEW_GUIDE.md
  database/
    schema.sql
  prompts/
    action_plan_prompt.md
    postmortem_prompt.md
    severity_prompt.md
  runbooks/
    payment-callback-timeout.md
    order-create-npe.md
    dependency-unavailable.md
    database-slow-query.md
    redis-cache-failure.md
  scripts/
    demo.http
  docker-compose.design.yml
```

## 推荐技术栈

- Backend: Java 21, Spring Boot 3, Spring Web, Spring Validation, Spring Data JPA 或 MyBatis Plus。
- Database: MySQL 8。
- Frontend: React + TypeScript + Vite。
- Agent: 固定节点式 Workflow 编排，MVP 同步执行。
- MCP: 调用已有 `diagnosis-service` 暴露的 MCP tools。
- LLM: 生成诊断摘要、处置方案、风险解释和复盘报告。
- Deploy: Docker Compose 一键启动。

## MVP 演示主线

1. 启动 `diagnosis-service`、`demo-service`、Incident Copilot 后端、前端和 MySQL。
2. 触发 `payment-timeout` 模拟故障。
3. 创建 Incident 并启动 `IncidentHandlingWorkflow`。
4. 展示 mock metrics、MCP 调用、Runbook 检索和 Workflow 时间线。
5. 生成 3 个候选处置方案。
6. 对中风险方案点击“标记线下已执行”。
7. mock metrics 从 `degraded` 变为 `recovering`。
8. 生成复盘报告并关闭 Incident。

## 核心文档

- 产品说明: `docs/PRD.md`
- 技术设计: `docs/TECH_DESIGN.md`
- 数据库 DDL: `database/schema.sql`
- 接口文档: `docs/API.md`
- 7 天开发步骤: `docs/DEVELOPMENT_PLAN.md`
- 演示脚本: `docs/DEMO_SCRIPT.md`
- 面试讲解稿: `docs/INTERVIEW_GUIDE.md`

