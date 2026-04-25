# QA Automation

UI smoke tests live under `qa/` so automation stays separate from application code.

## Stack

- `pytest`
- `selenium`
- local Chrome in headless mode

The suite starts the Vite frontend locally and injects browser-side API mocks before the app boots. That keeps the smoke tests deterministic and avoids any dependency on a running backend.

## Commands

```bash
cd qa
./run_smoke.sh
```

Or manually:

```bash
cd qa
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pytest tests
```

## Coverage

- `tests/test_editor_shell.py`
  - main editor shell loads
  - template management route is reachable
  - validation panel reflects invalid code
- `tests/test_templates_management.py`
  - new draft template flow
  - edit-and-save metadata flow
