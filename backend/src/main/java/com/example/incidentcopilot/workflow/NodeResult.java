package com.example.incidentcopilot.workflow;

import java.util.Map;

/**
 * 工作流节点的执行结果记录，封装了节点的输入、输出及节点类型信息。
 * <p>
 * 用于追踪工作流中每个节点的执行情况，便于后续分析、审计或跨节点数据传递。
 * </p>
 *
 * @param nodeType 节点类型标识（如："diagnosis"、"action_proposal" 等）
 * @param input    节点执行时的输入参数集合
 * @param output   节点执行后产生的输出数据集合
 */
public record NodeResult(
    String nodeType,
    Map<String, Object> input,
    Map<String, Object> output
) {
}
