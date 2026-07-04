package com.example.incidentcopilot.incident;

import java.time.LocalDateTime;

/**
 * Primary incident domain record.
 *
 * <p>The record mirrors the {@code incident} table and is intentionally kept as
 * a data carrier: lifecycle decisions live in services and workflow nodes.</p>
 *
 * @param id database primary key
 * @param incidentNo operator-facing incident number
 * @param title short incident title
 * @param serviceName affected service
 * @param endpoint affected endpoint or job when known
 * @param severity business severity such as P0/P1/P2/P3
 * @param status lifecycle status such as OPEN, WORKFLOW_RUNNING, RECOVERING, FAILED, CLOSED
 * @param source creation source such as MANUAL, DEMO, ALERT
 * @param traceId trace identifier used for log correlation
 * @param exceptionType observed exception or failure type
 * @param summary alert summary or operator context
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 * @param closedAt closure timestamp
 */
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
