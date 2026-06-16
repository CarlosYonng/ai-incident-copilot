package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.action.ActionProposal;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(70)
public class RiskReviewNode implements WorkflowNode {

  @Override
  public String name() {
    return "RiskReviewNode";
  }

  @Override
  public String nodeType() {
    return "RISK_REVIEW";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(WorkflowContext context) {
    List<ActionProposal> proposals = (List<ActionProposal>) context.get("actionProposals");
    List<ActionProposal> approvalRequired = proposals == null
        ? List.of()
        : proposals.stream().filter(ActionProposal::requiresApproval).toList();
    context.put("approvalRequired", !approvalRequired.isEmpty());
    return new NodeResult(
        nodeType(),
        Map.of("proposalCount", proposals == null ? 0 : proposals.size()),
        Map.of(
            "approvalRequired", !approvalRequired.isEmpty(),
            "approvalActionIds", approvalRequired.stream().map(ActionProposal::id).toList(),
            "rule", "LOW can be recorded directly; MEDIUM/HIGH require human confirmation."
        )
    );
  }
}
