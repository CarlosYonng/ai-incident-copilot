package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
  private final WorkflowService workflowService;

  public WorkflowController(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  @GetMapping("/{instanceId}")
  public ApiResponse<WorkflowResponse> get(@PathVariable Long instanceId) {
    return ApiResponse.ok(workflowService.get(instanceId));
  }

  @GetMapping("/{instanceId}/nodes")
  public ApiResponse<List<WorkflowNodeExecutionResponse>> nodes(@PathVariable Long instanceId) {
    return ApiResponse.ok(workflowService.listNodes(instanceId));
  }
}
