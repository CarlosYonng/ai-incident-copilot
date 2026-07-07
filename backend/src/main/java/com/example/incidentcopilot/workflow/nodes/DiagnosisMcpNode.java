package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.diagnosis.DiagnosisEvidence;
import com.example.incidentcopilot.diagnosis.DiagnosisMcpClient;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
/**
 * MCP 诊断节点。
 *
 * <p>通过 DiagnosisMcpClient 调用 diagnosis-service 的日志、代码、工单和报告工具，并把证据写入上下文。</p>
 */
public class DiagnosisMcpNode implements WorkflowNode {
  /** MCP 诊断客户端，用于调用 diagnosis-service 的日志、代码、工单等分析工具收集证据。 */
  private final DiagnosisMcpClient diagnosisMcpClient;

  /**
   * 构造 MCP 诊断节点。
   *
   * @param diagnosisMcpClient MCP 诊断客户端
   */
  public DiagnosisMcpNode(DiagnosisMcpClient diagnosisMcpClient) {
    this.diagnosisMcpClient = diagnosisMcpClient;
  }

  @Override
  public String name() {
    return "DiagnosisMcpNode";
  }

  @Override
  public String nodeType() {
    return "MCP";
  }

  /**
   * 执行 MCP 诊断逻辑。
   *
   * <p>从 Incident 中提取服务名、TraceId 和异常类型作为输入参数，
   * 调用 DiagnosisMcpClient 收集诊断证据，并将结果写入工作流上下文的 "diagnosis" 键。
   * 输出包含摘要、日志、代码提示、关联工单、报告 ID 和是否使用了降级策略。</p>
   *
   * @param context 工作流上下文，包含当前 Incident
   * @return 节点执行结果，输出为诊断证据的关键字段
   */
  @Override
  public NodeResult execute(WorkflowContext context) {
    Map<String, Object> input = Map.of(
        "serviceName", context.incident().serviceName(),
        "traceId", context.incident().traceId() == null ? "" : context.incident().traceId(),
        "exceptionType", context.incident().exceptionType() == null ? "" : context.incident().exceptionType()
    );
    DiagnosisEvidence evidence = diagnosisMcpClient.collectEvidence(context.workflowInstanceId(), name(), context.incident());
    context.put("diagnosis", evidence);
    Map<String, Object> output = Map.of(
        "summary", evidence.summary(),
        "logs", evidence.logs(),
        "codeHints", evidence.codeHints(),
        "tickets", evidence.tickets(),
        "reportId", evidence.reportId(),
        "fallbackUsed", evidence.fallbackUsed()
    );
    return new NodeResult(nodeType(), input, output);
  }
}
