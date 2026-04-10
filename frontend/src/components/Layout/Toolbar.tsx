import { useState } from 'react';
import { Button, Tooltip } from 'antd';
import {
  FileAddOutlined,
  ExportOutlined,
  AppstoreOutlined,
  BugOutlined,
} from '@ant-design/icons';
import { useEditorStore } from '../../stores/editorStore';
import { useChatStore } from '../../stores/chatStore';
import { exportFormula } from '../../services/api';
import { LlmDebugModal } from '../Debug/LlmDebugModal';

interface Props {
  onManageTemplates: () => void;
}

export function Toolbar({ onManageTemplates }: Props) {
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
    <header
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 20,
        padding: '11px 28px',
        backgroundColor: 'var(--bg-surface)',
        borderBottom: '1px solid var(--border-muted)',
        flexShrink: 0,
      }}
    >
      {/* Title block — editorial headline + mono kicker */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 1, lineHeight: 1.1 }}>
        <h1
          style={{
            margin: 0,
            fontFamily: 'var(--font-display)',
            fontWeight: 400,
            fontSize: 19,
            color: 'var(--text-primary)',
            letterSpacing: '-0.005em',
          }}
        >
          Fast Formula
        </h1>
        <div
          style={{
            fontSize: 9,
            color: 'var(--text-tertiary)',
            fontFamily: 'var(--font-mono)',
            letterSpacing: '0.12em',
            textTransform: 'uppercase',
            marginTop: 2,
          }}
        >
          Oracle HCM · AI generator
        </div>
      </div>

      <div style={{ flex: 1 }} />

      {/* Action cluster */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <ToolbarButton icon={<FileAddOutlined />} label="New" onClick={handleNew} />
        <ToolbarButton icon={<ExportOutlined />} label="Export" onClick={handleExport} />
        <Divider />
        <ToolbarButton
          icon={<AppstoreOutlined />}
          label="Templates"
          onClick={onManageTemplates}
          accent
        />
        <Divider />
        <Tooltip title="LLM debug logs" mouseEnterDelay={0.3}>
          <Button
            type="text"
            size="small"
            icon={<BugOutlined />}
            onClick={() => setDebugOpen(true)}
            style={{
              color: 'var(--text-tertiary)',
              height: 32,
              width: 32,
              padding: 0,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          />
        </Tooltip>
      </div>

      <LlmDebugModal open={debugOpen} onClose={() => setDebugOpen(false)} />
    </header>
  );
}

// ──────────────────────────────────────────────────────────────────────────
// Local presentational helpers — kept inline so the toolbar stays one file.

function ToolbarButton({
  icon,
  label,
  onClick,
  accent,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  accent?: boolean;
}) {
  const [hover, setHover] = useState(false);
  const showAccent = accent || hover;
  return (
    <button
      type="button"
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 7,
        background: hover ? 'var(--bg-elevated)' : 'transparent',
        border: 'none',
        color: showAccent ? 'var(--accent-amber)' : 'var(--text-secondary)',
        fontFamily: 'var(--font-body)',
        fontSize: 12.5,
        fontWeight: 500,
        cursor: 'pointer',
        height: 32,
        padding: '0 12px',
        borderRadius: 5,
        letterSpacing: '0.01em',
        transition: 'background 120ms ease, color 120ms ease',
      }}
    >
      <span style={{ fontSize: 13, display: 'inline-flex' }}>{icon}</span>
      <span>{label}</span>
    </button>
  );
}

function Divider() {
  return (
    <div
      aria-hidden
      style={{
        width: 1,
        height: 16,
        backgroundColor: 'var(--border-muted)',
        margin: '0 6px',
      }}
    />
  );
}
