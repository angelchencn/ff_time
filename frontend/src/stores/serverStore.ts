import { create } from 'zustand';

export interface ServerConfig {
  name: string;
  baseUrl: string;
  apiPrefix: string;
  auth?: { username: string; password: string };
}

const DEFAULT_SERVERS: ServerConfig[] = [
  {
    name: 'Agent Studio',
    baseUrl: '/fusion-proxy',
    apiPrefix: '/hcmRestApi/redwood/11.13.18.05/calculationEntries',
    auth: { username: 'tm-mfitzimmons', password: 'Welcome1' },
  },
  {
    name: 'Payroll VP DEV',
    baseUrl: '/fusion-proxy',
    apiPrefix: '/hcmRestApi/redwood/11.13.18.05/calculationEntries',
    auth: { username: 'hcm.user@oracle.com', password: 'Welcome1' },
  },
  {
    name: 'Local Dev (Grizzly)',
    baseUrl: 'http://100.95.220.150:8000',
    apiPrefix: '/api/11.13.18.05/calculationEntries',
  },
];

const STORAGE_KEY = 'ff_servers';
const INDEX_KEY = 'ff_server_index';

function loadServers(): ServerConfig[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      const parsed = JSON.parse(saved) as ServerConfig[];
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch { /* ignore */ }
  return DEFAULT_SERVERS;
}

function saveServers(servers: ServerConfig[]) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(servers)); } catch { /* ignore */ }
}

function loadSavedIndex(maxLen: number): number {
  try {
    const saved = localStorage.getItem(INDEX_KEY);
    if (saved !== null) {
      const idx = parseInt(saved, 10);
      if (idx >= 0 && idx < maxLen) return idx;
    }
  } catch { /* ignore */ }
  return 0;
}

interface ServerState {
  servers: ServerConfig[];
  selectedIndex: number;
  current: ServerConfig;
  select: (index: number) => void;
  addServer: (config: ServerConfig) => void;
  updateServer: (index: number, config: ServerConfig) => void;
  removeServer: (index: number) => void;
  getApiUrl: (path: string) => string;
}

const initialServers = loadServers();
const initialIndex = loadSavedIndex(initialServers.length);

export const useServerStore = create<ServerState>((set, get) => ({
  servers: initialServers,
  selectedIndex: initialIndex,
  current: initialServers[initialIndex],

  select: (index: number) => {
    const { servers } = get();
    if (index >= 0 && index < servers.length) {
      localStorage.setItem(INDEX_KEY, String(index));
      set({ selectedIndex: index, current: servers[index] });
    }
  },

  addServer: (config: ServerConfig) => {
    const { servers } = get();
    const updated = [...servers, config];
    saveServers(updated);
    const newIndex = updated.length - 1;
    localStorage.setItem(INDEX_KEY, String(newIndex));
    set({ servers: updated, selectedIndex: newIndex, current: config });
  },

  updateServer: (index: number, config: ServerConfig) => {
    const { servers, selectedIndex } = get();
    const updated = [...servers];
    updated[index] = config;
    saveServers(updated);
    const newCurrent = index === selectedIndex ? config : servers[selectedIndex];
    set({ servers: updated, current: newCurrent });
  },

  removeServer: (index: number) => {
    const { servers, selectedIndex } = get();
    if (servers.length <= 1) return; // keep at least one
    const updated = servers.filter((_, i) => i !== index);
    saveServers(updated);
    const newIndex = selectedIndex >= updated.length ? updated.length - 1 : selectedIndex;
    localStorage.setItem(INDEX_KEY, String(newIndex));
    set({ servers: updated, selectedIndex: newIndex, current: updated[newIndex] });
  },

  getApiUrl: (path: string) => {
    const { current } = get();
    return `${current.baseUrl}${current.apiPrefix}${path}`;
  },
}));
