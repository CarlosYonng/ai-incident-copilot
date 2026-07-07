package com.example.incidentcopilot.alert;

import com.example.incidentcopilot.incident.IncidentResponse;

/**
 * 告警入站内部处理结果。
 *
 * <p>包含已持久化的告警事件以及关联或创建的故障单信息，供上层编排使用。</p>
 *
 * @param event    已持久化的告警事件
 * @param incident 关联的故障单信息（可为 null，表示告警被忽略）
 */
public record AlertIngestResult(
    AlertEvent event,
    IncidentResponse incident
) {
}
