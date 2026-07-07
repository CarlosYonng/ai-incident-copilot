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

/**
 * 告警入站 REST 控制器。
 *
 * <p>提供标准内部告警入站、Grafana webhook 和 Alertmanager webhook 三种接入方式，
 * 并在创建或关联故障单后自动启动处理编排工作流。</p>
 */
@RestController
public class AlertController {
  /** 告警入站服务，负责告警的校验、去重和故障单关联。 */
  private final AlertIngestService alertIngestService;
  /** 工作流服务，用于在告警创建故障单后启动处理编排。 */
  private final WorkflowService workflowService;
  /** Webhook 适配器，将外部告警格式转换为内部统一模型。 */
  private final AlertWebhookAdapter alertWebhookAdapter;

  /**
   * 构造告警控制器实例。
   *
   * @param alertIngestService 告警入站服务
   * @param workflowService    工作流服务
   * @param alertWebhookAdapter Webhook 适配器
   */
  public AlertController(
      AlertIngestService alertIngestService,
      WorkflowService workflowService,
      AlertWebhookAdapter alertWebhookAdapter
  ) {
    this.alertIngestService = alertIngestService;
    this.workflowService = workflowService;
    this.alertWebhookAdapter = alertWebhookAdapter;
  }

  /**
   * 标准告警入站接口。
   *
   * <p>接收内部系统发送的告警请求，完成入站处理后可选启动工作流。</p>
   *
   * @param request 告警入站请求体，包含告警信息和是否启动工作流标识
   * @return 包含告警事件、故障单（若有）和工作流（若启动）的统一响应
   */
  @PostMapping("/api/alerts/ingest")
  public ApiResponse<AlertIngestResponse> ingest(@Valid @RequestBody AlertIngestRequest request) {
    return ingestAndMaybeStartWorkflow(request);
  }

  /**
   * Grafana webhook 告警入站接口。
   *
   * <p>接收 Grafana 发送的 webhook 告警负载，转换为内部模型后完成入站处理。</p>
   *
   * @param payload Grafana webhook 原始 JSON 负载
   * @return 包含告警事件、故障单（若有）和工作流（若启动）的统一响应
   */
  @PostMapping("/api/alerts/grafana")
  public ApiResponse<AlertIngestResponse> grafana(@RequestBody Map<String, Object> payload) {
    return ingestAndMaybeStartWorkflow(alertWebhookAdapter.fromGrafana(payload));
  }

  /**
   * Alertmanager webhook 告警入站接口。
   *
   * <p>接收 Prometheus Alertmanager 发送的 webhook 告警负载，转换为内部模型后完成入站处理。</p>
   *
   * @param payload Alertmanager webhook 原始 JSON 负载
   * @return 包含告警事件、故障单（若有）和工作流（若启动）的统一响应
   */
  @PostMapping("/api/alerts/alertmanager")
  public ApiResponse<AlertIngestResponse> alertmanager(@RequestBody Map<String, Object> payload) {
    return ingestAndMaybeStartWorkflow(alertWebhookAdapter.fromAlertmanager(payload));
  }

  /**
   * 告警入站并可选启动工作流的内部逻辑。
   *
   * <p>统一处理三种入站途径（标准、Grafana、Alertmanager），完成告警入站后
   * 根据请求标志自动启动处理编排。</p>
   *
   * @param request 告警入站请求体
   * @return 包含告警事件、故障单（若有）和工作流（若启动）的统一响应
   */
  private ApiResponse<AlertIngestResponse> ingestAndMaybeStartWorkflow(AlertIngestRequest request) {
    AlertIngestResult result = alertIngestService.ingest(request);
    WorkflowResponse workflow = null;
    if (Boolean.TRUE.equals(request.startWorkflow()) && result.incident() != null) {
      // 告警创建或关联到故障单后，按请求标志自动启动处理编排；被忽略的告警不会启动工作流。
      workflow = workflowService.startIncidentWorkflow(result.incident().id());
    }
    return ApiResponse.ok(new AlertIngestResponse(
        AlertEventResponse.from(result.event()),
        result.incident(),
        workflow
    ));
  }

  /**
   * 按故障单 ID 查询关联的所有告警事件。
   *
   * @param incidentId 故障单 ID
   * @return 告警事件响应列表
   */
  @GetMapping("/api/incidents/{incidentId}/alerts")
  public ApiResponse<List<AlertEventResponse>> listByIncident(@PathVariable Long incidentId) {
    return ApiResponse.ok(alertIngestService.listByIncident(incidentId));
  }
}
