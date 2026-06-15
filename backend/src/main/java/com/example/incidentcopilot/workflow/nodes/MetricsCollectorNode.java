package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.metrics.MetricSnapshot;
import com.example.incidentcopilot.metrics.MockMetricsService;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class MetricsCollectorNode implements WorkflowNode {
  private final MockMetricsService mockMetricsService;

  public MetricsCollectorNode(MockMetricsService mockMetricsService) {
    this.mockMetricsService = mockMetricsService;
  }

  @Override
  public String name() {
    return "MetricsCollectorNode";
  }

  @Override
  public String nodeType() {
    return "METRICS";
  }

  @Override
  public NodeResult execute(WorkflowContext context) {
    MetricSnapshot snapshot = mockMetricsService.recordWorkflowSnapshot(context.incident());
    Map<String, Object> input = Map.of(
        "incidentId", context.incident().id(),
        "serviceName", context.incident().serviceName()
    );
    Map<String, Object> output = Map.of(
        "metricStatus", snapshot.status(),
        "errorRate", snapshot.errorRate(),
        "p95Latency", snapshot.p95Latency(),
        "qps", snapshot.qps(),
        "snapshotTime", snapshot.snapshotTime().toString()
    );
    context.put("metrics", output);
    return new NodeResult(nodeType(), input, output);
  }
}
