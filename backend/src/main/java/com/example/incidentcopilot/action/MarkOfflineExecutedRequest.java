package com.example.incidentcopilot.action;

import jakarta.validation.constraints.NotBlank;

public record MarkOfflineExecutedRequest(
    @NotBlank String executor,
    String resultDetail
) {
}
