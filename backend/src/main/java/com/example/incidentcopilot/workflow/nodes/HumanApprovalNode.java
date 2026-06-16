package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(80)
public class HumanApprovalNode implements WorkflowNode {

  @Override
  public String name() {
    return "HumanApprovalNode";
  }

  @Override
  public String nodeType() {
    return "HUMAN_APPROVAL";
  }

  @Override
  public NodeResult execute(WorkflowContext context) {
    boolean approvalRequired = Boolean.TRUE.equals(context.get("approvalRequired"));
    if (approvalRequired) {
      context.put("workflowFinalStatus", "WAITING_APPROVAL");
      context.put("incidentFinalStatus", "WAITING_APPROVAL");
    }
    return new NodeResult(
        nodeType(),
        Map.of("incidentId", context.incident().id()),
        Map.of(
            "status", approvalRequired ? "WAITING_APPROVAL" : "NO_APPROVAL_REQUIRED",
            "message", approvalRequired
                ? "中高风险处置方案已生成，等待人工确认。"
                : "仅包含低风险建议，可直接记录。"
        )
    );
  }
}
