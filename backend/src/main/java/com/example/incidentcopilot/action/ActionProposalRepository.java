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

@Repository
public class ActionProposalRepository {
  private final JdbcTemplate jdbcTemplate;

  public ActionProposalRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

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

  public Optional<ActionProposal> findById(Long id) {
    List<ActionProposal> proposals = jdbcTemplate.query(
        "SELECT * FROM action_proposal WHERE id = ?",
        rowMapper(),
        id
    );
    return proposals.stream().findFirst();
  }

  public List<ActionProposal> findByIncident(Long incidentId) {
    return jdbcTemplate.query("""
        SELECT * FROM action_proposal
        WHERE incident_id = ?
        ORDER BY FIELD(risk_level, 'LOW', 'MEDIUM', 'HIGH'), id ASC
        """, rowMapper(), incidentId);
  }

  public List<ActionProposal> findByWorkflow(Long workflowInstanceId) {
    return jdbcTemplate.query("""
        SELECT * FROM action_proposal
        WHERE workflow_instance_id = ?
        ORDER BY id ASC
        """, rowMapper(), workflowInstanceId);
  }

  public List<ActionProposal> findExecutedByIncident(Long incidentId) {
    return jdbcTemplate.query("""
        SELECT * FROM action_proposal
        WHERE incident_id = ? AND status = 'OFFLINE_EXECUTED'
        ORDER BY updated_at DESC, id DESC
        """, rowMapper(), incidentId);
  }

  public void updateStatus(Long id, String status) {
    jdbcTemplate.update("UPDATE action_proposal SET status = ? WHERE id = ?", status, id);
  }

  public void markUnselectedActions(Long incidentId, Long selectedActionId) {
    jdbcTemplate.update("""
        UPDATE action_proposal
        SET status = 'NOT_SELECTED'
        WHERE incident_id = ?
          AND id <> ?
          AND status IN ('READY', 'PENDING', 'APPROVED', 'ESCALATED')
        """, incidentId, selectedActionId);
  }

  public void createApproval(Long actionProposalId, Long incidentId, String decision, String comment, String approvedBy) {
    jdbcTemplate.update("""
        INSERT INTO human_approval (action_proposal_id, incident_id, decision, comment, approved_by)
        VALUES (?, ?, ?, ?, ?)
        """, actionProposalId, incidentId, decision, comment, approvedBy);
  }

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

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
