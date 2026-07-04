package com.example.incidentcopilot.metrics;

import com.example.incidentcopilot.action.ActionProposal;
import com.example.incidentcopilot.incident.Incident;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class IncidentMetricsService {
  private final JdbcTemplate jdbcTemplate;

  public IncidentMetricsService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void recordInitialSnapshot(Incident incident) {
    insertSnapshot(incident, new BigDecimal("0.0820"), 3200, 1260, "degraded");
  }

  public void recordAlertSnapshot(Incident incident, BigDecimal errorRate, Integer p95Latency, Integer qps) {
    insertSnapshot(
        incident,
        errorRate == null ? new BigDecimal("0.0200") : errorRate,
        p95Latency == null ? 1000 : p95Latency,
        qps == null ? 1000 : qps,
        "degraded"
    );
  }

  public MetricSnapshot recordWorkflowSnapshot(Incident incident) {
    insertSnapshot(incident, new BigDecimal("0.0760"), 2850, 1210, "degraded");
    return findLatestSnapshots(incident.id(), 1).getFirst();
  }

  public void recordRecoveringSnapshot(Incident incident, ActionProposal proposal) {
    switch (proposal.riskLevel()) {
      case "LOW" -> insertSnapshot(incident, new BigDecimal("0.0520"), 2100, 1180, "recovering");
      case "HIGH" -> insertSnapshot(incident, new BigDecimal("0.0060"), 420, 1040, "recovering");
      default -> insertSnapshot(incident, new BigDecimal("0.0180"), 760, 1160, "recovering");
    }
  }

  public void recordRecoveredSnapshot(Incident incident) {
    insertSnapshot(incident, new BigDecimal("0.0020"), 180, 1180, "recovered");
  }

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
