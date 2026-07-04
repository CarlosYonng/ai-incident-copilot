package com.example.incidentcopilot.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AlertEvent(
    Long id,
    String eventId,
    Long incidentId,
    String source,
    String signalName,
    String serviceName,
    String endpoint,
    String traceId,
    String exceptionType,
    String summary,
    BigDecimal errorRate,
    Integer p95Latency,
    Integer qps,
    Integer affectedRequests,
    String severityHint,
    String rawPayloadJson,
    String status,
    String decisionReason,
    LocalDateTime receivedAt,
    LocalDateTime createdAt
) {
}
