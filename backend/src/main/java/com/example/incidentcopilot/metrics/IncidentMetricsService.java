package com.example.incidentcopilot.metrics;

import com.example.incidentcopilot.action.ActionProposal;
import com.example.incidentcopilot.common.DomainConstants.MetricStatus;
import com.example.incidentcopilot.common.DomainConstants.RiskLevel;
import com.example.incidentcopilot.incident.Incident;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * 故障指标快照服务。
 *
 * <p>负责在故障生命周期各阶段（创建、告警、工作流、恢复中、已恢复）记录和查询指标快照。
 * 当前 MVP 使用 {@code mock_metric_snapshot} 表模拟 Grafana/Prometheus 指标，后续可替换为真实指标源。</p>
 */
@Service
public class IncidentMetricsService {
  /** Spring JDBC 模板，用于执行指标快照的 SQL 操作 */
  private final JdbcTemplate jdbcTemplate;

  public IncidentMetricsService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 记录故障创建时的初始指标快照（降级状态）。
   *
   * @param incident 关联的故障单
   */
  public void recordInitialSnapshot(Incident incident) {
    insertSnapshot(incident, new BigDecimal("0.0820"), 3200, 1260, MetricStatus.DEGRADED);
  }

  /**
   * 记录告警入站时的指标快照，优先保留上游携带的指标值。
   *
   * @param incident   关联的故障单
   * @param errorRate  告警时的错误率，为空时使用默认值
   * @param p95Latency 告警时的 P95 延迟，为空时使用默认值
   * @param qps        告警时的 QPS，为空时使用默认值
   */
  public void recordAlertSnapshot(Incident incident, BigDecimal errorRate, Integer p95Latency, Integer qps) {
    insertSnapshot(
        incident,
        errorRate == null ? new BigDecimal("0.0200") : errorRate,
        p95Latency == null ? 1000 : p95Latency,
        qps == null ? 1000 : qps,
        MetricStatus.DEGRADED
    );
  }

  /**
   * 记录工作流执行时的指标快照。
   *
   * <p>当前 MVP 使用模拟指标值，后续可替换为从真实指标源读取。</p>
   *
   * @param incident 关联的故障单
   * @return 更新后最新的指标快照
   */
  public MetricSnapshot recordWorkflowSnapshot(Incident incident) {
    // 当前 MVP 使用 mock_metric_snapshot 模拟 Grafana/Prometheus 指标，
    // 后续替换真实指标源时，保留这里的快照语义即可。
    insertSnapshot(incident, new BigDecimal("0.0760"), 2850, 1210, MetricStatus.DEGRADED);
    return findLatestSnapshots(incident.id(), 1).getFirst();
  }

  /**
   * 记录故障正在恢复时的指标快照，根据风险等级选择不同的模拟指标值。
   *
   * @param incident 关联的故障单
   * @param proposal 操作建议，用于判断风险等级
   */
  public void recordRecoveringSnapshot(Incident incident, ActionProposal proposal) {
    switch (proposal.riskLevel()) {
      case RiskLevel.LOW -> insertSnapshot(incident, new BigDecimal("0.0520"), 2100, 1180, MetricStatus.RECOVERING);
      case RiskLevel.HIGH -> insertSnapshot(incident, new BigDecimal("0.0060"), 420, 1040, MetricStatus.RECOVERING);
      default -> insertSnapshot(incident, new BigDecimal("0.0180"), 760, 1160, MetricStatus.RECOVERING);
    }
  }

  /**
   * 记录故障已恢复时的指标快照（恢复状态）。
   *
   * @param incident 关联的故障单
   */
  public void recordRecoveredSnapshot(Incident incident) {
    insertSnapshot(incident, new BigDecimal("0.0020"), 180, 1180, MetricStatus.RECOVERED);
  }

  /**
   * 查询指定故障单的最新指标快照列表。
   *
   * @param incidentId 故障单 ID
   * @param limit      最多返回的记录数
   * @return 指标快照列表，按时间倒序排列
   */
  public List<MetricSnapshot> findLatestSnapshots(Long incidentId, int limit) {
    return jdbcTemplate.query("""
        SELECT * FROM mock_metric_snapshot
        WHERE incident_id = ?
        ORDER BY snapshot_time DESC, id DESC
        LIMIT ?
        """, metricRowMapper(), incidentId, Math.max(1, limit));
  }

  private void insertSnapshot(
      Incident incident,
      BigDecimal errorRate,
      int p95Latency,
      int qps,
      String status
  ) {
    jdbcTemplate.update("""
        INSERT INTO mock_metric_snapshot (
          incident_id, service_name, error_rate, p95_latency, qps, status
        ) VALUES (?, ?, ?, ?, ?, ?)
        """, incident.id(), incident.serviceName(), errorRate, p95Latency, qps, status);
  }

  private RowMapper<MetricSnapshot> metricRowMapper() {
    return (rs, rowNum) -> new MetricSnapshot(
        rs.getLong("id"),
        rs.getLong("incident_id"),
        rs.getString("service_name"),
        rs.getBigDecimal("error_rate"),
        rs.getInt("p95_latency"),
        rs.getInt("qps"),
        rs.getString("status"),
        rs.getTimestamp("snapshot_time").toLocalDateTime()
    );
  }
}
