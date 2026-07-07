package com.example.incidentcopilot.incident;

import com.example.incidentcopilot.metrics.MetricSnapshot;
import java.util.List;

/**
 * 故障单详情响应体。
 *
 * <p>包含故障单基本信息和关联的指标快照列表，供详情页展示。</p>
 *
 * @param incident 故障单基本信息
 * @param metrics  关联的指标快照列表，按时间倒序排列
 */
public record IncidentDetailResponse(
    IncidentResponse incident,
    List<MetricSnapshot> metrics
) {
}
