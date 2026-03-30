import { Button, Segmented, Space, Typography } from 'antd';
import {
  FileAddOutlined,
  SaveOutlined,
  ExportOutlined,
} from '@ant-design/icons';
import { useEditorStore, type EditorMode } from '../../stores/editorStore';
import { createFormula, updateFormula, exportFormula } from '../../services/api';

const { Text } = Typography;

export function Toolbar() {
  const { code, currentFormulaId, mode, isDirty, setMode, setCurrentFormulaId, setIsDirty } =
    useEditorStore();

  function handleNew() {
    useEditorStore.getState().setCode('');
    setCurrentFormulaId(null);
    setIsDirty(false);
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
      // Fall back to exporting current code
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
        padding: '8px 16px',
        backgroundColor: '#111',
        borderBottom: '1px solid #333',
        height: 48,
      }}
    >
      <Space>
        <Text strong style={{ color: '#e0e0e0', fontSize: 15 }}>
          FF Time
        </Text>
      </Space>

      <Space>
        <Button
          size="small"
          icon={<FileAddOutlined />}
          onClick={handleNew}
          style={{ backgroundColor: '#2a2a2a', borderColor: '#444', color: '#ccc' }}
        >
          New
        </Button>
        <Button
          size="small"
          icon={<SaveOutlined />}
          onClick={handleSave}
          type={isDirty ? 'primary' : 'default'}
          style={isDirty ? {} : { backgroundColor: '#2a2a2a', borderColor: '#444', color: '#ccc' }}
        >
          Save
        </Button>
        <Button
          size="small"
          icon={<ExportOutlined />}
          onClick={handleExport}
          style={{ backgroundColor: '#2a2a2a', borderColor: '#444', color: '#ccc' }}
        >
          Export
        </Button>
      </Space>

      <Segmented<EditorMode>
        size="small"
        options={[
          { label: 'Chat', value: 'chat' },
          { label: 'Code', value: 'code' },
        ]}
        value={mode}
        onChange={(val) => setMode(val)}
      />
    </div>
  );
}
