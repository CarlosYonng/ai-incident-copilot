package com.example.incidentcopilot.workflow;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowRepository {
  private final JdbcTemplate jdbcTemplate;

  public WorkflowRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public WorkflowInstance createInstance(Long incidentId) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO workflow_instance (incident_id, workflow_type, status)
          VALUES (?, 'IncidentHandlingWorkflow', 'CREATED')
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setLong(1, incidentId);
      return statement;
    }, keyHolder);
    return findInstance(keyHolder.getKey().longValue()).orElseThrow();
  }

  public Optional<WorkflowInstance> findInstance(Long id) {
    List<WorkflowInstance> instances = jdbcTemplate.query(
        "SELECT * FROM workflow_instance WHERE id = ?",
        workflowInstanceRowMapper(),
        id
    );
    return instances.stream().findFirst();
  }

  public List<WorkflowInstance> findByIncident(Long incidentId) {
    return jdbcTemplate.query("""
        SELECT * FROM workflow_instance
        WHERE incident_id = ?
        ORDER BY id ASC
        """, workflowInstanceRowMapper(), incidentId);
  }

  public Optional<WorkflowInstance> findLatestByIncident(Long incidentId) {
    List<WorkflowInstance> instances = jdbcTemplate.query("""
        SELECT * FROM workflow_instance
        WHERE incident_id = ?
        ORDER BY id DESC
        LIMIT 1
        """, workflowInstanceRowMapper(), incidentId);
    return instances.stream().findFirst();
  }

  public List<WorkflowNodeExecution> findNodeExecutions(Long workflowInstanceId) {
    return jdbcTemplate.query("""
        SELECT * FROM workflow_node_execution
        WHERE workflow_instance_id = ?
        ORDER BY id ASC
        """, workflowNodeExecutionRowMapper(), workflowInstanceId);
  }

  public void markRunning(Long workflowInstanceId, String currentNode) {
    jdbcTemplate.update("""
        UPDATE workflow_instance
        SET status = 'RUNNING', current_node = ?, started_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, currentNode, workflowInstanceId);
  }

  public void updateCurrentNode(Long workflowInstanceId, String currentNode) {
    jdbcTemplate.update(
        "UPDATE workflow_instance SET current_node = ? WHERE id = ?",
        currentNode,
        workflowInstanceId
    );
  }

  public void markSuccess(Long workflowInstanceId) {
    jdbcTemplate.update("""
        UPDATE workflow_instance
        SET status = 'SUCCESS', current_node = NULL, finished_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, workflowInstanceId);
  }

  public void markFinished(Long workflowInstanceId, String status) {
    jdbcTemplate.update("""
        UPDATE workflow_instance
        SET status = ?, current_node = NULL, finished_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, status, workflowInstanceId);
  }

  public void markFailed(Long workflowInstanceId, String currentNode) {
    jdbcTemplate.update("""
        UPDATE workflow_instance
        SET status = 'FAILED', current_node = ?, finished_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, currentNode, workflowInstanceId);
  }

  public Long createNodeExecution(Long workflowInstanceId, String nodeName, String nodeType, String inputJson) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO workflow_node_execution (
            workflow_instance_id, node_name, node_type, status, input_json, started_at
          ) VALUES (?, ?, ?, 'RUNNING', ?, CURRENT_TIMESTAMP)
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setLong(1, workflowInstanceId);
      statement.setString(2, nodeName);
      statement.setString(3, nodeType);
      statement.setString(4, inputJson);
      return statement;
    }, keyHolder);
    return keyHolder.getKey().longValue();
  }

  public void markNodeSuccess(Long nodeExecutionId, String outputJson, long durationMs) {
    jdbcTemplate.update("""
        UPDATE workflow_node_execution
        SET status = 'SUCCESS',
            output_json = ?,
            finished_at = CURRENT_TIMESTAMP,
            duration_ms = ?
        WHERE id = ?
        """, outputJson, durationMs, nodeExecutionId);
  }

  public void markNodeFailed(Long nodeExecutionId, String errorMessage, long durationMs) {
    jdbcTemplate.update("""
        UPDATE workflow_node_execution
        SET status = 'FAILED',
            error_message = ?,
            finished_at = CURRENT_TIMESTAMP,
            duration_ms = ?
        WHERE id = ?
        """, errorMessage, durationMs, nodeExecutionId);
  }

  private RowMapper<WorkflowInstance> workflowInstanceRowMapper() {
    return (rs, rowNum) -> new WorkflowInstance(
        rs.getLong("id"),
        rs.getLong("incident_id"),
        rs.getString("workflow_type"),
        rs.getString("status"),
        rs.getString("current_node"),
        toLocalDateTime(rs.getTimestamp("started_at")),
        toLocalDateTime(rs.getTimestamp("finished_at")),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime()
    );
  }

  private RowMapper<WorkflowNodeExecution> workflowNodeExecutionRowMapper() {
    return (rs, rowNum) -> new WorkflowNodeExecution(
        rs.getLong("id"),
        rs.getLong("workflow_instance_id"),
        rs.getString("node_name"),
        rs.getString("node_type"),
        rs.getString("status"),
        rs.getString("input_json"),
        rs.getString("output_json"),
        rs.getString("error_message"),
        toLocalDateTime(rs.getTimestamp("started_at")),
        toLocalDateTime(rs.getTimestamp("finished_at")),
        rs.getObject("duration_ms", Long.class),
        rs.getTimestamp("created_at").toLocalDateTime()
    );
  }

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
