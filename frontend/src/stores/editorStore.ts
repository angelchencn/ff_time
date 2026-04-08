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
  setCode: (code: string) => void;
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
  setCode: (code) => set({ code, isDirty: true }),
  setDiagnostics: (diagnostics) => set({ diagnostics }),
  setIsValid: (isValid) => set({ isValid }),
  setIsDirty: (isDirty) => set({ isDirty }),
  setCurrentFormulaId: (currentFormulaId) => set({ currentFormulaId }),
  setMode: (mode) => set({ mode }),
  setFormulaType: (formulaType) => set({ formulaType }),
}));
