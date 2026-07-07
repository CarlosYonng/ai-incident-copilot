package com.example.incidentcopilot.alert;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 告警入站请求 DTO。
 *
 * <p>包含告警的基本信息、服务指标以及是否启动工作流的标志。
 * 通过 Bean Validation 注解对字段进行校验。</p>
 *
 * @param eventId          外部系统告警事件唯一标识（必填，最长 128 字符）
 * @param source           告警来源，如 "internal"、"grafana"、"alertmanager"（必填，最长 64 字符）
 * @param signalName       告警信号名称，如 "HighErrorRate"（必填，最长 128 字符）
 * @param serviceName      所属服务名称（必填，最长 128 字符）
 * @param endpoint         发生告警的端点路径（最长 255 字符）
 * @param traceId          分布式追踪 ID（最长 128 字符）
 * @param exceptionType    异常类型（最长 128 字符）
 * @param summary          告警摘要描述
 * @param errorRate        错误率，最小 0.0
 * @param p95Latency       P95 延迟（毫秒），最小 0
 * @param qps              每秒查询数，最小 0
 * @param affectedRequests 受影响的请求数量，最小 0
 * @param severityHint     严重程度提示，如 "P0"、"P1"（最长 16 字符）
 * @param rawPayload       原始 webhook 负载（JSON 结构）
 * @param startWorkflow    是否在告警入站后自动启动工作流
 */
public record AlertIngestRequest(
    @NotBlank @Size(max = 128) String eventId,
    @NotBlank @Size(max = 64) String source,
    @NotBlank @Size(max = 128) String signalName,
    @NotBlank @Size(max = 128) String serviceName,
    @Size(max = 255) String endpoint,
    @Size(max = 128) String traceId,
    @Size(max = 128) String exceptionType,
    String summary,
    @DecimalMin("0.0") BigDecimal errorRate,
    @Min(0) Integer p95Latency,
    @Min(0) Integer qps,
    @Min(0) Integer affectedRequests,
    @Size(max = 16) String severityHint,
    Map<String, Object> rawPayload,
    Boolean startWorkflow
) {
}
