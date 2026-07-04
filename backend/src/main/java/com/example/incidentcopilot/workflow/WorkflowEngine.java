package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.incident.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEngine {
  private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

  private final List<WorkflowNode> nodes;
  private final WorkflowRepository workflowRepository;
  private final IncidentRepository incidentRepository;
  private final JdbcJson jdbcJson;

  public WorkflowEngine(
      List<WorkflowNode> nodes,
      WorkflowRepository workflowRepository,
      IncidentRepository incidentRepository,
      JdbcJson jdbcJson
  ) {
    this.nodes = nodes.stream()
        .sorted(AnnotationAwareOrderComparator.INSTANCE)
        .toList();
    this.workflowRepository = workflowRepository;
    this.incidentRepository = incidentRepository;
    this.jdbcJson = jdbcJson;
  }

  public WorkflowInstance run(WorkflowContext context) {
    workflowRepository.markRunning(context.workflowInstanceId(), firstNodeName());
    try {
      for (WorkflowNode node : nodes) {
        runNode(context, node);
      }
      // Nodes can decide the terminal business state. For example, HumanApprovalNode
      // finishes the technical workflow but leaves the incident waiting for a person.
      String workflowStatus = context.getString("workflowFinalStatus", "SUCCESS");
      String incidentStatus = context.getString("incidentFinalStatus", "OPEN");
      workflowRepository.markFinished(context.workflowInstanceId(), workflowStatus);
      incidentRepository.updateStatus(context.incident().id(), incidentStatus);
      return workflowRepository.findInstance(context.workflowInstanceId())
          .orElseThrow(() -> ApiException.notFound("Workflow not found: " + context.workflowInstanceId()));
    } catch (RuntimeException exception) {
      workflowRepository.markFailed(context.workflowInstanceId(), null);
      incidentRepository.updateStatus(context.incident().id(), "FAILED");
      throw exception;
    }
  }

  private void runNode(WorkflowContext context, WorkflowNode node) {
    workflowRepository.updateCurrentNode(context.workflowInstanceId(), node.name());
    Instant started = Instant.now();
    Long executionId = null;
    try {
      log.info(
          "workflow_node_started workflowInstanceId={} node={} nodeType={}",
          context.workflowInstanceId(),
          node.name(),
          node.nodeType()
      );
      NodeResult result = node.execute(context);
      // Persist node execution after the node returns so the audit log contains
      // the exact input/output that the node produced for the demo timeline.
      executionId = workflowRepository.createNodeExecution(
          context.workflowInstanceId(),
          node.name(),
          result.nodeType(),
          jdbcJson.stringify(result.input())
      );
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      workflowRepository.markNodeSuccess(executionId, jdbcJson.stringify(result.output()), durationMs);
      log.info(
          "workflow_node_succeeded workflowInstanceId={} node={} durationMs={}",
          context.workflowInstanceId(),
          node.name(),
          durationMs
      );
    } catch (RuntimeException exception) {
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      if (executionId == null) {
        // If a node fails before producing a NodeResult, still create a failed
        // execution row. This keeps the workflow debuggable and retry-friendly.
        executionId = workflowRepository.createNodeExecution(
            context.workflowInstanceId(),
            node.name(),
            node.nodeType(),
            jdbcJson.emptyObject()
        );
      }
      workflowRepository.markNodeFailed(executionId, exception.getMessage(), durationMs);
      log.error(
          "workflow_node_failed workflowInstanceId={} node={} durationMs={} message={}",
          context.workflowInstanceId(),
          node.name(),
          durationMs,
          exception.getMessage(),
          exception
      );
      throw ApiException.workflowFailed(node.name() + " failed: " + exception.getMessage());
    }
  }

  private String firstNodeName() {
    return nodes.isEmpty() ? null : nodes.getFirst().name();
  }
}
