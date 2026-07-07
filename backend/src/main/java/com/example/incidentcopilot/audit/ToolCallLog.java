package com.example.incidentcopilot.audit;

import java.time.LocalDateTime;

/**
 * 工作流节点发起外部工具调用时生成的审计记录。
 *
 * <p>工具调用审计与节点输出分开保存：一个节点可能调用多个 MCP 工具，每次请求和响应都需要独立追踪。</p>
 *
 * @param id 数据库主键
 * @param workflowInstanceId 触发工具调用的工作流实例 ID
 * @param nodeName 发起调用的节点名称
 * @param toolName MCP 或外部工具名称
 * @param requestJson 序列化后的请求体
 * @param responseJson 序列化后的响应体或 fallback 证据
 * @param success 调用是否成功
 * @param errorMessage 失败信息
 * @param durationMs 调用耗时，单位毫秒
 * @param createdAt 创建时间
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
