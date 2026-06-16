package com.example.incidentcopilot.action;

public record ActionProposalResponse(
    Long id,
    String title,
    String actionType,
    String riskLevel,
    String reason,
    String evidenceJson,
    String impact,
    String precheck,
    boolean requiresApproval,
    String status
) {
  public static ActionProposalResponse from(ActionProposal proposal) {
    return new ActionProposalResponse(
        proposal.id(),
        proposal.title(),
        proposal.actionType(),
        proposal.riskLevel(),
        proposal.reason(),
        proposal.evidenceJson(),
        proposal.impact(),
        proposal.precheck(),
        proposal.requiresApproval(),
        proposal.status()
    );
  }
}
