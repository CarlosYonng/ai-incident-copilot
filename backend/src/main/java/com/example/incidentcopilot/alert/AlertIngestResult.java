package com.example.incidentcopilot.alert;

import com.example.incidentcopilot.incident.IncidentResponse;

public record AlertIngestResult(
    AlertEvent event,
    IncidentResponse incident
) {
}
