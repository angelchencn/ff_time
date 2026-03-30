import { useEffect } from 'react';
import { useEditorStore } from '../stores/editorStore';

const STORAGE_KEY = 'ff_time_autosave_code';
const INTERVAL_MS = 10_000;

export function useAutoSave(): void {
  const { code, setCode } = useEditorStore();

  // Restore from localStorage on mount
  useEffect(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        setCode(saved);
      }
    } catch {
      // localStorage may be unavailable
    }
  // Only run on mount
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Save every 10 seconds
  useEffect(() => {
    const timer = setInterval(() => {
      try {
        if (code) {
          localStorage.setItem(STORAGE_KEY, code);
        }
      } catch {
        // Ignore storage errors
      }
    }, INTERVAL_MS);

    return () => clearInterval(timer);
  }, [code]);
}
