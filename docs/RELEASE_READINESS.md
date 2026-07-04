# 发布检查表

## 当前状态

当前项目达到 MVP release candidate 状态，可以作为面试/学习项目公开展示。距离生产可用还需要真实指标、真实告警、鉴权、多租户、权限和更完整测试。

## 必须完成

- [x] README 启动说明
- [x] Docker Compose 配置
- [x] 健康检查接口 `/api/health`
- [x] Smoke test
- [x] 后端核心单元测试
- [x] 前端构建
- [x] License
- [x] Security 文档
- [x] Changelog
- [x] `.env.example`
- [x] CI 配置
- [ ] 容器端到端实跑 `docker compose up -d --build`
- [ ] `scripts/smoke-test.sh` 在容器环境通过
- [ ] README 截图
- [ ] GitHub Release `v0.1.0`

## 发布验证命令

```bash
cd backend
mvn test
cd ../frontend
npm ci
npm run build
cd ..
docker compose config
bash -n scripts/smoke-test.sh
```

容器验收：

```bash
docker compose up -d --build
scripts/smoke-test.sh
```

## 发布边界

`v0.1.0` 应明确标记为 MVP / demo / interview project：

- 不包含鉴权系统。
- 不包含真实生产指标适配器。
- 不包含真实告警平台接入。
- 不执行真实生产处置动作。
- 默认 MySQL 密码仅用于本地演示。

## 发布后优先事项

1. 增加 API 集成测试。
2. 增加前端 Playwright 冒烟测试。
3. 接入真实 Diagnosis MCP 数据集。
4. 抽象 MetricsProvider 并接入 Prometheus。
5. 补充截图和演示录屏。
