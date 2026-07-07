package com.example.incidentcopilot.action;

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
 * 处置方案、人工审批和线下执行记录的数据访问层。
 *
 * <p>该 Repository 覆盖三类表写入，便于一次人工动作在同一事务中留下完整审计链路。</p>
 */
@Repository
public class ActionProposalRepository {
  private final JdbcTemplate jdbcTemplate;

  /**
   * 构造 Repository，注入 JDBC 模板。
   *
   * @param jdbcTemplate Spring JDBC 模板
   */
  public ActionProposalRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 创建一条新的处置方案记录。
   *
   * @param incidentId         故障单 ID
   * @param workflowInstanceId 工作流实例 ID
   * @param title              方案标题
   * @param actionType         动作编码
   * @param riskLevel          风险等级
   * @param reason             推荐原因
   * @param evidenceJson       证据 JSON
   * @param impact             预期影响
   * @param precheck           预检查项
   * @param requiresApproval   是否需要审批
   * @param status             初始状态
   * @return 创建成功的处置方案
   */
  public ActionProposal create(
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
      String status
  ) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO action_proposal (
            incident_id, workflow_instance_id, title, action_type, risk_level, reason,
            evidence_json, impact, precheck, requires_approval, status
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setLong(1, incidentId);
      statement.setLong(2, workflowInstanceId);
      statement.setString(3, title);
      statement.setString(4, actionType);
      statement.setString(5, riskLevel);
      statement.setString(6, reason);
      statement.setString(7, evidenceJson);
      statement.setString(8, impact);
      statement.setString(9, precheck);
      statement.setBoolean(10, requiresApproval);
      statement.setString(11, status);
      return statement;
    }, keyHolder);
    return findById(keyHolder.getKey().longValue()).orElseThrow();
  }

  /**
   * 根据主键查询处置方案。
   *
   * @param id 方案主键 ID
   * @return 包含处置方案的 Optional
   */
  public Optional<ActionProposal> findById(Long id) {
    List<ActionProposal> proposals = jdbcTemplate.query(
        "SELECT * FROM action_proposal WHERE id = ?",
        rowMapper(),
        id
    );
    return proposals.stream().findFirst();
  }

  /**
   * 查询指定故障单的所有处置方案，按风险等级和 ID 排序。
   *
   * @param incidentId 故障单 ID
   * @return 处置方案列表
   */
  public List<ActionProposal> findByIncident(Long incidentId) {
    return jdbcTemplate.query("""
        SELECT * FROM action_proposal
        WHERE incident_id = ?
        ORDER BY FIELD(risk_level, 'LOW', 'MEDIUM', 'HIGH'), id ASC
        """, rowMapper(), incidentId);
  }

  /**
   * 查询指定工作流实例的所有处置方案。
   *
   * @param workflowInstanceId 工作流实例 ID
   * @return 处置方案列表
   */
  public List<ActionProposal> findByWorkflow(Long workflowInstanceId) {
    return jdbcTemplate.query("""
        SELECT * FROM action_proposal
        WHERE workflow_instance_id = ?
        ORDER BY id ASC
        """, rowMapper(), workflowInstanceId);
  }

  /**
   * 查询指定故障单下已线下执行的处置方案。
   *
   * @param incidentId 故障单 ID
   * @return 已执行的处置方案列表
   */
  public List<ActionProposal> findExecutedByIncident(Long incidentId) {
    return jdbcTemplate.query("""
        SELECT * FROM action_proposal
        WHERE incident_id = ? AND status = 'OFFLINE_EXECUTED'
        ORDER BY updated_at DESC, id DESC
        """, rowMapper(), incidentId);
  }

  /**
   * 更新处置方案的状态。
   *
   * @param id     方案 ID
   * @param status 目标状态
   */
  public void updateStatus(Long id, String status) {
    jdbcTemplate.update("UPDATE action_proposal SET status = ? WHERE id = ?", status, id);
  }

  /**
   * 将指定故障单下除了选中方案以外的其他待选方案标记为未选中。
   *
   * @param incidentId       故障单 ID
   * @param selectedActionId 被选中的方案 ID
   */
  public void markUnselectedActions(Long incidentId, Long selectedActionId) {
    jdbcTemplate.update("""
        UPDATE action_proposal
        SET status = 'NOT_SELECTED'
        WHERE incident_id = ?
          AND id <> ?
          AND status IN ('READY', 'PENDING', 'APPROVED', 'ESCALATED')
        """, incidentId, selectedActionId);
  }

  /**
   * 创建人工审批记录。
   *
   * @param actionProposalId 方案 ID
   * @param incidentId       故障单 ID
   * @param decision         审批决策
   * @param comment          审批备注
   * @param approvedBy       审批人
   */
  public void createApproval(Long actionProposalId, Long incidentId, String decision, String comment, String approvedBy) {
    jdbcTemplate.update("""
        INSERT INTO human_approval (action_proposal_id, incident_id, decision, comment, approved_by)
        VALUES (?, ?, ?, ?, ?)
        """, actionProposalId, incidentId, decision, comment, approvedBy);
  }

  /**
   * 创建线下执行记录。
   *
   * @param incidentId       故障单 ID
   * @param actionProposalId 方案 ID
   * @param actionType       动作编码
   * @param executor         执行人
   * @param result           执行结果
   * @param resultDetail     执行结果详情
   */
  public void createActionRecord(
      Long incidentId,
      Long actionProposalId,
      String actionType,
      String executor,
      String result,
      String resultDetail
  ) {
    jdbcTemplate.update("""
        INSERT INTO action_record (
          incident_id, action_proposal_id, action_type, executor, result, result_detail
        ) VALUES (?, ?, ?, ?, ?, ?)
        """, incidentId, actionProposalId, actionType, executor, result, resultDetail);
  }

  /**
   * 创建结果集到 ActionProposal 记录的行映射器。
   *
   * @return RowMapper 实例
   */
  private RowMapper<ActionProposal> rowMapper() {
    return (rs, rowNum) -> new ActionProposal(
        rs.getLong("id"),
        rs.getLong("incident_id"),
        rs.getLong("workflow_instance_id"),
        rs.getString("title"),
        rs.getString("action_type"),
        rs.getString("risk_level"),
        rs.getString("reason"),
        rs.getString("evidence_json"),
        rs.getString("impact"),
        rs.getString("precheck"),
        rs.getBoolean("requires_approval"),
        rs.getString("status"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        toLocalDateTime(rs.getTimestamp("updated_at"))
    );
  }

  /**
   * 将 java.sql.Timestamp 转换为 LocalDateTime，处理 null 值。
   *
   * @param timestamp SQL Timestamp，可为 null
   * @return LocalDateTime 或 null
   */
  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
