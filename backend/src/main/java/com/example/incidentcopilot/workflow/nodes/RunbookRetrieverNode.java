package com.example.incidentcopilot.workflow.nodes;

import com.example.incidentcopilot.diagnosis.DiagnosisEvidence;
import com.example.incidentcopilot.runbook.RunbookDocument;
import com.example.incidentcopilot.runbook.RunbookRetriever;
import com.example.incidentcopilot.workflow.NodeResult;
import com.example.incidentcopilot.workflow.WorkflowContext;
import com.example.incidentcopilot.workflow.WorkflowNode;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
/**
 * Runbook 检索节点。
 *
 * <p>将 Incident 和诊断摘要转换为检索关键词，命中本地 Markdown Runbook 后写入上下文。</p>
 */
public class RunbookRetrieverNode implements WorkflowNode {
  /** Runbook 检索器，用于根据故障信息和诊断摘要搜索本地 Markdown 运维手册。 */
  private final RunbookRetriever runbookRetriever;

  /**
   * 构造 Runbook 检索节点。
   *
   * @param runbookRetriever Runbook 检索器
   */
  public RunbookRetrieverNode(RunbookRetriever runbookRetriever) {
    this.runbookRetriever = runbookRetriever;
  }

  @Override
  public String name() {
    return "RunbookRetrieverNode";
  }

  @Override
  public String nodeType() {
    return "RUNBOOK";
  }

  /**
   * 执行 Runbook 检索逻辑。
   *
   * <p>从工作流上下文读取诊断证据，提取摘要作为检索关键词，调用 RunbookRetriever
   * 搜索匹配的运维手册，并将结果写入工作流上下文的 "runbooks" 键。</p>
   *
   * @param context 工作流上下文，需包含 "diagnosis" 键
   * @return 节点执行结果，输出为匹配的 Runbook 文档列表概览
   */
  @Override
  public NodeResult execute(WorkflowContext context) {
    DiagnosisEvidence diagnosis = (DiagnosisEvidence) context.get("diagnosis");
    String summary = diagnosis == null ? "" : diagnosis.summary();
    List<RunbookDocument> runbooks = runbookRetriever.search(context.incident(), summary);
    context.put("runbooks", runbooks);
    return new NodeResult(
        nodeType(),
        Map.of("incidentId", context.incident().id(), "diagnosisSummary", summary),
        Map.of("matches", runbooks.stream().map(this::compact).toList())
    );
  }

  /**
   * 将 RunbookDocument 压缩为摘要 Map，仅保留文件名、标题、匹配分数和摘要片段。
   *
   * @param document Runbook 文档对象
   * @return 包含 fileName、title、score、excerpt 的不可变 Map
   */
  private Map<String, Object> compact(RunbookDocument document) {
    return Map.of(
        "fileName", document.fileName(),
        "title", document.title(),
        "score", document.score(),
        "excerpt", document.excerpt()
    );
  }
}
