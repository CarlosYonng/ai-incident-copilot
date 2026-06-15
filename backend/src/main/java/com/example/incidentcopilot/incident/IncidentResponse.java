package com.example.incidentcopilot.incident;

import java.time.LocalDateTime;

public record IncidentResponse(
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
  public static IncidentResponse from(Incident incident) {
    return new IncidentResponse(
        incident.id(),
        incident.incidentNo(),
        incident.title(),
        incident.serviceName(),
        incident.endpoint(),
        incident.severity(),
        incident.status(),
        incident.source(),
        incident.traceId(),
        incident.exceptionType(),
        incident.summary(),
        incident.createdAt(),
        incident.updatedAt(),
        incident.closedAt()
    );
  }
}
