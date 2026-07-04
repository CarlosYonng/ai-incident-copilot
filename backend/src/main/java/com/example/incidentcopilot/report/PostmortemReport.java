package com.example.incidentcopilot.report;

import java.time.LocalDateTime;

/**
 * Generated incident postmortem.
 *
 * <p>Structured JSON fields feed the UI, while {@code reportContent} keeps a
 * rendered long-form report suitable for copy/paste into external systems.</p>
 *
 * @param id database primary key
 * @param incidentId incident summarized by this report
 * @param summary short incident summary
 * @param rootCause root cause analysis
 * @param impact user or business impact
 * @param timelineJson serialized incident timeline
 * @param actionItemsJson serialized follow-up action items
 * @param preventionItemsJson serialized preventive improvements
 * @param reportContent rendered postmortem content
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
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
