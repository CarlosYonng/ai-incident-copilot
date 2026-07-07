package com.example.incidentcopilot.report;

import com.example.incidentcopilot.action.ActionProposalRepository;
import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.common.DomainConstants.RiskLevel;
import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentService;
import com.example.incidentcopilot.workflow.WorkflowNodeExecutionResponse;
import com.example.incidentcopilot.workflow.WorkflowRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 故障复盘报告业务服务。
 *
 * <p>负责根据故障单数据、工作流执行记录和已执行行动项，自动生成结构化复盘报告，包括
 * 故障摘要、根因分析、影响评估、行动项和预防改进项。所有变更在事务内完成。</p>
 */
@Service
public class PostmortemService {

  /** 复盘报告数据访问层 */
  private final PostmortemRepository postmortemRepository;

  /** 故障单业务服务 */
  private final IncidentService incidentService;

  /** 工作流数据访问层 */
  private final WorkflowRepository workflowRepository;

  /** 行动建议数据访问层 */
  private final ActionProposalRepository actionProposalRepository;

  /** JSON 序列化/反序列化工具 */
  private final JdbcJson jdbcJson;

  /**
   * 构造方法，注入所需依赖。
   *
   * @param postmortemRepository      复盘报告数据访问层
   * @param incidentService           故障单业务服务
   * @param workflowRepository        工作流数据访问层
   * @param actionProposalRepository  行动建议数据访问层
   * @param jdbcJson                  JSON 序列化工具
   */
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

  /**
   * 生成指定故障单的复盘报告。
   *
   * <p>收集故障信息、工作流节点执行记录和已执行行动建议，组装为结构化报告并持久化到数据库。</p>
   *
   * @param incidentId 故障单 ID
   * @return 复盘报告响应体
   */
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
    var selectedActions = actionProposalRepository.findExecutedByIncident(incidentId);
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
    String rootCause = selectedActions.stream()
        .findFirst()
        .map(action -> "主要风险来自 " + incident.serviceName() + " 链路异常；本次采用 "
            + action.title() + "（" + riskLabel(action.riskLevel()) + "），平台已记录处理结果并进入恢复观察。")
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

  /**
   * 获取指定故障单已有的复盘报告。
   *
   * <p>从数据库读取已持久化的复盘报告并组装为响应体。若不存在则抛出 {@link ApiException#notFound}。</p>
   *
   * @param incidentId 故障单 ID
   * @return 复盘报告响应体
   * @throws ApiException 当指定故障单的复盘报告不存在时抛出
   */
  public PostmortemResponse get(Long incidentId) {
    incidentService.findRequired(incidentId);
    return postmortemRepository.findByIncident(incidentId)
        .map(report -> new PostmortemResponse(
            report.incidentId(),
            report.summary(),
            report.rootCause(),
            report.impact(),
            jdbcJson.readStringList(report.actionItemsJson()),
            jdbcJson.readStringList(report.preventionItemsJson()),
            report.reportContent()
        ))
        .orElseThrow(() -> ApiException.notFound("Postmortem report not found for incident: " + incidentId));
  }

  /**
   * 渲染复盘报告 Markdown 文本。
   *
   * <p>使用模板将摘要、根因、影响、行动项和预防项组装为结构化 Markdown 文档。</p>
   *
   * @param incident        故障单实体
   * @param summary         故障摘要
   * @param rootCause       根因分析
   * @param impact          影响评估
   * @param actionItems     行动项列表
   * @param preventionItems 预防改进项列表
   * @return 格式化后的 Markdown 文本
   */
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

  /**
   * 返回字符串值，若为空或空白则使用默认值。
   *
   * @param value        原始字符串
   * @param defaultValue 默认值
   * @return 非空白的原始字符串，否则返回默认值
   */
  private String valueOrDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

  /**
   * 将风险等级英文标签转换为中文标签。
   *
   * @param riskLevel 风险等级英文标识
   * @return 对应的中文风险等级描述
   */
  private String riskLabel(String riskLevel) {
    return switch (riskLevel) {
      case RiskLevel.LOW -> "低风险";
      case RiskLevel.MEDIUM -> "中风险";
      case RiskLevel.HIGH -> "高风险";
      default -> riskLevel;
    };
  }
}
