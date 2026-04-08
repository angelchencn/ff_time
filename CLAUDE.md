# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FF Time is an AI-powered Oracle HCM Fast Formula generator for Time & Labor. It has a React/TypeScript frontend with Monaco Editor and TWO backend implementations:

1. **Java/Jersey backend** (`java/`) — Primary, uses GPT-5.4 via OpenAI API, ANTLR4 parser, 109 tests
2. **Python/FastAPI backend** (`backend/`) — Original, uses Claude API + ChromaDB RAG

The Java backend is self-contained with all data files in `java/src/main/resources/`. It includes 501 RAG formulas, 123 formula type templates, 41K DBIs, and custom formula samples.

## Commands

### Java Backend (run from `java/`) — PRIMARY

```bash
# Compile and run server (port 8000)
mvn compile exec:java

# Run all tests (109 tests)
mvn test

# Run a single test class
mvn test -Dtest=ValidatorTest

# Generate ANTLR4 sources from grammar
mvn generate-sources

# Environment variables required:
#   OPENAI_API_KEY=sk-...
#   https_proxy=http://proxy:80  (if behind proxy)
```

### Backend (run from `backend/`) — LEGACY PYTHON

```bash
# Activate virtualenv
source venv/bin/activate

# Run server
uvicorn app.main:app --reload --port 8000

# Run all tests (skip slow RAG tests)
python -m pytest tests/ --ignore=tests/test_rag_engine.py -v

# Run a single test file
python -m pytest tests/test_parser_extended.py -v

# Run a single test
python -m pytest tests/test_parser.py::test_parse_simple_formula -v

# Run RAG tests (require model download)
python -m pytest tests/test_rag_engine.py -v

# Database migrations
alembic upgrade head
alembic revision --autogenerate -m "description"

# Seed knowledge base with local Fast Formula samples
python -m app.scripts.seed_knowledge_base

# Import formulas from Oracle database into ChromaDB
python -m app.scripts.import_from_oracle --dry-run    # preview
python -m app.scripts.import_from_oracle               # full import
python -m app.scripts.import_from_oracle --formula-type "Oracle Payroll"  # filter by type
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
                   ├─ /api/validate       → Validator Service → Lark Parser (grammar.lark → AST)
                   ├─ /api/simulate       → Simulator Service → AST Interpreter
                   ├─ /api/chat           → AI Service → RAG Engine (ChromaDB) + Claude API (SSE streaming)
                   ├─ /api/complete       → AI Service → Claude Haiku (fast completions)
                   ├─ /api/explain        → AI Service → Claude Sonnet (SSE streaming)
                   ├─ /api/formulas       → SQLAlchemy → SQLite
                   ├─ /api/dbi            → JSON file (time_labor_dbis.json)
                   └─ /api/formula-types  → JSON registry (formula_types_registry.json + formula_type_templates.json)
```

### Backend Key Layers

- **Parser** (`app/parser/`): Lark grammar defines Fast Formula syntax. `ff_parser.py` transforms parse trees into immutable AST nodes (`ast_nodes.py`). Supports `||`, `WAS NOT DEFAULTED`, quoted identifiers, arrays, `CALL_FORMULA`, `CHANGE_CONTEXTS`, `EXECUTE`, bare function calls. Validated against ~39,000 real formulas (74% pass rate).
- **Interpreter** (`app/parser/interpreter.py`): Tree-walking interpreter that executes AST nodes. Uses `ReturnSignal` exception for control flow. Handles `GET_CONTEXT` identifier args specially. Supports `||` concatenation, `WAS DEFAULTED`, array access, method calls.
- **Validator** (`app/services/validator.py`): Three layers — syntax (Lark parser), semantic (undeclared vars, output assignment), rules (business logic like missing RETURN). Context function args (`GET_CONTEXT`, `SET_INPUT`, etc.) are excluded from undeclared-variable checks.
- **AI Service** (`app/services/ai_service.py`): Builds prompts with RAG context from ChromaDB + formula type templates. Uses Sonnet for generation/chat, Haiku for completions. System prompt includes Oracle naming conventions and header block requirements.
- **RAG Engine** (`app/services/rag_engine.py`): ChromaDB with `all-MiniLM-L6-v2` embeddings. ~39,000 real formulas imported from Oracle DB plus local samples in `data/samples/`.
- **Formula Templates** (`app/services/formula_templates.py`): Loads all 123 formula type templates from `data/formula_type_templates.json`. Each template has a skeleton, naming convention, and display name. Used by AI Service to inject type-specific structure into generation prompts.
- **Formula Types Registry** (`data/formula_types_registry.json`): 123 formula types with display names, formula counts, and up to 200 sample descriptions per type. Extracted from Oracle DB `FF_FORMULAS_VL` descriptions and `formula_text` headers.
- **Oracle Import** (`app/scripts/import_from_oracle.py`): Bulk imports Fast Formulas from Oracle DB into ChromaDB. Connects via `oracledb` thin mode (no Oracle Client needed).

### Frontend Key Layers

- **Zustand stores** (`src/stores/`): `editorStore` (code, diagnostics, mode, formulaType), `chatStore` (messages, streaming state), `simulationStore` (inputs, outputs, trace).
- **Monaco Editor** (`src/components/Editor/FFEditor.tsx`): Custom `fast-formula` language with Monarch tokenizer for syntax highlighting and completion provider.
- **EditorWithChat** (`src/components/Editor/EditorWithChat.tsx`): Combined editor + chat panel with Formula Type selector (123 types, searchable, A-Z sorted), sample dropdown (descriptions from real formulas), and resizable chat history.
- **useFormulaTypes hook** (`src/hooks/useFormulaTypes.ts`): Fetches formula types + sample prompts from `/api/formula-types`.
- **SSE Client** (`src/services/sse.ts`): Fetch-based streaming for chat and explain endpoints.
- **Theme**: Warm light palette with DM Sans + JetBrains Mono typography, amber accent color.

### Data Files

| File | Description |
|------|-------------|
| `data/formula_type_templates.json` | 123 formula type skeletons (naming, structure, header) |
| `data/formula_types_registry.json` | 123 types with display names, counts, sample descriptions |
| `data/dbi_registry/time_labor_dbis.json` | 65 Database Items across 3 modules |
| `data/samples/*.ff` | 6 local sample formulas with `.json` metadata |
| `data/chroma/` | ChromaDB persistent storage (~39,000 formula embeddings) |

## Test Suite

74 tests across 12 test modules (run with `--ignore=tests/test_rag_engine.py`):

| Module | Tests | Covers |
|--------|-------|--------|
| `test_parser.py` | 3 | Basic parsing (simple formula, if/else, syntax error) |
| `test_parser_extended.py` | 20 | `\|\|`, `WAS NOT DEFAULTED`, quoted names, arrays, method calls, `CALL_FORMULA`, `CHANGE_CONTEXTS`, `EXECUTE`, bare func calls, `DEFAULT_DATA_VALUE`, `OUTPUTS ARE`, empty `RETURN`, typed strings, real-world formulas |
| `test_simulator.py` | 4 | Arithmetic, branching, trace, division-by-zero |
| `test_simulator_extended.py` | 6 | `\|\|` concat, `PAY_INTERNAL_LOG_WRITE`, `GET_CONTEXT`, `ISNULL`, `WAS NOT DEFAULTED` |
| `test_validator.py` | 4 | Valid formula, undeclared var, syntax error, unassigned output |
| `test_validator_extended.py` | 8 | Context args not flagged, `SET_INPUT`/`GET_OUTPUT`, concat, quoted identifiers, typed strings, empty return, full WFM formula |
| `test_api.py` | 7 | Validate, simulate, DBI endpoints, missing fields |
| `test_api_extended.py` | 7 | `/api/formula-types`, template endpoint, new syntax via API |
| `test_formula_templates.py` | 7 | Template loading, skeletons, placeholders, auto-generated types |
| `test_ai_service.py` | 3 | Prompt building (system, generation with RAG, completion) |
| `test_models.py` | 5 | SQLAlchemy models (Formula, DBI, SimulationRun, ChatSession) |
| `test_health.py` | 1 | Health endpoint |

## Environment Variables

Backend requires a `.env` file in `backend/` (copy from `.env.example`):

```
ANTHROPIC_API_KEY=sk-ant-...   # Required
DATABASE_URL=sqlite:///./ff_time.db
CHROMA_PERSIST_DIR=./chroma_data
CORS_ORIGINS=http://localhost:5173
```

## RAG + LLM Flow

See `rag+llm.md` for the complete architecture document including:
- End-to-end request flow (6 steps)
- RAG retrieval details (ChromaDB + MiniLM-L6 embedding)
- Prompt assembly structure (System + RAG + Template + History + Request)
- Token budget breakdown (~6K-15K input per request)
- Data flow diagram
- Cost estimation

## Oracle Fast Formula Language Reference

Source: [Oracle FastFormula User Guide](https://docs.oracle.com/cd/E18727_01/doc.121/e14567/T1774T1776.htm)

### Grammar Scope (Supported)

The Lark grammar (`backend/app/parser/grammar.lark`) supports:

| Construct | Syntax |
|-----------|--------|
| Default declaration | `DEFAULT FOR name IS value` |
| Default data value | `DEFAULT_DATA_VALUE FOR name IS value` |
| Single/Multiple inputs | `INPUT IS name` / `INPUTS ARE name1, name2` |
| Single/Multiple outputs | `OUTPUT IS name` / `OUTPUTS ARE name1, name2` |
| Local | `LOCAL name` |
| Alias | `ALIAS long_name AS short_name` |
| Assignment | `name = expr` / `"AREA1" = expr` / `arr[idx] = expr` |
| If/Then/Else | `IF ... THEN ... ELSIF ... ELSE ... END IF` |
| While loop | `WHILE ... LOOP ... END LOOP` |
| Return | `RETURN var1, var2` / `RETURN` (empty) |
| Block grouping | `( statement1  statement2 )` |
| Was defaulted | `WAS DEFAULTED` / `WAS NOT DEFAULTED` |
| Pattern match | `LIKE` / `NOT LIKE` |
| String concat | `'Hello ' \|\| name` |
| Typed strings | `'01-JAN-2024'(DATE)` |
| Quoted identifiers | `"AREA1"` |
| Array access | `name[index]` / `name.FIRST(-1)` |
| Function call | `ROUND(x, 2)` / `SET_INPUT('key', val)` (bare) |
| CALL_FORMULA | `CALL_FORMULA('name', in > 'param', out < 'param' DEFAULT 0)` |
| CHANGE_CONTEXTS | `CHANGE_CONTEXTS(NAME = value)` |
| EXECUTE | `EXECUTE('formula_name')` |
| Comments | `/* block comment */` |

### Operators

| Type | Operators |
|------|-----------|
| Arithmetic | `+` `-` `*` `/` |
| String | `\|\|` |
| Comparison | `=` `!=` `<>` `><` `<` `>` `<=` `>=` `=>` `=<` |
| Logical | `AND` `OR` `NOT` |
| Pattern | `LIKE` `NOT LIKE` |
| Special | `WAS DEFAULTED` `WAS NOT DEFAULTED` |

### Built-in Functions

**Numeric:** `ABS`, `CEIL`, `FLOOR`, `GREATEST`, `LEAST`, `POWER`, `ROUND`, `TRUNC`

**String:** `CHR`, `INITCAP`, `INSTR`, `LENGTH`, `LOWER`, `LPAD`, `LTRIM`, `REPLACE`, `RPAD`, `RTRIM`, `SUBSTR`, `TRANSLATE`, `TRIM`, `UPPER`

**Date:** `ADD_DAYS`, `ADD_MONTHS`, `ADD_YEARS`, `DAYS_BETWEEN`, `HOURS_BETWEEN`, `LAST_DAY`, `MONTHS_BETWEEN`

**Conversion:** `TO_CHAR`, `TO_DATE`, `TO_NUMBER`

**Context:** `GET_CONTEXT`, `SET_CONTEXT`, `NEED_CONTEXT`

**Payroll I/O:** `SET_INPUT`, `GET_INPUT`, `GET_OUTPUT`, `EXECUTE`

**Globals:** `SET_TEXT`, `SET_NUMBER`, `SET_DATE`, `GET_TEXT`, `GET_NUMBER`, `GET_DATE`, `ISNULL`

**Formula:** `CALL_FORMULA`, `PAY_INTERNAL_LOG_WRITE`, `PUT_MESSAGE`, `RAISE_ERROR`

**Lookup:** `GET_TABLE_VALUE`, `GET_LOOKUP_MEANING`, `CALCULATE_HOURS_WORKED`

### Formula Types (123 types)

9 types with hand-crafted templates, 114 auto-generated from real formulas. Top types by count:

| Type | Count | Template Prefix |
|------|-------|----------------|
| Oracle Payroll | 26,864 | varies |
| Auto Indirect | 4,369 | `ORA_HRX_*` |
| Extract Record | 2,961 | `EXT_*` |
| Payroll Run Proration | 2,775 | `*_EARN_PRORATION` |
| WFM Time Calculation Rules | 37 | `ORA_WFM_TCR_*_AP` |
| WFM Time Entry Rules | 21 | `ORA_WFM_TER_*_AP` |
| WFM Time Compliance Rules | 9 | `ORA_WFM_WCR_*_AP` |

## Database Items (DBI)

DBI registry at `backend/data/dbi_registry/time_labor_dbis.json` — 65 items across 3 modules. The `/api/dbi` endpoint supports `?module=TIME_LABOR` and `?search=hours` query params.

## RAG Knowledge Base

~39,000 formulas imported from Oracle database + 6 local samples in `backend/data/samples/`. Run `python -m app.scripts.import_from_oracle` to import from Oracle.

## Immutability Pattern

All AST nodes are frozen dataclasses. Zustand stores use immutable state updates.



rsync -avh --progress \
    --exclude 'target/' \
    /Users/xiaojuch/Claude/ff_time/java/ \
    xiaojuch@phoenix647136.appsdev.fusionappsdphx1.oraclevcn.com:/scratch/xiaojuch/ff_time/java/

rsync -avh --progress \
    --exclude '.git/' \
        --exclude 'node_modules/' \
            /Users/xiaojuch/Claude/ff_time/frontend/ \
            xiaojuch@phoenix647136.appsdev.fusionappsdphx1.oraclevcn.com:/scratch/xiaojuch/ff_time/frontend/

cp -r -f /scratch/xiaojuch/ff_time/java/src/main/java/oracle/apps/hcm/formulas/core/* ./hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/

cp -r -f /scratch/xiaojuch/ff_time/java/src/test/java/oracle/apps/hcm/formulas/core/jerseyTest/* ./hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModelTest/src/oracle/apps/hcm/formulas/core/jerseyTest/

/ade/xiaojuch_phoenix647136_bronze2/fusionapps/jlib/AdfHcmCommonPayPublicModel.jar
/ade/xiaojuch_phoenix647136_bronze2/fusionapps/jlib/hcmPillar/AdfHcmFastFormulaRestModel.jar
/ade/xiaojuch_phoenix647136_bronze2/fusionapps/jlib/faglobal/AdfHcmIndexSearchPublicModel.jar


fusionapps/jlib/hcmPillar/AdfHcmFastFormulaRestModel.jar
fusionapps/jlib/faglobal/AdfHcmIndexSearchPublicModel.jar


https://faeops.oraclecorp.com/FALogs/cptchuhqy/FA1/podscratch/logs/wlslogs/FADomain/servers/ServiceServer_1/logs/ServiceServer_1.log

https://confluence.oraclecorp.com/confluence/display/DDA/Completion+REST+API
ai-common/llm/rest/v2/completion

ade co -nc hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/util/FastFormulaResourceUtil.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/ValidatorService.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/SimulatorService.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/RagService.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/OpenAiProvider.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/LlmProvider.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/LlmDebugLog.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/FusionAiProvider.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/FormulaTypesService.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/FormulaService.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/DbiService.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/CustomFormulaService.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/ChatSessionStore.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service/AiService.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/parser/Tokenizer.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/parser/Interpreter.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/parser/FFParser.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/parser/AstNodes.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/model/Formula.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/config/JerseyConfig.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/config/DbConfig.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/config/CorsFilter.java hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/api/FastFormulaResource.java


cp -r -f /scratch/xiaojuch/ff_time/java/src/main/java/oracle/apps/hcm/formulas/core/jersey/service/AiService.java ./hcm/components/hcmPayroll/formulas/core/restModel/HcmFastFormulaRestModel/src/oracle/apps/hcm/formulas/core/jersey/service