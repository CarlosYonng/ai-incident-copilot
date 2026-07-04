package com.example.incidentcopilot.metrics;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Demo metric sample associated with an incident.
 *
 * <p>Snapshots drive the console visualization of degradation and recovery; they
 * are not intended to replace a real metrics backend such as Prometheus.</p>
 *
 * @param id database primary key
 * @param incidentId incident this sample belongs to
 * @param serviceName sampled service name
 * @param errorRate error rate percentage
 * @param p95Latency p95 latency in milliseconds
 * @param qps requests per second
 * @param status metric state: normal, degraded, recovering, recovered
 * @param snapshotTime sample timestamp
 */
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
