package com.example.incidentcopilot.workflow;

import java.time.LocalDateTime;

/**
 * 固定故障处理工作流的一次运行实例。
 *
 * <p>同一个故障单可以多次启动工作流；节点级证据和耗时单独保存在 {@link WorkflowNodeExecution}。</p>
 *
 * @param id 数据库主键
 * @param incidentId 本次工作流处理的故障单 ID
 * @param workflowType 工作流定义名称
 * @param status 执行状态，例如 CREATED、RUNNING、SUCCESS、WAITING_APPROVAL、FAILED
 * @param currentNode 当前运行节点，结束后为 {@code null}
 * @param startedAt 工作流开始时间
 * @param finishedAt 工作流结束时间
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 */
public record WorkflowInstance(
    Long id,
    Long incidentId,
    String workflowType,
    String status,
    String currentNode,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
