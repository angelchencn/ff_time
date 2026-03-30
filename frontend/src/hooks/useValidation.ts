import { useEffect, useRef } from 'react';
import { useEditorStore } from '../stores/editorStore';
import { validateCode } from '../services/api';

const DEBOUNCE_MS = 300;

export function useValidation(): void {
  const { code, setDiagnostics, setIsValid } = useEditorStore();
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }

    timerRef.current = setTimeout(async () => {
      if (!code.trim()) {
        setDiagnostics([]);
        setIsValid(true);
        return;
      }

      try {
        const result = await validateCode(code);
        setDiagnostics(result.diagnostics);
        setIsValid(result.valid);
      } catch {
        // Backend may not be running; leave diagnostics as-is
      }
    }, DEBOUNCE_MS);

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, [code, setDiagnostics, setIsValid]);
}
