package com.example.incidentcopilot.alert;

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
public class AlertEventRepository {
  private final JdbcTemplate jdbcTemplate;

  public AlertEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public AlertEvent create(AlertIngestRequest request, String rawPayloadJson) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO alert_event (
            event_id, source, signal_name, service_name, endpoint, trace_id, exception_type,
            summary, error_rate, p95_latency, qps, affected_requests, severity_hint, raw_payload_json
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setString(1, request.eventId());
      statement.setString(2, request.source());
      statement.setString(3, request.signalName());
      statement.setString(4, request.serviceName());
      statement.setString(5, request.endpoint());
      statement.setString(6, request.traceId());
      statement.setString(7, request.exceptionType());
      statement.setString(8, request.summary());
      statement.setBigDecimal(9, request.errorRate());
      setNullableInteger(statement, 10, request.p95Latency());
      setNullableInteger(statement, 11, request.qps());
      setNullableInteger(statement, 12, request.affectedRequests());
      statement.setString(13, request.severityHint());
      statement.setString(14, rawPayloadJson);
      return statement;
    }, keyHolder);
    return findById(keyHolder.getKey().longValue()).orElseThrow();
  }

  public Optional<AlertEvent> findById(Long id) {
    List<AlertEvent> events = jdbcTemplate.query(
        "SELECT * FROM alert_event WHERE id = ?",
        rowMapper(),
        id
    );
    return events.stream().findFirst();
  }

  public Optional<AlertEvent> findByEventId(String eventId) {
    List<AlertEvent> events = jdbcTemplate.query(
        "SELECT * FROM alert_event WHERE event_id = ?",
        rowMapper(),
        eventId
    );
    return events.stream().findFirst();
  }

  public List<AlertEvent> findByIncident(Long incidentId) {
    return jdbcTemplate.query("""
        SELECT * FROM alert_event
        WHERE incident_id = ?
        ORDER BY received_at DESC, id DESC
        """, rowMapper(), incidentId);
  }

  public AlertEvent markIgnored(Long id, String reason) {
    jdbcTemplate.update("""
        UPDATE alert_event
        SET status = 'IGNORED', decision_reason = ?
        WHERE id = ?
        """, reason, id);
    return findById(id).orElseThrow();
  }

  public AlertEvent markIncidentCreated(Long id, Long incidentId, String reason) {
    jdbcTemplate.update("""
        UPDATE alert_event
        SET status = 'INCIDENT_CREATED', incident_id = ?, decision_reason = ?
        WHERE id = ?
        """, incidentId, reason, id);
    return findById(id).orElseThrow();
  }

  public AlertEvent markCorrelated(Long id, Long incidentId, String reason) {
    jdbcTemplate.update("""
        UPDATE alert_event
        SET status = 'CORRELATED', incident_id = ?, decision_reason = ?
        WHERE id = ?
        """, incidentId, reason, id);
    return findById(id).orElseThrow();
  }

  private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws java.sql.SQLException {
    if (value == null) {
      statement.setObject(index, null);
    } else {
      statement.setInt(index, value);
    }
  }

  private RowMapper<AlertEvent> rowMapper() {
    return (rs, rowNum) -> new AlertEvent(
        rs.getLong("id"),
        rs.getString("event_id"),
        nullableLong(rs.getObject("incident_id")),
        rs.getString("source"),
        rs.getString("signal_name"),
        rs.getString("service_name"),
        rs.getString("endpoint"),
        rs.getString("trace_id"),
        rs.getString("exception_type"),
        rs.getString("summary"),
        rs.getBigDecimal("error_rate"),
        nullableInteger(rs.getObject("p95_latency")),
        nullableInteger(rs.getObject("qps")),
        nullableInteger(rs.getObject("affected_requests")),
        rs.getString("severity_hint"),
        rs.getString("raw_payload_json"),
        rs.getString("status"),
        rs.getString("decision_reason"),
        toLocalDateTime(rs.getTimestamp("received_at")),
        toLocalDateTime(rs.getTimestamp("created_at"))
    );
  }

  private Long nullableLong(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  private Integer nullableInteger(Object value) {
    return value == null ? null : ((Number) value).intValue();
  }

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
