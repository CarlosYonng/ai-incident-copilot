package com.example.incidentcopilot.workflow;

public interface WorkflowNode {
  String name();

  String nodeType();

  NodeResult execute(WorkflowContext context);
}
