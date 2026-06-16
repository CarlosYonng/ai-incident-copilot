package com.example.incidentcopilot.action;

import java.time.LocalDateTime;

public record ActionProposal(
    Long id,
    Long incidentId,
    Long workflowInstanceId,
    String title,
    String actionType,
    String riskLevel,
    String reason,
    String evidenceJson,
    String impact,
    String precheck,
    boolean requiresApproval,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
