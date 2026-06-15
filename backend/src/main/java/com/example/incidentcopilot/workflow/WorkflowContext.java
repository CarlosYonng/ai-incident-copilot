package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.incident.Incident;
import java.util.HashMap;
import java.util.Map;

public class WorkflowContext {
  private final Long workflowInstanceId;
  private final Incident incident;
  private final Map<String, Object> attributes = new HashMap<>();

  public WorkflowContext(Long workflowInstanceId, Incident incident) {
    this.workflowInstanceId = workflowInstanceId;
    this.incident = incident;
  }

  public Long workflowInstanceId() {
    return workflowInstanceId;
  }

  public Incident incident() {
    return incident;
  }

  public void put(String key, Object value) {
    attributes.put(key, value);
  }

  public Object get(String key) {
    return attributes.get(key);
  }

  public Map<String, Object> attributes() {
    return Map.copyOf(attributes);
  }
}
