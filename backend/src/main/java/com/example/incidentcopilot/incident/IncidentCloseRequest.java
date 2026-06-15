package com.example.incidentcopilot.incident;

import jakarta.validation.constraints.NotBlank;

public record IncidentCloseRequest(
    @NotBlank String closedBy,
    String comment
) {
}
