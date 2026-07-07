package com.example.incidentcopilot.action;

import jakarta.validation.constraints.NotBlank;

/**
 * 标记处置方案已线下执行的请求体。
 *
 * @param executor     线下执行人（必填）
 * @param resultDetail 执行结果详情
 */
public record MarkOfflineExecutedRequest(
    @NotBlank String executor,
    String resultDetail
) {
}
