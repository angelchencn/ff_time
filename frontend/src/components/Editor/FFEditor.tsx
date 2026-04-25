import { useEffect, useRef } from 'react';
import MonacoEditor from '@monaco-editor/react';
import type { OnMount } from '@monaco-editor/react';
import type * as Monaco from 'monaco-editor';
import { useEditorStore } from '../../stores/editorStore';
import { registerFastFormulaLanguage, FF_LANGUAGE_ID } from '../../languages/fast-formula';
import { registerFFCompletionProvider } from '../../languages/ff-completion';
import type { Diagnostic } from '../../services/api';

declare global {
  interface Window {
    __ffTimeQaSetEditorValue?: (value: string) => void;
  }
}

interface FFEditorProps {
  dbiNames?: string[];
  height?: string;
}

function toMonacoSeverity(
  monaco: typeof Monaco,
  severity: Diagnostic['severity']
): Monaco.MarkerSeverity {
  switch (severity) {
    case 'error':
      return monaco.MarkerSeverity.Error;
    case 'warning':
      return monaco.MarkerSeverity.Warning;
    default:
      return monaco.MarkerSeverity.Info;
  }
}

export function FFEditor({ dbiNames = [], height = '100%' }: FFEditorProps) {
  const { code, diagnostics, setCode } = useEditorStore();
  const editorRef = useRef<Monaco.editor.IStandaloneCodeEditor | null>(null);
  const monacoRef = useRef<typeof Monaco | null>(null);
  const completionDisposable = useRef<Monaco.IDisposable | null>(null);

  const handleMount: OnMount = (editor, monaco) => {
    editorRef.current = editor;
    monacoRef.current = monaco;

    registerFastFormulaLanguage(monaco);
    completionDisposable.current = registerFFCompletionProvider(monaco, dbiNames);

    if (import.meta.env.DEV) {
      window.__ffTimeQaSetEditorValue = (value: string) => {
        editor.setValue(value);
      };
    }
  };

  // Update Monaco markers when diagnostics change
  useEffect(() => {
    const editor = editorRef.current;
    const monaco = monacoRef.current;
    if (!editor || !monaco) return;

    const model = editor.getModel();
    if (!model) return;

    const markers: Monaco.editor.IMarkerData[] = diagnostics.map((d) => ({
      severity: toMonacoSeverity(monaco, d.severity),
      message: d.message,
      startLineNumber: d.line,
      startColumn: d.column,
      endLineNumber: d.line,
      endColumn: d.column + 1,
    }));

    monaco.editor.setModelMarkers(model, 'fast-formula', markers);
  }, [diagnostics]);

  // Clean up completion provider on unmount
  useEffect(() => {
    return () => {
      completionDisposable.current?.dispose();
      if (window.__ffTimeQaSetEditorValue) {
        delete window.__ffTimeQaSetEditorValue;
      }
    };
  }, []);

  return (
    <div data-testid="ff-editor" style={{ height }}>
      <MonacoEditor
        height={height}
        language={FF_LANGUAGE_ID}
        value={code}
        onChange={(value) => setCode(value ?? '')}
        onMount={handleMount}
        options={{
          minimap: { enabled: false },
          fontSize: 14,
          lineNumbers: 'on',
          wordWrap: 'on',
          automaticLayout: true,
          scrollBeyondLastLine: false,
          tabSize: 2,
        }}
        theme="light"
      />
    </div>
  );
}
