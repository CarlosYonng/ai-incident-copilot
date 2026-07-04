# 后续开发计划

## 当前进度

项目已完成完整 MVP 闭环，约等于可演示版本的 85%-90%：

- 后端 Spring Boot、MySQL、Flyway、Docker Compose 基础设施已完成。
- Incident CRUD、Workflow 编排、节点执行记录已完成。
- Diagnosis MCP Client、工具调用审计、Runbook 检索已完成。
- Severity 分类、候选处置方案、风险审核、人工确认已完成。
- 记录处置结果、Incident 指标恢复观察、复盘报告已完成。
- React 控制台已能完成告警入站、启动 Workflow、查看证据、执行人工确认、生成复盘和关闭 Incident。
- `scripts/smoke-test.sh` 已覆盖核心 API 闭环。

## 仍缺失的部分

### 1. 端到端运行验证

当前已通过后端构建、前端构建、Docker Compose 配置校验。之前 `docker compose up -d --build` 卡在 Docker Hub 拉取基础镜像超时，需要网络正常后重新跑一次完整容器验收。

### 2. 自动化测试

目前主要依赖 smoke test，缺少更细粒度的测试：

- 后端单元测试：Workflow 节点、Runbook 打分、Severity 分类、Action 状态流转。
- 后端接口测试：Incident、Workflow、Action、Postmortem。
- 前端组件测试或 Playwright 冒烟测试。

### 3. 真实 MCP 联调数据

系统已能调用 `diagnosis-service`，也有 fallback 兜底；但还需要在真实 `diagnosis-service` 已导入日志、代码、工单数据时跑一轮联调，并保留截图或示例响应。

### 4. 演示材料 polish

面试展示还缺：

- README 截图。
- 3 分钟演示录屏或逐步截图。
- 关键架构图和页面截图同步到 `docs/DEMO_SCRIPT.md`。
- 常见面试问题回答补充到 `docs/INTERVIEW_GUIDE.md`。

### 5. 工程质量增强

MVP 内部实现偏轻量，后续可以补：

- 更规范的分页响应。
- 操作幂等性，例如重复启动 Workflow、重复记录处置结果。
- 更强的 JSON Schema 校验。
- 更清晰的错误码枚举。
- 更完整的审计查询页面。

## 下一阶段开发方向

下一阶段目标不是继续堆功能，而是把项目从“能演示”打磨到“面试时稳定、有证据、讲得漂亮”。

优先级建议：

1. **P0：跑通容器级端到端验收**
   - 网络恢复后执行 `docker compose up -d --build`。
   - 执行 `scripts/smoke-test.sh`。
   - 确认前端 `http://localhost:3000` 能走完整流程。

2. **P1：补后端核心测试**
   - Runbook 检索打分测试。
   - Severity 分类测试。
   - Action 状态流转测试。
   - Postmortem 生成测试。

3. **P1：补真实 MCP 联调材料**
   - 启动相邻 `diagnosis-service`。
   - 导入 demo 日志、代码、工单。
   - 确认 tool call success 不只是 fallback。

4. **P2：前端截图和展示 polish**
   - 用浏览器截图主页面、Workflow 时间线、Action 审批、复盘报告。
   - 更新 README 和演示脚本。

5. **P2：代码质量整理**
   - 抽取枚举和错误码。
   - 为重复操作增加幂等控制。
   - 完善接口分页和过滤。

## 执行步骤

### Step 1：容器验收

```bash
docker compose up -d --build
scripts/smoke-test.sh
```

### Step 2：真实 MCP 联调

```bash
cd ../diagnosis-service
docker compose up -d --build
```

回到本项目：

```bash
docker compose up -d --build
scripts/smoke-test.sh
```

### Step 3：测试补强

优先从纯 Java 单元测试开始，避免依赖 Docker 和 MySQL：

- `RunbookRetrieverTest`
- `SeverityClassifierNodeTest`
- `ActionProposalServiceTest`
- `PostmortemServiceTest`

### Step 4：演示材料

- 打开前端控制台。
- 跑支付超时场景。
- 截图 Workflow、Tool Calls、Actions、Postmortem。
- 更新 README 和 `docs/DEMO_SCRIPT.md`。
