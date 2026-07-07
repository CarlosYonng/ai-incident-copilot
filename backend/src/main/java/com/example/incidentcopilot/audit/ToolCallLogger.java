package com.example.incidentcopilot.audit;

import com.example.incidentcopilot.common.JdbcJson;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 外部工具调用审计写入器。
 *
 * <p>每一次 MCP 工具请求都单独落库，方便复盘时追踪请求参数、响应内容、失败原因和耗时。</p>
 */
@Repository
public class ToolCallLogger {
  /** JDBC 模板，用于执行 SQL 插入和查询操作。 */
  private final JdbcTemplate jdbcTemplate;
  /** JSON 序列化工具，用于将请求/响应对象转为 JSON 字符串写入数据库。 */
  private final JdbcJson jdbcJson;

  /**
   * 构造审计日志写入器。
   *
   * @param jdbcTemplate JDBC 模板
   * @param jdbcJson JSON 序列化工具
   */
  public ToolCallLogger(JdbcTemplate jdbcTemplate, JdbcJson jdbcJson) {
    this.jdbcTemplate = jdbcTemplate;
    this.jdbcJson = jdbcJson;
  }

  /**
   * 记录一次外部工具调用的审计日志。
   *
   * <p>将请求参数、响应内容和耗时等信息写入数据库 {@code tool_call_log} 表，
   * 用于后续复盘和排障。</p>
   *
   * @param workflowInstanceId 触发工具调用的工作流实例 ID
   * @param nodeName 发起调用的节点名称
   * @param toolName MCP 或外部工具名称
   * @param request 请求对象（将被序列化为 JSON 存储）
   * @param response 响应对象（将被序列化为 JSON 存储）
   * @param success 调用是否成功
   * @param errorMessage 失败信息，成功时可以为 null
   * @param durationMs 调用耗时，单位毫秒
   */
  public void log(
      Long workflowInstanceId,
      String nodeName,
      String toolName,
      Object request,
      Object response,
      boolean success,
      String errorMessage,
      long durationMs
  ) {
    jdbcTemplate.update("""
        INSERT INTO tool_call_log (
          workflow_instance_id, node_name, tool_name, request_json, response_json,
          success, error_message, duration_ms
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        workflowInstanceId,
        nodeName,
        toolName,
        jdbcJson.stringify(request),
        jdbcJson.stringify(response),
        success,
        errorMessage,
        durationMs
    );
  }

  /**
   * 根据工作流实例 ID 查询该工作流的所有工具调用审计记录。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @return 按创建时间升序排列的工具调用日志列表
   */
  public List<ToolCallLog> findByWorkflow(Long workflowInstanceId) {
    return jdbcTemplate.query("""
        SELECT * FROM tool_call_log
        WHERE workflow_instance_id = ?
        ORDER BY id ASC
        """, rowMapper(), workflowInstanceId);
  }

  /**
   * 构建 {@link ToolCallLog} 的行映射器，用于将查询结果集映射为实体对象。
   *
   * @return 行映射器实例
   */
  private RowMapper<ToolCallLog> rowMapper() {
    return (rs, rowNum) -> new ToolCallLog(
        rs.getLong("id"),
        rs.getLong("workflow_instance_id"),
        rs.getString("node_name"),
        rs.getString("tool_name"),
        rs.getString("request_json"),
        rs.getString("response_json"),
        rs.getBoolean("success"),
        rs.getString("error_message"),
        rs.getObject("duration_ms", Long.class),
        toLocalDateTime(rs.getTimestamp("created_at"))
    );
  }

  /**
   * 将 {@link Timestamp} 转换为 {@link LocalDateTime}，处理空值。
   *
   * @param timestamp SQL 时间戳，可能为 null
   * @return 转换后的 LocalDateTime，若入参为 null 则返回 null
   */
  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
