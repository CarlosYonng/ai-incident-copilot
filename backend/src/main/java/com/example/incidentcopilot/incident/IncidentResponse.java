package com.example.incidentcopilot.incident;

import java.time.LocalDateTime;

/**
 * 故障单响应体。
 *
 * <p>对外暴露的故障单数据结构，与 Incident record 字段一致，用于 API 返回。</p>
 *
 * @param id            数据库主键
 * @param incidentNo    故障编号
 * @param title         故障标题
 * @param serviceName   受影响服务
 * @param endpoint      受影响接口或任务名称
 * @param severity      严重等级，如 P0/P1/P2/P3
 * @param status        生命周期状态
 * @param source        创建来源
 * @param traceId       链路追踪 ID
 * @param exceptionType 异常类型
 * @param summary       故障摘要
 * @param createdAt     创建时间
 * @param updatedAt     最后更新时间
 * @param closedAt      关闭时间
 */
public record IncidentResponse(
    Long id,
    String incidentNo,
    String title,
    String serviceName,
    String endpoint,
    String severity,
    String status,
    String source,
    String traceId,
    String exceptionType,
    String summary,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime closedAt
) {
  /**
   * 将 Incident 记录转换为 IncidentResponse。
   *
   * @param incident 故障单核心记录
   * @return 对应的故障单响应体
   */
  public static IncidentResponse from(Incident incident) {
    return new IncidentResponse(
        incident.id(),
        incident.incidentNo(),
        incident.title(),
        incident.serviceName(),
        incident.endpoint(),
        incident.severity(),
        incident.status(),
        incident.source(),
        incident.traceId(),
        incident.exceptionType(),
        incident.summary(),
        incident.createdAt(),
        incident.updatedAt(),
        incident.closedAt()
    );
  }
}
