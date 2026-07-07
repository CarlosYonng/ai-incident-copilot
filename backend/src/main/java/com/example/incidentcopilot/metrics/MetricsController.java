package com.example.incidentcopilot.metrics;

import com.example.incidentcopilot.common.ApiResponse;
import com.example.incidentcopilot.incident.IncidentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 故障指标 REST 控制器。
 *
 * <p>当前返回演示指标快照；后续接真实监控源时应保持响应结构稳定。</p>
 */
@RestController
public class MetricsController {
  /** 故障单应用服务 */
  private final IncidentService incidentService;
  /** 故障指标快照服务 */
  private final IncidentMetricsService incidentMetricsService;

  public MetricsController(IncidentService incidentService, IncidentMetricsService incidentMetricsService) {
    this.incidentService = incidentService;
    this.incidentMetricsService = incidentMetricsService;
  }

  /**
   * 查询指定故障单的指标快照列表。
   *
   * @param incidentId 故障单 ID
   * @return 指标快照列表，最多返回 20 条，按时间倒序排列
   */
  @GetMapping("/api/incidents/{incidentId}/metrics")
  public ApiResponse<List<MetricSnapshot>> listByIncident(@PathVariable Long incidentId) {
    incidentService.findRequired(incidentId);
    return ApiResponse.ok(incidentMetricsService.findLatestSnapshots(incidentId, 20));
  }
}
