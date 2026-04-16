import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Input, Modal, Select, Switch, Tooltip, message } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  SaveOutlined,
  ArrowLeftOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import MonacoEditor from '@monaco-editor/react';
import axios from 'axios';
import { useServerStore } from '../../stores/serverStore';
import { useFormulaTypes } from '../../hooks/useFormulaTypes';

/**
 * Template — UI representation of a row in FF_FORMULA_TEMPLATES_VL.
 *
 * Server column mapping (see TemplateService.rowToMap in Java):
 *   template_id         <- v.TEMPLATE_ID
 *   formula_type_id     <- v.FORMULA_TYPE_ID        (null = Custom Formula)
 *   formula_type_name   <- FF_FORMULA_TYPES.FORMULA_TYPE_NAME (joined)
 *   template_code       <- v.TEMPLATE_CODE
 *   code                <- v.FORMULA_TEXT           (CLOB)
 *   rule                <- v.ADDITIONAL_PROMPT_TEXT (CLOB)
 *   name                <- v.NAME                   (from _TL)
 *   description         <- v.DESCRIPTION            (from _TL)
 *   source_type         <- v.SOURCE_TYPE            ('SEEDED' or 'USER_CREATED')
 *   active_flag         <- v.ACTIVE_FLAG            ('Y' or 'N')
 *   semantic_flag       <- v.SEMANTIC_FLAG          ('Y' or 'N')
 *   sort_order          <- v.SORT_ORDER             (1-based display order)
 *
 * Unsaved local rows (not yet POSTed) carry a synthetic `_draftKey` to keep
 * them identifiable until the server assigns a real TEMPLATE_ID.
 */
interface Template {
  template_id?: number;
  template_code?: string;
  name: string;
  description: string;
  code: string;
  rule?: string;
  formula_type_id?: number | null;
  formula_type_name?: string | null;
  source_type?: string;
  active_flag?: string;        // 'Y' or 'N', default 'Y'
  semantic_flag?: string;      // 'Y' or 'N', default 'Y'
  systemprompt_flag?: string;  // 'Y' or 'N', default 'N'
  sort_order?: number;
  object_version_number?: number;
  /** Local-only marker for rows that haven't been saved yet. */
  _draftKey?: string;
}

/** Sentinel value used in the formula type filter to mean "Custom Formula" (NULL FK). */
const CUSTOM_FORMULA_TYPE = 'Custom';

interface Props {
  onBack: () => void;
}

// ─── Formula Lookup (load existing formula into template body) ────────────

interface FormulaLookupProps {
  formulaType: string | null;
  serverConfig: import('../../stores/serverStore').ServerConfig;
  onSelect: (formulaText: string) => void;
}

function FormulaLookup({ formulaType, serverConfig, onSelect }: FormulaLookupProps) {
  const PAGE_SIZE = 25;
  const [options, setOptions] = useState<{ value: number; label: string }[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const offsetRef = useRef(0);
  const searchTermRef = useRef<string | undefined>(undefined);
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const headers: Record<string, string> = {};
  if (serverConfig.auth) {
    headers['Authorization'] = `Basic ${btoa(`${serverConfig.auth.username}:${serverConfig.auth.password}`)}`;
  }

  async function fetchFormulas(search?: string, offset = 0, append = false) {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        limit: String(PAGE_SIZE),
        offset: String(offset),
      });
      if (formulaType) params.set('formula_type', formulaType);
      if (search) params.set('search', search);
      const url = `${serverConfig.baseUrl}${serverConfig.apiPrefix}/formulas/lookup?${params}`;
      const res = await axios.get<{ formula_id: number; formula_name: string }[]>(url, { headers });
      const newOpts = res.data.map((f) => ({ value: f.formula_id, label: f.formula_name }));
      setOptions((prev) => (append ? [...prev, ...newOpts] : newOpts));
      setHasMore(res.data.length >= PAGE_SIZE);
      offsetRef.current = offset + res.data.length;
    } catch {
      if (!append) setOptions([]);
      setHasMore(false);
    } finally {
      setLoading(false);
    }
  }

  // Reload when formula type changes
  useEffect(() => {
    offsetRef.current = 0;
    searchTermRef.current = undefined;
    setSelectedId(null);
    fetchFormulas();
  }, [formulaType]);

  function handleSearch(value: string) {
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    searchTermRef.current = value || undefined;
    searchTimerRef.current = setTimeout(() => {
      offsetRef.current = 0;
      fetchFormulas(value || undefined, 0, false);
    }, 300);
  }

  function handlePopupScroll(e: React.UIEvent<HTMLDivElement>) {
    const target = e.target as HTMLDivElement;
    if (loading || !hasMore) return;
    // Load more when scrolled to within 40px of bottom
    if (target.scrollTop + target.clientHeight >= target.scrollHeight - 40) {
      fetchFormulas(searchTermRef.current, offsetRef.current, true);
    }
  }

  async function handleSelect(formulaId: number) {
    setSelectedId(formulaId);
    try {
      const url = `${serverConfig.baseUrl}${serverConfig.apiPrefix}/formulas/lookup/${formulaId}/text`;
      const res = await axios.get<{ formula_text: string }>(url, { headers });
      if (res.data.formula_text) {
        onSelect(res.data.formula_text);
      }
    } catch (err) {
      console.error('Failed to load formula text', err);
    }
  }

  return (
    <Select
      showSearch
      filterOption={false}
      placeholder="Load from formula…"
      onSearch={handleSearch}
      onSelect={handleSelect}
      onPopupScroll={handlePopupScroll}
      loading={loading}
      options={options}
      notFoundContent={loading ? 'Searching…' : 'No formulas found'}
      size="middle"
      style={{
        flex: 1,
        fontSize: 13,
      }}
      allowClear
      value={selectedId}
      onChange={(val) => { if (val === undefined || val === null) setSelectedId(null); }}
    />
  );
}

// ─── Generate Name & Description ──────────────────────────────────────────

interface GenerateMetaButtonProps {
  formulaText: string;
  formulaName: string;
  serverConfig: import('../../stores/serverStore').ServerConfig;
  onGenerated: (name: string, description: string) => void;
}

function GenerateMetaButton({ formulaText, formulaName, serverConfig, onGenerated }: GenerateMetaButtonProps) {
  const [loading, setLoading] = useState(false);

  async function handleClick() {
    if (!formulaText && !formulaName) {
      message.warning('Load a formula first');
      return;
    }
    setLoading(true);
    try {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (serverConfig.auth) {
        headers['Authorization'] = `Basic ${btoa(`${serverConfig.auth.username}:${serverConfig.auth.password}`)}`;
      }
      const resp = await axios.post<{ name: string; description: string }>(
        `${serverConfig.baseUrl}${serverConfig.apiPrefix}/templates/generate-meta`,
        { formula_text: formulaText, formula_name: formulaName },
        { headers }
      );
      if (resp.data.name || resp.data.description) {
        onGenerated(resp.data.name || formulaName, resp.data.description || '');
        message.success('Name & description generated');
      }
    } catch (err: any) {
      message.error('Generate failed: ' + (err?.response?.data?.error || err.message));
    } finally {
      setLoading(false);
    }
  }

  return (
    <Tooltip title="AI generates name & description from the formula">
      <Button
        size="middle"
        loading={loading}
        onClick={handleClick}
        style={{ flexShrink: 0, fontSize: 12 }}
      >
        AI Generate
      </Button>
    </Tooltip>
  );
}

// ─── Extract Prompt from URL ──────────────────────────────────────────────

interface ExtractPromptBarProps {
  formulaType: string | null;
  serverConfig: import('../../stores/serverStore').ServerConfig;
  onExtracted: (promptText: string) => void;
}

function ExtractPromptBar({ formulaType, serverConfig, onExtracted }: ExtractPromptBarProps) {
  const [url, setUrl] = useState('');
  const [extracting, setExtracting] = useState(false);

  async function handleExtract() {
    if (!url.trim()) return;
    setExtracting(true);
    try {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (serverConfig.auth) {
        headers['Authorization'] = `Basic ${btoa(`${serverConfig.auth.username}:${serverConfig.auth.password}`)}`;
      }
      const resp = await axios.post<{ prompt: string }>(
        `${serverConfig.baseUrl}${serverConfig.apiPrefix}/templates/extract-prompt`,
        { url: url.trim(), formula_type: formulaType },
        { headers }
      );
      if (resp.data.prompt) {
        onExtracted(resp.data.prompt);
        message.success('Prompt extracted');
      }
    } catch (err: any) {
      const detail = err?.response?.data?.error || err.message;
      message.error(`Extract failed: ${detail}`);
    } finally {
      setExtracting(false);
    }
  }

  return (
    <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
      <Input
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        placeholder="Oracle help URL (e.g. https://docs.oracle.com/…)"
        style={{
          flex: 1,
          fontSize: 12,
          backgroundColor: 'var(--bg-base)',
          border: '1px solid var(--border-muted)',
        }}
        onPressEnter={handleExtract}
      />
      <Button
        onClick={handleExtract}
        loading={extracting}
        disabled={!url.trim()}
        size="middle"
        style={{
          flexShrink: 0,
          fontSize: 12,
          fontWeight: 600,
        }}
      >
        Extract Prompt
      </Button>
    </div>
  );
}

// ─── Tiny presentational helpers ──────────────────────────────────────────
// Inline styles only — the rest of the project uses inline styles, no CSS
// modules / styled-components. Pulling these into named components keeps the
// JSX below readable without introducing new conventions.

function SectionLabel({ children, hint }: { children: React.ReactNode; hint?: string }) {
  return (
    <div
      style={{
        fontSize: 10,
        fontWeight: 600,
        letterSpacing: '0.14em',
        textTransform: 'uppercase',
        color: 'var(--text-tertiary)',
        fontFamily: 'var(--font-body)',
        marginBottom: 10,
        display: 'flex',
        alignItems: 'baseline',
        gap: 10,
      }}
    >
      <span>{children}</span>
      {hint && (
        <span
          style={{
            marginLeft: 'auto',
            fontFamily: 'var(--font-mono)',
            letterSpacing: 0,
            textTransform: 'none',
            fontSize: 10,
            color: 'var(--text-tertiary)',
            opacity: 0.7,
          }}
        >
          {hint}
        </span>
      )}
    </div>
  );
}

function StatusDot({ color, title }: { color: string; title: string }) {
  return (
    <span
      title={title}
      style={{
        display: 'inline-block',
        width: 7,
        height: 7,
        borderRadius: '50%',
        backgroundColor: color,
        flexShrink: 0,
      }}
    />
  );
}

// ─── Main panel ───────────────────────────────────────────────────────────

export function TemplatesPanel({ onBack }: Props) {
  const { current } = useServerStore();
  const { formulaTypes } = useFormulaTypes();

  // All formula types from FF_FORMULA_TYPES (for the detail panel type picker)
  const [allFormulaTypes, setAllFormulaTypes] = useState<{ type_name: string; display_name: string }[]>([]);
  useEffect(() => {
    const headers: Record<string, string> = {};
    if (current.auth) {
      headers['Authorization'] = `Basic ${btoa(`${current.auth.username}:${current.auth.password}`)}`;
    }
    axios
      .get<{ type_name: string; display_name: string }[]>(
        `${current.baseUrl}${current.apiPrefix}/formula-types?all=true`,
        { headers }
      )
      .then((res) => setAllFormulaTypes(res.data))
      .catch(() => {
        // Fallback: use the template-filtered list
        setAllFormulaTypes(formulaTypes.map((ft) => ({ type_name: ft.type_name, display_name: ft.display_name })));
      });
  }, [current.baseUrl]);

  const [templates, setTemplates] = useState<Template[]>([]);
  const [selectedIndex, setSelectedIndex] = useState<number>(-1);
  const [editName, setEditName] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [editCode, setEditCode] = useState('');
  const [editRule, setEditRule] = useState('');
  const [editActive, setEditActive] = useState(true);
  const [editSemantic, setEditSemantic] = useState(true);
  const [editSystemPrompt, setEditSystemPrompt] = useState(false);
  const [isDirty, setIsDirty] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [reordering, setReordering] = useState(false);
  // Filter — defaults to Custom Formula since that's where existing JSON samples live
  const [filterType, setFilterType] = useState<string>(CUSTOM_FORMULA_TYPE);
  // Track which row in the list is being hovered, so we can lift it subtly.
  const [hoverIndex, setHoverIndex] = useState<number>(-1);

  function getHeaders(): Record<string, string> {
    const headers: Record<string, string> = {};
    if (current.auth) {
      headers['Authorization'] = `Basic ${btoa(`${current.auth.username}:${current.auth.password}`)}`;
    }
    return headers;
  }

  function templatesUrl(path: string = ''): string {
    return `${current.baseUrl}${current.apiPrefix}/templates${path}`;
  }

  // Load whenever the formula type filter changes — the server does the filtering.
  useEffect(() => {
    loadTemplates(filterType);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterType, current.baseUrl]);

  async function loadTemplates(type: string) {
    setLoading(true);
    try {
      const resp = await axios.get<Template[]>(templatesUrl(), {
        // include_inactive=true → Manage Templates shows disabled rows too so the
        // admin can toggle them back on. Without this param the endpoint defaults
        // to active-only (consumer view).
        params: { formula_type: type, include_inactive: true },
        headers: getHeaders(),
      });
      setTemplates(resp.data || []);
      if (resp.data && resp.data.length > 0) {
        selectTemplate(0, resp.data);
      } else {
        clearEditor();
      }
    } catch (err: any) {
      message.error(`Failed to load templates: ${err?.response?.data?.error || err.message}`);
      setTemplates([]);
      clearEditor();
    } finally {
      setLoading(false);
    }
  }

  function clearEditor() {
    setSelectedIndex(-1);
    setEditName('');
    setEditDesc('');
    setEditCode('');
    setEditRule('');
    setEditActive(true);
    setEditSemantic(true);
    setIsDirty(false);
  }

  function selectTemplate(index: number, list?: Template[]) {
    const source = list || templates;
    const t = source[index];
    if (!t) return;
    setSelectedIndex(index);
    setEditName(t.name || '');
    setEditDesc(t.description || '');
    setEditCode(t.code || '');
    setEditRule(t.rule || '');
    // Flags default to 'Y' when the row is new or missing the column
    setEditActive(t.active_flag !== 'N');
    setEditSemantic(t.semantic_flag !== 'N');
    setEditSystemPrompt(t.systemprompt_flag === 'Y');
    setIsDirty(false);
  }

  function handleFilterChange(newType: string) {
    if (isDirty) {
      Modal.confirm({
        title: 'Discard unsaved changes?',
        content: 'Switching formula type will drop your current edits.',
        okText: 'Discard',
        okButtonProps: { danger: true },
        onOk: () => setFilterType(newType),
      });
      return;
    }
    setFilterType(newType);
  }

  function handleNew() {
    const draftKey = `draft-${Date.now()}`;
    const typeLabel = filterType === CUSTOM_FORMULA_TYPE ? 'Custom' : filterType;
    const newTemplate: Template = {
      _draftKey: draftKey,
      name: 'New Template',
      description: 'Description',
      rule: '',
      formula_type_id: null,
      formula_type_name: filterType === CUSTOM_FORMULA_TYPE ? null : filterType,
      source_type: 'USER_CREATED',
      active_flag: 'Y',
      semantic_flag: 'N',
      sort_order: templates.length + 1,
      code: `/******************************************************************************
 *
 * Formula Name : NEW_TEMPLATE
 *
 * Formula Type : ${typeLabel}
 *
 * Description  : New template
 *
 ******************************************************************************/

DEFAULT FOR HOURS_WORKED IS 0

INPUTS ARE HOURS_WORKED

l_log = PAY_INTERNAL_LOG_WRITE('NEW_TEMPLATE - Enter')

l_result = HOURS_WORKED

l_log = PAY_INTERNAL_LOG_WRITE('NEW_TEMPLATE - Exit')

RETURN l_result

/* End Formula Text */`,
    };
    const updated = [...templates, newTemplate];
    setTemplates(updated);
    selectTemplate(updated.length - 1, updated);
    setIsDirty(true);
  }

  async function handleSave() {
    if (selectedIndex < 0) return;
    const current = templates[selectedIndex];
    if (!current) return;

    const payload: Record<string, unknown> = {
      name: editName,
      description: editDesc,
      code: editCode,
      rule: editRule,
      active_flag: editActive ? 'Y' : 'N',
      semantic_flag: editSemantic ? 'Y' : 'N',
      systemprompt_flag: editSystemPrompt ? 'Y' : 'N',
      formula_type: current.formula_type_name || CUSTOM_FORMULA_TYPE,
    };

    setSaving(true);
    try {
      let saved: Template;
      if (current.template_id == null) {
        // New template — POST
        const resp = await axios.post<Template>(templatesUrl(), payload, { headers: getHeaders() });
        saved = resp.data;
      } else {
        // Existing — PUT
        const resp = await axios.put<Template>(
          templatesUrl(`/${current.template_id}`),
          payload,
          { headers: getHeaders() }
        );
        saved = resp.data;
      }

      const savedType = saved.formula_type_name || CUSTOM_FORMULA_TYPE;
      if (savedType !== filterType) {
        // Auto-switch filter so the saved template appears in the left list
        setFilterType(savedType);
      } else {
        const updated = [...templates];
        updated[selectedIndex] = saved;
        setTemplates(updated);
      }
      setIsDirty(false);
      message.success('Saved');
    } catch (err: any) {
      const detail = err?.response?.data?.error || err?.response?.data?.detail || err.message;
      message.error(`Save failed: ${detail}`);
    } finally {
      setSaving(false);
    }
  }

  function handleDelete() {
    if (selectedIndex < 0) return;
    const current = templates[selectedIndex];
    if (!current) return;

    Modal.confirm({
      title: 'Delete Template',
      content: `Delete "${editName}"?`,
      okButtonProps: { danger: true },
      onOk: async () => {
        // Unsaved drafts: just drop locally.
        if (current.template_id == null) {
          const updated = templates.filter((_, i) => i !== selectedIndex);
          setTemplates(updated);
          if (updated.length > 0) {
            selectTemplate(Math.min(selectedIndex, updated.length - 1), updated);
          } else {
            clearEditor();
          }
          return;
        }

        try {
          await axios.delete(templatesUrl(`/${current.template_id}`), { headers: getHeaders() });
          const updated = templates.filter((_, i) => i !== selectedIndex);
          setTemplates(updated);
          if (updated.length > 0) {
            selectTemplate(Math.min(selectedIndex, updated.length - 1), updated);
          } else {
            clearEditor();
          }
          message.success('Deleted');
        } catch (err: any) {
          const detail = err?.response?.data?.error || err.message;
          message.error(`Delete failed: ${detail}`);
        }
      },
    });
  }

  /**
   * Swap the template at `fromIdx` with the one at `toIdx`, then persist the
   * new SORT_ORDER values to the server via two partial PUTs — one for each
   * affected row. Uses 1-based positions so the first row has SORT_ORDER=1.
   *
   * If either row is an unsaved draft (no template_id), its sort_order is
   * only updated locally; the server write happens on first Save.
   */
  async function swapAndPersist(fromIdx: number, toIdx: number) {
    if (fromIdx === toIdx) return;
    const a = templates[fromIdx];
    const b = templates[toIdx];
    if (!a || !b) return;

    // Swap locally first for immediate feedback.
    const updated = [...templates];
    updated[fromIdx] = { ...b, sort_order: fromIdx + 1 };
    updated[toIdx]   = { ...a, sort_order: toIdx + 1 };
    setTemplates(updated);
    setSelectedIndex(toIdx);

    // Persist both new sort_orders — only for rows that exist in DB.
    const puts: Promise<unknown>[] = [];
    if (a.template_id != null) {
      puts.push(
        axios.put(
          templatesUrl(`/${a.template_id}`),
          { sort_order: toIdx + 1 },
          { headers: getHeaders() }
        )
      );
    }
    if (b.template_id != null) {
      puts.push(
        axios.put(
          templatesUrl(`/${b.template_id}`),
          { sort_order: fromIdx + 1 },
          { headers: getHeaders() }
        )
      );
    }
    if (puts.length === 0) return; // both are drafts, nothing to persist

    setReordering(true);
    try {
      await Promise.all(puts);
    } catch (err: any) {
      const detail = err?.response?.data?.error || err.message;
      message.error(`Reorder failed: ${detail}`);
      // Best-effort: refresh from server so local state doesn't lie.
      await loadTemplates(filterType);
    } finally {
      setReordering(false);
    }
  }

  function handleMoveUp() {
    if (selectedIndex <= 0) return;
    void swapAndPersist(selectedIndex, selectedIndex - 1);
  }

  function handleMoveDown() {
    if (selectedIndex < 0 || selectedIndex >= templates.length - 1) return;
    void swapAndPersist(selectedIndex, selectedIndex + 1);
  }

  // Build the formula type filter options.
  // "Custom Formula" appears first, then all other types alphabetically.
  const filterOptions = useMemo(() => {
    const customOption = { value: CUSTOM_FORMULA_TYPE, label: 'Custom Formula' };
    const otherOptions = formulaTypes
      .filter((ft) => ft.type_name !== CUSTOM_FORMULA_TYPE)
      .sort((a, b) => a.display_name.localeCompare(b.display_name))
      .map((ft) => ({ value: ft.type_name, label: ft.display_name }));
    return [customOption, ...otherOptions];
  }, [formulaTypes]);

  const filterLabel =
    filterOptions.find((o) => o.value === filterType)?.label ?? filterType;

  // Currently selected template (for the detail header).
  const selected = selectedIndex >= 0 ? templates[selectedIndex] : null;

  // Semantic ON ⇒ formula body & prompt overlay are LOCKED (frozen because
  // the row participates in semantic / vector retrieval and editing it would
  // invalidate the embedding). Semantic OFF ⇒ the row is a manual reference
  // that the admin can freely edit.
  const fieldsLocked = editSemantic;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: 'var(--bg-base)',
        color: 'var(--text-primary)',
        fontFamily: 'var(--font-body)',
      }}
    >
      {/* ─────────────── Page header ─────────────── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 24,
          padding: '18px 28px 16px',
          borderBottom: '1px solid var(--border-muted)',
          backgroundColor: 'var(--bg-surface)',
        }}
      >
        <button
          onClick={onBack}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            background: 'transparent',
            border: 'none',
            color: 'var(--text-secondary)',
            fontFamily: 'var(--font-body)',
            fontSize: 12,
            cursor: 'pointer',
            padding: '6px 8px',
            borderRadius: 4,
          }}
          onMouseEnter={(e) => {
            (e.currentTarget as HTMLElement).style.color = 'var(--accent-amber)';
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLElement).style.color = 'var(--text-secondary)';
          }}
        >
          <ArrowLeftOutlined style={{ fontSize: 11 }} />
          <span>Back to editor</span>
        </button>

        <div
          style={{
            width: 1,
            height: 24,
            backgroundColor: 'var(--border-muted)',
          }}
        />

        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, lineHeight: 1.1 }}>
          <h1
            style={{
              margin: 0,
              fontFamily: 'var(--font-display)',
              fontWeight: 400,
              fontSize: 22,
              color: 'var(--text-primary)',
              letterSpacing: '-0.005em',
            }}
          >
            Manage Templates
          </h1>
          <div
            style={{
              fontSize: 11,
              color: 'var(--text-tertiary)',
              fontFamily: 'var(--font-mono)',
              letterSpacing: '0.02em',
            }}
          >
            {templates.length} {templates.length === 1 ? 'template' : 'templates'} ·{' '}
            <span style={{ color: 'var(--text-secondary)' }}>{filterLabel}</span>
          </div>
        </div>

        <div style={{ flex: 1 }} />

        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleNew}
          style={{
            backgroundColor: 'var(--accent-amber)',
            borderColor: 'var(--accent-amber)',
            fontFamily: 'var(--font-body)',
            fontWeight: 500,
            height: 32,
            paddingInline: 14,
            boxShadow: 'var(--shadow-sm)',
          }}
        >
          New template
        </Button>
      </div>

      {/* ─────────────── Body: list + detail ─────────────── */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* ──────── Left: filter + list ──────── */}
        <aside
          style={{
            width: 340,
            borderRight: '1px solid var(--border-muted)',
            display: 'flex',
            flexDirection: 'column',
            flexShrink: 0,
            backgroundColor: 'var(--bg-surface)',
          }}
        >
          {/* Filter section */}
          <div
            style={{
              padding: '16px 20px 14px',
              borderBottom: '1px solid var(--border-muted)',
            }}
          >
            <SectionLabel>Formula type</SectionLabel>
            <Select
              value={filterType}
              onChange={handleFilterChange}
              showSearch
              optionFilterProp="label"
              style={{ width: '100%' }}
              options={filterOptions}
              placeholder="Filter by formula type"
              variant="borderless"
              dropdownStyle={{ fontFamily: 'var(--font-body)' }}
            />
          </div>

          {/* List */}
          <div style={{ flex: 1, overflow: 'auto' }}>
            {templates.length === 0 ? (
              <div
                style={{
                  padding: '40px 20px',
                  textAlign: 'center',
                  fontFamily: 'var(--font-body)',
                }}
              >
                <div
                  style={{
                    fontFamily: 'var(--font-display)',
                    fontWeight: 400,
                    fontSize: 18,
                    color: 'var(--text-secondary)',
                    marginBottom: 8,
                    fontStyle: 'italic',
                  }}
                >
                  Nothing here yet
                </div>
                <div
                  style={{
                    fontSize: 12,
                    color: 'var(--text-tertiary)',
                    lineHeight: 1.5,
                  }}
                >
                  {loading
                    ? 'Loading templates…'
                    : 'Click “New template” to draft your first one.'}
                </div>
              </div>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                {templates.map((item, index) => {
                  const isSelected = index === selectedIndex;
                  const isHovered = hoverIndex === index && !isSelected;
                  const isInactive = item.active_flag === 'N';
                  const isDraft = item.template_id == null;
                  const isSeed = item.source_type === 'SEEDED';

                  // Status dot color: draft (amber) > seeded (teal-ish) > user-created (muted)
                  const dotColor = isDraft
                    ? 'var(--accent-amber)'
                    : isSeed
                    ? 'var(--accent-teal)'
                    : 'var(--text-tertiary)';
                  const dotTitle = isDraft
                    ? 'Unsaved draft'
                    : isSeed
                    ? 'Oracle-seeded'
                    : 'User-created';

                  return (
                    <li
                      key={item.template_id ?? item._draftKey ?? index}
                      onClick={() => selectTemplate(index)}
                      onMouseEnter={() => setHoverIndex(index)}
                      onMouseLeave={() => setHoverIndex(-1)}
                      style={{
                        position: 'relative',
                        cursor: 'pointer',
                        padding: '14px 20px 14px 24px',
                        borderBottom: '1px solid var(--border-muted)',
                        background: isSelected
                          ? 'linear-gradient(90deg, var(--accent-amber-dim) 0%, transparent 100%)'
                          : isHovered
                          ? 'var(--bg-elevated)'
                          : 'transparent',
                        transition: 'background 120ms ease',
                        opacity: isInactive ? 0.55 : 1,
                      }}
                    >
                      {/* Selection bar (left edge) */}
                      <span
                        aria-hidden
                        style={{
                          position: 'absolute',
                          left: 0,
                          top: 0,
                          bottom: 0,
                          width: 3,
                          backgroundColor: isSelected ? 'var(--accent-amber)' : 'transparent',
                        }}
                      />

                      {/* Top row: dot, name, sort# */}
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 10,
                          marginBottom: 4,
                        }}
                      >
                        <StatusDot color={dotColor} title={dotTitle} />
                        <span
                          style={{
                            flex: 1,
                            fontSize: 13.5,
                            fontWeight: isSelected ? 600 : 500,
                            color: isSelected ? 'var(--text-primary)' : 'var(--text-secondary)',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            letterSpacing: '-0.005em',
                          }}
                        >
                          {item.name || '(unnamed)'}
                        </span>
                        {item.sort_order != null && (
                          <span
                            style={{
                              fontFamily: 'var(--font-mono)',
                              fontSize: 10,
                              color: 'var(--text-tertiary)',
                              padding: '0 4px',
                              minWidth: 16,
                              textAlign: 'right',
                            }}
                          >
                            {item.sort_order.toString().padStart(2, '0')}
                          </span>
                        )}
                      </div>

                      {/* Description */}
                      <div
                        style={{
                          fontSize: 11.5,
                          color: 'var(--text-tertiary)',
                          marginLeft: 17,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          lineHeight: 1.4,
                        }}
                      >
                        {item.description || (
                          <span style={{ fontStyle: 'italic', opacity: 0.6 }}>no description</span>
                        )}
                      </div>

                      {/* Bottom badges row */}
                      {(isInactive || isDraft || isSeed) && (
                        <div
                          style={{
                            display: 'flex',
                            gap: 6,
                            marginTop: 6,
                            marginLeft: 17,
                            fontSize: 9,
                            fontFamily: 'var(--font-mono)',
                            letterSpacing: '0.06em',
                            textTransform: 'uppercase',
                            color: 'var(--text-tertiary)',
                          }}
                        >
                          {isDraft && <span style={{ color: 'var(--accent-amber)' }}>Draft</span>}
                          {isSeed && !isDraft && <span style={{ color: 'var(--accent-teal)' }}>Seeded</span>}
                          {isInactive && <span style={{ color: 'var(--accent-red)' }}>Hidden</span>}
                        </div>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          {/* Footer */}
          <div
            style={{
              padding: '10px 20px',
              borderTop: '1px solid var(--border-muted)',
              fontSize: 10,
              fontFamily: 'var(--font-mono)',
              color: 'var(--text-tertiary)',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              letterSpacing: '0.04em',
            }}
          >
            <span>{templates.length} loaded</span>
            <div style={{ flex: 1 }} />
            <Tooltip title="Reload from database">
              <button
                onClick={() => loadTemplates(filterType)}
                disabled={loading}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: 'var(--text-tertiary)',
                  cursor: loading ? 'wait' : 'pointer',
                  padding: 4,
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                <ReloadOutlined spin={loading} style={{ fontSize: 11 }} />
              </button>
            </Tooltip>
          </div>
        </aside>

        {/* ──────── Right: detail ──────── */}
        <main style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          {selected ? (
            <>
              {/* ─── Detail header strip ─── */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 24,
                  padding: '24px 32px 18px',
                  borderBottom: '1px solid var(--border-muted)',
                  backgroundColor: 'var(--bg-surface)',
                }}
              >
                {/* Title block */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      fontSize: 10,
                      fontWeight: 600,
                      letterSpacing: '0.14em',
                      textTransform: 'uppercase',
                      color: 'var(--text-tertiary)',
                      marginBottom: 4,
                      fontFamily: 'var(--font-mono)',
                    }}
                  >
                    {selected.template_id != null
                      ? `Template · ${selected.template_code ?? `#${selected.template_id}`}`
                      : 'Unsaved draft'}
                  </div>
                  <input
                    value={editName}
                    onChange={(e) => {
                      setEditName(e.target.value);
                      setIsDirty(true);
                    }}
                    placeholder="Untitled template"
                    style={{
                      width: '100%',
                      border: 'none',
                      background: 'transparent',
                      outline: 'none',
                      fontFamily: 'var(--font-display)',
                      fontWeight: 400,
                      fontSize: 28,
                      lineHeight: 1.2,
                      color: 'var(--text-primary)',
                      letterSpacing: '-0.01em',
                      padding: 0,
                    }}
                  />
                </div>

                {/* Actions */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    paddingTop: 16,
                  }}
                >
                  <Tooltip title="Move up — persists SORT_ORDER">
                    <Button
                      size="small"
                      type="text"
                      icon={<ArrowUpOutlined />}
                      onClick={handleMoveUp}
                      disabled={selectedIndex <= 0 || reordering}
                      loading={reordering}
                    />
                  </Tooltip>
                  <Tooltip title="Move down — persists SORT_ORDER">
                    <Button
                      size="small"
                      type="text"
                      icon={<ArrowDownOutlined />}
                      onClick={handleMoveDown}
                      disabled={selectedIndex >= templates.length - 1 || reordering}
                    />
                  </Tooltip>

                  <div
                    style={{
                      width: 1,
                      height: 18,
                      backgroundColor: 'var(--border-muted)',
                      margin: '0 4px',
                    }}
                  />

                  <Button
                    type={isDirty ? 'primary' : 'default'}
                    icon={<SaveOutlined />}
                    onClick={handleSave}
                    loading={saving}
                    disabled={saving}
                    style={{
                      fontWeight: 500,
                      ...(isDirty && {
                        backgroundColor: 'var(--accent-amber)',
                        borderColor: 'var(--accent-amber)',
                      }),
                    }}
                  >
                    {isDirty ? 'Save changes' : 'Saved'}
                  </Button>
                  <Tooltip title="Delete template">
                    <Button
                      danger
                      type="text"
                      icon={<DeleteOutlined />}
                      onClick={handleDelete}
                    />
                  </Tooltip>
                </div>
              </div>

              {/* ─── Form sections (scroll-able if needed) + Monaco at bottom ─── */}
              <div
                style={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  overflow: 'hidden',
                }}
              >
                {/* Identity + Behavior + Additional Prompt Text (scrollable on small heights) */}
                <div
                  style={{
                    padding: '20px 32px 16px',
                    borderBottom: '1px solid var(--border-muted)',
                    backgroundColor: 'var(--bg-surface)',
                    overflowY: 'auto',
                    maxHeight: '50%',
                  }}
                >
                  {/* Identity */}
                  <section style={{ marginBottom: 22 }}>
                    <SectionLabel>Identity</SectionLabel>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      <Input
                        value={editDesc}
                        onChange={(e) => {
                          setEditDesc(e.target.value);
                          setIsDirty(true);
                        }}
                        placeholder="A short description of what this template does"
                        style={{
                          fontSize: 13,
                          backgroundColor: 'var(--bg-base)',
                          border: '1px solid var(--border-muted)',
                        }}
                      />
                      <div style={{ display: 'flex', gap: 8 }}>
                        {/* Formula type — all types from FF_FORMULA_TYPES */}
                        <Select
                          value={selected?.formula_type_name || CUSTOM_FORMULA_TYPE}
                          onChange={(val: string) => {
                            if (selected) {
                              const updated = [...templates];
                              updated[selectedIndex] = {
                                ...selected,
                                formula_type_name: val === CUSTOM_FORMULA_TYPE ? null : val,
                              };
                              setTemplates(updated);
                              setIsDirty(true);
                            }
                          }}
                          showSearch
                          optionFilterProp="label"
                          placeholder="Formula type"
                          style={{ flex: 1, fontSize: 13 }}
                          size="middle"
                          options={allFormulaTypes.map((ft) => ({
                            value: ft.type_name,
                            label: ft.display_name,
                          }))}
                        />
                        {/* Formula name — search FF_FORMULAS_VL, load code */}
                        <FormulaLookup
                          formulaType={selected?.formula_type_name || null}
                          serverConfig={current}
                          onSelect={(text) => {
                            setEditCode(text);
                            setIsDirty(true);
                          }}
                        />
                        <GenerateMetaButton
                          formulaText={editCode}
                          formulaName={editName}
                          serverConfig={current}
                          onGenerated={(name, desc) => {
                            setEditName(name);
                            setEditDesc(desc);
                            setIsDirty(true);
                          }}
                        />
                      </div>
                    </div>
                  </section>

                  {/* Behavior */}
                  <section style={{ marginBottom: 22 }}>
                    <SectionLabel hint="active_flag · semantic_flag">Behavior</SectionLabel>
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr 1fr',
                        gap: 12,
                      }}
                    >
                      {/* Active toggle card */}
                      <label
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 4,
                          padding: '12px 14px',
                          border: '1px solid var(--border-muted)',
                          borderRadius: 6,
                          backgroundColor: 'var(--bg-base)',
                          cursor: 'pointer',
                        }}
                      >
                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                          }}
                        >
                          <span
                            style={{
                              fontSize: 12,
                              fontWeight: 600,
                              color: 'var(--text-primary)',
                              letterSpacing: '0.01em',
                            }}
                          >
                            Active
                          </span>
                          <Switch
                            size="small"
                            checked={editActive}
                            onChange={(v) => {
                              setEditActive(v);
                              setIsDirty(true);
                            }}
                          />
                        </div>
                        <span
                          style={{
                            fontSize: 11,
                            color: 'var(--text-tertiary)',
                            lineHeight: 1.4,
                          }}
                        >
                          Visible to formula authors. Off = hidden from the editor.
                        </span>
                      </label>

                      {/* Semantic toggle card */}
                      <label
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 4,
                          padding: '12px 14px',
                          border: `1px solid ${
                            fieldsLocked ? 'var(--accent-amber)' : 'var(--border-muted)'
                          }`,
                          borderRadius: 6,
                          backgroundColor: fieldsLocked
                            ? 'var(--accent-amber-dim)'
                            : 'var(--bg-base)',
                          cursor: 'pointer',
                          transition: 'border-color 120ms ease, background 120ms ease',
                        }}
                      >
                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                          }}
                        >
                          <span
                            style={{
                              fontSize: 12,
                              fontWeight: 600,
                              color: 'var(--text-primary)',
                              letterSpacing: '0.01em',
                            }}
                          >
                            Semantic
                          </span>
                          <Switch
                            size="small"
                            checked={editSemantic}
                            onChange={(v) => {
                              setEditSemantic(v);
                              setIsDirty(true);
                            }}
                          />
                        </div>
                        <span
                          style={{
                            fontSize: 11,
                            color: fieldsLocked
                              ? 'var(--accent-amber)'
                              : 'var(--text-tertiary)',
                            lineHeight: 1.4,
                          }}
                        >
                          {fieldsLocked
                            ? 'Indexed in RAG · prompt + body are frozen.'
                            : 'Manual reference. Body is unlocked for editing.'}
                        </span>
                      </label>
                    </div>

                    {/* System Prompt flag */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 8 }}>
                      <label
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 3,
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span
                            style={{
                              fontSize: 11,
                              fontWeight: 600,
                              color: editSystemPrompt
                                ? 'var(--accent-amber)'
                                : 'var(--text-tertiary)',
                              fontFamily: 'var(--font-body)',
                              letterSpacing: '0.06em',
                              textTransform: 'uppercase',
                            }}
                          >
                            System Prompt
                          </span>
                          <Switch
                            size="small"
                            checked={editSystemPrompt}
                            onChange={(v) => {
                              setEditSystemPrompt(v);
                              setIsDirty(true);
                            }}
                          />
                        </div>
                        <span
                          style={{
                            fontSize: 11,
                            color: editSystemPrompt
                              ? 'var(--accent-amber)'
                              : 'var(--text-tertiary)',
                            lineHeight: 1.4,
                          }}
                        >
                          {editSystemPrompt
                            ? 'This row\'s FORMULA_TEXT is the LLM system prompt.'
                            : 'Normal template (not a system prompt).'}
                        </span>
                      </label>
                    </div>
                  </section>

                  {/* Additional Prompt Text */}
                  <section>
                    <SectionLabel hint="ADDITIONAL_PROMPT_TEXT">Additional Prompt Text</SectionLabel>
                    {!fieldsLocked && (
                      <ExtractPromptBar
                        formulaType={selected?.formula_type_name || null}
                        serverConfig={current}
                        onExtracted={(text) => {
                          setEditRule(text);
                          setIsDirty(true);
                        }}
                      />
                    )}
                    <Input.TextArea
                      value={editRule}
                      rows={5}
                      disabled={fieldsLocked}
                      onChange={(e) => {
                        setEditRule(e.target.value);
                        setIsDirty(true);
                      }}
                      placeholder={
                        fieldsLocked
                          ? 'Locked while Semantic is on — toggle Semantic off to edit.'
                          : 'Optional rule appended to the AI system prompt when this template is selected.'
                      }
                      style={{
                        fontSize: 12,
                        fontFamily: 'var(--font-mono)',
                        backgroundColor: fieldsLocked
                          ? 'var(--bg-inset)'
                          : 'var(--bg-base)',
                        border: '1px solid var(--border-muted)',
                        color: fieldsLocked
                          ? 'var(--text-tertiary)'
                          : 'var(--text-primary)',
                      }}
                    />
                  </section>
                </div>

                {/* Reference Formula — fills remaining height */}
                <div
                  style={{
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    minHeight: 0,
                    backgroundColor: 'var(--bg-surface)',
                  }}
                >
                  <div
                    style={{
                      padding: '14px 32px 8px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 12,
                    }}
                  >
                    <div style={{ flex: 1 }}>
                      <SectionLabel hint="FORMULA_TEXT · CLOB">Reference Formula</SectionLabel>
                    </div>
                    {fieldsLocked && (
                      <div
                        style={{
                          fontSize: 10,
                          fontFamily: 'var(--font-mono)',
                          letterSpacing: '0.08em',
                          textTransform: 'uppercase',
                          color: 'var(--accent-amber)',
                          padding: '3px 8px',
                          border: '1px solid var(--accent-amber)',
                          borderRadius: 3,
                          backgroundColor: 'var(--accent-amber-dim)',
                        }}
                      >
                        Locked · semantic on
                      </div>
                    )}
                  </div>

                  <div
                    style={{
                      flex: 1,
                      margin: '0 32px 24px',
                      border: '1px solid var(--border-muted)',
                      borderRadius: 6,
                      overflow: 'hidden',
                      boxShadow: 'var(--shadow-sm)',
                      backgroundColor: fieldsLocked ? 'var(--bg-inset)' : '#ffffff',
                    }}
                  >
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
                        readOnly: fieldsLocked,
                        renderLineHighlight: fieldsLocked ? 'none' : 'line',
                        padding: { top: 12, bottom: 12 },
                      }}
                    />
                  </div>
                </div>
              </div>
            </>
          ) : (
            // Empty state
            <div
              style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 14,
                padding: 48,
                color: 'var(--text-secondary)',
              }}
            >
              <div
                aria-hidden
                style={{
                  width: 56,
                  height: 56,
                  borderRadius: '50%',
                  backgroundColor: 'var(--bg-elevated)',
                  border: '1px solid var(--border-muted)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'var(--text-tertiary)',
                  fontSize: 22,
                }}
              >
                <PlusOutlined />
              </div>
              <div
                style={{
                  fontFamily: 'var(--font-display)',
                  fontWeight: 400,
                  fontSize: 22,
                  color: 'var(--text-primary)',
                  fontStyle: 'italic',
                  letterSpacing: '-0.01em',
                }}
              >
                {loading ? 'Loading templates…' : 'Pick a template to start editing'}
              </div>
              <div
                style={{
                  fontSize: 13,
                  color: 'var(--text-tertiary)',
                  textAlign: 'center',
                  lineHeight: 1.5,
                  maxWidth: 360,
                }}
              >
                Choose a template from the list on the left, or create a new one with the
                button above.
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
