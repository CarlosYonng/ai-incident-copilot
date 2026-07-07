package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.action.ActionProposal;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(70)
/**
 * 风险复核节点。
 *
 * <p>检查候选处置方案中是否存在需要审批的中高风险动作，并把审批需求写入工作流上下文。</p>
 */
public class RiskReviewNode implements WorkflowNode {

  @Override
  public String name() {
    return "RiskReviewNode";
  }

  @Override
  public String nodeType() {
    return "RISK_REVIEW";
  }

  /**
   * 执行风险复核逻辑。
   *
   * <p>从工作流上下文读取 "actionProposals" 候选处置方案列表，过滤出所有需要审批的方案
   * （requiresApproval = true），并将审批需求标记写入上下文。输出包含是否需要审批、
   * 需要审批的处置方案 ID 列表及分级判定规则说明。</p>
   *
   * @param context 工作流上下文，需包含 "actionProposals" 键
   * @return 节点执行结果，包含审查结论和审批动作 ID 列表
   */
  @Override
  @SuppressWarnings("unchecked")
  public NodeResult execute(WorkflowContext context) {
    List<ActionProposal> proposals = (List<ActionProposal>) context.get("actionProposals");
    List<ActionProposal> approvalRequired = proposals == null
        ? List.of()
        : proposals.stream().filter(ActionProposal::requiresApproval).toList();
    context.put("approvalRequired", !approvalRequired.isEmpty());
    return new NodeResult(
        nodeType(),
        Map.of("proposalCount", proposals == null ? 0 : proposals.size()),
        Map.of(
            "approvalRequired", !approvalRequired.isEmpty(),
            "approvalActionIds", approvalRequired.stream().map(ActionProposal::id).toList(),
            "rule", "LOW 可直接记录；MEDIUM/HIGH 必须人工确认。"
        )
    );
  }
}
