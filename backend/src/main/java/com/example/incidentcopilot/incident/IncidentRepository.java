package com.example.incidentcopilot.incident;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class IncidentRepository {
  private static final DateTimeFormatter INCIDENT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final JdbcTemplate jdbcTemplate;

  public IncidentRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Incident create(IncidentCreateRequest request) {
    String incidentNo = nextIncidentNo();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO incident (
            incident_no, title, service_name, endpoint, severity, status, source,
            trace_id, exception_type, summary
          ) VALUES (?, ?, ?, ?, 'P2', 'OPEN', ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setString(1, incidentNo);
      statement.setString(2, request.title());
      statement.setString(3, request.serviceName());
      statement.setString(4, request.endpoint());
      statement.setString(5, defaultSource(request.source()));
      statement.setString(6, request.traceId());
      statement.setString(7, request.exceptionType());
      statement.setString(8, request.summary());
      return statement;
    }, keyHolder);
    Number key = keyHolder.getKey();
    return findById(key.longValue()).orElseThrow();
  }

  public List<Incident> findAll(String status, String serviceName, String severity, int page, int size) {
    StringBuilder sql = new StringBuilder("SELECT * FROM incident WHERE 1 = 1");
    List<Object> args = new ArrayList<>();
    if (hasText(status)) {
      sql.append(" AND status = ?");
      args.add(status);
    }
    if (hasText(serviceName)) {
      sql.append(" AND service_name = ?");
      args.add(serviceName);
    }
    if (hasText(severity)) {
      sql.append(" AND severity = ?");
      args.add(severity);
    }
    sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
    int normalizedSize = Math.max(1, Math.min(size, 100));
    int normalizedPage = Math.max(0, page);
    args.add(normalizedSize);
    args.add(normalizedPage * normalizedSize);
    return jdbcTemplate.query(sql.toString(), incidentRowMapper(), args.toArray());
  }

  public Optional<Incident> findById(Long id) {
    List<Incident> incidents = jdbcTemplate.query(
        "SELECT * FROM incident WHERE id = ?",
        incidentRowMapper(),
        id
    );
    return incidents.stream().findFirst();
  }

  public Incident updateStatus(Long id, String status) {
    jdbcTemplate.update("UPDATE incident SET status = ? WHERE id = ?", status, id);
    return findById(id).orElseThrow();
  }

  public Incident close(Long id) {
    jdbcTemplate.update("""
        UPDATE incident
        SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, id);
    return findById(id).orElseThrow();
  }

  public Incident updateWorkflowRunning(Long id) {
    return updateStatus(id, "WORKFLOW_RUNNING");
  }

  private String nextIncidentNo() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    return "INC-" + LocalDate.now().format(INCIDENT_DATE) + "-" + suffix;
  }

  private String defaultSource(String source) {
    return hasText(source) ? source : "MANUAL";
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private RowMapper<Incident> incidentRowMapper() {
    return (rs, rowNum) -> new Incident(
        rs.getLong("id"),
        rs.getString("incident_no"),
        rs.getString("title"),
        rs.getString("service_name"),
        rs.getString("endpoint"),
        rs.getString("severity"),
        rs.getString("status"),
        rs.getString("source"),
        rs.getString("trace_id"),
        rs.getString("exception_type"),
        rs.getString("summary"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime(),
        toLocalDateTime(rs.getTimestamp("closed_at"))
    );
  }

  private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
