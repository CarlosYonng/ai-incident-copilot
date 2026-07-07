package com.example.incidentcopilot.incident;

import java.time.LocalDateTime;

/**
 * 故障单核心领域记录。
 *
 * <p>该 record 与 {@code incident} 表字段保持一致，只承载数据；生命周期判断放在 Service 和工作流节点中。</p>
 *
 * @param id 数据库主键
 * @param incidentNo 面向值班人员展示的故障编号
 * @param title 简短故障标题
 * @param serviceName 受影响服务
 * @param endpoint 受影响接口或任务名称
 * @param severity 业务严重等级，例如 P0/P1/P2/P3
 * @param status 生命周期状态，例如 OPEN、WORKFLOW_RUNNING、RECOVERING、FAILED、CLOSED
 * @param source 创建来源，例如 MANUAL、DEMO、ALERT
 * @param traceId 用于日志关联的链路追踪 ID
 * @param exceptionType 观察到的异常或失败类型
 * @param summary 告警摘要或人工补充上下文
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 * @param closedAt 关闭时间
 */
public record Incident(
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
}
