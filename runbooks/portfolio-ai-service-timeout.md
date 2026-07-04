# AI Agent Portfolio AI Service 超时 Runbook

## 适用场景

- Java 后端调用 Python FastAPI ai-service 超时。
- ai-service 调用 LLM、embedding、rerank 或 MCP 工具耗时过长。
- 日志出现 `ReadTimeout`、`AI service timeout`、`LLM gateway timeout`、`upstream model timeout`。

## 常见症状

- `/api/chat` 或 `/api/chat/stream` p95 延迟升高。
- SSE 首 token 时间明显变长。
- Java 后端线程等待 ai-service 响应。
- ai-service worker 数不足或请求队列积压。
- LLM provider 返回 429、5xx 或 timeout。

## 排查步骤

1. 区分超时发生在 Java backend -> ai-service，还是 ai-service -> LLM provider。
2. 查询 trace 中 rewrite、retrieval、rerank、LLM generate 各阶段耗时。
3. 查看 ai-service worker、CPU、内存、连接池和请求队列。
4. 查询 LLM provider 错误码、限流、模型延迟和 token 输出量。
5. 检查是否有超长上下文、异常 topK 或过大的 prompt。

## 常见原因

- LLM provider 延迟或限流。
- prompt 上下文过长导致生成慢。
- rerank 或 embedding 批量请求过大。
- ai-service worker 数不足。
- Java 后端超时时间小于真实 AI 链路耗时。

## 低风险动作

- 记录慢请求 traceId、用户问题、token 数、模型名和阶段耗时。
- 打开只读监控观察 ai-service 队列和 provider 错误码。
- 通知 AI service 负责人和业务侧说明 AI 回答延迟。

## 中风险动作

- 临时降低 topK 或关闭 rerank。
- 对长问题启用摘要压缩或 token 截断。
- 切换到低延迟备用模型。
- 对聊天入口做限流或排队提示。

## 高风险动作

- 修改全局模型路由。
- 大幅调整超时和重试策略。
- 重启 ai-service。
- 修改生产 API key 或 provider 配置。

## 前置检查

- 确认降低 topK 不会让答案质量明显不可接受。
- 确认备用模型的数据合规和输出风格符合要求。
- 确认限流策略不会影响管理端或关键客户演示。

## 观察指标

- ai-service 请求 p95 / p99。
- first token latency。
- LLM provider error rate。
- token input / output。
- Java backend timeout count。

## 复盘要求

- 拆分各阶段耗时，说明瓶颈在检索、rerank、LLM 还是服务容量。
- 增加 AI 链路阶段级 trace。
- 明确备用模型、降级和限流策略。
