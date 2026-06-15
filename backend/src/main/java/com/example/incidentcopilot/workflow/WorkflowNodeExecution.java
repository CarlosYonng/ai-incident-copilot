package com.example.incidentcopilot.workflow;

import java.time.LocalDateTime;

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
