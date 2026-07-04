package com.example.incidentcopilot.audit;

import java.time.LocalDateTime;

/**
 * Audit record for a tool call made by a workflow node.
 *
 * <p>This is intentionally separate from node execution output: one node may
 * call several MCP tools, and each request/response needs its own traceable row.</p>
 *
 * @param id database primary key
 * @param workflowInstanceId workflow instance that triggered the tool call
 * @param nodeName node that made the call
 * @param toolName MCP or external tool name
 * @param requestJson serialized request payload
 * @param responseJson serialized response payload or fallback evidence
 * @param success whether the call succeeded
 * @param errorMessage failure message when present
 * @param durationMs call duration in milliseconds
 * @param createdAt creation timestamp
 */
public record ToolCallLog(
    Long id,
    Long workflowInstanceId,
    String nodeName,
    String toolName,
    String requestJson,
    String responseJson,
    boolean success,
    String errorMessage,
    Long durationMs,
    LocalDateTime createdAt
) {
}
