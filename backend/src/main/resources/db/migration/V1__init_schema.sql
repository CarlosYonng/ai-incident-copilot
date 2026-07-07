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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故障主记录及生命周期状态。';

CREATE TABLE IF NOT EXISTS workflow_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_id BIGINT NOT NULL COMMENT '本次工作流处理的故障单。',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一次故障处理工作流的运行实例。';

CREATE TABLE IF NOT EXISTS workflow_node_execution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  workflow_instance_id BIGINT NOT NULL COMMENT '所属工作流实例。',
  node_name VARCHAR(128) NOT NULL COMMENT '具体工作流节点 Bean 名称。',
  node_type VARCHAR(64) NOT NULL COMMENT '节点类型，例如 ALERT、METRICS、MCP、RUNBOOK、ACTION、APPROVAL。',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '节点执行状态：PENDING、RUNNING、SUCCESS、FAILED。',
  input_json JSON NULL COMMENT '节点执行入参快照。',
  output_json JSON NULL COMMENT '节点产出的结构化结果。',
  error_message TEXT NULL COMMENT '状态为 FAILED 时的失败信息。',
  started_at DATETIME NULL COMMENT '节点开始时间。',
  finished_at DATETIME NULL COMMENT '节点结束时间。',
  duration_ms BIGINT NULL COMMENT '节点执行耗时（毫秒）。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  CONSTRAINT fk_node_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_node_workflow (workflow_instance_id),
  KEY idx_node_status (status),
  KEY idx_node_name (node_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点可审计执行时间线。';

CREATE TABLE IF NOT EXISTS tool_call_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  workflow_instance_id BIGINT NOT NULL COMMENT '触发工具调用的工作流实例。',
  node_name VARCHAR(128) NOT NULL COMMENT '发起工具调用的工作流节点。',
  tool_name VARCHAR(128) NOT NULL COMMENT 'MCP 或外部工具名称。',
  request_json JSON NULL COMMENT '序列化后的工具请求体。',
  response_json JSON NULL COMMENT '序列化后的工具响应或 fallback 证据。',
  success TINYINT(1) NOT NULL DEFAULT 0 COMMENT '工具调用是否成功。',
  error_message TEXT NULL COMMENT '成功标志为 false 时的错误详情。',
  duration_ms BIGINT NULL COMMENT '工具调用耗时（毫秒）。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  CONSTRAINT fk_tool_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_tool_workflow (workflow_instance_id),
  KEY idx_tool_name (tool_name),
  KEY idx_tool_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 及外部工具调用的审计日志。';

CREATE TABLE IF NOT EXISTS action_proposal (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_id BIGINT NOT NULL COMMENT '处置方案所属故障单。',
  workflow_instance_id BIGINT NOT NULL COMMENT '生成该方案的工作流实例。',
  title VARCHAR(255) NOT NULL COMMENT '面向值班人员展示的方案标题。',
  action_type VARCHAR(64) NOT NULL COMMENT '前端和审计使用的稳定操作编码。',
  risk_level VARCHAR(16) NOT NULL COMMENT '风险等级：LOW、MEDIUM、HIGH。',
  reason TEXT NULL COMMENT '推荐该方案的原因。',
  evidence_json JSON NULL COMMENT '生成方案所依据的诊断、Runbook、严重等级等证据。',
  impact TEXT NULL COMMENT '预期业务或技术影响。',
  precheck TEXT NULL COMMENT '人工执行前需要确认的检查项。',
  requires_approval TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否需要人工审批。',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '方案状态：READY、PENDING、APPROVED、REJECTED、ESCALATED、OFFLINE_EXECUTED。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间。',
  CONSTRAINT fk_action_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  CONSTRAINT fk_action_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
  KEY idx_action_incident (incident_id),
  KEY idx_action_status (status),
  KEY idx_action_risk (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='带风险分级和审批门禁的候选处置方案。';

CREATE TABLE IF NOT EXISTS human_approval (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  action_proposal_id BIGINT NOT NULL COMMENT '被人工评审的处置方案。',
  incident_id BIGINT NOT NULL COMMENT '该决策所属故障单。',
  decision VARCHAR(32) NOT NULL COMMENT '决策类型：APPROVED、REJECTED、ESCALATED、MARK_OFFLINE_EXECUTED。',
  comment TEXT NULL COMMENT '人工操作备注。',
  approved_by VARCHAR(128) NOT NULL COMMENT '做出决策的人员。',
  approved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '决策时间。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  CONSTRAINT fk_approval_action FOREIGN KEY (action_proposal_id) REFERENCES action_proposal(id),
  CONSTRAINT fk_approval_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_approval_action (action_proposal_id),
  KEY idx_approval_incident (incident_id),
  KEY idx_approval_decision (decision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人工确认流程中的审批和决策历史。';

CREATE TABLE IF NOT EXISTS action_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_id BIGINT NOT NULL COMMENT '执行记录所属故障单。',
  action_proposal_id BIGINT NULL COMMENT '触发该执行记录的处置方案（如有）。',
  action_type VARCHAR(64) NOT NULL COMMENT '在线下执行的动作编码。',
  executor VARCHAR(128) NOT NULL COMMENT '执行动作的人员或外部系统。',
  result VARCHAR(32) NOT NULL COMMENT '执行结果，例如 SUCCESS 或 FAILED。',
  result_detail TEXT NULL COMMENT '自由格式的执行结果详情。',
  executed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '线下动作执行或回填时间。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  CONSTRAINT fk_record_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  CONSTRAINT fk_record_action FOREIGN KEY (action_proposal_id) REFERENCES action_proposal(id),
  KEY idx_record_incident (incident_id),
  KEY idx_record_action (action_proposal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='已审批人工动作的线下执行记录。';

CREATE TABLE IF NOT EXISTS postmortem_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_id BIGINT NOT NULL UNIQUE COMMENT '复盘对应的故障单；每个故障单一份复盘。',
  summary TEXT NULL COMMENT '简短故障摘要。',
  root_cause TEXT NULL COMMENT '根因分析。',
  impact TEXT NULL COMMENT '用户或业务影响。',
  timeline_json JSON NULL COMMENT '结构化故障时间线。',
  action_items_json JSON NULL COMMENT '后续行动项。',
  prevention_items_json JSON NULL COMMENT '预防类改进项。',
  report_content MEDIUMTEXT NULL COMMENT '渲染后的复盘报告正文。',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间。',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间。',
  CONSTRAINT fk_report_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_report_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统生成的复盘报告和结构化改进项。';

CREATE TABLE IF NOT EXISTS mock_metric_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '代理主键。',
  incident_id BIGINT NOT NULL COMMENT '指标快照所属故障单。',
  service_name VARCHAR(128) NOT NULL COMMENT '指标样本对应服务名。',
  error_rate DECIMAL(8, 4) NOT NULL COMMENT '演示使用的错误率。',
  p95_latency INT NOT NULL COMMENT 'P95 延迟（毫秒）。',
  qps INT NOT NULL COMMENT '每秒请求数。',
  status VARCHAR(32) NOT NULL COMMENT '指标状态：normal、degraded、recovering、recovered。',
  snapshot_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '指标采样时间。',
  CONSTRAINT fk_metric_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
  KEY idx_metric_incident_time (incident_id, snapshot_time),
  KEY idx_metric_service_status (service_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用于展示故障恢复过程的演示指标快照。';
