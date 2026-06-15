package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.incident.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEngine {
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
      workflowRepository.markSuccess(context.workflowInstanceId());
      incidentRepository.updateStatus(context.incident().id(), "OPEN");
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
      NodeResult result = node.execute(context);
      executionId = workflowRepository.createNodeExecution(
          context.workflowInstanceId(),
          node.name(),
          result.nodeType(),
          jdbcJson.stringify(result.input())
      );
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      workflowRepository.markNodeSuccess(executionId, jdbcJson.stringify(result.output()), durationMs);
    } catch (RuntimeException exception) {
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      if (executionId == null) {
        executionId = workflowRepository.createNodeExecution(
            context.workflowInstanceId(),
            node.name(),
            node.nodeType(),
            jdbcJson.emptyObject()
        );
      }
      workflowRepository.markNodeFailed(executionId, exception.getMessage(), durationMs);
      throw ApiException.workflowFailed(node.name() + " failed: " + exception.getMessage());
    }
  }

  private String firstNodeName() {
    return nodes.isEmpty() ? null : nodes.getFirst().name();
  }
}
