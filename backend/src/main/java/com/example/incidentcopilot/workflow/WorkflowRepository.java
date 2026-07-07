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

/**
 * 工作流实例和节点执行记录的数据访问层。
 *
 * <p>工作流引擎每执行一个节点都会通过这里更新 current_node、节点状态、输入输出和耗时。</p>
 */
@Repository
public class WorkflowRepository {
  private final JdbcTemplate jdbcTemplate;

  /**
   * 构造函数。
   *
   * @param jdbcTemplate Spring JDBC 模板
   */
  public WorkflowRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 创建一条新的工作流实例记录，初始状态为 CREATED。
   *
   * @param incidentId 关联的事件 ID
   * @return 新创建的工作流实例
   */
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

  /**
   * 根据 ID 查询工作流实例。
   *
   * @param id 工作流实例 ID
   * @return 工作流实例的 Optional
   */
  public Optional<WorkflowInstance> findInstance(Long id) {
    List<WorkflowInstance> instances = jdbcTemplate.query(
        "SELECT * FROM workflow_instance WHERE id = ?",
        workflowInstanceRowMapper(),
        id
    );
    return instances.stream().findFirst();
  }

  /**
   * 查询指定事件关联的所有工作流实例，按创建时间升序排列。
   *
   * @param incidentId 事件 ID
   * @return 工作流实例列表
   */
  public List<WorkflowInstance> findByIncident(Long incidentId) {
    return jdbcTemplate.query("""
        SELECT * FROM workflow_instance
        WHERE incident_id = ?
        ORDER BY id ASC
        """, workflowInstanceRowMapper(), incidentId);
  }

  /**
   * 查询指定事件最近一次关联的工作流实例。
   *
   * @param incidentId 事件 ID
   * @return 最后一条工作流实例的 Optional
   */
  public Optional<WorkflowInstance> findLatestByIncident(Long incidentId) {
    List<WorkflowInstance> instances = jdbcTemplate.query("""
        SELECT * FROM workflow_instance
        WHERE incident_id = ?
        ORDER BY id DESC
        LIMIT 1
        """, workflowInstanceRowMapper(), incidentId);
    return instances.stream().findFirst();
  }

  /**
   * 查询指定工作流实例的所有节点执行记录，按创建时间升序排列。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @return 节点执行记录列表
   */
  public List<WorkflowNodeExecution> findNodeExecutions(Long workflowInstanceId) {
    return jdbcTemplate.query("""
        SELECT * FROM workflow_node_execution
        WHERE workflow_instance_id = ?
        ORDER BY id ASC
        """, workflowNodeExecutionRowMapper(), workflowInstanceId);
  }

  /**
   * 将工作流实例状态更新为 RUNNING，并设置当前节点和开始时间。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @param currentNode        首个执行的节点名称
   */
  public void markRunning(Long workflowInstanceId, String currentNode) {
    jdbcTemplate.update("""
        UPDATE workflow_instance
        SET status = 'RUNNING', current_node = ?, started_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, currentNode, workflowInstanceId);
  }

  /**
   * 更新工作流实例的当前执行节点。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @param currentNode        当前正在执行的节点名称
   */
  public void updateCurrentNode(Long workflowInstanceId, String currentNode) {
    jdbcTemplate.update(
        "UPDATE workflow_instance SET current_node = ? WHERE id = ?",
        currentNode,
        workflowInstanceId
    );
  }

  /**
   * 将工作流实例标记为已成功（SUCCESS），清空当前节点并设置结束时间。
   *
   * @param workflowInstanceId 工作流实例 ID
   */
  public void markSuccess(Long workflowInstanceId) {
    jdbcTemplate.update("""
        UPDATE workflow_instance
        SET status = 'SUCCESS', current_node = NULL, finished_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, workflowInstanceId);
  }

  /**
   * 将工作流实例标记为指定状态（如 WAITING_APPROVAL），清空当前节点并设置结束时间。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @param status             最终状态
   */
  public void markFinished(Long workflowInstanceId, String status) {
    jdbcTemplate.update("""
        UPDATE workflow_instance
        SET status = ?, current_node = NULL, finished_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, status, workflowInstanceId);
  }

  /**
   * 将工作流实例标记为失败（FAILED），设置当前节点和结束时间。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @param currentNode        失败时的当前节点名称，可为 null
   */
  public void markFailed(Long workflowInstanceId, String currentNode) {
    jdbcTemplate.update("""
        UPDATE workflow_instance
        SET status = 'FAILED', current_node = ?, finished_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, currentNode, workflowInstanceId);
  }

  /**
   * 创建一条节点执行记录，初始状态为 RUNNING。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @param nodeName           节点名称
   * @param nodeType           节点类型
   * @param inputJson          序列化后的输入 JSON
   * @return 新创建的节点执行记录 ID
   */
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

  /**
   * 将节点执行记录标记为成功（SUCCESS），并记录输出和耗时。
   *
   * @param nodeExecutionId 节点执行记录 ID
   * @param outputJson      序列化后的输出 JSON
   * @param durationMs      执行耗时（毫秒）
   */
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

  /**
   * 将节点执行记录标记为失败（FAILED），并记录错误信息和耗时。
   *
   * @param nodeExecutionId 节点执行记录 ID
   * @param errorMessage    失败错误信息
   * @param durationMs      执行耗时（毫秒）
   */
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

  /**
   * 返回 WorkflowInstance 的行映射器。
   *
   * @return RowMapper 实例
   */
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

  /**
   * 返回 WorkflowNodeExecution 的行映射器。
   *
   * @return RowMapper 实例
   */
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

  /**
   * 将 java.sql.Timestamp 安全转换为 LocalDateTime，处理 null。
   *
   * @param timestamp SQL Timestamp，可能为 null
   * @return LocalDateTime 或 null
   */
  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
