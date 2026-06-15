package com.example.incidentcopilot.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentCreateRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 128) String serviceName,
    @Size(max = 255) String endpoint,
    @Size(max = 64) String source,
    @Size(max = 128) String traceId,
    @Size(max = 128) String exceptionType,
    String summary
) {
}
