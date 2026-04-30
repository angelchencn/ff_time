# HCM Fast Formula AI Generator — Uptake Guide

## Overview

The Fast Formula AI Generator provides REST endpoints that accept a natural language requirement and return complete, syntactically valid Oracle Fast Formula source code. The backend routes through the **Fusion AI Agent Studio** workflow engine (`FAIOrchestratorAgentClientV2`), which handles LLM selection, prompt rendering, and conversation management.

The default Agent Studio workflow code is `HCM_FF_GENERATOR`.

---

## Quick Start for Agent Studio Workflow Authors

The Fast Formulas Generator is pre-registered as a **Supported Business Object** in AI Agent Studio -- no manual tool wiring required. Workflow authors can drop it into a workflow directly.

### Registered Business Object

| Attribute | Value |
|---|---|
| BO Name | **Fast Formula Generator Business Object** |
| Display Name | Fast Formulas Generator |
| Object Code | `ORA_HCM_FF_FASTFORMULAGENERATORBUSINESSOBJECT` |
| Object Source | `HCM_SEARCH` |
| Family / Product | `HCM` / `GLOBAL_PAYROLL` |
| REST Base Path | `/hcmRestApi/redwood/11.13.18.05/fastFormulaAssistants` |
| Seed Source | `seeddata/SupportedObjectSD.xml` (shipped via `FaiAgentSDAM`) |
| Authentication | Native Fusion (OAuth via TopologyManager) |

### Available Tools (auto-registered)

| Tool | Operation | Resource Path | Purpose |
|---|---|---|---|
| `generateFormulaSync` | POST | `/chat/sync` | **Recommended** -- blocking, returns full formula |
| `generateFormulaAsync` | POST | `/chat` | Async submit, returns `jobId` |
| `pollJobStatus` | GET | `/chat/status/{pJob_Id}` | Poll async job status |
| `listTemplates` | GET | `/templates` | List formula templates |
| `listFormulaTypes` | GET | `/formula-types` | List all formula types |
| `validateFormula` | POST | `/validate` | 3-layer syntax/semantic/rules validation |
| `healthCheck` | GET | `/health` | Health probe |

### Tool Parameters (sync/async generate)

| Parameter | Required | Description |
|---|---|---|
| `pMessage` | Yes | Natural language requirement |
| `pTemplate_code` | Yes | Business key from `FF_FORMULA_TEMPLATES.TEMPLATE_CODE` (e.g. `ORA_HCM_FF_TER_MIN_HOURS_001`) |
| `pLlm` | No | `GPT5MINI` (default) or `GPT41MINI` |
| `pEditor_code` | No | Current editor code for refinement |
| `pSession_id` | No | Agent Studio `conversationId` for multi-turn |

### How to Use from a Workflow

1. In Agent Studio **Workflow Designer**, add a Tool node.
2. Select **Business Object** -> search "Fast Formula Generator Business Object".
3. Pick the tool (e.g. `generateFormulaSync`).
4. Map workflow variables to tool parameters (`pMessage`, `pTemplate_code`, `pLlm`, `pEditor_code`, `pSession_id`).
5. The tool invokes the REST endpoint with native authentication -- no OAuth setup required.

Sample query embedded in the seed data:

```json
{
  "message": "Generate a time entry rule formula that validates minimum hours",
  "template_code": "ORA_HCM_FF_TER_MIN_HOURS_001",
  "llm": "GPT5MINI"
}
```

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
https://<pod>/hcmRestApi/redwood/11.13.18.05/fastFormulaAssistants
```

---

## 1. POST `/chat/sync` (Recommended)

Blocking endpoint. Submits to Agent Studio, polls internally until complete, returns the full formula code.

### Request Body

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `message` | string | **Yes** | -- | Natural language requirement. e.g. "Generate an overtime pay formula that calculates 1.5x rate for hours over 40" |
| `template_code` | string | **Yes** | -- | Business key from `FF_FORMULA_TEMPLATES.TEMPLATE_CODE`. Server fetches the template's formula body, additional rules, formula type (from FK), and `USE_SYSTEM_PROMPT_FLAG` from DB. Always required -- even on multi-turn follow-ups |
| `editor_code` | string | No | `""` | Existing formula code to modify/refine. When provided, the LLM edits this code instead of generating from scratch |
| `session_id` | string | No | auto-generated | Session ID (Agent Studio conversationId) for multi-turn conversation. Reuse the value from the first response to continue refining |
| `llm` | string | No | `"GPT5MINI"` | LLM model selector. Available: `"GPT5MINI"` (GPT-5 Mini), `"GPT41MINI"` (GPT-4.1 Mini). Passed as the `LLM` workflow variable in Agent Studio |

Note: `formula_type` is **not** in the request body. It is derived server-side from the template's `FORMULA_TYPE_ID` FK in `FF_FORMULA_TYPES`. NULL FK defaults to "Custom".

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
  "template_code": "ORA_HCM_FF_TCR_OVERTIME_001"
}
```

### Example: With LLM Selection

```json
{
  "message": "Generate a flow schedule formula that returns the next scheduled date every 2 weeks",
  "template_code": "ORA_HCM_FF_FLOW_SCHEDULE_838595",
  "llm": "GPT41MINI"
}
```

### Example: Multi-turn Refinement

```json
// Turn 1
{
  "message": "Generate an element skip formula that skips processing when employee is on leave",
  "template_code": "ORA_HCM_FF_ELEMENT_SKIP_001"
}

// Turn 2 -- pass back session_id (conversationId) from Turn 1
{
  "message": "Add a check for unpaid leave type as well",
  "template_code": "ORA_HCM_FF_ELEMENT_SKIP_001",
  "session_id": "484B044033C78457E0639C71060AC075_..."
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

## Formula Template Seed Data

`FF_FORMULA_TEMPLATES` rows are source controlled through Fusion seed data, not by
ad hoc SQL in target pods. The seed file owns the template body, per-template AI
instructions, display metadata, and system-prompt rows used by the generator.

### Seed File

Use the standard FND seed data loader XML format for the `FormulaTemplates` VO.
The sample file is:

```
FormulaTemplatesSD.xml
```

The file should be checked in under the owning Fusion seed-data module path
for HCM Fast Formula templates. The sample header uses:

```
fusionapps/hcm/hrc/db/data/HRC/HcmCommonTop/HcmCommonFf/FormulaTemplatesSD.xml
```

### Loader Metadata

The seed file must point to the Fast Formula service AM and `FormulaTemplates`
VO:

```xml
<SEEDDATA
  am="oracle.apps.hcm.formulas.core.fastFormulaService.applicationModule.FastFormulaServiceAM"
  vo="FormulaTemplates"
  translatable="true"
  effective_dated="false"
  xmlns="http://www.oracle.com/apps/fnd/applseed">
```

The adxml apply action should invoke the FND seed loader with the same AM:

```xml
<!-- adxml:
<args>-am oracle.apps.hcm.formulas.core.fastFormulaService.applicationModule.FastFormulaServiceAM
      -dburl jdbc:oracle:#jdbc_protocol_fusion#:@#jdbc_db_addr#
      -dbuser #un:fusion#
      -file #fullpath:~PROD:~PATH:~FILE#
      -cfver #last_applied_version#</args>
-->
```

### Row Identity and Conflict Detection

`TEMPLATE_CODE` is the stable business key for source control. Use it as the
seed row key and never change it after a template is referenced by Agent Studio
or a client request.

```xml
<FormulaTemplates rowkey="ORA_HCM_FF_LANGUAGE_REFERENCE" rowver="0">
  <TemplateCode>ORA_HCM_FF_LANGUAGE_REFERENCE</TemplateCode>
  ...
</FormulaTemplates>
```

Conflict detection should protect both the base row and the translated row from
overwriting customer-modified content. The sample checks:

| Check | Table | Key | Purpose |
|---|---|---|---|
| `base_customization` | `FF_FORMULA_TEMPLATES` | `TEMPLATE_CODE` | Do not overwrite a base row if `LAST_UPDATED_BY` is not seed-owned |
| `translation_customization` | `FF_FORMULA_TEMPLATES_TL` | `TEMPLATE_CODE` + current language | Do not overwrite translated `NAME` or `DESCRIPTION` if customer-modified |
| `FormulaTemplatesAltKey` | `FF_FORMULA_TEMPLATES` | `TEMPLATE_CODE` | Detect duplicate business-key rows |

Seed-owned rows should use `LAST_UPDATED_BY` / `CREATED_BY` values of
`SEED_DATA_FROM_APPLICATION` or `0`, matching the conflict checks.

### Important Attributes

| XML Attribute | Database / Runtime Meaning |
|---|---|
| `TemplateCode` | Business key. This is the value clients pass as `template_code` to `/chat/sync`, `/chat`, and `/chat/stream`. |
| `FormulaText` | Template formula body, or the system prompt text when `SystempromptFlag='Y'`. |
| `AdditionalPromptText` | Optional template-specific guidance appended to the prompt. Use `isNull="true"` when absent. |
| `SourceType` | `SEEDED` for Oracle-delivered rows. |
| `SystempromptFlag` | `Y` means this row contributes to the global system prompt loaded by `AiService.getSystemPrompt()`. Normal starter templates should use `N`. |
| `UseSystempromptFlag` | `Y` means generation with this template includes system prompt rows. `N` allows a lightweight template-specific prompt. |
| `ActiveFlag` | `Y` makes the row visible to template APIs and the editor; `N` hides it without deleting the seed row. |
| `SemanticFlag` | `Y` means the row can participate in semantic/RAG usage; `N` excludes it from semantic retrieval. |
| `SortOrder` | Display and prompt ordering. System prompt rows are concatenated by `SORT_ORDER`, then `TEMPLATE_ID`. |
| `ModuleId` | Required seed ownership partition. Use the owning module's taxonomy module id. |
| `Name` / `Description` | Translatable display strings stored through `FF_FORMULA_TEMPLATES_TL`. |
| `BaseFormulaTypeName` | Formula type lookup/display helper. Use `isNull="true"` for system-prompt rows and Custom templates. |

### Minimal Seed Row Example

```xml
<FormulaTemplates rowkey="ORA_HCM_FF_TER_MIN_HOURS_001" rowver="0">
  <TemplateCode>ORA_HCM_FF_TER_MIN_HOURS_001</TemplateCode>
  <FormulaText>/* Fast Formula starter body goes here */</FormulaText>
  <AdditionalPromptText>Generate a time entry rule that validates minimum reported hours.</AdditionalPromptText>
  <SourceType>SEEDED</SourceType>
  <SystempromptFlag>N</SystempromptFlag>
  <ActiveFlag>Y</ActiveFlag>
  <SemanticFlag>Y</SemanticFlag>
  <UseSystempromptFlag>Y</UseSystempromptFlag>
  <SortOrder>100</SortOrder>
  <ModuleId>61ECAF4AAAC2E990E040449821C61C97</ModuleId>
  <Name>Minimum Hours Time Entry Rule</Name>
  <Description>Starter template for a WFM Time Entry Rule that validates minimum hours.</Description>
  <CreatedBy>SEED_DATA_FROM_APPLICATION</CreatedBy>
  <CreationDate>2026-04-25 00:00:00</CreationDate>
  <LastUpdateDate>2026-04-25 00:00:00</LastUpdateDate>
  <LastUpdatedBy>SEED_DATA_FROM_APPLICATION</LastUpdatedBy>
  <LastUpdateLogin>-1</LastUpdateLogin>
  <EnterpriseId>0</EnterpriseId>
  <BaseFormulaTypeName>WFM Time Entry Rules</BaseFormulaTypeName>
</FormulaTemplates>
```

For system prompt rows, set `SystempromptFlag` to `Y`, set `SemanticFlag` to
`N`, keep `ActiveFlag` as `Y`, and put the prompt section in `FormulaText`.
Multiple active system prompt rows are allowed; the service concatenates them in
seeded sort order.

### Apply and Verify

After the seed file is applied by the Fusion seed-data loader, verify the row is
available through the REST API:

```bash
curl -u <user>:<password> \
  "https://<pod>/hcmRestApi/redwood/11.13.18.05/fastFormulaAssistants/templates?formula_type=WFM%20Time%20Entry%20Rules"
```

Then invoke generation using the seeded `TemplateCode`:

```json
{
  "message": "Generate a time entry rule that errors when reported hours are less than 4",
  "template_code": "ORA_HCM_FF_TER_MIN_HOURS_001",
  "llm": "GPT5MINI"
}
```

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
| First | ALL variables (SystemPrompt, FormulaType, ReferenceFormula, AdditionalRules, EditorCode, LLM). SystemPrompt is empty if template has `USE_SYSTEM_PROMPT_FLAG='N'`. FormulaType is derived from template FK. | empty (new conversation) |
| Subsequent | EditorCode only (Conversation-scoped variables retained by Agent Studio). template_code is always re-sent for server-side lookup. | reused from first response |

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
| Template Config | Each template needs `USE_SYSTEM_PROMPT_FLAG` ('Y' = include system prompt, 'N' = skip). Column added to `FF_FORMULA_TEMPLATES` with default 'Y' |
| Formula Template Seed Data | `FormulaTemplatesSD.xml` applied through the FND seed data loader for `FastFormulaServiceAM` / `FormulaTemplates` |
| Agent Studio Security | Calling user must have permission to execute the workflow |

---

## Contact

Fast Formula AI Generator team -- HCM Payroll, Formulas Core
