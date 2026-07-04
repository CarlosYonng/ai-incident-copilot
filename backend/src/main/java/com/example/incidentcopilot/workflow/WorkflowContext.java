package com.example.incidentcopilot.workflow;

import com.example.incidentcopilot.incident.Incident;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory state shared by workflow nodes during one synchronous workflow run.
 *
 * <p>Durable audit data is persisted by repositories. This context only carries
 * transient values such as collected metrics, diagnosis evidence, runbooks, and
 * final status hints between nodes.</p>
 */
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

  public String getString(String key, String defaultValue) {
    Object value = attributes.get(key);
    return value == null ? defaultValue : String.valueOf(value);
  }

  public Map<String, Object> attributes() {
    return Map.copyOf(attributes);
  }
}
