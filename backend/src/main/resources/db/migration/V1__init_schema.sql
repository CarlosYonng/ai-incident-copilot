CREATE TABLE IF NOT EXISTS incident (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Surrogate primary key.',
  incident_no VARCHAR(64) NOT NULL UNIQUE COMMENT 'Human-readable incident number, for example INC-20260703-ABC123.',
  title VARCHAR(255) NOT NULL COMMENT 'Short incident title shown in the console.',
  service_name VARCHAR(128) NOT NULL COMMENT 'Affected service name.',
  endpoint VARCHAR(255) NULL COMMENT 'Affected API endpoint or job name when available.',
  severity VARCHAR(16) NOT NULL DEFAULT 'P2' COMMENT 'Business severity such as P0/P1/P2/P3.',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'Incident lifecycle status: OPEN, WORKFLOW_RUNNING, RECOVERING, FAILED, CLOSED.',
  source VARCHAR(64) NOT NULL DEFAULT 'MANUAL' COMMENT 'Incident source such as MANUAL, DEMO, ALERT.',
  trace_id VARCHAR(128) NULL COMMENT 'Trace identifier used to correlate logs and MCP diagnosis evidence.',
  exception_type VARCHAR(128) NULL COMMENT 'Observed exception or failure class.',
  summary TEXT NULL COMMENT 'Free-form alert summary or operator context.',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp.',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
  closed_at DATETIME NULL COMMENT 'Timestamp when the incident is closed.',
  KEY idx_incident_service_status (service_name, status),
  KEY idx_incident_created_at (created_at),
  KEY idx_incident_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Primary incident record and lifecycle state.';

CREATE TABLE IF NOT EXISTS workflow_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Surrogate primary key.',
  incident_id BIGINT NOT NULL COMMENT 'Incident handled by this workflow instance.',
  workflow_type VARCHAR(64) NOT NULL COMMENT 'Workflow definition name, for example IncidentHandlingWorkflow.',
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT 'Workflow status: CREATED, RUNNING, SUCCESS, WAITING_APPROVAL, FAILED.',
  current_node VARCHAR(128) NULL COMMENT 'Current node name while the workflow is running.',
  started_at DATETIME NULL COMMENT 'Workflow start timestamp.',
  finished_at DATETIME NULL COMMENT 'Workflow terminal timestamp.',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp.',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
  CONSTRAINT fk_workflow_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_workflow_incident (incident_id),
  KEY idx_workflow_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A single run of the incident handling workflow.';

CREATE TABLE IF NOT EXISTS workflow_node_execution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Surrogate primary key.',
  workflow_instance_id BIGINT NOT NULL COMMENT 'Workflow instance that owns this node execution.',
  node_name VARCHAR(128) NOT NULL COMMENT 'Concrete workflow node bean/name.',
  node_type VARCHAR(64) NOT NULL COMMENT 'Node category such as ALERT, METRICS, MCP, RUNBOOK, ACTION, APPROVAL.',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Node execution status: PENDING, RUNNING, SUCCESS, FAILED.',
  input_json JSON NULL COMMENT 'Input snapshot used by the node.',
  output_json JSON NULL COMMENT 'Structured output produced by the node.',
  error_message TEXT NULL COMMENT 'Failure message when status is FAILED.',
  started_at DATETIME NULL COMMENT 'Node start timestamp.',
  finished_at DATETIME NULL COMMENT 'Node finish timestamp.',
  duration_ms BIGINT NULL COMMENT 'Node execution duration in milliseconds.',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp.',
  CONSTRAINT fk_node_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_node_workflow (workflow_instance_id),
  KEY idx_node_status (status),
  KEY idx_node_name (node_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Auditable execution timeline for workflow nodes.';

CREATE TABLE IF NOT EXISTS tool_call_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Surrogate primary key.',
  workflow_instance_id BIGINT NOT NULL COMMENT 'Workflow instance that triggered the tool call.',
  node_name VARCHAR(128) NOT NULL COMMENT 'Workflow node that called the tool.',
  tool_name VARCHAR(128) NOT NULL COMMENT 'MCP or external tool name.',
  request_json JSON NULL COMMENT 'Serialized tool request payload.',
  response_json JSON NULL COMMENT 'Serialized tool response or fallback evidence.',
  success TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether the tool call succeeded.',
  error_message TEXT NULL COMMENT 'Error details when success is false.',
  duration_ms BIGINT NULL COMMENT 'Tool call duration in milliseconds.',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp.',
  CONSTRAINT fk_tool_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_tool_workflow (workflow_instance_id),
  KEY idx_tool_name (tool_name),
  KEY idx_tool_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Audit log for MCP and external tool calls.';

CREATE TABLE IF NOT EXISTS action_proposal (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Surrogate primary key.',
  incident_id BIGINT NOT NULL COMMENT 'Incident this proposal belongs to.',
  workflow_instance_id BIGINT NOT NULL COMMENT 'Workflow instance that generated the proposal.',
  title VARCHAR(255) NOT NULL COMMENT 'Operator-facing action title.',
  action_type VARCHAR(64) NOT NULL COMMENT 'Stable action code used by UI and audit.',
  risk_level VARCHAR(16) NOT NULL COMMENT 'Risk level: LOW, MEDIUM, HIGH.',
  reason TEXT NULL COMMENT 'Why this action is recommended.',
  evidence_json JSON NULL COMMENT 'Diagnosis, runbook, severity, and other evidence used to generate the proposal.',
  impact TEXT NULL COMMENT 'Expected business or technical impact.',
  precheck TEXT NULL COMMENT 'Checks required before a human executes the action.',
  requires_approval TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether a human approval step is required.',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Proposal status: READY, PENDING, APPROVED, REJECTED, ESCALATED, OFFLINE_EXECUTED.',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp.',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
  CONSTRAINT fk_action_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  CONSTRAINT fk_action_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_action_incident (incident_id),
  KEY idx_action_status (status),
  KEY idx_action_risk (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Candidate remediation actions with risk and approval gates.';

CREATE TABLE IF NOT EXISTS human_approval (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Surrogate primary key.',
  action_proposal_id BIGINT NOT NULL COMMENT 'Action proposal reviewed by a human.',
  incident_id BIGINT NOT NULL COMMENT 'Incident this decision belongs to.',
  decision VARCHAR(32) NOT NULL COMMENT 'Decision: APPROVED, REJECTED, ESCALATED, MARK_OFFLINE_EXECUTED.',
  comment TEXT NULL COMMENT 'Human operator comment.',
  approved_by VARCHAR(128) NOT NULL COMMENT 'Operator who made the decision.',
  approved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Decision timestamp.',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp.',
  CONSTRAINT fk_approval_action FOREIGN KEY (action_proposal_id) REFERENCES action_proposal(id),
  CONSTRAINT fk_approval_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_approval_action (action_proposal_id),
  KEY idx_approval_incident (incident_id),
  KEY idx_approval_decision (decision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Human-in-the-loop approval and decision history.';

CREATE TABLE IF NOT EXISTS action_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Surrogate primary key.',
  incident_id BIGINT NOT NULL COMMENT 'Incident this execution record belongs to.',
  action_proposal_id BIGINT NULL COMMENT 'Proposal that led to this execution record, when available.',
  action_type VARCHAR(64) NOT NULL COMMENT 'Action code that was executed outside the system.',
  executor VARCHAR(128) NOT NULL COMMENT 'Human or external system that executed the action.',
  result VARCHAR(32) NOT NULL COMMENT 'Execution result such as SUCCESS or FAILED.',
  result_detail TEXT NULL COMMENT 'Free-form execution result detail.',
  executed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When the offline action was executed or recorded.',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp.',
  CONSTRAINT fk_record_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  CONSTRAINT fk_record_action FOREIGN KEY (action_proposal_id) REFERENCES action_proposal(id),
  KEY idx_record_incident (incident_id),
  KEY idx_record_action (action_proposal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Offline execution records for approved human actions.';

CREATE TABLE IF NOT EXISTS postmortem_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Surrogate primary key.',
  incident_id BIGINT NOT NULL UNIQUE COMMENT 'Incident summarized by this postmortem; one report per incident.',
  summary TEXT NULL COMMENT 'Short incident summary.',
  root_cause TEXT NULL COMMENT 'Root cause analysis.',
  impact TEXT NULL COMMENT 'User or business impact.',
  timeline_json JSON NULL COMMENT 'Structured incident timeline.',
  action_items_json JSON NULL COMMENT 'Follow-up action items.',
  prevention_items_json JSON NULL COMMENT 'Preventive improvement items.',
  report_content MEDIUMTEXT NULL COMMENT 'Rendered postmortem report content.',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp.',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp.',
  CONSTRAINT fk_report_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_report_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Generated postmortem report and structured improvement items.';

CREATE TABLE IF NOT EXISTS mock_metric_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Surrogate primary key.',
  incident_id BIGINT NOT NULL COMMENT 'Incident this metric snapshot belongs to.',
  service_name VARCHAR(128) NOT NULL COMMENT 'Service name for the metric sample.',
  error_rate DECIMAL(8, 4) NOT NULL COMMENT 'Error rate percentage used by the demo.',
  p95_latency INT NOT NULL COMMENT 'P95 latency in milliseconds.',
  qps INT NOT NULL COMMENT 'Queries or requests per second.',
  status VARCHAR(32) NOT NULL COMMENT 'Metric state: normal, degraded, recovering, recovered.',
  snapshot_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Metric sample timestamp.',
  CONSTRAINT fk_metric_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_metric_incident_time (incident_id, snapshot_time),
  KEY idx_metric_service_status (service_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Demo metric snapshots used to visualize incident recovery.';
