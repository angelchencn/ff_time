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

test('migrates saved VP DEV Agent endpoint to fastFormulaAssistants', async () => {
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
  assert.equal(servers[0].name, 'VP DEV Agent');
  assert.equal(servers[0].apiPrefix, FAST_FORMULA_ASSISTANTS);
  assert.equal(
    JSON.parse(globalThis.localStorage.getItem('ff_servers'))[0].apiPrefix,
    FAST_FORMULA_ASSISTANTS,
  );
});
