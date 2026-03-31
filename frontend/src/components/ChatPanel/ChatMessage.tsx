import { useMemo } from 'react';
import { Typography } from 'antd';
import type { ChatMessage as ChatMessageType } from '../../stores/chatStore';

const { Text } = Typography;

interface ChatMessageProps {
  message: ChatMessageType;
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
        backgroundColor: '#f8f8f8',
        border: '1px solid #e0e0e0',
        borderRadius: 6,
        padding: '10px 12px',
        margin: '8px 0',
        overflowX: 'auto',
        fontFamily: "'SF Mono', 'Fira Code', 'Consolas', monospace",
        fontSize: 12,
        lineHeight: 1.6,
        color: '#333',
        whiteSpace: 'pre',
        tabSize: 2,
      }}
    >
      {content.trim()}
    </pre>
  );
}

export function ChatMessage({ message }: ChatMessageProps) {
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
          borderRadius: isUser ? '16px 16px 4px 16px' : '16px 16px 16px 4px',
          backgroundColor: isUser ? '#1677ff' : '#f0f0f0',
          color: isUser ? '#fff' : '#1a1a1a',
          wordBreak: 'break-word',
          fontSize: 13,
          lineHeight: 1.6,
        }}
      >
        {parts.map((part, i) =>
          part.type === 'code' ? (
            <CodeBlock key={i} content={part.content} />
          ) : (
            <Text key={i} style={{ color: 'inherit', fontSize: 'inherit', whiteSpace: 'pre-wrap' }}>
              {part.content}
            </Text>
          )
        )}
      </div>
    </div>
  );
}
