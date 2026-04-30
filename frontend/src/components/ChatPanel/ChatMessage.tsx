import { useMemo } from 'react';
import { Typography } from 'antd';
import type { ChatMessage as ChatMessageType } from '../../stores/chatStore';

const { Text } = Typography;

interface ChatMessageProps {
  message: ChatMessageType;
  isStreamingPlaceholder?: boolean;
}

interface ContentPart {
  type: 'text' | 'code';
  content: string;
  lang?: string;
}

function parseContent(raw: string): ContentPart[] {
  const parts: ContentPart[] = [];
  const regex = /```([^\n]*)\n([\s\S]*?)```/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(raw)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ type: 'text', content: raw.slice(lastIndex, match.index) });
    }
    parts.push({ type: 'code', content: match[2], lang: match[1] || undefined });
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < raw.length) {
    parts.push({ type: 'text', content: raw.slice(lastIndex) });
  }

  return parts;
}

function CodeBlock({ content }: { content: string }) {
  return (
    <pre
      style={{
        backgroundColor: 'var(--bg-inset)',
        border: '1px solid var(--border-default)',
        borderRadius: 'var(--radius-md)',
        padding: '10px 12px',
        margin: '8px 0',
        overflowX: 'auto',
        fontFamily: 'var(--font-mono)',
        fontSize: 12,
        lineHeight: 1.6,
        color: 'var(--text-primary)',
        whiteSpace: 'pre',
        tabSize: 2,
      }}
    >
      {content.trim()}
    </pre>
  );
}

function TypingDots() {
  return (
    <>
      <style>{`
        @keyframes chatTypingDot {
          0%, 60%, 100% { transform: translateY(0); opacity: 0.35; }
          30% { transform: translateY(-3px); opacity: 1; }
        }
        .typing-dot {
          width: 5px;
          height: 5px;
          border-radius: 50%;
          background: var(--text-secondary);
          animation: chatTypingDot 1.1s infinite ease-in-out;
        }
      `}</style>
      <div
        aria-label="Assistant is typing"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          minWidth: 28,
          padding: '4px 0',
        }}
      >
        <span className="typing-dot" />
        <span className="typing-dot" style={{ animationDelay: '120ms' }} />
        <span className="typing-dot" style={{ animationDelay: '240ms' }} />
      </div>
    </>
  );
}

export function ChatMessage({ message, isStreamingPlaceholder = false }: ChatMessageProps) {
  const isUser = message.role === 'user';
  const parts = useMemo(
    () => isUser ? [{ type: 'text' as const, content: message.content }] : parseContent(message.content),
    [isUser, message.content]
  );

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: isUser ? 'flex-end' : 'flex-start',
        marginBottom: 12,
      }}
    >
      <div
        style={{
          maxWidth: isUser ? '80%' : '95%',
          padding: '8px 12px',
          borderRadius: isUser ? '12px 12px 2px 12px' : '12px 12px 12px 2px',
          background: isUser ? 'var(--chat-user-bg)' : 'var(--chat-assistant-bg)',
          border: isUser ? 'none' : '1px solid var(--border-default)',
          color: isUser ? '#ffffff' : 'var(--text-primary)',
          wordBreak: 'break-word',
          fontSize: 13,
          lineHeight: 1.6,
          boxShadow: 'var(--shadow-sm)',
        }}
      >
        {isStreamingPlaceholder ? (
          <TypingDots />
        ) : (
          parts.map((part, i) =>
          part.type === 'code' ? (
            <CodeBlock key={i} content={part.content} />
          ) : (
            <Text key={i} style={{ color: 'inherit', fontSize: 'inherit', whiteSpace: 'pre-wrap' }}>
              {part.content}
            </Text>
          )
          )
        )}
      </div>
    </div>
  );
}
