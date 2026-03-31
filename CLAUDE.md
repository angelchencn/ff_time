# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FF Time is an AI-powered Oracle HCM Fast Formula generator for Time & Labor. It has a React/TypeScript frontend with Monaco Editor and a Python/FastAPI backend that uses Claude API + RAG to generate, validate, and simulate Fast Formula code.

## Commands

### Backend (run from `backend/`)

```bash
# Activate virtualenv
source venv/bin/activate

# Run server
uvicorn app.main:app --reload --port 8000

# Run all tests
python -m pytest tests/ -v

# Run a single test file
python -m pytest tests/test_parser.py -v

# Run a single test
python -m pytest tests/test_parser.py::test_parse_simple_formula -v

# Skip slow RAG tests (require model download)
python -m pytest tests/ --ignore=tests/test_rag_engine.py -v

# Database migrations
alembic upgrade head
alembic revision --autogenerate -m "description"

# Seed knowledge base with Fast Formula samples
python -m app.scripts.seed_knowledge_base
```

### Frontend (run from `frontend/`)

```bash
npm run dev       # Dev server at http://localhost:5173
npm run build     # TypeScript check + Vite bundle
npm run lint      # ESLint
```

## Architecture

```
React Frontend (port 5173)
  → axios/SSE → FastAPI Backend (port 8000)
                   ├─ /api/validate  → Validator Service → Lark Parser (grammar.lark → AST)
                   ├─ /api/simulate  → Simulator Service → AST Interpreter
                   ├─ /api/chat      → AI Service → RAG Engine (ChromaDB) + Claude API (SSE streaming)
                   ├─ /api/complete  → AI Service → Claude Haiku (fast completions)
                   ├─ /api/explain   → AI Service → Claude Sonnet (SSE streaming)
                   ├─ /api/formulas  → SQLAlchemy → SQLite
                   └─ /api/dbi       → JSON file (time_labor_dbis.json)
```

### Backend Key Layers

- **Parser** (`app/parser/`): Lark grammar defines Fast Formula syntax. `ff_parser.py` transforms parse trees into immutable AST nodes (`ast_nodes.py`). The grammar uses case-insensitive keyword terminals with priority to avoid NAME conflicts.
- **Interpreter** (`app/parser/interpreter.py`): Tree-walking interpreter that executes AST nodes. Uses `ReturnSignal` exception for control flow. Tracks execution trace and filters output to exclude input variables.
- **Validator** (`app/services/validator.py`): Three layers — syntax (Lark parser), semantic (undeclared vars, output assignment), rules (business logic like missing RETURN).
- **AI Service** (`app/services/ai_service.py`): Builds prompts with RAG context from ChromaDB. Uses Sonnet for generation/chat, Haiku for completions. Lazy-initialized in API routes. Sends current editor code as context so AI can modify existing formulas.
- **RAG Engine** (`app/services/rag_engine.py`): ChromaDB with `all-MiniLM-L6-v2` embeddings. Samples stored in `data/samples/` as `.ff` files with optional `.json` metadata.

### Frontend Key Layers

- **Zustand stores** (`src/stores/`): `editorStore` (code, diagnostics, mode), `chatStore` (messages, streaming state), `simulationStore` (inputs, outputs, trace).
- **Monaco Editor** (`src/languages/`): Custom `fast-formula` language with Monarch tokenizer for syntax highlighting and completion provider for keywords, functions, and DBI names.
- **SSE Client** (`src/services/sse.ts`): Fetch-based streaming for chat and explain endpoints. Backend sends JSON-encoded chunks (`{"text": "..."}`) to preserve newlines in SSE protocol.
- **Three-column layout** with draggable dividers (Chat | Editor | Simulation Panel). Light theme via Ant Design ConfigProvider.

## Environment Variables

Backend requires a `.env` file in `backend/` (copy from `.env.example`):

```
ANTHROPIC_API_KEY=sk-ant-...   # Required
DATABASE_URL=sqlite:///./ff_time.db
CHROMA_PERSIST_DIR=./chroma_data
CORS_ORIGINS=http://localhost:5173
```

## Oracle Fast Formula Language Reference

Source: [Oracle FastFormula User Guide](https://docs.oracle.com/cd/E18727_01/doc.121/e14567/T1774T1776.htm)

### Reserved Words

Cannot be used as variable names:

```
ALIAS   AND     ARE     AS      DEFAULT   DEFAULTED
ELSE    ELSIF   EXECUTE EXIT    FOR       IF
INPUT   INPUTS  IS      LIKE    LOCAL     LOOP
NOT     OR      OUTPUT  RETURN  THEN      USING
WAS     WHILE
```

### Grammar Scope (Supported)

The Lark grammar (`backend/app/parser/grammar.lark`) supports:

| Construct | Syntax |
|-----------|--------|
| Default declaration | `DEFAULT FOR name IS value` |
| Single input | `INPUT IS name` |
| Multiple inputs | `INPUTS ARE name1, name2, name3` |
| Output | `OUTPUT IS name` |
| Local | `LOCAL name` |
| Alias | `ALIAS long_name AS short_name` |
| Assignment | `name = expression` |
| If/Then/Else | `IF ... THEN ... ELSIF ... THEN ... ELSE ... END IF` |
| While loop | `WHILE ... LOOP ... END LOOP` |
| Return (multi) | `RETURN var1, var2` |
| Block grouping | `( statement1  statement2 )` — for multiple statements under THEN/ELSE |
| Was defaulted | `IF var WAS DEFAULTED THEN ...` |
| Pattern match | `name LIKE 'pattern%'` / `name NOT LIKE 'x%'` |
| Comments | `/* block comment */` |
| Data types | `(NUMBER)`, `(TEXT)`, `(DATE)` |
| Single-quote strings | `'O''Brien'` — doubled quotes for escaping |

**Not yet supported:** `CURSOR`/`FETCH`, `CHANGE_CONTEXTS`, array processing (`A.FIRST`, `A.NEXT`, `A.EXISTS`)

### Operators

| Type | Operators |
|------|-----------|
| Arithmetic | `+` `-` `*` `/` |
| Comparison | `=` `!=` `<>` `><` `<` `>` `<=` `>=` `=>` `=<` |
| Logical | `AND` `OR` `NOT` |
| Pattern | `LIKE` `NOT LIKE` (wildcards: `%` = any chars, `_` = single char) |
| Special | `WAS DEFAULTED` |
| Precedence | NOT > AND > OR; unary `-` > `*` `/` > `+` `-` |

### Built-in Functions

**Numeric:** `ABS`, `CEIL`, `FLOOR`, `GREATEST`, `GREATEST_OF`, `LEAST`, `LEAST_OF`, `POWER`, `ROUND`, `ROUNDUP`, `ROUND_UP`, `TRUNC`, `TRUNCATE`

**String:** `CHR`, `INITCAP`, `INSTR`, `INSTRB`, `LENGTH`, `LENGTHB`, `LOWER`, `LPAD`, `LTRIM`, `REPLACE`, `RPAD`, `RTRIM`, `SUBSTR`, `SUBSTRING`, `SUBSTRB`, `TRANSLATE`, `TRIM`, `UPPER`

**Date:** `ADD_DAYS`, `ADD_MONTHS`, `ADD_YEARS`, `DAYS_BETWEEN`, `HOURS_BETWEEN`, `LAST_DAY`, `MONTHS_BETWEEN`, `NEW_TIME`, `NEXT_DAY`

**Conversion:** `CONVERT`, `DATE_TO_TEXT`, `NUM_TO_CHAR`, `TO_CHAR`, `TO_DATE`, `TO_NUM`, `TO_NUMBER`, `TO_TEXT`

**Lookup/Table:** `GET_LOOKUP_MEANING`, `GET_TABLE_VALUE`, `RAISE_ERROR`, `RATES_HISTORY`, `CALCULATE_HOURS_WORKED`

**Globals:** `SET_TEXT`, `SET_NUMBER`, `SET_DATE`, `GET_TEXT`, `GET_NUMBER`, `GET_DATE`, `ISNULL`, `CLEAR_GLOBALS`, `REMOVE_GLOBALS`

**Accruals:** `GET_ABSENCE`, `GET_ACCRUAL_BAND`, `GET_CARRY_OVER`, `GET_NET_ACCRUAL`, `GET_WORKING_DAYS`, `GET_PAYROLL_PERIOD`, `GET_PERIOD_DATES`, `GET_START_DATE`, `GET_ASSIGNMENT_STATUS`

**Formula/Debug:** `CALL_FORMULA`, `LOOP_CONTROL`, `PUT_MESSAGE`, `DEBUG`

### Statement Ordering Rules

Formulas must follow this order:
1. Comments (optional, can appear anywhere)
2. ALIAS statements
3. DEFAULT statements
4. INPUTS statement
5. Other statements (Assignment, If, While, Return)

### Data Types

| Type | Literal Format | Example |
|------|---------------|---------|
| Numeric | No quotes, optional decimal | `63`, `-2.3`, `0.33` |
| Text | Single quotes, `''` to escape | `'Smith'`, `'O''Brien'` |
| Date | Quoted + `(date)` | `'2024-01-15 00:00:00' (date)`, `'15-JAN-2024' (date)` |

### Variable Naming Rules

- Words joined by underscores: `variable_name`
- Start with alphabetic character (A-Z), followed by alphanumeric (A-Z, 0-9)
- Maximum 80 characters
- Case-insensitive: `EMPLOYEE_NAME` = `employee_name`
- Cannot be reserved words or digits-only

## Database Items (DBI)

DBI registry at `backend/data/dbi_registry/time_labor_dbis.json` — 65 items across 3 modules:

| Prefix | Module | Count | Description |
|--------|--------|-------|-------------|
| `HWM_PPM_TM_` | TIME_LABOR | 7 | Time entry detail (measure, start/stop time, project, task) |
| `HWM_EMP_SCHD_` | TIME_LABOR | 5 | Employee schedule (availability, shift name, hours) |
| `HWM_CTX_` / `HWM_` | TIME_LABOR | 9 | Time rule contexts (period dates, rule ID, resource ID) |
| `PER_ASG_` | PERSON | 13 | Assignment (grade, job, location, salary basis, status, normal hours) |
| `PER_EMP_` | PERSON | 8 | Employee (hire date, name, employee number, DOB) |
| `PAY_` | PAYROLL | 5 | Payroll period (start/end, period type, periods per year) |
| (no prefix) | TIME_LABOR | 18 | Core items (HOURS_WORKED, OVERTIME_RATE, SHIFT_TYPE, HOLIDAY_FLAG, etc.) |

The `/api/dbi` endpoint supports `?module=TIME_LABOR` and `?search=hours` query params.

Full DBI list from Oracle: My Oracle Support Doc ID [1990057.1](https://support.oracle.com/epmos/faces/DocumentDisplay?id=1990057.1) (WFM Database Items spreadsheet).

### Formula Types for Time & Labor

| Type | Purpose |
|------|---------|
| Time Entry Rule | Validate/modify time entries on submission |
| Time Calculation Rule | Calculate derived values (overtime, premiums) |
| Time Advanced Category Rule | Categorize time entries |
| Time Device Rule | Process time device data |
| Time Submission Rule | Validate time card submissions |
| Workforce Compliance Rule | Check labor compliance |

## RAG Knowledge Base

5 sample formulas in `backend/data/samples/`:

| File | Use Case | Complexity |
|------|----------|------------|
| `overtime_pay.ff` | Standard overtime (threshold + multiplier) | medium |
| `shift_differential.ff` | Night/evening shift differential | medium |
| `holiday_pay.ff` | Holiday pay multiplier | simple |
| `weekly_hours_cap.ff` | Weekly hours cap with warning | simple |
| `double_time_overtime.ff` | California-style tiered overtime (1x/1.5x/2x) | complex |

Run `python -m app.scripts.seed_knowledge_base` to ingest into ChromaDB.

## Immutability Pattern

All AST nodes are frozen dataclasses. Zustand stores use immutable state updates. When modifying data, always create new objects rather than mutating existing ones.
