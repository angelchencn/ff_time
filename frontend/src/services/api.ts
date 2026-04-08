import axios from 'axios';
import { useServerStore } from '../stores/serverStore';

function getApi() {
  const { current } = useServerStore.getState();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (current.auth) {
    const token = btoa(`${current.auth.username}:${current.auth.password}`);
    headers['Authorization'] = `Basic ${token}`;
  }
  return axios.create({
    baseURL: current.baseUrl,
    headers,
  });
}

function prefix(path: string): string {
  const { current } = useServerStore.getState();
  return `${current.apiPrefix}${path}`;
}

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
  const response = await getApi().post<ValidateResponse>(prefix('/validate'), { code });
  return response.data;
}

export async function simulateCode(
  code: string,
  inputs: Record<string, string | number>
): Promise<SimulateResponse> {
  const response = await getApi().post<SimulateResponse>(prefix('/simulate'), { code, input_data: inputs });
  return response.data;
}

export async function completeCode(
  code: string,
  position: { line: number; column: number }
): Promise<CompleteSuggestion[]> {
  const response = await getApi().post<{ suggestions: CompleteSuggestion[] }>(prefix('/complete'), {
    code,
    position,
  });
  return response.data.suggestions || [];
}

export async function fetchDBIs(): Promise<DBI[]> {
  const response = await getApi().get<DBI[]>(prefix('/dbi'));
  return response.data;
}

export async function fetchFormulas(): Promise<Formula[]> {
  const response = await getApi().get<Formula[]>(prefix('/formulas'));
  return response.data;
}

export async function createFormula(
  name: string,
  code: string,
  description?: string
): Promise<Formula> {
  const response = await getApi().post<Formula>(prefix('/formulas'), { name, code, description });
  return response.data;
}

export async function getFormula(id: string): Promise<Formula> {
  const response = await getApi().get<Formula>(prefix(`/formulas/${id}`));
  return response.data;
}

export async function updateFormula(
  id: string,
  updates: Partial<Pick<Formula, 'name' | 'code' | 'description'>>
): Promise<Formula> {
  const response = await getApi().put<Formula>(prefix(`/formulas/${id}`), updates);
  return response.data;
}

export async function exportFormula(id: string): Promise<string> {
  const response = await getApi().get<{ content: string }>(prefix(`/formulas/${id}/export`));
  return response.data.content;
}

export default getApi;
