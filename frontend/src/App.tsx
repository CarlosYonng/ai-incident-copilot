import { useEffect, useMemo, useState } from 'react';

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

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

const demoIncident = {
  title: 'payment-service 支付回调超时',
  serviceName: 'payment-service',
  endpoint: '/api/payment/callback',
  source: 'DEMO',
  traceId: 'trace-payment-timeout-001',
  exceptionType: 'TimeoutError',
  summary: '5 分钟内 500 错误率升高，p95 延迟升至 3200ms',
};

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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedIncident = useMemo(
    () => incidents.find((incident) => incident.id === selectedIncidentId) ?? incidents[0],
    [incidents, selectedIncidentId],
  );

  async function loadIncidents() {
    const data = await api<Incident[]>('/incidents');
    setIncidents(data);
    if (!selectedIncidentId && data.length > 0) {
      setSelectedIncidentId(data[0].id);
    }
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

  async function createDemoIncident() {
    await api<Incident>('/incidents', {
      method: 'POST',
      body: JSON.stringify(demoIncident),
    });
    await loadIncidents();
  }

  async function startWorkflow() {
    if (!selectedIncident) return;
    const nextWorkflow = await api<Workflow>(`/incidents/${selectedIncident.id}/start-workflow`, {
      method: 'POST',
    });
    setWorkflow(nextWorkflow);
    const nextNodes = await api<WorkflowNode[]>(`/workflows/${nextWorkflow.workflowInstanceId}/nodes`);
    setNodes(nextNodes);
    await loadIncidents();
  }

  useEffect(() => {
    runAction(loadIncidents);
  }, []);

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">AI Incident Copilot</p>
          <h1>故障协同控制台</h1>
        </div>
        <div className="actions">
          <button type="button" onClick={() => runAction(createDemoIncident)} disabled={loading}>
            创建 Demo Incident
          </button>
          <button type="button" onClick={() => runAction(startWorkflow)} disabled={loading || !selectedIncident}>
            启动 Workflow
          </button>
        </div>
      </header>

      {error && <div className="alert">{error}</div>}

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
                  onClick={() => setSelectedIncidentId(incident.id)}
                >
                  <span>{incident.incidentNo}</span>
                  <strong>{incident.title}</strong>
                  <small>{incident.serviceName} · {incident.status}</small>
                </button>
              ))}
            </div>
          )}
        </aside>

        <section className="panel detail">
          <h2>Incident 详情</h2>
          {selectedIncident ? (
            <div className="detail-grid">
              <Info label="编号" value={selectedIncident.incidentNo} />
              <Info label="服务" value={selectedIncident.serviceName} />
              <Info label="接口" value={selectedIncident.endpoint ?? '-'} />
              <Info label="等级" value={selectedIncident.severity} />
              <Info label="状态" value={selectedIncident.status} />
              <Info label="Trace ID" value={selectedIncident.traceId ?? '-'} />
              <div className="wide">
                <Info label="摘要" value={selectedIncident.summary ?? '-'} />
              </div>
            </div>
          ) : (
            <p className="muted">先创建一个 Demo Incident</p>
          )}
        </section>
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
    </main>
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
