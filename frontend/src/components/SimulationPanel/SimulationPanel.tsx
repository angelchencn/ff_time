import { useState, useRef } from 'react';
import { Button, Tabs, Typography } from 'antd';
import { BulbOutlined } from '@ant-design/icons';
import { ValidationResults } from './ValidationResults';
// NOTE: Simulate + DBIs tabs are temporarily removed from the UI. The
// underlying InputForm / ExecutionTrace / DBIPanel component files are kept
// on disk so they can be wired back up when those features are reimplemented.
import { useEditorStore } from '../../stores/editorStore';
import { useServerStore } from '../../stores/serverStore';
import { streamSSE } from '../../services/sse';

const { Text } = Typography;

function ExplainPanel() {
  const { code } = useEditorStore();
  const { current } = useServerStore();
  const [explanation, setExplanation] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  function handleExplain() {
    setExplanation('');
    setIsStreaming(true);

    const authHeaders: Record<string, string> = {};
    if (current.auth) {
      authHeaders['Authorization'] = `Basic ${btoa(`${current.auth.username}:${current.auth.password}`)}`;
    }

    abortRef.current = streamSSE(
      `${current.baseUrl}${current.apiPrefix}/explain`,
      { code },
      (token) => setExplanation((prev) => prev + token),
      () => setIsStreaming(false),
      (err) => {
        setExplanation((prev) => prev + `\n[Error: ${err.message}]`);
        setIsStreaming(false);
      },
      undefined,
      authHeaders
    );
  }

  return (
    <div style={{ padding: 16, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Button
        type="default"
        icon={<BulbOutlined />}
        onClick={handleExplain}
        loading={isStreaming}
        block
        style={{ marginBottom: 12, flexShrink: 0 }}
      >
        Explain Formula
      </Button>
      {explanation && (
        <div
          style={{
            backgroundColor: 'var(--bg-inset)',
            border: '1px solid var(--border-default)',
            borderRadius: 'var(--radius-md)',
            padding: 12,
            flex: 1,
            overflowY: 'auto',
          }}
        >
          <Text style={{ color: 'var(--text-primary)', fontSize: 13, whiteSpace: 'pre-wrap' }}>
            {explanation}
          </Text>
        </div>
      )}
    </div>
  );
}

export function SimulationPanel() {
  // Simulate and DBIs tabs are removed from the UI entirely until the real
  // implementations are reworked against the DB-backed template store. The
  // InputForm / ExecutionTrace / DBIPanel source files are kept on disk so
  // they can be re-wired later without re-writing them from scratch.
  const tabItems = [
    {
      key: 'validate',
      label: 'Validate',
      children: <ValidationResults />,
    },
    {
      key: 'explain',
      label: 'Explain',
      children: <ExplainPanel />,
    },
  ];

  return (
    <div
      style={{
        height: '100%',
        backgroundColor: 'var(--bg-surface)',
        display: 'flex',
        flexDirection: 'column',
        borderLeft: '1px solid var(--border-default)',
      }}
    >
      <Tabs
        defaultActiveKey="validate"
        items={tabItems}
        style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
        tabBarStyle={{ paddingLeft: 16, flexShrink: 0 }}
        className="simulation-tabs"
      />
      <style>{`
        .simulation-tabs .ant-tabs-content-holder {
          flex: 1;
          overflow: hidden;
        }
        .simulation-tabs .ant-tabs-content,
        .simulation-tabs .ant-tabs-tabpane-active {
          height: 100%;
          overflow-y: auto;
        }
      `}</style>
    </div>
  );
}
