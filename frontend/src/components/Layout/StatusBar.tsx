import { Space, Typography } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, WarningOutlined } from '@ant-design/icons';
import { useEditorStore } from '../../stores/editorStore';

const { Text } = Typography;

export function StatusBar() {
  const { diagnostics, isValid, code } = useEditorStore();

  const errorCount = diagnostics.filter((d) => d.severity === 'error').length;
  const warningCount = diagnostics.filter((d) => d.severity === 'warning').length;
  const lineCount = code ? code.split('\n').length : 0;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '2px 16px',
        backgroundColor: isValid ? '#1a2a1a' : '#2a1a1a',
        borderTop: '1px solid #333',
        height: 24,
      }}
    >
      <Space size={12}>
        {isValid ? (
          <Space size={4}>
            <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 12 }} />
            <Text style={{ color: '#52c41a', fontSize: 11 }}>Syntax OK</Text>
          </Space>
        ) : (
          <Space size={4}>
            <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 12 }} />
            <Text style={{ color: '#ff4d4f', fontSize: 11 }}>Syntax Error</Text>
          </Space>
        )}

        {errorCount > 0 && (
          <Space size={4}>
            <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 12 }} />
            <Text style={{ color: '#ff4d4f', fontSize: 11 }}>
              {errorCount} error{errorCount !== 1 ? 's' : ''}
            </Text>
          </Space>
        )}

        {warningCount > 0 && (
          <Space size={4}>
            <WarningOutlined style={{ color: '#faad14', fontSize: 12 }} />
            <Text style={{ color: '#faad14', fontSize: 11 }}>
              {warningCount} warning{warningCount !== 1 ? 's' : ''}
            </Text>
          </Space>
        )}
      </Space>

      <Text style={{ color: '#666', fontSize: 11 }}>
        {lineCount} line{lineCount !== 1 ? 's' : ''}
      </Text>
    </div>
  );
}
