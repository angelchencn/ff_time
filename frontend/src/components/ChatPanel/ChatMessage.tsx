import { Typography } from 'antd';
import type { ChatMessage as ChatMessageType } from '../../stores/chatStore';

const { Text } = Typography;

interface ChatMessageProps {
  message: ChatMessageType;
}

export function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.role === 'user';

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
          maxWidth: '80%',
          padding: '8px 12px',
          borderRadius: isUser ? '16px 16px 4px 16px' : '16px 16px 16px 4px',
          backgroundColor: isUser ? '#1677ff' : '#2a2a2a',
          color: isUser ? '#fff' : '#e0e0e0',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
          fontSize: 13,
          lineHeight: 1.5,
        }}
      >
        <Text style={{ color: 'inherit', fontSize: 'inherit' }}>{message.content}</Text>
      </div>
    </div>
  );
}
