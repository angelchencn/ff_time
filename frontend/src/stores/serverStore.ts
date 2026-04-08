import { create } from 'zustand';

export interface ServerConfig {
  name: string;
  baseUrl: string;
  apiPrefix: string;
  auth?: { username: string; password: string };
}

const SERVERS: ServerConfig[] = [
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

interface ServerState {
  servers: ServerConfig[];
  selectedIndex: number;
  current: ServerConfig;
  select: (index: number) => void;
  getApiUrl: (path: string) => string;
}

function loadSavedIndex(): number {
  try {
    const saved = localStorage.getItem('ff_server_index');
    if (saved !== null) {
      const idx = parseInt(saved, 10);
      if (idx >= 0 && idx < SERVERS.length) return idx;
    }
  } catch (e) { /* ignore */ }
  return 0;
}

const initialIndex = loadSavedIndex();

export const useServerStore = create<ServerState>((set, get) => ({
  servers: SERVERS,
  selectedIndex: initialIndex,
  current: SERVERS[initialIndex],
  select: (index: number) => {
    localStorage.setItem('ff_server_index', String(index));
    set({ selectedIndex: index, current: SERVERS[index] });
  },
  getApiUrl: (path: string) => {
    const { current } = get();
    return `${current.baseUrl}${current.apiPrefix}${path}`;
  },
}));
