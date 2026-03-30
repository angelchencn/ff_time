import { useEffect, useRef, useState } from 'react';
import { Button, Input, Space, Typography } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { useChatStore } from '../../stores/chatStore';
import { useEditorStore } from '../../stores/editorStore';
import { streamSSE } from '../../services/sse';
import { ChatMessage } from './ChatMessage';

const { Text } = Typography;

function extractCodeBlocks(text: string): string[] {
  const regex = /```(?:fast-formula|ff|oracle)?\n([\s\S]*?)```/g;
  const blocks: string[] = [];
  let match: RegExpExecArray | null;
  while ((match = regex.exec(text)) !== null) {
    blocks.push(match[1].trim());
  }
  return blocks;
}

export function ChatPanel() {
  const { messages, isStreaming, sessionId, addMessage, appendToLast, setStreaming, setSessionId } =
    useChatStore();
  const { code, setCode } = useEditorStore();
  const [inputValue, setInputValue] = useState('');
  const listEndRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    listEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  function handleSend() {
    const text = inputValue.trim();
    if (!text || isStreaming) return;

    setInputValue('');
    addMessage({ role: 'user', content: text });
    addMessage({ role: 'assistant', content: '' });
    setStreaming(true);

    const body: Record<string, unknown> = {
      message: text,
      code,
    };
    if (sessionId) {
      body.session_id = sessionId;
    }

    abortRef.current = streamSSE(
      '/api/chat',
      body,
      (token) => {
        appendToLast(token);
      },
      () => {
        setStreaming(false);
        // Extract the last assistant message's content
        const lastMsg = useChatStore.getState().messages.slice(-1)[0];
        if (lastMsg?.role === 'assistant') {
          const blocks = extractCodeBlocks(lastMsg.content);
          if (blocks.length > 0) {
            setCode(blocks[blocks.length - 1]);
          }
        }
        // Generate a simple session id if we don't have one
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

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: '#1a1a1a',
      }}
    >
      {/* Message list */}
      <div
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '16px',
        }}
      >
        {messages.length === 0 ? (
          <div
            style={{
              display: 'flex',
              height: '100%',
              alignItems: 'center',
              justifyContent: 'center',
              textAlign: 'center',
              padding: 24,
            }}
          >
            <Text style={{ color: '#666', fontSize: 13 }}>
              Ask me to generate a Fast Formula.
              <br />
              For example: &ldquo;Write a formula to calculate overtime pay&rdquo;
            </Text>
          </div>
        ) : (
          messages.map((msg) => <ChatMessage key={msg.id} message={msg} />)
        )}
        <div ref={listEndRef} />
      </div>

      {/* Input area */}
      <div style={{ padding: '12px 16px', borderTop: '1px solid #333' }}>
        <Space.Compact style={{ width: '100%' }}>
          <Input.TextArea
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Ask about Fast Formulas... (Enter to send, Shift+Enter for newline)"
            autoSize={{ minRows: 1, maxRows: 4 }}
            disabled={isStreaming}
            style={{ backgroundColor: '#2a2a2a', borderColor: '#444', color: '#e0e0e0' }}
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={handleSend}
            loading={isStreaming}
            disabled={!inputValue.trim()}
            style={{ height: 'auto', alignSelf: 'flex-end' }}
          />
        </Space.Compact>
      </div>
    </div>
  );
}
