# Technical Design Document -- FF Time

## 1. Overview

**Product Name:** FF Time -- AI-Based Fast Formula Generator for HCM

**Purpose:** A web application that enables Oracle HCM consultants to generate, validate, simulate, and manage Fast Formula code using AI with a built-in hand-written parser, interpreter, and RAG knowledge base.

**Target Users:**
- HR/Payroll Business Consultants -- generate formulas via natural language
- Oracle HCM Technical Consultants -- write/edit code with AI-assisted completion and validation
- AI Agent Studio consumers -- programmatic formula generation via REST API

**Two Backend Implementations:**

| Backend | Stack | LLM | Status |
|---------|-------|-----|--------|
| Java/Jersey (`java/`) | Grizzly HTTP, hand-written parser, JDBC | Agent Studio (GPT-5 Mini / GPT-4.1 Mini), Spectra, or OpenAI GPT-5.4 | **Primary** |
| Python/FastAPI (`backend/`) | Lark parser, SQLAlchemy, ChromaDB | Claude API | Legacy |

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
+----------------------------------------------------------+
|                   React Frontend                          |
|   Monaco Editor + Chat + Simulation + Templates Panel     |
|   (TypeScript, Vite, Ant Design, Zustand)                 |
|   Model Selector: GPT-5 Mini / GPT-4.1 Mini (Fusion)     |
|                   GPT 5.4 (Local Dev)                     |
+------------------------+---------------------------------+
                         | REST / SSE
                         v
+----------------------------------------------------------+
|              Java/Jersey Backend (Grizzly)                 |
|  +------------+------------+------------+--------------+  |
|  | Validator   | Simulator  | AI Service | Template     |  |
|  | (3-layer)   | (AST       | (Agent     | Service      |  |
|  |             |  Interp.)  |  Studio /  | (DB CRUD)    |  |
|  |             |            |  Spectra / |              |  |
|  |             |            |  OpenAI)   |              |  |
|  +------+------+------+-----+------+-----+------+------+  |
|         |             |            |            |          |
|  +------+-------------+--+  +-----+------+  +--+-------+  |
|  |  Hand-written Parser   |  | LlmProvider|  | RAG      |  |
|  |  Tokenizer -> FFParser |  | (interface)|  | Service  |  |
|  |  -> AST -> Interpreter |  |            |  | (JDBC)   |  |
|  +------------------------+  +-----+------+  +---------+  |
|                                    |                       |
|              +---------------------+-----+                 |
|              |          |                |                  |
|       +------+------+  +------+------+  +------+-------+  |
|       |AgentStudio  |  |FusionAi    |  |OpenAi        |  |
|       |Provider     |  |Provider    |  |Provider      |  |
|       |(SDK v2)     |  |(Spectra)   |  |(GPT-5.4 API) |  |
|       +-------------+  +-----------+  +--------------+  |
|                                                           |
|  +------------------------------------------------------+ |
|  |  Oracle DB (JDBC / ADF BC)                            | |
|  |  FF_FORMULA_TEMPLATES, FF_FORMULA_TEMPLATES_TL,       | |
|  |  FF_FORMULA_TYPES, FF_FORMULAS_VL,                    | |
|  |  PAY_ACTION_LOGS, PAY_ACTION_LOG_LINES (debug log)    | |
|  +------------------------------------------------------+ |
+----------------------------------------------------------+
```

### 2.2 Provider Selection

```
Environment variable: LLM_PROVIDER

  LLM_PROVIDER unset   ->  AgentStudioProvider (DEFAULT)
  (default)                FAIOrchestratorAgentClientV2 SDK
                           Agent Studio workflow: HCM_FF_GENERATOR

  LLM_PROVIDER=spectra ->  FusionAiProvider (Spectra direct)
                           FAICompletionsClient SDK

  LLM_PROVIDER=openai  ->  OpenAiProvider (local dev)
                           GPT-5.4 via OpenAI API
```

Fallback chain: AgentStudioProvider -> FusionAiProvider -> OpenAiProvider

### 2.3 Request Flow -- Formula Generation (Agent Studio)

```
User: "Write overtime formula for Time Calculation Rules"
  |
  v
Frontend: POST /chat/sync {message, formula_type, llm?, template_code?}
  |
  v
FastFormulaResource: extract request fields
  |-- Resolve template_code -> TemplateService.findByTemplateCode() -> DB
  |-- Agent Studio mode: skip ChatSessionStore, use conversationId
  |
  v
AiService.chatOnce()
  |-- getSystemPrompt() -> FF_FORMULA_TEMPLATES (SYSTEMPROMPT_FLAG=Y)
  |-- extractReferenceFormula() -> template body OR RagService
  |-- buildPromptContext() -> PromptContext (8 fields)
  |
  v
AgentStudioProvider.completeWithContext()
  |-- buildAgentRequest() -> AgentRequestV2 (first turn: all params; subsequent: EditorCode only)
  |-- invokeAgentAsyncAsUser() -> POST /agent/v2/HCM_FF_GENERATOR/invokeAsync
  |-- pollForCompletion() -> GET /agent/v2/HCM_FF_GENERATOR/status/{jobId}
  |-- storeConversationId() -> ThreadLocal + ConcurrentHashMap
  |
  v
AiService: fixDefaultTypes() + LlmDebugDBLog.record() -> PAY_ACTION_LOGS
  |
  v
FastFormulaResource: return conversationId as session_id
  |
  v
Response: {"text": "<formula code>", "session_id": "<conversationId>"}
```

---

## 3. Backend Design (Java)

### 3.1 Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| HTTP Server | Grizzly + Jersey | REST API |
| Language | Java 17+ | Backend runtime |
| Parser | Hand-written (Tokenizer -> FFParser) | Fast Formula -> AST |
| Interpreter | Tree-walking | Simulation |
| LLM (Default) | FAIOrchestratorAgentClientV2 (reflection) | Agent Studio: GPT-5 Mini / GPT-4.1 Mini |
| LLM (Spectra) | FAICompletionsClient (reflection) | Direct Spectra completions |
| LLM (Local) | OpenAI API (java.net.http) | GPT-5.4 |
| Database | Oracle DB (JDBC) | Templates, formula types, formulas, debug logs |
| Session | Agent Studio conversationId (Fusion) / in-memory (Local) | Chat history |
| Debug Log | PAY_ACTION_LOGS + PAY_ACTION_LOG_LINES (DB) | Cross-server persistent debug |

### 3.2 Module Design

#### 3.2.1 Parser Module (`jersey/parser/`)

| File | Responsibility |
|------|---------------|
| `Tokenizer.java` | Lexer: source code -> token stream |
| `FFParser.java` | Recursive descent parser: tokens -> AST |
| `AstNodes.java` | Immutable AST node types (sealed interfaces / records) |
| `Interpreter.java` | Tree-walking interpreter for simulation |

**Grammar Coverage:** All Oracle Fast Formula constructs supported including DEFAULT, INPUTS ARE, IF/ELSIF/ELSE, WHILE/LOOP, RETURN, WAS DEFAULTED, LIKE, string concat, typed strings, quoted identifiers, arrays, CALL_FORMULA, CHANGE_CONTEXTS, EXECUTE, block comments, bare function calls.

#### 3.2.2 LLM Provider Interface (`jersey/service/`)

```java
public interface LlmProvider {
    void streamChat(messages, maxTokens, tokenCallback);
    String complete(messages, maxTokens);
    String autocomplete(messages, maxTokens);
    String completeWithContext(PromptContext, maxTokens);
    void streamChatWithContext(PromptContext, maxTokens, tokenCallback);
    String submitAsync(PromptContext);       // async job submission
    String getJobStatus(String jobId);       // poll async job
    String getLastConversationId();          // Agent Studio conversationId
    boolean isAvailable();
    String name();
}
```

**Implementations:**

| Provider | Class | Model | Selection |
|----------|-------|-------|-----------|
| **Agent Studio** | `AgentStudioProvider` | GPT-5 Mini / GPT-4.1 Mini | **Default** (no LLM_PROVIDER env) |
| Spectra | `FusionAiProvider` | Configurable via prompt_code | `LLM_PROVIDER=spectra` |
| OpenAI | `OpenAiProvider` | GPT-5.4 (chat) / GPT-5.4-mini (autocomplete) | `LLM_PROVIDER=openai` |

#### 3.2.3 AgentStudioProvider

Uses `FAIOrchestratorAgentClientV2` SDK (loaded via reflection) to call the Agent Studio workflow `HCM_FF_GENERATOR`.

**Key behaviors:**
- First turn: passes ALL variables (SystemPrompt, FormulaType, ReferenceFormula, AdditionalRules, EditorCode, LLM)
- Subsequent turns: passes only EditorCode (Conversation-scoped variables retained by Agent Studio)
- Manages `conversationId` via ThreadLocal + ConcurrentHashMap for multi-turn conversations
- `invokeAsync` + `pollForCompletion` pattern (2s interval, 5 min max timeout)
- Unwraps `InvocationTargetException` for accurate error messages from reflection calls

#### 3.2.4 PromptContext (Structured Prompt)

```java
public record PromptContext(
    String systemPrompt,       // FF language reference (~730 lines, from DB)
    String message,            // user's natural language request
    String formulaType,        // e.g. "WFM Time Calculation Rules"
    String referenceFormula,   // template body or RAG results
    String editorCode,         // current Monaco editor content
    String additionalRules,    // per-template prompt overlay
    String chatHistory,        // prior turns OR Agent Studio conversationId
    String promptCode          // LLM model selector (e.g. "GPT5MINI")
)
```

#### 3.2.5 Agent Studio Workflow

Workflow code: `HCM_FF_GENERATOR`

| Variable | Scope | Description |
|---|---|---|
| `SystemPrompt` | Conversation | Full Fast Formula language specification |
| `FormulaType` | Conversation | Formula type name |
| `ReferenceFormula` | Conversation | Template body or RAG examples |
| `AdditionalRules` | Conversation | Per-template prompt overlay |
| `LLM` | Conversation | Model selector (GPT5MINI / GPT41MINI) |
| `EditorCode` | User Question | Current editor code (per-turn) |

**LLM Node Properties:**

| Parameter | Value |
|---|---|
| Temperature | 0.1 |
| Max Tokens | 10240 |
| Top P | 0.9 |
| Top K | 0 |
| Verbosity | LOW |
| Reasoning Effort | HIGH |

#### 3.2.6 Debug Logging (DB-backed)

`LlmDebugDBLog` writes to `PAY_ACTION_LOGS` (header) + `PAY_ACTION_LOG_LINES` (detail). Cross-server visible, persistent across restarts. Called from `AiService.chatOnce()` -- single entry point for all providers.

**LINE_NUMBER layout:**

| Line | Content | Truncation |
|---|---|---|
| 1 | Summary (model, endpoint, totalChars, estTokens) | No |
| 2 | MESSAGE | 3900 chars + `...` |
| 3 | SESSION_ID (conversationId) | No |
| 10 | SYSTEM_PROMPT | 3900 chars + `...` |
| 20 | FORMULA_TYPE | No |
| 30 | REFERENCE_FORMULA | 3900 chars + `...` |
| 40 | ADDITIONAL_RULES | 3900 chars + `...` |
| 50 | EDITOR_CODE | 3900 chars + `...` |
| 60 | CHAT_HISTORY | 3900 chars + `...` |
| 90 | TOKEN_BREAKDOWN (original untruncated lengths) | No |
| 100 | RESPONSE | 3900 chars + `...` |

### 3.3 API Design

| Method | Endpoint | Response | Behavior |
|--------|----------|----------|----------|
| POST | `/chat` | `{jobId, session_id, status}` | **Async** -- submit job, return immediately |
| GET | `/chat/status/{jobId}` | `{status, text, jobId}` | Poll async job status |
| POST | `/chat/sync` | `{text, session_id}` | **Blocking** -- wait for completion (HTTP 500 on error) |
| POST | `/chat/stream` | SSE stream | **Streaming** via Agent Studio invokeStream |
| POST | `/complete` | `{suggestions[]}` | Editor autocomplete |
| POST | `/explain` | SSE stream | Formula explanation |
| POST | `/validate` | `{valid, diagnostics[]}` | 3-layer validation |
| POST | `/simulate` | `{status, output_data, trace}` | AST interpreter |
| GET | `/debug/llm-logs` | `[{log_id, summary, message, ...}]` | DB debug logs (last 20) |
| GET | `/debug/llm-logs/latest` | `{log_id, ...all fields...}` | Latest log detail |
| GET | `/debug/llm-logs/{id}` | `{log_id, ...all fields...}` | Specific log detail |
| CRUD | `/templates` | `{template_id, ...}` | Template management |
| GET | `/formula-types` | `[{type_name, display_name}]` | Formula type listing |
| GET | `/formulas/lookup` | `[{formula_id, ...}]` | Formula search |
| GET | `/health` | `{status}` | Health check |

**Base Path:** `/11.13.18.05/calculationEntries`

### 3.4 Session Management

| Mode | Mechanism |
|---|---|
| **Agent Studio** (default) | `conversationId` from Agent Studio response returned as `session_id`. ChatSessionStore skipped. Agent Studio manages chat history natively. |
| **Spectra / OpenAI** | `ChatSessionStore` (in-memory ConcurrentHashMap). Local UUID as `session_id`. |

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

### 4.2 Context Selectors

| Selector | Source | Behavior |
|----------|--------|----------|
| **Type** | `GET /formula-types` | 123 formula types, searchable, A-Z sorted |
| **Start with** | `GET /templates?formula_type=X` | DB-backed templates per formula type |
| **Model** | Static options | Fusion: dropdown (GPT-5 Mini / GPT-4.1 Mini). Local: static "GPT 5.4" |

Model selector sends `llm` parameter (e.g. `"GPT5MINI"`, `"GPT41MINI"`) for Fusion environments only.

### 4.3 Server Switching

| Server | Auth | Provider |
|--------|------|----------|
| Agent Studio | tm-mfitzimmons / Welcome1 | AgentStudioProvider |
| Payroll VP DEV | hcm.user@oracle.com / Welcome1 | FusionAiProvider (spectra) |
| Local Dev (Grizzly) | None | OpenAiProvider |

### 4.4 Debug Modal

DB-backed LLM debug log viewer. Loads from `/debug/llm-logs` (persistent across ServiceServer instances).

Features:
- Log selector with message preview
- Info bar: Log ID, Formula Type, Message, Session ID, SUCCESS/ERROR badge
- Token Breakdown: shows **original untruncated** char/token counts per field
- Content tabs: System Prompt, Message, Formula Type, Reference Formula, Additional Rules, Editor Code, Chat History, Response (tab labels show real sizes from token breakdown)
- Full Request JSON tab

---

## 5. Seed Data

### 5.1 FF_FORMULA_TEMPLATES Seed

**Bulk SQL:** `FastFormulaServiceAM_FormulaTemplatesSeed.sql`

- AM: `FastFormulaServiceAM`
- Tables: `FF_FORMULA_TEMPLATES` + `FF_FORMULA_TEMPLATES_TL`
- Business key: `TEMPLATE_CODE` (MERGE ON condition)
- Protection: `WHERE last_updated_by IN ('SEED_DATA_FROM_APPLICATION', '0')`

### 5.2 Agent Studio Workflow

Workflow `HCM_FF_GENERATOR` configured in Fusion AI Agent Studio with:
- Switch node on `$variables.LLM` (GPT5MINI / GPT41MINI)
- Two LLM nodes: GPT-5 Mini (Oracle Premium), GPT-4.1 Mini (Oracle Premium)
- Prompt template using `{{$context.$variables.*}}` and `{{$context.$system.*}}`

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

---

## 7. Deployment

### 7.1 Fusion Environment

Deployed as part of the HCM Payroll REST service on WebLogic.

**Prerequisites:**
- `AdfHcmFaiGenAiSdk.jar` on classpath (for `FAIOrchestratorAgentClientV2`)
- Agent Studio workflow `HCM_FF_GENERATOR` published
- System prompt rows in `FF_FORMULA_TEMPLATES` with `SYSTEMPROMPT_FLAG='Y'`
- Calling user has Agent Studio execution permission

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
| Agent Studio Auth | FAIOrchestratorClient handles OAuth token via TopologyManager |

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

1. **Agent Studio SDK via reflection** -- AgentStudioProvider compiles without the SDK jar; ClassNotFoundException at runtime means the jar is missing. Fallback chain: AgentStudio -> Spectra -> OpenAI.
2. **No authentication** -- relies on Fusion REST infrastructure for auth
3. **RAG is DB-query based** -- no vector embeddings in the Java backend (uses SQL LIKE search on FF_FORMULAS_VL)
4. **System prompt from DB** -- if no active SYSTEMPROMPT_FLAG=Y row exists, the LLM gets no domain knowledge and returns generic responses
5. **Debug log truncation** -- large fields (system_prompt, response) truncated to 3900 chars in PAY_ACTION_LOG_LINES; token breakdown retains original lengths for accurate metrics
6. **ThreadLocal conversationId** -- not cleaned up after request; minimal impact (short string, WebLogic thread pool reuse)
7. **CLOB handling** -- FORMULA_TEXT and ADDITIONAL_PROMPT_TEXT are CLOBs; bulk seed SQL handles them but large templates may hit Oracle MERGE limitations
