import { Space, Tag, Typography } from 'antd';
import { useEditorStore } from '../../stores/editorStore';
import type { Diagnostic } from '../../services/api';

const { Text } = Typography;

function DiagnosticRow({ diagnostic }: { diagnostic: Diagnostic }) {
  const severityColor = diagnostic.severity === 'error' ? 'red' : 'orange';

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 8,
        padding: '6px 0',
        borderBottom: '1px solid #2a2a2a',
      }}
    >
      <Tag color={severityColor} style={{ marginTop: 1, flexShrink: 0 }}>
        {diagnostic.severity.toUpperCase()}
      </Tag>
      {diagnostic.layer && (
        <Tag color="blue" style={{ marginTop: 1, flexShrink: 0 }}>
          {diagnostic.layer}
        </Tag>
      )}
      <div>
        <Text style={{ color: '#e0e0e0', fontSize: 12 }}>{diagnostic.message}</Text>
        <br />
        <Text style={{ color: '#888', fontSize: 11 }}>
          Line {diagnostic.line}, Col {diagnostic.column}
        </Text>
      </div>
    </div>
  );
}

export function ValidationResults() {
  const { diagnostics, isValid } = useEditorStore();

  const errors = diagnostics.filter((d) => d.severity === 'error');
  const warnings = diagnostics.filter((d) => d.severity === 'warning');

  return (
    <div style={{ padding: 16 }}>
      <Space style={{ marginBottom: 12 }}>
        <Tag color={isValid ? 'green' : 'red'}>{isValid ? 'VALID' : 'INVALID'}</Tag>
        {errors.length > 0 && <Tag color="red">{errors.length} error{errors.length !== 1 ? 's' : ''}</Tag>}
        {warnings.length > 0 && (
          <Tag color="orange">{warnings.length} warning{warnings.length !== 1 ? 's' : ''}</Tag>
        )}
      </Space>

      {diagnostics.length === 0 ? (
        <Text style={{ color: '#888', fontSize: 13 }}>
          {isValid ? 'No issues found.' : 'No diagnostics available.'}
        </Text>
      ) : (
        <div>
          {diagnostics.map((d, i) => (
            <DiagnosticRow key={i} diagnostic={d} />
          ))}
        </div>
      )}
    </div>
  );
}
