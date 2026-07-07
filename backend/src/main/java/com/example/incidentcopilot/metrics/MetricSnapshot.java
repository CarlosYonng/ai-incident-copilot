package com.example.incidentcopilot.metrics;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 与故障单关联的演示指标快照。
 *
 * <p>快照用于驱动控制台展示降级、恢复中、已恢复等状态，不替代 Prometheus 这类真实指标后端。</p>
 *
 * @param id 数据库主键
 * @param incidentId 所属故障单 ID
 * @param serviceName 指标对应服务名称
 * @param errorRate 错误率
 * @param p95Latency p95 延迟，单位毫秒
 * @param qps 每秒请求数
 * @param status 指标状态：normal、degraded、recovering、recovered
 * @param snapshotTime 采样时间
 */
public record MetricSnapshot(
    Long id,
    Long incidentId,
    String serviceName,
    BigDecimal errorRate,
    Integer p95Latency,
    Integer qps,
    String status,
    LocalDateTime snapshotTime
) {
}
