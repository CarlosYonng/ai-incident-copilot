package com.example.incidentcopilot.action;

import java.time.LocalDateTime;

/**
 * 基于诊断证据、指标快照和 Runbook 生成的候选处置方案。
 *
 * <p>系统只记录建议、审批和线下执行结果，不直接调用生产回滚、SQL、扩缩容或配置变更接口。</p>
 *
 * @param id 数据库主键
 * @param incidentId 所属故障单 ID
 * @param workflowInstanceId 生成该方案的工作流实例 ID
 * @param title 面向值班人员展示的方案标题
 * @param actionType 稳定动作编码，用于前端展示和审计记录
 * @param riskLevel 风险等级：LOW、MEDIUM、HIGH
 * @param reason 推荐该方案的原因
 * @param evidenceJson 生成方案时引用的诊断、Runbook、严重等级等证据
 * @param impact 预期业务或技术影响
 * @param precheck 执行前需要人工确认的检查项
 * @param requiresApproval 是否需要人工审批
 * @param status 方案状态，例如 READY、PENDING、APPROVED、REJECTED、ESCALATED、OFFLINE_EXECUTED
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 */
public record ActionProposal(
    Long id,
    Long incidentId,
    Long workflowInstanceId,
    String title,
    String actionType,
    String riskLevel,
    String reason,
    String evidenceJson,
    String impact,
    String precheck,
    boolean requiresApproval,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
