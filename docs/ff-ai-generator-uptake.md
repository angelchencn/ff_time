# HCM Fast Formula AI Generator — Uptake Guide

## Overview

The Fast Formula AI Generator provides REST endpoints that accept a natural language requirement and return complete, syntactically valid Oracle Fast Formula source code. The backend routes through the **Fusion AI Agent Studio** workflow engine (`FAIOrchestratorAgentClientV2`), which handles LLM selection, prompt rendering, and conversation management.

The default Agent Studio workflow code is `HCM_FF_GENERATOR`.

---

## Endpoints

| Endpoint | Method | Response | Behavior |
|---|---|---|---|
| `POST /chat/sync` | POST | JSON | **Recommended** -- blocking, waits for LLM to complete, returns full response |
| `POST /chat` | POST | JSON | Async -- submits job, returns `jobId` immediately |
| `GET /chat/status/{jobId}` | GET | JSON | Poll async job status |
| `POST /chat/stream` | POST | SSE | True streaming via Agent Studio `invokeStream` |

**Base URL (Fusion):**

```
https://<pod>/hcmRestApi/redwood/11.13.18.05/calculationEntries
```

---

## 1. POST `/chat/sync` (Recommended)

Blocking endpoint. Submits to Agent Studio, polls internally until complete, returns the full formula code.

### Request Body

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `message` | string | **Yes** | -- | Natural language requirement. e.g. "Generate an overtime pay formula that calculates 1.5x rate for hours over 40" |
| `formula_type` | string | No | `"TIME_LABOR"` | Formula type name. Determines DBIs, contexts, inputs, and RETURN variables. See [Formula Types](#formula-types) |
| `editor_code` | string | No | `""` | Existing formula code to modify/refine. When provided, the LLM edits this code instead of generating from scratch |
| `session_id` | string | No | auto-generated | Session ID for multi-turn conversation. Reuse the value from the first response to continue refining |
| `template_code` | string | No | -- | Business key from `FF_FORMULA_TEMPLATES.TEMPLATE_CODE`. Server fetches the template's formula body and additional rules from DB |
| `llm` | string | No | `"GPT5MINI"` | LLM model selector. Available: `"GPT5MINI"` (GPT-5 Mini), `"GPT41MINI"` (GPT-4.1 Mini). Passed as the `LLM` workflow variable in Agent Studio |

### Response

**Success (HTTP 200):**

```json
{
  "text": "/******************************************************************************\n * Formula Name : ...\n/* End Formula Text */",
  "session_id": "8fcaf9bc-a176-4238-8fcc-cfa98d0c191f"
}
```

**Error (HTTP 500):**

```json
{
  "error": "Error: Agent Studio job failed: user can not execute this workflow.",
  "session_id": "8fcaf9bc-a176-4238-8fcc-cfa98d0c191f"
}
```

### Example: Simple Request

```json
{
  "message": "Generate a time calculation rule formula that calculates overtime at 1.5x for hours exceeding 40 per week",
  "formula_type": "WFM Time Calculation Rules"
}
```

### Example: With Template Reference

```json
{
  "message": "Generate a flow schedule formula that returns the next scheduled date every 2 weeks",
  "formula_type": "Flow Schedule",
  "template_code": "ORA_HCM_FF_FLOW_SCHEDULE_838595"
}
```

### Example: Multi-turn Refinement

```json
// Turn 1
{
  "message": "Generate an element skip formula that skips processing when employee is on leave",
  "formula_type": "Element Skip"
}

// Turn 2 -- pass back session_id from Turn 1
{
  "message": "Add a check for unpaid leave type as well",
  "formula_type": "Element Skip",
  "session_id": "8fcaf9bc-a176-4238-8fcc-cfa98d0c191f"
}
```

### Example: Select LLM Model

```json
{
  "message": "Generate an overtime formula",
  "formula_type": "Oracle Payroll",
  "llm": "GPT41MINI"
}
```

---

## 2. POST `/chat` (Async)

Submits the job to Agent Studio and returns immediately with a `jobId`. The client polls `/chat/status/{jobId}` for the result.

### Request Body

Same fields as `/chat/sync`.

### Response (HTTP 200)

```json
{
  "jobId": "8c266ccc-983f-408d-9bda-0c3a81a23820",
  "session_id": "8fcaf9bc-a176-4238-8fcc-cfa98d0c191f",
  "status": "SUBMITTED"
}
```

---

## 3. GET `/chat/status/{jobId}` (Poll)

Poll the status of an async job submitted via `POST /chat`.

### Response

**Running:**

```json
{
  "status": "RUNNING",
  "jobId": "8c266ccc-983f-408d-9bda-0c3a81a23820"
}
```

**Complete:**

```json
{
  "status": "COMPLETE",
  "jobId": "8c266ccc-983f-408d-9bda-0c3a81a23820",
  "text": "/******************************************************************************\n * Formula Name : ...\n/* End Formula Text */",
  "conversationId": "484B044033C78457E0639C71060AC075_..."
}
```

**Error:**

```json
{
  "status": "ERROR",
  "jobId": "8c266ccc-983f-408d-9bda-0c3a81a23820",
  "error": "user can not execute this workflow."
}
```

### Polling Pattern

```
1. POST /chat -> {"jobId": "abc-123", "status": "SUBMITTED"}
2. GET /chat/status/abc-123 -> {"status": "RUNNING"}
   ... (poll every 2 seconds)
3. GET /chat/status/abc-123 -> {"status": "COMPLETE", "text": "..."}
```

---

## 4. POST `/chat/stream` (SSE Streaming)

True token-by-token streaming via Agent Studio's `invokeStream` endpoint.

### Request Body

Same fields as `/chat/sync`.

### SSE Frame Sequence

| Order | Frame | Description |
|---|---|---|
| 1 | `data: {"session_id":"<uuid>"}` | Session ID for multi-turn |
| 2..N | `data: {"text":"<token>"}` | Incremental tokens -- append to build response |
| Optional | `data: {"replace":"<full_code>"}` | Post-processed replacement (if DEFAULT types were fixed) |
| Last | `data: [DONE]` | End of stream |

---

## LLM Model Selection

| Value | Model | Notes |
|---|---|---|
| `GPT5MINI` | GPT-5 Mini (Oracle Premium) | **Default** |
| `GPT41MINI` | GPT-4.1 Mini (Oracle Premium) | Alternative |

The `llm` field is passed as the `LLM` workflow variable in Agent Studio. The workflow's Switch node routes to the corresponding LLM Node.

---

## Formula Types

Common formula types for Time & Labor:

| Formula Type | Prefix | RETURN Variables |
|---|---|---|
| `WFM Time Calculation Rules` | `ORA_WFM_TCR_*_AP` | As defined by time calculation rule |
| `WFM Time Entry Rules` | `ORA_WFM_TER_*_AP` | As defined by time entry validation |
| `WFM Time Submission Rules` | `ORA_WFM_TSR_*_AP` | As defined by submit rule |
| `WFM Time Compliance Rules` | `ORA_WFM_WCR_*_AP` | As defined by compliance rule |
| `WFM Time Device Rules` | `ORA_WFM_TDR_*` | As defined by device rule |
| `Oracle Payroll` | varies | Depends on element classification |
| `Element Skip` | varies | `skip_flag` ('Y'/'N') |
| `Flow Schedule` | varies | `NEXT_SCHEDULED_DATE` |
| `Custom` | user-defined | user-defined |

Full list: 123 formula types available via `GET /formula-types`.

---

## Agent Studio Workflow

### Workflow Code

`HCM_FF_GENERATOR`

### Workflow Variables

| Variable | Scope | Description |
|---|---|---|
| `SystemPrompt` | Conversation | Full Fast Formula language specification (~730 lines, 18 sections) |
| `FormulaType` | Conversation | Formula type name |
| `ReferenceFormula` | Conversation | Template body or RAG-retrieved examples |
| `AdditionalRules` | Conversation | Per-template prompt overlay |
| `LLM` | Conversation | LLM model selector (GPT5MINI / GPT41MINI) |
| `EditorCode` | User Question | Current editor code (per-turn, may change) |

### First Turn vs Subsequent Turns

| Turn | Parameters Sent | conversationId |
|---|---|---|
| First | ALL variables (SystemPrompt, FormulaType, ReferenceFormula, AdditionalRules, EditorCode, LLM) | empty (new conversation) |
| Subsequent | EditorCode only (Conversation-scoped variables retained by Agent Studio) | reused from first response |

### Workflow Structure

```
Start
  |
  v
Choose LLM (Switch on $variables.LLM)
  |
  +-- GPT5MINI --> LLM Node (GPT-5 Mini, Oracle Premium)
  |
  +-- GPT41MINI --> LLM Node (GPT-4.1 Mini, Oracle Premium)
  |
  v
End
```

### LLM Node Prompt Template

The LLM Node prompt references workflow variables via `{{$context.$variables.*}}` and system context via `{{$context.$system.*}}`:

```
<role>
You are an Oracle HCM Fast Formula code generator...
</role>
<rules>
{{$context.$variables.SystemPrompt}}
</rules>
<formula_type>{{$context.$variables.FormulaType}}</formula_type>
<reference_formula>{{$context.$variables.ReferenceFormula}}</reference_formula>
<current_editor_code>{{$context.$variables.EditorCode}}</current_editor_code>
<additional_rules>{{$context.$variables.AdditionalRules}}</additional_rules>
<chat_history>{{$context.$system.$chatHistory}}</chat_history>
<user_request>{{$context.$system.$inputMessage}}</user_request>

Task: Generate a complete Oracle Fast Formula based on <rules> and <formula_type>...
```

### LLM Node Properties

| Parameter | Value |
|---|---|
| Temperature | 0.1 |
| Max Tokens | 10240 |
| Top P | 0.9 |
| Top K | 0 |
| Verbosity | LOW |
| Reasoning Effort | HIGH |

---

## Error Handling

| HTTP Status | Scenario |
|---|---|
| **200** | Success -- `text` field contains formula code |
| **400** | Provider doesn't support async (e.g. OpenAI provider for `/chat` async endpoint) |
| **500** | Agent Studio error -- `error` field contains details (e.g. permission denied, timeout, empty response) |

Error responses always include an `error` field with the error message.

---

## Authentication

Standard Fusion REST authentication. The AI Agent Studio SDK (`FAIOrchestratorAgentClientV2`) handles OAuth token acquisition and host resolution via TopologyManager internally.

---

## Architecture

```
Client (UI / AI Agent Studio / REST)
  |
  v
POST /chat/sync (or /chat, /chat/stream)
  |
  v
FastFormulaResource (Jersey)
  +-- TemplateService.findByTemplateCode()  --> FF_FORMULA_TEMPLATES (DB)
  +-- ChatSessionStore (in-memory history)
  |
  v
AiService.chatOnce() / submitAsync() / streamChat()
  +-- getSystemPrompt()                     --> FF_FORMULA_TEMPLATES (SYSTEMPROMPT_FLAG=Y)
  +-- extractReferenceFormula()             --> RagService or template body
  +-- buildPromptContext()                  --> PromptContext (message, systemPrompt,
  |                                             formulaType, referenceFormula, editorCode,
  |                                             additionalRules, chatHistory, llm)
  |
  v
AgentStudioProvider (default)
  +-- FAIOrchestratorAgentClientV2 (SDK, reflection)
  +-- invokeAgentAsyncAsUser() -> POST /agent/v2/HCM_FF_GENERATOR/invokeAsync
  +-- getAgentRequestStatusAsUser() -> GET /agent/v2/HCM_FF_GENERATOR/status/{jobId}
  +-- parameters: {SystemPrompt, FormulaType, ReferenceFormula,
  |                AdditionalRules, EditorCode, LLM}
  |
  v
Agent Studio Workflow (HCM_FF_GENERATOR)
  +-- Switch on LLM variable
  +-- GPT5MINI node or GPT41MINI node
  |
  v
LLM (GPT-5 Mini / GPT-4.1 Mini)
  |
  v
Response: generated Fast Formula source code
```

### Provider Selection (LLM_PROVIDER env var)

| Value | Provider | Use Case |
|---|---|---|
| unset (default) | **AgentStudioProvider** | Fusion -- Agent Studio workflow |
| `spectra` | FusionAiProvider | Fusion -- direct Spectra completions |
| `openai` | OpenAiProvider | Local dev -- GPT-5.4 |

---

## Dependencies

| Component | Requirement |
|---|---|
| FAI SDK | `AdfHcmFaiGenAiSdk.jar` on classpath (for `FAIOrchestratorAgentClientV2`) |
| Agent Studio Workflow | `HCM_FF_GENERATOR` workflow published in Agent Studio |
| System Prompt | At least one active row in `FF_FORMULA_TEMPLATES` with `SYSTEMPROMPT_FLAG='Y'` and `ACTIVE_FLAG='Y'` |
| Agent Studio Security | Calling user must have permission to execute the workflow |

---

## Contact

Fast Formula AI Generator team -- HCM Payroll, Formulas Core
