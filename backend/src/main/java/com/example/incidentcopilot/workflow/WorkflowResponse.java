package com.example.incidentcopilot.workflow;

import java.time.LocalDateTime;

public record WorkflowResponse(
    Long workflowInstanceId,
    Long incidentId,
    String workflowType,
    String status,
    String currentNode,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {
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
