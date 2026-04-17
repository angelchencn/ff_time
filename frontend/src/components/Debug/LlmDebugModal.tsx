import { useEffect, useRef, useState } from 'react';
import { Modal, Button, Tabs, Select, Space, message } from 'antd';
import { ReloadOutlined, DeleteOutlined } from '@ant-design/icons';
import axios from 'axios';
import { useServerStore } from '../../stores/serverStore';

interface TokenPart {
  part: string;
  chars: number;
  est_tokens: number;
}

interface PromptContextFields {
  system_prompt: string;
  system_prompt_length: number;
  user_prompt: string;
  user_prompt_length: number;
  formula_type: string;
  formula_type_length: number;
  reference_formula: string;
  reference_formula_length: number;
  editor_code: string;
  editor_code_length: number;
  additional_rules: string;
  additional_rules_length: number;
  chat_history: string;
  chat_history_length: number;
}

interface LlmLogEntry {
  timestamp: string;
  endpoint: string;
  model: string;
  max_completion_tokens: number;
  mode?: 'flat' | 'structured';
  system_prompt: string;
  system_prompt_length: number;
  messages: Array<{ role: string; content: string }>;
  estimated_input_tokens: number;
  total_chars: number;
  token_breakdown: TokenPart[];
  user_message: string;
  // Structured mode only (FusionAiProvider → Spectra path). Each field maps
  // to one {placeholder} in the server-side prompt template.
  prompt_context?: PromptContextFields;
}

// Order matches the Spectra template's XML tag order so the UI walks the
// reader top-to-bottom through the actual prompt structure.
const STRUCTURED_FIELDS: Array<{
  key: keyof PromptContextFields;
  lengthKey: keyof PromptContextFields;
  label: string;
}> = [
  { key: 'system_prompt',     lengthKey: 'system_prompt_length',     label: 'System Prompt' },
  { key: 'user_prompt',       lengthKey: 'user_prompt_length',       label: 'User Prompt' },
  { key: 'formula_type',      lengthKey: 'formula_type_length',      label: 'Formula Type' },
  { key: 'reference_formula', lengthKey: 'reference_formula_length', label: 'Reference Formula' },
  { key: 'editor_code',       lengthKey: 'editor_code_length',       label: 'Editor Code' },
  { key: 'additional_rules',  lengthKey: 'additional_rules_length',  label: 'Additional Rules' },
  { key: 'chat_history',      lengthKey: 'chat_history_length',      label: 'Chat History' },
];

interface Props {
  open: boolean;
  onClose: () => void;
}

export function LlmDebugModal({ open, onClose }: Props) {
  const { current } = useServerStore();
  const [logs, setLogs] = useState<LlmLogEntry[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const dragging = useRef(false);
  const dragStart = useRef({ x: 0, y: 0 });

  useEffect(() => {
    if (open) loadLogs();
  }, [open]);

  function getHeaders(): Record<string, string> {
    const headers: Record<string, string> = {};
    if (current.auth) {
      headers['Authorization'] = `Basic ${btoa(`${current.auth.username}:${current.auth.password}`)}`;
    }
    return headers;
  }

  async function loadLogs() {
    try {
      const resp = await axios.get<LlmLogEntry[]>(
        `${current.baseUrl}${current.apiPrefix}/debug/llm-logs`,
        { headers: getHeaders() }
      );
      setLogs(resp.data);
      setSelectedIndex(0);
    } catch {
      message.error('Failed to load debug logs');
    }
  }

  async function clearLogs() {
    try {
      await axios.delete(
        `${current.baseUrl}${current.apiPrefix}/debug/llm-logs`,
        { headers: getHeaders() }
      );
      setLogs([]);
      message.success('Logs cleared');
    } catch {
      message.error('Failed to clear logs');
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

  const entry = logs[selectedIndex];

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
          <Button size="small" icon={<DeleteOutlined />} danger onClick={clearLogs}>Clear</Button>
          <span style={{ fontSize: 12, color: '#5c4a3e' }}>{logs.length} request(s)</span>
        </Space>
      </div>

      {/* History selector */}
      {logs.length > 0 && (
        <div style={{ padding: '6px 16px', borderBottom: '1px solid #d6cdc2' }}>
          <Select
            value={selectedIndex}
            onChange={(val: number) => setSelectedIndex(val)}
            style={{ width: '100%' }}
            size="small"
            options={logs.map((log, i) => {
              const ts = new Date(log.timestamp);
              const date = ts.toLocaleDateString('en-CA');
              const time = ts.toLocaleTimeString('en-GB');
              // Extract the user's actual request text, not the full formatted prompt
              const lastUser = [...log.messages].reverse().find((m) => m.role === 'user');
              let preview = log.user_message || log.endpoint;
              if (lastUser) {
                const reqMatch = lastUser.content.match(/(?:Requirement|Request):\s*(.+)/);
                preview = reqMatch ? reqMatch[1].trim() : lastUser.content.replace(/\s+/g, ' ');
              }
              preview = preview.substring(0, 100);
              return {
                value: i,
                label: `${date} ${time} — ${preview}${preview.length >= 100 ? '…' : ''}`,
              };
            })}
          />
        </div>
      )}

      {entry ? (
        <div style={{ padding: 16 }}>
          {/* Summary */}
          <div style={{ display: 'flex', gap: 16, marginBottom: 12, flexWrap: 'wrap', alignItems: 'center' }}>
            <InfoTag label="Time" value={new Date(entry.timestamp).toLocaleTimeString()} />
            <InfoTag label="Model" value={entry.model} />
            <InfoTag label="Endpoint" value={entry.endpoint} />
            <InfoTag label="Max Output Tokens" value={String(entry.max_completion_tokens)} />
            <ModeBadge mode={entry.mode} />
          </div>

          {/* Token Breakdown */}
          <div style={{
            marginBottom: 16, padding: '8px 12px', backgroundColor: '#faf8f5',
            border: '1px solid #d6cdc2', borderRadius: 6, fontSize: 12,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            <div style={{ fontWeight: 600, marginBottom: 4 }}>Input Token Breakdown:</div>
            {(entry.token_breakdown || []).map((t, i) => (
              <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '2px 0' }}>
                <span>{t.part}</span>
                <span>{t.chars.toLocaleString()} chars → ~{t.est_tokens.toLocaleString()} tokens</span>
              </div>
            ))}
            <div style={{ borderTop: '1px solid #d6cdc2', marginTop: 4, paddingTop: 4, fontWeight: 600, display: 'flex', justifyContent: 'space-between' }}>
              <span>Total</span>
              <span>{(entry.total_chars || 0).toLocaleString()} chars → ~{entry.estimated_input_tokens.toLocaleString()} tokens</span>
            </div>
          </div>

          <Tabs
            size="small"
            items={buildTabs(entry)}
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

function ModeBadge({ mode }: { mode?: 'flat' | 'structured' }) {
  if (!mode) return null;
  const isStructured = mode === 'structured';
  return (
    <span
      style={{
        fontSize: 11,
        fontWeight: 600,
        padding: '2px 8px',
        borderRadius: 10,
        backgroundColor: isStructured ? '#e8f3e4' : '#f3ece4',
        color: isStructured ? '#2e5e23' : '#6b4a2e',
        border: `1px solid ${isStructured ? '#8cb87b' : '#c9a87e'}`,
      }}
      title={
        isStructured
          ? 'Structured mode: each PromptContext field sent as its own Spectra property'
          : 'Flat mode: system + user messages flattened for OpenAI / hybrid path'
      }
    >
      {isStructured ? 'STRUCTURED' : 'FLAT'}
    </span>
  );
}

/**
 * Build the Tabs items for a log entry. In structured mode (FusionAiProvider
 * → Spectra), we emit one tab per non-empty PromptContext field so the user
 * can inspect exactly what went into each XML placeholder in the template.
 * In flat mode (OpenAI / hybrid), we fall back to the original
 * system-prompt + user-messages tabs.
 */
function buildTabs(entry: LlmLogEntry) {
  const isStructured = entry.mode === 'structured' && entry.prompt_context;

  if (isStructured && entry.prompt_context) {
    const pc = entry.prompt_context;
    const fieldTabs = STRUCTURED_FIELDS
      .filter((f) => (pc[f.lengthKey] as number) > 0)
      .map((f) => ({
        key: `pc-${f.key}`,
        label: `${f.label} (${(pc[f.lengthKey] as number).toLocaleString()} chars)`,
        children: <CodeBlock content={pc[f.key] as string} />,
      }));

    // If literally every field is empty (pathological case), still give
    // the user something rather than a bare Tabs with only "Full JSON".
    const hasAny = fieldTabs.length > 0;

    return [
      ...(hasAny
        ? fieldTabs
        : [
            {
              key: 'pc-empty',
              label: 'Prompt Context (all empty)',
              children: (
                <div style={{ padding: 16, color: '#5c4a3e', fontSize: 12 }}>
                  All PromptContext fields were empty or whitespace. Check the
                  AiService.buildPromptContext implementation.
                </div>
              ),
            },
          ]),
      {
        key: 'full',
        label: 'Full Request JSON',
        children: <CodeBlock content={JSON.stringify(entry, null, 2)} />,
      },
    ];
  }

  // Flat mode — legacy rendering preserved.
  return [
    {
      key: 'system',
      label: `System Prompt (${entry.system_prompt_length} chars)`,
      children: <CodeBlock content={entry.system_prompt} />,
    },
    ...entry.messages
      .filter((m) => m.role !== 'system')
      .map((msg, i) => ({
        key: `msg-${i}`,
        label: `${msg.role === 'user' ? 'User Prompt' : 'Assistant'} (${msg.content.length} chars)`,
        children: <CodeBlock content={msg.content} />,
      })),
    {
      key: 'full',
      label: 'Full Request JSON',
      children: <CodeBlock content={JSON.stringify(entry, null, 2)} />,
    },
  ];
}

function CodeBlock({ content }: { content: string }) {
  return (
    <pre
      style={{
        backgroundColor: '#faf8f5',
        border: '1px solid #d6cdc2',
        borderRadius: 6,
        padding: 12,
        fontSize: 12,
        fontFamily: "'JetBrains Mono', monospace",
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        maxHeight: 400,
        overflow: 'auto',
        lineHeight: 1.5,
      }}
    >
      {content}
    </pre>
  );
}
