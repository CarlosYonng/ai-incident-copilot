package com.example.incidentcopilot.action;

import java.time.LocalDateTime;

/**
 * 处置方案响应对象，用于对外展示方案信息。
 *
 * <p>从 ActionProposal 实体映射而来，去除内部工作流字段。</p>
 *
 * @param id               方案主键 ID
 * @param title            方案标题
 * @param actionType       动作编码
 * @param riskLevel        风险等级
 * @param reason           推荐原因
 * @param evidenceJson     证据 JSON
 * @param impact           预期影响
 * @param precheck         预检查项
 * @param requiresApproval 是否需要审批
 * @param status           方案状态
 * @param createdAt        创建时间
 * @param updatedAt        最后更新时间
 */
public record ActionProposalResponse(
    Long id,
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
  /**
   * 从 ActionProposal 实体转换为响应对象。
   *
   * @param proposal 原始方案实体
   * @return 方案响应对象
   */
  public static ActionProposalResponse from(ActionProposal proposal) {
    return new ActionProposalResponse(
        proposal.id(),
        proposal.title(),
        proposal.actionType(),
        proposal.riskLevel(),
        proposal.reason(),
        proposal.evidenceJson(),
        proposal.impact(),
        proposal.precheck(),
        proposal.requiresApproval(),
        proposal.status(),
        proposal.createdAt(),
        proposal.updatedAt()
    );
  }
}
