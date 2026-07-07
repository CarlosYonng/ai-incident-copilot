package com.example.incidentcopilot.alert;

import com.example.incidentcopilot.incident.IncidentResponse;
import com.example.incidentcopilot.workflow.WorkflowResponse;

/**
 * 告警入站响应 DTO。
 *
 * <p>包含告警事件详情、关联的故障单信息（若有）以及工作流启动结果（若请求启动）。</p>
 *
 * @param alertEvent 告警事件详情
 * @param incident   关联的故障单信息（可为 null）
 * @param workflow   工作流启动结果（可为 null）
 */
public record AlertIngestResponse(
    AlertEventResponse alertEvent,
    IncidentResponse incident,
    WorkflowResponse workflow
) {
}
