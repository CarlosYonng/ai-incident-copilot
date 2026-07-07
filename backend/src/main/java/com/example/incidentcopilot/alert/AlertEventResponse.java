package com.example.incidentcopilot.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警事件响应 DTO，返回给客户端的告警事件信息。
 *
 * <p>与 {@link AlertEvent} 相比去掉了 rawPayloadJson 和 createdAt 等内部字段，
 * 仅暴露对外可见的事件属性。</p>
 *
 * @param id              告警事件 ID
 * @param eventId         外部系统告警事件唯一标识
 * @param incidentId      关联的故障单 ID
 * @param source          告警来源
 * @param signalName      告警信号名称
 * @param serviceName     所属服务名称
 * @param endpoint        端点路径
 * @param traceId         分布式追踪 ID
 * @param exceptionType   异常类型
 * @param summary         告警摘要
 * @param errorRate       错误率
 * @param p95Latency      P95 延迟（毫秒）
 * @param qps             每秒查询数
 * @param affectedRequests 受影响请求数
 * @param severityHint    严重程度提示
 * @param status          告警状态
 * @param decisionReason  决策原因
 * @param receivedAt      系统收到时间
 */
public record AlertEventResponse(
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
    String status,
    String decisionReason,
    LocalDateTime receivedAt
) {
  /**
   * 从 {@link AlertEvent} 实体创建响应 DTO。
   *
   * @param event 告警事件实体
   * @return 告警事件响应 DTO
   */
  public static AlertEventResponse from(AlertEvent event) {
    return new AlertEventResponse(
        event.id(),
        event.eventId(),
        event.incidentId(),
        event.source(),
        event.signalName(),
        event.serviceName(),
        event.endpoint(),
        event.traceId(),
        event.exceptionType(),
        event.summary(),
        event.errorRate(),
        event.p95Latency(),
        event.qps(),
        event.affectedRequests(),
        event.severityHint(),
        event.status(),
        event.decisionReason(),
        event.receivedAt()
    );
  }
}
