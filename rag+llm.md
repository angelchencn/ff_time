# RAG + LLM Architecture — FF Time Formula Generation

## Overview

FF Time uses Retrieval-Augmented Generation (RAG) to generate Oracle HCM Fast Formulas. The system combines a vector database of ~39,000 real Oracle formulas with Claude LLM to produce production-grade formula code.

```
User Request → RAG Retrieval → Prompt Assembly → Claude API → Streaming Response → Editor
```

---

## End-to-End Flow

### Step 1: User Input (Frontend)

```
┌─────────────────────────────────────────────────────────────┐
│  User selects:                                              │
│    • Formula Type: "Time Calculation Rule"                  │
│    • Sample: "Daily overtime threshold..."   (optional)     │
│                                                             │
│  User types: "Calculate overtime for hours > 40 at 1.5x"   │
│                                                             │
│  Frontend sends POST /api/chat:                             │
│  {                                                          │
│    "message": "Calculate overtime for hours > 40 at 1.5x",│
│    "formula_type": "WORKFORCE_MANAGEMENT_TIME_CALC...",     │
│    "code": "<current editor content>",                      │
│    "session_id": "session-1711878400000"                    │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
```

### Step 2: Backend API Route (`/api/chat`)

```python
# app/api/chat.py
@router.post("/api/chat")
async def chat(req: ChatRequest):
    # 1. Load conversation history from SQLite
    session = db.get(ChatSession, session_id)
    history = session.messages  # Previous turns

    # 2. Call AI Service (streaming)
    for chunk in ai.stream_generate(
        message=req.message,
        formula_type=req.formula_type,
        history=history,
        current_code=req.code
    ):
        yield {"data": json.dumps({"text": chunk})}  # SSE

    # 3. Save conversation to database
    session.messages = [...history, user_msg, assistant_msg]
```

### Step 3: RAG Retrieval (ChromaDB)

```
┌─ ChromaDB Vector Database ──────────────────────────────────┐
│                                                              │
│  Collection: "fast_formulas" (38,951 documents)              │
│  Embedding Model: all-MiniLM-L6-v2 (384 dimensions)         │
│  Distance Metric: cosine similarity (HNSW index)            │
│                                                              │
│  Query Flow:                                                 │
│                                                              │
│  1. EMBED: User message → 384-dim vector                    │
│     "Calculate overtime for hours > 40"                      │
│         ↓ SentenceTransformer                                │
│     [0.12, -0.34, 0.08, ..., 0.56]                          │
│                                                              │
│  2. SEARCH: Find top-3 nearest vectors                      │
│     HNSW approximate nearest neighbor search                 │
│     across 38,951 pre-computed formula embeddings            │
│                                                              │
│  3. FILTER: Discard results with similarity < 0.6           │
│                                                              │
│  4. RETURN: Top-3 formula source code + metadata            │
│     ┌─────────────────────────────────┬────────────┐        │
│     │ Formula                         │ Similarity │        │
│     ├─────────────────────────────────┼────────────┤        │
│     │ overtime_pay.ff                 │ 0.82       │        │
│     │ double_time_overtime.ff         │ 0.78       │        │
│     │ weekly_hours_cap.ff             │ 0.71       │        │
│     └─────────────────────────────────┴────────────┘        │
│                                                              │
│  Each result contains:                                       │
│  {                                                           │
│    "id": "oracle_overtime_pay",                              │
│    "code": "DEFAULT FOR HOURS IS 0\n...\nRETURN ...",       │
│    "metadata": {                                             │
│      "formula_type": "Oracle Payroll",                       │
│      "formula_name": "OVERTIME_PAY",                         │
│      "source": "oracle_db"                                   │
│    },                                                        │
│    "similarity": 0.82                                        │
│  }                                                           │
└──────────────────────────────────────────────────────────────┘
```

### Step 4: Prompt Assembly

The AI Service assembles the final prompt from multiple sources:

```
┌─ SYSTEM PROMPT (~3,000 tokens) ─────────────────────────────┐
│                                                              │
│  You are an expert assistant for Oracle HCM Fast Formula...  │
│                                                              │
│  ## Fast Formula Syntax Rules                                │
│  - Every formula begins with DEFAULT statements...           │
│  - Comments start with /* and end with */                    │
│                                                              │
│  ## Supported Keywords                                       │
│  DEFAULT, IF, THEN, ELSE, RETURN, WHILE, LOOP...            │
│                                                              │
│  ## Supported Built-in Functions                             │
│  ADD_MONTHS, CEIL, FLOOR, ROUND, GET_CONTEXT...             │
│                                                              │
│  ## Formula Structure Convention                             │
│  ### 1. Header Comment Block (REQUIRED)                      │
│  ### 2. Formula Naming Convention                            │
│  ### 3. Formula Body Order (REQUIRED)                        │
│  ### 4. End Marker                                           │
│                                                              │
│  ## Output Format                                            │
│  - Return only valid Fast Formula code...                    │
│  - ALWAYS include the header comment block                   │
└──────────────────────────────────────────────────────────────┘

┌─ USER PROMPT (assembled dynamically) ───────────────────────┐
│                                                              │
│  ## Relevant Example Formulas        ← FROM RAG (3 formulas)│
│                                                              │
│  /* use_case: overtime */                                    │
│  DEFAULT FOR HOURS_WORKED IS 0                               │
│  DEFAULT FOR OVERTIME_THRESHOLD IS 40                        │
│  INPUTS ARE HOURS_WORKED                                     │
│  IF HOURS_WORKED > OVERTIME_THRESHOLD THEN                   │
│    OVERTIME_HOURS = HOURS_WORKED - OVERTIME_THRESHOLD         │
│    OVERTIME_PAY = OVERTIME_HOURS * RATE * 1.5                │
│  ...                                                         │
│  RETURN OVERTIME_PAY                                         │
│                                                              │
│  (+ 2 more similar formulas)                                 │
│                                                              │
│  ## Current Formula in Editor        ← FROM FRONTEND         │
│  ```                                                         │
│  (whatever code is currently in Monaco Editor)               │
│  ```                                                         │
│                                                              │
│  ## Formula Type Template            ← FROM TEMPLATES        │
│  **Type:** Time Calculation Rule                             │
│  **Naming:** ORA_WFM_TCR_<DESCRIPTION>_AP                   │
│  ### Skeleton                                                │
│  ```                                                         │
│  /***************...                                         │
│  * Formula Name : {formula_name}                             │
│  * Formula Type : Time Calculation Rule                      │
│  ...                                                         │
│  DEFAULT FOR measure IS EMPTY_NUMBER_NUMBER                  │
│  INPUTS ARE measure, StartTime, StopTime                     │
│  ...                                                         │
│  RETURN measure                                              │
│  ```                                                         │
│                                                              │
│  ## Request                                                  │
│  Formula type: WORKFORCE_MANAGEMENT_TIME_CALCULATION_RULES   │
│  Requirement: Calculate overtime for hours > 40 at 1.5x     │
│                                                              │
│  Generate a complete Fast Formula that satisfies...          │
└──────────────────────────────────────────────────────────────┘
```

### Step 5: Claude API Call

```
┌─ Anthropic Messages API ────────────────────────────────────┐
│                                                              │
│  Model: claude-sonnet-4-20250514                             │
│  Max Tokens: 4,096                                           │
│  Stream: true                                                │
│                                                              │
│  messages = [                                                │
│    { role: "user", content: "..." },      ← turn 1          │
│    { role: "assistant", content: "..." }, ← turn 1 response  │
│    ...                                    ← conversation     │
│    { role: "user", content: "<assembled prompt>" } ← current │
│  ]                                                           │
│                                                              │
│  system = SYSTEM_PROMPT                                      │
│                                                              │
│  ┌─ Token Budget Breakdown ───────────────────────────────┐ │
│  │                                                         │ │
│  │  Component                        Approx Tokens        │ │
│  │  ─────────────────────────────    ────────────          │ │
│  │  System Prompt                    ~3,000                │ │
│  │  RAG Examples (3 formulas)        ~2,000 - 6,000       │ │
│  │  Current Editor Code              0 - 2,000            │ │
│  │  Formula Type Template/Skeleton   ~800 - 1,200         │ │
│  │  Conversation History             0 - 4,000            │ │
│  │  User Request Text                ~50 - 200            │ │
│  │  ─────────────────────────────    ────────────          │ │
│  │  TOTAL INPUT                      ~6,000 - 15,000      │ │
│  │  OUTPUT (generated formula)       ~500 - 4,000         │ │
│  │  ─────────────────────────────    ────────────          │ │
│  │  TOTAL per request                ~7,000 - 19,000      │ │
│  │                                                         │ │
│  │  Well within Sonnet's 200K context window               │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### Step 6: Streaming Response

```
┌─ SSE Stream ────────────────────────────────────────────────┐
│                                                              │
│  Backend (FastAPI + sse-starlette):                          │
│    for chunk in claude_stream.text_stream:                   │
│      yield {"data": json.dumps({"text": chunk})}            │
│                                                              │
│  Frontend (fetch-based SSE client):                          │
│    1. Each chunk → appendToLast() → Chat bubble updates     │
│    2. On complete → extractCodeBlocks() from response       │
│    3. Code block → setCode() → Monaco Editor auto-fills     │
│    4. Editor triggers validation → Parser → Diagnostics     │
│                                                              │
│  Database:                                                   │
│    Conversation saved to SQLite ChatSession table            │
│    for multi-turn context in subsequent requests             │
└──────────────────────────────────────────────────────────────┘
```

---

## RAG Database Details

### Data Source

| Source | Count | Description |
|--------|-------|-------------|
| Oracle Database Import | ~38,951 | Real production formulas from `FF_FORMULAS_VL` table |
| Local Samples | 6 | Curated `.ff` files in `data/samples/` |
| **Total** | **~38,957** | |

### Import Pipeline

```
Oracle DB (FF_FORMULAS_VL + FF_FORMULA_TYPES)
    │
    │  python -m app.scripts.import_from_oracle
    │  - oracledb thin mode (no Oracle Client needed)
    │  - fetch_lobs = False for performance
    │  - prefetchrows = 1000, arraysize = 1000
    │
    ▼
RAGEngine.add_formula(doc_id, code, metadata)
    │
    │  For each formula:
    │  1. Embed code text → 384-dim vector (all-MiniLM-L6-v2)
    │  2. Store in ChromaDB collection "fast_formulas"
    │  3. Metadata: formula_name, formula_type, source, description
    │
    ▼
ChromaDB Persistent Storage (data/chroma/)
    - HNSW vector index for fast ANN search
    - Cosine distance metric
    - ~39K vectors × 384 dimensions
```

### Embedding Model

| Property | Value |
|----------|-------|
| Model | `all-MiniLM-L6-v2` (via `sentence-transformers`) |
| Dimensions | 384 |
| Max Sequence | 256 tokens |
| Speed | ~14,000 sentences/sec (CPU) |
| Size | ~80 MB |

### Query Performance

| Metric | Value |
|--------|-------|
| Query latency | ~50-100ms (including embedding) |
| Index type | HNSW (Hierarchical Navigable Small World) |
| Similarity metric | Cosine |
| Top-K default | 3 (for chat), 2 (for completion) |
| Min similarity threshold | 0.6 |

---

## What Each Component Contributes

### RAG (ChromaDB) — "What similar formulas exist?"

- **Input**: User's natural language request
- **Output**: Top-3 most semantically similar real Oracle formulas
- **Purpose**: Provides concrete, working examples as few-shot context
- **Does NOT**: Generate new code, understand grammar rules, or filter by type

### Formula Templates — "What structure should this type follow?"

- **Input**: Selected formula type (e.g., Time Calculation Rule)
- **Output**: Skeleton code, naming convention, example pattern
- **Purpose**: Enforces Oracle's structural conventions per formula type
- **Does NOT**: Provide real formula examples or business logic

### System Prompt — "What are the language rules?"

- **Input**: Static (always the same)
- **Output**: Syntax rules, keywords, functions, formatting requirements
- **Purpose**: Teaches the LLM Fast Formula syntax and output format
- **Does NOT**: Provide examples or type-specific structure

### LLM (Claude Sonnet) — "Generate the formula"

- **Input**: System prompt + RAG examples + template + user request + editor code + history
- **Output**: Complete Fast Formula code (streaming)
- **Purpose**: Synthesizes all context into working, production-grade formula code
- **Does NOT**: Store formulas, retrieve examples, or validate syntax

### Conversation History (SQLite) — "What did we discuss before?"

- **Input**: Session ID
- **Output**: Previous user/assistant message pairs
- **Purpose**: Enables multi-turn refinement ("change the threshold to 45 hours")
- **Does NOT**: Affect RAG retrieval or template selection

---

## Diagram: Data Flow

```
                    ┌──────────────┐
                    │   Frontend   │
                    │  (React +    │
                    │   Monaco)    │
                    └──────┬───────┘
                           │ POST /api/chat
                           │ { message, formula_type, code, session_id }
                           ▼
                    ┌──────────────┐
                    │   FastAPI    │
                    │  /api/chat   │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
     ┌──────────────┐ ┌────────┐ ┌──────────┐
     │   SQLite     │ │  RAG   │ │ Template │
     │  ChatSession │ │ Engine │ │ Service  │
     │   (history)  │ │        │ │          │
     └──────┬───────┘ └───┬────┘ └────┬─────┘
            │             │           │
            │             ▼           │
            │    ┌──────────────┐     │
            │    │   ChromaDB   │     │
            │    │  38,951 docs │     │
            │    │  MiniLM-L6   │     │
            │    └──────┬───────┘     │
            │           │             │
            │    top-3 formulas       │
            │           │     skeleton + naming
            ▼           ▼             ▼
         ┌──────────────────────────────────┐
         │     Prompt Assembly              │
         │                                  │
         │  system_prompt                   │
         │  + history     (from SQLite)     │
         │  + RAG results (from ChromaDB)   │
         │  + template    (from Templates)  │
         │  + user request                  │
         │  + editor code                   │
         │  ≈ 6,000 - 15,000 tokens        │
         └──────────────┬───────────────────┘
                        │
                        ▼
              ┌──────────────────┐
              │   Claude API     │
              │   (Sonnet 4)     │
              │                  │
              │  model: sonnet   │
              │  max_tokens: 4096│
              │  stream: true    │
              └────────┬─────────┘
                       │ SSE chunks
                       ▼
              ┌──────────────────┐
              │   Frontend       │
              │                  │
              │  Chat bubble     │
              │  → Code extract  │
              │  → Monaco Editor │
              │  → Auto-validate │
              └──────────────────┘
```

---

## Cost Estimation

| Endpoint | Model | Input Tokens | Output Tokens | Approx Cost |
|----------|-------|-------------|--------------|-------------|
| `/api/chat` | Sonnet 4 | ~6K-15K | ~500-4K | ~$0.03-0.10 |
| `/api/complete` | Haiku 4.5 | ~2K-5K | ~50-500 | ~$0.001-0.003 |
| `/api/explain` | Sonnet 4 | ~2K-5K | ~500-2K | ~$0.02-0.04 |

*Prices based on Anthropic's published API pricing for Sonnet 4 ($3/$15 per MTok) and Haiku 4.5 ($0.80/$4 per MTok).*

---

## Potential Optimizations

1. **RAG filter by formula_type**: Add `filter_metadata={"formula_type": selected_type}` to ChromaDB query so retrieved examples match the user's selected type.

2. **Prompt caching**: The system prompt and template skeletons are static per request type — Anthropic's prompt caching could reduce input costs by ~90% for repeated requests.

3. **Chunked embeddings**: Long formulas (>256 tokens) get truncated by MiniLM-L6. Could chunk long formulas and store multiple vectors per formula for better retrieval.

4. **Re-ranking**: Add a cross-encoder re-ranking step after initial vector search to improve retrieval precision.

5. **Haiku for simple requests**: Route simple "explain" or "fix" requests to Haiku instead of Sonnet for 10x cost savings.
