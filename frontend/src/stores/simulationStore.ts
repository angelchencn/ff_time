import { create } from 'zustand';

export type SimulationStatus = 'idle' | 'running' | 'success' | 'error';

interface SimulationState {
  inputData: Record<string, string>;
  outputData: Record<string, string | number>;
  trace: string[];
  status: SimulationStatus;
  error: string | null;
  activeTab: string;
  setInputData: (inputData: Record<string, string>) => void;
  setInputField: (key: string, value: string) => void;
  setOutputData: (outputData: Record<string, string | number>) => void;
  setTrace: (trace: string[]) => void;
  setStatus: (status: SimulationStatus) => void;
  setError: (error: string | null) => void;
  setActiveTab: (activeTab: string) => void;
  reset: () => void;
}

export const useSimulationStore = create<SimulationState>((set) => ({
  inputData: {},
  outputData: {},
  trace: [],
  status: 'idle',
  error: null,
  activeTab: 'validate',
  setInputData: (inputData) => set({ inputData }),
  setInputField: (key, value) =>
    set((state) => ({ inputData: { ...state.inputData, [key]: value } })),
  setOutputData: (outputData) => set({ outputData }),
  setTrace: (trace) => set({ trace }),
  setStatus: (status) => set({ status }),
  setError: (error) => set({ error }),
  setActiveTab: (activeTab) => set({ activeTab }),
  reset: () =>
    set({ inputData: {}, outputData: {}, trace: [], status: 'idle', error: null }),
}));
