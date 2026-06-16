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
public class RunbookRetrieverNode implements WorkflowNode {
  private final RunbookRetriever runbookRetriever;

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

  private Map<String, Object> compact(RunbookDocument document) {
    return Map.of(
        "fileName", document.fileName(),
        "title", document.title(),
        "score", document.score(),
        "excerpt", document.excerpt()
    );
  }
}
