package com.example.incidentcopilot.audit;

import java.time.LocalDateTime;

public record ToolCallLog(
    Long id,
    Long workflowInstanceId,
    String nodeName,
    String toolName,
    String requestJson,
    String responseJson,
    boolean success,
    String errorMessage,
    Long durationMs,
    LocalDateTime createdAt
) {
}
