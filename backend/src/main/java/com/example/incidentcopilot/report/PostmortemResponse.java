package com.example.incidentcopilot.report;

import java.util.List;

public record PostmortemResponse(
    Long incidentId,
    String summary,
    String rootCause,
    String impact,
    List<String> actionItems,
    List<String> preventionItems,
    String reportContent
) {
}
