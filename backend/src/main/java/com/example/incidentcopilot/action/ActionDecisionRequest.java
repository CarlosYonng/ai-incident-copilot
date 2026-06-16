package com.example.incidentcopilot.action;

import jakarta.validation.constraints.NotBlank;

public record ActionDecisionRequest(
    @NotBlank String approvedBy,
    String comment
) {
}
