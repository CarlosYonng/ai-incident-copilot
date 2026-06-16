package com.example.incidentcopilot.audit;

import java.time.LocalDateTime;

public record ToolCallLogResponse(
    Long id,
    String nodeName,
    String toolName,
    boolean success,
    Long durationMs,
    String requestJson,
    String responseJson,
    String errorMessage,
    LocalDateTime createdAt
) {
  public static ToolCallLogResponse from(ToolCallLog log) {
    return new ToolCallLogResponse(
        log.id(),
        log.nodeName(),
        log.toolName(),
        log.success(),
        log.durationMs(),
        log.requestJson(),
        log.responseJson(),
        log.errorMessage(),
        log.createdAt()
    );
  }
}
