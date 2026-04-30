from __future__ import annotations

import atexit
import json
import os
import socket
import subprocess
import time
from pathlib import Path

import pytest
from selenium import webdriver
from selenium.webdriver.chrome.options import Options


ROOT = Path(__file__).resolve().parents[2]
FRONTEND_DIR = ROOT / "frontend"
BASE_URL = os.environ.get("FF_QA_BASE_URL", "http://127.0.0.1:4173")
CHROME_BINARY = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

FORMULA_TYPES = [
    {"type_name": "Custom", "display_name": "Custom Formula"},
    {"type_name": "WFM Time Entry Rules", "display_name": "WFM Time Entry Rules"},
]

EDITOR_TEMPLATES = [
    {
        "template_id": 101,
        "template_code": "ORA_HCM_FF_SHIFT_DIFF_001",
        "name": "Shift Differential",
        "description": "Apply shift differential premium for night and weekend work.",
    },
    {
        "template_id": 102,
        "template_code": "ORA_HCM_FF_HOLIDAY_PAY_001",
        "name": "Holiday Pay",
        "description": "Calculate holiday pay using a configurable multiplier.",
    },
]

MANAGE_TEMPLATES = [
    {
        "template_id": 101,
        "template_code": "ORA_HCM_FF_SHIFT_DIFF_001",
        "name": "Shift Differential",
        "description": "Apply shift differential premium for night and weekend work.",
        "code": "DEFAULT FOR HOURS_WORKED IS 0\nRETURN HOURS_WORKED",
        "rule": "Use the approved differential matrix for night and weekend work.",
        "formula_type_name": None,
        "source_type": "SEEDED",
        "active_flag": "Y",
        "semantic_flag": "N",
        "systemprompt_flag": "N",
        "use_system_prompt_flag": "Y",
        "sort_order": 1,
    },
    {
        "template_id": 102,
        "template_code": "ORA_HCM_FF_HOLIDAY_PAY_001",
        "name": "Holiday Pay",
        "description": "Calculate holiday pay using a configurable multiplier.",
        "code": "DEFAULT FOR HOURS_WORKED IS 0\nRETURN HOURS_WORKED",
        "rule": "Honor local holiday policy configuration.",
        "formula_type_name": None,
        "source_type": "SEEDED",
        "active_flag": "Y",
        "semantic_flag": "N",
        "systemprompt_flag": "N",
        "use_system_prompt_flag": "Y",
        "sort_order": 2,
    },
]


def _wait_for_port(host: str, port: int, timeout: float = 60.0) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.settimeout(1)
            try:
                sock.connect((host, port))
                return
            except OSError:
                time.sleep(0.5)
    raise RuntimeError(f"Timed out waiting for {host}:{port}")


def _start_frontend() -> subprocess.Popen[str]:
    proc = subprocess.Popen(
        ["npm", "run", "dev", "--", "--host", "127.0.0.1", "--port", "4173"],
        cwd=FRONTEND_DIR,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        text=True,
    )
    atexit.register(proc.terminate)
    _wait_for_port("127.0.0.1", 4173)
    return proc


@pytest.fixture(scope="session", autouse=True)
def frontend_server() -> subprocess.Popen[str]:
    return _start_frontend()


def _mock_bootstrap_script() -> str:
    routes = {
        "formulaTypes": FORMULA_TYPES,
        "editorTemplates": EDITOR_TEMPLATES,
        "manageTemplates": MANAGE_TEMPLATES,
    }
    servers = [
        {
            "name": "Local QA",
            "baseUrl": "http://127.0.0.1:8000",
            "apiPrefix": "/api/11.13.18.05/fastFormulaAssistants",
        }
    ]

    return f"""
(() => {{
  const routes = {json.dumps(routes)};
  const servers = {json.dumps(servers)};

  localStorage.setItem('ff_servers', JSON.stringify(servers));
  localStorage.setItem('ff_server_index', '0');

  const originalOpen = XMLHttpRequest.prototype.open;
  const originalSend = XMLHttpRequest.prototype.send;
  const originalSetHeader = XMLHttpRequest.prototype.setRequestHeader;

  function buildResponse(method, url, body) {{
    if (url.includes('/formula-types')) {{
      return {{ status: 200, body: JSON.stringify(routes.formulaTypes) }};
    }}

    if (url.includes('/templates')) {{
      if (method === 'GET') {{
        const data = url.includes('include_inactive=true')
          ? routes.manageTemplates
          : routes.editorTemplates;
        return {{ status: 200, body: JSON.stringify(data) }};
      }}

      if (method === 'POST') {{
        const payload = body ? JSON.parse(body) : {{}};
        return {{
          status: 200,
          body: JSON.stringify({{
            template_id: 999,
            template_code: 'ORA_HCM_FF_NEW_TEMPLATE_999',
            name: payload.name || 'New Template',
            description: payload.description || 'Description',
            code: payload.code || '',
            rule: payload.rule || '',
            formula_type_name: null,
            source_type: 'USER_CREATED',
            active_flag: payload.active_flag || 'Y',
            semantic_flag: payload.semantic_flag || 'N',
            systemprompt_flag: payload.systemprompt_flag || 'N',
            use_system_prompt_flag: payload.use_system_prompt_flag || 'Y',
            sort_order: 3
          }})
        }};
      }}

      if (method === 'PUT') {{
        const payload = body ? JSON.parse(body) : {{}};
        return {{
          status: 200,
          body: JSON.stringify({{
            template_id: 101,
            template_code: 'ORA_HCM_FF_SHIFT_DIFF_001',
            name: payload.name || 'Shift Differential',
            description: payload.description || '',
            code: payload.code || '',
            rule: payload.rule || '',
            formula_type_name: null,
            source_type: 'SEEDED',
            active_flag: payload.active_flag || 'Y',
            semantic_flag: payload.semantic_flag || 'N',
            systemprompt_flag: payload.systemprompt_flag || 'N',
            use_system_prompt_flag: payload.use_system_prompt_flag || 'Y',
            sort_order: 1
          }})
        }};
      }}
    }}

    if (url.includes('/validate')) {{
      const payload = body ? JSON.parse(body) : {{}};
      const code = payload.code || '';
      const isInvalid = code.includes('BAD_TOKEN');
      return {{
        status: 200,
        body: JSON.stringify({{
          valid: !isInvalid,
          diagnostics: isInvalid
            ? [{{
                line: 1,
                column: 1,
                message: 'Unexpected token BAD_TOKEN',
                severity: 'error',
                layer: 'syntax'
              }}]
            : []
        }})
      }};
    }}

    return null;
  }}

  XMLHttpRequest.prototype.open = function(method, url) {{
    this.__qaMethod = method;
    this.__qaUrl = url;
    this.__qaHeaders = {{}};
    return originalOpen.apply(this, arguments);
  }};

  XMLHttpRequest.prototype.setRequestHeader = function(name, value) {{
    this.__qaHeaders[name] = value;
    return originalSetHeader.apply(this, arguments);
  }};

  XMLHttpRequest.prototype.send = function(body) {{
    const response = buildResponse(this.__qaMethod || 'GET', String(this.__qaUrl || ''), body);
    if (!response) {{
      return originalSend.apply(this, arguments);
    }}

    const xhr = this;
    const bodyText = response.body;

    Object.defineProperty(xhr, 'readyState', {{ configurable: true, get: () => 4 }});
    Object.defineProperty(xhr, 'status', {{ configurable: true, get: () => response.status }});
    Object.defineProperty(xhr, 'responseText', {{ configurable: true, get: () => bodyText }});
    Object.defineProperty(xhr, 'response', {{ configurable: true, get: () => bodyText }});

    setTimeout(() => {{
      if (typeof xhr.onreadystatechange === 'function') xhr.onreadystatechange(new Event('readystatechange'));
      if (typeof xhr.onload === 'function') xhr.onload(new Event('load'));
      xhr.dispatchEvent(new Event('readystatechange'));
      xhr.dispatchEvent(new Event('load'));
      xhr.dispatchEvent(new Event('loadend'));
    }}, 0);
  }};
}})();
"""


@pytest.fixture()
def driver(frontend_server: subprocess.Popen[str]) -> webdriver.Chrome:
    options = Options()
    options.binary_location = CHROME_BINARY
    options.add_argument("--headless=new")
    options.add_argument("--window-size=1600,1200")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")

    browser = webdriver.Chrome(options=options)
    browser.execute_cdp_cmd(
        "Page.addScriptToEvaluateOnNewDocument",
        {"source": _mock_bootstrap_script()},
    )
    browser.implicitly_wait(2)

    try:
        yield browser
    finally:
        browser.quit()
