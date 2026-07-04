# AI Agent Portfolio GraphRAG Fallback 异常 Runbook

## 适用场景

- 向量检索结果不足时，GraphRAG / Neo4j fallback 未生效。
- Neo4j 查询超时、连接失败或 Cypher 报错。
- 日志出现 `GraphRAG fallback failed`、`Neo4j timeout`、`Cypher syntax error`、`graph context empty`。

## 常见症状

- 向量检索无结果后没有补充图谱上下文。
- GraphRAG 查询耗时高，拖慢整体回答。
- 同一个客户或知识库的实体关系查询为空。
- 回答缺少客户、项目、角色、知识库之间的关系解释。

## 排查步骤

1. 查看 Agent Trace 是否进入 GraphRAG fallback 节点。
2. 查询 Neo4j 连接状态、慢查询和错误日志。
3. 检查实体抽取结果、图谱节点数量和关系数量。
4. 查看生成的 Cypher 是否包含租户、客户或权限过滤条件。
5. 查询近期是否修改实体 schema、关系类型或 fallback 触发阈值。

## 常见原因

- Neo4j 不可用或查询超时。
- 实体抽取失败导致 graph query 条件为空。
- Cypher 生成不合法。
- 图谱数据未同步或关系缺失。
- fallback 触发阈值配置错误。

## 低风险动作

- 记录 traceId、实体抽取结果、Cypher 和 Neo4j 错误。
- 临时关闭有问题知识库的 GraphRAG 展示标签。
- 通知图谱维护人检查数据同步任务。

## 中风险动作

- 临时关闭 GraphRAG fallback，回退到向量检索 + 关键词检索。
- 限制 GraphRAG 查询深度和返回节点数。
- 对高耗时 Cypher 增加超时保护。

## 高风险动作

- 重建 Neo4j 图谱。
- 修改实体关系 schema。
- 批量删除或重导图谱数据。
- 修改全局 fallback 触发规则。

## 前置检查

- 确认关闭 GraphRAG 不会影响核心问答链路可用性。
- 确认查询深度限制不会漏掉关键业务关系。
- 确认图谱重建有原始数据和回滚方案。

## 观察指标

- GraphRAG fallback trigger count。
- Neo4j query latency。
- graph context hit rate。
- Cypher error rate。
- RAG answer citation count。

## 复盘要求

- 明确问题来自 Neo4j 可用性、实体抽取、Cypher 生成还是图谱同步。
- 增加 GraphRAG 节点级 trace 和慢查询告警。
- 为 fallback 设计明确的降级开关和超时预算。
