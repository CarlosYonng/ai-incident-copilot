package com.example.incidentcopilot.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 故障单创建请求体。
 *
 * <p>包含创建故障单所需的全部字段，其中 title 和 serviceName 为必填。</p>
 *
 * @param title         简短故障标题，最长 255 字符，不可为空
 * @param serviceName   受影响服务名，最长 128 字符，不可为空
 * @param endpoint      受影响接口或任务名称（可选）
 * @param source        创建来源，例如 MANUAL、DEMO、ALERT（可选）
 * @param traceId       用于日志关联的链路追踪 ID（可选）
 * @param exceptionType 观察到的异常或失败类型（可选）
 * @param summary       告警摘要或人工补充上下文（可选）
 */
public record IncidentCreateRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 128) String serviceName,
    @Size(max = 255) String endpoint,
    @Size(max = 64) String source,
    @Size(max = 128) String traceId,
    @Size(max = 128) String exceptionType,
    String summary
) {
}
