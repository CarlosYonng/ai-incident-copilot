import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode: string | null;
  message: string | null;
  requestId: string;
};

type Incident = {
  id: number;
  incidentNo: string;
  title: string;
  serviceName: string;
  endpoint: string | null;
  severity: string;
  status: string;
  source: string;
  traceId: string | null;
  exceptionType: string | null;
  summary: string | null;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
};

type Workflow = {
  workflowInstanceId: number;
  incidentId: number;
  workflowType: string;
  status: string;
  currentNode: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};

type WorkflowNode = {
  id: number;
  nodeName: string;
  nodeType: string;
  status: string;
  durationMs: number | null;
  inputJson: string | null;
  outputJson: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};

type ToolCall = {
  id: number;
  nodeName: string;
  toolName: string;
  success: boolean;
  durationMs: number | null;
  requestJson: string | null;
  responseJson: string | null;
  errorMessage: string | null;
  createdAt: string;
};

type AlertEvent = {
  id: number;
  eventId: string;
  incidentId: number | null;
  source: string;
  signalName: string;
  serviceName: string;
  endpoint: string | null;
  traceId: string | null;
  exceptionType: string | null;
  summary: string | null;
  errorRate: number | null;
  p95Latency: number | null;
  qps: number | null;
  affectedRequests: number | null;
  severityHint: string | null;
  status: string;
  decisionReason: string | null;
  receivedAt: string;
};

type AlertIngestResponse = {
  alertEvent: AlertEvent;
  incident: Incident | null;
  workflow: Workflow | null;
};

type ActionProposal = {
  id: number;
  title: string;
  actionType: string;
  riskLevel: string;
  reason: string;
  impact: string;
  precheck: string;
  requiresApproval: boolean;
  status: string;
  createdAt: string;
  updatedAt: string | null;
};

type MetricSnapshot = {
  id: number;
  serviceName: string;
  errorRate: number;
  p95Latency: number;
  qps: number;
  status: string;
  snapshotTime: string;
};

type Postmortem = {
  incidentId: number;
  summary: string;
  rootCause: string;
  impact: string;
  actionItems: string[];
  preventionItems: string[];
  reportContent: string;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    ...init,
  });
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message ?? `Request failed: ${response.status}`);
  }
  return payload.data;
}

export default function App() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [selectedIncidentId, setSelectedIncidentId] = useState<number | null>(null);
  const [workflow, setWorkflow] = useState<Workflow | null>(null);
  const [nodes, setNodes] = useState<WorkflowNode[]>([]);
  const [toolCalls, setToolCalls] = useState<ToolCall[]>([]);
  const [alertEvents, setAlertEvents] = useState<AlertEvent[]>([]);
  const [actions, setActions] = useState<ActionProposal[]>([]);
  const [metrics, setMetrics] = useState<MetricSnapshot[]>([]);
  const [postmortem, setPostmortem] = useState<Postmortem | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedIncident = useMemo(
    () => incidents.find((incident) => incident.id === selectedIncidentId) ?? incidents[0] ?? null,
    [incidents, selectedIncidentId],
  );
  const selectedAction = useMemo(() => latestExecutedAction(actions), [actions]);
  const hasExecutedAction = Boolean(selectedAction);
  const pendingActionCount = actions.filter((action) => action.status === 'PENDING' || action.status === 'READY').length;
  const isClosed = selectedIncident?.status === 'CLOSED';
  const canStartWorkflow = Boolean(selectedIncident) && !workflow && !isClosed;
  const canGeneratePostmortem = Boolean(selectedIncident) && hasExecutedAction && !isClosed;
  const canCloseIncident = Boolean(selectedIncident) && Boolean(postmortem) && !isClosed;

  async function loadIncidents() {
    const data = await api<Incident[]>('/incidents');
    setIncidents(data);
    if (!selectedIncidentId && data.length > 0) {
      setSelectedIncidentId(data[0].id);
    }
  }

  async function loadIncidentArtifacts(incidentId: number) {
    const [nextActions, nextMetrics, nextAlertEvents] = await Promise.all([
      api<ActionProposal[]>(`/incidents/${incidentId}/actions`).catch(() => []),
      api<MetricSnapshot[]>(`/incidents/${incidentId}/metrics`).catch(() => []),
      api<AlertEvent[]>(`/incidents/${incidentId}/alerts`).catch(() => []),
    ]);
    setActions(nextActions);
    setMetrics(nextMetrics);
    setAlertEvents(nextAlertEvents);
    const nextPostmortem = await api<Postmortem>(`/incidents/${incidentId}/postmortem`).catch(() => null);
    setPostmortem(nextPostmortem);
    const latestWorkflow = await api<Workflow>(`/incidents/${incidentId}/workflow/latest`).catch(() => null);
    setWorkflow(latestWorkflow);
    if (latestWorkflow) {
      await loadWorkflowArtifacts(latestWorkflow.workflowInstanceId);
    } else {
      setNodes([]);
      setToolCalls([]);
    }
  }

  async function loadWorkflowArtifacts(workflowId: number) {
    const [nextNodes, nextToolCalls] = await Promise.all([
      api<WorkflowNode[]>(`/workflows/${workflowId}/nodes`),
      api<ToolCall[]>(`/workflows/${workflowId}/tool-calls`),
    ]);
    setNodes(nextNodes);
    setToolCalls(nextToolCalls);
  }

  async function runAction(action: () => Promise<void>) {
    setLoading(true);
    setError(null);
    try {
      await action();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  }

  async function createPaymentIncident() {
    const result = await api<AlertIngestResponse>('/alerts/ingest', {
      method: 'POST',
      body: JSON.stringify({
        eventId: eventId('payment-alert'),
        source: 'payment-gateway-apm',
        signalName: '支付回调超时',
        serviceName: 'payment-service',
        endpoint: '/api/payment/callback',
        traceId: 'trace-payment-timeout-001',
        exceptionType: 'TimeoutError',
        summary: '支付网关回调链路 5 分钟窗口内 500 错误率和超时数同时升高',
        errorRate: 0.082,
        p95Latency: 3200,
        qps: 1260,
        affectedRequests: 238,
        severityHint: 'P1',
        rawPayload: {
          businessOperation: 'payment_callback',
          gateway: 'sandbox-pay-gateway',
          alertWindow: '5m',
          timeoutCount: 238,
        },
        startWorkflow: false,
      }),
    });
    if (!result.incident) return;
    await loadIncidents();
    setSelectedIncidentId(result.incident.id);
    setWorkflow(null);
    await loadIncidentArtifacts(result.incident.id);
  }

  async function createOrderIncident() {
    const result = await api<AlertIngestResponse>('/alerts/ingest', {
      method: 'POST',
      body: JSON.stringify({
        eventId: eventId('order-alert'),
        source: 'order-api-error-monitor',
        signalName: '创建订单空指针',
        serviceName: 'order-service',
        endpoint: '/api/orders',
        traceId: 'trace-order-npe-001',
        exceptionType: 'NullPointerException',
        summary: '订单创建接口出现 userProfile 为空导致的 500 错误',
        errorRate: 0.036,
        p95Latency: 1180,
        qps: 880,
        affectedRequests: 42,
        severityHint: 'P2',
        rawPayload: {
          businessOperation: 'order_create',
          nullField: 'userProfile',
          alertWindow: '10m',
          errorCount: 42,
        },
        startWorkflow: false,
      }),
    });
    if (!result.incident) return;
    await loadIncidents();
    setSelectedIncidentId(result.incident.id);
    setWorkflow(null);
    await loadIncidentArtifacts(result.incident.id);
  }

  async function startWorkflow() {
    if (!selectedIncident) return;
    const nextWorkflow = await api<Workflow>(`/incidents/${selectedIncident.id}/start-workflow`, {
      method: 'POST',
    });
    setWorkflow(nextWorkflow);
    await Promise.all([
      loadWorkflowArtifacts(nextWorkflow.workflowInstanceId),
      loadIncidents(),
      loadIncidentArtifacts(selectedIncident.id),
    ]);
  }

  async function recordActionResult(action: ActionProposal) {
    await api<ActionProposal>(`/actions/${action.id}/record-result`, {
      method: 'POST',
      body: JSON.stringify({
        executor: 'sre-demo',
        resultDetail:
          action.riskLevel === 'LOW'
            ? `已采纳低风险方案「${action.title}」，作为当前 Incident 的处理记录。`
            : `已在线下通过值班系统执行「${action.title}」，本系统只记录处理结果和审计证据。`,
      }),
    });
    if (selectedIncident) {
      await Promise.all([loadIncidents(), loadIncidentArtifacts(selectedIncident.id)]);
    }
  }

  async function generatePostmortem() {
    if (!selectedIncident) return;
    const report = await api<Postmortem>(`/incidents/${selectedIncident.id}/generate-postmortem`, {
      method: 'POST',
      body: JSON.stringify({}),
    });
    setPostmortem(report);
  }

  async function closeIncident() {
    if (!selectedIncident) return;
    await api<Incident>(`/incidents/${selectedIncident.id}/close`, {
      method: 'POST',
      body: JSON.stringify({
        closedBy: 'demo-user',
        comment: '指标恢复，复盘已生成',
      }),
    });
    await Promise.all([loadIncidents(), loadIncidentArtifacts(selectedIncident.id)]);
  }

  useEffect(() => {
    runAction(loadIncidents);
  }, []);

  useEffect(() => {
    if (selectedIncident?.id) {
      loadIncidentArtifacts(selectedIncident.id);
    }
  }, [selectedIncident?.id]);

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Incident Response Agent Workflow</p>
          <h1>被关注告警响应编排台</h1>
        </div>
        <div className="actions">
          <button type="button" onClick={() => runAction(createPaymentIncident)} disabled={loading}>
            注入 portfolio 支付告警
          </button>
          <button type="button" onClick={() => runAction(createOrderIncident)} disabled={loading}>
            注入 portfolio 订单告警
          </button>
          <button type="button" onClick={() => runAction(startWorkflow)} disabled={loading || !canStartWorkflow}>
            {workflow ? 'Workflow 已启动' : '启动 Workflow'}
          </button>
        </div>
      </header>

      {error && <div className="alert">{error}</div>}

      <section className="summary-grid">
        <Summary label="故障单总数" value={String(incidents.length)} />
        <Summary label="当前状态" value={selectedIncident ? statusLabel(selectedIncident.status) : '-'} />
        <Summary label="最近 Workflow" value={workflow ? statusLabel(workflow.status) : '未启动'} />
        <Summary label="入站告警数" value={`${alertEvents.length} 条`} />
        <Summary label="待处理方案" value={`${pendingActionCount} 个`} />
      </section>

      <nav className="jump-nav" aria-label="页面导航">
        <a href="#incident-section">Incident</a>
        <a href="#signals-section">告警与指标</a>
        <a href="#actions-section">处置方案</a>
        <a href="#workflow-section">Workflow</a>
        <a href="#audit-section">工具审计</a>
        <a href="#postmortem-section">复盘</a>
      </nav>

      {selectedIncident && (
        <section className="panel process-strip">
          <div className="section-head">
            <h2>当前处理进度</h2>
            <span className="muted">从 portfolio 告警入站到处理复盘的 Incident 生命周期</span>
          </div>
          <div className="steps">
            {processSteps(selectedIncident, workflow, actions, postmortem, alertEvents).map((step) => (
              <div className={step.done ? 'step done' : 'step'} key={step.label}>
                <span>{step.label}</span>
                <strong>{step.done ? '已完成' : '待处理'}</strong>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="layout" id="incident-section">
        <aside className="panel incident-list">
          <h2>故障单列表</h2>
          {incidents.length === 0 ? (
            <p className="muted">暂无故障单</p>
          ) : (
            <div className="stack">
              {incidents.map((incident) => (
                <button
                  className={incident.id === selectedIncident?.id ? 'row selected' : 'row'}
                  key={incident.id}
                  type="button"
                  onClick={() => {
                    setSelectedIncidentId(incident.id);
                    setWorkflow(null);
                    setNodes([]);
                    setToolCalls([]);
                    setAlertEvents([]);
                  }}
                >
                  <span>{incident.incidentNo}</span>
                  <strong>{incident.title}</strong>
                  <small>
                    {incident.serviceName} · {severityLabel(incident.severity)} · {statusLabel(incident.status)}
                  </small>
                  <small>创建 {formatTime(incident.createdAt)}</small>
                </button>
              ))}
            </div>
          )}
        </aside>

        <section className="panel detail">
          <div className="section-head">
            <h2>Incident 详情</h2>
            {selectedIncident && <span className="badge">{statusLabel(selectedIncident.status)}</span>}
          </div>
          {selectedIncident ? (
            <div className="detail-grid">
              <Info label="编号" value={selectedIncident.incidentNo} />
              <Info label="服务" value={selectedIncident.serviceName} />
              <Info label="接口" value={selectedIncident.endpoint ?? '-'} />
              <Info label="等级" value={severityLabel(selectedIncident.severity)} />
              <Info label="状态" value={statusLabel(selectedIncident.status)} />
              <Info label="来源" value={sourceLabel(selectedIncident.source)} />
              <Info label="创建时间" value={formatTime(selectedIncident.createdAt)} />
              <Info label="更新时间" value={formatTime(selectedIncident.updatedAt)} />
              {selectedIncident.closedAt && <Info label="关闭时间" value={formatTime(selectedIncident.closedAt)} />}
              <Info label="Trace ID" value={selectedIncident.traceId ?? '-'} />
              <Info label="异常" value={selectedIncident.exceptionType ?? '-'} />
              <div className="wide">
                <Info label="摘要" value={selectedIncident.summary ?? '-'} />
              </div>
            </div>
          ) : (
            <p className="muted">先点击右上角告警入站，创建一条故障链路</p>
          )}
        </section>
      </section>

      <section className="board signals-board" id="signals-section">
        <Panel title="入站事件">
          <p className="panel-note">这里显示 Grafana / Alertmanager 或演示入口推来的被关注告警。全量异常证据在 diagnosis-service，当前系统只处理已经触发告警、需要闭环的事件。</p>
          {alertEvents.length === 0 ? (
            <p className="muted">暂无业务告警事件</p>
          ) : (
            <div className="event-list">
              {alertEvents.slice(0, 4).map((event) => (
                <article className="event-card" key={event.id}>
                  <div className="card-head">
                    <strong>{event.signalName}</strong>
                    <span className="badge">{statusLabel(event.status)}</span>
                  </div>
                  <p>{event.summary ?? '-'}</p>
                  <small>
                    {formatTime(event.receivedAt)} · {sourceLabel(event.source)}
                  </small>
                  <small>
                    错误率 {event.errorRate == null ? '-' : percent(event.errorRate)} · p95{' '}
                    {event.p95Latency ?? '-'}ms · QPS {event.qps ?? '-'} · 影响请求 {event.affectedRequests ?? '-'}
                  </small>
                  {event.decisionReason && <small>入站决策：{decisionLabel(event.decisionReason)}</small>}
                </article>
              ))}
            </div>
          )}
        </Panel>

        <Panel title="指标快照">
          <p className="panel-note">指标快照来自入站告警携带的观测值，以及 Workflow、处置结果、关闭阶段写入的状态样本，用来解释故障是否在恶化或恢复。</p>
          {metrics.length === 0 ? (
            <p className="muted">暂无指标</p>
          ) : (
            <div className="metric-grid">
              {metrics.slice(0, 4).map((metric, index) => (
                <article className="metric" key={metric.id}>
                  <strong>{statusLabel(metric.status)}</strong>
                  <span>{metricSource(metric, index, metrics, selectedAction)}</span>
                  <span>采样时间 {formatTime(metric.snapshotTime)}</span>
                  <span>错误率 {percent(metric.errorRate)}</span>
                  <span>p95 延迟 {metric.p95Latency}ms</span>
                  <span>QPS {metric.qps}</span>
                </article>
              ))}
            </div>
          )}
        </Panel>
      </section>

      <section className="panel actions-panel" id="actions-section">
        <div className="section-head">
          <div>
            <h2>处置方案</h2>
            <p className="panel-note">Workflow 基于 diagnosis-service 的 MCP 诊断证据、Runbook 和风险规则生成候选方案。一次 Incident 只能采用一个方案；采用后其它方案会锁定为未采用，复盘也只围绕最终采用方案生成。</p>
          </div>
          {selectedAction && <span className="badge">已采用：{selectedAction.title}</span>}
        </div>
        {hasExecutedAction && <div className="success-note">已记录处理结果，故障单进入恢复观察。其它候选方案已锁定，避免同一 Incident 出现多套互相冲突的处理记录。</div>}
        {actions.length === 0 ? (
          <p className="muted">启动 Workflow 后生成候选方案</p>
        ) : (
          <div className="action-list">
            {actions.map((action) => {
              const isSelected = selectedAction?.id === action.id;
              const isLockedBySelection = Boolean(selectedAction) && !isSelected;
              const selectable = !isLockedBySelection && action.status !== 'OFFLINE_EXECUTED' && action.status !== 'NOT_SELECTED';
              return (
                <article className={isSelected ? 'action-card selected-action' : isLockedBySelection ? 'action-card locked-action' : 'action-card'} key={action.id}>
                  <div className="card-head">
                    <strong>{action.title}</strong>
                    <span className={`risk risk-${action.riskLevel.toLowerCase()}`}>{riskLabel(action.riskLevel)}</span>
                  </div>
                  <p>{action.reason}</p>
                  <small>影响：{action.impact}</small>
                  <small>前置检查：{action.precheck}</small>
                  <small>生成时间：{formatTime(action.createdAt)}</small>
                  {isSelected && <small>复盘依据：最终采用方案。</small>}
                  {isLockedBySelection && (
                    <small>
                      未采用原因：本 Incident 已记录其它处理方案
                      {action.status === 'OFFLINE_EXECUTED' ? '，这条属于历史重复记录，不作为复盘依据。' : '。'}
                    </small>
                  )}
                  <div className="card-actions">
                    <span>{actionStatusLabel(action, selectedAction)}</span>
                    {selectable && (
                      <button
                        type="button"
                        onClick={() => runAction(() => recordActionResult(action))}
                        disabled={loading}
                      >
                        {action.riskLevel === 'LOW' ? '采纳并记录' : '记录处理结果'}
                      </button>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <section className="panel workflow" id="workflow-section">
        <div className="section-head">
          <h2>Workflow 时间线</h2>
          {workflow && <span className="badge">{statusLabel(workflow.status)}</span>}
        </div>
        <p className="panel-note">时间线按节点真实执行顺序展示：每个节点会消费前面节点放入上下文的证据，并把自己的输入输出落库，方便复盘。</p>
        {nodes.length === 0 ? (
          <p className="muted">暂无 Workflow 时间线。手动注入告警后，可点击右上角“启动 Workflow”。</p>
        ) : (
          <div className="timeline">
            {nodes.map((node) => (
              <article className="node" key={node.id}>
                <div className="node-main">
                  <strong>{nodeLabel(node.nodeName).title}</strong>
                  <span>{nodeLabel(node.nodeName).description}</span>
                  <small>{formatTime(node.startedAt)} 至 {formatTime(node.finishedAt)}</small>
                </div>
                <div>
                  <b>{statusLabel(node.status)}</b>
                  <small>{node.durationMs ?? 0} ms</small>
                </div>
                <div className="node-output">
                  <strong>关键结果</strong>
                  <span>{nodeSummary(node)}</span>
                  <details>
                    <summary>查看节点输出 JSON</summary>
                    <pre>{formatJson(node.outputJson)}</pre>
                  </details>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="board">
        <section className="panel" id="audit-section">
          <h2>诊断工具审计</h2>
          <p className="panel-note">这里记录 Workflow 调用外部诊断工具的过程。每条审计会标明查询对象、证据规模和是否使用兜底，方便判断 AI 建议的证据来源是否可靠。</p>
          {toolCalls.length === 0 ? (
            <p className="muted">暂无工具调用记录</p>
          ) : (
            <div className="audit-list">
              {toolCalls.map((call) => (
                <article className="audit" key={call.id}>
                  <div>
                    <strong>{toolLabel(call.toolName)}</strong>
                    <span>{toolCallSummary(call)}</span>
                    {!call.success && call.errorMessage && <small>失败原因：{call.errorMessage}</small>}
                  </div>
                  <div className="audit-status">
                    <b>{call.success ? 'MCP 成功' : '兜底证据'}</b>
                    <small>{formatTime(call.createdAt)} · {call.durationMs ?? 0} ms</small>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>

        <section className="panel" id="postmortem-section">
          <h2>复盘报告</h2>
          <p className="panel-note">复盘只基于最终采用的处置方案、Workflow 时间线和工具审计生成。推荐顺序：先记录处理结果，再生成复盘，最后关闭 Incident。</p>
          <div className="report-actions">
            <button type="button" onClick={() => runAction(generatePostmortem)} disabled={loading || !canGeneratePostmortem}>
              {postmortem ? '重新生成复盘' : '生成复盘'}
            </button>
            <button type="button" onClick={() => runAction(closeIncident)} disabled={loading || !canCloseIncident}>
              {isClosed ? '已关闭归档' : '关闭 Incident'}
            </button>
          </div>
          {postmortem ? (
            <article className="report">
              <span className="badge">{isClosed ? '已归档' : '已生成，待关闭'}</span>
              <strong>{postmortem.summary}</strong>
              <p>{postmortem.rootCause}</p>
              <ul>
                {postmortem.actionItems.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </article>
          ) : (
            <p className="muted">处理动作完成后生成结构化复盘</p>
          )}
        </section>
      </section>
    </main>
  );
}

function Summary({ label, value }: { label: string; value: string }) {
  return (
    <div className="summary">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="panel">
      <h2>{title}</h2>
      {children}
    </section>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="info">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function eventId(prefix: string) {
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${random}`;
}

function processSteps(
  incident: Incident,
  workflow: Workflow | null,
  actions: ActionProposal[],
  postmortem: Postmortem | null,
  alertEvents: AlertEvent[],
) {
  return [
    { label: '告警入站', done: alertEvents.length > 0 },
    { label: '创建故障单', done: Boolean(incident) },
    { label: '执行 Workflow', done: Boolean(workflow) },
    { label: '生成人工方案', done: actions.length > 0 },
    { label: '记录处理结果', done: actions.some((action) => action.status === 'OFFLINE_EXECUTED') },
    { label: '生成复盘', done: Boolean(postmortem) },
    { label: '关闭归档', done: incident.status === 'CLOSED' },
  ];
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    OPEN: '待处理',
    WORKFLOW_RUNNING: 'Workflow 执行中',
    WAITING_APPROVAL: '等待人工确认',
    RECOVERING: '恢复观察中',
    FAILED: '失败',
    CLOSED: '已关闭',
    CREATED: '已创建',
    RUNNING: '执行中',
    SUCCESS: '成功',
    PENDING: '待确认',
    READY: '可记录',
    APPROVED: '已批准',
    REJECTED: '已驳回',
    ESCALATED: '已升级',
    OFFLINE_EXECUTED: '已记录处理',
    NOT_SELECTED: '未采用',
    RECEIVED: '已接收',
    IGNORED: '已忽略',
    INCIDENT_CREATED: '已创建故障单',
    CORRELATED: '已关联故障单',
    degraded: '异常中',
    recovering: '恢复中',
    recovered: '已恢复',
    normal: '正常',
  };
  return labels[status] ?? status;
}

function latestExecutedAction(actions: ActionProposal[]) {
  return actions
    .filter((action) => action.status === 'OFFLINE_EXECUTED')
    .sort((left, right) => actionTime(right) - actionTime(left))[0] ?? null;
}

function actionTime(action: ActionProposal) {
  return new Date(action.updatedAt ?? action.createdAt).getTime();
}

function actionStatusLabel(action: ActionProposal, selectedAction: ActionProposal | null) {
  if (selectedAction && action.id !== selectedAction.id && action.status === 'OFFLINE_EXECUTED') {
    return '历史重复，未采用';
  }
  return statusLabel(action.status);
}

function severityLabel(severity: string) {
  const labels: Record<string, string> = {
    P0: 'P0 最高优先级',
    P1: 'P1 核心链路故障',
    P2: 'P2 服务级故障',
    P3: 'P3 观察处理',
  };
  return labels[severity] ?? severity;
}

function riskLabel(risk: string) {
  const labels: Record<string, string> = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
  };
  return labels[risk] ?? risk;
}

function sourceLabel(source: string) {
  const labels: Record<string, string> = {
    'payment-gateway-apm': '支付网关 APM',
    'order-api-error-monitor': '订单接口错误监控',
    'demo-payment-gateway': '支付网关演示事件',
    'demo-order-api': '订单接口演示事件',
    grafana: 'Grafana Alerting',
    alertmanager: 'Prometheus Alertmanager',
    portfolio: 'AI Agent Portfolio',
    DEMO: '演示来源',
    MANUAL: '人工创建',
  };
  return labels[source] ?? source;
}

function decisionLabel(reason: string) {
  if (reason.includes('crossed incident thresholds')) {
    return '达到故障阈值，已创建新的 Incident';
  }
  if (reason.includes('Matched active incident')) {
    return '命中已有未关闭 Incident，已关联到同一故障';
  }
  if (reason.includes('did not cross thresholds')) {
    return '未达到故障阈值，仅保留入站事件';
  }
  return reason;
}

function nodeLabel(nodeName: string) {
  const labels: Record<string, { title: string; description: string }> = {
    AlertReceiverNode: {
      title: '接收告警',
      description: '读取 Incident 的服务、接口、Trace 和摘要，确认这条故障进入 Workflow。',
    },
    MetricsCollectorNode: {
      title: '采集指标',
      description: '记录当前错误率、p95 延迟和 QPS，用于判断故障是否仍在异常状态。',
    },
    DiagnosisMcpNode: {
      title: '调用诊断工具',
      description: '通过 MCP 查询日志、代码线索、历史工单并生成诊断报告。',
    },
    RunbookRetrieverNode: {
      title: '匹配 Runbook',
      description: '根据服务、接口、异常和诊断摘要检索本地处置手册。',
    },
    SeverityClassifierNode: {
      title: '判断故障等级',
      description: '根据支付、超时、空指针等规则更新 Incident 严重等级。',
    },
    ActionPlanGeneratorNode: {
      title: '生成处置方案',
      description: '基于诊断证据、Runbook 和故障等级生成低/中/高风险方案。',
    },
    RiskReviewNode: {
      title: '风险复核',
      description: '检查哪些方案需要人工确认，低风险可记录，中高风险必须人工介入。',
    },
    HumanApprovalNode: {
      title: '等待人工确认',
      description: '如果存在中高风险方案，Workflow 停在等待人工确认状态。',
    },
  };
  return labels[nodeName] ?? { title: nodeName, description: 'Workflow 节点执行记录。' };
}

function toolLabel(toolName: string) {
  const labels: Record<string, string> = {
    search_logs: '查询错误日志',
    search_code: '搜索代码线索',
    search_tickets: '检索历史工单',
    generate_report: '生成诊断报告',
  };
  return labels[toolName] ?? toolName;
}

function toolCallSummary(call: ToolCall) {
  const request = parseJsonObject(call.requestJson);
  const params = asRecord(request.params);
  const toolArgs = asRecord(params.arguments);
  const target = [
    toolArgs.service ? `服务 ${String(toolArgs.service)}` : null,
    toolArgs.trace_id ? `Trace ${String(toolArgs.trace_id)}` : null,
    toolArgs.query ? `查询 ${String(toolArgs.query)}` : null,
    toolArgs.symptom ? `症状 ${String(toolArgs.symptom)}` : null,
  ].filter(Boolean).join(' · ');
  const response = parseJsonValue(call.responseJson);
  const evidence = toolEvidenceSummary(call.toolName, response);
  return [target || '未携带查询条件', evidence].filter(Boolean).join('；');
}

function toolEvidenceSummary(toolName: string, response: unknown) {
  if (Array.isArray(response)) {
    const preview = response.length > 0 ? `，首条：${String(response[0]).slice(0, 80)}` : '';
    return `返回 ${response.length} 条${toolLabel(toolName).replace('查询', '').replace('搜索', '').replace('检索', '')}${preview}`;
  }
  const object = asRecord(response);
  if (toolName === 'generate_report') {
    const summary = String(object.summary ?? object.report_summary ?? '已生成诊断报告');
    const reportId = String(object.report_id ?? object.id ?? '-');
    return `报告 ${reportId}：${summary}`;
  }
  const values = Object.values(object);
  const nestedCount = values.reduce<number>((count, value) => count + (Array.isArray(value) ? value.length : 0), 0);
  if (nestedCount > 0) {
    return `返回 ${nestedCount} 条结构化证据`;
  }
  return Object.keys(object).length > 0 ? '返回结构化证据' : '无响应内容';
}

function nodeSummary(node: WorkflowNode) {
  const output = parseJsonObject(node.outputJson);
  switch (node.nodeName) {
    case 'AlertReceiverNode':
      return `接收 ${String(output.serviceName ?? '-')} 的告警，接口 ${String(output.endpoint ?? '-')}。`;
    case 'MetricsCollectorNode':
      return `指标状态 ${statusLabel(String(output.metricStatus ?? '-'))}，错误率 ${percent(Number(output.errorRate ?? 0))}，p95 ${String(output.p95Latency ?? '-')}ms。`;
    case 'DiagnosisMcpNode':
      return `诊断摘要：${String(output.summary ?? '-')}`;
    case 'RunbookRetrieverNode':
      return `命中 ${Array.isArray(output.matches) ? output.matches.length : 0} 个 Runbook。`;
    case 'SeverityClassifierNode':
      return `故障等级更新为 ${severityLabel(String(output.severity ?? '-'))}，原因：${String(output.reason ?? '-')}`;
    case 'ActionPlanGeneratorNode':
      return `生成 ${Array.isArray(output.proposals) ? output.proposals.length : 0} 个候选处置方案。`;
    case 'RiskReviewNode':
      return Boolean(output.approvalRequired)
        ? `发现中高风险方案，需要人工确认。方案 ID：${Array.isArray(output.approvalActionIds) ? output.approvalActionIds.join(', ') : '-'}`
        : '没有需要人工确认的中高风险方案。';
    case 'HumanApprovalNode':
      return String(output.message ?? '-');
    default:
      return formatJson(node.outputJson);
  }
}

function metricSource(metric: MetricSnapshot, index: number, metrics: MetricSnapshot[], selectedAction: ActionProposal | null) {
  if (metric.status === 'recovering' && selectedAction) {
    const riskHint = selectedAction.riskLevel === 'LOW'
      ? '低风险观察动作只表示进入持续观察，指标改善通常较慢'
      : selectedAction.riskLevel === 'HIGH'
        ? '高风险变更预期带来更明显恢复，但需要更严格复核'
        : '中风险缓解动作预期降低错误率和延迟';
    return `来源：采用「${selectedAction.title}」后写入，${riskHint}`;
  }
  if (metric.status === 'recovering') return '来源：记录处理结果后写入，表示进入恢复观察';
  if (metric.status === 'recovered') return '来源：关闭 Incident 时写入，表示故障已恢复';
  const degradedMetrics = metrics.filter((item) => item.status === 'degraded');
  const oldestDegradedId = degradedMetrics.length > 0 ? degradedMetrics[degradedMetrics.length - 1].id : null;
  if (metric.status === 'degraded' && metric.id === oldestDegradedId) {
    return '来源：入站告警携带的原始观测值';
  }
  if (metric.status === 'degraded') return index === 0 ? '来源：Workflow 指标采集节点复查' : '来源：故障处理过程中的异常样本';
  return '来源：指标快照';
}

function formatTime(value: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date);
}

function parseJsonObject(value: string | null): Record<string, unknown> {
  if (!value) return {};
  try {
    const parsed = JSON.parse(value);
    return asRecord(parsed);
  } catch {
    return {};
  }
}

function parseJsonValue(value: string | null): unknown {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function formatJson(value: string | null) {
  if (!value) return '{}';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function percent(value: number) {
  return `${(Number(value) * 100).toFixed(2)}%`;
}
