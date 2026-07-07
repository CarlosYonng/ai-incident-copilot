package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.incident.Incident;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
/**
 * 告警接收节点。
 *
 * <p>这是工作流时间线的第一步，用于把故障单基础字段固化为可审计输入输出。</p>
 */
public class AlertReceiverNode implements WorkflowNode {

  @Override
  public String name() {
    return "AlertReceiverNode";
  }

  @Override
  public String nodeType() {
    return "ALERT";
  }

  /**
   * 执行告警接收逻辑。
   *
   * <p>将 Incident 的基础字段（ID、编号、来源、TraceId）记录为输入，
   * 将标题、服务名、端点、摘要记录为输出，并写入工作流上下文的 "alert" 键。</p>
   *
   * @param context 工作流上下文，包含当前 Incident
   * @return 节点执行结果，输入为告警原始字段，输出为已接收的告警摘要
   */
  @Override
  public NodeResult execute(WorkflowContext context) {
    Incident incident = context.incident();
    Map<String, Object> input = Map.of(
        "incidentId", incident.id(),
        "incidentNo", incident.incidentNo(),
        "source", incident.source(),
        "traceId", incident.traceId() == null ? "" : incident.traceId()
    );
    Map<String, Object> output = Map.of(
        "accepted", true,
        "title", incident.title(),
        "serviceName", incident.serviceName(),
        "endpoint", incident.endpoint() == null ? "" : incident.endpoint(),
        "summary", incident.summary() == null ? "" : incident.summary()
    );
    context.put("alert", output);
    return new NodeResult(nodeType(), input, output);
  }
}
