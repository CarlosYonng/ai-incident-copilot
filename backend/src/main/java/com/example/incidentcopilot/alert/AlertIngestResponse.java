package com.example.incidentcopilot.alert;

import com.example.incidentcopilot.incident.IncidentResponse;
import com.example.incidentcopilot.workflow.WorkflowResponse;

public record AlertIngestResponse(
    AlertEventResponse alertEvent,
    IncidentResponse incident,
    WorkflowResponse workflow
) {
}
