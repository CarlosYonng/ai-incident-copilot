package com.example.incidentcopilot.audit;

import java.time.LocalDateTime;

/**
 * 工具调用日志的响应 DTO，用于对外暴露审计记录。
 *
 * <p>与 {@link ToolCallLog} 结构一致，但作为 API 响应对象使用，
 * 避免直接将数据库实体暴露给前端。</p>
 *
 * @param id 数据库主键
 * @param nodeName 发起调用的节点名称
 * @param toolName MCP 或外部工具名称
 * @param success 调用是否成功
 * @param durationMs 调用耗时，单位毫秒
 * @param requestJson 序列化后的请求体
 * @param responseJson 序列化后的响应体或 fallback 证据
 * @param errorMessage 失败信息
 * @param createdAt 创建时间
 */
public record ToolCallLogResponse(
    Long id,
    String nodeName,
    String toolName,
    boolean success,
    Long durationMs,
    String requestJson,
    String responseJson,
    String errorMessage,
    LocalDateTime createdAt
) {
  /**
   * 将 {@link ToolCallLog} 实体转换为响应 DTO。
   *
   * @param log 工具调用日志实体
   * @return 转换后的响应 DTO
   */
  public static ToolCallLogResponse from(ToolCallLog log) {
    return new ToolCallLogResponse(
        log.id(),
        log.nodeName(),
        log.toolName(),
        log.success(),
        log.durationMs(),
        log.requestJson(),
        log.responseJson(),
        log.errorMessage(),
        log.createdAt()
    );
  }
}
