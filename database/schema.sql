USE incident_copilot;

CREATE TABLE IF NOT EXISTS incident (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_no VARCHAR(64) NOT NULL UNIQUE COMMENT '人类可读的故障编号，例如 INC-20260703-ABC123。',
  title VARCHAR(255) NOT NULL COMMENT '控制台展示的简短故障标题。',
  service_name VARCHAR(128) NOT NULL COMMENT '受影响的服务名称。',
  endpoint VARCHAR(255) NULL COMMENT '受影响的 API 端点或任务名称（如有）。',
  severity VARCHAR(16) NOT NULL DEFAULT 'P2' COMMENT '业务严重等级：P0/P1/P2/P3。',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '故障生命周期状态：OPEN、WORKFLOW_RUNNING、RECOVERING、FAILED、CLOSED。',
  source VARCHAR(64) NOT NULL DEFAULT 'MANUAL' COMMENT '故障来源：MANUAL、DEMO、ALERT。',
  trace_id VARCHAR(128) NULL COMMENT '用于关联日志和 MCP 诊断证据的链路追踪标识。',
  exception_type VARCHAR(128) NULL COMMENT '观察到的异常或错误类型。',
  summary TEXT NULL COMMENT '自由格式的告警摘要或运维说明。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间。',
  closed_at DATETIME NULL COMMENT '故障关闭时间。',
  KEY idx_incident_service_status (service_name, status),
  KEY idx_incident_created_at (created_at),
  KEY idx_incident_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '故障主记录及生命周期状态。';

CREATE TABLE IF NOT EXISTS alert_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  event_id VARCHAR(128) NOT NULL UNIQUE COMMENT '上游事件 ID，用于入站幂等。',
  incident_id BIGINT NULL COMMENT '该告警创建或关联的故障单。',
  source VARCHAR(64) NOT NULL COMMENT '上游来源，例如 prometheus、apm、log-platform。',
  signal_name VARCHAR(128) NOT NULL COMMENT '业务或监控信号名称。',
  service_name VARCHAR(128) NOT NULL COMMENT '受影响的服务名称。',
  endpoint VARCHAR(255) NULL COMMENT '受影响的端点、任务或业务操作。',
  trace_id VARCHAR(128) NULL COMMENT '上游链路追踪或关联 ID。',
  exception_type VARCHAR(128) NULL COMMENT '观察到的异常类型。',
  summary TEXT NULL COMMENT '上游告警摘要。',
  error_rate DECIMAL(8, 4) NULL COMMENT '上游观测错误率。',
  p95_latency INT NULL COMMENT '上游观测 p95 延迟。',
  qps INT NULL COMMENT '上游观测请求速率。',
  affected_requests INT NULL COMMENT '告警窗口内受影响请求数。',
  severity_hint VARCHAR(16) NULL COMMENT '上游严重等级提示。',
  raw_payload_json JSON NULL COMMENT '上游原始 payload。',
  status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED' COMMENT '入站状态：RECEIVED、IGNORED、INCIDENT_CREATED、CORRELATED。',
  decision_reason TEXT NULL COMMENT '入站决策原因。',
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警进入系统时间。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  CONSTRAINT fk_alert_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_alert_incident (incident_id),
  KEY idx_alert_service_time (service_name, received_at),
  KEY idx_alert_trace_id (trace_id),
  KEY idx_alert_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '原始业务告警入站事件。';

CREATE TABLE IF NOT EXISTS workflow_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_id BIGINT NOT NULL COMMENT '该工作流实例处理的故障。',
  workflow_type VARCHAR(64) NOT NULL COMMENT '工作流定义名称，例如 IncidentHandlingWorkflow。',
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '工作流状态：CREATED、RUNNING、SUCCESS、WAITING_APPROVAL、FAILED。',
  current_node VARCHAR(128) NULL COMMENT '工作流运行时的当前节点名称。',
  started_at DATETIME NULL COMMENT '工作流开始时间。',
  finished_at DATETIME NULL COMMENT '工作流结束时间。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间。',
  CONSTRAINT fk_workflow_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_workflow_incident (incident_id),
  KEY idx_workflow_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '故障处理工作流的单次运行实例。';

CREATE TABLE IF NOT EXISTS workflow_node_execution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  workflow_instance_id BIGINT NOT NULL COMMENT '拥有该节点执行记录的工作流实例。',
  node_name VARCHAR(128) NOT NULL COMMENT '具体的工作流节点 Bean/名称。',
  node_type VARCHAR(64) NOT NULL COMMENT '节点分类：ALERT、METRICS、MCP、RUNBOOK、ACTION、APPROVAL。',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '节点执行状态：PENDING、RUNNING、SUCCESS、FAILED。',
  input_json JSON NULL COMMENT '节点使用的输入快照。',
  output_json JSON NULL COMMENT '节点产生的结构化输出。',
  error_message TEXT NULL COMMENT '状态为 FAILED 时的错误信息。',
  started_at DATETIME NULL COMMENT '节点开始时间。',
  finished_at DATETIME NULL COMMENT '节点结束时间。',
  duration_ms BIGINT NULL COMMENT '节点执行耗时（毫秒）。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  CONSTRAINT fk_node_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_node_workflow (workflow_instance_id),
  KEY idx_node_status (status),
  KEY idx_node_name (node_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '工作流节点的可审计执行时间线。';

CREATE TABLE IF NOT EXISTS tool_call_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  workflow_instance_id BIGINT NOT NULL COMMENT '触发工具调用的工作流实例。',
  node_name VARCHAR(128) NOT NULL COMMENT '调用工具的工作流节点名称。',
  tool_name VARCHAR(128) NOT NULL COMMENT 'MCP 或外部工具名称。',
  request_json JSON NULL COMMENT '序列化的工具请求载荷。',
  response_json JSON NULL COMMENT '序列化的工具响应或兜底证据。',
  success TINYINT(1) NOT NULL DEFAULT 0 COMMENT '工具调用是否成功。',
  error_message TEXT NULL COMMENT '成功标志为 false 时的错误详情。',
  duration_ms BIGINT NULL COMMENT '工具调用耗时（毫秒）。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  CONSTRAINT fk_tool_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_tool_workflow (workflow_instance_id),
  KEY idx_tool_name (tool_name),
  KEY idx_tool_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT 'MCP 及外部工具调用的审计日志。';

CREATE TABLE IF NOT EXISTS action_proposal (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_id BIGINT NOT NULL COMMENT '该处置方案所属的故障。',
  workflow_instance_id BIGINT NOT NULL COMMENT '生成该方案的工作流实例。',
  title VARCHAR(255) NOT NULL COMMENT '面向运维人员的操作标题。',
  action_type VARCHAR(64) NOT NULL COMMENT '前端和审计使用的稳定操作编码。',
  risk_level VARCHAR(16) NOT NULL COMMENT '风险等级：LOW、MEDIUM、HIGH。',
  reason TEXT NULL COMMENT '推荐该操作的理由。',
  evidence_json JSON NULL COMMENT '用于生成方案的诊断、Runbook、严重等级等证据。',
  impact TEXT NULL COMMENT '预期的业务或技术影响。',
  precheck TEXT NULL COMMENT '人工执行操作前需要完成的检查项。',
  requires_approval TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否需要人工审批步骤。',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '方案状态：READY、PENDING、APPROVED、REJECTED、ESCALATED、OFFLINE_EXECUTED。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间。',
  CONSTRAINT fk_action_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  CONSTRAINT fk_action_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_action_incident (incident_id),
  KEY idx_action_status (status),
  KEY idx_action_risk (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '带风险等级与审批门槛的候选修复操作。';

CREATE TABLE IF NOT EXISTS human_approval (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  action_proposal_id BIGINT NOT NULL COMMENT '人工审核的处置方案。',
  incident_id BIGINT NOT NULL COMMENT '该决策所属的故障。',
  decision VARCHAR(32) NOT NULL COMMENT '决策结果：APPROVED、REJECTED、ESCALATED、MARK_OFFLINE_EXECUTED。',
  comment TEXT NULL COMMENT '人工操作员的备注。',
  approved_by VARCHAR(128) NOT NULL COMMENT '做出决策的操作员。',
  approved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '决策时间。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  CONSTRAINT fk_approval_action FOREIGN KEY (action_proposal_id) REFERENCES action_proposal(id),
  CONSTRAINT fk_approval_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_approval_action (action_proposal_id),
  KEY idx_approval_incident (incident_id),
  KEY idx_approval_decision (decision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '人工在环（Human-in-the-loop）审批与决策记录。';

CREATE TABLE IF NOT EXISTS action_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_id BIGINT NOT NULL COMMENT '该执行记录所属的故障。',
  action_proposal_id BIGINT NULL COMMENT '引出该执行记录的方案（如有）。',
  action_type VARCHAR(64) NOT NULL COMMENT '在系统外部执行的操作编码。',
  executor VARCHAR(128) NOT NULL COMMENT '执行操作的外部人员或系统。',
  result VARCHAR(32) NOT NULL COMMENT '执行结果：SUCCESS 或 FAILED。',
  result_detail TEXT NULL COMMENT '自由格式的执行结果详情。',
  executed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '线下操作执行或记录的时间。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  CONSTRAINT fk_record_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  CONSTRAINT fk_record_action FOREIGN KEY (action_proposal_id) REFERENCES action_proposal(id),
  KEY idx_record_incident (incident_id),
  KEY idx_record_action (action_proposal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '已批准人工操作的线下执行记录。';

CREATE TABLE IF NOT EXISTS postmortem_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_id BIGINT NOT NULL UNIQUE COMMENT '该复盘报告对应的故障，每个故障一份报告。',
  summary TEXT NULL COMMENT '简短故障摘要。',
  root_cause TEXT NULL COMMENT '根因分析。',
  impact TEXT NULL COMMENT '对用户或业务的影响。',
  timeline_json JSON NULL COMMENT '结构化的故障时间线。',
  action_items_json JSON NULL COMMENT '后续改进行动项。',
  prevention_items_json JSON NULL COMMENT '预防性改进项。',
  report_content MEDIUMTEXT NULL COMMENT '渲染后的复盘报告内容。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间。',
  CONSTRAINT fk_report_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_report_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '已生成的复盘报告及结构化的改进项。';

CREATE TABLE IF NOT EXISTS mock_metric_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_id BIGINT NOT NULL COMMENT '该指标快照所属的故障。',
  service_name VARCHAR(128) NOT NULL COMMENT '指标样本对应的服务名称。',
  error_rate DECIMAL(8, 4) NOT NULL COMMENT '演示使用的错误率百分比。',
  p95_latency INT NOT NULL COMMENT 'P95 延迟（毫秒）。',
  qps INT NOT NULL COMMENT '每秒查询或请求数。',
  status VARCHAR(32) NOT NULL COMMENT '指标状态：normal、degraded、recovering、recovered。',
  snapshot_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '指标采样时间。',
  CONSTRAINT fk_metric_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_metric_incident_time (incident_id, snapshot_time),
  KEY idx_metric_service_status (service_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '用于可视化故障恢复过程的演示指标快照。';
