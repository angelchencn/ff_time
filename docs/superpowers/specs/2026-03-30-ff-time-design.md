# FF Time — AI-Based Fast Formula Generator for Time & Labor

## Overview

A web application that uses AI (Claude API) to generate, validate, and simulate Oracle HCM Fast Formulas, focused on the Time & Labor module with extensibility to other modules.

### Target Users

| User Type | Primary Interaction | Key Needs |
|-----------|-------------------|-----------|
| HR/Payroll Business Consultants | Natural language chat | Describe requirements in plain language, get working Fast Formula code |
| Oracle HCM Technical Consultants | Monaco code editor | Code completion, syntax checking, debugging, AI-assisted fixes |

## System Architecture

```
┌─────────────────────────────────────────────────────┐
│                   React Frontend                     │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ Chat UI  │  │ Monaco Editor│  │ Simulation    │  │
│  │(NL Input)│  │ (Code Edit)  │  │ Panel         │  │
│  └────┬─────┘  └──────┬───────┘  └───────┬───────┘  │
└───────┼───────────────┼───────────────────┼──────────┘
        │               │                   │
        ▼               ▼                   ▼
┌─────────────────────────────────────────────────────┐
│              Python FastAPI Backend                   │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │ AI       │  │ Validator│  │ Simulator         │  │
│  │ Service  │  │ Service  │  │ Service           │  │
│  └────┬─────┘  └──────────┘  └───────────────────┘  │
│       │                                              │
│  ┌────┴─────┐                                        │
│  │ RAG      │                                        │
│  │ Engine   │                                        │
│  └────┬─────┘                                        │
└───────┼──────────────────────────────────────────────┘
        │
   ┌────┴─────┐    ┌─────────────┐
   │ Vector   │    │ Claude API  │
   │ Store    │    │             │
   └──────────┘    └─────────────┘
```

### Core Services

| Service | Responsibility |
|---------|---------------|
| **AI Service** | Receives user requests, calls RAG retrieval + Claude API to generate/complete Fast Formulas |
| **RAG Engine** | Manages Fast Formula knowledge base, retrieves similar examples, assembles context |
| **Validator Service** | Fast Formula syntax parsing, variable/DBI legality checks, error localization |
| **Simulator Service** | Accepts test data, simulates Fast Formula execution, returns computed results |

## AI Service + RAG Pipeline

### Knowledge Base Construction

- Chunk existing Fast Formula samples + Oracle documentation, generate embeddings, store in vector database
- Each chunk has metadata tags: `formula_type` (Time/Labor/Payroll etc.), `use_case` (overtime/scheduling/leave etc.), `complexity`

### Mode 1: Natural Language Generation (Business Consultants)

```
User input: "Employees working weekends get 2x pay, public holidays get 3x"
    │
    ▼
Intent parsing → Extract keywords: overtime, weekend 2x, holiday 3x
    │
    ▼
RAG retrieval → Find most similar overtime Fast Formula samples (top 3-5)
    │
    ▼
Prompt assembly → System Prompt (FF syntax rules) + retrieved samples + user requirements
    │
    ▼
Claude API → Generate complete Fast Formula code
    │
    ▼
Validator auto-check → Pass: display; Fail: AI auto-correction (max 3 rounds)
```

### Mode 2: Code Assistance (Technical Consultants)

- **Auto-completion**: As user types in Monaco Editor, backend uses current context + RAG samples to call Claude for completion suggestions
- **Real-time diagnostics**: Code changes trigger Validator, errors shown as red squiggly lines in editor
- **AI Explain/Fix**: Select code, right-click "Explain" or "Fix this", Claude returns explanation or fix suggestion

### Vector Database

ChromaDB (lightweight, Python-native, easy local development). Can migrate to Pinecone/Weaviate later.

## Validator Service

Three-layer validation built on a custom Fast Formula parser.

### Layer 1 — Syntax Parsing (Parser)

- Built with Python's **Lark** library using a formal Fast Formula grammar
- Covers: variable declarations (`DEFAULT`/`INPUT`/`OUTPUT`), assignments, IF/ELSE, loops, function calls, RETURN
- Output: AST (Abstract Syntax Tree) + precise error locations (line, column) for Monaco markers

### Layer 2 — Semantic Validation

- **Variable reference check**: All used variables must be declared
- **DBI legality**: Referenced Database Items must exist in known DBI catalog (maintained DBI list for Time & Labor module)
- **Type checking**: No mixing strings and numbers in arithmetic operations
- **Output variable check**: All OUTPUT-declared variables must have assignments

### Layer 3 — Rule Validation

- Business-level rules: e.g., overtime formula must reference required DBIs (`HOURS_WORKED`, `OVERTIME_RATE`, etc.)
- Configurable rule sets, customizable per client/scenario

### Monaco Integration

```
User edits code
    │ (debounce 300ms)
    ▼
POST /api/validate → Validator returns diagnostic list
    │
    ▼
Monaco markers API → Red squiggly lines + hover tooltips with error messages
```

## Simulator Service

A lightweight Fast Formula interpreter that runs locally without connecting to Oracle.

### Execution Engine

Tree-Walking Interpreter based on the AST from the Validator:

- **Variable environment**: Scoped dictionary storing INPUT/DEFAULT/LOCAL variable values
- **DBI simulation**: User provides test data via JSON/form in Simulation Panel, simulating DBI return values
- **Expression evaluation**: Recursive AST traversal for arithmetic, string operations, conditionals, loops
- **Function support**: Built-in simulation of common Fast Formula functions (`TO_NUMBER`, `TO_CHAR`, `GET_VALUE_SET`, etc.)

### User Interface

```
┌─────────────────────────────────────────┐
│  Simulation Panel                       │
│                                         │
│  Input Variables:                       │
│  ┌───────────────┬──────────────────┐   │
│  │ HOURS_WORKED  │  45              │   │
│  │ OT_RATE       │  1.5             │   │
│  │ HOLIDAY_FLAG  │  N               │   │
│  └───────────────┴──────────────────┘   │
│                                         │
│  [▶ Run]                                │
│                                         │
│  Output:                                │
│  ┌──────────────────────────────────┐   │
│  │ OT_HOURS = 5                    │   │
│  │ OT_PAY = 7.5                    │   │
│  │ TOTAL_PAY = 47.5                │   │
│  └──────────────────────────────────┘   │
│                                         │
│  Execution Trace:  [expand/collapse]    │
│  Line 3: IF HOURS_WORKED > 40 → true   │
│  Line 4: OT_HOURS = 45 - 40 = 5       │
│  Line 5: OT_PAY = 5 * 1.5 = 7.5      │
└─────────────────────────────────────────┘
```

### Key Features

| Feature | Description |
|---------|-------------|
| **Execution Trace** | Line-by-line execution display for debugging |
| **Breakpoint Debugging** | Click line numbers to set breakpoints, step through execution |
| **Batch Testing** | Upload CSV to run multiple test data sets, compare results |
| **Boundary Detection** | Auto-suggest boundary test cases (0 hours, negative values, large values) |

### Limitations

The simulator cannot 100% reproduce Oracle runtime behavior (e.g., edge cases of certain built-in functions, database interactions). UI clearly states: "Simulation results are for reference only. Please do final verification in your Oracle environment."

## Frontend Design

### Layout

Three-column adaptive layout with draggable dividers:

```
┌──────────────────────────────────────────────────────────┐
│  Toolbar: [New] [Open] [Save] [Export] [Mode: Chat/Code] │
├────────────┬──────────────────────┬──────────────────────┤
│            │                      │                      │
│  Chat      │   Monaco Editor      │  Right Panel         │
│  Panel     │                      │  ┌────────────────┐  │
│            │   Fast Formula code   │  │ Tab: Validate  │  │
│  Natural   │   Syntax highlight   │  │ Tab: Simulate  │  │
│  language  │   Auto-complete      │  │ Tab: Explain   │  │
│  dialog    │   Error markers      │  │                │  │
│            │                      │  │ Validation /   │  │
│            │                      │  │ Simulation /   │  │
│            │                      │  │ AI explain     │  │
│            │                      │  └────────────────┘  │
├────────────┴──────────────────────┴──────────────────────┤
│  Status Bar: Syntax ✓ │ Variables: 5 │ Lines: 42         │
└──────────────────────────────────────────────────────────┘
```

### Two Modes

| Mode | Target User | Layout |
|------|-------------|--------|
| **Chat Mode** | Business consultants | Chat Panel is primary, Editor and Simulation on right |
| **Code Mode** | Technical consultants | Editor is primary, Chat collapsed to sidebar |

### Monaco Editor Customization

- **Custom Fast Formula language definition**: keyword highlighting (`DEFAULT`, `INPUT`, `OUTPUT`, `IF`, `THEN`, `ELSE`, `RETURN`, etc.)
- **Auto-completion**: DBI names, built-in functions, declared variables
- **Hover tooltips**: DBI hover shows data type and description
- **Code folding**: IF/ELSE blocks, comment blocks collapsible

## Data Model

```
Formula
├── id: UUID
├── name: string
├── description: string
├── formula_type: enum (TIME_LABOR, PAYROLL, ABSENCE, ...)
├── use_case: string (overtime, shift_diff, ...)
├── code: text
├── version: int
├── status: enum (DRAFT, VALIDATED, EXPORTED)
├── created_at / updated_at
└── user_id: UUID

DBIRegistry
├── id: UUID
├── name: string (e.g. HOURS_WORKED)
├── data_type: enum (NUMBER, TEXT, DATE)
├── module: enum (TIME_LABOR, PAYROLL, ...)
├── description: string
└── is_active: bool

SimulationRun
├── id: UUID
├── formula_id: UUID
├── input_data: JSON
├── output_data: JSON
├── execution_trace: JSON
├── status: enum (SUCCESS, ERROR)
└── created_at

ChatSession
├── id: UUID
├── formula_id: UUID (nullable)
├── messages: [{role, content, timestamp}]
└── created_at
```

## API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/chat` | Natural language dialog, streaming response (SSE) |
| POST | `/api/complete` | Code completion request |
| POST | `/api/validate` | Validate Fast Formula code |
| POST | `/api/simulate` | Simulate execution |
| POST | `/api/explain` | AI explain selected code |
| GET/POST/PUT | `/api/formulas` | Formula CRUD |
| GET | `/api/dbi` | Query DBI catalog (supports search/filter) |
| POST | `/api/formulas/{id}/export` | Export to deployable format |

## Storage

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Primary DB | **SQLite** (MVP) → PostgreSQL | Lightweight for MVP, can migrate later |
| Vector DB | **ChromaDB** | Python-native, embedded, zero ops |
| File Storage | Local filesystem | Store exported Formula files |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + TypeScript + Vite |
| UI Components | Ant Design |
| Code Editor | Monaco Editor (`@monaco-editor/react`) |
| Backend | Python 3.12 + FastAPI |
| AI | Claude API (Anthropic SDK) |
| RAG | ChromaDB + `sentence-transformers` for embeddings |
| Parser | Lark (Python, Fast Formula grammar) |
| Database | SQLite (MVP) + SQLAlchemy ORM |
| API Communication | REST + SSE (streaming generation) |

## Project Structure

```
ff_time/
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   │   ├── ChatPanel/
│   │   │   ├── Editor/
│   │   │   ├── SimulationPanel/
│   │   │   └── Layout/
│   │   ├── services/          # API calls
│   │   ├── hooks/             # Custom hooks
│   │   ├── languages/         # Monaco FF language definition
│   │   └── App.tsx
│   └── package.json
│
├── backend/
│   ├── app/
│   │   ├── api/               # FastAPI routes
│   │   │   ├── chat.py
│   │   │   ├── validate.py
│   │   │   ├── simulate.py
│   │   │   └── formulas.py
│   │   ├── services/
│   │   │   ├── ai_service.py
│   │   │   ├── rag_engine.py
│   │   │   ├── validator.py
│   │   │   └── simulator.py
│   │   ├── parser/
│   │   │   ├── grammar.lark   # Fast Formula grammar definition
│   │   │   ├── ast_nodes.py
│   │   │   └── interpreter.py
│   │   ├── models/            # SQLAlchemy models
│   │   └── main.py
│   ├── data/
│   │   ├── samples/           # FF sample files
│   │   └── dbi_registry/      # DBI catalog
│   └── requirements.txt
│
├── docs/
└── README.md
```

## Error Handling

| Scenario | Handling |
|----------|---------|
| Claude API timeout/failure | Retry 2 times; if still fails, prompt user to try later, show last successful result |
| AI-generated code is invalid | Auto-trigger correction loop (max 3 rounds); if still invalid, show code + validation errors for manual adjustment |
| Simulation execution error | Show error line number and reason (e.g., division by zero, undefined variable); Execution Trace highlights error step in red |
| RAG retrieval returns nothing | Degrade to pure Claude generation (without sample context); inform user results may be less precise |
| Unclear user input | AI asks clarifying questions, does not guess |

## Security

| Concern | Measure |
|---------|---------|
| **API Key** | Claude API Key stored in backend environment variables, frontend never touches it |
| **Input Validation** | All API inputs validated with Pydantic schemas |
| **Code Injection** | Simulator uses AST-based interpretation, never uses `eval`, never executes arbitrary code |
| **Rate Limiting** | FastAPI middleware limits API call frequency, prevents Claude API abuse |
| **Data Isolation** | Users can only access their own Formulas and ChatSessions |

## Scope

### MVP (Time & Labor)

- Chat-based natural language → Fast Formula generation
- Monaco Editor with FF syntax highlighting, auto-completion, error markers
- Three-layer validation (syntax, semantic, rules)
- Simulation execution with trace
- Formula CRUD and export

### Future Extensions

- Additional modules: Payroll, Absence, Benefits
- User authentication and multi-tenancy
- Formula version history and diff
- Team collaboration and sharing
- Oracle environment integration for direct deployment
