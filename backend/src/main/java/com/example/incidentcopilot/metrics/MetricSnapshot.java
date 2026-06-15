package com.example.incidentcopilot.metrics;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MetricSnapshot(
    Long id,
    Long incidentId,
    String serviceName,
    BigDecimal errorRate,
    Integer p95Latency,
    Integer qps,
    String status,
    LocalDateTime snapshotTime
) {
}
