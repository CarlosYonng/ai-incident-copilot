package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.audit.ToolCallLogResponse;
import com.example.incidentcopilot.audit.ToolCallLogger;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.incident.IncidentService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkflowService {
  private final IncidentService incidentService;
  private final IncidentRepository incidentRepository;
  private final WorkflowRepository workflowRepository;
  private final WorkflowEngine workflowEngine;
  private final ToolCallLogger toolCallLogger;

  public WorkflowService(
      IncidentService incidentService,
      IncidentRepository incidentRepository,
      WorkflowRepository workflowRepository,
      WorkflowEngine workflowEngine,
      ToolCallLogger toolCallLogger
  ) {
    this.incidentService = incidentService;
    this.incidentRepository = incidentRepository;
    this.workflowRepository = workflowRepository;
    this.workflowEngine = workflowEngine;
    this.toolCallLogger = toolCallLogger;
  }

  public WorkflowResponse startIncidentWorkflow(Long incidentId) {
    Incident incident = incidentService.findRequired(incidentId);
    if ("CLOSED".equals(incident.status())) {
      throw ApiException.conflict("Closed incident cannot start workflow: " + incidentId);
    }
    incidentRepository.updateWorkflowRunning(incidentId);
    WorkflowInstance instance = workflowRepository.createInstance(incidentId);
    WorkflowInstance finished = workflowEngine.run(new WorkflowContext(instance.id(), incident));
    return WorkflowResponse.from(finished);
  }

  public WorkflowResponse get(Long instanceId) {
    return workflowRepository.findInstance(instanceId)
        .map(WorkflowResponse::from)
        .orElseThrow(() -> ApiException.notFound("Workflow not found: " + instanceId));
  }

  public List<WorkflowNodeExecutionResponse> listNodes(Long instanceId) {
    if (workflowRepository.findInstance(instanceId).isEmpty()) {
      throw ApiException.notFound("Workflow not found: " + instanceId);
    }
    return workflowRepository.findNodeExecutions(instanceId).stream()
        .map(WorkflowNodeExecutionResponse::from)
        .toList();
  }

  public List<ToolCallLogResponse> listToolCalls(Long instanceId) {
    if (workflowRepository.findInstance(instanceId).isEmpty()) {
      throw ApiException.notFound("Workflow not found: " + instanceId);
    }
    return toolCallLogger.findByWorkflow(instanceId).stream()
        .map(ToolCallLogResponse::from)
        .toList();
  }
}
