package com.example.incidentcopilot.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警事件记录，表示一条从监控系统入站的原始告警。
 *
 * <p>包含告警的来源标识、信号信息、服务维度指标、严重程度推测以及原始负载等字段。
 * 一条告警可以关联到一个故障单（incident），也可独立存在。</p>
 *
 * @param id              主键 ID
 * @param eventId         外部系统告警事件唯一标识
 * @param incidentId      关联的故障单 ID（可为 null）
 * @param source          告警来源，如 "internal"、"grafana"、"alertmanager"
 * @param signalName      告警信号名称，如 "HighErrorRate"
 * @param serviceName     所属服务名称
 * @param endpoint        发生告警的端点路径
 * @param traceId         关联的分布式追踪 ID
 * @param exceptionType   异常类型（如 "HttpServerErrorException"）
 * @param summary         告警摘要描述
 * @param errorRate       错误率（0 ~ 1 之间的小数）
 * @param p95Latency      P95 延迟（毫秒），用于反映链路尾延迟
 * @param qps             每秒查询数
 * @param affectedRequests 受影响的请求数量
 * @param severityHint    严重程度提示，如 "P0"、"P1"
 * @param rawPayloadJson  原始 webhook 负载 JSON
 * @param status          告警状态，如 "open"、"resolved"、"ignored"
 * @param decisionReason  决策原因，记录为何关联/忽略该告警
 * @param receivedAt      系统收到告警的时间
 * @param createdAt       记录创建时间
 */
public record AlertEvent(
    Long id,
    String eventId,
    Long incidentId,
    String source,
    String signalName,
    String serviceName,
    String endpoint,
    String traceId,
    String exceptionType,
    String summary,
    BigDecimal errorRate,
    Integer p95Latency,
    Integer qps,
    Integer affectedRequests,
    String severityHint,
    String rawPayloadJson,
    String status,
    String decisionReason,
    LocalDateTime receivedAt,
    LocalDateTime createdAt
) {
}
