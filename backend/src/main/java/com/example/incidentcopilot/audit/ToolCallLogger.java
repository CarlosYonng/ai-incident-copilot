package com.example.incidentcopilot.audit;

import com.example.incidentcopilot.common.JdbcJson;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ToolCallLogger {
  private final JdbcTemplate jdbcTemplate;
  private final JdbcJson jdbcJson;

  public ToolCallLogger(JdbcTemplate jdbcTemplate, JdbcJson jdbcJson) {
    this.jdbcTemplate = jdbcTemplate;
    this.jdbcJson = jdbcJson;
  }

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

  public List<ToolCallLog> findByWorkflow(Long workflowInstanceId) {
    return jdbcTemplate.query("""
        SELECT * FROM tool_call_log
        WHERE workflow_instance_id = ?
        ORDER BY id ASC
        """, rowMapper(), workflowInstanceId);
  }

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

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
