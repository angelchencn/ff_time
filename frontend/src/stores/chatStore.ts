import { create } from 'zustand';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
}

interface ChatState {
  sessionId: string | null;
  messages: ChatMessage[];
  isStreaming: boolean;
  setSessionId: (id: string | null) => void;
  addMessage: (message: Omit<ChatMessage, 'id' | 'timestamp'>) => string;
  appendToLast: (text: string) => void;
  setStreaming: (isStreaming: boolean) => void;
  clearMessages: () => void;
}

function generateId(): string {
  return `msg-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

export const useChatStore = create<ChatState>((set) => ({
  sessionId: null,
  messages: [],
  isStreaming: false,
  setSessionId: (sessionId) => set({ sessionId }),
  addMessage: (message) => {
    const id = generateId();
    const fullMessage: ChatMessage = {
      ...message,
      id,
      timestamp: Date.now(),
    };
    set((state) => ({ messages: [...state.messages, fullMessage] }));
    return id;
  },
  appendToLast: (text) =>
    set((state) => {
      if (state.messages.length === 0) return state;
      const updated = state.messages.map((msg, idx) => {
        if (idx === state.messages.length - 1) {
          return { ...msg, content: msg.content + text };
        }
        return msg;
      });
      return { messages: updated };
    }),
  setStreaming: (isStreaming) => set({ isStreaming }),
  clearMessages: () => set({ messages: [], sessionId: null }),
}));
