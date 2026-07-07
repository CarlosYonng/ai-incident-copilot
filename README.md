# AI Incident Copilot 智能故障协同处理系统

AI Incident Copilot 是一个面向“被关注告警”的 Incident 响应编排服务。`diagnosis-service` 负责接收和沉淀全量异常证据，并通过 MCP tools 提供日志、代码、历史工单和诊断报告能力；本项目只处理 Grafana、Alertmanager、portfolio 监控等来源推送过来的告警事件：创建或关联 Incident，调用 `diagnosis-service` MCP 获取诊断证据，编排处置方案、处理记录、恢复观察和复盘报告，形成可审计的故障处理闭环。

更清晰的职责边界和优化路线见 `docs/PROJECT_SCOPE.md`。
第一次接手项目建议先读 `docs/BACKEND_CODE_WALKTHROUGH.md`，里面按告警入站、Incident、Workflow、MCP、处置方案、复盘和脚本解释了后端业务链路与数据流转。

## 项目边界

系统自动执行：

- 创建 Incident。
- 记录原始业务告警事件，并将高置信事件关联或升级为 Incident。
- 查询 Incident 指标快照，当前由告警 payload 和演示状态机驱动，后续可替换为 Prometheus / Grafana 数据源。
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
    BACKEND_CODE_WALKTHROUGH.md
    API.md
    FRONTEND_DESIGN.md
    DEVELOPMENT_PLAN.md
    DEMO_SCRIPT.md
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
    portfolio-rag-retrieval-empty.md
    portfolio-qdrant-unavailable.md
    portfolio-ai-service-timeout.md
    portfolio-sse-stream-interrupted.md
    portfolio-knowledge-ingestion-failure.md
    portfolio-graphrag-fallback-failure.md
  scripts/
    demo.http
    smoke-test.sh
  .env.example
  CHANGELOG.md
  LICENSE
  SECURITY.md
  docker-compose.yml
  docker-compose.design.yml
```

## 当前开发进度

已完成 Day 1 到 Day 7 的可运行 MVP 闭环：

- Spring Boot 后端项目骨架。
- Flyway 初始化核心库表。
- Incident 创建、列表、详情、关闭接口。
- Alert Ingest 入站接口，支持原始告警事件落库、幂等、阈值判断、Incident 关联和自动启动 Workflow。
- 创建 Incident 后写入告警携带的 degraded 指标快照。
- 关闭 Incident 后写入 recovered 指标快照。
- 固定顺序 `IncidentHandlingWorkflow`。
- `AlertReceiverNode`、`MetricsCollectorNode`、`DiagnosisMcpNode`、`RunbookRetrieverNode`、`SeverityClassifierNode`、`ActionPlanGeneratorNode`、`RiskReviewNode`、`HumanApprovalNode`。
- Workflow 实例、节点输入输出、状态、耗时落库。
- Workflow 查询和节点时间线查询接口。
- Diagnosis MCP JSON-RPC Client，支持 `search_logs`、`search_code`、`search_tickets`、`generate_report`。
- MCP 调用写入 `tool_call_log`，服务不可用时使用模板证据兜底。
- 本地 Markdown Runbook 检索。
- Severity 分类和候选处置方案生成。
- 中高风险方案人工确认接口：批准、驳回、升级、记录处理结果。
- 记录处理结果后写入 `human_approval`、`action_record`，Incident 进入 `RECOVERING`，指标快照进入 `recovering`。
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
scripts/start-docker.sh
```

启动后访问：

- 前端控制台: `http://localhost:3000`
- 后端 API: `http://localhost:8080/api`
- MySQL: 复用 `ai-agent-infra-stack` 的 `localhost:3306`

默认复用相邻项目 `ai-agent-infra-stack` 中的 MySQL。Docker 后端通过 `host.docker.internal:3306` 连接，宿主机后端通过 `localhost:3306` 连接。
本项目使用独立 database `incident_copilot` 和独立用户 `incident_copilot`，不复用其他项目的 `agent` 用户。

首次运行前初始化数据库和专用用户：

```bash
scripts/init-db.sh
```

如果本机已启动相邻项目 `diagnosis-service`，后端会通过 `http://host.docker.internal:8200/mcp` 调用真实 MCP 工具；如果未启动，系统会记录失败审计并使用模板证据兜底，演示流程仍可继续。

复制环境变量模板：

```bash
cp .env.example .env
```

真实 MCP 链路验收时，把 `.env` 中的 fallback 关闭：

```env
DIAGNOSIS_MCP_FALLBACK_ENABLED=false
```

停止 Docker 服务：

```bash
scripts/stop-docker.sh
```

### Smoke Test

启动后端后可以直接跑完整 API 闭环：

```bash
scripts/smoke-test.sh
```

脚本会自动完成：

- 健康检查。
- 注入支付回调超时业务告警事件。
- 启动 Workflow。
- 校验 Workflow 节点和 MCP 工具调用审计。
- 查找需要人工确认的处置方案。
- 记录处置结果。
- 校验 Incident 指标快照进入 `recovering`。
- 生成复盘。
- 关闭 Incident。

可通过环境变量切换 API 地址：

```bash
BASE_URL=http://localhost:8080/api scripts/smoke-test.sh
```

### 本机开发

推荐使用脚本启动本机开发环境：

```bash
scripts/start-local.sh
```

脚本默认复用 `ai-agent-infra-stack` 的 MySQL，并在宿主机启动 Spring Boot 后端和 Vite 前端。Java 后端使用 Logback 写入 `logs/backend/incident-copilot.log` 和 `logs/backend/incident-copilot-error.log`，前端日志写入 `.run/frontend.log`。

查看运行日志：

```bash
scripts/logs.sh local
scripts/logs.sh backend
scripts/logs.sh error
scripts/logs.sh docker
```

查看或停止本地端口进程：

```bash
scripts/status-local.sh
scripts/stop-local.sh
```

如需手动启动，先启动 `ai-agent-infra-stack`，并确保其中 MySQL 存在数据库和账号：

```text
database: incident_copilot
username: incident_copilot
password: incident_copilot123
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

更完整的运行配置见 `docs/RUNNING.md`。

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
curl -X POST http://localhost:8080/api/actions/1/record-result \
  -H 'Content-Type: application/json' \
  -d '{"executor":"sre-demo","resultDetail":"已在线下执行，本系统记录审计结果"}'
curl http://localhost:8080/api/incidents/1/metrics
curl -X POST http://localhost:8080/api/incidents/1/generate-postmortem
curl http://localhost:8080/api/incidents/1/postmortem
```

## MVP 演示主线

1. 启动 `ai-agent-infra-stack`、`diagnosis-service`、`demo-service`、Incident Copilot 后端和前端。
2. 通过 Grafana / Alertmanager webhook payload 注入 portfolio 支付回调超时告警。
3. 系统记录入站告警、创建或关联 Incident，并启动 `IncidentHandlingWorkflow`。
4. 展示 Incident 指标快照、MCP 调用、Runbook 检索和 Workflow 时间线。
5. 生成 3 个候选处置方案。
6. 对中风险方案点击“记录处理结果”。
7. 指标快照从 `degraded` 变为 `recovering`。
8. 生成复盘报告并关闭 Incident。

## 核心文档

- 产品说明: `docs/PRD.md`
- 技术设计: `docs/TECH_DESIGN.md`
- 数据库 DDL: `database/schema.sql`
- 数据库说明: `docs/DATABASE_SCHEMA.md`
- 接口文档: `docs/API.md`
- Runbook 定义: `docs/RUNBOOKS.md`
- 模块结构设计: `docs/MODULE_STRUCTURE.md`
- 运行说明: `docs/RUNNING.md`
- 真实链路联调: `docs/REAL_CHAIN_INTEGRATION.md`
- 发布检查表: `docs/RELEASE_READINESS.md`
- 7 天开发步骤: `docs/DEVELOPMENT_PLAN.md`
- 后续开发计划: `docs/NEXT_STEPS.md`
- 演示脚本: `docs/DEMO_SCRIPT.md`

## 当前缺失与下一步

当前项目已经具备完整 MVP 能力，剩余重点是稳定性和展示质量：

- 网络正常后重新跑 `docker compose up -d --build`，完成容器级端到端验收。
- 补后端核心单元测试和接口测试。
- 与真实 `diagnosis-service` 做一轮非 fallback MCP 联调。
- 增强重复操作幂等性、错误码枚举和分页响应。

详细计划见 `docs/NEXT_STEPS.md`。

## 发布状态

当前仓库已补齐 MVP 发布所需的基础资料：

- `LICENSE`
- `SECURITY.md`
- `CHANGELOG.md`
- `.env.example`
- `.github/workflows/ci.yml`
- `docs/RELEASE_READINESS.md`

发布前仍需完成容器级端到端实跑和 README 截图。真实链路边界见 `docs/REAL_CHAIN_INTEGRATION.md`。
