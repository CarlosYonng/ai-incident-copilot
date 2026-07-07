package com.example.incidentcopilot.action;

import jakarta.validation.constraints.NotBlank;

/**
 * 处置方案决策请求——用于审批、驳回和升级操作。
 *
 * @param approvedBy 操作人（必填）
 * @param comment    决策备注或原因
 */
public record ActionDecisionRequest(
    @NotBlank String approvedBy,
    String comment
) {
}
