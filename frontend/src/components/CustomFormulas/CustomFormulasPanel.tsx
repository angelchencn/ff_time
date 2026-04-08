import { useEffect, useState } from 'react';
import { Button, Input, List, Modal, Space, message } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  SaveOutlined,
  ArrowLeftOutlined,
  UpOutlined,
  DownOutlined,
} from '@ant-design/icons';
import MonacoEditor from '@monaco-editor/react';
import axios from 'axios';
import { useServerStore } from '../../stores/serverStore';

interface CustomFormula {
  name: string;
  description: string;
  code: string;
  rule?: string;
}

interface Props {
  onBack: () => void;
}

export function CustomFormulasPanel({ onBack }: Props) {
  const { current } = useServerStore();
  const [formulas, setFormulas] = useState<CustomFormula[]>([]);
  const [selectedIndex, setSelectedIndex] = useState<number>(-1);
  const [editName, setEditName] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [editCode, setEditCode] = useState('');
  const [editRule, setEditRule] = useState('');
  const [isDirty, setIsDirty] = useState(false);

  function getHeaders(): Record<string, string> {
    const headers: Record<string, string> = {};
    if (current.auth) {
      headers['Authorization'] = `Basic ${btoa(`${current.auth.username}:${current.auth.password}`)}`;
    }
    return headers;
  }

  useEffect(() => {
    loadFormulas();
  }, []);

  async function loadFormulas() {
    try {
      const resp = await axios.get<CustomFormula[]>(
        `${current.baseUrl}${current.apiPrefix}/custom-formulas`,
        { headers: getHeaders() }
      );
      setFormulas(resp.data);
      if (resp.data.length > 0) {
        selectFormula(0, resp.data);
      }
    } catch {
      message.error('Failed to load custom formulas');
    }
  }

  function selectFormula(index: number, list?: CustomFormula[]) {
    const f = (list || formulas)[index];
    if (!f) return;
    setSelectedIndex(index);
    setEditName(f.name);
    setEditDesc(f.description);
    setEditCode(f.code);
    setEditRule(f.rule || '');
    setIsDirty(false);
  }

  function handleNew() {
    const newFormula: CustomFormula = {
      name: 'New Formula',
      description: 'Description',
      rule: '',
      code: `/******************************************************************************
 *
 * Formula Name : NEW_CUSTOM_FORMULA
 *
 * Formula Type : Custom
 *
 * Description  : New custom formula
 *
 ******************************************************************************/

DEFAULT FOR HOURS_WORKED IS 0

INPUTS ARE HOURS_WORKED

l_log = PAY_INTERNAL_LOG_WRITE('NEW_CUSTOM_FORMULA - Enter')

l_result = HOURS_WORKED

l_log = PAY_INTERNAL_LOG_WRITE('NEW_CUSTOM_FORMULA - Exit')

RETURN l_result

/* End Formula Text */`,
    };
    const updated = [...formulas, newFormula];
    setFormulas(updated);
    selectFormula(updated.length - 1, updated);
    setIsDirty(true);
  }

  async function handleSave() {
    if (selectedIndex < 0) return;
    const updated = [...formulas];
    updated[selectedIndex] = {
      name: editName,
      description: editDesc,
      code: editCode,
      rule: editRule,
    };
    setFormulas(updated);

    try {
      await axios.put(`${current.baseUrl}${current.apiPrefix}/custom-formulas`, updated, { headers: getHeaders() });
      setIsDirty(false);
      message.success('Saved');
    } catch {
      message.error('Failed to save — server may not support PUT yet');
      setIsDirty(false);
    }
  }

  function handleDelete() {
    if (selectedIndex < 0) return;
    Modal.confirm({
      title: 'Delete Formula',
      content: `Delete "${editName}"?`,
      onOk: async () => {
        const updated = formulas.filter((_, i) => i !== selectedIndex);
        setFormulas(updated);
        if (updated.length > 0) {
          selectFormula(Math.min(selectedIndex, updated.length - 1), updated);
        } else {
          setSelectedIndex(-1);
          setEditName('');
          setEditDesc('');
          setEditCode('');
          setEditRule('');
        }
        try {
          await axios.put(`${current.baseUrl}${current.apiPrefix}/custom-formulas`, updated, { headers: getHeaders() });
        } catch {
          // best effort
        }
      },
    });
  }

  function handleMoveUp() {
    if (selectedIndex <= 0) return;
    const updated = [...formulas];
    [updated[selectedIndex - 1], updated[selectedIndex]] = [updated[selectedIndex], updated[selectedIndex - 1]];
    setFormulas(updated);
    setSelectedIndex(selectedIndex - 1);
    setIsDirty(true);
  }

  function handleMoveDown() {
    if (selectedIndex < 0 || selectedIndex >= formulas.length - 1) return;
    const updated = [...formulas];
    [updated[selectedIndex], updated[selectedIndex + 1]] = [updated[selectedIndex + 1], updated[selectedIndex]];
    setFormulas(updated);
    setSelectedIndex(selectedIndex + 1);
    setIsDirty(true);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Header */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '8px 16px',
          borderBottom: '1px solid var(--border-default)',
          backgroundColor: 'var(--bg-surface)',
        }}
      >
        <Space>
          <Button size="small" icon={<ArrowLeftOutlined />} onClick={onBack}>
            Back
          </Button>
          <span style={{ fontWeight: 600, fontSize: 14 }}>Manage Custom Formulas</span>
        </Space>
      </div>

      {/* Body */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Left: formula list */}
        <div
          style={{
            width: 320,
            borderRight: '1px solid var(--border-default)',
            overflow: 'hidden',
            flexShrink: 0,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          {/* List toolbar */}
          <div style={{
            padding: '6px 8px',
            borderBottom: '1px solid var(--border-default)',
            display: 'flex',
            gap: 4,
            justifyContent: 'center',
          }}>
            <Button size="small" icon={<UpOutlined />} onClick={handleMoveUp}
              disabled={selectedIndex <= 0} />
            <Button size="small" icon={<DownOutlined />} onClick={handleMoveDown}
              disabled={selectedIndex < 0 || selectedIndex >= formulas.length - 1} />
            <Button size="small" icon={<PlusOutlined />} onClick={handleNew}>Add</Button>
            <Button size="small" icon={<SaveOutlined />}
              type={isDirty ? 'primary' : 'default'}
              onClick={handleSave} disabled={selectedIndex < 0}>Save</Button>
            <Button size="small" icon={<DeleteOutlined />} danger
              onClick={handleDelete} disabled={selectedIndex < 0}>Delete</Button>
          </div>
          <div style={{ flex: 1, overflow: 'auto' }}>
          <List
            size="small"
            dataSource={formulas}
            renderItem={(item, index) => (
              <List.Item
                onClick={() => selectFormula(index)}
                style={{
                  cursor: 'pointer',
                  padding: '10px 16px',
                  backgroundColor:
                    index === selectedIndex ? 'var(--bg-elevated, #eee8e0)' : 'transparent',
                  borderLeft:
                    index === selectedIndex ? '3px solid var(--color-primary, #b8602a)' : '3px solid transparent',
                }}
              >
                <div>
                  <div style={{ fontWeight: 500, fontSize: 13 }}>{item.name}</div>
                  <div
                    style={{
                      fontSize: 11,
                      color: 'var(--text-secondary)',
                      marginTop: 2,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      maxWidth: 280,
                    }}
                  >
                    {item.description}
                  </div>
                </div>
              </List.Item>
            )}
          />
          </div>
        </div>

        {/* Right: editor */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          {selectedIndex >= 0 ? (
            <>
              {/* Name & Description */}
              <div style={{ padding: '8px 12px', borderBottom: '1px solid var(--border-default)' }}>
                <Space direction="vertical" style={{ width: '100%' }} size={4}>
                  <Input
                    size="small"
                    addonBefore="Name"
                    value={editName}
                    onChange={(e) => {
                      setEditName(e.target.value);
                      setIsDirty(true);
                    }}
                  />
                  <Input
                    size="small"
                    addonBefore="Description"
                    value={editDesc}
                    onChange={(e) => {
                      setEditDesc(e.target.value);
                      setIsDirty(true);
                    }}
                  />
                  <Input.TextArea
                    size="small"
                    placeholder="Additional Rule (appended to system prompt when this sample is selected)"
                    value={editRule}
                    rows={8}
                    onChange={(e) => {
                      setEditRule(e.target.value);
                      setIsDirty(true);
                    }}
                    style={{ fontSize: 12, fontFamily: "'JetBrains Mono', monospace" }}
                  />
                </Space>
              </div>

              {/* Code editor */}
              <div style={{ flex: 1 }}>
                <MonacoEditor
                  height="100%"
                  language="plaintext"
                  theme="vs"
                  value={editCode}
                  onChange={(v) => {
                    setEditCode(v || '');
                    setIsDirty(true);
                  }}
                  options={{
                    minimap: { enabled: false },
                    fontSize: 13,
                    fontFamily: "'JetBrains Mono', monospace",
                    lineNumbers: 'on',
                    scrollBeyondLastLine: false,
                    wordWrap: 'on',
                  }}
                />
              </div>
            </>
          ) : (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                color: 'var(--text-secondary)',
              }}
            >
              Select a formula or click Add to create one
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
