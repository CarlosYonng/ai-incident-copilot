package com.example.incidentcopilot.workflow;

import java.time.LocalDateTime;

/**
 * 工作流节点执行记录的 API 响应体。
 * <p>
 * 包含节点的名称、类型、执行状态、耗时、输入/输出 JSON 及时间信息，用于前端展示节点执行时间线。
 * </p>
 *
 * @param id           节点执行记录 ID
 * @param nodeName     节点名称
 * @param nodeType     节点类型 （如 METRICS、MCP、RUNBOOK、ACTION）
 * @param status       执行状态 （RUNNING、SUCCESS、FAILED）
 * @param durationMs   执行耗时（毫秒）
 * @param inputJson    序列化后的输入快照
 * @param outputJson   序列化后的节点输出
 * @param errorMessage 失败时的错误信息
 * @param startedAt    节点开始时间
 * @param finishedAt   节点结束时间
 */
public record WorkflowNodeExecutionResponse(
    Long id,
    String nodeName,
    String nodeType,
    String status,
    Long durationMs,
    String inputJson,
    String outputJson,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {
  /**
   * 从持久化实体 {@link WorkflowNodeExecution} 转换为 API 响应体。
   *
   * @param execution 节点执行实体
   * @return API 响应体
   */
  public static WorkflowNodeExecutionResponse from(WorkflowNodeExecution execution) {
    return new WorkflowNodeExecutionResponse(
        execution.id(),
        execution.nodeName(),
        execution.nodeType(),
        execution.status(),
        execution.durationMs(),
        execution.inputJson(),
        execution.outputJson(),
        execution.errorMessage(),
        execution.startedAt(),
        execution.finishedAt()
    );
  }
}
