package com.example.incidentcopilot.incident;

import com.example.incidentcopilot.metrics.MetricSnapshot;
import java.util.List;

public record IncidentDetailResponse(
    IncidentResponse incident,
    List<MetricSnapshot> metrics
) {
}
