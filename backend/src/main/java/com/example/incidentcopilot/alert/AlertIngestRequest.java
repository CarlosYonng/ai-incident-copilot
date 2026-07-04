package com.example.incidentcopilot.alert;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

public record AlertIngestRequest(
    @NotBlank @Size(max = 128) String eventId,
    @NotBlank @Size(max = 64) String source,
    @NotBlank @Size(max = 128) String signalName,
    @NotBlank @Size(max = 128) String serviceName,
    @Size(max = 255) String endpoint,
    @Size(max = 128) String traceId,
    @Size(max = 128) String exceptionType,
    String summary,
    @DecimalMin("0.0") BigDecimal errorRate,
    @Min(0) Integer p95Latency,
    @Min(0) Integer qps,
    @Min(0) Integer affectedRequests,
    @Size(max = 16) String severityHint,
    Map<String, Object> rawPayload,
    Boolean startWorkflow
) {
}
