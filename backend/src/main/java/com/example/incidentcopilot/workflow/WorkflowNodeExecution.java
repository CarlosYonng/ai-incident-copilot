package com.example.incidentcopilot.workflow;

import java.time.LocalDateTime;

/**
 * 单个工作流节点的执行审计记录。
 *
 * <p>输入和输出以 JSON 字符串保存，前端可以展示每一步使用的证据；MVP 阶段不用为每种节点输出都拆明细表。</p>
 *
 * @param id 数据库主键
 * @param workflowInstanceId 所属工作流实例 ID
 * @param nodeName 具体节点名称
 * @param nodeType 节点类型，例如 METRICS、MCP、RUNBOOK、ACTION
 * @param status 节点状态，例如 RUNNING、SUCCESS、FAILED
 * @param inputJson 序列化后的输入快照
 * @param outputJson 序列化后的节点输出
 * @param errorMessage 失败信息
 * @param startedAt 节点开始时间
 * @param finishedAt 节点结束时间
 * @param durationMs 执行耗时，单位毫秒
 * @param createdAt 创建时间
 */
public record WorkflowNodeExecution(
    Long id,
    Long workflowInstanceId,
    String nodeName,
    String nodeType,
    String status,
    String inputJson,
    String outputJson,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Long durationMs,
    LocalDateTime createdAt
) {
}
