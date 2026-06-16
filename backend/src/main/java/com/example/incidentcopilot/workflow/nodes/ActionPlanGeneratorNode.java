package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.action.ActionProposal;
import com.example.incidentcopilot.action.ActionProposalService;
import com.example.incidentcopilot.diagnosis.DiagnosisEvidence;
import com.example.incidentcopilot.runbook.RunbookDocument;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(60)
public class ActionPlanGeneratorNode implements WorkflowNode {
  private final ActionProposalService actionProposalService;

  public ActionPlanGeneratorNode(ActionProposalService actionProposalService) {
    this.actionProposalService = actionProposalService;
  }

  @Override
  public String name() {
    return "ActionPlanGeneratorNode";
  }

  @Override
  public String nodeType() {
    return "ACTION_PLAN";
  }

  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(WorkflowContext context) {
    DiagnosisEvidence diagnosis = (DiagnosisEvidence) context.get("diagnosis");
    List<RunbookDocument> runbooks = (List<RunbookDocument>) context.get("runbooks");
    String severity = context.getString("severity", "P2");
    List<ActionProposal> proposals = actionProposalService.generateDefaults(
        context.incident(),
        context.workflowInstanceId(),
        diagnosis,
        runbooks == null ? List.of() : runbooks,
        severity
    );
    context.put("actionProposals", proposals);
    return new NodeResult(
        nodeType(),
        Map.of("incidentId", context.incident().id(), "severity", severity),
        Map.of("proposals", proposals.stream().map(this::compact).toList())
    );
  }

  private Map<String, Object> compact(ActionProposal proposal) {
    return Map.of(
        "id", proposal.id(),
        "title", proposal.title(),
        "riskLevel", proposal.riskLevel(),
        "requiresApproval", proposal.requiresApproval(),
        "status", proposal.status()
    );
  }
}
