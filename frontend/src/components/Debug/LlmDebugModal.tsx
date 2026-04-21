import { useEffect, useRef, useState } from 'react';
import { Modal, Button, Tabs, Select, Space, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import axios from 'axios';
import { useServerStore } from '../../stores/serverStore';

/** Summary row from GET /debug/llm-logs */
interface LogSummary {
  log_id: number;
  name: string;
  status: string;
  source_type: string;
  creator_type: string;
  timestamp: string;
  summary: string;
  message: string;
}

/** Detail from GET /debug/llm-logs/{id} */
interface LogDetail {
  log_id: number;
  summary: string;
  message: string;
  system_prompt: string;
  system_prompt_length: number;
  formula_type: string;
  reference_formula: string;
  additional_rules: string;
  editor_code: string;
  chat_history: string;
  token_breakdown: string;
  response?: string;
  session_id?: string;
}

interface Props {
  open: boolean;
  onClose: () => void;
}

export function LlmDebugModal({ open, onClose }: Props) {
  const { current } = useServerStore();
  const [logs, setLogs] = useState<LogSummary[]>([]);
  const [selectedLogId, setSelectedLogId] = useState<number | null>(null);
  const [detail, setDetail] = useState<LogDetail | null>(null);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const dragging = useRef(false);
  const dragStart = useRef({ x: 0, y: 0 });

  useEffect(() => {
    if (open) loadLogs();
  }, [open]);

  useEffect(() => {
    if (selectedLogId != null) loadDetail(selectedLogId);
  }, [selectedLogId]);

  function getHeaders(): Record<string, string> {
    const headers: Record<string, string> = {};
    if (current.auth) {
      headers['Authorization'] = `Basic ${btoa(`${current.auth.username}:${current.auth.password}`)}`;
    }
    return headers;
  }

  async function loadLogs() {
    try {
      const resp = await axios.get<LogSummary[]>(
        `${current.baseUrl}${current.apiPrefix}/debug/llm-logs`,
        { headers: getHeaders() }
      );
      setLogs(resp.data);
      if (resp.data.length > 0) {
        setSelectedLogId(resp.data[0].log_id);
      } else {
        setSelectedLogId(null);
        setDetail(null);
      }
    } catch {
      message.error('Failed to load debug logs from DB');
    }
  }

  async function loadDetail(logId: number) {
    try {
      const resp = await axios.get<LogDetail>(
        `${current.baseUrl}${current.apiPrefix}/debug/llm-logs/${logId}`,
        { headers: getHeaders() }
      );
      setDetail(resp.data);
    } catch {
      message.error('Failed to load log detail');
    }
  }

  const onTitleMouseDown = (e: React.MouseEvent) => {
    dragging.current = true;
    dragStart.current = { x: e.clientX - position.x, y: e.clientY - position.y };

    const onMouseMove = (ev: MouseEvent) => {
      if (!dragging.current) return;
      setPosition({
        x: ev.clientX - dragStart.current.x,
        y: ev.clientY - dragStart.current.y,
      });
    };
    const onMouseUp = () => {
      dragging.current = false;
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  };

  return (
    <Modal
      title={
        <div onMouseDown={onTitleMouseDown} style={{ cursor: 'move', userSelect: 'none' }}>
          LLM Request Debug
        </div>
      }
      open={open}
      onCancel={onClose}
      footer={null}
      width="90vw"
      style={{ top: 20 }}
      maskClosable={false}
      modalRender={(modal) => (
        <div style={{ transform: `translate(${position.x}px, ${position.y}px)` }}>
          {modal}
        </div>
      )}
      styles={{ body: { height: '75vh', overflow: 'auto', padding: 0 } }}
    >
      <div style={{ padding: '8px 16px', borderBottom: '1px solid #d6cdc2', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button size="small" icon={<ReloadOutlined />} onClick={loadLogs}>Refresh</Button>
          <span style={{ fontSize: 12, color: '#5c4a3e' }}>{logs.length} request(s)</span>
        </Space>
      </div>

      {/* History selector */}
      {logs.length > 0 && (
        <div style={{ padding: '6px 16px', borderBottom: '1px solid #d6cdc2' }}>
          <Select
            value={selectedLogId}
            onChange={(val: number) => setSelectedLogId(val)}
            style={{ width: '100%' }}
            size="small"
            options={logs.map((log) => {
              const ts = new Date(log.timestamp);
              const date = ts.toLocaleDateString('en-CA');
              const time = ts.toLocaleTimeString('en-GB');
              const preview = (log.message || '').substring(0, 80);
              return {
                value: log.log_id,
                label: `${date} ${time} — [${log.status === 'S' ? 'OK' : 'ERR'}] ${log.source_type || ''} — ${preview}${preview.length >= 80 ? '...' : ''}`,
              };
            })}
          />
        </div>
      )}

      {detail ? (
        <div style={{ padding: 16 }}>
          {/* Summary */}
          <div style={{ display: 'flex', gap: 16, marginBottom: 12, flexWrap: 'wrap', alignItems: 'center' }}>
            <InfoTag label="Log ID" value={String(detail.log_id)} />
            <InfoTag label="Formula Type" value={detail.formula_type || ''} />
            <InfoTag label="Message" value={(detail.message || '').substring(0, 80)} />
            <StatusBadge status={logs.find(l => l.log_id === selectedLogId)?.status} />
            {detail.session_id && (
              <InfoTag label="Session ID" value={detail.session_id} />
            )}
          </div>

          {/* Token Breakdown */}
          {detail.token_breakdown && (
            <div style={{
              marginBottom: 16, padding: '8px 12px', backgroundColor: '#faf8f5',
              border: '1px solid #d6cdc2', borderRadius: 6, fontSize: 12,
              fontFamily: "'JetBrains Mono', monospace",
            }}>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>Input Token Breakdown:</div>
              {detail.token_breakdown.split(',').map((part, i) => {
                const [name, sizes] = part.split(':');
                const [chars, tokens] = (sizes || '0/0').split('/');
                return (
                  <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '2px 0' }}>
                    <span>{name}</span>
                    <span>{Number(chars).toLocaleString()} chars &rarr; ~{Number(tokens).toLocaleString()} tokens</span>
                  </div>
                );
              })}
            </div>
          )}

          <Tabs
            size="small"
            items={buildTabs(detail)}
          />
        </div>
      ) : (
        <div style={{ padding: 40, textAlign: 'center', color: '#5c4a3e' }}>
          No LLM requests recorded yet. Send a chat message first.
        </div>
      )}
    </Modal>
  );
}

function InfoTag({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ fontSize: 12 }}>
      <span style={{ color: '#5c4a3e' }}>{label}: </span>
      <span style={{ fontWeight: 600, color: '#2c1810' }}>{value}</span>
    </div>
  );
}

function StatusBadge({ status }: { status?: string }) {
  if (!status) return null;
  const isSuccess = status === 'S';
  return (
    <span
      style={{
        fontSize: 11,
        fontWeight: 600,
        padding: '2px 8px',
        borderRadius: 10,
        backgroundColor: isSuccess ? '#e8f3e4' : '#f9e4e4',
        color: isSuccess ? '#2e5e23' : '#8b2020',
        border: `1px solid ${isSuccess ? '#8cb87b' : '#d49090'}`,
      }}
    >
      {isSuccess ? 'SUCCESS' : 'ERROR'}
    </span>
  );
}

const DETAIL_FIELDS: Array<{ key: keyof LogDetail; breakdownKey: string; label: string }> = [
  { key: 'system_prompt',     breakdownKey: 'system_prompt',     label: 'System Prompt' },
  { key: 'message',           breakdownKey: 'message',           label: 'Message' },
  { key: 'formula_type',      breakdownKey: 'formula_type',      label: 'Formula Type' },
  { key: 'reference_formula', breakdownKey: 'reference_formula', label: 'Reference Formula' },
  { key: 'additional_rules',  breakdownKey: 'additional_rules',  label: 'Additional Rules' },
  { key: 'editor_code',       breakdownKey: 'editor_code',       label: 'Editor Code' },
  { key: 'chat_history',      breakdownKey: 'chat_history',      label: 'Chat History' },
  { key: 'response',          breakdownKey: '',                   label: 'Response' },
];

/**
 * Parse the token_breakdown string to get real (untruncated) char counts.
 * Format: "system_prompt:12537/3134,message:50/12,..."
 */
function parseBreakdownChars(breakdown: string | undefined): Record<string, number> {
  const result: Record<string, number> = {};
  if (!breakdown) return result;
  for (const part of breakdown.split(',')) {
    const colonIdx = part.indexOf(':');
    if (colonIdx < 0) continue;
    const name = part.substring(0, colonIdx).trim();
    const sizes = part.substring(colonIdx + 1).trim();
    if (name && sizes) {
      const chars = parseInt(sizes.split('/')[0], 10);
      if (!isNaN(chars)) result[name] = chars;
    }
  }
  return result;
}

function buildTabs(detail: LogDetail) {
  const realChars = parseBreakdownChars(detail.token_breakdown);

  const tabs = DETAIL_FIELDS
    .filter((f) => {
      const val = detail[f.key];
      return val != null && typeof val === 'string' && val.length > 0;
    })
    .map((f) => {
      const content = detail[f.key] as string;
      // Use real (untruncated) char count from token_breakdown if available
      const chars = f.breakdownKey && realChars[f.breakdownKey] != null
        ? realChars[f.breakdownKey]
        : content.length;
      return {
        key: f.key,
        label: `${f.label} (${chars.toLocaleString()} chars)`,
        children: <CodeBlock content={content} />,
      };
    });

  // Full JSON tab
  tabs.push({
    key: 'full',
    label: 'Full Request JSON',
    children: <CodeBlock content={JSON.stringify(detail, null, 2)} />,
  });

  return tabs;
}

function CodeBlock({ content }: { content: string }) {
  return (
    <pre
      style={{
        backgroundColor: '#faf8f5',
        border: '1px solid #d6cdc2',
        borderRadius: 6,
        padding: '12px 16px',
        fontSize: 11,
        fontFamily: "'JetBrains Mono', monospace",
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-all',
        maxHeight: '55vh',
        overflow: 'auto',
        lineHeight: 1.5,
        margin: 0,
      }}
    >
      {content}
    </pre>
  );
}
