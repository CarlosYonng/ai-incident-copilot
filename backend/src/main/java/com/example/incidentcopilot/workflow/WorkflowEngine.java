package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.common.ApiException;
import com.example.incidentcopilot.common.DomainConstants.IncidentStatus;
import com.example.incidentcopilot.common.DomainConstants.WorkflowContextKey;
import com.example.incidentcopilot.common.DomainConstants.WorkflowStatus;
import com.example.incidentcopilot.common.JdbcJson;
import com.example.incidentcopilot.incident.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工作流引擎，负责按顺序编排并执行工作流节点。
 * <p>
 * 读取所有已注册的 {@link WorkflowNode} 实现类，按 {@link org.springframework.core.annotation.Order} 排序后依次执行。
 * 执行过程中记录每个节点的输入/输出、耗时及状态，失败时自动标记工作流实例为失败。
 * </p>
 */
@Component
public class WorkflowEngine {
  private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

  /** 按顺序排列的所有工作流节点 */
  private final List<WorkflowNode> nodes;
  /** 工作流实例持久化仓库 */
  private final WorkflowRepository workflowRepository;
  /** 事件持久化仓库 */
  private final IncidentRepository incidentRepository;
  /** JSON 序列化/反序列化工具 */
  private final JdbcJson jdbcJson;

  /**
   * 构造工作流引擎，按 {@link org.springframework.core.annotation.Order} 值升序排序所有工作流节点。
   *
   * @param nodes               所有已注册的工作流节点
   * @param workflowRepository  工作流实例仓库
   * @param incidentRepository  事件仓库
   * @param jdbcJson            JSON 工具
   */
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

  /**
   * 运行整个工作流：依次执行每个节点，执行完成后更新工作流实例和事件的最终状态。
   * <p>
   * 任何节点抛出异常都会导致工作流实例标记为失败，事件状态更新为 {@code FAILED}。
   * </p>
   *
   * @param context 工作流运行时上下文
   * @return 执行完毕的工作流实例
   * @throws ApiException 工作流执行失败时抛出
   */
  public WorkflowInstance run(WorkflowContext context) {
    workflowRepository.markRunning(context.workflowInstanceId(), firstNodeName());
    try {
      for (WorkflowNode node : nodes) {
        runNode(context, node);
      }
      // 节点可以决定最终业务状态。例如 HumanApprovalNode 让技术编排结束，
      // 但 Incident 仍停在等待人工确认，避免系统自动执行中高风险动作。
      String workflowStatus = context.getString(WorkflowContextKey.WORKFLOW_FINAL_STATUS, WorkflowStatus.SUCCESS);
      String incidentStatus = context.getString(WorkflowContextKey.INCIDENT_FINAL_STATUS, IncidentStatus.OPEN);
      workflowRepository.markFinished(context.workflowInstanceId(), workflowStatus);
      incidentRepository.updateStatus(context.incident().id(), incidentStatus);
      return workflowRepository.findInstance(context.workflowInstanceId())
          .orElseThrow(() -> ApiException.notFound("Workflow not found: " + context.workflowInstanceId()));
    } catch (RuntimeException exception) {
      workflowRepository.markFailed(context.workflowInstanceId(), null);
      incidentRepository.updateStatus(context.incident().id(), IncidentStatus.FAILED);
      throw exception;
    }
  }

  /**
   * 执行单个工作流节点，并记录执行时间线（开始时间、输入/输出、耗时及状态）。
   * <p>
   * 节点执行成功时创建节点执行记录并标记成功；失败时补充创建执行记录并标记失败。
   * </p>
   *
   * @param context 工作流运行时上下文
   * @param node    待执行的节点
   * @throws ApiException 节点执行失败时抛出
   */
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
      // 节点成功后再落库，审计时间线可以保留节点真实产生的输入/输出。
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
        // 节点还没返回 NodeResult 就失败时，也补一条失败记录，便于排查和后续重试。
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

  /**
   * 返回第一个节点的名称，用于工作流实例启动时设置当前节点。
   *
   * @return 第一个节点的名称，若没有节点则返回 null
   */
  private String firstNodeName() {
    return nodes.isEmpty() ? null : nodes.getFirst().name();
  }
}
