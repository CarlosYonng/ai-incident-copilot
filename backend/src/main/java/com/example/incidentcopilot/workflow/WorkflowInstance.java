package com.example.incidentcopilot.workflow;

import java.time.LocalDateTime;

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
