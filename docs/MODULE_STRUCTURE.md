# 模块结构设计

## 项目级结构

```text
ai-incident-copilot/
  backend/                 Spring Boot 后端服务
  frontend/                React + Vite 前端控制台
  database/                设计阶段数据库 DDL
  docs/                    产品、架构、发布和演示文档
  prompts/                 LLM prompt 设计稿
  runbooks/                本地 Markdown Runbook 知识库
  scripts/                 HTTP 示例和 smoke test
  docker-compose.yml       本地一键运行编排
```

## Backend

```text
backend/src/main/java/com/example/incidentcopilot/
  action/      处置方案、人工确认、动作记录的业务服务和 API
  alert/       业务告警入站、幂等、阈值判断、Incident 关联
  audit/       MCP/LLM 工具调用审计日志
  common/      通用响应、异常处理、JSON、CORS 配置
  demo/        本地演示故障入口
  diagnosis/   Diagnosis MCP JSON-RPC 客户端和诊断证据模型
  incident/    Incident CRUD、详情、关闭
  metrics/     指标快照状态流转
  report/      结构化复盘报告
  runbook/     Markdown Runbook 检索
  system/      健康检查和运行状态
  workflow/    Workflow 引擎、上下文、节点执行记录和节点实现
```

### Backend 编码约定

- Controller 只处理 HTTP 入参、出参和状态码，不承载业务规则。
- Service 承载业务流程和跨 Repository 编排。
- Repository 只做 SQL 访问，不做业务决策。
- 外部业务信号优先进入 `alert/`，由入站链路决定忽略、关联已有 Incident 或创建新 Incident。
- Workflow 节点必须通过 `NodeResult` 输出输入、输出和节点类型，方便审计。
- 中高风险动作只能写入审批和执行记录，不允许执行真实生产动作。

## Frontend

```text
frontend/src/
  App.tsx       当前 MVP 控制台主界面
  main.tsx      React 入口
  styles.css    控制台样式
```

### Frontend 后续拆分方向

当前前端是 MVP 单文件控制台。发布后建议拆分为：

```text
frontend/src/
  api/          API client 和类型
  components/   通用 UI 组件
  features/
    incidents/
    workflows/
    actions/
    metrics/
    reports/
  styles/
```

## Scripts

- `scripts/demo.http`: 手工接口演示。
- `scripts/smoke-test.sh`: 自动跑核心业务闭环。

## Docs

- `PRD.md`: 产品范围。
- `TECH_DESIGN.md`: 技术架构。
- `DATABASE_SCHEMA.md`: 数据库表、字段、状态和索引说明。
- `BACKEND_CODE_WALKTHROUGH.md`: 后端业务链路、代码调用关系、数据流转和脚本说明。
- `API.md`: REST API。
- `RUNNING.md`: 本地开发和 Docker Compose 运行说明。
- `DEMO_SCRIPT.md`: 演示步骤。
- `INTERVIEW_GUIDE.md`: 面试讲解。
- `REAL_CHAIN_INTEGRATION.md`: 真实链路联调说明。
- `RELEASE_READINESS.md`: 发布检查表。
