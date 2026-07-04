# AI Agent Portfolio Qdrant 向量库不可用 Runbook

## 适用场景

- RAG 检索请求调用 Qdrant 超时、连接失败或返回 5xx。
- `/api/chat`、`/api/chat/stream`、知识库查询接口错误率升高。
- 日志出现 `QdrantUnavailable`、`Connection refused`、`Vector search timeout`、`collection not found`。

## 常见症状

- RAG 问答整体延迟升高或直接失败。
- Agent Trace 停在 vector retrieval 节点。
- Qdrant collection 查询失败或 collection 缺失。
- Python AI service 与 Qdrant 之间连接池耗尽。

## 排查步骤

1. 查看 Qdrant 实例健康状态、CPU、内存、磁盘和连接数。
2. 查询失败请求的 collectionName、tenantId、knowledgeBaseId 和 vector dimension。
3. 检查 ai-service 到 Qdrant 的网络、DNS、端口和认证配置。
4. 查询近期是否执行过 collection 重建、导入任务或 embedding model 切换。
5. 检查是否有大批量导入任务和在线查询抢占资源。

## 常见原因

- Qdrant 实例不可用或资源耗尽。
- collection 未创建、被误删或命名规则变更。
- embedding dimension 与 collection vector size 不一致。
- 大批量 upsert 导致在线检索延迟升高。
- 连接池、超时或重试配置不合理。

## 低风险动作

- 通知 AI service / 向量库负责人。
- 暂停非核心知识库导入任务观察。
- 收集失败 collection、traceId 和 Qdrant 错误日志。
- 在页面提示“知识库检索暂时不可用”，避免用户误以为答案可信。

## 中风险动作

- 临时启用 GraphRAG / 关键词检索 fallback。
- 对 RAG 查询入口限流。
- 暂停批量 embedding upsert 任务。
- 将受影响知识库切换到只读检索降级模式。

## 高风险动作

- 重启 Qdrant。
- 重建 production collection。
- 切换 embedding model 或 vector size。
- 批量删除异常 collection。

## 前置检查

- 确认 fallback 不会跨租户返回数据。
- 确认暂停导入不会影响正在进行的客户交付。
- 确认重建 collection 有完整原始文档和 embedding 任务记录。

## 观察指标

- Qdrant query latency p95 / p99。
- vector search error rate。
- collection count 和 point count。
- ai-service RAG 请求错误率。
- fallback 命中率。

## 复盘要求

- 说明是资源、网络、collection、dimension 还是导入任务导致故障。
- 增加 Qdrant collection 健康检查和 dimension 校验。
- 将导入任务与在线查询做资源隔离或限速。
