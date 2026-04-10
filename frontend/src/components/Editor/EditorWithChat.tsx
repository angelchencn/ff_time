import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Input, Select, Typography } from 'antd';
import { SendOutlined, MessageOutlined, UpOutlined, DownOutlined } from '@ant-design/icons';
import { useChatStore } from '../../stores/chatStore';
import { useEditorStore } from '../../stores/editorStore';
import { useServerStore } from '../../stores/serverStore';
import { streamSSE } from '../../services/sse';
import { extractCodeBlocks } from '../../services/formulaUtils';
import { ChatMessage } from '../ChatPanel/ChatMessage';
import { FFEditor } from './FFEditor';
import { useFormulaTypes } from '../../hooks/useFormulaTypes';
import { useTemplatesByFormulaType } from '../../hooks/useTemplatesByFormulaType';

const { Text } = Typography;

// Sample templates are loaded from the FF_FORMULA_TEMPLATES table via
// useTemplatesByFormulaType — no longer from the legacy sample_prompts[]
// embedded in /api/formula-types.

const MIN_CHAT_HEIGHT = 0;
const MAX_CHAT_HEIGHT = 500;
const DEFAULT_CHAT_HEIGHT = 200;

export function EditorWithChat() {
  const { messages, isStreaming, sessionId, addMessage, appendToLast, replaceLastContent, setStreaming, setSessionId } =
    useChatStore();
  const { code, setCode, formulaType, setFormulaType } = useEditorStore();
  const { current } = useServerStore();
  const [inputValue, setInputValue] = useState('');
  // selectedSampleId stores the picked template's primary key (template_id)
  // purely for the dropdown's own state — antd Select needs a stable value.
  const [selectedSampleId, setSelectedSampleId] = useState<number | null>(null);
  // selectedTemplateCodeKey stores the picked template's TEMPLATE_CODE column
  // value — a short business-key identifier like
  // "ORA_FFT_CUSTOM_OVERTIME_PAY_CALCULATION_001". This is the only thing we
  // send to the /chat backend; the server does its own DB lookup on the code
  // to fetch the full FORMULA_TEXT + ADDITIONAL_PROMPT_TEXT. Keeps the
  // request payload small and stops the frontend from drifting away from
  // the authoritative template store.
  const [selectedTemplateCodeKey, setSelectedTemplateCodeKey] = useState<string | null>(null);
  const { formulaTypes } = useFormulaTypes();
  const { templates: dbTemplates, loading: templatesLoading } =
    useTemplatesByFormulaType(formulaType);
  const [chatOpen, setChatOpen] = useState(false);
  const [chatHeight, setChatHeight] = useState(DEFAULT_CHAT_HEIGHT);
  const listEndRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  // Sample dropdown options come from the DB-backed templates list.
  // We show the human name (or fall back to description) and key by
  // template_id so picking is unambiguous even if two templates share a name.
  const sampleOptions = useMemo(() => {
    return dbTemplates
      .filter((t) => t.template_id != null)
      .map((t) => ({
        value: t.template_id as number,
        label: t.name || t.description || `#${t.template_id}`,
        description: t.description,
      }));
  }, [dbTemplates]);

  // Reset the picked sample whenever the formula type changes — the previous
  // selection no longer applies.
  useEffect(() => {
    setSelectedSampleId(null);
    setSelectedTemplateCodeKey(null);
  }, [formulaType]);

  useEffect(() => {
    listEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Toggle busy cursor on body during streaming
  useEffect(() => {
    document.body.classList.toggle('is-streaming', isStreaming);
    return () => { document.body.classList.remove('is-streaming'); };
  }, [isStreaming]);

  // Chat stays closed by default; user opens it manually via toggle

  const handleDragStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    const startY = e.clientY;
    const startHeight = chatHeight;

    const onMouseMove = (ev: MouseEvent) => {
      const dy = startY - ev.clientY;
      setChatHeight(Math.max(MIN_CHAT_HEIGHT, Math.min(MAX_CHAT_HEIGHT, startHeight + dy)));
    };

    const onMouseUp = () => {
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
    document.body.style.cursor = 'row-resize';
    document.body.style.userSelect = 'none';
  }, [chatHeight]);

  function handleSend() {
    const text = inputValue.trim();
    if (!text || isStreaming) return;

    setInputValue('');
    setChatOpen(true);
    addMessage({ role: 'user', content: text });
    addMessage({ role: 'assistant', content: '' });
    setStreaming(true);

    // Request body shape:
    //   message          — the user's chat text
    //   editor_code      — current Monaco editor content (renamed from `code`
    //                      to disambiguate from `template_code`)
    //   formula_type     — type_name from the Type dropdown
    //   session_id       — for multi-turn chat history
    //   template_code    — TEMPLATE_CODE business key of the picked sample.
    //                      Server looks it up in FF_FORMULA_TEMPLATES and
    //                      fetches FORMULA_TEXT + ADDITIONAL_PROMPT_TEXT
    //                      itself, so we don't ship the CLOBs over the wire.
    const body: Record<string, unknown> = {
      message: text,
      editor_code: code,
      formula_type: formulaType,
    };
    if (sessionId) body.session_id = sessionId;
    if (selectedTemplateCodeKey) body.template_code = selectedTemplateCodeKey;
    setSelectedSampleId(null);
    setSelectedTemplateCodeKey(null);

    const authHeaders: Record<string, string> = {};
    if (current.auth) {
      authHeaders['Authorization'] = `Basic ${btoa(`${current.auth.username}:${current.auth.password}`)}`;
    }

    abortRef.current = streamSSE(
      `${current.baseUrl}${current.apiPrefix}/chat`,
      body,
      (token) => appendToLast(token),
      () => {
        setStreaming(false);
        const lastMsg = useChatStore.getState().messages.slice(-1)[0];
        if (lastMsg?.role === 'assistant') {
          const blocks = extractCodeBlocks(lastMsg.content);
          if (blocks.length > 0) setCode(blocks[blocks.length - 1]);
        }
        if (!useChatStore.getState().sessionId) {
          setSessionId(`session-${Date.now()}`);
        }
      },
      (err) => {
        appendToLast(`\n[Error: ${err.message}]`);
        setStreaming(false);
      },
      (fullText) => {
        replaceLastContent(fullText);
      },
      authHeaders
    );
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  function toggleChat() {
    setChatOpen((prev) => !prev);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* ─── Editor surface ─── */}
      <div style={{ flex: 1, overflow: 'hidden', backgroundColor: '#ffffff' }}>
        <FFEditor height="100%" />
      </div>

      {/* ─── Chat toggle bar ─── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '8px 20px',
          borderTop: '1px solid var(--border-muted)',
          backgroundColor: 'var(--bg-surface)',
          cursor: 'pointer',
          userSelect: 'none',
          flexShrink: 0,
          transition: 'background-color 120ms ease',
        }}
        onClick={toggleChat}
        onMouseEnter={(e) => {
          (e.currentTarget as HTMLElement).style.backgroundColor = 'var(--bg-elevated)';
        }}
        onMouseLeave={(e) => {
          (e.currentTarget as HTMLElement).style.backgroundColor = 'var(--bg-surface)';
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <MessageOutlined style={{ color: 'var(--text-tertiary)', fontSize: 11 }} />
          <Text
            style={{
              fontSize: 10,
              color: 'var(--text-tertiary)',
              fontFamily: 'var(--font-body)',
              fontWeight: 600,
              letterSpacing: '0.14em',
              textTransform: 'uppercase',
            }}
          >
            Conversation
          </Text>
          {messages.length > 0 && (
            <Text
              style={{
                fontSize: 10,
                color: 'var(--text-tertiary)',
                fontFamily: 'var(--font-mono)',
                letterSpacing: '0.04em',
              }}
            >
              {messages.length} {messages.length === 1 ? 'message' : 'messages'}
            </Text>
          )}
        </div>
        {chatOpen ? (
          <DownOutlined style={{ color: 'var(--text-tertiary)', fontSize: 10 }} />
        ) : (
          <UpOutlined style={{ color: 'var(--text-tertiary)', fontSize: 10 }} />
        )}
      </div>

      {/* ─── Chat history (resizable) ─── */}
      {chatOpen && (
        <>
          <div
            onMouseDown={handleDragStart}
            style={{
              height: 5,
              cursor: 'row-resize',
              backgroundColor: 'var(--border-muted)',
              flexShrink: 0,
              position: 'relative',
              zIndex: 10,
            }}
          >
            <div
              style={{
                width: 32,
                height: 2,
                backgroundColor: 'var(--text-tertiary)',
                borderRadius: 1,
                position: 'absolute',
                left: '50%',
                top: '50%',
                transform: 'translate(-50%, -50%)',
                opacity: 0.5,
              }}
            />
          </div>
          <div
            style={{
              height: chatHeight,
              overflowY: 'auto',
              backgroundColor: 'var(--bg-base)',
              padding: '16px 20px',
              flexShrink: 0,
            }}
          >
            {messages.length === 0 ? (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 6,
                  height: '100%',
                  minHeight: 80,
                }}
              >
                <Text
                  style={{
                    color: 'var(--text-secondary)',
                    fontSize: 14,
                    fontFamily: 'var(--font-display)',
                    fontStyle: 'italic',
                  }}
                >
                  No conversation yet
                </Text>
                <Text style={{ color: 'var(--text-tertiary)', fontSize: 11 }}>
                  Type a request below to begin.
                </Text>
              </div>
            ) : (
              messages.map((msg) => <ChatMessage key={msg.id} message={msg} />)
            )}
            <div ref={listEndRef} />
          </div>
        </>
      )}

      {/* ─── Context selectors (formula type + sample) ─── */}
      <div
        style={{
          padding: '12px 20px 10px',
          borderTop: '1px solid var(--border-muted)',
          backgroundColor: 'var(--bg-surface)',
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          flexShrink: 0,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
          <span
            style={{
              fontSize: 9,
              fontWeight: 600,
              letterSpacing: '0.14em',
              textTransform: 'uppercase',
              color: 'var(--text-tertiary)',
              fontFamily: 'var(--font-body)',
            }}
          >
            Type
          </span>
          <Select
            value={formulaType}
            onChange={setFormulaType}
            size="small"
            showSearch
            optionFilterProp="label"
            variant="borderless"
            style={{ width: 220 }}
            options={[...formulaTypes]
              .sort((a, b) => a.display_name.localeCompare(b.display_name))
              .map((ft) => ({
                value: ft.type_name,
                label: ft.display_name,
              }))}
            placeholder="Formula Type"
          />
        </div>

        <div style={{ width: 1, height: 18, backgroundColor: 'var(--border-muted)' }} />

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flex: 1,
            minWidth: 0,
          }}
        >
          <span
            style={{
              fontSize: 9,
              fontWeight: 600,
              letterSpacing: '0.14em',
              textTransform: 'uppercase',
              color: 'var(--text-tertiary)',
              fontFamily: 'var(--font-body)',
              flexShrink: 0,
            }}
          >
            Start with
          </span>
          <Select
            value={selectedSampleId}
            onChange={(val: number | null) => {
              setSelectedSampleId(val ?? null);
              // Switching to a new template (or clearing the selection) wipes
              // any previously generated formula from the editor — the user
              // is about to ask for a fresh generation, and leaving stale code
              // around causes it to be sent back in the next /chat call as the
              // "Current Formula in Editor" reference.
              setCode('');
              if (val != null) {
                const picked = dbTemplates.find((t) => t.template_id === val);
                if (picked) {
                  // Only seed the chat input with the description — the main
                  // Monaco editor is reserved for AI-generated formulas and
                  // should NOT be overwritten with the template source code.
                  setInputValue(picked.description || picked.name || '');
                  // Stash only the short TEMPLATE_CODE business key. The
                  // backend will look up the actual FORMULA_TEXT and
                  // ADDITIONAL_PROMPT_TEXT from the DB on the next /chat call,
                  // so we don't cache the CLOB bodies here.
                  setSelectedTemplateCodeKey(picked.template_code || null);
                } else {
                  setSelectedTemplateCodeKey(null);
                }
              } else {
                // val === null → user cleared the dropdown
                setSelectedTemplateCodeKey(null);
              }
            }}
            size="small"
            variant="borderless"
            style={{ flex: 1, minWidth: 0 }}
            placeholder={
              templatesLoading
                ? 'Loading templates from database…'
                : sampleOptions.length === 0
                ? 'No templates for this formula type'
                : 'A sample template to seed the editor…'
            }
            allowClear
            showSearch
            optionFilterProp="label"
            loading={templatesLoading}
            notFoundContent={
              templatesLoading ? 'Loading…' : 'No templates available'
            }
            options={sampleOptions}
          />
        </div>
      </div>

      {/* ─── Chat input bar ─── */}
      <div
        style={{
          padding: '10px 20px 14px',
          backgroundColor: 'var(--bg-surface)',
          display: 'flex',
          gap: 10,
          alignItems: 'flex-end',
          flexShrink: 0,
          borderTop: '1px solid var(--border-muted)',
        }}
      >
        <Input.TextArea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Describe what you need… (Enter to send · Shift+Enter for newline)"
          autoSize={{ minRows: 2, maxRows: 4 }}
          disabled={isStreaming}
          style={{
            flex: 1,
            fontSize: 13,
            backgroundColor: 'var(--bg-base)',
            borderColor: 'var(--border-muted)',
          }}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={isStreaming}
          disabled={!inputValue.trim()}
          style={{
            flexShrink: 0,
            alignSelf: 'flex-end',
            height: 38,
            width: 38,
            padding: 0,
            backgroundColor: 'var(--accent-amber)',
            borderColor: 'var(--accent-amber)',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: 'var(--shadow-sm)',
          }}
        />
      </div>
    </div>
  );
}
