import { Collapse, Tag, Typography } from 'antd';
import { useSimulationStore } from '../../stores/simulationStore';

const { Text } = Typography;

export function ExecutionTrace() {
  const { outputData, trace, status, error } = useSimulationStore();

  if (status === 'idle') {
    return (
      <div style={{ padding: 16 }}>
        <Text style={{ color: '#888', fontSize: 13 }}>
          Run a simulation to see output and execution trace.
        </Text>
      </div>
    );
  }

  if (status === 'running') {
    return (
      <div style={{ padding: 16 }}>
        <Text style={{ color: '#888', fontSize: 13 }}>Running...</Text>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div style={{ padding: 16 }}>
        <Tag color="red">ERROR</Tag>
        <Text style={{ color: '#ff7875', fontSize: 13, marginTop: 8, display: 'block' }}>
          {error ?? 'Unknown error'}
        </Text>
      </div>
    );
  }

  const outputEntries = Object.entries(outputData);

  return (
    <div style={{ padding: 16 }}>
      {/* Output Variables */}
      <div style={{ marginBottom: 16 }}>
        <Text strong style={{ color: '#ccc', fontSize: 13, display: 'block', marginBottom: 8 }}>
          Output Variables
        </Text>
        {outputEntries.length === 0 ? (
          <Text style={{ color: '#888', fontSize: 12 }}>No output variables.</Text>
        ) : (
          outputEntries.map(([key, val]) => (
            <div
              key={key}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                padding: '4px 8px',
                backgroundColor: '#2a2a2a',
                borderRadius: 4,
                marginBottom: 4,
              }}
            >
              <Text style={{ color: '#a8d8a8', fontSize: 12 }}>{key}</Text>
              <Text style={{ color: '#e0e0e0', fontSize: 12 }}>{String(val)}</Text>
            </div>
          ))
        )}
      </div>

      {/* Execution trace */}
      {trace.length > 0 && (
        <Collapse
          size="small"
          items={[
            {
              key: 'trace',
              label: <span style={{ color: '#ccc', fontSize: 12 }}>Execution Trace ({trace.length} steps)</span>,
              children: (
                <div
                  style={{
                    maxHeight: 200,
                    overflowY: 'auto',
                    fontFamily: 'monospace',
                    fontSize: 11,
                  }}
                >
                  {trace.map((line, idx) => (
                    <div
                      key={idx}
                      style={{
                        color: '#aaa',
                        padding: '2px 0',
                        borderBottom: '1px solid #222',
                      }}
                    >
                      <Text style={{ color: '#555', marginRight: 8 }}>{idx + 1}</Text>
                      <Text style={{ color: '#ccc' }}>{line}</Text>
                    </div>
                  ))}
                </div>
              ),
            },
          ]}
          style={{ backgroundColor: '#1a1a1a', borderColor: '#333' }}
        />
      )}
    </div>
  );
}
