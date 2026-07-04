package com.example.incidentcopilot.action;

import java.time.LocalDateTime;

/**
 * Candidate remediation action generated from diagnosis, metrics, and runbook evidence.
 *
 * <p>The system records suggestions and human decisions. It does not execute
 * production rollback, SQL, scaling, or configuration APIs directly.</p>
 *
 * @param id database primary key
 * @param incidentId incident that owns the proposal
 * @param workflowInstanceId workflow instance that generated the proposal
 * @param title operator-facing title
 * @param actionType stable action code used by UI and audit records
 * @param riskLevel LOW, MEDIUM, or HIGH
 * @param reason why this action is recommended
 * @param evidenceJson serialized diagnosis/runbook/severity evidence
 * @param impact expected business or technical impact
 * @param precheck checks required before execution
 * @param requiresApproval whether human approval is required
 * @param status proposal status such as READY, PENDING, APPROVED, REJECTED, ESCALATED, OFFLINE_EXECUTED
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
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
