package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.audit.ToolCallLogResponse;
import com.example.incidentcopilot.audit.ToolCallLogger;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.incident.IncidentService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkflowService {
  private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

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
    var latestWorkflow = workflowRepository.findLatestByIncident(incidentId);
    if (latestWorkflow.isPresent() && isStillAwaitingOperator(latestWorkflow.get().status())) {
      return WorkflowResponse.from(latestWorkflow.get());
    }
    incidentRepository.updateWorkflowRunning(incidentId);
    WorkflowInstance instance = workflowRepository.createInstance(incidentId);
    log.info(
        "workflow_started workflowInstanceId={} incidentId={} incidentNo={} service={}",
        instance.id(),
        incidentId,
        incident.incidentNo(),
        incident.serviceName()
    );
    WorkflowInstance finished = workflowEngine.run(new WorkflowContext(instance.id(), incident));
    log.info(
        "workflow_finished workflowInstanceId={} incidentId={} status={}",
        finished.id(),
        incidentId,
        finished.status()
    );
    return WorkflowResponse.from(finished);
  }

  private boolean isStillAwaitingOperator(String status) {
    return "RUNNING".equals(status) || "WAITING_APPROVAL".equals(status);
  }

  public WorkflowResponse get(Long instanceId) {
    return workflowRepository.findInstance(instanceId)
        .map(WorkflowResponse::from)
        .orElseThrow(() -> ApiException.notFound("Workflow not found: " + instanceId));
  }

  public WorkflowResponse getLatestByIncidentOrNull(Long incidentId) {
    incidentService.findRequired(incidentId);
    return workflowRepository.findLatestByIncident(incidentId)
        .map(WorkflowResponse::from)
        .orElse(null);
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
