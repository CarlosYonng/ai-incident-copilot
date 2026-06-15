package com.example.incidentcopilot.workflow;

import java.time.LocalDateTime;

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
