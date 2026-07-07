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

/**
 * 告警事件数据访问层。
 *
 * <p>使用 JdbcTemplate 对 alert_event 表进行 CRUD 操作，包括创建告警事件、
 * 按 ID/EventId/IncidentId 查询、以及标注忽略/关联/创建故障单等状态变更。</p>
 */
@Repository
public class AlertEventRepository {
  /** Spring JDBC 模板，用于执行 SQL 操作。 */
  private final JdbcTemplate jdbcTemplate;

  /**
   * 构造仓库实例。
   *
   * @param jdbcTemplate JDBC 模板
   */
  public AlertEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 创建一条新的告警事件记录。
   *
   * @param request        告警入站请求
   * @param rawPayloadJson 原始 webhook 负载 JSON
   * @return 创建完成的告警事件，包含自增主键
   */
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

  /**
   * 根据主键 ID 查询告警事件。
   *
   * @param id 主键 ID
   * @return 包含告警事件的 Optional
   */
  public Optional<AlertEvent> findById(Long id) {
    List<AlertEvent> events = jdbcTemplate.query(
        "SELECT * FROM alert_event WHERE id = ?",
        rowMapper(),
        id
    );
    return events.stream().findFirst();
  }

  /**
   * 根据外部系统事件 ID 查询告警事件（用于幂等去重）。
   *
   * @param eventId 外部系统的告警事件唯一标识
   * @return 包含告警事件的 Optional
   */
  public Optional<AlertEvent> findByEventId(String eventId) {
    List<AlertEvent> events = jdbcTemplate.query(
        "SELECT * FROM alert_event WHERE event_id = ?",
        rowMapper(),
        eventId
    );
    return events.stream().findFirst();
  }

  /**
   * 查询指定故障单关联的所有告警事件。
   *
   * <p>结果按接收时间和主键降序排列。</p>
   *
   * @param incidentId 故障单 ID
   * @return 告警事件列表
   */
  public List<AlertEvent> findByIncident(Long incidentId) {
    return jdbcTemplate.query("""
        SELECT * FROM alert_event
        WHERE incident_id = ?
        ORDER BY received_at DESC, id DESC
        """, rowMapper(), incidentId);
  }

  /**
   * 将告警事件标记为忽略。
   *
   * @param id     告警事件 ID
   * @param reason 忽略原因
   * @return 更新后的告警事件
   */
  public AlertEvent markIgnored(Long id, String reason) {
    jdbcTemplate.update("""
        UPDATE alert_event
        SET status = 'IGNORED', decision_reason = ?
        WHERE id = ?
        """, reason, id);
    return findById(id).orElseThrow();
  }

  /**
   * 将告警事件标记为已创建故障单。
   *
   * @param id         告警事件 ID
   * @param incidentId 新创建的故障单 ID
   * @param reason     关联原因
   * @return 更新后的告警事件
   */
  public AlertEvent markIncidentCreated(Long id, Long incidentId, String reason) {
    jdbcTemplate.update("""
        UPDATE alert_event
        SET status = 'INCIDENT_CREATED', incident_id = ?, decision_reason = ?
        WHERE id = ?
        """, incidentId, reason, id);
    return findById(id).orElseThrow();
  }

  /**
   * 将告警事件标记为已关联到现有故障单。
   *
   * @param id         告警事件 ID
   * @param incidentId 关联的故障单 ID
   * @param reason     关联原因
   * @return 更新后的告警事件
   */
  public AlertEvent markCorrelated(Long id, Long incidentId, String reason) {
    jdbcTemplate.update("""
        UPDATE alert_event
        SET status = 'CORRELATED', incident_id = ?, decision_reason = ?
        WHERE id = ?
        """, incidentId, reason, id);
    return findById(id).orElseThrow();
  }

  /**
   * 设置可空的整型参数。
   *
   * @param statement SQL 预编译语句
   * @param index     参数索引（从 1 开始）
   * @param value     整型值，可为 null
   * @throws java.sql.SQLException SQL 异常
   */
  private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws java.sql.SQLException {
    if (value == null) {
      statement.setObject(index, null);
    } else {
      statement.setInt(index, value);
    }
  }

  /**
   * 创建告警事件的行映射器。
   *
   * @return RowMapper 实例
   */
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

  /**
   * 将可能为 null 的 Object 转换为 Long。
   *
   * @param value 数据库查询结果中的对象
   * @return Long 值，或 null
   */
  private Long nullableLong(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  /**
   * 将可能为 null 的 Object 转换为 Integer。
   *
   * @param value 数据库查询结果中的对象
   * @return Integer 值，或 null
   */
  private Integer nullableInteger(Object value) {
    return value == null ? null : ((Number) value).intValue();
  }

  /**
   * 将 java.sql.Timestamp 转换为 LocalDateTime。
   *
   * @param timestamp SQL 时间戳，可为 null
   * @return LocalDateTime 值，或 null
   */
  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
