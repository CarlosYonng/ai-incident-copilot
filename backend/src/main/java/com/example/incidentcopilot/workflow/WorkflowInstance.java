package com.example.incidentcopilot.workflow;

import java.time.LocalDateTime;

/**
 * Runtime state for one execution of the fixed incident handling workflow.
 *
 * <p>Each incident can have multiple workflow instances over time. Node-level
 * evidence and timing are stored separately in {@link WorkflowNodeExecution}.</p>
 *
 * @param id database primary key
 * @param incidentId incident handled by this workflow
 * @param workflowType workflow definition name
 * @param status execution status such as CREATED, RUNNING, SUCCESS, WAITING_APPROVAL, FAILED
 * @param currentNode node currently running, or {@code null} after completion
 * @param startedAt workflow start timestamp
 * @param finishedAt workflow terminal timestamp
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record WorkflowInstance(
    Long id,
    Long incidentId,
    String workflowType,
    String status,
    String currentNode,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
