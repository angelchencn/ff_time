import { create } from 'zustand';
import type { Diagnostic } from '../services/api';

export type EditorMode = 'chat' | 'code';

interface EditorState {
  code: string;
  diagnostics: Diagnostic[];
  isValid: boolean;
  isDirty: boolean;
  currentFormulaId: string | null;
  mode: EditorMode;
  formulaType: string;
  /**
   * Update the editor content. By default also flips `isDirty` to `true`
   * so the next chat send knows to ship `editor_code`.
   *
   * Pass `markDirty: false` for *programmatic* writes that should NOT be
   * treated as a manual edit — e.g. auto-extracting a code block from an
   * assistant response, or clearing the buffer when the user picks a new
   * template. Without this, those writes look like user edits and the
   * follow-up turn re-sends the assistant's previous response as
   * `editor_code`, duplicating it against the same content already
   * sitting in `history` as the prior assistant message.
   */
  setCode: (code: string, markDirty?: boolean) => void;
  setDiagnostics: (diagnostics: Diagnostic[]) => void;
  setIsValid: (isValid: boolean) => void;
  setIsDirty: (isDirty: boolean) => void;
  setCurrentFormulaId: (id: string | null) => void;
  setMode: (mode: EditorMode) => void;
  setFormulaType: (formulaType: string) => void;
}

export const useEditorStore = create<EditorState>((set) => ({
  code: '',
  diagnostics: [],
  isValid: true,
  isDirty: false,
  currentFormulaId: null,
  mode: 'chat',
  formulaType: 'Custom',
  setCode: (code, markDirty = true) => set({ code, isDirty: markDirty }),
  setDiagnostics: (diagnostics) => set({ diagnostics }),
  setIsValid: (isValid) => set({ isValid }),
  setIsDirty: (isDirty) => set({ isDirty }),
  setCurrentFormulaId: (currentFormulaId) => set({ currentFormulaId }),
  setMode: (mode) => set({ mode }),
  setFormulaType: (formulaType) => set({ formulaType }),
}));
