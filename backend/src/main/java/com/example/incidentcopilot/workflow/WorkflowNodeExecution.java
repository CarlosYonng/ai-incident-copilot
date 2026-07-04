package com.example.incidentcopilot.workflow;

import java.time.LocalDateTime;

/**
 * Audit row for a single workflow node execution.
 *
 * <p>Inputs and outputs are stored as JSON strings so the UI can show the exact
 * evidence used at each step without requiring every node payload to become a
 * separate relational table during the MVP phase.</p>
 *
 * @param id database primary key
 * @param workflowInstanceId workflow instance that owns this execution
 * @param nodeName concrete workflow node name
 * @param nodeType node category such as METRICS, MCP, RUNBOOK, ACTION
 * @param status node status such as RUNNING, SUCCESS, FAILED
 * @param inputJson serialized input snapshot
 * @param outputJson serialized node output
 * @param errorMessage failure message when present
 * @param startedAt node start timestamp
 * @param finishedAt node finish timestamp
 * @param durationMs execution duration in milliseconds
 * @param createdAt creation timestamp
 */
public record WorkflowNodeExecution(
    Long id,
    Long workflowInstanceId,
    String nodeName,
    String nodeType,
    String status,
    String inputJson,
    String outputJson,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Long durationMs,
    LocalDateTime createdAt
) {
}
