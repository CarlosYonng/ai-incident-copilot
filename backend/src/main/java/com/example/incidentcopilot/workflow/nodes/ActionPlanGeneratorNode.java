package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.action.ActionProposal;
import com.example.incidentcopilot.action.ActionProposalService;
import com.example.incidentcopilot.common.DomainConstants.Severity;
import com.example.incidentcopilot.diagnosis.DiagnosisEvidence;
import com.example.incidentcopilot.runbook.RunbookDocument;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(60)
/**
 * 处置方案生成节点。
 *
 * <p>读取前序节点写入的诊断证据、Runbook 和严重等级，生成带风险分级的候选处置方案。</p>
 */
public class ActionPlanGeneratorNode implements WorkflowNode {
  /** 处置方案生成服务，用于创建默认的候选处置动作集合。 */
  private final ActionProposalService actionProposalService;

  /**
   * 构造处置方案生成节点。
   *
   * @param actionProposalService 处置方案生成服务
   */
  public ActionPlanGeneratorNode(ActionProposalService actionProposalService) {
    this.actionProposalService = actionProposalService;
  }

  @Override
  public String name() {
    return "ActionPlanGeneratorNode";
  }

  @Override
  public String nodeType() {
    return "ACTION_PLAN";
  }

  /**
   * 执行处置方案生成逻辑。
   *
   * <p>从工作流上下文读取诊断证据、Runbook 列表及严重等级，调用 ActionProposalService
   * 生成候选处置方案列表并写回上下文。</p>
   *
   * @param context 工作流上下文，需包含 "diagnosis"、"runbooks" 及 "severity" 键
   * @return 节点执行结果，包含故障单 ID、严重等级及候选处置方案概览
   */
  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(WorkflowContext context) {
    // 处置方案集合会被后续风险复核节点继续读取，用于判断是否需要人工确认。
    DiagnosisEvidence diagnosis = (DiagnosisEvidence) context.get("diagnosis");
    List<RunbookDocument> runbooks = (List<RunbookDocument>) context.get("runbooks");
    String severity = context.getString("severity", Severity.P2);
    List<ActionProposal> proposals = actionProposalService.generateDefaults(
        context.incident(),
        context.workflowInstanceId(),
        diagnosis,
        runbooks == null ? List.of() : runbooks,
        severity
    );
    context.put("actionProposals", proposals);
    return new NodeResult(
        nodeType(),
        Map.of("incidentId", context.incident().id(), "severity", severity),
        Map.of("proposals", proposals.stream().map(this::compact).toList())
    );
  }

  /**
   * 将 ActionProposal 压缩为摘要 Map，仅保留关键字段用于日志输出。
   *
   * @param proposal 处置方案对象
   * @return 包含 id、title、riskLevel、requiresApproval、status 的不可变 Map
   */
  private Map<String, Object> compact(ActionProposal proposal) {
    return Map.of(
        "id", proposal.id(),
        "title", proposal.title(),
        "riskLevel", proposal.riskLevel(),
        "requiresApproval", proposal.requiresApproval(),
        "status", proposal.status()
    );
  }
}
