package com.example.incidentcopilot.incident;

import java.time.LocalDateTime;

public record Incident(
    Long id,
    String incidentNo,
    String title,
    String serviceName,
    String endpoint,
    String severity,
    String status,
    String source,
    String traceId,
    String exceptionType,
    String summary,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime closedAt
) {
}
