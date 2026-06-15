CREATE DATABASE IF NOT EXISTS incident_copilot
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE incident_copilot;

CREATE TABLE IF NOT EXISTS incident (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  incident_no VARCHAR(64) NOT NULL UNIQUE,
  title VARCHAR(255) NOT NULL,
  service_name VARCHAR(128) NOT NULL,
  endpoint VARCHAR(255) NULL,
  severity VARCHAR(16) NOT NULL DEFAULT 'P2',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  source VARCHAR(64) NOT NULL DEFAULT 'MANUAL',
  trace_id VARCHAR(128) NULL,
  exception_type VARCHAR(128) NULL,
  summary TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  closed_at DATETIME NULL,
  KEY idx_incident_service_status (service_name, status),
  KEY idx_incident_created_at (created_at),
  KEY idx_incident_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS workflow_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  incident_id BIGINT NOT NULL,
  workflow_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  current_node VARCHAR(128) NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_workflow_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_workflow_incident (incident_id),
  KEY idx_workflow_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS workflow_node_execution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_instance_id BIGINT NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  node_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  input_json JSON NULL,
  output_json JSON NULL,
  error_message TEXT NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  duration_ms BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_node_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_node_workflow (workflow_instance_id),
  KEY idx_node_status (status),
  KEY idx_node_name (node_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tool_call_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_instance_id BIGINT NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  tool_name VARCHAR(128) NOT NULL,
  request_json JSON NULL,
  response_json JSON NULL,
  success TINYINT(1) NOT NULL DEFAULT 0,
  error_message TEXT NULL,
  duration_ms BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tool_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_tool_workflow (workflow_instance_id),
  KEY idx_tool_name (tool_name),
  KEY idx_tool_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS action_proposal (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  incident_id BIGINT NOT NULL,
  workflow_instance_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  action_type VARCHAR(64) NOT NULL,
  risk_level VARCHAR(16) NOT NULL,
  reason TEXT NULL,
  evidence_json JSON NULL,
  impact TEXT NULL,
  precheck TEXT NULL,
  requires_approval TINYINT(1) NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_action_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  CONSTRAINT fk_action_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_action_incident (incident_id),
  KEY idx_action_status (status),
  KEY idx_action_risk (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS human_approval (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  action_proposal_id BIGINT NOT NULL,
  incident_id BIGINT NOT NULL,
  decision VARCHAR(32) NOT NULL,
  comment TEXT NULL,
  approved_by VARCHAR(128) NOT NULL,
  approved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_approval_action FOREIGN KEY (action_proposal_id) REFERENCES action_proposal(id),
  CONSTRAINT fk_approval_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_approval_action (action_proposal_id),
  KEY idx_approval_incident (incident_id),
  KEY idx_approval_decision (decision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS action_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  incident_id BIGINT NOT NULL,
  action_proposal_id BIGINT NULL,
  action_type VARCHAR(64) NOT NULL,
  executor VARCHAR(128) NOT NULL,
  result VARCHAR(32) NOT NULL,
  result_detail TEXT NULL,
  executed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_record_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  CONSTRAINT fk_record_action FOREIGN KEY (action_proposal_id) REFERENCES action_proposal(id),
  KEY idx_record_incident (incident_id),
  KEY idx_record_action (action_proposal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS postmortem_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  incident_id BIGINT NOT NULL UNIQUE,
  summary TEXT NULL,
  root_cause TEXT NULL,
  impact TEXT NULL,
  timeline_json JSON NULL,
  action_items_json JSON NULL,
  prevention_items_json JSON NULL,
  report_content MEDIUMTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_report_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_report_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mock_metric_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  incident_id BIGINT NOT NULL,
  service_name VARCHAR(128) NOT NULL,
  error_rate DECIMAL(8, 4) NOT NULL,
  p95_latency INT NOT NULL,
  qps INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  snapshot_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_metric_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_metric_incident_time (incident_id, snapshot_time),
  KEY idx_metric_service_status (service_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

