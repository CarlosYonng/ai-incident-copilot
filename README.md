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
  backend/
    src/main/java/com/example/incidentcopilot/
    src/main/resources/db/migration/
    pom.xml
    Dockerfile
  frontend/
    src/
    package.json
    Dockerfile
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
  docker-compose.yml
  docker-compose.design.yml
```

## 当前开发进度

已完成 Day 1 到 Day 7 的可运行 MVP 闭环：

- Spring Boot 后端项目骨架。
- Flyway 初始化核心库表。
- Incident 创建、列表、详情、关闭接口。
- 创建 Incident 后写入 degraded mock metrics。
- 关闭 Incident 后写入 recovered mock metrics。
- 固定顺序 `IncidentHandlingWorkflow`。
- `AlertReceiverNode`、`MetricsCollectorNode`、`DiagnosisMcpNode`、`RunbookRetrieverNode`、`SeverityClassifierNode`、`ActionPlanGeneratorNode`、`RiskReviewNode`、`HumanApprovalNode`。
- Workflow 实例、节点输入输出、状态、耗时落库。
- Workflow 查询和节点时间线查询接口。
- Diagnosis MCP JSON-RPC Client，支持 `search_logs`、`search_code`、`search_tickets`、`generate_report`。
- MCP 调用写入 `tool_call_log`，服务不可用时使用模板证据兜底。
- 本地 Markdown Runbook 检索。
- Severity 分类和候选处置方案生成。
- 中高风险方案人工确认接口：批准、驳回、升级、标记线下已执行。
- 标记线下已执行后写入 `human_approval`、`action_record`，Incident 进入 `RECOVERING`，mock metrics 进入 `recovering`。
- 结构化复盘报告生成和查询。
- React + TypeScript + Vite 前端控制台完整演示闭环。

## 推荐技术栈

- Backend: Java 21, Spring Boot 3, Spring Web, Spring Validation, Spring JDBC。
- Database: MySQL 8。
- Frontend: React + TypeScript + Vite。
- Agent: 固定节点式 Workflow 编排，MVP 同步执行。
- MCP: 调用已有 `diagnosis-service` 暴露的 MCP tools。
- LLM: 生成诊断摘要、处置方案、风险解释和复盘报告。
- Deploy: Docker Compose 一键启动。

## 本地启动

### Docker Compose

```bash
docker compose up --build
```

启动后访问：

- 前端控制台: `http://localhost:3000`
- 后端 API: `http://localhost:8080/api`
- MySQL: `localhost:3306`

如果本机已启动相邻项目 `diagnosis-service`，后端会通过 `http://host.docker.internal:8200/mcp` 调用真实 MCP 工具；如果未启动，系统会记录失败审计并使用模板证据兜底，演示流程仍可继续。

### 本机开发

先启动 MySQL，并确保存在数据库和账号：

```text
database: incident_copilot
username: incident
password: incident
```

启动后端：

```bash
cd backend
mvn spring-boot:run
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

### MVP 验收接口

```bash
curl -X POST http://localhost:8080/api/incidents \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "payment-service 支付回调超时",
    "serviceName": "payment-service",
    "endpoint": "/api/payment/callback",
    "source": "DEMO",
    "traceId": "trace-payment-timeout-001",
    "exceptionType": "TimeoutError",
    "summary": "5 分钟内 500 错误率升高，p95 延迟升至 3200ms"
  }'
```

```bash
curl http://localhost:8080/api/incidents
curl -X POST http://localhost:8080/api/incidents/1/start-workflow
curl http://localhost:8080/api/workflows/1
curl http://localhost:8080/api/workflows/1/nodes
curl http://localhost:8080/api/workflows/1/tool-calls
curl http://localhost:8080/api/incidents/1/actions
curl -X POST http://localhost:8080/api/actions/1/mark-offline-executed \
  -H 'Content-Type: application/json' \
  -d '{"executor":"sre-demo","resultDetail":"已在线下执行，本系统记录审计结果"}'
curl -X POST http://localhost:8080/api/incidents/1/generate-postmortem
curl http://localhost:8080/api/incidents/1/postmortem
```

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
