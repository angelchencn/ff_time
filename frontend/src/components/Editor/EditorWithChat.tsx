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

const { Text } = Typography;

// Quick prompts are now loaded dynamically per formula type from the API

const MIN_CHAT_HEIGHT = 0;
const MAX_CHAT_HEIGHT = 500;
const DEFAULT_CHAT_HEIGHT = 200;

export function EditorWithChat() {
  const { messages, isStreaming, sessionId, addMessage, appendToLast, replaceLastContent, setStreaming, setSessionId } =
    useChatStore();
  const { code, setCode, formulaType, setFormulaType } = useEditorStore();
  const { current } = useServerStore();
  const [inputValue, setInputValue] = useState('');
  const [selectedSample, setSelectedSample] = useState<string | null>(null);
  const { formulaTypes } = useFormulaTypes();
  const [chatOpen, setChatOpen] = useState(false);
  const [chatHeight, setChatHeight] = useState(DEFAULT_CHAT_HEIGHT);
  const listEndRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  const quickPrompts = useMemo(() => {
    const ft = formulaTypes.find((t) => t.type_name === formulaType);
    return ft?.sample_prompts ?? [];
  }, [formulaTypes, formulaType]);

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

    const body: Record<string, unknown> = { message: text, code, formula_type: formulaType };
    if (sessionId) body.session_id = sessionId;
    if (selectedSample) body.selected_sample = selectedSample;
    setSelectedSample(null);

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
      {/* Editor */}
      <div style={{ flex: 1, overflow: 'hidden' }}>
        <FFEditor height="100%" />
      </div>

      {/* Chat toggle bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '4px 12px',
          borderTop: '1px solid var(--border-default)',
          backgroundColor: 'var(--bg-surface)',
          cursor: 'pointer',
          userSelect: 'none',
          flexShrink: 0,
        }}
        onClick={toggleChat}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <MessageOutlined style={{ color: 'var(--text-tertiary)', fontSize: 12 }} />
          <Text style={{ fontSize: 11, color: 'var(--text-tertiary)', fontFamily: 'var(--font-mono)' }}>
            Chat{messages.length > 0 ? ` (${messages.length})` : ''}
          </Text>
        </div>
        {chatOpen
          ? <DownOutlined style={{ color: 'var(--text-tertiary)', fontSize: 10 }} />
          : <UpOutlined style={{ color: 'var(--text-tertiary)', fontSize: 10 }} />
        }
      </div>

      {/* Chat history (resizable) */}
      {chatOpen && (
        <>
          <div
            onMouseDown={handleDragStart}
            style={{
              height: 5,
              cursor: 'row-resize',
              backgroundColor: 'var(--border-default)',
              flexShrink: 0,
              position: 'relative',
              zIndex: 10,
            }}
          >
            <div style={{
              width: 36,
              height: 2,
              backgroundColor: 'var(--text-tertiary)',
              borderRadius: 1,
              position: 'absolute',
              left: '50%',
              top: '50%',
              transform: 'translate(-50%, -50%)',
            }} />
          </div>
          <div
            style={{
              height: chatHeight,
              overflowY: 'auto',
              backgroundColor: 'var(--bg-base)',
              padding: '12px',
              flexShrink: 0,
            }}
          >
            {messages.length === 0 ? (
              <Text style={{ color: 'var(--text-tertiary)', fontSize: 12 }}>
                No messages yet. Type below to start a conversation.
              </Text>
            ) : (
              messages.map((msg) => (
                <ChatMessage key={msg.id} message={msg} />
              ))
            )}
            <div ref={listEndRef} />
          </div>
        </>
      )}

      {/* Formula type + sample selector row */}
      <div
        style={{
          padding: '6px 12px',
          borderTop: '1px solid var(--border-default)',
          backgroundColor: 'var(--bg-surface)',
          display: 'flex',
          gap: 8,
          flexShrink: 0,
        }}
      >
        <Select
          value={formulaType}
          onChange={setFormulaType}
          size="small"
          showSearch
          optionFilterProp="label"
          style={{ width: 260, flexShrink: 0 }}
          options={[...formulaTypes]
            .sort((a, b) => a.display_name.localeCompare(b.display_name))
            .map((ft) => ({
              value: ft.type_name,
              label: ft.display_name,
            }))}
          placeholder="Formula Type"
        />
        <Select
          value={selectedSample}
          onChange={(val: string) => {
            setSelectedSample(val || null);
            if (val) {
              setInputValue(val);
              setCode('');
            }
          }}
          size="small"
          style={{ flex: 1 }}
          placeholder="Select a sample to start..."
          allowClear
          showSearch
          optionFilterProp="label"
          options={quickPrompts.map((p) => ({
            value: p,
            label: p,
          }))}
        />
      </div>

      {/* Chat input bar */}
      <div
        style={{
          padding: '6px 12px 8px',
          backgroundColor: 'var(--bg-surface)',
          display: 'flex',
          gap: 8,
          alignItems: 'flex-end',
          flexShrink: 0,
        }}
      >
        <Input.TextArea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Describe what you need... (Enter to send)"
          autoSize={{ minRows: 2, maxRows: 3 }}
          disabled={isStreaming}
          style={{ flex: 1 }}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={isStreaming}
          disabled={!inputValue.trim()}
          style={{ flexShrink: 0, alignSelf: 'flex-end' }}
        />
      </div>
    </div>
  );
}
