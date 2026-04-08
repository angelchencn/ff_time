# Technical Design Document — FF Time

## 1. Executive Summary

FF Time is an AI-powered Oracle HCM Fast Formula generator that enables Payroll and Time & Labor consultants to create, validate, simulate, and explain Fast Formula code through a web-based IDE with integrated AI chat. The system leverages Retrieval-Augmented Generation (RAG) with ~39,000 real Oracle formulas and Claude LLM to produce production-grade formula code.

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        Browser (Client)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ Monaco Editor │  │  AI Chat     │  │  Simulation Panel      │ │
│  │ (FF Language) │  │  (SSE Stream)│  │  Validate│Simulate│DBI │ │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬─────────────┘ │
│         │                 │                      │               │
│         └─────────────────┼──────────────────────┘               │
│                           │  HTTP/SSE                            │
└───────────────────────────┼──────────────────────────────────────┘
                            │
┌───────────────────────────┼──────────────────────────────────────┐
│                    FastAPI Backend (port 8000)                    │
│                                                                  │
│  ┌─────────────────────── API Layer ──────────────────────────┐  │
│  │ /api/validate  /api/simulate  /api/chat  /api/complete     │  │
│  │ /api/explain   /api/formulas  /api/dbi   /api/formula-types│  │
│  └───────┬────────────┬──────────────┬────────────┬───────────┘  │
│          │            │              │            │               │
│  ┌───────▼──┐  ┌──────▼─────┐  ┌────▼────┐  ┌───▼───────────┐  │
│  │ Validator │  │ Simulator  │  │   AI    │  │   Formula     │  │
│  │ Service   │  │ Service    │  │ Service │  │   Templates   │  │
│  └───────┬──┘  └──────┬─────┘  └────┬────┘  └───────────────┘  │
│          │            │              │                            │
│  ┌───────▼────────────▼──┐    ┌──────▼──────────┐               │
│  │   Lark Parser         │    │   RAG Engine    │               │
│  │   grammar.lark → AST  │    │   (ChromaDB)    │               │
│  │   ff_parser.py        │    │   38,951 formulas│              │
│  │   interpreter.py      │    │   MiniLM-L6-v2  │               │
│  └───────────────────────┘    └──────┬──────────┘               │
│                                      │                           │
│                               ┌──────▼──────────┐               │
│                               │  Claude API     │               │
│                               │  (Sonnet 4)     │               │
│                               └─────────────────┘               │
│                                                                  │
│  ┌────────────── Data Layer ──────────────────────────────────┐  │
│  │ SQLite (formulas, chat sessions, simulation runs)          │  │
│  │ ChromaDB (formula embeddings, vector index)                │  │
│  │ JSON files (DBI registry, formula types, templates)        │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 Technology Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Frontend | React | 19.2 | UI framework |
| UI Library | Ant Design | 6.3 | Component library |
| Code Editor | Monaco Editor | 4.7 | Fast Formula IDE |
| State Management | Zustand | 5.0 | Lightweight stores |
| Backend | FastAPI | 0.115 | REST API + SSE streaming |
| Parser | Lark | 1.2 | Earley parser for FF grammar |
| LLM | Claude Sonnet 4 | — | Formula generation, explanation |
| LLM (fast) | Claude Haiku 4.5 | — | Code completion |
| Vector DB | ChromaDB | 0.6 | RAG retrieval |
| Embeddings | all-MiniLM-L6-v2 | — | 384-dim sentence embeddings |
| Database | SQLite | — | Formulas, chat sessions |
| Oracle Driver | oracledb | 3.4 | Thin-mode Oracle DB access |

---

## 3. Data Architecture

### 3.1 Data Sources

```
┌─────────────────────────────────────────────────────────────┐
│                   Oracle Database                            │
│  FF_FORMULAS_VL        — 38,951 formulas (source code)      │
│  FF_FORMULA_TYPES      — 123 formula types                  │
│  FF_DATABASE_ITEMS_VL  — 1.76M database items               │
│  FF_FDI_USAGES         — 1.15M DBI-to-formula mappings      │
└─────────────────────┬───────────────────────────────────────┘
                      │ import_from_oracle.py
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                   Local Data Files                            │
│  data/chroma/                    — ChromaDB vector store     │
│  data/formula_type_templates.json — 123 type skeletons       │
│  data/formula_types_registry.json — Types + sample prompts   │
│  data/dbi_registry/all_formula_dbis.json — 41,937 DBIs       │
│  data/samples/*.ff               — 6 curated samples         │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 Database Schema (SQLite)

```sql
-- User-saved formulas
CREATE TABLE formulas (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    code        TEXT NOT NULL,
    formula_type TEXT DEFAULT 'TIME_LABOR',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Chat conversation history
CREATE TABLE chat_sessions (
    id          TEXT PRIMARY KEY,
    formula_id  TEXT REFERENCES formulas(id),
    messages    JSON NOT NULL DEFAULT '[]',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Simulation run history
CREATE TABLE simulation_runs (
    id          TEXT PRIMARY KEY,
    formula_id  TEXT REFERENCES formulas(id),
    input_data  JSON NOT NULL,
    output_data JSON,
    status      TEXT NOT NULL,
    error       TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3.3 ChromaDB Vector Store

| Property | Value |
|----------|-------|
| Collection | `fast_formulas` |
| Documents | ~38,957 |
| Embedding Model | `all-MiniLM-L6-v2` (384 dimensions) |
| Distance Metric | Cosine (HNSW index) |
| Query Latency | ~50-100ms |
| Storage | `data/chroma/` (persistent) |

---

## 4. Component Design

### 4.1 Parser (`app/parser/`)

The Fast Formula parser converts source code to an immutable AST using the Lark parsing library with Earley algorithm.

**Files:**
- `grammar.lark` — Lark grammar definition (130+ lines)
- `ff_parser.py` — Transformer: parse tree → AST nodes
- `ast_nodes.py` — 16 frozen dataclass node types
- `interpreter.py` — Tree-walking executor for simulation

**AST Node Hierarchy:**
```
Program
├── VariableDecl (kind: default|input|output|local|alias)
├── Assignment (var_name, value)
├── ArrayAssignment (name, index, value)
├── IfStatement (condition, then_body, else_body)
├── WhileLoop (condition, body)
├── ReturnStatement (value)
├── CallFormulaStatement (formula_name, params)
├── ChangeContextsStatement (assignments)
├── ExecuteStatement (formula_name)
├── BinaryOp (op, left, right)
├── UnaryOp (op, operand)
├── FunctionCall (name, args)
├── MethodCall (object_name, method_name, args)
├── ArrayAccess (name, index)
├── NumberLiteral (value)
├── StringLiteral (value)
└── VariableRef (name)
```

**Supported Grammar Constructs:**

| Category | Constructs |
|----------|------------|
| Declarations | `DEFAULT FOR`, `DEFAULT_DATA_VALUE FOR`, `INPUT IS`, `INPUTS ARE`, `OUTPUT IS`, `OUTPUTS ARE`, `LOCAL`, `ALIAS` |
| Control Flow | `IF/THEN/ELSIF/ELSE/ENDIF`, `WHILE/LOOP/END LOOP`, `RETURN`, empty `RETURN` |
| Expressions | Arithmetic (`+ - * /`), String concat (`\|\|`), Comparison (`= != <> < > <= >=`), Logical (`AND OR NOT`), Pattern (`LIKE NOT LIKE`) |
| Special Ops | `WAS DEFAULTED`, `WAS NOT DEFAULTED`, `IS NULL`, `IS NOT NULL` |
| Assignments | `name = expr`, `"QUOTED" = expr`, `arr[idx] = expr` |
| Functions | `FUNC(args)`, `obj.METHOD(args)`, `arr[idx]`, bare `FUNC(args)` as statement |
| Statements | `CALL_FORMULA('name', in > 'p', out < 'p' DEFAULT val)`, `CHANGE_CONTEXTS(N=V)`, `EXECUTE('name')` |
| Literals | Numbers, single-quoted strings (`'O''Brien'`), typed strings (`'2024-01-01'(DATE)`), quoted identifiers (`"AREA1"`) |

**Validation rate:** 74% pass rate against 5,000 real Oracle formulas.

### 4.2 Validator (`app/services/validator.py`)

Three-layer validation pipeline:

```
Source Code
    │
    ▼
Layer 1: SYNTAX (Lark parser)
    │  Parse errors → line/col diagnostics
    ▼
Layer 2: SEMANTIC (AST analysis)
    │  Undeclared variable references
    │  Unassigned OUTPUT variables
    │  (Excludes GET_CONTEXT/SET_INPUT first args)
    ▼
Layer 3: BUSINESS RULES
    │  Missing RETURN statement
    │  Overtime OUTPUT without HOURS_WORKED reference
    ▼
ValidationResult { valid: bool, diagnostics: Diagnostic[] }
```

### 4.3 AI Service (`app/services/ai_service.py`)

Orchestrates RAG retrieval, prompt assembly, and Claude API calls.

**Prompt Assembly:**
```
System Prompt (~3,000 tokens)
├── FF syntax rules
├── Supported keywords & functions
├── Formula structure convention
├── Header comment block template
├── Naming convention rules
└── Output format requirements

User Prompt (dynamic, ~3,000-12,000 tokens)
├── ## Relevant Example Formulas     ← 3 formulas from RAG
├── ## Current Formula in Editor     ← Editor content
├── ## Formula Type Template         ← Type-specific skeleton
└── ## Request                       ← User's message
```

**Token Budget:**
| Component | Tokens |
|-----------|--------|
| System Prompt | ~3,000 |
| RAG Examples (top-3) | ~2,000 - 6,000 |
| Editor Code | 0 - 2,000 |
| Type Template | ~800 - 1,200 |
| Conversation History | 0 - 4,000 |
| **Total Input** | **~6,000 - 15,000** |
| Output | ~500 - 4,000 |

### 4.4 RAG Engine (`app/services/rag_engine.py`)

```
User Query: "Calculate overtime for hours > 40"
    │
    ▼ SentenceTransformer (all-MiniLM-L6-v2)
384-dim embedding vector
    │
    ▼ ChromaDB HNSW cosine search
Top-3 most similar formulas (similarity > 0.6)
    │
    ▼ Return full formula source code + metadata
Injected into LLM prompt as few-shot examples
```

### 4.5 Formula Templates (`app/services/formula_templates.py`)

- **123 formula type templates** stored in `data/formula_type_templates.json`
- Each template contains: skeleton code, naming convention, display name
- 9 types have hand-crafted detailed templates (WFM rules, Oracle Payroll)
- 114 types auto-generated from real formula structure analysis
- Templates inject `{formula_name}`, `{description}`, `{date_today}` placeholders
- Author fixed as "Payroll Admin", date in YYYY/MM/DD format

### 4.6 DBI Registry

- **41,937 Database Items** extracted from Oracle `FF_FDI_USAGES` + `FF_DATABASE_ITEMS_VL`
- Only DBIs actually referenced by Fast Formulas (not the full 1.76M)
- Categorized by module: OTHER (38K), PERSON (1.3K), COMPENSATION (665), BENEFITS (591), TIME_LABOR (422), ABSENCE (289), RECRUITING (173), PAYROLL (76)
- Searchable, filterable, sortable in the frontend DBI panel

---

## 5. Frontend Design

### 5.1 Component Hierarchy

```
App (ConfigProvider + Theme)
└── AppLayout (full viewport)
    ├── Toolbar (New, Save, Export)
    ├── Main Content (horizontal split, draggable)
    │   ├── EditorWithChat (left panel)
    │   │   ├── FFEditor (Monaco, vs-light theme)
    │   │   ├── Chat Toggle Bar
    │   │   ├── Chat History (resizable)
    │   │   ├── Formula Type + Sample Selectors
    │   │   └── Chat Input (2-line textarea + send)
    │   └── SimulationPanel (right panel, 360px default)
    │       ├── Validate Tab → ValidationResults
    │       ├── Simulate Tab → InputForm + ExecutionTrace
    │       ├── DBIs Tab → DBIPanel (41,937 items, paginated)
    │       └── Explain Tab → ExplainPanel (streaming)
    └── StatusBar (syntax status, errors, warnings, line count)
```

### 5.2 State Management (Zustand)

| Store | State | Purpose |
|-------|-------|---------|
| `editorStore` | code, diagnostics, isValid, isDirty, formulaType, currentFormulaId | Editor state + formula type |
| `chatStore` | messages, isStreaming, sessionId | Chat conversation |
| `simulationStore` | inputData, outputData, trace, status, error | Simulation I/O |

### 5.3 Theme

- **Palette:** Warm parchment light theme (#f8f6f2 base, #c07528 amber accent)
- **Typography:** DM Sans (UI) + JetBrains Mono (code/status)
- **Monaco Theme:** `light`
- **Cursor:** `wait` during AI streaming

---

## 6. API Design

### 6.1 Endpoints

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| POST | `/api/validate` | `{code}` | `{valid, diagnostics[]}` | 3-layer validation |
| POST | `/api/simulate` | `{code, input_data}` | `{status, output_data, execution_trace}` | Execute formula |
| POST | `/api/chat` | `{message, formula_type, code, session_id}` | SSE stream `{text}` | AI formula generation |
| POST | `/api/complete` | `{code, cursor_line, cursor_col}` | `{completions[]}` | Inline completion (Haiku) |
| POST | `/api/explain` | `{code}` | SSE stream `{text}` | Formula explanation |
| GET | `/api/formulas` | — | `[{id, name, code, ...}]` | List saved formulas |
| POST | `/api/formulas` | `{name, code}` | `{id, ...}` | Save formula |
| GET | `/api/dbi` | `?search=&module=&data_type=&limit=&offset=` | `{total, items[]}` | DBI registry with filtering |
| GET | `/api/dbi/modules` | — | `[{module, count}]` | DBI module list |
| GET | `/api/formula-types` | — | `[{type_name, display_name, sample_prompts[]}]` | 123 formula types |
| GET | `/api/formula-types/{name}/template` | — | `{skeleton, naming_pattern, ...}` | Type template |
| GET | `/api/health` | — | `{status: "ok"}` | Health check |

### 6.2 SSE Streaming Protocol

Chat and Explain endpoints use Server-Sent Events:
```
data: {"text": "DEFAULT FOR"}
data: {"text": " hours IS 0\n"}
data: {"text": "INPUTS ARE hours\n"}
...
data: [DONE]
```

Frontend extracts code blocks from the streamed response and auto-fills the Monaco Editor.

---

## 7. Data Import Pipeline

### 7.1 Oracle Formula Import

```bash
python -m app.scripts.import_from_oracle [--dry-run] [--formula-type TYPE]
```

| Step | Action | Details |
|------|--------|---------|
| 1 | Connect | oracledb thin mode, no Oracle Client needed |
| 2 | Query | `FF_FORMULAS_VL JOIN FF_FORMULA_TYPES` |
| 3 | Fetch | `fetch_lobs=False`, `arraysize=1000` for performance |
| 4 | Embed | each formula → 384-dim vector via MiniLM-L6-v2 |
| 5 | Store | ChromaDB upsert with metadata (name, type, source) |

- **Total:** 38,951 formulas imported
- **Duration:** ~37 minutes (embedding bottleneck)
- **Storage:** `data/chroma/` (~500MB)

### 7.2 DBI Import

Extracted from `FF_FDI_USAGES JOIN FF_DATABASE_ITEMS_VL` where `USAGE='D'`:
- 41,937 distinct DBIs actually used by formulas
- Stored as `data/dbi_registry/all_formula_dbis.json`

### 7.3 Formula Types Export

Extracted from `FF_FORMULA_TYPES` with descriptions from `FF_FORMULAS_VL`:
- 123 types with display names and up to 200 sample descriptions
- Stored as `data/formula_types_registry.json`
- Templates stored as `data/formula_type_templates.json`

---

## 8. Security Considerations

| Area | Measure |
|------|---------|
| API Key | `ANTHROPIC_API_KEY` in `.env`, never committed |
| Oracle Credentials | In import script defaults, not in production code |
| CORS | Restricted to `http://localhost:5173` |
| Input Validation | Pydantic models on all API inputs |
| Rate Limiting | slowapi on API endpoints |
| SQL Injection | SQLAlchemy ORM, no raw SQL |

---

## 9. Testing Strategy

**74 automated tests** across 12 modules. See [TDD Test Matrix](TDD.md) for full details.

| Category | Tests | Coverage |
|----------|-------|----------|
| Parser (core + extended) | 23 | All grammar constructs |
| Simulator (core + extended) | 10 | Arithmetic, branching, functions, concat, context |
| Validator (core + extended) | 12 | 3-layer validation, false positive prevention |
| API (core + extended) | 14 | All HTTP endpoints, error cases |
| Templates | 7 | 123 type templates, skeletons, placeholders |
| AI Service | 3 | Prompt building (no real API calls) |
| Models | 5 | SQLAlchemy CRUD |
| Health | 1 | Liveness check |

**Grammar validation:** 74% pass rate against 5,000 real Oracle formulas.

---

## 10. Performance

| Operation | Latency | Notes |
|-----------|---------|-------|
| Formula validation | <100ms | Earley parser + semantic checks |
| Formula simulation | <50ms | Tree-walking interpreter |
| RAG query | ~50-100ms | ChromaDB HNSW + embedding |
| AI generation (chat) | 2-8s | Claude Sonnet streaming |
| AI completion | 0.5-2s | Claude Haiku |
| DBI search | <50ms | In-memory JSON filtering |
| Frontend build | ~8s | Vite production bundle (~990KB) |

---

## 11. Deployment

### Development
```bash
# Backend
cd backend && source venv/bin/activate
uvicorn app.main:app --reload --port 8000

# Frontend
cd frontend && npm run dev
```

### Dependencies
- Python 3.13+ with virtualenv
- Node.js 20+ with npm
- No Oracle Client needed (oracledb thin mode)
- No GPU needed (MiniLM-L6 runs on CPU)
