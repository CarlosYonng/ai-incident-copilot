package com.example.incidentcopilot.metrics;

import com.example.incidentcopilot.common.ApiResponse;
import com.example.incidentcopilot.incident.IncidentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {
  private final IncidentService incidentService;
  private final IncidentMetricsService incidentMetricsService;

  public MetricsController(IncidentService incidentService, IncidentMetricsService incidentMetricsService) {
    this.incidentService = incidentService;
    this.incidentMetricsService = incidentMetricsService;
  }

  @GetMapping("/api/incidents/{incidentId}/metrics")
  public ApiResponse<List<MetricSnapshot>> listByIncident(@PathVariable Long incidentId) {
    incidentService.findRequired(incidentId);
    return ApiResponse.ok(incidentMetricsService.findLatestSnapshots(incidentId, 20));
  }
}
