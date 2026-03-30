export function streamSSE(
  url: string,
  body: Record<string, unknown>,
  onToken: (text: string) => void,
  onDone: () => void,
  onError: (error: Error) => void
): AbortController {
  const controller = new AbortController();

  (async () => {
    try {
      const response = await fetch(`http://localhost:8000${url}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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
              const parsed = JSON.parse(dataStr) as { text?: string; content?: string };
              const text = parsed.text ?? parsed.content ?? '';
              if (text) {
                onToken(text);
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
