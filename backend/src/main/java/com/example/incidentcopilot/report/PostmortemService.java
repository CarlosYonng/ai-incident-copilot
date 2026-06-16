package com.example.incidentcopilot.report;

import com.example.incidentcopilot.action.ActionProposalRepository;
import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentService;
import com.example.incidentcopilot.workflow.WorkflowNodeExecutionResponse;
import com.example.incidentcopilot.workflow.WorkflowRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostmortemService {
  private final PostmortemRepository postmortemRepository;
  private final IncidentService incidentService;
  private final WorkflowRepository workflowRepository;
  private final ActionProposalRepository actionProposalRepository;
  private final JdbcJson jdbcJson;

  public PostmortemService(
      PostmortemRepository postmortemRepository,
      IncidentService incidentService,
      WorkflowRepository workflowRepository,
      ActionProposalRepository actionProposalRepository,
      JdbcJson jdbcJson
  ) {
    this.postmortemRepository = postmortemRepository;
    this.incidentService = incidentService;
    this.workflowRepository = workflowRepository;
    this.actionProposalRepository = actionProposalRepository;
    this.jdbcJson = jdbcJson;
  }

  @Transactional
  public PostmortemResponse generate(Long incidentId) {
    Incident incident = incidentService.findRequired(incidentId);
    var workflows = workflowRepository.findByIncident(incidentId);
    Long workflowId = workflows.isEmpty() ? null : workflows.getLast().id();
    var nodes = workflowId == null
        ? List.<WorkflowNodeExecutionResponse>of()
        : workflowRepository.findNodeExecutions(workflowId).stream()
            .map(WorkflowNodeExecutionResponse::from)
            .toList();
    var actions = actionProposalRepository.findByIncident(incidentId);
    List<String> actionItems = List.of(
        "补充 " + incident.serviceName() + " 错误率、p95 延迟和下游依赖监控",
        "为 " + valueOrDefault(incident.endpoint(), "核心接口") + " 建立超时/异常 Runbook 演练",
        "把本次人工确认动作纳入值班交接记录"
    );
    List<String> preventionItems = List.of(
        "为中高风险处置保持人工审批门禁",
        "补齐 MCP 诊断证据和历史工单索引",
        "在发布前演练降级、重试和回滚预案"
    );
    String summary = incident.title() + " 已进入故障处理闭环，系统完成证据收集、建议生成和审计记录。";
    String rootCause = actions.stream()
        .filter(action -> "OFFLINE_EXECUTED".equals(action.status()))
        .findFirst()
        .map(action -> "主要风险来自 " + incident.serviceName() + " 链路异常；已线下执行 " + action.title() + "。")
        .orElse("初步判断为下游依赖延迟、代码边界或配置变更导致的服务异常。");
    String impact = "影响范围集中在 " + incident.serviceName() + " " + valueOrDefault(incident.endpoint(), "相关接口") + "，表现为错误率或延迟升高。";
    String reportContent = renderMarkdown(incident, summary, rootCause, impact, actionItems, preventionItems);
    postmortemRepository.upsert(
        incidentId,
        summary,
        rootCause,
        impact,
        jdbcJson.stringify(nodes),
        jdbcJson.stringify(actionItems),
        jdbcJson.stringify(preventionItems),
        reportContent
    );
    return new PostmortemResponse(incidentId, summary, rootCause, impact, actionItems, preventionItems, reportContent);
  }

  public PostmortemResponse get(Long incidentId) {
    incidentService.findRequired(incidentId);
    return postmortemRepository.findByIncident(incidentId)
        .map(report -> new PostmortemResponse(
            report.incidentId(),
            report.summary(),
            report.rootCause(),
            report.impact(),
            readStringList(report.actionItemsJson()),
            readStringList(report.preventionItemsJson()),
            report.reportContent()
        ))
        .orElseThrow(() -> ApiException.notFound("Postmortem report not found for incident: " + incidentId));
  }

  @SuppressWarnings("unchecked")
  private List<String> readStringList(String json) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
    } catch (Exception exception) {
      return List.of();
    }
  }

  private String renderMarkdown(
      Incident incident,
      String summary,
      String rootCause,
      String impact,
      List<String> actionItems,
      List<String> preventionItems
  ) {
    return """
        # Incident Postmortem

        ## Summary
        %s

        ## Root Cause
        %s

        ## Impact
        %s

        ## Action Items
        - %s

        ## Prevention
        - %s

        ## Audit
        Incident: %s
        Service: %s
        """.formatted(
        summary,
        rootCause,
        impact,
        String.join("\n- ", actionItems),
        String.join("\n- ", preventionItems),
        incident.incidentNo(),
        incident.serviceName()
    );
  }

  private String valueOrDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
