package com.example.incidentcopilot.workflow;

import java.time.LocalDateTime;

/**
 * 工作流实例的 API 响应体。
 * <p>
 * 包含工作流实例的基本信息，用于前端展示工作流概览。
 * </p>
 *
 * @param workflowInstanceId 工作流实例 ID
 * @param incidentId         关联的事件 ID
 * @param workflowType       工作流类型名称
 * @param status             执行状态（CREATED、RUNNING、SUCCESS、WAITING_APPROVAL、FAILED）
 * @param currentNode        当前正在执行的节点名称，结束后为 null
 * @param startedAt           工作流开始时间
 * @param finishedAt          工作流结束时间
 */
public record WorkflowResponse(
    Long workflowInstanceId,
    Long incidentId,
    String workflowType,
    String status,
    String currentNode,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {
  /**
   * 从持久化实体 {@link WorkflowInstance} 转换为 API 响应体。
   *
   * @param instance 工作流实例实体
   * @return API 响应体
   */
  public static WorkflowResponse from(WorkflowInstance instance) {
    return new WorkflowResponse(
        instance.id(),
        instance.incidentId(),
        instance.workflowType(),
        instance.status(),
        instance.currentNode(),
        instance.startedAt(),
        instance.finishedAt()
    );
  }
}
