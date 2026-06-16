package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class SeverityClassifierNode implements WorkflowNode {
  private final IncidentRepository incidentRepository;

  public SeverityClassifierNode(IncidentRepository incidentRepository) {
    this.incidentRepository = incidentRepository;
  }

  @Override
  public String name() {
    return "SeverityClassifierNode";
  }

  @Override
  public String nodeType() {
    return "SEVERITY";
  }

  @Override
  public NodeResult execute(WorkflowContext context) {
    String severity = classify(context);
    incidentRepository.updateSeverity(context.incident().id(), severity);
    context.put("severity", severity);
    return new NodeResult(
        nodeType(),
        Map.of(
            "title", context.incident().title(),
            "serviceName", context.incident().serviceName(),
            "exceptionType", context.incident().exceptionType() == null ? "" : context.incident().exceptionType()
        ),
        Map.of(
            "severity", severity,
            "reason", reason(severity)
        )
    );
  }

  private String classify(WorkflowContext context) {
    String text = (context.incident().title() + " " + context.incident().summary() + " " + context.incident().exceptionType()).toLowerCase();
    if (text.contains("payment") || text.contains("支付") || text.contains("timeout")) {
      return "P1";
    }
    if (text.contains("nullpointer") || text.contains("空指针") || text.contains("500")) {
      return "P2";
    }
    return "P3";
  }

  private String reason(String severity) {
    return switch (severity) {
      case "P1" -> "支付链路或核心链路异常，错误率和延迟升高，需要快速缓解。";
      case "P2" -> "单服务核心接口异常，需要排查并生成处置建议。";
      default -> "影响有限，保持观察并补充证据。";
    };
  }
}
