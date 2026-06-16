package com.example.incidentcopilot.diagnosis;

import java.util.List;

public record DiagnosisEvidence(
    String summary,
    List<String> logs,
    List<String> codeHints,
    List<String> tickets,
    String reportId,
    String reportMarkdown,
    boolean fallbackUsed
) {
}
