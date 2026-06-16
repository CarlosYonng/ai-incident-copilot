package com.example.incidentcopilot.action;

import com.example.incidentcopilot.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActionProposalController {
  private final ActionProposalService actionProposalService;

  public ActionProposalController(ActionProposalService actionProposalService) {
    this.actionProposalService = actionProposalService;
  }

  @GetMapping("/api/incidents/{incidentId}/actions")
  public ApiResponse<List<ActionProposalResponse>> list(@PathVariable Long incidentId) {
    return ApiResponse.ok(actionProposalService.listByIncident(incidentId));
  }

  @PostMapping("/api/actions/{actionId}/approve")
  public ApiResponse<ActionProposalResponse> approve(
      @PathVariable Long actionId,
      @Valid @RequestBody ActionDecisionRequest request
  ) {
    return ApiResponse.ok(actionProposalService.approve(actionId, request));
  }

  @PostMapping("/api/actions/{actionId}/reject")
  public ApiResponse<ActionProposalResponse> reject(
      @PathVariable Long actionId,
      @Valid @RequestBody ActionDecisionRequest request
  ) {
    return ApiResponse.ok(actionProposalService.reject(actionId, request));
  }

  @PostMapping("/api/actions/{actionId}/mark-offline-executed")
  public ApiResponse<ActionProposalResponse> markOfflineExecuted(
      @PathVariable Long actionId,
      @Valid @RequestBody MarkOfflineExecutedRequest request
  ) {
    return ApiResponse.ok(actionProposalService.markOfflineExecuted(actionId, request));
  }

  @PostMapping("/api/actions/{actionId}/escalate")
  public ApiResponse<ActionProposalResponse> escalate(
      @PathVariable Long actionId,
      @Valid @RequestBody ActionDecisionRequest request
  ) {
    return ApiResponse.ok(actionProposalService.escalate(actionId, request));
  }
}
