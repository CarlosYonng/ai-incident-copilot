package com.example.incidentcopilot.report;

import java.time.LocalDateTime;

public record PostmortemReport(
    Long id,
    Long incidentId,
    String summary,
    String rootCause,
    String impact,
    String timelineJson,
    String actionItemsJson,
    String preventionItemsJson,
    String reportContent,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
