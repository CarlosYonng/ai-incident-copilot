package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.metrics.MetricSnapshot;
import com.example.incidentcopilot.metrics.IncidentMetricsService;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
/**
 * 指标采集节点。
 *
 * <p>当前从演示指标服务写入并读取快照，后续接 Prometheus/Grafana 时优先替换 IncidentMetricsService 内部实现。</p>
 */
public class MetricsCollectorNode implements WorkflowNode {
  /** 故障指标服务，用于记录并获取当前故障关联的指标快照（错误率、延迟、QPS 等）。 */
  private final IncidentMetricsService incidentMetricsService;

  /**
   * 构造指标采集节点。
   *
   * @param incidentMetricsService 故障指标服务
   */
  public MetricsCollectorNode(IncidentMetricsService incidentMetricsService) {
    this.incidentMetricsService = incidentMetricsService;
  }

  @Override
  public String name() {
    return "MetricsCollectorNode";
  }

  @Override
  public String nodeType() {
    return "METRICS";
  }

  /**
   * 执行指标采集逻辑。
   *
   * <p>通过 IncidentMetricsService 记录当前故障的工作流快照，获取状态、错误率、P95 延迟、
   * QPS 及快照时间，并写入工作流上下文的 "metrics" 键。</p>
   *
   * @param context 工作流上下文，包含当前 Incident
   * @return 节点执行结果，输出为指标快照的关键字段
   */
  @Override
  public NodeResult execute(WorkflowContext context) {
    MetricSnapshot snapshot = incidentMetricsService.recordWorkflowSnapshot(context.incident());
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
