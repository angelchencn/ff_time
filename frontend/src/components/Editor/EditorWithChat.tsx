import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Input, Typography } from 'antd';
import { SendOutlined, MessageOutlined, UpOutlined, DownOutlined } from '@ant-design/icons';
import { useChatStore } from '../../stores/chatStore';
import { useEditorStore } from '../../stores/editorStore';
import { streamSSE } from '../../services/sse';
import { extractCodeBlocks } from '../../services/formulaUtils';
import { ChatMessage } from '../ChatPanel/ChatMessage';
import { FFEditor } from './FFEditor';

const { Text } = Typography;

const QUICK_PROMPTS = [
  'Calculate overtime pay (1.5x after 40 hours)',
  'California double overtime (1x / 1.5x / 2x)',
  'Shift differential (night 15%, evening 8%)',
  'Holiday pay with 2x multiplier',
  'Weekly hours cap at 60 with warning flag',
  'Weekend overtime (Sat 1.5x, Sun 2x)',
  'Calculate total pay with regular + overtime + shift premium',
  'Validate hours worked are within legal limits',
];

const MIN_CHAT_HEIGHT = 0;
const MAX_CHAT_HEIGHT = 500;
const DEFAULT_CHAT_HEIGHT = 200;

export function EditorWithChat() {
  const { messages, isStreaming, sessionId, addMessage, appendToLast, setStreaming, setSessionId } =
    useChatStore();
  const { code, setCode } = useEditorStore();
  const [inputValue, setInputValue] = useState('');
  const [chatOpen, setChatOpen] = useState(false);
  const [chatHeight, setChatHeight] = useState(DEFAULT_CHAT_HEIGHT);
  const listEndRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    listEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Auto-open chat on first message
  useEffect(() => {
    if (messages.length > 0 && !chatOpen) setChatOpen(true);
  }, [messages.length, chatOpen]);

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

    const body: Record<string, unknown> = { message: text, code };
    if (sessionId) body.session_id = sessionId;

    abortRef.current = streamSSE(
      '/api/chat',
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
      }
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

      {/* Chat toggle bar — always visible, always clickable */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '4px 12px',
          borderTop: '1px solid #e0e0e0',
          backgroundColor: '#f5f5f5',
          cursor: 'pointer',
          userSelect: 'none',
          flexShrink: 0,
        }}
        onClick={toggleChat}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <MessageOutlined style={{ color: '#888', fontSize: 12 }} />
          <Text style={{ fontSize: 11, color: '#888' }}>
            Chat{messages.length > 0 ? ` (${messages.length} messages)` : ''}
          </Text>
        </div>
        {chatOpen ? <DownOutlined style={{ color: '#888', fontSize: 10 }} /> : <UpOutlined style={{ color: '#888', fontSize: 10 }} />}
      </div>

      {/* Chat history (resizable) */}
      {chatOpen && (
        <>
          {/* Drag handle for resizing */}
          <div
            onMouseDown={handleDragStart}
            style={{
              height: 4,
              cursor: 'row-resize',
              backgroundColor: '#eee',
              flexShrink: 0,
            }}
          />
          <div
            style={{
              height: chatHeight,
              overflowY: 'auto',
              backgroundColor: '#fafafa',
              padding: '8px 12px',
              flexShrink: 0,
            }}
          >
            {messages.length === 0 ? (
              <Text style={{ color: '#bbb', fontSize: 12 }}>
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

      {/* Quick prompts — show when input is empty and no messages yet */}
      {!inputValue && messages.length === 0 && (
        <div
          style={{
            padding: '6px 12px',
            borderTop: '1px solid #e0e0e0',
            backgroundColor: '#fafafa',
            display: 'flex',
            flexWrap: 'wrap',
            gap: 6,
            flexShrink: 0,
          }}
        >
          {QUICK_PROMPTS.map((prompt) => (
            <Button
              key={prompt}
              size="small"
              type="dashed"
              onClick={() => setInputValue(prompt)}
              style={{ fontSize: 11, color: '#555', borderColor: '#d9d9d9' }}
            >
              {prompt}
            </Button>
          ))}
        </div>
      )}

      {/* Chat input bar */}
      <div
        style={{
          padding: '8px 12px',
          borderTop: '1px solid #e0e0e0',
          backgroundColor: '#fff',
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
          autoSize={{ minRows: 1, maxRows: 3 }}
          disabled={isStreaming}
          style={{ flex: 1 }}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={isStreaming}
          disabled={!inputValue.trim()}
          style={{ flexShrink: 0 }}
        />
      </div>
    </div>
  );
}
