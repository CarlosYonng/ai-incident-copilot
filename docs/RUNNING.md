# 本地与 Docker 运行说明

本文档给出两种启动方式：

- 本地开发模式：固定复用 `ai-agent-infra-stack` 的 MySQL，后端和前端在宿主机运行。
- Docker 模式：固定复用 `ai-agent-infra-stack` 的 MySQL，只启动本项目后端和前端。

## 端口

| 服务 | 地址 |
| --- | --- |
| Frontend | `http://localhost:3000` |
| Backend API | `http://localhost:8080/api` |
| Backend Health | `http://localhost:8080/api/health` |
| MySQL | `localhost:3306`，来自 `ai-agent-infra-stack` |

## 本地开发模式

前置依赖：

- 已启动 `ai-agent-infra-stack`，其中 MySQL 暴露在 `localhost:3306`
- Java 21
- Maven
- Node.js 20+
- npm

启动：

```bash
scripts/init-db.sh
scripts/start-local.sh
```

脚本行为：

- 固定连接 `ai-agent-infra-stack` 的 MySQL，本项目不提供 MySQL 容器。
- 在宿主机启动 Spring Boot 后端。
- 在宿主机启动 Vite 前端。
- 后端应用日志由 Logback 写入 `logs/backend/incident-copilot.log`。
- 后端错误日志由 Logback 写入 `logs/backend/incident-copilot-error.log`。
- Maven 启动器日志写入 `.run/backend-launcher.log`。
- 前端日志写入 `.run/frontend.log`。
- 默认实时 tail 后端和前端日志到当前终端。

常用环境变量：

```bash
BACKEND_PORT=8080 FRONTEND_PORT=3000 scripts/start-local.sh
DIAGNOSIS_MCP_BASE_URL=http://localhost:8200 scripts/start-local.sh
DIAGNOSIS_MCP_FALLBACK_ENABLED=false scripts/start-local.sh
TAIL_LOGS=false scripts/start-local.sh
```

查看日志：

```bash
scripts/logs.sh local
scripts/logs.sh backend
scripts/logs.sh error
scripts/logs.sh frontend
```

查看或停止本地运行进程：

```bash
scripts/status-local.sh
scripts/stop-local.sh
```

固定本地配置：

```text
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/incident_copilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=incident_copilot
SPRING_DATASOURCE_PASSWORD=incident_copilot123
SERVER_PORT=8080
RUNBOOK_DIR=<project>/runbooks
DIAGNOSIS_MCP_BASE_URL=http://localhost:8200
DIAGNOSIS_MCP_FALLBACK_ENABLED=true
VITE_API_BASE_URL=http://localhost:8080/api
```

数据库说明：

- `scripts/init-db.sh` 使用 infra MySQL root 创建 `incident_copilot` database。
- 应用只使用专用用户 `incident_copilot` 连接数据库。
- Flyway 在 `incident_copilot` 中初始化业务表。

## Docker 模式

启动：

```bash
scripts/start-docker.sh
```

停止：

```bash
scripts/stop-docker.sh
```

脚本行为：

- 如果根目录没有 `.env`，会从 `.env.example` 复制一份。
- 执行 `docker compose --env-file .env up --build -d backend frontend`，只启动非 MySQL 服务。
- 等待后端健康检查通过。
- 如果后端未就绪，会自动打印最近 120 行后端容器日志。

查看 Docker 日志：

```bash
scripts/logs.sh docker
docker compose logs -f backend frontend
docker compose logs -f backend
docker compose logs -f frontend
```

Docker 模式下后端固定连接宿主机上的 infra MySQL：

```env
SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/incident_copilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=incident_copilot
SPRING_DATASOURCE_PASSWORD=incident_copilot123
```

Docker 模式下后端 Logback 日志挂载到宿主机：

```text
logs/backend/incident-copilot.log
logs/backend/incident-copilot-error.log
```

后端默认使用容器内路径 `/runbooks`，由 Compose 挂载根目录 `runbooks/`：

```yaml
volumes:
  - ./runbooks:/runbooks:ro
```

如果 `diagnosis-service` 在宿主机运行，Docker 后端应使用：

```env
DIAGNOSIS_MCP_BASE_URL=http://host.docker.internal:8200
```

如果 `diagnosis-service` 也在同一个 Docker 网络中运行，可改成对应服务名：

```env
DIAGNOSIS_MCP_BASE_URL=http://diagnosis-service:8200
```

## 验收

服务启动后运行：

```bash
scripts/smoke-test.sh
```

如果后端地址不同：

```bash
BASE_URL=http://localhost:8080/api scripts/smoke-test.sh
```
