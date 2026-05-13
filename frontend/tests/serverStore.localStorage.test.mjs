import assert from 'node:assert/strict';
import test from 'node:test';

const FAST_FORMULA_ASSISTANTS =
  '/hcmRestApi/redwood/11.13.18.05/fastFormulaAssistants';
const CALCULATION_ENTRIES =
  '/hcmRestApi/redwood/11.13.18.05/calculationEntries';

function createLocalStorage(initial = {}) {
  const values = new Map(Object.entries(initial));

  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    },
    clear() {
      values.clear();
    },
  };
}

test('migrates saved VP DEV Agent endpoint and name', async () => {
  globalThis.localStorage = createLocalStorage({
    ff_servers: JSON.stringify([
      {
        name: 'VP DEV Agent',
        baseUrl: '/fusion-proxy',
        apiPrefix: CALCULATION_ENTRIES,
        auth: { username: 'tm-mfitzimmons', password: 'Welcome1' },
      },
      {
        name: 'cookie cutter',
        baseUrl: '/cookie-cutter-proxy',
        apiPrefix: FAST_FORMULA_ASSISTANTS,
        auth: { username: 'tm-mfitzimmons', password: 'Welcome1' },
      },
    ]),
  });

  const { useServerStore } = await import(
    `../src/stores/serverStore.ts?case=${Date.now()}`
  );

  const servers = useServerStore.getState().servers;
  assert.deepEqual(
    servers.map((server) => server.name),
    ['VP Dev', 'VP QA', 'Cookie Cutter', 'Silver Resp'],
  );
  const vpQa = servers.find((server) => server.name === 'VP QA');
  assert.ok(vpQa);
  assert.equal(vpQa.apiPrefix, FAST_FORMULA_ASSISTANTS);
  const savedServers = JSON.parse(globalThis.localStorage.getItem('ff_servers'));
  assert.equal(
    savedServers.find((server) => server.baseUrl === '/fusion-proxy').name,
    'VP QA',
  );
  assert.equal(
    savedServers.find((server) => server.baseUrl === '/fusion-proxy').apiPrefix,
    FAST_FORMULA_ASSISTANTS,
  );
});

test('adds VP Dev and Silver Resp to existing saved server list', async () => {
  globalThis.localStorage = createLocalStorage({
    ff_servers: JSON.stringify([
      {
        name: 'VP QA',
        baseUrl: '/fusion-proxy',
        apiPrefix: FAST_FORMULA_ASSISTANTS,
        auth: { username: 'tm-mfitzimmons', password: 'Welcome1' },
      },
      {
        name: 'cookie cutter',
        baseUrl: '/cookie-cutter-proxy',
        apiPrefix: FAST_FORMULA_ASSISTANTS,
        auth: { username: 'tm-mfitzimmons', password: 'Welcome1' },
      },
    ]),
  });

  const { useServerStore } = await import(
    `../src/stores/serverStore.ts?case=${Date.now()}`
  );

  const servers = useServerStore.getState().servers;
  assert.deepEqual(
    servers.map((server) => server.name),
    ['VP Dev', 'VP QA', 'Cookie Cutter', 'Silver Resp'],
  );
  const vpDev = servers.find((server) => server.name === 'VP Dev');
  const silverResp = servers.find((server) => server.name === 'Silver Resp');

  assert.ok(vpDev);
  assert.equal(vpDev.baseUrl, '/cne-agent-proxy');
  assert.equal(vpDev.apiPrefix, FAST_FORMULA_ASSISTANTS);
  assert.deepEqual(vpDev.auth, { username: 'tm-mfitzimmons', password: 'Welcome1' });
  assert.ok(silverResp);
  assert.equal(silverResp.baseUrl, '/silver-resp-proxy');
  assert.equal(silverResp.apiPrefix, FAST_FORMULA_ASSISTANTS);
  assert.deepEqual(silverResp.auth, { username: 'tm-mfitzimmons', password: 'Welcome1' });
  assert.ok(
    JSON.parse(globalThis.localStorage.getItem('ff_servers')).some(
      (server) => server.name === 'VP Dev',
    ),
  );
  assert.ok(
    JSON.parse(globalThis.localStorage.getItem('ff_servers')).some(
      (server) => server.name === 'Silver Resp',
    ),
  );
});
