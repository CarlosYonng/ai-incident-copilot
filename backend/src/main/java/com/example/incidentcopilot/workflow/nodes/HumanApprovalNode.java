package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.common.DomainConstants.IncidentStatus;
import com.example.incidentcopilot.common.DomainConstants.WorkflowContextKey;
import com.example.incidentcopilot.common.DomainConstants.WorkflowStatus;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(80)
/**
 * 人工确认节点。
 *
 * <p>根据风险复核节点的判断决定工作流终态；存在中高风险动作时，故障单进入等待人工确认。</p>
 */
public class HumanApprovalNode implements WorkflowNode {

  @Override
  public String name() {
    return "HumanApprovalNode";
  }

  @Override
  public String nodeType() {
    return "HUMAN_APPROVAL";
  }

  /**
   * 执行人工确认逻辑。
   *
   * <p>从工作流上下文读取 "approvalRequired" 标记。若需要审批，则将工作流状态和故障单状态
   * 均置为 "待确认"（WAITING_APPROVAL），指示运维人员线下执行并回填结果；否则仅记录信息。</p>
   *
   * @param context 工作流上下文，需包含 "approvalRequired" 布尔值
   * @return 节点执行结果，包含最终状态和说明消息
   */
  @Override
  public NodeResult execute(WorkflowContext context) {
    boolean approvalRequired = Boolean.TRUE.equals(context.get("approvalRequired"));
    if (approvalRequired) {
      // 中高风险动作只进入“待确认”，实际执行必须由人在线下完成并回填结果。
      context.put(WorkflowContextKey.WORKFLOW_FINAL_STATUS, WorkflowStatus.WAITING_APPROVAL);
      context.put(WorkflowContextKey.INCIDENT_FINAL_STATUS, IncidentStatus.WAITING_APPROVAL);
    }
    return new NodeResult(
        nodeType(),
        Map.of("incidentId", context.incident().id()),
        Map.of(
            "status", approvalRequired ? "WAITING_APPROVAL" : "NO_APPROVAL_REQUIRED",
            "message", approvalRequired
                ? "中高风险处置方案已生成，等待人工确认。"
                : "仅包含低风险建议，可直接记录。"
        )
    );
  }
}
