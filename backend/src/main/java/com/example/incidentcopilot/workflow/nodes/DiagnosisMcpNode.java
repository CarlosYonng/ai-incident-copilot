package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.diagnosis.DiagnosisEvidence;
import com.example.incidentcopilot.diagnosis.DiagnosisMcpClient;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class DiagnosisMcpNode implements WorkflowNode {
  private final DiagnosisMcpClient diagnosisMcpClient;

  public DiagnosisMcpNode(DiagnosisMcpClient diagnosisMcpClient) {
    this.diagnosisMcpClient = diagnosisMcpClient;
  }

  @Override
  public String name() {
    return "DiagnosisMcpNode";
  }

  @Override
  public String nodeType() {
    return "MCP";
  }

  @Override
  public NodeResult execute(WorkflowContext context) {
    Map<String, Object> input = Map.of(
        "serviceName", context.incident().serviceName(),
        "traceId", context.incident().traceId() == null ? "" : context.incident().traceId(),
        "exceptionType", context.incident().exceptionType() == null ? "" : context.incident().exceptionType()
    );
    DiagnosisEvidence evidence = diagnosisMcpClient.collectEvidence(context.workflowInstanceId(), name(), context.incident());
    context.put("diagnosis", evidence);
    Map<String, Object> output = Map.of(
        "summary", evidence.summary(),
        "logs", evidence.logs(),
        "codeHints", evidence.codeHints(),
        "tickets", evidence.tickets(),
        "reportId", evidence.reportId(),
        "fallbackUsed", evidence.fallbackUsed()
    );
    return new NodeResult(nodeType(), input, output);
  }
}
