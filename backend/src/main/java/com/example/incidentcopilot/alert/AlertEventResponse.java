package com.example.incidentcopilot.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AlertEventResponse(
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
    String status,
    String decisionReason,
    LocalDateTime receivedAt
) {
  public static AlertEventResponse from(AlertEvent event) {
    return new AlertEventResponse(
        event.id(),
        event.eventId(),
        event.incidentId(),
        event.source(),
        event.signalName(),
        event.serviceName(),
        event.endpoint(),
        event.traceId(),
        event.exceptionType(),
        event.summary(),
        event.errorRate(),
        event.p95Latency(),
        event.qps(),
        event.affectedRequests(),
        event.severityHint(),
        event.status(),
        event.decisionReason(),
        event.receivedAt()
    );
  }
}
