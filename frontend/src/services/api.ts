import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8000',
  headers: { 'Content-Type': 'application/json' },
});

export interface Diagnostic {
  line: number;
  column: number;
  message: string;
  severity: 'error' | 'warning' | 'info';
  layer?: string;
}

export interface ValidateResponse {
  valid: boolean;
  diagnostics: Diagnostic[];
}

export interface SimulateResponse {
  status: 'SUCCESS' | 'ERROR';
  output_data: Record<string, string | number>;
  execution_trace: Array<{ line: number; statement: string; result: string }>;
  error: string | null;
}

export interface CompleteSuggestion {
  label: string;
  insertText: string;
  detail?: string;
  documentation?: string;
}

export interface Formula {
  id: string;
  name: string;
  code: string;
  description?: string;
  created_at?: string;
  updated_at?: string;
}

export interface DBI {
  name: string;
  description?: string;
  data_type?: string;
  module?: string;
}

export async function validateCode(code: string): Promise<ValidateResponse> {
  const response = await api.post<ValidateResponse>('/api/validate', { code });
  return response.data;
}

export async function simulateCode(
  code: string,
  inputs: Record<string, string | number>
): Promise<SimulateResponse> {
  const response = await api.post<SimulateResponse>('/api/simulate', { code, input_data: inputs });
  return response.data;
}

export async function completeCode(
  code: string,
  position: { line: number; column: number }
): Promise<CompleteSuggestion[]> {
  const response = await api.post<{ suggestions: CompleteSuggestion[] }>('/api/complete', {
    code,
    position,
  });
  return response.data.suggestions || [];
}

export async function fetchDBIs(): Promise<DBI[]> {
  const response = await api.get<DBI[]>('/api/dbi');
  return response.data;
}

export async function fetchFormulas(): Promise<Formula[]> {
  const response = await api.get<Formula[]>('/api/formulas');
  return response.data;
}

export async function createFormula(
  name: string,
  code: string,
  description?: string
): Promise<Formula> {
  const response = await api.post<Formula>('/api/formulas', { name, code, description });
  return response.data;
}

export async function getFormula(id: string): Promise<Formula> {
  const response = await api.get<Formula>(`/api/formulas/${id}`);
  return response.data;
}

export async function updateFormula(
  id: string,
  updates: Partial<Pick<Formula, 'name' | 'code' | 'description'>>
): Promise<Formula> {
  const response = await api.put<Formula>(`/api/formulas/${id}`, updates);
  return response.data;
}

export async function exportFormula(id: string): Promise<string> {
  const response = await api.get<{ content: string }>(`/api/formulas/${id}/export`);
  return response.data.content;
}

export default api;
