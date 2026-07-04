package com.example.incidentcopilot.alert;

import com.example.incidentcopilot.common.ApiResponse;
import com.example.incidentcopilot.workflow.WorkflowResponse;
import com.example.incidentcopilot.workflow.WorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlertController {
  private final AlertIngestService alertIngestService;
  private final WorkflowService workflowService;
  private final AlertWebhookAdapter alertWebhookAdapter;

  public AlertController(
      AlertIngestService alertIngestService,
      WorkflowService workflowService,
      AlertWebhookAdapter alertWebhookAdapter
  ) {
    this.alertIngestService = alertIngestService;
    this.workflowService = workflowService;
    this.alertWebhookAdapter = alertWebhookAdapter;
  }

  @PostMapping("/api/alerts/ingest")
  public ApiResponse<AlertIngestResponse> ingest(@Valid @RequestBody AlertIngestRequest request) {
    return ingestAndMaybeStartWorkflow(request);
  }

  @PostMapping("/api/alerts/grafana")
  public ApiResponse<AlertIngestResponse> grafana(@RequestBody Map<String, Object> payload) {
    return ingestAndMaybeStartWorkflow(alertWebhookAdapter.fromGrafana(payload));
  }

  @PostMapping("/api/alerts/alertmanager")
  public ApiResponse<AlertIngestResponse> alertmanager(@RequestBody Map<String, Object> payload) {
    return ingestAndMaybeStartWorkflow(alertWebhookAdapter.fromAlertmanager(payload));
  }

  private ApiResponse<AlertIngestResponse> ingestAndMaybeStartWorkflow(AlertIngestRequest request) {
    AlertIngestResult result = alertIngestService.ingest(request);
    WorkflowResponse workflow = null;
    if (Boolean.TRUE.equals(request.startWorkflow()) && result.incident() != null) {
      workflow = workflowService.startIncidentWorkflow(result.incident().id());
    }
    return ApiResponse.ok(new AlertIngestResponse(
        AlertEventResponse.from(result.event()),
        result.incident(),
        workflow
    ));
  }

  @GetMapping("/api/incidents/{incidentId}/alerts")
  public ApiResponse<List<AlertEventResponse>> listByIncident(@PathVariable Long incidentId) {
    return ApiResponse.ok(alertIngestService.listByIncident(incidentId));
  }
}
