import { create } from 'zustand';

export interface ServerConfig {
  name: string;
  baseUrl: string;
  apiPrefix: string;
  auth?: { username: string; password: string };
  workflowCode?: string;
}

const FAST_FORMULA_ASSISTANTS_PREFIX =
  '/hcmRestApi/redwood/11.13.18.05/fastFormulaAssistants';
const LEGACY_VP_DEV_PREFIX =
  '/hcmRestApi/redwood/11.13.18.05/calculationEntries';
const CNE_AGENT_SERVER: ServerConfig = {
  name: 'VP Dev',
  baseUrl: '/cne-agent-proxy',
  apiPrefix: FAST_FORMULA_ASSISTANTS_PREFIX,
  auth: { username: 'tm-mfitzimmons', password: 'Welcome1' },
};
const SILVER_RESP_SERVER: ServerConfig = {
  name: 'Silver Resp',
  baseUrl: '/silver-resp-proxy',
  apiPrefix: FAST_FORMULA_ASSISTANTS_PREFIX,
  auth: { username: 'tm-mfitzimmons', password: 'Welcome1' },
};
const VP_QA_SERVER: ServerConfig = {
  name: 'VP QA',
  baseUrl: '/fusion-proxy',
  apiPrefix: FAST_FORMULA_ASSISTANTS_PREFIX,
  auth: { username: 'tm-mfitzimmons', password: 'Welcome1' },
};
const COOKIE_CUTTER_SERVER: ServerConfig = {
  name: 'Cookie Cutter',
  baseUrl: '/cookie-cutter-proxy',
  apiPrefix: FAST_FORMULA_ASSISTANTS_PREFIX,
  auth: { username: 'tm-mfitzimmons', password: 'Welcome1' },
};

const DEFAULT_SERVERS: ServerConfig[] = [
  CNE_AGENT_SERVER,
  VP_QA_SERVER,
  COOKIE_CUTTER_SERVER,
  SILVER_RESP_SERVER,
];
const DEFAULT_SERVER_ORDER = new Map(
  DEFAULT_SERVERS.map((server, index) => [server.baseUrl, index]),
);

const STORAGE_KEY = 'ff_servers';
const INDEX_KEY = 'ff_server_index';

function loadServers(): ServerConfig[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      const parsed = JSON.parse(saved) as ServerConfig[];
      if (Array.isArray(parsed) && parsed.length > 0) {
        const migrated = migrateSavedServers(parsed);
        if (migrated.changed) saveServers(migrated.servers);
        return migrated.servers;
      }
    }
  } catch { /* ignore */ }
  return DEFAULT_SERVERS;
}

function saveServers(servers: ServerConfig[]) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(servers)); } catch { /* ignore */ }
}

function migrateSavedServers(servers: ServerConfig[]): { servers: ServerConfig[]; changed: boolean } {
  let changed = false;
  let migrated = servers.map((server) => {
    if (
      server.name === 'VP DEV Agent'
      && server.baseUrl === '/fusion-proxy'
      && (server.apiPrefix === LEGACY_VP_DEV_PREFIX
        || server.apiPrefix === FAST_FORMULA_ASSISTANTS_PREFIX)
    ) {
      changed = true;
      return {
        ...server,
        name: 'VP QA',
        apiPrefix: FAST_FORMULA_ASSISTANTS_PREFIX,
      };
    }
    if (
      server.name === 'CNE Agent'
      && server.baseUrl === CNE_AGENT_SERVER.baseUrl
    ) {
      changed = true;
      return { ...server, name: 'VP Dev' };
    }
    if (
      server.name === 'cookie cutter'
      && server.baseUrl === COOKIE_CUTTER_SERVER.baseUrl
    ) {
      changed = true;
      return { ...server, name: 'Cookie Cutter' };
    }
    return server;
  });

  const hasVpDev = migrated.some((server) => server.baseUrl === CNE_AGENT_SERVER.baseUrl);
  if (!hasVpDev) {
    migrated = [...migrated, CNE_AGENT_SERVER];
    changed = true;
  }
  const hasSilverResp = migrated.some((server) => server.baseUrl === SILVER_RESP_SERVER.baseUrl);
  if (!hasSilverResp) {
    migrated = [...migrated, SILVER_RESP_SERVER];
    changed = true;
  }

  const ordered = [...migrated].sort((first, second) => {
    const firstOrder = DEFAULT_SERVER_ORDER.get(first.baseUrl) ?? Number.MAX_SAFE_INTEGER;
    const secondOrder = DEFAULT_SERVER_ORDER.get(second.baseUrl) ?? Number.MAX_SAFE_INTEGER;
    return firstOrder - secondOrder;
  });
  const orderChanged = ordered.some((server, index) => server !== migrated[index]);
  if (orderChanged) {
    migrated = ordered;
    changed = true;
  }

  return { servers: migrated, changed };
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
