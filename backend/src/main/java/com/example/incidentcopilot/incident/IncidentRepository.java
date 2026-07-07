package com.example.incidentcopilot.incident;

import com.example.incidentcopilot.common.DomainConstants.IncidentStatus;
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

/**
 * 故障单数据访问层。
 *
 * <p>封装 incident 表 SQL 操作和简单查询条件拼装，业务状态判断放在 IncidentService 或工作流节点中。</p>
 */
@Repository
public class IncidentRepository {
  /** 故障编号日期格式，格式为 yyyyMMdd */
  private static final DateTimeFormatter INCIDENT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

  /** Spring JDBC 模板，用于执行 SQL 操作 */
  private final JdbcTemplate jdbcTemplate;

  public IncidentRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 创建一条新的故障单记录。
   *
   * @param request 故障创建请求，包含标题、受影响服务、来源等必要信息
   * @return 创建完成后的完整故障单对象
   */
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

  /**
   * 查询故障单列表，支持按状态、服务名、严重等级筛选及分页。
   *
   * @param status      过滤条件：故障状态（可选）
   * @param serviceName 过滤条件：受影响服务（可选）
   * @param severity    过滤条件：严重等级（可选）
   * @param page        分页页码，从 0 开始
   * @param size        每页记录数
   * @return 符合条件的所有故障单列表
   */
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

  /**
   * 根据主键 ID 查找故障单。
   *
   * @param id 故障单 ID
   * @return 故障单的 Optional，不存在时返回 Optional.empty()
   */
  public Optional<Incident> findById(Long id) {
    List<Incident> incidents = jdbcTemplate.query(
        "SELECT * FROM incident WHERE id = ?",
        incidentRowMapper(),
        id
    );
    return incidents.stream().findFirst();
  }

  /**
   * 查找指定服务下处于活跃（未关闭）状态的关联故障单。
   *
   * <p>优先使用 traceId 关联，其次使用 endpoint 关联。只要故障未关闭，就允许新告警关联进来。</p>
   *
   * @param serviceName 受影响服务名
   * @param endpoint    受影响接口或任务名
   * @param traceId     链路追踪 ID
   * @return 活跃的故障单 Optional，不存在时返回 Optional.empty()
   */
  public Optional<Incident> findActiveByCorrelation(String serviceName, String endpoint, String traceId) {
    // 活跃故障关联优先使用 traceId，其次使用 endpoint；只要故障未关闭，就允许新告警关联进来。
    List<Incident> incidents = jdbcTemplate.query("""
        SELECT * FROM incident
        WHERE service_name = ?
          AND status <> 'CLOSED'
          AND (
            (? IS NOT NULL AND trace_id = ?)
            OR (? IS NOT NULL AND endpoint = ?)
          )
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        """,
        incidentRowMapper(),
        serviceName,
        traceId,
        traceId,
        endpoint,
        endpoint
    );
    return incidents.stream().findFirst();
  }

  /**
   * 更新故障单状态。
   *
   * @param id     故障单 ID
   * @param status 新状态值
   * @return 更新后的完整故障单对象
   */
  public Incident updateStatus(Long id, String status) {
    jdbcTemplate.update("UPDATE incident SET status = ? WHERE id = ?", status, id);
    return findById(id).orElseThrow();
  }

  /**
   * 更新故障单严重等级。
   *
   * @param id       故障单 ID
   * @param severity 新的严重等级（如 P0/P1/P2/P3）
   * @return 更新后的完整故障单对象
   */
  public Incident updateSeverity(Long id, String severity) {
    jdbcTemplate.update("UPDATE incident SET severity = ? WHERE id = ?", severity, id);
    return findById(id).orElseThrow();
  }

  /**
   * 关闭故障单，将状态设为 CLOSED 并记录关闭时间。
   *
   * @param id 故障单 ID
   * @return 更新后的完整故障单对象
   */
  public Incident close(Long id) {
    jdbcTemplate.update("""
        UPDATE incident
        SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, id);
    return findById(id).orElseThrow();
  }

  /**
   * 将故障单状态更新为工作流运行中。
   *
   * @param id 故障单 ID
   * @return 更新后的完整故障单对象
   */
  public Incident updateWorkflowRunning(Long id) {
    return updateStatus(id, IncidentStatus.WORKFLOW_RUNNING);
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
