package com.example.incidentcopilot.system;

import java.time.LocalDateTime;
import java.util.Map;

public record HealthResponse(
    String status,
    String application,
    LocalDateTime checkedAt,
    Map<String, Object> dependencies
) {
}
