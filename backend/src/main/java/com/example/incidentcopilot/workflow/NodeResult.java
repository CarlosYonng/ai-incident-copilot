package com.example.incidentcopilot.workflow;

import java.util.Map;

public record NodeResult(
    String nodeType,
    Map<String, Object> input,
    Map<String, Object> output
) {
}
