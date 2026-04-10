/**
 * Streaming SSE client used by the chat / explain endpoints.
 *
 * Frame shapes the backend may send (one JSON object per `data:` line):
 *   { session_id: "..." }   — first frame from /chat, hand-off of the
 *                             server-allocated chat session id. Tell the
 *                             store via onSessionId so subsequent turns
 *                             can reuse the same id and the server can
 *                             keep accumulating history under it.
 *   { text: "..." }         — token to append to the current assistant
 *                             message
 *   { content: "..." }      — alias for text used by some endpoints
 *   { replace: "..." }      — full-text replacement (e.g. after a
 *                             post-processor like fixDefaultTypes runs)
 *   "[DONE]" or event: done — terminator
 */
export function streamSSE(
  url: string,
  body: Record<string, unknown>,
  onToken: (text: string) => void,
  onDone: () => void,
  onError: (error: Error) => void,
  onReplace?: (fullText: string) => void,
  extraHeaders?: Record<string, string>,
  onSessionId?: (sessionId: string) => void
): AbortController {
  const controller = new AbortController();

  (async () => {
    try {
      const headers: Record<string, string> = { 'Content-Type': 'application/json', ...extraHeaders };
      const response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new Error(`HTTP error: ${response.status}`);
      }

      if (!response.body) {
        throw new Error('No response body');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();

        if (done) {
          onDone();
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';

        for (const line of lines) {
          const trimmed = line.trim();

          if (trimmed === 'event: done') {
            onDone();
            return;
          }

          if (trimmed.startsWith('data:')) {
            const dataStr = trimmed.slice(5).trim();
            if (!dataStr || dataStr === '[DONE]') {
              continue;
            }
            try {
              const parsed = JSON.parse(dataStr) as {
                text?: string;
                content?: string;
                replace?: string;
                session_id?: string;
              };
              if (parsed.session_id && onSessionId) {
                // Server-allocated session id frame — hand it off and
                // continue reading. There's no token to forward here.
                onSessionId(parsed.session_id);
              } else if (parsed.replace && onReplace) {
                onReplace(parsed.replace);
              } else {
                const text = parsed.text ?? parsed.content ?? '';
                if (text) {
                  onToken(text);
                }
              }
            } catch {
              // If it's not JSON, treat as plain text
              onToken(dataStr);
            }
          }
        }
      }
    } catch (err) {
      if ((err as Error).name === 'AbortError') {
        return;
      }
      onError(err instanceof Error ? err : new Error(String(err)));
    }
  })();

  return controller;
}
