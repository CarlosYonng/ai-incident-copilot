package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.common.DomainConstants.Severity;
import com.example.incidentcopilot.incident.IncidentRepository;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
/**
 * 严重等级分类节点。
 *
 * <p>基于可解释规则给 Incident 标记 P1/P2/P3，后续处置方案生成会读取该等级。</p>
 */
public class SeverityClassifierNode implements WorkflowNode {
  /** 故障单持久化仓库，用于更新故障的严重等级。 */
  private final IncidentRepository incidentRepository;

  /**
   * 构造严重等级分类节点。
   *
   * @param incidentRepository 故障单持久化仓库
   */
  public SeverityClassifierNode(IncidentRepository incidentRepository) {
    this.incidentRepository = incidentRepository;
  }

  @Override
  public String name() {
    return "SeverityClassifierNode";
  }

  @Override
  public String nodeType() {
    return "SEVERITY";
  }

  /**
   * 执行严重等级分类逻辑。
   *
   * <p>通过可解释规则对故障进行分类（P1/P2/P3），将分类结果持久化到数据库，
   * 并写入工作流上下文的 "severity" 键。输出包含严重等级和判定理由。</p>
   *
   * @param context 工作流上下文，包含当前 Incident
   * @return 节点执行结果，包含严重等级和判定理由说明
   */
  @Override
  public NodeResult execute(WorkflowContext context) {
    String severity = classify(context);
    incidentRepository.updateSeverity(context.incident().id(), severity);
    context.put("severity", severity);
    return new NodeResult(
        nodeType(),
        Map.of(
            "title", context.incident().title(),
            "serviceName", context.incident().serviceName(),
            "exceptionType", context.incident().exceptionType() == null ? "" : context.incident().exceptionType()
        ),
        Map.of(
            "severity", severity,
            "reason", reason(severity)
        )
    );
  }

  /**
   * 基于可解释规则对故障进行严重等级分类。
   *
   * <p>同时检查标题、摘要和异常类型文本，通过关键词匹配判定等级：支付或超时相关为 P1，
   * 空指针或 500 错误为 P2，其余为 P3。此规则便于演示和单测稳定，后续可接入 LLM 分类。</p>
   *
   * @param context 工作流上下文，包含 Incident 信息
   * @return 严重等级字符串（P1 / P2 / P3）
   */
  private String classify(WorkflowContext context) {
    String text = (context.incident().title() + " " + context.incident().summary() + " " + context.incident().exceptionType()).toLowerCase();
    // 这里先用可解释规则做分级，便于演示和单测稳定；接入 LLM 后也应输出同样的 P1/P2/P3 字典值。
    if (text.contains("payment") || text.contains("支付") || text.contains("timeout")) {
      return Severity.P1;
    }
    if (text.contains("nullpointer") || text.contains("空指针") || text.contains("500")) {
      return Severity.P2;
    }
    return Severity.P3;
  }

  /**
   * 根据严重等级返回中文判定理由说明。
   *
   * @param severity 严重等级（P1 / P2 / P3）
   * @return 对应的中文解释文本
   */
  private String reason(String severity) {
    return switch (severity) {
      case Severity.P1 -> "支付链路或核心链路异常，错误率和延迟升高，需要快速缓解。";
      case Severity.P2 -> "单服务核心接口异常，需要排查并生成处置建议。";
      default -> "影响有限，保持观察并补充证据。";
    };
  }
}
