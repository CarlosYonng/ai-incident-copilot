# AI Incident Copilot

AI Incident Copilot 是一个面向“被关注告警”的智能故障协同处理系统。它接收 Grafana、Alertmanager 或业务系统推送的告警事件，自动创建或关联 Incident，调用 `diagnosis-service` 的 MCP 工具获取诊断证据，检索 Runbook，生成处置建议，并把人工决策、处理记录、恢复观察和复盘报告沉淀为可审计的故障闭环。

项目定位不是替代 SRE 自动执行生产变更，而是把告警后的证据收集、诊断编排、风险分级、人工确认和复盘归档标准化。中高风险动作只生成建议和审批卡片，由人确认并在线下执行，系统负责记录过程和结果。

## 核心价值

- **告警入站统一化**：支持统一告警模型，并提供 Grafana / Alertmanager webhook 适配。
- **Incident 生命周期闭环**：从告警、诊断、处置建议、人工记录、恢复观察到复盘归档。
- **MCP 诊断工具编排**：通过 `diagnosis-service` 查询日志、代码、历史工单和诊断报告。
- **Runbook 知识检索**：按服务、接口、异常类型和诊断摘要检索本地 Markdown Runbook。
- **Human-in-the-loop 风险门禁**：低风险动作可记录采纳，中高风险动作必须人工确认。
- **可审计 Workflow**：每个节点、工具调用、人工决策和处理结果都有结构化记录。
- **真实链路可扩展**：可对接 `ai-agent-portfolio` 的 Prometheus / Grafana 告警，打通真实业务告警链路。

## 系统边界

系统会自动执行：

- 记录原始告警事件，并做幂等、阈值判断和 Incident 关联。
- 创建 Incident，启动固定顺序的 `IncidentHandlingWorkflow`。
- 记录告警指标快照，并在处置后写入恢复观察快照。
- 调用 `diagnosis-service` MCP tools。
- 检索 Runbook，生成诊断摘要、处置建议、风险说明和复盘报告。
- 记录 Workflow 节点、MCP 工具调用、人工决策、处理结果和复盘归档。

系统不会自动执行：

- 真实重启服务器。
- 真实回滚发布。
- 真实扩容或缩容。
- 真实执行 SQL。
- 真实修改生产配置。

中高风险动作必须进入人工确认流程。本项目只记录建议、审批意图和线下执行结果。

## 业务链路

```text
ai-agent-portfolio / business service
  -> Prometheus metrics / logs / traces
  -> Grafana or Alertmanager alert
  -> POST /api/alerts/grafana or /api/alerts/alertmanager
  -> AlertIngestService
  -> IncidentHandlingWorkflow
  -> diagnosis-service MCP tools
  -> Runbook retrieval
  -> Action proposal and risk review
  -> Human decision and action record
  -> Recovery metrics
  -> Postmortem report
```

其中：

- `ai-agent-portfolio` 负责产生真实业务请求、错误、指标和告警。
- `diagnosis-service` 负责沉淀全量诊断证据，并暴露 MCP 工具。
- `ai-incident-copilot` 负责告警后的响应编排、审计和闭环。

## 架构概览

```text
frontend React console
  -> backend Spring Boot API
    -> MySQL incident_copilot
    -> local runbooks/*.md
    -> diagnosis-service /mcp
    -> Grafana / Alertmanager webhook payload
```

后端核心模块：

- `alert`：告警入站、Grafana / Alertmanager payload 适配、Incident 关联。
- `incident`：Incident 创建、查询、关闭和生命周期状态。
- `workflow`：固定节点式 Incident 处理编排和节点审计。
- `diagnosis`：MCP JSON-RPC client。
- `runbook`：本地 Markdown Runbook 检索。
- `action`：候选处置方案、审批、升级、记录处理结果。
- `metrics`：Incident 指标快照。
- `report`：结构化复盘报告。

## 技术栈

- Backend: Java 21, Spring Boot 3, Spring Web, Spring Validation, Spring JDBC
- Database: MySQL 8, Flyway
- Frontend: React, TypeScript, Vite
- Workflow: 固定节点式 Agent Workflow
- MCP: JSON-RPC 调用 `diagnosis-service`
- Knowledge: 本地 Markdown Runbook
- Deploy: Docker Compose

## 目录结构

```text
ai-incident-copilot/
  backend/                 Spring Boot 后端
  frontend/                React 控制台
  database/                初始化 SQL 和 schema
  docs/                    产品、技术、API、运行、演示文档
  prompts/                 处置方案、严重级别、复盘等提示词
  runbooks/                故障处置知识库
  scripts/                 启停、初始化、smoke test 脚本
  docker-compose.yml       本地 Docker 编排
```

## 快速启动

首次启动前先初始化数据库和专用用户：

```bash
scripts/init-db.sh
```

使用 Docker Compose 启动：

```bash
scripts/start-docker.sh
```

启动后访问：

- 前端控制台: `http://localhost:3000`
- 后端 API: `http://localhost:8080/api`
- 健康检查: `http://localhost:8080/api/health`

停止服务：

```bash
scripts/stop-docker.sh
```

## 本机开发

推荐使用脚本启动本机开发环境：

```bash
scripts/start-local.sh
```

查看日志：

```bash
scripts/logs.sh local
scripts/logs.sh backend
scripts/logs.sh error
```

查看或停止本地端口进程：

```bash
scripts/status-local.sh
scripts/stop-local.sh
```

如需手动启动：

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

完整运行说明见 `docs/RUNNING.md`。

## 环境变量

复制模板：

```bash
cp .env.example .env
```

关键变量：

```env
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/incident_copilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=incident_copilot
SPRING_DATASOURCE_PASSWORD=incident_copilot123
DIAGNOSIS_MCP_BASE_URL=http://localhost:8200
DIAGNOSIS_MCP_FALLBACK_ENABLED=true
RUNBOOK_DIR=../runbooks
```

真实 MCP 链路验收时建议关闭 fallback：

```env
DIAGNOSIS_MCP_FALLBACK_ENABLED=false
```

## 告警入口

统一入站接口：

```text
POST /api/alerts/ingest
```

Grafana webhook：

```text
POST /api/alerts/grafana
```

Alertmanager webhook：

```text
POST /api/alerts/alertmanager
```

示例：

```bash
curl -X POST http://localhost:8080/api/alerts/grafana \
  -H 'Content-Type: application/json' \
  -d '{
    "alerts": [
      {
        "fingerprint": "portfolio-ai-timeout-001",
        "startsAt": "2026-07-07T10:00:00Z",
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
          "description": "Java backend -> ai-service -> RAG chain is degraded."
        }
      }
    ]
  }'
```

## 验收

启动后运行完整 API 闭环：

```bash
scripts/smoke-test.sh
```

脚本会完成：

- 健康检查。
- 注入 Grafana 告警。
- 创建 Incident 并启动 Workflow。
- 校验 Workflow 节点和 MCP 工具调用审计。
- 查找需要人工确认的处置方案。
- 记录处置结果。
- 校验恢复观察指标。
- 生成复盘报告。
- 关闭 Incident。

可切换 API 地址：

```bash
BASE_URL=http://localhost:8080/api scripts/smoke-test.sh
```

## Runbook

当前内置 Runbook 覆盖两类场景：

- 通用后端故障：支付回调超时、订单空指针、依赖不可用、数据库慢查询、Redis 缓存异常。
- `ai-agent-portfolio` 真实链路：RAG 检索无结果、Qdrant 不可用、AI service 超时、知识库入库失败、GraphRAG fallback 异常。

Runbook 不是自动执行脚本，而是面向 Incident 的处置知识。它用于说明适用场景、排查步骤、常见原因、低/中/高风险动作、前置检查、观察指标和复盘要求。

## 项目文档

- 产品说明: `docs/PRD.md`
- 项目职责边界: `docs/PROJECT_SCOPE.md`
- 技术设计: `docs/TECH_DESIGN.md`
- API 文档: `docs/API.md`
- 运行说明: `docs/RUNNING.md`
- Runbook 维护说明: `docs/RUNBOOKS.md`
- 真实链路联调: `docs/REAL_CHAIN_INTEGRATION.md`
- 数据库说明: `docs/DATABASE_SCHEMA.md`
- 模块结构: `docs/MODULE_STRUCTURE.md`
- 演示脚本: `docs/DEMO_SCRIPT.md`
- 面试讲解: `docs/INTERVIEW_GUIDE.md`
- 发布检查: `docs/RELEASE_READINESS.md`
- 下一步计划: `docs/NEXT_STEPS.md`

## 当前状态

当前仓库已经具备可运行 MVP 闭环：

- 后端 API、数据库迁移、前端控制台、Docker Compose。
- 告警入站、Incident 生命周期、Workflow 编排、MCP 调用审计。
- Runbook 检索、处置方案生成、风险门禁、人工记录。
- 指标恢复观察和结构化复盘报告。

下一阶段重点是与 `ai-agent-portfolio` 的 Prometheus / Grafana 真实告警联调、补充更细粒度测试、增强幂等性和演示材料。
