# Technical Design Document — FF Time

## 1. Overview

**Product Name:** FF Time — AI-Based Fast Formula Generator for Time & Labor

**Purpose:** A web application that enables Oracle HCM consultants to generate, validate, simulate, and manage Fast Formula code using AI (Claude API) with a built-in compiler, interpreter, and knowledge base.

**Target Users:**
- HR/Payroll Business Consultants — generate formulas via natural language
- Oracle HCM Technical Consultants — write/edit code with AI-assisted completion and validation

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────┐
│               React Frontend                     │
│   Monaco Editor + Chat + Simulation Panel        │
│   (TypeScript, Vite, Ant Design, Zustand)        │
└──────────────────┬──────────────────────────────┘
                   │ REST / SSE
                   ▼
┌─────────────────────────────────────────────────┐
│             FastAPI Backend (Python)              │
│  ┌───────────┬───────────┬───────────┬────────┐  │
│  │ Validator  │ Simulator │ AI Service│  RAG   │  │
│  │ (3-layer)  │ (AST      │ (Claude   │Engine  │  │
│  │            │ Interp.)  │  API)     │(Chroma)│  │
│  └─────┬─────┴─────┬─────┴─────┬─────┴───┬────┘  │
│        │           │           │         │        │
│  ┌─────┴───────────┴───┐  ┌───┴───┐  ┌──┴─────┐  │
│  │   Lark Parser       │  │Claude │  │ChromaDB│  │
│  │   (grammar.lark)    │  │  API  │  │Vector  │  │
│  │   → AST → Interp.   │  │       │  │Store   │  │
│  └─────────────────────┘  └───────┘  └────────┘  │
│                                                   │
│  ┌─────────────────────────────────────────────┐  │
│  │  SQLite + SQLAlchemy + Alembic              │  │
│  │  (Formula, ChatSession, SimulationRun, DBI) │  │
│  └─────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 2.2 Component Interaction Flow

```
User types in Chat Input
  → POST /api/chat (SSE streaming)
    → AI Service builds prompt:
        1. Load chat history from DB (ChatSession)
        2. Include current editor code as context
        3. RAG retrieval from ChromaDB (similar formulas)
        4. Assemble system prompt + examples + user request
    → Claude API streams response
    → Frontend extracts code blocks → fills Monaco Editor
    → Validator auto-triggered (300ms debounce)
        → Parser (Lark grammar → AST)
        → Semantic check (undeclared vars, output assignment)
        → Rule check (missing RETURN, business rules)
    → Diagnostics displayed as Monaco markers + Validate tab
```

---

## 3. Backend Design

### 3.1 Technology Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Framework | FastAPI | 0.115.6 | Async REST API |
| Language | Python | 3.13 | Backend runtime |
| ORM | SQLAlchemy | 2.0.36 | Database access |
| Migrations | Alembic | 1.14.1 | Schema versioning |
| Parser | Lark | 1.2.2 | Fast Formula grammar → AST |
| AI | Anthropic SDK | 0.43.0+ | Claude API integration |
| Vector DB | ChromaDB | 0.6.3 | RAG knowledge base |
| Embeddings | sentence-transformers | 3.3.1 | all-MiniLM-L6-v2 model |
| Streaming | sse-starlette | 2.2.1 | Server-Sent Events |
| Rate Limit | slowapi | 0.1.9 | API rate limiting |
| Config | pydantic-settings | 2.7.1 | Environment config |

### 3.2 Module Design

#### 3.2.1 Parser Module (`app/parser/`)

**Purpose:** Parse Oracle Fast Formula source code into an Abstract Syntax Tree (AST).

**Components:**

| File | Responsibility |
|------|---------------|
| `grammar.lark` | Lark EBNF grammar definition for Fast Formula |
| `ast_nodes.py` | Immutable frozen dataclass AST node types |
| `ff_parser.py` | Lark Transformer: parse tree → AST |
| `interpreter.py` | Tree-walking AST interpreter for simulation |

**Grammar Coverage:**

| Construct | Syntax | Status |
|-----------|--------|--------|
| Default declaration | `DEFAULT FOR name IS value` | Supported |
| Single input | `INPUT IS name` | Supported |
| Multiple inputs | `INPUTS ARE name1 (TYPE), name2` | Supported |
| Output | `OUTPUT IS name` | Supported |
| Local variable | `LOCAL name` | Supported |
| Alias | `ALIAS long_name AS short_name` | Supported |
| If/Elsif/Else | `IF...THEN...ELSIF...ELSE...END IF` | Supported |
| While loop | `WHILE...LOOP...END LOOP` | Supported |
| Return (multi) | `RETURN var1, var2` | Supported |
| Was Defaulted | `IF var WAS DEFAULTED` | Supported |
| Like | `name LIKE 'pattern%'` | Supported |
| Block comments | `/* comment */` | Supported |
| Cursor/Fetch | `CURSOR...FETCH` | Not supported |
| Change Contexts | `CHANGE_CONTEXTS(...)` | Not supported |
| Array processing | `A.FIRST`, `A.NEXT` | Not supported |

**AST Node Types:**

```
Program(statements)
├── VariableDecl(kind, var_name, data_type, default_value)
├── Assignment(var_name, value)
├── IfStatement(condition, then_body, else_body)
├── WhileLoop(condition, body)
├── ReturnStatement(value)
└── Expression nodes:
    ├── BinaryOp(op, left, right)
    ├── UnaryOp(op, operand)
    ├── FunctionCall(name, args)
    ├── NumberLiteral(value)
    ├── StringLiteral(value)
    └── VariableRef(name)
```

**Key Design Decisions:**
- All AST nodes are frozen dataclasses (immutable)
- Keywords are case-insensitive via regex terminals with priority `.2`
- `END IF` and `END LOOP` support optional space via `\s*` in regex
- `INPUTS ARE` multi-line support: grammar uses `name_list` rule
- ELSIF chains are converted to nested IfStatement nodes in else_body
- Comments are ignored at grammar level (`%ignore BLOCK_COMMENT`)

#### 3.2.2 Validator Service (`app/services/validator.py`)

**Purpose:** Three-layer validation of Fast Formula code.

| Layer | Check | Severity | Example |
|-------|-------|----------|---------|
| 1. Syntax | Lark parser errors | error | `IF hours > THEN` → missing expression |
| 2. Semantic | Undeclared variable references | error | Using `unknown_var` without DEFAULT/INPUT |
| 2. Semantic | OUTPUT variable never assigned | warning | `OUTPUT IS result` but no `result = ...` |
| 3. Rules | Missing RETURN statement | warning | Formula without RETURN |
| 3. Rules | OT formula missing HOURS_WORKED | warning | Overtime output without DBI reference |

**Output:** `ValidationResult(valid: bool, diagnostics: list[Diagnostic])`

Each `Diagnostic` contains: `line`, `col`, `end_col`, `severity`, `message`, `layer`

#### 3.2.3 Simulator Service (`app/services/simulator.py`)

**Purpose:** Execute Fast Formula code with user-provided test data.

**Interpreter Design:**
- Tree-walking interpreter based on AST from parser
- Variable environment: dict mapping names → values
- `ReturnSignal` exception for RETURN control flow
- `SimulationError` for runtime errors (division by zero, etc.)
- Infinite loop protection: max 10,000 iterations
- Execution trace: records each statement's execution with line, statement, result
- Output filtering: excludes input/declared variables, returns only computed values

**Built-in Functions Supported:**

| Category | Functions |
|----------|----------|
| Numeric | `ABS`, `ROUND`, `GREATEST`, `LEAST`, `TO_NUMBER` |
| String | `TO_CHAR`, `UPPER`, `LOWER`, `LENGTH`, `SUBSTR` |
| Conversion | `TO_NUMBER`, `TO_CHAR` |

**Output:** `SimulationResult(status, output_data, execution_trace, error)`

#### 3.2.4 AI Service (`app/services/ai_service.py`)

**Purpose:** AI-powered formula generation, completion, and explanation via Claude API.

**Model Selection:**

| Use Case | Model | Max Tokens | Latency |
|----------|-------|-----------|---------|
| Chat / Generation | claude-sonnet-4 | 4096 | 2-10s |
| Code Completion | claude-haiku-4.5 | 512 | <1s |
| Explain | claude-sonnet-4 | 4096 | 2-10s |

**Prompt Assembly Flow:**

```
System Prompt (FF syntax rules, keywords, functions)
  + RAG Context (similar formulas from ChromaDB, top-k=3)
  + Current Editor Code (if modifying existing formula)
  + Chat History (from DB, last 10 messages)
  + User Message
```

**Key Design:** When `current_code` is provided, the prompt instructs Claude to modify the existing formula rather than generate from scratch. This enables iterative refinement.

#### 3.2.5 RAG Engine (`app/services/rag_engine.py`)

**Purpose:** Retrieval-Augmented Generation for formula examples.

| Config | Value |
|--------|-------|
| Vector DB | ChromaDB (PersistentClient) |
| Embedding Model | all-MiniLM-L6-v2 (384-dim) |
| Distance Metric | Cosine |
| Collection | `fast_formulas` |
| Retrieval | Top-k=3, min similarity=0.6 |

**Knowledge Base:** 5 sample formulas in `data/samples/`:
- `overtime_pay.ff` — standard overtime
- `shift_differential.ff` — night/evening premiums
- `holiday_pay.ff` — holiday multiplier
- `weekly_hours_cap.ff` — hours cap with warning
- `double_time_overtime.ff` — California-style tiered OT

### 3.3 Database Design

**Engine:** SQLite (MVP) → PostgreSQL (production)

**ORM:** SQLAlchemy 2.0 with Mapped types

```
┌──────────────┐     ┌──────────────────┐
│   Formula     │◄────│  SimulationRun   │
├──────────────┤     ├──────────────────┤
│ id (PK)      │     │ id (PK)          │
│ name         │     │ formula_id (FK)  │
│ description  │     │ input_data (JSON)│
│ formula_type │     │ output_data(JSON)│
│ use_case     │     │ exec_trace(JSON) │
│ code (TEXT)  │     │ status           │
│ version      │     │ created_at       │
│ status       │     └──────────────────┘
│ user_id      │
│ created_at   │     ┌──────────────────┐
│ updated_at   │◄────│  ChatSession     │
└──────────────┘     ├──────────────────┤
                     │ id (PK)          │
┌──────────────┐     │ formula_id (FK)  │
│ DBIRegistry  │     │ messages (JSON)  │
├──────────────┤     │ created_at       │
│ id (PK)      │     └──────────────────┘
│ name (UNIQUE)│
│ data_type    │
│ module       │
│ description  │
│ is_active    │
└──────────────┘
```

### 3.4 API Design

| Method | Endpoint | Request | Response | Transport |
|--------|----------|---------|----------|-----------|
| POST | `/api/validate` | `{code}` | `{valid, diagnostics[]}` | JSON |
| POST | `/api/simulate` | `{code, input_data}` | `{status, output_data, execution_trace, error}` | JSON |
| POST | `/api/chat` | `{session_id?, message, code}` | `{text}` chunks | SSE |
| POST | `/api/complete` | `{code, cursor_line, cursor_col}` | `{suggestions[]}` | JSON |
| POST | `/api/explain` | `{code, selected_range?, action}` | `{text}` chunks | SSE |
| GET | `/api/formulas` | — | `[{id, name, ...}]` | JSON |
| POST | `/api/formulas` | `{name, code, ...}` | `{id, name}` | JSON |
| GET | `/api/formulas/:id` | — | `{id, name, code, ...}` | JSON |
| PUT | `/api/formulas/:id` | `{name?, code?, status?}` | `{id, status}` | JSON |
| POST | `/api/formulas/:id/export` | — | `{filename, content}` | JSON |
| GET | `/api/dbi` | `?module=&search=` | `[{name, data_type, module, description}]` | JSON |
| GET | `/api/health` | — | `{status: "ok"}` | JSON |

**SSE Protocol:** Backend sends `data: {"text": "..."}` (JSON encoded to preserve newlines). Frontend parses JSON and extracts `text` field.

---

## 4. Frontend Design

### 4.1 Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | React | 19.x |
| Language | TypeScript | 5.9 |
| Build Tool | Vite | 5.x |
| UI Library | Ant Design | 6.x |
| State Management | Zustand | 5.x |
| Code Editor | Monaco Editor | 4.x |
| HTTP Client | Axios | 1.x |

### 4.2 Layout

```
┌──────────────────────────────────────────────────┐
│  [New] [Save] [Export]                            │  ← Toolbar
├──────────────────────────────┬───────────────────┤
│                              │  Validate         │
│  Monaco Editor               │  Simulate         │
│  (Fast Formula code)         │  DBIs             │
│                              │  Explain          │
├──────────────────────────────┤                   │
│  💬 Chat History (collapse)  │                   │
├──────────────────────────────┤                   │
│  [Chat input] [Send]         │                   │
├──────────────────────────────┴───────────────────┤
│  ✓ Syntax OK                         32 lines    │  ← StatusBar
└──────────────────────────────────────────────────┘
```

- Two-column layout with draggable divider
- Left: Monaco Editor + collapsible chat history + chat input
- Right: Tabbed panel (Validate / Simulate / DBIs / Explain)

### 4.3 State Management (Zustand)

| Store | State | Purpose |
|-------|-------|---------|
| `editorStore` | code, diagnostics, isValid, isDirty, currentFormulaId | Editor state + validation results |
| `chatStore` | sessionId, messages[], isStreaming | Chat conversation state |
| `simulationStore` | inputData, outputData, trace, status, error | Simulation inputs/outputs |

### 4.4 Monaco Editor Integration

**Custom Language:** `fast-formula`

| Feature | Implementation |
|---------|---------------|
| Syntax Highlighting | Monarch tokenizer (28 keywords, 3 types, 60+ functions) |
| Auto-completion | CompletionItemProvider (keywords, functions, DBI names) |
| Error Markers | Diagnostics from `/api/validate` → Monaco `setModelMarkers` |
| Code Folding | Regex markers for IF/END IF, WHILE/END LOOP |
| Theme | Light theme |

### 4.5 Chat Integration

- Chat input at bottom of editor panel
- Chat history collapsible between editor and input
- SSE streaming: tokens arrive in real-time
- Code extraction: regex matches ` ```...``` ` blocks in AI response → fills editor
- Fallback: if no fenced blocks, detect FF keywords in response
- Context: current editor code sent with each chat request for iterative modification

---

## 5. Data Flow Diagrams

### 5.1 Formula Generation Flow

```
User: "Write overtime formula"
  │
  ▼
Frontend: POST /api/chat {message, code, session_id}
  │
  ▼
Backend: Load ChatSession history from SQLite
  │
  ▼
AI Service: RAG query ChromaDB → top 3 similar formulas
  │
  ▼
AI Service: Assemble prompt (system + RAG + code context + history + message)
  │
  ▼
Claude API: Stream response tokens
  │
  ▼
Backend: yield SSE events {data: {"text": "..."}}
  │
  ▼
Frontend: Append tokens to chat message
  │
  ▼
Frontend: On stream done → extract code blocks → set editor code
  │
  ▼
Frontend: useValidation hook triggers (300ms debounce)
  │
  ▼
Backend: POST /api/validate → Parser → Semantic → Rules
  │
  ▼
Frontend: Update Monaco markers + Validate tab
```

### 5.2 Simulation Flow

```
User: Fills input form → clicks "Run Simulation"
  │
  ▼
Frontend: POST /api/simulate {code, input_data}
  │
  ▼
Backend: parse_formula(code) → AST
  │
  ▼
Backend: Interpreter(input_data).run(AST)
  │
  ▼
Backend: Execute statements, build trace, catch errors
  │
  ▼
Frontend: Display output_data + execution_trace
```

---

## 6. Security Design

| Concern | Measure |
|---------|---------|
| API Key | Stored in backend `.env`, never exposed to frontend |
| Input Validation | All API inputs validated with Pydantic schemas |
| Code Injection | Simulator uses AST interpretation, never `eval()` |
| Rate Limiting | slowapi middleware on AI endpoints (10/min) |
| CORS | Configured for frontend origin only |
| Data Isolation | MVP is single-user; auth deferred to post-MVP |

---

## 7. DBI Registry

65 database items across 3 modules, stored in `data/dbi_registry/time_labor_dbis.json`:

| Module | Prefix | Count | Examples |
|--------|--------|-------|----------|
| TIME_LABOR | `HWM_PPM_TM_` | 7 | `HWM_PPM_TM_MEASURE`, `HWM_PPM_TM_START_TIME` |
| TIME_LABOR | `HWM_EMP_SCHD_` | 5 | `HWM_EMP_SCHD_SHIFT_NAME`, `HWM_EMP_SCHD_MEASURE` |
| TIME_LABOR | `HWM_CTX_` | 4 | `HWM_CTX_PERIOD_START_DATE`, `HWM_CTX_PERIOD_END_DATE` |
| TIME_LABOR | (core) | 23 | `HOURS_WORKED`, `OVERTIME_RATE`, `SHIFT_TYPE`, `HOLIDAY_FLAG` |
| PERSON | `PER_ASG_` | 13 | `PER_ASG_NORMAL_HOURS`, `PER_ASG_STATUS`, `PER_ASG_GRADE_ID` |
| PERSON | `PER_EMP_` | 8 | `PER_EMP_HIRE_DATE`, `PER_EMP_FULL_NAME` |
| PAYROLL | `PAY_` | 5 | `PAY_EARN_PERIOD_START`, `PAY_PERIODS_PER_YEAR` |

Source: Oracle FastFormula User Guide + MOS Doc ID 1990057.1

---

## 8. Testing Strategy

### 8.1 Backend Tests

| Test File | Coverage |
|-----------|----------|
| `test_health.py` | Health endpoint |
| `test_models.py` | All 4 SQLAlchemy models (CRUD, relationships) |
| `test_parser.py` | Simple formula, IF/ELSE, syntax errors |
| `test_validator.py` | Valid formula, undeclared vars, syntax errors, output warnings |
| `test_simulator.py` | Arithmetic, IF branching, execution trace, division by zero |
| `test_ai_service.py` | Prompt building (system, generation with RAG, completion) |
| `test_api.py` | Validate, simulate, DBI endpoints |
| `test_rag_engine.py` | Add/query formulas, empty collection |

**Total:** 27 tests, all passing

### 8.2 Test Commands

```bash
# All tests
python -m pytest tests/ -v

# Single file
python -m pytest tests/test_parser.py -v

# Single test
python -m pytest tests/test_parser.py::test_parse_if_else -v

# Skip slow RAG tests
python -m pytest tests/ --ignore=tests/test_rag_engine.py -v
```

---

## 9. Deployment

### 9.1 Development Setup

```bash
# Backend
cd backend
cp .env.example .env          # Add ANTHROPIC_API_KEY
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
alembic upgrade head
python -m app.scripts.seed_knowledge_base
uvicorn app.main:app --reload --port 8000

# Frontend
cd frontend
npm install
npm run dev                    # http://localhost:5173
```

### 9.2 Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `ANTHROPIC_API_KEY` | Yes | — | Claude API key |
| `DATABASE_URL` | No | `sqlite:///./ff_time.db` | Database connection |
| `CHROMA_PERSIST_DIR` | No | `./chroma_data` | ChromaDB storage path |
| `CORS_ORIGINS` | No | `http://localhost:5173` | Allowed CORS origins |

---

## 10. Known Limitations

1. **Single-user only** — No authentication/authorization in MVP
2. **Grammar coverage** — CURSOR/FETCH, CHANGE_CONTEXTS, array processing not supported
3. **Simulator fidelity** — Cannot 100% reproduce Oracle runtime behavior (edge cases in built-in functions, database interactions)
4. **No type inference** — Validator does not track variable types through expressions
5. **DBI registry is static** — Not connected to live Oracle instance; manually maintained JSON file
6. **posthog version pinned** — ChromaDB requires `posthog<4` due to API incompatibility
