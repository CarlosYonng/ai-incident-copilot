package com.example.incidentcopilot.action;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.common.DomainConstants.ActionStatus;
import com.example.incidentcopilot.common.DomainConstants.ApprovalDecision;
import com.example.incidentcopilot.common.DomainConstants.IncidentStatus;
import com.example.incidentcopilot.common.DomainConstants.RiskLevel;
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

/**
 * 处置方案应用服务。
 *
 * <p>负责生成候选方案、记录审批决策和回填线下执行结果；任何生产变更都必须由人在线下执行。</p>
 */
@Service
public class ActionProposalService {
  private final ActionProposalRepository actionProposalRepository;
  private final IncidentRepository incidentRepository;
  private final IncidentMetricsService incidentMetricsService;
  private final JdbcJson jdbcJson;

  /**
   * 构造处置方案服务，注入所需依赖。
   *
   * @param actionProposalRepository 处置方案仓库
   * @param incidentRepository       故障单仓库
   * @param incidentMetricsService   指标快照服务
   * @param jdbcJson                 JSON 序列化工具
   */
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

  /**
   * 查询指定故障单的处置方案列表。
   *
   * @param incidentId 故障单 ID
   * @return 方案响应列表
   */
  public List<ActionProposalResponse> listByIncident(Long incidentId) {
    return actionProposalRepository.findByIncident(incidentId).stream()
        .map(ActionProposalResponse::from)
        .toList();
  }

  /**
   * 为故障单生成默认的三级处置方案（低 / 中 / 高风险）。
   *
   * <p>如果同一工作流实例已生成过方案则直接复用，避免重复插入。MVP 阶段使用确定性规则生成，
   * 后续接 LLM 时也必须保留风险分级和人工审批门禁。</p>
   *
   * @param incident            故障单
   * @param workflowInstanceId  工作流实例 ID
   * @param diagnosis           诊断证据
   * @param runbooks            相关 Runbook 文档列表
   * @param severity            故障严重等级
   * @return 生成的处置方案列表
   */
  @Transactional
  public List<ActionProposal> generateDefaults(
      Incident incident,
      Long workflowInstanceId,
      DiagnosisEvidence diagnosis,
      List<RunbookDocument> runbooks,
      String severity
  ) {
    if (!actionProposalRepository.findByWorkflow(workflowInstanceId).isEmpty()) {
      // 工作流重试或重复查询时复用已生成方案，避免同一实例下重复插入三张方案卡片。
      return actionProposalRepository.findByWorkflow(workflowInstanceId);
    }
    // MVP 阶段使用确定性方案生成，保证演示和测试稳定。
    // 后续接 LLM 时也必须保留风险分级和人工审批门禁。
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
            RiskLevel.LOW,
            "当前已有告警和指标证据，低风险动作可用于补足定位材料。",
            evidenceJson,
            "不影响线上流量，只增加排障信息。",
            "确认日志采集不会暴露敏感字段。",
            false,
            ActionStatus.READY
        ),
        actionProposalRepository.create(
            incident.id(),
            workflowInstanceId,
            suggestedMediumTitle(incident),
            suggestedMediumType(incident),
            RiskLevel.MEDIUM,
            "诊断证据显示延迟和错误率升高，中风险缓解动作可降低用户影响。",
            evidenceJson,
            "可能导致部分请求延迟处理，需要人工确认业务影响。",
            "确认不会造成重复支付、重复下单或状态不一致。",
            true,
            ActionStatus.PENDING
        ),
        actionProposalRepository.create(
            incident.id(),
            workflowInstanceId,
            "回滚最近发布或切换生产配置",
            "ROLLBACK_OR_CONFIG_CHANGE",
            RiskLevel.HIGH,
            "如果中风险缓解无效，可考虑高风险变更，但系统只生成建议。",
            evidenceJson,
            "可能影响生产配置、版本和流量，需要 SRE/负责人审批。",
            "确认发布窗口、回滚版本、数据库兼容性和应急联系人。",
            true,
            ActionStatus.PENDING
        )
    );
  }

  /**
   * 审批指定处置方案，记录审批人信息和备注，并将方案状态更新为 APPROVED。
   *
   * @param actionId 方案 ID
   * @param request  审批请求（操作人 + 备注）
   * @return 更新后的处置方案
   */
  @Transactional
  public ActionProposalResponse approve(Long actionId, ActionDecisionRequest request) {
    ActionProposal proposal = findRequired(actionId);
    actionProposalRepository.createApproval(
        proposal.id(),
        proposal.incidentId(),
        ApprovalDecision.APPROVED,
        request.comment(),
        request.approvedBy()
    );
    actionProposalRepository.updateStatus(actionId, ActionStatus.APPROVED);
    return ActionProposalResponse.from(findRequired(actionId));
  }

  /**
   * 驳回指定处置方案，记录驳回原因和操作人，并将方案状态更新为 REJECTED。
   *
   * @param actionId 方案 ID
   * @param request  驳回请求（操作人 + 原因）
   * @return 更新后的处置方案
   */
  @Transactional
  public ActionProposalResponse reject(Long actionId, ActionDecisionRequest request) {
    ActionProposal proposal = findRequired(actionId);
    actionProposalRepository.createApproval(
        proposal.id(),
        proposal.incidentId(),
        ApprovalDecision.REJECTED,
        request.comment(),
        request.approvedBy()
    );
    actionProposalRepository.updateStatus(actionId, ActionStatus.REJECTED);
    return ActionProposalResponse.from(findRequired(actionId));
  }

  /**
   * 升级指定处置方案转交上级处理，记录升级人信息，并将方案状态更新为 ESCALATED。
   *
   * @param actionId 方案 ID
   * @param request  升级请求（操作人 + 备注）
   * @return 更新后的处置方案
   */
  @Transactional
  public ActionProposalResponse escalate(Long actionId, ActionDecisionRequest request) {
    ActionProposal proposal = findRequired(actionId);
    actionProposalRepository.createApproval(
        proposal.id(),
        proposal.incidentId(),
        ApprovalDecision.ESCALATED,
        request.comment(),
        request.approvedBy()
    );
    actionProposalRepository.updateStatus(actionId, ActionStatus.ESCALATED);
    return ActionProposalResponse.from(findRequired(actionId));
  }

  /**
   * 标记处置方案已在线下执行完成（委托给 {@link #recordResult}）。
   *
   * @param actionId 方案 ID
   * @param request  线下执行请求（执行人 + 结果详情）
   * @return 更新后的处置方案
   */
  @Transactional
  public ActionProposalResponse markOfflineExecuted(Long actionId, MarkOfflineExecutedRequest request) {
    return recordResult(actionId, request);
  }

  /**
   * 回填处置方案的线下执行结果。
   *
   * <p>该方法会校验冲突（同一故障单不能执行多个不相关的方案）、记录审批和动作记录、
   * 更新方案状态、将其他待选方案标记为 NOT_SELECTED，并将故障单状态更新为 RECOVERING。</p>
   *
   * @param actionId 方案 ID
   * @param request  执行结果请求（执行人 + 结果详情）
   * @return 更新后的处置方案
   */
  @Transactional
  public ActionProposalResponse recordResult(Long actionId, MarkOfflineExecutedRequest request) {
    ActionProposal proposal = findRequired(actionId);
    List<ActionProposal> executedActions = actionProposalRepository.findExecutedByIncident(proposal.incidentId());
    if (!executedActions.isEmpty() && executedActions.stream().noneMatch(action -> action.id().equals(actionId))) {
      ActionProposal selected = executedActions.getFirst();
      throw ApiException.conflict("该故障单已选择处置方案: " + selected.title());
    }
    if (ActionStatus.NOT_SELECTED.equals(proposal.status()) || ActionStatus.REJECTED.equals(proposal.status())) {
      throw ApiException.conflict("该处置方案不可选择: " + actionId);
    }
    // 这里只记录“人已经在线下执行”的结果，系统绝不直接调用生产回滚、SQL、扩缩容或配置变更接口。
    actionProposalRepository.createApproval(
        proposal.id(),
        proposal.incidentId(),
        ApprovalDecision.MARK_OFFLINE_EXECUTED,
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
    actionProposalRepository.updateStatus(actionId, ActionStatus.OFFLINE_EXECUTED);
    actionProposalRepository.markUnselectedActions(proposal.incidentId(), actionId);
    Incident incident = incidentRepository.updateStatus(proposal.incidentId(), IncidentStatus.RECOVERING);
    incidentMetricsService.recordRecoveringSnapshot(incident, proposal);
    return ActionProposalResponse.from(findRequired(actionId));
  }

  /**
   * 根据 ID 查找处置方案，如果不存在则抛出异常。
   *
   * @param actionId 方案 ID
   * @return 处置方案
   * @throws com.example.incidentcopilot.common.ApiException 如果方案不存在
   */
  public ActionProposal findRequired(Long actionId) {
    return actionProposalRepository.findById(actionId)
        .orElseThrow(() -> ApiException.notFound("Action proposal not found: " + actionId));
  }

  /**
   * 根据故障内容关键词生成中风险方案的推荐标题。
   *
   * @param incident 故障单
   * @return 中风险方案标题
   */
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

  /**
   * 根据故障内容关键词生成中风险方案的动作编码。
   *
   * @param incident 故障单
   * @return 动作编码
   */
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

  /**
   * 将故障单的标题、服务名、端点、异常类型和摘要拼接为用于关键词匹配的文本。
   *
   * @param incident 故障单
   * @return 拼接后的待匹配文本
   */
  private String incidentText(Incident incident) {
    return String.join(" ",
        valueOrEmpty(incident.title()),
        valueOrEmpty(incident.serviceName()),
        valueOrEmpty(incident.endpoint()),
        valueOrEmpty(incident.exceptionType()),
        valueOrEmpty(incident.summary())
    );
  }

  /**
   * 检查字符串中是否包含任意一个指定的关键词（不区分大小写）。
   *
   * @param value   待检查字符串
   * @param needles 关键词列表
   * @return 如果包含任意关键词则返回 true
   */
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

  /**
   * 将可能为 null 的字符串转换为空字符串。
   *
   * @param value 原始字符串，可为 null
   * @return 原值或空字符串
   */
  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
