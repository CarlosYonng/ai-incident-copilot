# AI Agent Portfolio 知识库导入失败 Runbook

## 适用场景

- 上传文档后知识库一直处于 processing / failed。
- 文档切片、embedding、向量 upsert 或元数据写入失败。
- 日志出现 `ingestion failed`、`embedding batch failed`、`chunk parse error`、`upsert points failed`。

## 常见症状

- 新上传文档无法被 RAG 检索命中。
- 导入任务队列积压。
- 同一批文档部分成功、部分失败。
- embedding token 超限或文档解析异常。

## 排查步骤

1. 查询导入任务状态、失败阶段、documentId、knowledgeBaseId 和 batchId。
2. 查看文档类型、大小、页数、编码和解析日志。
3. 检查 chunk 数量、chunk size、overlap 和 embedding token 数。
4. 查询 embedding provider 错误码和限流情况。
5. 检查 Qdrant upsert 是否成功以及 metadata 是否写入。

## 常见原因

- 文档格式不支持或解析失败。
- 单个 chunk 超过 embedding token 限制。
- embedding provider 限流或超时。
- Qdrant upsert 失败。
- 任务重试没有幂等，导致状态卡住。

## 低风险动作

- 标记失败文档并通知知识库维护人。
- 保存失败阶段、错误码和原始文件名。
- 暂停该文档自动重试，避免重复消耗 token。

## 中风险动作

- 对失败批次重新切片并单独重跑 embedding。
- 暂停大文件导入队列，优先处理小文件。
- 降低 embedding batch size。
- 对失败文档启用纯文本解析 fallback。

## 高风险动作

- 清空并重建整个知识库索引。
- 删除生产文档元数据。
- 切换 embedding model。
- 手工修改导入任务状态。

## 前置检查

- 确认重跑任务具备幂等性，不会产生重复 chunk。
- 确认失败文档不包含敏感信息泄露风险。
- 确认清理向量点时只作用于目标 documentId。

## 观察指标

- ingestion success rate。
- failed document count。
- embedding batch latency。
- Qdrant upsert error rate。
- processing queue length。

## 复盘要求

- 说明失败发生在解析、切片、embedding、upsert 还是状态写入。
- 补齐导入任务幂等和失败重试策略。
- 增加文档导入前置校验和失败可视化。
