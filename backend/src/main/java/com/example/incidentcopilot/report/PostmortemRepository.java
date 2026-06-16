package com.example.incidentcopilot.report;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostmortemRepository {
  private final JdbcTemplate jdbcTemplate;

  public PostmortemRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void upsert(
      Long incidentId,
      String summary,
      String rootCause,
      String impact,
      String timelineJson,
      String actionItemsJson,
      String preventionItemsJson,
      String reportContent
  ) {
    jdbcTemplate.update("""
        INSERT INTO postmortem_report (
          incident_id, summary, root_cause, impact, timeline_json, action_items_json,
          prevention_items_json, report_content
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          summary = VALUES(summary),
          root_cause = VALUES(root_cause),
          impact = VALUES(impact),
          timeline_json = VALUES(timeline_json),
          action_items_json = VALUES(action_items_json),
          prevention_items_json = VALUES(prevention_items_json),
          report_content = VALUES(report_content)
        """, incidentId, summary, rootCause, impact, timelineJson, actionItemsJson, preventionItemsJson, reportContent);
  }

  public Optional<PostmortemReport> findByIncident(Long incidentId) {
    List<PostmortemReport> reports = jdbcTemplate.query(
        "SELECT * FROM postmortem_report WHERE incident_id = ?",
        rowMapper(),
        incidentId
    );
    return reports.stream().findFirst();
  }

  private RowMapper<PostmortemReport> rowMapper() {
    return (rs, rowNum) -> new PostmortemReport(
        rs.getLong("id"),
        rs.getLong("incident_id"),
        rs.getString("summary"),
        rs.getString("root_cause"),
        rs.getString("impact"),
        rs.getString("timeline_json"),
        rs.getString("action_items_json"),
        rs.getString("prevention_items_json"),
        rs.getString("report_content"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        toLocalDateTime(rs.getTimestamp("updated_at"))
    );
  }

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
