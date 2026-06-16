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
  traceId: string | null;
  exceptionType: string | null;
  summary: string | null;
  createdAt: string;
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
  const [actions, setActions] = useState<ActionProposal[]>([]);
  const [metrics, setMetrics] = useState<MetricSnapshot[]>([]);
  const [postmortem, setPostmortem] = useState<Postmortem | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedIncident = useMemo(
    () => incidents.find((incident) => incident.id === selectedIncidentId) ?? incidents[0] ?? null,
    [incidents, selectedIncidentId],
  );

  async function loadIncidents() {
    const data = await api<Incident[]>('/incidents');
    setIncidents(data);
    if (!selectedIncidentId && data.length > 0) {
      setSelectedIncidentId(data[0].id);
    }
  }

  async function loadIncidentArtifacts(incidentId: number) {
    const [nextActions, nextMetrics] = await Promise.all([
      api<ActionProposal[]>(`/incidents/${incidentId}/actions`).catch(() => []),
      api<MetricSnapshot[]>(`/demo/metrics/${incidentId}`).catch(() => []),
    ]);
    setActions(nextActions);
    setMetrics(nextMetrics);
    const nextPostmortem = await api<Postmortem>(`/incidents/${incidentId}/postmortem`).catch(() => null);
    setPostmortem(nextPostmortem);
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
    const incident = await api<Incident>('/demo/faults/payment-timeout', {
      method: 'POST',
      body: JSON.stringify({ autoCreateIncident: true }),
    });
    await loadIncidents();
    setSelectedIncidentId(incident.id);
    await loadIncidentArtifacts(incident.id);
  }

  async function createOrderIncident() {
    const incident = await api<Incident>('/demo/faults/order-npe', {
      method: 'POST',
      body: JSON.stringify({ autoCreateIncident: true }),
    });
    await loadIncidents();
    setSelectedIncidentId(incident.id);
    await loadIncidentArtifacts(incident.id);
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

  async function markOfflineExecuted(actionId: number) {
    await api<ActionProposal>(`/actions/${actionId}/mark-offline-executed`, {
      method: 'POST',
      body: JSON.stringify({
        executor: 'sre-demo',
        resultDetail: '已在线下通过值班系统执行，本系统只记录处理结果和审计证据。',
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
          <p className="eyebrow">AI Incident Copilot</p>
          <h1>故障协同控制台</h1>
        </div>
        <div className="actions">
          <button type="button" onClick={() => runAction(createPaymentIncident)} disabled={loading}>
            支付超时
          </button>
          <button type="button" onClick={() => runAction(createOrderIncident)} disabled={loading}>
            订单 NPE
          </button>
          <button type="button" onClick={() => runAction(startWorkflow)} disabled={loading || !selectedIncident}>
            启动 Workflow
          </button>
        </div>
      </header>

      {error && <div className="alert">{error}</div>}

      <section className="summary-grid">
        <Summary label="Incidents" value={String(incidents.length)} />
        <Summary label="Workflow" value={workflow?.status ?? '-'} />
        <Summary label="Actions" value={String(actions.length)} />
        <Summary label="Tool Calls" value={String(toolCalls.length)} />
      </section>

      <section className="layout">
        <aside className="panel incident-list">
          <h2>Incidents</h2>
          {incidents.length === 0 ? (
            <p className="muted">暂无 Incident</p>
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
                  }}
                >
                  <span>{incident.incidentNo}</span>
                  <strong>{incident.title}</strong>
                  <small>{incident.serviceName} · {incident.severity} · {incident.status}</small>
                </button>
              ))}
            </div>
          )}
        </aside>

        <section className="panel detail">
          <div className="section-head">
            <h2>Incident 详情</h2>
            {selectedIncident && <span className="badge">{selectedIncident.status}</span>}
          </div>
          {selectedIncident ? (
            <div className="detail-grid">
              <Info label="编号" value={selectedIncident.incidentNo} />
              <Info label="服务" value={selectedIncident.serviceName} />
              <Info label="接口" value={selectedIncident.endpoint ?? '-'} />
              <Info label="等级" value={selectedIncident.severity} />
              <Info label="Trace ID" value={selectedIncident.traceId ?? '-'} />
              <Info label="异常" value={selectedIncident.exceptionType ?? '-'} />
              <div className="wide">
                <Info label="摘要" value={selectedIncident.summary ?? '-'} />
              </div>
            </div>
          ) : (
            <p className="muted">先创建一个 Demo Incident</p>
          )}
        </section>
      </section>

      <section className="board">
        <Panel title="指标快照">
          {metrics.length === 0 ? (
            <p className="muted">暂无指标</p>
          ) : (
            <div className="metric-grid">
              {metrics.slice(0, 4).map((metric) => (
                <article className="metric" key={metric.id}>
                  <strong>{metric.status}</strong>
                  <span>error {percent(metric.errorRate)}</span>
                  <span>p95 {metric.p95Latency}ms</span>
                  <span>qps {metric.qps}</span>
                </article>
              ))}
            </div>
          )}
        </Panel>

        <Panel title="处置方案">
          {actions.length === 0 ? (
            <p className="muted">启动 Workflow 后生成候选方案</p>
          ) : (
            <div className="action-list">
              {actions.map((action) => (
                <article className="action-card" key={action.id}>
                  <div className="card-head">
                    <strong>{action.title}</strong>
                    <span className={`risk risk-${action.riskLevel.toLowerCase()}`}>{action.riskLevel}</span>
                  </div>
                  <p>{action.reason}</p>
                  <small>{action.impact}</small>
                  <div className="card-actions">
                    <span>{action.status}</span>
                    {action.requiresApproval && action.status !== 'OFFLINE_EXECUTED' && (
                      <button
                        type="button"
                        onClick={() => runAction(() => markOfflineExecuted(action.id))}
                        disabled={loading}
                      >
                        标记线下已执行
                      </button>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}
        </Panel>
      </section>

      <section className="panel workflow">
        <div className="section-head">
          <h2>Workflow 时间线</h2>
          {workflow && <span className="badge">{workflow.status}</span>}
        </div>
        {nodes.length === 0 ? (
          <p className="muted">启动 Workflow 后会显示节点执行记录</p>
        ) : (
          <div className="timeline">
            {nodes.map((node) => (
              <article className="node" key={node.id}>
                <div>
                  <strong>{node.nodeName}</strong>
                  <span>{node.nodeType}</span>
                </div>
                <div>
                  <b>{node.status}</b>
                  <small>{node.durationMs ?? 0} ms</small>
                </div>
                <pre>{formatJson(node.outputJson)}</pre>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="board">
        <Panel title="MCP / LLM 审计">
          {toolCalls.length === 0 ? (
            <p className="muted">暂无工具调用记录</p>
          ) : (
            <div className="audit-list">
              {toolCalls.map((call) => (
                <article className="audit" key={call.id}>
                  <strong>{call.toolName}</strong>
                  <span>{call.nodeName}</span>
                  <b>{call.success ? 'SUCCESS' : 'FALLBACK'}</b>
                  <small>{call.durationMs ?? 0} ms</small>
                </article>
              ))}
            </div>
          )}
        </Panel>

        <Panel title="复盘报告">
          <div className="report-actions">
            <button type="button" onClick={() => runAction(generatePostmortem)} disabled={loading || !selectedIncident}>
              生成复盘
            </button>
            <button type="button" onClick={() => runAction(closeIncident)} disabled={loading || !selectedIncident}>
              关闭 Incident
            </button>
          </div>
          {postmortem ? (
            <article className="report">
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
        </Panel>
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
