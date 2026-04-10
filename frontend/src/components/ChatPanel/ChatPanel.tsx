import { useEffect, useRef, useState } from 'react';
import { Button, Input, Select, Space, Typography } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { useChatStore } from '../../stores/chatStore';
import { useEditorStore } from '../../stores/editorStore';
import { useServerStore } from '../../stores/serverStore';
import { streamSSE } from '../../services/sse';
import { ChatMessage } from './ChatMessage';
import { useFormulaTypes } from '../../hooks/useFormulaTypes';

const { Text } = Typography;

const STRING_KEYWORDS = new Set([
  'NAME', 'TEXT', 'DESC', 'CODE', 'TYPE', 'STATUS', 'FLAG', 'MESSAGE',
  'MSG', 'LABEL', 'CATEGORY', 'TITLE', 'MODE', 'REASON', 'COMMENT',
  'NOTE', 'KEY', 'TAG', 'LEVEL', 'CLASS', 'GROUP', 'ROLE', 'UNIT',
  'TASK', 'PROCESS', 'ACTION', 'METHOD', 'FORMAT', 'PATTERN',
  'PREFIX', 'SUFFIX', 'STRING', 'CHAR', 'CURRENCY',
]);

const DATE_KEYWORDS = new Set([
  'DATE', 'START', 'END', 'EFFECTIVE', 'EXPIRY', 'HIRE',
  'TERMINATION', 'BIRTH',
]);

function fixDefaultTypes(code: string): string {
  return code.split('\n').map((line) => {
    const m = line.match(/^(\s*DEFAULT\s+FOR\s+)(\w+)(\s+IS\s+)(0(?:\.0+)?\s*)$/i);
    if (!m) return line;
    const [, prefix, varName, isPart] = m;
    const parts = varName.toUpperCase().split('_');
    if (parts.some((p) => STRING_KEYWORDS.has(p))) {
      return `${prefix}${varName}${isPart}' '`;
    }
    if (parts.some((p) => DATE_KEYWORDS.has(p))) {
      return `${prefix}${varName}${isPart}'01-JAN-0001'(DATE)`;
    }
    return line;
  }).join('\n');
}

function extractCodeBlocks(text: string): string[] {
  // Match any fenced code block: ```lang\n...\n``` or ```\n...\n```
  const regex = /```[^\n]*\n([\s\S]*?)```/g;
  const blocks: string[] = [];
  let match: RegExpExecArray | null;
  while ((match = regex.exec(text)) !== null) {
    const code = match[1].trim();
    if (code) blocks.push(code);
  }
  // If no fenced blocks found, check if the entire response looks like FF code
  if (blocks.length === 0) {
    const ffKeywords = /\b(DEFAULT\s+FOR|INPUT\s+IS|OUTPUT\s+IS|RETURN)\b/i;
    if (ffKeywords.test(text)) {
      blocks.push(text.trim());
    }
  }
  return blocks;
}

export function ChatPanel() {
  const { messages, isStreaming, sessionId, addMessage, appendToLast, replaceLastContent, setStreaming, setSessionId } =
    useChatStore();
  const { code, setCode, formulaType, setFormulaType } = useEditorStore();
  const { current } = useServerStore();
  const [inputValue, setInputValue] = useState('');
  const { formulaTypes } = useFormulaTypes();
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
      formula_type: formulaType,
    };
    if (sessionId) {
      body.session_id = sessionId;
    }

    const authHeaders: Record<string, string> = {};
    if (current.auth) {
      authHeaders['Authorization'] = `Basic ${btoa(`${current.auth.username}:${current.auth.password}`)}`;
    }

    abortRef.current = streamSSE(
      `${current.baseUrl}${current.apiPrefix}/chat`,
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
            // Programmatic write — keep isDirty=false (see EditorWithChat
            // for the full reasoning).
            setCode(fixDefaultTypes(blocks[blocks.length - 1]), false);
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
      },
      (fullText) => {
        // Backend sent a corrected version (e.g. fixed DEFAULT types)
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

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: 'var(--bg-base)',
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
            <Text style={{ color: 'var(--text-tertiary)', fontSize: 13 }}>
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
      <div style={{ padding: '12px 16px', borderTop: '1px solid var(--border-default)' }}>
        <div style={{ marginBottom: 8 }}>
          <Select
            value={formulaType}
            onChange={setFormulaType}
            style={{ width: '100%' }}
            size="small"
            options={formulaTypes.map((ft) => ({
              value: ft.type_name,
              label: ft.display_name,
            }))}
            placeholder="Select Formula Type"
          />
        </div>
        <Space.Compact style={{ width: '100%' }}>
          <Input.TextArea
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Ask about Fast Formulas... (Enter to send, Shift+Enter for newline)"
            autoSize={{ minRows: 1, maxRows: 4 }}
            disabled={isStreaming}
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-default)', color: 'var(--text-primary)' }}
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
