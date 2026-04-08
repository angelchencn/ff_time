# Invention Disclosure: Template-Constrained Domain Code Generation and Validation

## 1. Title of Invention

**A Method and System for Template-Constrained Domain-Specific Code Generation and Multi-Layer Validation Using Large Language Models and Retrieval-Augmented Generation**

---

## 2. Inventors

- (填写发明人姓名)

---

## 3. Filing Strategy

| Item | Detail |
|------|--------|
| 建议类型 | US Provisional Patent Application |
| 费用 | $150 (micro entity) / $320 (small entity) |
| 优先日锁定 | 自申请日起 12 个月内需提交正式申请 |
| 分类 | G06F 8/30 (Code generation); G06N 3/08 (Neural networks) |

---

## 4. Technical Field

The present invention relates to automated domain-specific code generation, and more particularly to a method and system that combines retrieval-augmented generation (RAG), structural template constraints, and multi-layer validation to generate, verify, and simulate domain-specific programming code using large language models (LLMs).

---

## 5. Background / Problem Statement

Enterprise Resource Planning (ERP) systems such as Oracle HCM Cloud use domain-specific languages (DSLs) — e.g., Oracle Fast Formula — to encode business rules for payroll, time & labor, and benefits calculation. These DSLs have:

- **123+ distinct formula types**, each with unique naming conventions, structural requirements, and semantic patterns
- **Strict syntax and semantic rules** that differ from general-purpose programming languages
- **Business-rule constraints** (e.g., overtime formulas must reference HOURS_WORKED; all formulas must contain RETURN statements)

Current AI code generation tools (GitHub Copilot, Amazon CodeWhisperer) are designed for general-purpose languages and lack:

1. Formula-type-aware structural templates
2. Domain-specific multi-layer validation (syntax + semantic + business rules)
3. Executable simulation with semantic output filtering
4. Post-generation type inference based on variable naming conventions

---

## 6. Summary of the Invention

A computer-implemented method for generating domain-specific code comprising six stages:

```
┌─────────────────────────────────────────────────────┐
│  Stage 1: Template Retrieval                        │
│  User specifies formula type → load structural      │
│  template (skeleton, naming pattern, header)        │
├─────────────────────────────────────────────────────┤
│  Stage 2: RAG Retrieval                             │
│  Query vector database of ~39,000 real formulas     │
│  → retrieve top-K semantically similar samples      │
│  filtered by formula type                           │
├─────────────────────────────────────────────────────┤
│  Stage 3: Constrained Prompt Assembly               │
│  Combine template skeleton + RAG samples + user     │
│  requirement + system prompt into a structured      │
│  generation prompt with preservation constraints    │
├─────────────────────────────────────────────────────┤
│  Stage 4: LLM Generation + Post-Processing          │
│  Generate code via LLM constrained by template;    │
│  apply post-generation type inference to correct    │
│  DEFAULT declarations based on variable naming      │
├─────────────────────────────────────────────────────┤
│  Stage 5: Multi-Layer Validation                    │
│  Layer 1 — Syntax: Parse into AST via formal       │
│    grammar                                          │
│  Layer 2 — Semantic: Undeclared vars, unassigned    │
│    outputs, context function arg exclusion          │
│  Layer 3 — Business Rules: RETURN presence,         │
│    overtime naming conventions                      │
├─────────────────────────────────────────────────────┤
│  Stage 6: Executable Simulation                     │
│  Tree-walking interpreter executes AST with         │
│  user-provided inputs; semantic output filtering    │
│  returns only computed variables                    │
└─────────────────────────────────────────────────────┘
```

---

## 7. Detailed Description

### 7.1 Template Retrieval (Stage 1)

The system maintains a **Formula Type Template Registry** containing 123+ template definitions. Each template is an immutable data structure with:

| Field | Description | Example |
|-------|-------------|---------|
| `type_name` | Unique type identifier | `"WFM_TIME_CALCULATION_RULES"` |
| `display_name` | Human-readable label | `"WFM Time Calculation Rules"` |
| `naming_pattern` | Required name format | `"ORA_WFM_TCR_<DESCRIPTION>_AP"` |
| `skeleton` | Structural code template with placeholders | See below |

**Template Skeleton Example:**
```
/************************************************************
 * Formula Name: {formula_name}
 * Formula Type: WFM Time Calculation Rules
 * Description:  {{description}}
 * Change History:
 *   {date_today}  Author  Created
 ************************************************************/

DEFAULT FOR hours_worked IS 0
DEFAULT FOR rate IS 0

INPUTS ARE hours_worked, rate

/* ---- Business logic (to be generated) ---- */

l_result = hours_worked * rate

RETURN l_result
/* End Formula Text */
```

The template is **not** used as a fill-in-the-blank scaffold. Instead, it is injected into the LLM prompt as a **structural constraint**, with explicit instructions to:
- Preserve header block format
- Maintain DEFAULT → INPUTS → logic → RETURN ordering
- Follow the naming pattern
- Replace only the business logic placeholder section

### 7.2 RAG Retrieval (Stage 2)

The system uses a vector database (ChromaDB) with sentence-transformer embeddings (`all-MiniLM-L6-v2`) to store ~39,000 real domain-specific formulas imported from production Oracle databases.

**Retrieval Process:**
1. Encode user's natural language requirement as embedding vector
2. Query vector database for top-K (default: 3) semantically similar formulas
3. Return formula source code with metadata (use case, formula type)

The retrieved examples provide the LLM with **real-world patterns** that complement the structural template.

### 7.3 Constrained Prompt Assembly (Stage 3)

The prompt is assembled from multiple sections in a specific order:

```
┌─ System Prompt ──────────────────────────────────┐
│  • DSL syntax reference (keywords, operators,    │
│    built-in functions)                           │
│  • Formula naming conventions by module          │
│  • Header comment block format (REQUIRED)        │
│  • Body ordering rules                           │
│  • DEFAULT value type inference rules            │
│  • 150+ lines of domain-specific instructions    │
└──────────────────────────────────────────────────┘
┌─ User Prompt ────────────────────────────────────┐
│  Section 1: RAG Examples                         │
│    "Here are similar existing formulas for       │
│     reference: [top-K results]"                  │
│                                                  │
│  Section 2: Template Injection                   │
│    "Formula type: [display_name]                 │
│     Naming convention: [naming_pattern]          │
│     Use this skeleton as structural foundation:  │
│     [skeleton with date substitution]"           │
│                                                  │
│  Section 3: User Requirement                     │
│    "Generate a [formula_type] formula that:      │
│     [user's natural language description]"       │
└──────────────────────────────────────────────────┘
```

**Key innovation:** The template is not a code template in the traditional sense — it is a **prompt-level structural constraint** that guides LLM generation while allowing full creative flexibility in the business logic section.

### 7.4 LLM Generation + Post-Processing (Stage 4)

**Generation:** The assembled prompt is sent to a large language model (e.g., Claude Sonnet) which generates formula code constrained by the template structure and informed by RAG examples.

**Post-Processing — Variable Name Type Inference:**

After generation, a post-processing step scans all `DEFAULT FOR <var> IS <value>` declarations and applies type inference based on variable naming conventions:

| Variable Name Contains | Inferred Type | Corrected Default |
|------------------------|---------------|-------------------|
| `_NAME`, `_CODE`, `_STATUS`, `_TYPE`, `TEXT` | String | `' '` |
| `_DATE`, `_START`, `_END`, `_TIME` | Date | `'01-JAN-0001'(DATE)` |
| (other) | Numeric | `0` (unchanged) |

This corrects a common LLM error where all DEFAULT values are generated as numeric `0` regardless of the variable's semantic type.

### 7.5 Multi-Layer Validation (Stage 5)

The generated code passes through three sequential validation layers:

**Layer 1 — Syntax Validation:**
- Parses code using a formal grammar (Lark EBNF)
- Produces an Abstract Syntax Tree (AST) of immutable nodes
- Reports syntax errors with line/column positions

**Layer 2 — Semantic Validation:**
- Builds a **declared variable set** from: DEFAULT declarations, assignment LHS, DBI names, built-in variables (SYSDATE, TRUE, FALSE)
- Traverses AST to find all `VariableRef` nodes
- **Context function exclusion:** First arguments of context functions (`GET_CONTEXT`, `SET_INPUT`, `GET_OUTPUT`, etc.) are excluded from undeclared-variable checks because they are identifier names, not variable references
- Reports undeclared variable references as errors
- Checks that all declared OUTPUT variables are assigned at least once

**Layer 3 — Business Rule Validation:**
- **RETURN presence:** Recursively checks all code paths for at least one RETURN statement
- **Domain-specific conventions:** For OUTPUT variables containing overtime keywords (`OVERTIME`, `OT_PAY`, `OT_RATE`), warns if `HOURS_WORKED` is not referenced in the formula
- Reports business rule violations as warnings

**Aggregation:** All diagnostics from the three layers are merged into a single `ValidationResult` with `valid=True` only if no error-severity diagnostics exist.

### 7.6 Executable Simulation (Stage 6)

A tree-walking AST interpreter executes the validated formula with user-provided input values.

**Key Technical Features:**

1. **Semantic Variable Tracking:** The interpreter categorizes each variable as:
   - `input_key` — provided by the caller
   - `declared_input_key` — declared via `INPUT IS` / `INPUTS ARE`
   - `default_key` — declared via `DEFAULT FOR`
   - `assigned_key` — modified after declaration (computed)

2. **Output Filtering:** Only **computed variables** are returned:
   ```
   output = env \ (input_keys ∪ declared_input_keys ∪ (default_keys \ assigned_keys))
   ```
   This separates inputs from computed outputs automatically.

3. **Control Flow:** RETURN statements raise a `ReturnSignal` exception caught at the top level, enabling early exit from nested scopes.

4. **Infinite Loop Protection:** While loops are limited to 10,000 iterations.

5. **Execution Trace:** Every statement execution is logged with step number, statement type, and current variable state, enabling debugging and auditability.

6. **60+ Built-in Functions:** Numeric, string, date, conversion, context, and lookup functions are implemented natively.

---

## 8. Claims

### Independent Claims

**Claim 1 (Method):**
A computer-implemented method for generating and validating domain-specific code, comprising:
1. receiving a formula type identifier and a natural language description from a user;
2. retrieving, from a template registry, a structural template associated with said formula type, the template comprising a code skeleton defining required structural elements, a naming convention pattern, and a header format;
3. querying a vector database of domain-specific code samples using an embedding of said natural language description to retrieve semantically similar code samples;
4. assembling a constrained generation prompt incorporating said structural template as a structural constraint, said retrieved code samples as reference examples, and said natural language description as the generation objective;
5. transmitting said constrained generation prompt to a large language model to generate candidate domain-specific code;
6. applying post-generation type inference to correct variable declarations in said candidate code based on variable naming conventions;
7. validating said candidate code through a multi-layer validation pipeline comprising:
   - (a) syntax validation by parsing said candidate code into an abstract syntax tree using a formal grammar;
   - (b) semantic validation by traversing said abstract syntax tree to detect undeclared variable references and unassigned output variables, wherein arguments to context functions are excluded from undeclared variable detection; and
   - (c) business rule validation by checking domain-specific constraints including return statement presence and naming convention compliance;
8. returning said candidate code with aggregated validation diagnostics.

**Claim 2 (System):**
A system for template-constrained domain-specific code generation comprising:
- a template registry storing a plurality of structural templates indexed by formula type;
- a vector database storing embeddings of domain-specific code samples;
- a prompt assembly module configured to combine a retrieved template, retrieved code samples, and a user requirement into a constrained generation prompt;
- a large language model interface configured to generate candidate code from said prompt;
- a multi-layer validation pipeline comprising a syntax validator, a semantic analyzer, and a business rule checker; and
- an AST interpreter configured to execute validated code with user-provided inputs and return only computed output variables.

### Dependent Claims

**Claim 3:** The method of Claim 1, wherein said structural template comprises a code skeleton with placeholder sections, a formula naming pattern, and a required header comment block format, and wherein the large language model is instructed to preserve said structural elements while generating business logic in the placeholder sections.

**Claim 4:** The method of Claim 1, wherein said post-generation type inference comprises scanning variable declarations, matching variable names against keyword sets (string keywords, date keywords), and replacing default values with type-appropriate values.

**Claim 5:** The method of Claim 1, further comprising executing said validated candidate code using a tree-walking AST interpreter that:
- categorizes variables as input, declared-input, default, or assigned;
- filters output to return only computed variables by excluding input variables and unmodified default variables; and
- generates an execution trace logging each statement execution with variable state.

**Claim 6:** The method of Claim 1, wherein said template registry contains templates for 123 or more formula types, each template being an immutable data structure.

**Claim 7:** The method of Claim 1, wherein said vector database stores embeddings of 39,000 or more real domain-specific formulas and retrieval is filtered by formula type.

**Claim 8:** The method of Claim 5, wherein said AST interpreter implements infinite loop protection by limiting loop iterations to a maximum threshold and raises a simulation error upon exceeding said threshold.

---

## 9. Novelty Analysis

### 9.1 Comparison with Prior Art

| Feature | GitHub Copilot / CodeWhisperer | This Invention |
|---------|-------------------------------|----------------|
| Code generation | General-purpose languages | Domain-specific language with 123+ type-aware templates |
| Template usage | None (pure LLM generation) | Templates as prompt-level structural constraints |
| Retrieval | Code context from open files | RAG from 39,000+ real production formulas |
| Validation | Basic syntax (IDE linters) | 3-layer: syntax + semantic + business rules |
| Simulation | None | AST interpreter with semantic output filtering |
| Post-processing | None | Variable name → type inference correction |
| Type awareness | None | 123+ formula types with distinct structure |

### 9.2 Non-Obvious Combinations

1. **Template as Prompt Constraint (not Scaffold):** Unlike traditional code scaffolding where templates are filled in directly, this invention uses templates as constraints injected into LLM prompts, allowing the LLM creative freedom in business logic while enforcing structural compliance.

2. **Post-Generation Type Inference:** Using variable naming conventions to automatically correct LLM-generated type errors is a novel post-processing step not found in existing code generation systems.

3. **Context Function Argument Exclusion:** The semantic validator's awareness that first arguments to context functions (`GET_CONTEXT`, `SET_INPUT`) are identifier names — not variable references — prevents false-positive undeclared-variable errors, a domain-specific semantic nuance.

4. **Semantic Output Filtering in Simulation:** The interpreter's variable categorization system (input vs. computed) that automatically determines which variables to return as output — without explicit output declarations — is a novel approach to DSL simulation.

---

## 10. Prior Art Search Keywords

For searching USPTO, Google Patents, and academic databases:

- "template-constrained code generation LLM"
- "retrieval-augmented code generation domain-specific"
- "multi-layer code validation abstract syntax tree"
- "post-generation type inference variable naming"
- "domain-specific language simulation semantic output filtering"
- "formula type template prompt engineering"
- "enterprise code generation validation pipeline"

---

## 11. Implementation Reference

| Component | File Path |
|-----------|-----------|
| Template Registry | `backend/app/services/formula_templates.py` |
| Template Data (123 types) | `backend/data/formula_type_templates.json` |
| AI Service (Prompt Assembly) | `backend/app/services/ai_service.py` |
| Validator (3-Layer Pipeline) | `backend/app/services/validator.py` |
| Parser (Lark Grammar → AST) | `backend/app/parser/ff_parser.py` |
| AST Nodes (Immutable) | `backend/app/parser/ast_nodes.py` |
| Interpreter (Simulation) | `backend/app/parser/interpreter.py` |
| RAG Engine (ChromaDB) | `backend/app/services/rag_engine.py` |
| Oracle Import Script | `backend/app/scripts/import_from_oracle.py` |

---

## 12. Figures (to be created for formal filing)

1. **Fig. 1** — System architecture overview (6-stage pipeline)
2. **Fig. 2** — Constrained prompt assembly data flow
3. **Fig. 3** — Multi-layer validation pipeline
4. **Fig. 4** — AST interpreter variable categorization and output filtering
5. **Fig. 5** — Post-generation type inference decision tree
6. **Fig. 6** — Template skeleton with placeholder mapping

---

## 13. Next Steps

- [ ] Conduct formal Prior Art Search (USPTO, Google Patents, IEEE, ACM)
- [ ] Consult IP attorney with software patent experience
- [ ] Consider filing US Provisional Patent to lock priority date
- [ ] Prepare formal drawings (Fig. 1–6)
- [ ] Evaluate international filing (PCT) within 12 months of provisional
