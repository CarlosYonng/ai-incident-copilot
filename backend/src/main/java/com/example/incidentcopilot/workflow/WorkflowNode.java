package com.example.incidentcopilot.workflow;

/**
 * 工作流的最小节点契约。
 *
 * <p>每个节点负责一个可审计业务步骤：从 {@link WorkflowContext} 读取上游结果，执行本节点逻辑，
 * 再通过 {@link NodeResult} 返回输入输出快照，供工作流引擎落库到节点时间线。</p>
 */
public interface WorkflowNode {
  /**
   * 节点唯一名称，通常使用具体类名，便于日志、审计和前端时间线定位。
   */
  String name();

  /**
   * 节点业务类型，例如 ALERT、METRICS、MCP、RUNBOOK、SEVERITY、ACTION_PLAN。
   */
  String nodeType();

  /**
   * 执行节点逻辑，并返回需要审计的输入输出。
   */
  NodeResult execute(WorkflowContext context);
}
