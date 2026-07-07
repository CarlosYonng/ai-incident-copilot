package com.example.incidentcopilot.report;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 复盘报告数据访问层。
 *
 * <p>每个 Incident 只保留一份复盘报告，重复生成时通过 upsert 覆盖最新内容。</p>
 */
@Repository
public class PostmortemRepository {

  /** Spring JDBC 模板，用于执行 SQL 操作 */
  private final JdbcTemplate jdbcTemplate;

  /**
   * 构造方法，注入 JdbcTemplate。
   *
   * @param jdbcTemplate Spring JDBC 模板
   */
  public PostmortemRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 插入或更新复盘报告（UPSERT）。
   *
   * <p>若指定故障单已存在复盘报告则更新，否则插入新记录。</p>
   *
   * @param incidentId          故障单 ID
   * @param summary             简短故障摘要
   * @param rootCause           根因分析
   * @param impact              用户或业务影响
   * @param timelineJson        序列化后的故障时间线
   * @param actionItemsJson     序列化后的后续行动项
   * @param preventionItemsJson 序列化后的预防改进项
   * @param reportContent       渲染后的复盘正文
   */
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

  /**
   * 根据故障单 ID 查询复盘报告。
   *
   * @param incidentId 故障单 ID
   * @return 复盘报告 Optional，不存在时返回 {@link Optional#empty()}
   */
  public Optional<PostmortemReport> findByIncident(Long incidentId) {
    List<PostmortemReport> reports = jdbcTemplate.query(
        "SELECT * FROM postmortem_report WHERE incident_id = ?",
        rowMapper(),
        incidentId
    );
    return reports.stream().findFirst();
  }

  /**
   * 创建行映射器，将数据库行转为 {@link PostmortemReport} 记录对象。
   *
   * @return 行映射器实例
   */
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

  /**
   * 将 {@link Timestamp} 安全转换为 {@link LocalDateTime}，处理空值。
   *
   * @param timestamp SQL 时间戳，可能为 null
   * @return 转换后的 LocalDateTime，输入为 null 时返回 null
   */
  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
