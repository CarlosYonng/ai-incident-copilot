package com.example.incidentcopilot.action;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.diagnosis.DiagnosisEvidence;
import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.metrics.IncidentMetricsService;
import com.example.incidentcopilot.runbook.RunbookDocument;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActionProposalService {
  private final ActionProposalRepository actionProposalRepository;
  private final IncidentRepository incidentRepository;
  private final IncidentMetricsService incidentMetricsService;
  private final JdbcJson jdbcJson;

  public ActionProposalService(
      ActionProposalRepository actionProposalRepository,
      IncidentRepository incidentRepository,
      IncidentMetricsService incidentMetricsService,
      JdbcJson jdbcJson
  ) {
    this.actionProposalRepository = actionProposalRepository;
    this.incidentRepository = incidentRepository;
    this.incidentMetricsService = incidentMetricsService;
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
    // MVP uses deterministic action generation so interview demos are stable.
    // A later LLM implementation should preserve the same risk and approval gates.
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
    return recordResult(actionId, request);
  }

  @Transactional
  public ActionProposalResponse recordResult(Long actionId, MarkOfflineExecutedRequest request) {
    ActionProposal proposal = findRequired(actionId);
    List<ActionProposal> executedActions = actionProposalRepository.findExecutedByIncident(proposal.incidentId());
    if (!executedActions.isEmpty() && executedActions.stream().noneMatch(action -> action.id().equals(actionId))) {
      ActionProposal selected = executedActions.getFirst();
      throw ApiException.conflict("Incident already selected action: " + selected.title());
    }
    if ("NOT_SELECTED".equals(proposal.status()) || "REJECTED".equals(proposal.status())) {
      throw ApiException.conflict("Action proposal is not selectable: " + actionId);
    }
    // The system records that a human performed the action elsewhere. It never
    // calls production rollback, SQL, scaling, or configuration APIs.
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
    actionProposalRepository.markUnselectedActions(proposal.incidentId(), actionId);
    Incident incident = incidentRepository.updateStatus(proposal.incidentId(), "RECOVERING");
    incidentMetricsService.recordRecoveringSnapshot(incident, proposal);
    return ActionProposalResponse.from(findRequired(actionId));
  }

  public ActionProposal findRequired(Long actionId) {
    return actionProposalRepository.findById(actionId)
        .orElseThrow(() -> ApiException.notFound("Action proposal not found: " + actionId));
  }

  private String suggestedMediumTitle(Incident incident) {
    String text = incidentText(incident);
    if (containsAny(text, "qdrant", "vector", "向量")) {
      return "启用 RAG 检索兜底并暂停批量向量写入";
    }
    if (containsAny(text, "rag", "retrieval", "召回", "检索", "无结果", "no chunks")) {
      return "调整 RAG 召回参数并启用关键词检索兜底";
    }
    if (containsAny(text, "ai-service", "llm", "model provider", "模型调用", "模型服务")) {
      return "启用 AI 服务超时降级和备用模型路由";
    }
    if (containsAny(text, "sse", "stream", "流式", "断流")) {
      return "切换非流式响应并缩短 SSE 心跳间隔";
    }
    if (containsAny(text, "ingestion", "embedding", "知识库导入", "导入失败", "切片")) {
      return "暂停失败批次导入并重跑目标文档 embedding";
    }
    if (containsAny(text, "graphrag", "neo4j", "cypher", "图谱")) {
      return "关闭 GraphRAG fallback 并回退到向量检索";
    }
    if (containsAny(text, "支付", "payment")) {
      return "开启支付回调延迟重试";
    }
    if (containsAny(text, "订单", "order")) {
      return "临时启用订单创建参数兜底";
    }
    return "临时降级高风险依赖调用";
  }

  private String suggestedMediumType(Incident incident) {
    String text = incidentText(incident);
    if (containsAny(text, "qdrant", "vector", "向量")) {
      return "ENABLE_RAG_RETRIEVAL_FALLBACK";
    }
    if (containsAny(text, "rag", "retrieval", "召回", "检索", "无结果", "no chunks")) {
      return "TUNE_RAG_RETRIEVAL";
    }
    if (containsAny(text, "ai-service", "llm", "model provider", "模型调用", "模型服务")) {
      return "ENABLE_AI_SERVICE_DEGRADATION";
    }
    if (containsAny(text, "sse", "stream", "流式", "断流")) {
      return "ENABLE_NON_STREAMING_FALLBACK";
    }
    if (containsAny(text, "ingestion", "embedding", "知识库导入", "导入失败", "切片")) {
      return "RETRY_KNOWLEDGE_INGESTION_BATCH";
    }
    if (containsAny(text, "graphrag", "neo4j", "cypher", "图谱")) {
      return "DISABLE_GRAPHRAG_FALLBACK";
    }
    if (containsAny(text, "支付", "payment")) {
      return "ENABLE_DELAYED_RETRY";
    }
    if (containsAny(text, "订单", "order")) {
      return "ENABLE_INPUT_FALLBACK";
    }
    return "ENABLE_DEGRADATION";
  }

  private String incidentText(Incident incident) {
    return String.join(" ",
        valueOrEmpty(incident.title()),
        valueOrEmpty(incident.serviceName()),
        valueOrEmpty(incident.endpoint()),
        valueOrEmpty(incident.exceptionType()),
        valueOrEmpty(incident.summary())
    );
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

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
