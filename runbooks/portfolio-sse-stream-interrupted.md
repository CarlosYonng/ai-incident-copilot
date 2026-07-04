# AI Agent Portfolio SSE 流式响应中断 Runbook

## 适用场景

- `/api/chat/stream` SSE 连接中断、前端收不到后续 token。
- 用户看到回答卡住、半截输出或一直 loading。
- 日志出现 `SseEmitter timeout`、`Broken pipe`、`client disconnected`、`stream closed`。

## 常见症状

- 非流式接口可用，流式接口失败。
- 首 token 正常，但中途断流。
- 浏览器 Network 显示 EventStream pending 后关闭。
- Nginx、网关或浏览器代理存在 idle timeout。

## 排查步骤

1. 确认断点发生在前端、Java backend、ai-service 还是 LLM provider。
2. 查看 SSE 连接建立时间、首 token 时间、最后 token 时间和关闭原因。
3. 检查网关、Nginx、负载均衡的 buffering 和 idle timeout 配置。
4. 查询 ai-service 是否持续产生 token。
5. 检查前端是否正确处理 `done`、`error` 和 reconnect 状态。

## 常见原因

- 网关对 EventStream 做了 buffering。
- idle timeout 小于模型生成间隔。
- 后端没有发送 heartbeat。
- 客户端刷新或切换页面导致连接断开。
- ai-service 异常关闭流但 Java 后端未透传错误。

## 低风险动作

- 记录 traceId、浏览器时间、断流阶段和最后一个 token。
- 在前端提示“回答中断，可重试”。
- 增加 SSE 错误日志和关闭原因。

## 中风险动作

- 临时切换为非流式回答。
- 缩短 heartbeat 间隔。
- 对超长回答启用分段生成。
- 降低单次回答最大 token。

## 高风险动作

- 修改生产网关超时配置。
- 重启网关或 backend。
- 调整全局 SSE buffering 策略。

## 前置检查

- 确认非流式 fallback 不会导致请求总超时。
- 确认 heartbeat 不会造成额外成本或前端解析错误。
- 确认网关配置变更经过回滚预案。

## 观察指标

- SSE disconnect rate。
- first token latency。
- stream duration。
- client retry count。
- `/api/chat/stream` 5xx 和 499 数量。

## 复盘要求

- 明确断流发生在哪一层。
- 补充 SSE 生命周期日志。
- 给出网关、backend、ai-service、前端各层的流式响应契约。
