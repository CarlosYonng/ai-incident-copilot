package com.example.incidentcopilot.runbook;

public record RunbookDocument(
    String fileName,
    String title,
    String content,
    int score,
    String excerpt
) {
}
