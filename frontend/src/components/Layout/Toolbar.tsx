import { useState } from 'react';
import { Button, Space } from 'antd';
import {
  FileAddOutlined,
  ExportOutlined,
  SettingOutlined,
  BugOutlined,
} from '@ant-design/icons';
import { useEditorStore } from '../../stores/editorStore';
import { useChatStore } from '../../stores/chatStore';
import { exportFormula } from '../../services/api';
import { LlmDebugModal } from '../Debug/LlmDebugModal';

interface Props {
  onManageCustom: () => void;
}

export function Toolbar({ onManageCustom }: Props) {
  const { code, currentFormulaId } = useEditorStore();
  const [debugOpen, setDebugOpen] = useState(false);

  function handleNew() {
    useEditorStore.getState().setCode('');
    useEditorStore.getState().setCurrentFormulaId(null);
    useEditorStore.getState().setIsDirty(false);
    useChatStore.getState().clearMessages();
  }

  async function handleExport() {
    try {
      let content: string;
      if (currentFormulaId) {
        content = await exportFormula(currentFormulaId);
      } else {
        content = code;
      }

      const blob = new Blob([content], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'formula.ff';
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      const blob = new Blob([code], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'formula.ff';
      a.click();
      URL.revokeObjectURL(url);
    }
  }

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 16px',
        backgroundColor: 'var(--bg-surface)',
        borderBottom: '1px solid var(--border-default)',
        height: 44,
        backdropFilter: 'blur(12px)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <Space size={4}>
          <Button size="small" icon={<FileAddOutlined />} onClick={handleNew}>
            New
          </Button>
          <Button size="small" icon={<ExportOutlined />} onClick={handleExport}>
            Export
          </Button>
          <Button size="small" icon={<SettingOutlined />} onClick={onManageCustom}>
            Custom
          </Button>
          <Button size="small" icon={<BugOutlined />} onClick={() => setDebugOpen(true)}>
            Debug
          </Button>
        </Space>
      </div>
      <span style={{
        fontSize: 14,
        color: 'var(--text-secondary)',
        fontFamily: 'var(--font-display)',
        letterSpacing: '-0.3px',
      }}>
        Oracle HCM Fast Formula
      </span>

      <LlmDebugModal open={debugOpen} onClose={() => setDebugOpen(false)} />
    </div>
  );
}
