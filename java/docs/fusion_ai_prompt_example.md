# Fusion AI Completions API — Prompt Example

This is the actual prompt sent to `/hcmRestApi/redwood/11.13.18.05/completions` for a "Custom" formula type chat request.

---

## System Prompt (sections 0–18)

```
You are an expert assistant for Oracle Fusion Cloud HCM Fast Formula — a domain-specific
language used to configure payroll, time, and absence rules in Oracle Fusion Cloud.

IMPORTANT: This is Oracle Fusion Cloud HCM ONLY. Do NOT use EBS (E-Business Suite) or
legacy Oracle Applications Fast Formula syntax, APIs, or patterns.

## 0. Formula Type First (CRITICAL)
Before generating any formula, FIRST identify the formula type...
- Oracle Payroll: RETURN depends on element classification
- Element Skip: RETURN skip_flag ('Y'/'N')
- Validation: RETURN formula_status ('S'/'E'/'W'), formula_message
- WFM Time Calculation Rules: RETURN as defined by time calculation rule
- ...

## 1. Data Types (NUMBER, TEXT, DATE)
## 2. Statement Ordering (ALIAS → DEFAULT → INPUTS → other)
## 3. Variable Declarations
## 4. Variable Scope (local read-write, input/DBI read-only)
## 5. Operators (precedence)
## 6. Control Flow (IF/WHILE/RETURN)
## 7. CALL_FORMULA
## 8. EXECUTE Pattern
## 9. CHANGE_CONTEXTS
## 10. Array Handling
## 11. Built-in Functions
## 12. Formula Types and Their Contracts (table)
## 13. Formula Structure Convention (header, naming, body order)
## 14. Output Format (rules for generation)
## 15. DEFAULT Value Type Rules (by variable name)
## 16. Limitations
## 17. Anti-Hallucination Rules
## 18. Compile Self-Check (10 items)
```

---

## User Prompt (dynamic, built by AiService.buildGenerationPrompt)

### Section 1: Reference Formula (from Custom sample)

```
## Reference Formula

Use the following formula as the base template.
Modify it according to the user's request.

/******************************************************************************
 *
 * Formula Name : CUSTOM_OVERTIME_PAY_CALC
 * Formula Type : Custom
 * Description  : Calculate overtime pay for hours exceeding 40 per week
 *
 * Change History
 * --------------
 *  Who             Ver    Date          Description
 * ---------------  ----   -----------   --------------------------------
 * Payroll Admin    1.0    2026/04/03    Created.
 *
 ******************************************************************************/

DEFAULT FOR HOURS_WORKED IS 0
DEFAULT FOR REGULAR_RATE IS 0
DEFAULT FOR OVERTIME_THRESHOLD IS 40
DEFAULT FOR OVERTIME_MULTIPLIER IS 1.5

INPUTS ARE
  HOURS_WORKED,
  REGULAR_RATE,
  OVERTIME_THRESHOLD,
  OVERTIME_MULTIPLIER

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_OVERTIME_PAY_CALC - Enter')

regular_hours = 0
overtime_hours = 0
regular_pay = 0
overtime_pay = 0
total_pay = 0

IF HOURS_WORKED > OVERTIME_THRESHOLD THEN
(
  regular_hours = OVERTIME_THRESHOLD
  overtime_hours = HOURS_WORKED - OVERTIME_THRESHOLD
)
ELSE
(
  regular_hours = HOURS_WORKED
  overtime_hours = 0
)

regular_pay = regular_hours * REGULAR_RATE
overtime_pay = overtime_hours * REGULAR_RATE * OVERTIME_MULTIPLIER
total_pay = regular_pay + overtime_pay

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_OVERTIME_PAY_CALC - Exit, total=' || TO_CHAR(total_pay))

RETURN regular_hours, overtime_hours, regular_pay, overtime_pay, total_pay

/* End Formula Text */
```

### Section 2: Formula Type Template (from FormulaTypesService)

```
## Formula Type Template

Type: Custom Formulas
Naming convention: CUSTOM_<BUSINESS_DESCRIPTION>

### Skeleton

/******************************************************************************
 *
 * Formula Name : {formula_name}
 * Formula Type : Custom
 * Description  : {description}
 *
 * Change History
 * --------------
 *  Who             Ver    Date          Description
 * ---------------  ----   -----------   --------------------------------
 * Payroll Admin    1.0    2026-04-07    Created.
 *
 ******************************************************************************/

/* DEFAULT and INPUTS declarations here */

l_log = PAY_INTERNAL_LOG_WRITE('{formula_name} - Enter')

/* ---- Business logic here ---- */

l_log = PAY_INTERNAL_LOG_WRITE('{formula_name} - Exit')

RETURN /* return variables here */

/* End Formula Text */
```

### Section 3: Request

```
## Request

Formula type: Custom

Requirement: Calculate overtime pay for weekly hours over 40
```

### Section 4: Generation Instructions

```
Generate a complete Fast Formula that satisfies the requirement.
Follow the skeleton template above exactly — fill in the formula name,
description, author, date, and replace the business logic placeholder.

CRITICAL REQUIREMENTS:
1. MUST include a professional header comment block
2. Include DEFAULT FOR statements for input variables that need fallback values
3. Include INPUTS ARE if the formula type requires input variables
4. MUST include PAY_INTERNAL_LOG_WRITE at entry and exit
5. MUST end with RETURN — variables must match the formula type's output contract
6. Return ONLY the formula code, no markdown fences, no explanation
7. Do NOT invent DBI names, context names, or return variable names — use placeholders if unsure
```

---

## JSON Payload (sent to Fusion Completions API)

```json
{
  "prompt": "<system_prompt>\n\n<reference_formula>\n\n<template>\n\n<request>\n\n<instructions>",
  "params": []
}
```

Total prompt size: ~8K–12K characters depending on formula type and RAG results.
