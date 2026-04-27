import { useState } from 'react';
import { Button, Input, Modal, Select, Space, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  WarningOutlined,
  CloudOutlined,
  SettingOutlined,
  PlusOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { useEditorStore } from '../../stores/editorStore';
import { useServerStore, type ServerConfig } from '../../stores/serverStore';

const { Text } = Typography;

export function StatusBar() {
  const { diagnostics, isValid, code } = useEditorStore();
  const { servers, selectedIndex, select } = useServerStore();
  const [configOpen, setConfigOpen] = useState(false);

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
          <Space size={4} data-testid="status-validation-state">
            <CheckCircleOutlined style={{ color: 'var(--accent-green)', fontSize: 11 }} />
            <Text style={{ color: 'var(--accent-green)', fontSize: 11, fontFamily: 'var(--font-mono)' }}>OK</Text>
          </Space>
        ) : (
          <Space size={4} data-testid="status-validation-state">
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
            variant="borderless"
            dropdownStyle={{ fontSize: 11 }}
          >
            {servers.map((s, i) => (
              <Select.Option key={i} value={i} style={{ fontSize: 11 }}>
                {s.name}
              </Select.Option>
            ))}
          </Select>
          <SettingOutlined
            style={{ color: 'var(--text-tertiary)', fontSize: 11, cursor: 'pointer' }}
            onClick={() => setConfigOpen(true)}
          />
        </Space>

        <Text style={{ color: 'var(--text-tertiary)', fontSize: 11, fontFamily: 'var(--font-mono)' }}>
          Ln {lineCount}
        </Text>
      </Space>

      <ServerConfigModal open={configOpen} onClose={() => setConfigOpen(false)} />
    </div>
  );
}

// ─── Server Config Modal ─────────────────────────────────────────────────

function ServerConfigModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { servers, addServer, updateServer, removeServer, select } = useServerStore();
  const [editIndex, setEditIndex] = useState<number | null>(null);
  const [form, setForm] = useState<ServerConfig>({ name: '', baseUrl: '', apiPrefix: '' });

  function startEdit(index: number) {
    setEditIndex(index);
    setForm({ ...servers[index] });
  }

  function startAdd() {
    setEditIndex(-1);
    setForm({
      name: '',
      baseUrl: '',
      apiPrefix: '/hcmRestApi/redwood/11.13.18.05/calculationEntries',
    });
  }

  function handleSave() {
    if (!form.name.trim() || !form.baseUrl.trim()) return;
    const cleaned: ServerConfig = {
      name: form.name.trim(),
      baseUrl: form.baseUrl.trim(),
      apiPrefix: form.apiPrefix.trim(),
    };
    if (form.auth?.username) {
      cleaned.auth = { username: form.auth.username, password: form.auth.password || '' };
    }
    if (form.workflowCode?.trim()) {
      cleaned.workflowCode = form.workflowCode.trim();
    }
    if (editIndex === -1) {
      addServer(cleaned);
    } else if (editIndex !== null) {
      updateServer(editIndex, cleaned);
    }
    setEditIndex(null);
  }

  function handleDelete(index: number) {
    if (servers.length <= 1) return;
    removeServer(index);
    if (editIndex === index) setEditIndex(null);
  }

  return (
    <Modal
      title="Server Configuration"
      open={open}
      onCancel={onClose}
      footer={null}
      width={600}
    >
      {/* Server list */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
        {servers.map((s, i) => (
          <div
            key={i}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 12px',
              border: '1px solid var(--border-muted)',
              borderRadius: 6,
              backgroundColor: editIndex === i ? 'var(--bg-elevated)' : 'var(--bg-base)',
              cursor: 'pointer',
            }}
            onClick={() => startEdit(i)}
          >
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600, fontSize: 13 }}>{s.name}</div>
              <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
                {s.baseUrl}{s.apiPrefix}
              </div>
            </div>
            <Button
              size="small"
              type="text"
              onClick={(e) => { e.stopPropagation(); select(i); onClose(); }}
              style={{ fontSize: 11 }}
            >
              Connect
            </Button>
            <Button
              size="small"
              type="text"
              danger
              icon={<DeleteOutlined />}
              disabled={servers.length <= 1}
              onClick={(e) => { e.stopPropagation(); handleDelete(i); }}
            />
          </div>
        ))}
      </div>

      <Button
        size="small"
        icon={<PlusOutlined />}
        onClick={startAdd}
        style={{ marginBottom: 16 }}
      >
        Add Server
      </Button>

      {/* Edit form */}
      {editIndex !== null && (
        <div
          style={{
            padding: 16,
            border: '1px solid var(--border-muted)',
            borderRadius: 6,
            backgroundColor: 'var(--bg-elevated)',
          }}
        >
          <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>
            {editIndex === -1 ? 'New Server' : 'Edit Server'}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Input
              size="small"
              placeholder="Name (e.g. Payroll VP DEV)"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
            <Input
              size="small"
              placeholder="Base URL (e.g. http://localhost:8000)"
              value={form.baseUrl}
              onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
            />
            <Input
              size="small"
              placeholder="API Prefix (e.g. /api/11.13.18.05/calculationEntries)"
              value={form.apiPrefix}
              onChange={(e) => setForm({ ...form, apiPrefix: e.target.value })}
            />
            <Input
              size="small"
              placeholder="Username (optional)"
              value={form.auth?.username || ''}
              onChange={(e) =>
                setForm({
                  ...form,
                  auth: e.target.value
                    ? { username: e.target.value, password: form.auth?.password || '' }
                    : undefined,
                })
              }
            />
            {form.auth?.username && (
              <Input.Password
                size="small"
                placeholder="Password"
                value={form.auth?.password || ''}
                onChange={(e) =>
                  setForm({
                    ...form,
                    auth: { username: form.auth!.username, password: e.target.value },
                  })
                }
              />
            )}
            <Input
              size="small"
              placeholder="Workflow Code (optional, e.g. HCM_FAST_FORMULA_GENERATOR1)"
              value={form.workflowCode || ''}
              onChange={(e) => setForm({ ...form, workflowCode: e.target.value || undefined })}
            />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button size="small" onClick={() => setEditIndex(null)}>Cancel</Button>
              <Button
                size="small"
                type="primary"
                disabled={!form.name.trim() || !form.baseUrl.trim()}
                onClick={handleSave}
              >
                {editIndex === -1 ? 'Add' : 'Save'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
