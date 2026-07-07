package com.example.incidentcopilot.incident;

import jakarta.validation.constraints.NotBlank;

/**
 * 故障单关闭请求体。
 *
 * <p>包含关闭人和可选备注信息。</p>
 *
 * @param closedBy 关闭人，不可为空
 * @param comment  关闭备注（可选）
 */
public record IncidentCloseRequest(
    @NotBlank String closedBy,
    String comment
) {
}
