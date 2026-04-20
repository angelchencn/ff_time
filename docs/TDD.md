# Technical Design Document — FF Time

## 1. Overview

**Product Name:** FF Time — AI-Based Fast Formula Generator for HCM

**Purpose:** A web application that enables Oracle HCM consultants to generate, validate, simulate, and manage Fast Formula code using AI with a built-in hand-written parser, interpreter, and RAG knowledge base.

**Target Users:**
- HR/Payroll Business Consultants — generate formulas via natural language
- Oracle HCM Technical Consultants — write/edit code with AI-assisted completion and validation
- AI Agent Studio consumers — programmatic formula generation via REST API

**Two Backend Implementations:**

| Backend | Stack | LLM | Status |
|---------|-------|-----|--------|
| Java/Jersey (`java/`) | Grizzly HTTP, hand-written parser, JDBC | Fusion AI Spectra (Llama 405B / GPT-5 Mini) or OpenAI GPT-5.4 | **Primary** |
| Python/FastAPI (`backend/`) | Lark parser, SQLAlchemy, ChromaDB | Claude API | Legacy |

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌──────────────────────────────────────────────────────────┐
│                   React Frontend                          │
│   Monaco Editor + Chat + Simulation + Templates Panel     │
│   (TypeScript, Vite, Ant Design, Zustand)                 │
│   Model Selector: Llama 405B / GPT-5 Mini (Fusion)        │
│                   GPT 5.4 (Local Dev)                     │
└────────────────────────┬─────────────────────────────────┘
                         │ REST / SSE
                         ▼
┌──────────────────────────────────────────────────────────┐
│              Java/Jersey Backend (Grizzly)                 │
│  ┌────────────┬────────────┬────────────┬──────────────┐  │
│  │ Validator   │ Simulator  │ AI Service │ Template     │  │
│  │ (3-layer)   │ (AST       │ (Spectra / │ Service      │  │
│  │             │  Interp.)  │  OpenAI)   │ (DB CRUD)    │  │
│  └──────┬──────┴──────┬─────┴──────┬─────┴──────┬───────┘  │
│         │             │            │            │           │
│  ┌──────┴─────────────┴──┐  ┌─────┴──────┐  ┌──┴────────┐  │
│  │  Hand-written Parser   │  │ LlmProvider│  │ RAG       │  │
│  │  Tokenizer → FFParser  │  │ (interface)│  │ Service   │  │
│  │  → AST → Interpreter   │  │            │  │ (JDBC)    │  │
│  └────────────────────────┘  └─────┬──────┘  └───────────┘  │
│                                    │                         │
│                         ┌──────────┴──────────┐              │
│                         │                     │              │
│                  ┌──────┴───────┐  ┌──────────┴─────────┐   │
│                  │FusionAiProv. │  │ OpenAiProvider     │   │
│                  │(Spectra SDK) │  │ (GPT-5.4 API)      │   │
│                  └──────────────┘  └────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Oracle DB (JDBC / ADF BC)                            │   │
│  │  FF_FORMULA_TEMPLATES, FF_FORMULA_TEMPLATES_TL,       │   │
│  │  FF_FORMULA_TYPES, FF_FORMULAS_VL                     │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

### 2.2 Provider Selection

```
Environment variable: LLM_PROVIDER

  LLM_PROVIDER=openai  →  OpenAiProvider (GPT-5.4)
                           + JDBC via DriverManager (FF_DB_URL)

  LLM_PROVIDER unset   →  FusionAiProvider (Spectra SDK)
  (default)                + JDBC via ADF BC ApplicationModule
```

### 2.3 Request Flow — Formula Generation

```
User: "Write overtime formula for Time Calculation Rules"
  │
  ▼
Frontend: POST /chat/sync {message, formula_type, prompt_code?, template_code?}
  │
  ▼
FastFormulaResource: extract request fields
  ├── Resolve template_code → TemplateService.findByTemplateCode() → DB
  ├── ChatSessionStore.getOrCreateSession() → in-memory history
  │
  ▼
AiService.chatOnce()
  ├── getSystemPrompt() → FF_FORMULA_TEMPLATES (SYSTEMPROMPT_FLAG=Y, ACTIVE_FLAG=Y)
  ├── extractReferenceFormula() → template body OR RagService (top-3 vector search)
  ├── buildPromptContext() → PromptContext (8 fields)
  │
  ▼
LlmProvider.completeWithContext(context, maxTokens)
  │
  ├── FusionAiProvider path:
  │     resolve promptCode from context (default: HCM_FF_GENERATION_LLM405B)
  │     FAICompletionsClient.getCompletions(promptCode, properties) via reflection
  │     Spectra Orchestrator → LLM (Llama 405B or GPT-5 Mini)
  │
  └── OpenAiProvider path:
        flatten PromptContext → system + user messages
        OpenAI Chat Completions API → GPT-5.4
  │
  ▼
AiService.fixDefaultTypes() → post-process DEFAULT value types
  │
  ▼
Response: {"text": "<formula code>", "session_id": "<uuid>"}
```

---

## 3. Backend Design (Java)

### 3.1 Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| HTTP Server | Grizzly + Jersey | REST API |
| Language | Java 17+ | Backend runtime |
| Parser | Hand-written (Tokenizer → FFParser) | Fast Formula → AST |
| Interpreter | Tree-walking | Simulation |
| LLM (Fusion) | FAI Spectra SDK (reflection) | Llama 405B / GPT-5 Mini |
| LLM (Local) | OpenAI API (okhttp3) | GPT-5.4 |
| Database | Oracle DB (JDBC) | Templates, formula types, formulas |
| Session | In-memory ConcurrentHashMap | Chat history |

### 3.2 Module Design

#### 3.2.1 Parser Module (`jersey/parser/`)

| File | Responsibility |
|------|---------------|
| `Tokenizer.java` | Lexer: source code → token stream |
| `FFParser.java` | Recursive descent parser: tokens → AST |
| `AstNodes.java` | Immutable AST node types (sealed interfaces / records) |
| `Interpreter.java` | Tree-walking interpreter for simulation |

**Grammar Coverage:**

| Construct | Status |
|-----------|--------|
| DEFAULT / DEFAULT_DATA_VALUE | Supported |
| INPUTS ARE / INPUT IS | Supported |
| OUTPUTS ARE / OUTPUT IS | Supported |
| LOCAL declarations | Supported |
| ALIAS | Supported |
| IF / ELSIF / ELSE / END IF | Supported |
| WHILE / LOOP / END LOOP / EXIT | Supported |
| RETURN (multi-value, empty) | Supported |
| WAS DEFAULTED / WAS NOT DEFAULTED | Supported |
| LIKE / NOT LIKE | Supported |
| String concat `\|\|` | Supported |
| Typed strings `'01-JAN-2024'(DATE)` | Supported |
| Quoted identifiers `"AREA1"` | Supported |
| Array access `name[index]`, `.FIRST`, `.NEXT` | Supported |
| CALL_FORMULA | Supported |
| CHANGE_CONTEXTS | Supported |
| EXECUTE / SET_INPUT / GET_OUTPUT | Supported |
| Block comments `/* */` | Supported |
| Bare function calls | Supported |

#### 3.2.2 LLM Provider Interface (`jersey/service/`)

```java
public interface LlmProvider {
    void streamChat(messages, maxTokens, tokenCallback);
    String complete(messages, maxTokens);
    String autocomplete(messages, maxTokens);  // editor inline-completion
    String completeWithContext(PromptContext, maxTokens);
    void streamChatWithContext(PromptContext, maxTokens, tokenCallback);
    boolean isAvailable();
    String name();
}
```

**Implementations:**

| Provider | Class | Model | Selection |
|----------|-------|-------|-----------|
| Fusion AI Spectra | `FusionAiProvider` | Llama 405B or GPT-5 Mini | Default (no LLM_PROVIDER env) |
| OpenAI | `OpenAiProvider` | GPT-5.4 (chat) / GPT-5.4-mini (autocomplete) | `LLM_PROVIDER=openai` |

#### 3.2.3 PromptContext (Structured Prompt)

```java
public record PromptContext(
    String systemPrompt,       // FF language reference (~730 lines, from DB)
    String userPrompt,         // user's natural language request
    String formulaType,        // e.g. "WFM Time Calculation Rules"
    String referenceFormula,   // template body or RAG results
    String editorCode,         // current Monaco editor content
    String additionalRules,    // per-template prompt overlay
    String chatHistory,        // prior conversation turns
    String promptCode          // Spectra prompt code override (nullable)
)
```

FusionAiProvider sends each field as a named property to the Spectra template.
OpenAiProvider flattens them into a system + user message pair.

#### 3.2.4 Spectra Prompt Configuration

Two prompt codes in `hr_gen_ai_prompts_seed_b`:

| prompt_code | Model | Provider | UI Label |
|---|---|---|---|
| `HCM_FF_GENERATION_LLM405B` | meta.llama-3.1-405b-instruct | OCI_META | Llama 405B (default) |
| `HCM_FF_GENERATION_GPT5MINI` | openai.gpt-5-mini | OCI_ON_DEMAND | GPT-5 Mini |

**Model Parameters** (shared):

| Parameter | Value | Rationale |
|---|---|---|
| temperature | 0.1 | Code generation needs determinism |
| maxTokens | 10240 | Formulas can be long |
| topP | 0.9 | Slightly narrowed sampling |
| topK | 0 | Disabled — topP alone controls |
| frequencyPenalty | 0 | Repeated keywords are normal in formulas |
| presencePenalty | 0 | Formula structure naturally repeats tokens |

#### 3.2.5 Template Service (`jersey/service/TemplateService.java`)

CRUD operations on `FF_FORMULA_TEMPLATES` + `FF_FORMULA_TEMPLATES_TL` (Oracle DB).

| Method | Purpose |
|--------|---------|
| `listByFormulaType()` | Templates for a formula type (active only by default) |
| `listDistinctFormulaTypes()` | Distinct formula types that have templates |
| `findByTemplateCode()` | Lookup by TEMPLATE_CODE business key |
| `findSystemPrompts()` | Active rows with SYSTEMPROMPT_FLAG=Y, ordered by SORT_ORDER |
| `create() / update() / delete()` | CRUD for Manage Templates UI |
| `searchFormulas()` | Search FF_FORMULAS_VL for formula lookup |

#### 3.2.6 Validator Service

Three-layer validation (same as Python version):

| Layer | Check | Severity |
|-------|-------|----------|
| Syntax | Parser errors | error |
| Semantic | Undeclared variables, unassigned outputs | error/warning |
| Rules | Missing RETURN, business logic | warning |

#### 3.2.7 Simulator Service

AST interpreter with:
- Variable environment (dict)
- ReturnSignal exception for control flow
- Infinite loop protection (max iterations)
- Execution trace recording
- Built-in function support (numeric, string, date, conversion)

### 3.3 Database Schema

#### FF_FORMULA_TEMPLATES (Base — 20 columns)

| Column | Type | Key | Description |
|--------|------|-----|-------------|
| TEMPLATE_ID | NUMBER(18) | PK | Surrogate key |
| FORMULA_TYPE_ID | NUMBER(18) | FK | FK to FF_FORMULA_TYPES (NULL = Custom) |
| TEMPLATE_CODE | VARCHAR2(150) | UK | Business key |
| FORMULA_TEXT | CLOB | | Fast Formula source code |
| ADDITIONAL_PROMPT_TEXT | CLOB | | Per-template AI prompt overlay |
| SOURCE_TYPE | VARCHAR2(30) | | SEEDED or USER_CREATED |
| SYSTEMPROMPT_FLAG | VARCHAR2(1) | | Y = this row is the AI system prompt |
| ACTIVE_FLAG | VARCHAR2(1) | | Y = visible in UI |
| SEMANTIC_FLAG | VARCHAR2(1) | | Y = participates in RAG search |
| SORT_ORDER | NUMBER(9) | | Display order within formula type |
| + WHO columns, ENTERPRISE_ID, SEED_DATA_SOURCE, MODULE_ID, ORA_SEED_SET1/2 |

#### FF_FORMULA_TEMPLATES_TL (Translation — 15 columns)

| Column | Type | Description |
|--------|------|-------------|
| TEMPLATE_ID | NUMBER(18) | FK to base table |
| LANGUAGE | VARCHAR2(4) | Language code |
| NAME | VARCHAR2(240) | Translated display name (translateFlag=Y) |
| DESCRIPTION | VARCHAR2(4000) | Translated description (translateFlag=Y) |
| SOURCE_LANG | VARCHAR2(4) | Original language |
| + WHO columns, ENTERPRISE_ID, SEED_DATA_SOURCE, ORA_SEED_SET1/2 |

### 3.4 API Design

| Method | Endpoint | Request | Response | Transport |
|--------|----------|---------|----------|-----------|
| POST | `/chat` | `{message, formula_type, editor_code, session_id, template_code, prompt_code}` | SSE stream | text/event-stream |
| POST | `/chat/sync` | same as /chat | `{text, session_id}` | JSON |
| POST | `/complete` | `{code, cursor_line}` | `{suggestions[]}` | JSON |
| POST | `/explain` | `{code}` | SSE stream | text/event-stream |
| POST | `/validate` | `{code}` | `{valid, diagnostics[]}` | JSON |
| POST | `/simulate` | `{code, input_data}` | `{status, output_data, trace, error}` | JSON |
| GET | `/formulas` | — | `[{id, name, ...}]` | JSON |
| POST | `/formulas` | `{name, code, ...}` | `{id, name}` | JSON |
| GET | `/formulas/{id}` | — | `{id, name, code, ...}` | JSON |
| PUT | `/formulas/{id}` | `{name?, code?}` | `{id, ...}` | JSON |
| GET | `/formulas/{id}/export` | — | `{id, name, code, content}` | JSON |
| GET | `/formulas/lookup` | `?formula_type=&search=&limit=&offset=` | `[{formula_id, formula_name, ...}]` | JSON |
| GET | `/formulas/lookup/{id}/text` | — | `{formula_id, formula_text}` | JSON |
| GET | `/formula-types` | `?all=true` | `[{type_name, display_name}]` | JSON |
| GET | `/templates` | `?formula_type=&include_inactive=` | `[{template_id, name, ...}]` | JSON |
| GET | `/templates/{id}` | — | `{template_id, name, code, rule, ...}` | JSON |
| POST | `/templates` | `{name, template_code, ...}` | `{template_id, ...}` | JSON |
| PUT | `/templates/{id}` | `{name?, code?, ...}` | `{template_id, ...}` | JSON |
| DELETE | `/templates/{id}` | — | `{status, template_id}` | JSON |
| POST | `/templates/generate-meta` | `{formula_text, formula_name}` | `{name, description}` | JSON |
| POST | `/templates/extract-prompt` | `{url, formula_type}` | `{prompt}` | JSON |
| GET | `/dbi` | `?module=&search=&data_type=&limit=&offset=` | `{items[], total}` | JSON |
| GET | `/dbi/modules` | — | `[module_names]` | JSON |
| GET | `/debug/llm-logs` | — | `[{timestamp, provider, ...}]` | JSON |
| GET | `/debug/llm-logs/latest` | — | `{...}` | JSON |
| DELETE | `/debug/llm-logs` | — | `{status}` | JSON |
| GET | `/health` | — | `{status}` | JSON |

**Base Path:** `/11.13.18.05/calculationEntries`

---

## 4. Frontend Design

### 4.1 Technology Stack

| Component | Technology |
|-----------|-----------|
| Framework | React 19 |
| Language | TypeScript 5.9 |
| Build Tool | Vite 5 |
| UI Library | Ant Design 6 |
| State Management | Zustand 5 |
| Code Editor | Monaco Editor 4 |

### 4.2 Layout

```
┌─────────────────────────────────────────────────────────────┐
│  Fast Formula                          [New] [Export] [Templates] │
├──────────────────────────────────┬──────────────────────────┤
│                                  │  Validate | Explain       │
│  Monaco Editor                   │                          │
│  (fast-formula language)         │  VALID  No issues found. │
│                                  │                          │
├──────────────────────────────────┤                          │
│  ▼ CONVERSATION  2 messages      │                          │
│  [drag to resize]                │                          │
│  Chat history (collapsible)      │                          │
├──────────────────────────────────┤                          │
│  TYPE [Custom Formula ▼]         │                          │
│  START WITH [template... ▼]      │                          │
│  MODEL [Llama 405B ▼]           │                          │
├──────────────────────────────────┤                          │
│  [Describe what you need...]  [>]│                    [ENV ▼]│
└──────────────────────────────────┴──────────────────────────┘
```

### 4.3 State Management (Zustand)

| Store | State | Purpose |
|-------|-------|---------|
| `editorStore` | code, diagnostics, isValid, isDirty, formulaType | Editor state + validation |
| `chatStore` | sessionId, messages[], isStreaming | Chat conversation |
| `simulationStore` | inputData, outputData, trace, status | Simulation I/O |
| `serverStore` | servers[], selectedIndex, current | Multi-server switching (Fusion / Local) |

### 4.4 Context Selectors

| Selector | Source | Behavior |
|----------|--------|----------|
| **Type** | `GET /formula-types` | 123 formula types, searchable, A-Z sorted |
| **Start with** | `GET /templates?formula_type=X` | DB-backed templates per formula type |
| **Model** | Static options | Fusion: dropdown (Llama 405B / GPT-5 Mini). Local: static "GPT 5.4" |

Model selector only sends `prompt_code` in the request body for Fusion environments (detected by `current.auth` presence).

### 4.5 Server Switching

| Server | baseUrl | Auth | LLM |
|--------|---------|------|-----|
| Payroll VP DEV | `/fusion-proxy` | Basic auth | Fusion AI Spectra |
| Local Dev (Grizzly) | `http://<ip>:8000` | None | OpenAI GPT-5.4 |

---

## 5. Seed Data

### 5.1 FF_FORMULA_TEMPLATES Seed

**Bulk SQL:** `FastFormulaServiceAM_FormulaTemplatesSeed.sql`

- AM: `FastFormulaServiceAM`
- Tables: `FF_FORMULA_TEMPLATES` + `FF_FORMULA_TEMPLATES_TL`
- Business key: `TEMPLATE_CODE` (MERGE ON condition)
- TL join: `TEMPLATE_CODE` + `ENTERPRISE_ID` to resolve TEMPLATE_ID across schemas
- Protection: `WHERE last_updated_by IN ('SEED_DATA_FROM_APPLICATION', '0')` — does not overwrite user-modified rows
- ID generation: `S_ROW_ID_SEQ.NEXTVAL` on INSERT

### 5.2 Spectra Prompt Seed

Prompt codes in `hr_gen_ai_prompts_seed_b`:

| prompt_code | model_name | model_provider | label |
|---|---|---|---|
| HCM_FF_GENERATION_LLM405B | meta.llama-3.1-405b-instruct | OCI_META | HCM Fast Formula AI Generator (Llama 405B) |
| HCM_FF_GENERATION_GPT5MINI | openai.gpt-5-mini | OCI_ON_DEMAND | HCM Fast Formula AI Generator (GPT-5 Mini) |

Both share the same `prompt_tmpl` with 7 placeholders: `{systemPrompt}`, `{userPrompt}`, `{formulaType}`, `{referenceFormula}`, `{editorCode}`, `{additionalRules}`, `{chatHistory}`.

---

## 6. Testing

### 6.1 Java Test Suite (113 tests)

| Module | Tests | Covers |
|--------|-------|--------|
| `FFParserTest` | 3 | Basic parsing |
| `FFParserExtendedTest` | 20 | Full grammar coverage |
| `ValidatorTest` | 19 | 3-layer validation |
| `SimulatorTest` | 10 | Arithmetic, branching, trace |
| `InterpreterTest` | 5 | AST interpretation |
| `AiServiceTest` | 18 | Prompt building, provider selection |
| `ApiTest` | 13 | REST endpoints |
| `SessionFixesTest` | 16 | Multi-turn session management |
| `AstNodesTest` | 3 | AST node construction |
| `FormulaServiceTest` | 3 | Formula CRUD |
| `FormulaTypesServiceTest` | 2 | Formula type listing |
| `HealthResourceTest` | 1 | Health endpoint |

```bash
cd java && mvn test        # all 113 tests
mvn test -Dtest=ValidatorTest  # single class
```

---

## 7. Deployment

### 7.1 Fusion Environment

Deployed as part of the HCM Payroll REST service on WebLogic. No separate deployment — the Jersey resource is registered via `JerseyConfig` in the existing `HcmFastFormulaRestModel` application module.

**Prerequisites:**
- `ORA_FAI_SDK_ENABLE_SPECTRA_ROUTE = Y` (profile option)
- `AdfHcmFaiGenAiSdk.jar` on classpath
- Prompt codes registered in `hr_gen_ai_prompts_seed_b`
- System prompt rows in `FF_FORMULA_TEMPLATES`

### 7.2 Local Development

```bash
export LLM_PROVIDER=openai
export OPENAI_API_KEY=sk-...
export FF_DB_URL='jdbc:oracle:thin:@//host:port/service'
export FF_DB_USER='fusion'
export FF_DB_PASSWORD='fusion'

cd java && mvn compile exec:java    # Backend at port 8000
cd frontend && npm run dev -- --host 0.0.0.0  # Frontend at port 5173
```

### 7.3 Linux Server (Oracle Linux / csh)

```bash
rsync -avh --progress --exclude 'target/' \
    java/ user@host:/scratch/user/ff_time/java/

rsync -avh --progress --exclude '.git/' --exclude 'node_modules/' \
    frontend/ user@host:/scratch/user/ff_time/frontend/
```

---

## 8. Security

| Concern | Measure |
|---------|---------|
| API Keys | Environment variables, never in source |
| Code Injection | AST interpretation, never `eval()` |
| SQL Injection | Parameterized queries via PreparedStatement |
| Prompt Injection | `<user_request>` tag with DATA ONLY declaration |
| CORS | CorsFilter configured for frontend origin |
| Seed Data Protection | MERGE only overwrites `SEED_DATA_FROM_APPLICATION` rows |

---

## 9. External Integration

### 9.1 AI Agent Studio

Time & Labor team uptakes via AI Agent Studio, calling `/chat/sync` as a tool.

See: [ff-ai-generator-uptake.md](ff-ai-generator-uptake.md)

### 9.2 Supported Formula Types (T&L)

| Type | Prefix |
|------|--------|
| WFM Time Calculation Rules | `ORA_WFM_TCR_*_AP` |
| WFM Time Entry Rules | `ORA_WFM_TER_*_AP` |
| WFM Time Submission Rules | `ORA_WFM_TSR_*_AP` |
| WFM Time Compliance Rules | `ORA_WFM_WCR_*_AP` |
| WFM Time Device Rules | `ORA_WFM_TDR_*` |

---

## 10. Known Limitations

1. **Chat session is in-memory** — server restart loses conversation history
2. **No authentication** — relies on Fusion REST infrastructure for auth
3. **RAG is DB-query based** — no vector embeddings in the Java backend (uses SQL LIKE search on FF_FORMULAS_VL)
4. **Spectra SDK loaded via reflection** — FusionAiProvider compiles without the SDK jar; ClassNotFoundException at runtime means the jar is missing
5. **System prompt from DB** — if no active SYSTEMPROMPT_FLAG=Y row exists, the LLM gets no domain knowledge and returns generic responses
6. **CLOB handling** — FORMULA_TEXT and ADDITIONAL_PROMPT_TEXT are CLOBs; bulk seed SQL handles them but large templates may hit Oracle MERGE limitations
