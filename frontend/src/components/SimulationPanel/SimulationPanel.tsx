import { useState, useRef } from 'react';
import { Button, Tabs, Typography } from 'antd';
import { BulbOutlined } from '@ant-design/icons';
import { ValidationResults } from './ValidationResults';
import { InputForm } from './InputForm';
import { ExecutionTrace } from './ExecutionTrace';
import { useEditorStore } from '../../stores/editorStore';
import { streamSSE } from '../../services/sse';

const { Text } = Typography;

function ExplainPanel() {
  const { code } = useEditorStore();
  const [explanation, setExplanation] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  function handleExplain() {
    setExplanation('');
    setIsStreaming(true);

    abortRef.current = streamSSE(
      '/api/explain',
      { code },
      (token) => setExplanation((prev) => prev + token),
      () => setIsStreaming(false),
      (err) => {
        setExplanation((prev) => prev + `\n[Error: ${err.message}]`);
        setIsStreaming(false);
      }
    );
  }

  return (
    <div style={{ padding: 16 }}>
      <Button
        type="default"
        icon={<BulbOutlined />}
        onClick={handleExplain}
        loading={isStreaming}
        block
        style={{ marginBottom: 12 }}
      >
        Explain Formula
      </Button>
      {explanation && (
        <div
          style={{
            backgroundColor: '#2a2a2a',
            borderRadius: 4,
            padding: 12,
            maxHeight: 400,
            overflowY: 'auto',
          }}
        >
          <Text style={{ color: '#e0e0e0', fontSize: 13, whiteSpace: 'pre-wrap' }}>
            {explanation}
          </Text>
        </div>
      )}
    </div>
  );
}

export function SimulationPanel() {
  const tabItems = [
    {
      key: 'validate',
      label: 'Validate',
      children: <ValidationResults />,
    },
    {
      key: 'simulate',
      label: 'Simulate',
      children: (
        <>
          <InputForm />
          <ExecutionTrace />
        </>
      ),
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
        backgroundColor: '#1a1a1a',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <Tabs
        defaultActiveKey="validate"
        items={tabItems}
        style={{ flex: 1 }}
        tabBarStyle={{ paddingLeft: 16, borderBottomColor: '#333' }}
      />
    </div>
  );
}
