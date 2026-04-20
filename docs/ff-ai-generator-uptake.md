# HCM Fast Formula AI Generator — Uptake Guide for AI Agent Studio

## Overview

The Fast Formula AI Generator exposes two REST endpoints that accept a natural language requirement and return complete, syntactically valid Oracle Fast Formula source code. The endpoints are hosted on the **HCM Payroll REST service** (`calculationEntries`) and route through the Fusion AI Spectra completions pipeline.

**AI Agent Studio integration**: Configure your agent to call `/chat/sync` (recommended) as a tool/action. The agent sends the user's request as the `message` field and receives the generated formula code in the response.

---

## Endpoints

| Endpoint | Method | Content-Type | Response | Use Case |
|---|---|---|---|---|
| `/chat/sync` | POST | `application/json` | `application/json` | **Recommended for Agent Studio** — blocking, returns full response as JSON |
| `/chat` | POST | `application/json` | `text/event-stream` | Streaming SSE — for UIs that need token-by-token display |

**Base URL (Fusion):**

```
https://<pod>/hcmRestApi/redwood/11.13.18.05/calculationEntries
```

---

## 1. POST `/chat/sync` (Recommended)

Blocking endpoint. Sends request, waits for the LLM to complete, returns full formula code in one JSON response.

### Request Body

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `message` | string | **Yes** | — | Natural language requirement. e.g. "Generate an overtime pay formula that calculates 1.5x rate for hours over 40" |
| `formula_type` | string | No | `"TIME_LABOR"` | Formula type name. Determines which DBIs, contexts, inputs, and RETURN variables are valid. See [Formula Types](#formula-types) below |
| `editor_code` | string | No | `""` | Existing formula code to modify/refine. When provided, the LLM edits this code instead of generating from scratch |
| `session_id` | string | No | auto-generated | Session ID for multi-turn conversation. Reuse the value from the first response to continue refining |
| `template_code` | string | No | — | Business key from `FF_FORMULA_TEMPLATES.TEMPLATE_CODE`. Server fetches the template's formula body and additional rules from DB. e.g. `"ORA_HCM_FF_FLOW_SCHEDULE_838595"` |
| `prompt_code` | string | No | `"HCM_FF_GENERATION_LLM405B"` | Spectra prompt code selecting the LLM backend. Available: `"HCM_FF_GENERATION_LLM405B"` (Llama 405B), `"HCM_FF_GENERATION_GPT5MINI"` (GPT-5 Mini) |

### Response Body

| Field | Type | Description |
|---|---|---|
| `text` | string | Complete generated Fast Formula source code |
| `session_id` | string | Session ID — pass back in subsequent requests for multi-turn conversation |

### Example: Simple Request

```json
// POST /chat/sync
{
  "message": "Generate a time calculation rule formula that calculates overtime at 1.5x for hours exceeding 40 per week",
  "formula_type": "WFM Time Calculation Rules"
}
```

```json
// Response
{
  "text": "/******************************************************************************\n * Formula Name : ORA_WFM_TCR_WEEKLY_OVERTIME_1_5X_AP\n * Formula Type : WFM Time Calculation Rules\n * Description  : Calculates overtime at 1.5x rate for hours exceeding 40 per week.\n ...\n/* End Formula Text */",
  "session_id": "8fcaf9bc-a176-4238-8fcc-cfa98d0c191f"
}
```

### Example: With Template Reference

```json
// POST /chat/sync
{
  "message": "Generate a flow schedule formula that returns the next scheduled date",
  "formula_type": "Custom",
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

// Turn 2 — pass back session_id from Turn 1
{
  "message": "Add a check for unpaid leave type as well",
  "formula_type": "Element Skip",
  "session_id": "8fcaf9bc-a176-4238-8fcc-cfa98d0c191f"
}
```

### Example: Modify Existing Code

```json
{
  "message": "Add PAY_INTERNAL_LOG_WRITE at entry and exit",
  "formula_type": "Oracle Payroll",
  "editor_code": "/* existing formula code here */\nDEFAULT FOR hours IS 0\nINPUTS ARE hours\nl_pay = hours * 25\nRETURN l_pay"
}
```

---

## 2. POST `/chat` (Streaming SSE)

Same request body as `/chat/sync`. Response is a stream of Server-Sent Events.

### SSE Frame Sequence

| Order | Frame | Description |
|---|---|---|
| 1 | `data: {"session_id":"<uuid>"}` | Session ID for multi-turn |
| 2..N | `data: {"text":"<token>"}` | Streamed tokens — append to build response |
| Optional | `data: {"replace":"<full_code>"}` | Post-processed replacement (if DEFAULT types were fixed) |
| Last | `data: [DONE]` | End of stream |

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
| `Custom` | user-defined | user-defined |

Full list: 123 formula types available via `GET /formula-types`.

---

## prompt_code Selection

| prompt_code | LLM Backend | Notes |
|---|---|---|
| `HCM_FF_GENERATION_LLM405B` | Llama 3.1 405B (OCI_META) | **Default** — highest quality, slower |
| `HCM_FF_GENERATION_GPT5MINI` | GPT-5 Mini (OCI_ON_DEMAND) | Faster, lower cost |

---

## AI Agent Studio Configuration

### Tool Definition

```yaml
name: generate_fast_formula
description: Generate Oracle HCM Fast Formula source code from natural language requirements
endpoint: POST /hcmRestApi/redwood/11.13.18.05/calculationEntries/chat/sync
input_schema:
  message: string (required) - what the formula should do
  formula_type: string (optional) - e.g. "WFM Time Calculation Rules"
  template_code: string (optional) - reference template from FF_FORMULA_TEMPLATES
  prompt_code: string (optional) - LLM model selection
output_schema:
  text: string - the generated formula code
  session_id: string - for follow-up refinements
```

### Agent Prompt Guidance

When configuring the agent's system prompt, include:

```
When the user asks to create or generate a Fast Formula:
1. Identify the formula type from the user's request (default: "WFM Time Calculation Rules" for Time & Labor)
2. Call generate_fast_formula with the user's requirement as "message" and the identified "formula_type"
3. Return the generated formula code from the "text" field
4. If the user wants to refine the formula, pass the "session_id" from the previous response
```

---

## Authentication

Standard Fusion REST authentication — the AI Agent Studio handles this automatically when the agent is deployed within the Fusion environment. No additional auth configuration needed.

---

## Error Handling

| Scenario | Response |
|---|---|
| LLM provider unavailable | `{"text": "Error: <provider> is not available.", "session_id": "..."}` |
| Spectra routing disabled | `{"text": "Error: ORA_FAI_SDK_ENABLE_SPECTRA_ROUTE is not enabled...", "session_id": "..."}` |
| Empty response from LLM | `{"text": "Error: Fusion AI Spectra returned empty response...", "session_id": "..."}` |

Errors are returned inline in the `text` field (HTTP 200) — the agent should check if `text` starts with `"Error:"` and handle accordingly.

---

## Dependencies

| Component | Requirement |
|---|---|
| Profile Option | `ORA_FAI_SDK_ENABLE_SPECTRA_ROUTE = Y` |
| Spectra Prompt | `HCM_FF_GENERATION_LLM405B` and/or `HCM_FF_GENERATION_GPT5MINI` registered in `hr_gen_ai_prompts_seed_b` |
| System Prompt | At least one active row in `FF_FORMULA_TEMPLATES` with `SYSTEMPROMPT_FLAG='Y'` and `ACTIVE_FLAG='Y'` |
| FAI SDK | `AdfHcmFaiGenAiSdk.jar` on classpath |

---

## Architecture

```
AI Agent Studio
  |
  v
POST /chat/sync (JSON)
  |
  v
FastFormulaResource (Jersey)
  |
  +-- TemplateService.findByTemplateCode()  --> FF_FORMULA_TEMPLATES (DB)
  +-- ChatSessionStore (in-memory history)
  |
  v
AiService.chatOnce()
  +-- getSystemPrompt()                     --> FF_FORMULA_TEMPLATES (SYSTEMPROMPT_FLAG=Y)
  +-- extractReferenceFormula()             --> RagService (vector search) or template body
  +-- buildPromptContext()                  --> PromptContext (7 named fields + promptCode)
  |
  v
FusionAiProvider.completeWithContext()
  +-- FAICompletionsClient (Spectra SDK, reflection)
  +-- promptCode: HCM_FF_GENERATION_LLM405B or HCM_FF_GENERATION_GPT5MINI
  +-- properties: {systemPrompt, userPrompt, formulaType, referenceFormula,
  |                editorCode, additionalRules, chatHistory}
  |
  v
FAI Spectra Orchestrator --> LLM (Llama 405B / GPT-5 Mini)
  |
  v
Response: generated Fast Formula source code
```

---

## Contact

Fast Formula AI Generator team — HCM Payroll, Formulas Core
