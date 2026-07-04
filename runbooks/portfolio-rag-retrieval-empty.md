# AI Agent Portfolio RAG 检索无结果 Runbook

## 适用场景

- `ai-agent-portfolio` 的 RAG 问答返回“未找到相关知识”或答案明显泛化。
- `/api/chat`、`/api/chat/stream`、`/api/knowledge/query` 召回文档数量为 0。
- diagnosis-service 报告中出现 `retrieval empty`、`no chunks found`、`low similarity score`、`RAG recall failed`。

## 常见症状

- 用户问题能命中知识库主题，但回答没有引用任何文档。
- 检索 topK 结果为空或相似度整体偏低。
- 同一知识库近期导入过文档，但问答仍无法召回。
- feedback 中出现“答非所问”“没有引用资料”“知识库里明明有”的负反馈。

## 排查步骤

1. 查询 ai-agent-portfolio 的 Agent Trace，确认本次请求是否进入 RAG 检索节点。
2. 查询知识库、租户、角色权限过滤条件，确认用户是否有权访问目标知识库。
3. 通过 diagnosis-service `search_logs` 查看 query、knowledgeBaseId、topK、scoreThreshold、retrievedChunkCount。
4. 检查最近是否调整 embedding model、相似度阈值、rerank 规则或知识库过滤条件。
5. 查询相关代码位置，确认检索失败后是否错误地直接走纯 LLM 回答。

## 常见原因

- 相似度阈值过高导致全部 chunk 被过滤。
- 用户角色或客户隔离条件误过滤目标知识库。
- 文档导入成功但 embedding 未完成或索引未刷新。
- query rewrite 生成了偏离原问题的检索 query。
- 知识库切片过大、过小或缺少标题上下文。

## 低风险动作

- 记录问题、用户、知识库、traceId 和 query。
- 补充 Agent Trace 中的 retrievedChunkCount、topScore、knowledgeBaseId 字段。
- 通知知识库负责人确认文档是否存在和是否允许访问。
- 创建知识库召回质量分析任务。

## 中风险动作

- 临时降低相似度阈值或扩大 topK。
- 对该知识库启用关键词检索 / BM25 兜底。
- 临时关闭有问题的 query rewrite 规则。
- 重新触发目标知识库的 embedding 索引刷新。

## 高风险动作

- 批量重建全量向量索引。
- 切换 embedding model。
- 修改租户或角色权限过滤逻辑。
- 删除并重新导入生产知识库。

## 前置检查

- 确认降低阈值不会引入跨客户、跨角色的数据泄露。
- 确认 topK 扩大会带来的 token 成本和响应延迟可接受。
- 确认索引刷新只影响目标知识库或目标租户。

## 观察指标

- retrievedChunkCount。
- topScore / averageScore。
- RAG answer citation count。
- RAG 请求 p95 延迟。
- 负反馈率。

## 复盘要求

- 说明是数据导入、权限过滤、检索参数还是 query rewrite 导致召回失败。
- 补齐 RAG Trace 字段和召回质量监控。
- 为核心知识库增加定时检索健康检查问题集。
