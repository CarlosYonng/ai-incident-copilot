package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class AlertReceiverNode implements WorkflowNode {

  @Override
  public String name() {
    return "AlertReceiverNode";
  }

  @Override
  public String nodeType() {
    return "ALERT";
  }

  @Override
  public NodeResult execute(WorkflowContext context) {
    Incident incident = context.incident();
    Map<String, Object> input = Map.of(
        "incidentId", incident.id(),
        "incidentNo", incident.incidentNo(),
        "source", incident.source(),
        "traceId", incident.traceId() == null ? "" : incident.traceId()
    );
    Map<String, Object> output = Map.of(
        "accepted", true,
        "title", incident.title(),
        "serviceName", incident.serviceName(),
        "endpoint", incident.endpoint() == null ? "" : incident.endpoint(),
        "summary", incident.summary() == null ? "" : incident.summary()
    );
    context.put("alert", output);
    return new NodeResult(nodeType(), input, output);
  }
}
