import { Button, Space } from 'antd';
import {
  FileAddOutlined,
  SaveOutlined,
  ExportOutlined,
} from '@ant-design/icons';
import { useEditorStore } from '../../stores/editorStore';
import { useChatStore } from '../../stores/chatStore';
import { createFormula, updateFormula, exportFormula } from '../../services/api';

export function Toolbar() {
  const { code, currentFormulaId, isDirty, setCurrentFormulaId, setIsDirty } =
    useEditorStore();

  function handleNew() {
    useEditorStore.getState().setCode('');
    setCurrentFormulaId(null);
    setIsDirty(false);
    useChatStore.getState().clearMessages();
  }

  async function handleSave() {
    if (!code.trim()) return;

    try {
      if (currentFormulaId) {
        await updateFormula(currentFormulaId, { code });
      } else {
        const name = `Formula ${new Date().toISOString().slice(0, 10)}`;
        const formula = await createFormula(name, code);
        setCurrentFormulaId(formula.id);
      }
      setIsDirty(false);
    } catch {
      // Backend may not be running; ignore save errors silently
    }
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
        padding: '8px 16px',
        backgroundColor: '#fff',
        borderBottom: '1px solid #e0e0e0',
        height: 48,
      }}
    >
      <Space>
        <Button size="small" icon={<FileAddOutlined />} onClick={handleNew}>
          New
        </Button>
        <Button
          size="small"
          icon={<SaveOutlined />}
          onClick={handleSave}
          type={isDirty ? 'primary' : 'default'}
        >
          Save
        </Button>
        <Button size="small" icon={<ExportOutlined />} onClick={handleExport}>
          Export
        </Button>
      </Space>
    </div>
  );
}
