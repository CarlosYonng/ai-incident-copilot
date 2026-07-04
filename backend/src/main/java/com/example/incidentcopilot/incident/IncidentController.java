package com.example.incidentcopilot.incident;

import com.example.incidentcopilot.common.ApiResponse;
import com.example.incidentcopilot.workflow.WorkflowResponse;
import com.example.incidentcopilot.workflow.WorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
  private final IncidentService incidentService;
  private final WorkflowService workflowService;

  public IncidentController(IncidentService incidentService, WorkflowService workflowService) {
    this.incidentService = incidentService;
    this.workflowService = workflowService;
  }

  @PostMapping
  public ApiResponse<IncidentResponse> create(@Valid @RequestBody IncidentCreateRequest request) {
    return ApiResponse.ok(incidentService.create(request));
  }

  @GetMapping
  public ApiResponse<List<IncidentResponse>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String serviceName,
      @RequestParam(required = false) String severity,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    return ApiResponse.ok(incidentService.list(status, serviceName, severity, page, size));
  }

  @GetMapping("/{id}")
  public ApiResponse<IncidentDetailResponse> get(@PathVariable Long id) {
    return ApiResponse.ok(incidentService.getDetail(id));
  }

  @PostMapping("/{id}/start-workflow")
  public ApiResponse<WorkflowResponse> startWorkflow(@PathVariable Long id) {
    return ApiResponse.ok(workflowService.startIncidentWorkflow(id));
  }

  @GetMapping("/{id}/workflow/latest")
  public ApiResponse<WorkflowResponse> latestWorkflow(@PathVariable Long id) {
    return ApiResponse.ok(workflowService.getLatestByIncidentOrNull(id));
  }

  @PostMapping("/{id}/close")
  public ApiResponse<IncidentResponse> close(
      @PathVariable Long id,
      @Valid @RequestBody IncidentCloseRequest request
  ) {
    return ApiResponse.ok(incidentService.close(id, request));
  }
}
