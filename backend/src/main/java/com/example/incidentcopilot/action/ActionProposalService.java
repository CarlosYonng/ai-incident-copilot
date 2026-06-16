package com.example.incidentcopilot.action;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.diagnosis.DiagnosisEvidence;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.metrics.MockMetricsService;
import com.example.incidentcopilot.runbook.RunbookDocument;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActionProposalService {
  private final ActionProposalRepository actionProposalRepository;
  private final IncidentRepository incidentRepository;
  private final MockMetricsService mockMetricsService;
  private final JdbcJson jdbcJson;

  public ActionProposalService(
      ActionProposalRepository actionProposalRepository,
      IncidentRepository incidentRepository,
      MockMetricsService mockMetricsService,
      JdbcJson jdbcJson
  ) {
    this.actionProposalRepository = actionProposalRepository;
    this.incidentRepository = incidentRepository;
    this.mockMetricsService = mockMetricsService;
    this.jdbcJson = jdbcJson;
  }

  public List<ActionProposalResponse> listByIncident(Long incidentId) {
    return actionProposalRepository.findByIncident(incidentId).stream()
        .map(ActionProposalResponse::from)
        .toList();
  }

  @Transactional
  public List<ActionProposal> generateDefaults(
      Incident incident,
      Long workflowInstanceId,
      DiagnosisEvidence diagnosis,
      List<RunbookDocument> runbooks,
      String severity
  ) {
    if (!actionProposalRepository.findByWorkflow(workflowInstanceId).isEmpty()) {
      return actionProposalRepository.findByWorkflow(workflowInstanceId);
    }
    String evidenceJson = jdbcJson.stringify(Map.of(
        "diagnosisSummary", diagnosis.summary(),
        "runbooks", runbooks.stream().map(RunbookDocument::fileName).toList(),
        "severity", severity
    ));
    return List.of(
        actionProposalRepository.create(
            incident.id(),
            workflowInstanceId,
            "继续观察并补充链路日志",
            "OBSERVE_AND_ENRICH_LOGS",
            "LOW",
            "当前已有告警和指标证据，低风险动作可用于补足定位材料。",
            evidenceJson,
            "不影响线上流量，只增加排障信息。",
            "确认日志采集不会暴露敏感字段。",
            false,
            "READY"
        ),
        actionProposalRepository.create(
            incident.id(),
            workflowInstanceId,
            suggestedMediumTitle(incident),
            suggestedMediumType(incident),
            "MEDIUM",
            "诊断证据显示延迟和错误率升高，中风险缓解动作可降低用户影响。",
            evidenceJson,
            "可能导致部分请求延迟处理，需要人工确认业务影响。",
            "确认不会造成重复支付、重复下单或状态不一致。",
            true,
            "PENDING"
        ),
        actionProposalRepository.create(
            incident.id(),
            workflowInstanceId,
            "回滚最近发布或切换生产配置",
            "ROLLBACK_OR_CONFIG_CHANGE",
            "HIGH",
            "如果中风险缓解无效，可考虑高风险变更，但系统只生成建议。",
            evidenceJson,
            "可能影响生产配置、版本和流量，需要 SRE/负责人审批。",
            "确认发布窗口、回滚版本、数据库兼容性和应急联系人。",
            true,
            "PENDING"
        )
    );
  }

  @Transactional
  public ActionProposalResponse approve(Long actionId, ActionDecisionRequest request) {
    ActionProposal proposal = findRequired(actionId);
    actionProposalRepository.createApproval(
        proposal.id(),
        proposal.incidentId(),
        "APPROVED",
        request.comment(),
        request.approvedBy()
    );
    actionProposalRepository.updateStatus(actionId, "APPROVED");
    return ActionProposalResponse.from(findRequired(actionId));
  }

  @Transactional
  public ActionProposalResponse reject(Long actionId, ActionDecisionRequest request) {
    ActionProposal proposal = findRequired(actionId);
    actionProposalRepository.createApproval(
        proposal.id(),
        proposal.incidentId(),
        "REJECTED",
        request.comment(),
        request.approvedBy()
    );
    actionProposalRepository.updateStatus(actionId, "REJECTED");
    return ActionProposalResponse.from(findRequired(actionId));
  }

  @Transactional
  public ActionProposalResponse escalate(Long actionId, ActionDecisionRequest request) {
    ActionProposal proposal = findRequired(actionId);
    actionProposalRepository.createApproval(
        proposal.id(),
        proposal.incidentId(),
        "ESCALATED",
        request.comment(),
        request.approvedBy()
    );
    actionProposalRepository.updateStatus(actionId, "ESCALATED");
    return ActionProposalResponse.from(findRequired(actionId));
  }

  @Transactional
  public ActionProposalResponse markOfflineExecuted(Long actionId, MarkOfflineExecutedRequest request) {
    ActionProposal proposal = findRequired(actionId);
    actionProposalRepository.createApproval(
        proposal.id(),
        proposal.incidentId(),
        "MARK_OFFLINE_EXECUTED",
        request.resultDetail(),
        request.executor()
    );
    actionProposalRepository.createActionRecord(
        proposal.incidentId(),
        proposal.id(),
        proposal.actionType(),
        request.executor(),
        "SUCCESS",
        request.resultDetail()
    );
    actionProposalRepository.updateStatus(actionId, "OFFLINE_EXECUTED");
    Incident incident = incidentRepository.updateStatus(proposal.incidentId(), "RECOVERING");
    mockMetricsService.recordRecoveringSnapshot(incident);
    return ActionProposalResponse.from(findRequired(actionId));
  }

  public ActionProposal findRequired(Long actionId) {
    return actionProposalRepository.findById(actionId)
        .orElseThrow(() -> ApiException.notFound("Action proposal not found: " + actionId));
  }

  private String suggestedMediumTitle(Incident incident) {
    if (containsAny(incident.title(), "支付", "payment")) {
      return "开启支付回调延迟重试";
    }
    if (containsAny(incident.title(), "订单", "order")) {
      return "临时启用订单创建参数兜底";
    }
    return "临时降级高风险依赖调用";
  }

  private String suggestedMediumType(Incident incident) {
    if (containsAny(incident.title(), "支付", "payment")) {
      return "ENABLE_DELAYED_RETRY";
    }
    if (containsAny(incident.title(), "订单", "order")) {
      return "ENABLE_INPUT_FALLBACK";
    }
    return "ENABLE_DEGRADATION";
  }

  private boolean containsAny(String value, String... needles) {
    if (value == null) {
      return false;
    }
    String normalized = value.toLowerCase();
    for (String needle : needles) {
      if (normalized.contains(needle.toLowerCase())) {
        return true;
      }
    }
    return false;
  }
}
