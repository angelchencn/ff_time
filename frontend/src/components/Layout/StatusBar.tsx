import { Select, Space, Typography } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, WarningOutlined, CloudOutlined } from '@ant-design/icons';
import { useEditorStore } from '../../stores/editorStore';
import { useServerStore } from '../../stores/serverStore';

const { Text } = Typography;

export function StatusBar() {
  const { diagnostics, isValid, code } = useEditorStore();
  const { servers, selectedIndex, select } = useServerStore();

  const errorCount = diagnostics.filter((d) => d.severity === 'error').length;
  const warningCount = diagnostics.filter((d) => d.severity === 'warning').length;
  const lineCount = code ? code.split('\n').length : 0;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 16px',
        backgroundColor: isValid ? 'var(--bg-elevated)' : '#fef2f2',
        borderTop: `1px solid ${isValid ? 'var(--border-default)' : '#fecaca'}`,
        height: 28,
        transition: 'background-color 0.3s, border-color 0.3s',
      }}
    >
      <Space size={12}>
        {isValid ? (
          <Space size={4}>
            <CheckCircleOutlined style={{ color: 'var(--accent-green)', fontSize: 11 }} />
            <Text style={{ color: 'var(--accent-green)', fontSize: 11, fontFamily: 'var(--font-mono)' }}>OK</Text>
          </Space>
        ) : (
          <Space size={4}>
            <CloseCircleOutlined style={{ color: 'var(--accent-red)', fontSize: 11 }} />
            <Text style={{ color: 'var(--accent-red)', fontSize: 11, fontFamily: 'var(--font-mono)' }}>ERROR</Text>
          </Space>
        )}

        {errorCount > 0 && (
          <Text style={{ color: 'var(--accent-red)', fontSize: 11, fontFamily: 'var(--font-mono)' }}>
            {errorCount} error{errorCount !== 1 ? 's' : ''}
          </Text>
        )}

        {warningCount > 0 && (
          <Space size={4}>
            <WarningOutlined style={{ color: 'var(--accent-orange)', fontSize: 11 }} />
            <Text style={{ color: 'var(--accent-orange)', fontSize: 11, fontFamily: 'var(--font-mono)' }}>
              {warningCount} warn
            </Text>
          </Space>
        )}
      </Space>

      <Space size={12}>
        <Space size={4}>
          <CloudOutlined style={{ color: 'var(--text-tertiary)', fontSize: 11 }} />
          <Select
            size="small"
            value={selectedIndex}
            onChange={select}
            style={{ fontSize: 11, width: 180 }}
            bordered={false}
            dropdownStyle={{ fontSize: 11 }}
          >
            {servers.map((s, i) => (
              <Select.Option key={i} value={i} style={{ fontSize: 11 }}>
                {s.name}
              </Select.Option>
            ))}
          </Select>
        </Space>

        <Text style={{ color: 'var(--text-tertiary)', fontSize: 11, fontFamily: 'var(--font-mono)' }}>
          Ln {lineCount}
        </Text>
      </Space>
    </div>
  );
}
