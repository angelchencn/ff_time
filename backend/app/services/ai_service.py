"""AI service — wraps Anthropic Claude for Fast Formula generation, completion, and explanation."""
from __future__ import annotations

import logging
import re
from typing import Any, Generator, Optional

import anthropic

from app.config import settings
from app.services.formula_templates import get_template
from app.services.rag_engine import RAGEngine

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Model identifiers
# ---------------------------------------------------------------------------
_CHAT_MODEL = "claude-sonnet-4-20250514"
_COMPLETION_MODEL = "claude-haiku-4-5-20251001"
_MAX_TOKENS_CHAT = 4096
_MAX_TOKENS_COMPLETION = 512

# ---------------------------------------------------------------------------
# System prompt constant
# ---------------------------------------------------------------------------
SYSTEM_PROMPT = """\
You are an expert assistant for Oracle HCM Fast Formula — a domain-specific language used to \
configure payroll, time, and absence rules.

## Fast Formula Syntax Rules

- Every formula begins with optional DEFAULT statements and ends with a RETURN statement.
- Variables are untyped; type is inferred from context (number, text, date).
- Comments start with /* and end with */.

## Supported Keywords

DEFAULT, DEFAULT FOR, IS, IF, THEN, ELSE, ELSIF, END IF, RETURN,
WHILE, LOOP, END LOOP, AND, OR, NOT, WAS, WAS DEFAULTED, INPUTS ARE,
INPUT IS, OUTPUT IS, OUTPUTS ARE, ALIAS, AS, LOCAL, LIKE, NOT LIKE, EXIT

## Supported Built-in Functions

ADD_MONTHS, CEIL, FLOOR, ROUND, TRUNC, ABS, GREATEST, LEAST,
TO_CHAR, TO_DATE, TO_NUMBER, LENGTH, SUBSTR, INSTR,
GET_TABLE_VALUE, INITIALIZE_TABLE_SS,
DAYS_BETWEEN, MONTHS_BETWEEN, ADD_DAYS, ADD_YEARS, HOURS_BETWEEN,
UPPER, LOWER, REPLACE, TRIM, LPAD, RPAD, LTRIM, RTRIM,
GET_LOOKUP_MEANING, RAISE_ERROR, CALL_FORMULA,
PAY_INTERNAL_LOG_WRITE, PUT_MESSAGE, ISNULL,
SET_TEXT, SET_NUMBER, SET_DATE, GET_TEXT, GET_NUMBER, GET_DATE,
CALCULATE_HOURS_WORKED, GET_WORKING_DAYS

## Formula Types

Oracle Payroll, Auto Indirect, Extract Record, Payroll Run Proration,
Element Skip, Extract Rule, Calculation Utility,
WORKFORCE_MANAGEMENT_TIME_CALCULATION_RULES,
WORKFORCE_MANAGEMENT_TIME_ENTRY_RULES,
WORKFORCE_MANAGEMENT_TIME_SUBMISSION_RULES,
WORKFORCE_MANAGEMENT_TIME_COMPLIANCE_RULES,
WORKFORCE_MANAGEMENT_TIME_DEVICE_RULES,
WORKFORCE_MANAGEMENT_TIME_ADVANCE_CATEGORY_RULES,
WORKFORCE_MANAGEMENT_SUBROUTINE,
WORKFORCE_MANAGEMENT_UTILITY,
Global Absence Accrual, Global Absence Plan Entitlement,
Global Absence Entry Validation, ACCRUAL

## Formula Structure Convention

Every generated formula MUST follow the standard Oracle Fast Formula structure \
used in production environments. This includes:

### 1. Header Comment Block (REQUIRED)

Always generate a professional header comment block at the top:

```
/******************************************************************************
 *
 * Formula Name : <FORMULA_NAME>
 *
 * Formula Type : <FORMULA_TYPE>
 *
 * Description  : <Brief description of what the formula does>
 *
 * Change History
 * --------------
 *
 *  Who             Ver    Date          Description
 * ---------------  ----   -----------   --------------------------------
 * Payroll Admin    1.0    <YYYY/MM/DD>  Created.
 *
 ******************************************************************************/
```

### 2. Formula Naming Convention

Formula names follow the pattern: `<PREFIX>_<BUSINESS_DESCRIPTION>_<SUFFIX>`

Common suffixes by purpose:
- `_EARN` / `_EARNINGS` — Earnings calculation
- `_RESULTS` — Result formulas
- `_PRORATION` / `_EARN_PRORATION` — Proration logic
- `_BASE` — Base formulas that call sub-formulas
- `_CALCULATOR` — Fee/amount calculators
- `_DISTRIBUTION` — Distribution formulas
- `_DEDN` — Deduction formulas
- `_AUTO_INDIRECT` — Auto indirect entry

Common prefixes by module:
- `ORA_WFM_` / `WFM_` — Workforce Management (Time & Labor)
- `ORA_HRX_` / `HRX_` — Regional extensions
- Time rule subtypes: `_TCR_` (Time Calculation Rule), `_TER_` (Time Entry Rule), \
`_TSR_` (Time Submission Rule), `_TDR_` (Time Device Rule)

### 3. Formula Body Order (REQUIRED)

Formulas MUST follow this strict ordering:
1. Header comment block
2. DEFAULT statements (with type annotations where needed)
3. INPUTS ARE statement
4. Variable assignments and business logic
5. Logging calls (PAY_INTERNAL_LOG_WRITE) at entry/exit
6. RETURN statement

### 4. End Marker

Optionally end the formula with:
```
/* End Formula Text */
```

## Output Format

- Return only valid Fast Formula code unless explicitly asked for an explanation.
- Use consistent 2-space indentation for nested blocks.
- Include DEFAULT statements for all input variables.
- End every formula with a RETURN statement.
- ALWAYS include the header comment block as described above.

## DEFAULT Value Type Rules

Choose the correct DEFAULT value based on the variable name:
- Use string defaults (`' '` or `'X'`) for variables whose name contains: \
NAME, TEXT, DESC, CODE, TYPE, STATUS, FLAG, MESSAGE, MSG, LABEL, CATEGORY, \
TITLE, MODE, REASON, COMMENT, NOTE, KEY, TAG, LEVEL, CLASS, GROUP, ROLE, UNIT.
- Use numeric defaults (`0`) for variables whose name contains: \
COUNT, COUNTER, TOTAL, AMOUNT, AMT, RATE, HOURS, DAYS, SALARY, PAY, WAGE, \
BALANCE, QTY, QUANTITY, NUMBER, NUM, PCT, PERCENT, FACTOR, LIMIT, MAX, MIN, \
THRESHOLD, INDEX, ID, CYCLE, ITERATION, RESULT, VALUE, SCORE.
- Use date defaults (`'01-JAN-0001'(DATE)`) for variables whose name contains: \
DATE, START, END, FROM, TO, EFFECTIVE, EXPIRY, HIRE, TERMINATION, BIRTH.
- When in doubt, infer the type from how the variable is used in the formula logic \
(e.g., compared with a string → use string default; used in arithmetic → use numeric default).
"""


_STRING_KEYWORDS = frozenset({
    "NAME", "TEXT", "DESC", "CODE", "TYPE", "STATUS", "FLAG", "MESSAGE",
    "MSG", "LABEL", "CATEGORY", "TITLE", "MODE", "REASON", "COMMENT",
    "NOTE", "KEY", "TAG", "LEVEL", "CLASS", "GROUP", "ROLE", "UNIT",
    "TASK", "PROCESS", "ACTION", "METHOD", "FORMAT", "PATTERN",
    "PREFIX", "SUFFIX", "STRING", "CHAR", "CURRENCY",
})

_DATE_KEYWORDS = frozenset({
    "DATE", "START", "END", "EFFECTIVE", "EXPIRY", "HIRE",
    "TERMINATION", "BIRTH",
})

_DEFAULT_LINE_RE = re.compile(
    r"^(\s*DEFAULT\s+FOR\s+)(\w+)(\s+IS\s+)(\S+.*)$",
    re.IGNORECASE,
)


def fix_default_types(code: str) -> str:
    """Post-process generated formula to fix DEFAULT value types based on variable names.

    Scans each DEFAULT line. If the variable name strongly suggests a string
    but the default value is numeric (e.g. 0), replace with ' '.
    Similarly for date-like names with numeric defaults.
    """
    lines = code.split("\n")
    fixed: list[str] = []

    for line in lines:
        m = _DEFAULT_LINE_RE.match(line)
        if m:
            prefix, var_name, is_part, value = m.group(1), m.group(2), m.group(3), m.group(4)
            parts = var_name.upper().split("_")

            value_stripped = value.strip()
            is_numeric_val = value_stripped in ("0", "0.0", "0.00")

            if is_numeric_val:
                if any(p in _STRING_KEYWORDS for p in parts):
                    line = f"{prefix}{var_name}{is_part}' '"
                elif any(p in _DATE_KEYWORDS for p in parts):
                    line = f"{prefix}{var_name}{is_part}'01-JAN-0001'(DATE)"

        fixed.append(line)

    return "\n".join(fixed)


class AIService:
    """Provides AI-powered Fast Formula generation, completion, and explanation."""

    def __init__(self, rag_persist_dir: Optional[str] = None) -> None:
        self._client = anthropic.Anthropic(api_key=settings.anthropic_api_key)
        self._rag = RAGEngine(persist_dir=rag_persist_dir)

    # ------------------------------------------------------------------
    # Prompt builders
    # ------------------------------------------------------------------

    def _build_system_prompt(self) -> str:
        """Return the static system prompt."""
        return SYSTEM_PROMPT

    def _build_generation_prompt(
        self,
        message: str,
        formula_type: str,
        current_code: str = "",
    ) -> str:
        """Build a user prompt for formula generation with RAG context.

        Parameters
        ----------
        message:
            Natural language description of what the formula should do.
        formula_type:
            The Fast Formula type (e.g. TIME_LABOR, PAYROLL).
        current_code:
            The current formula code in the editor, used for modification requests.
        """
        rag_results = self._rag.query(message, top_k=3)

        sections: list[str] = []

        if rag_results:
            examples = "\n\n".join(
                f"/* use_case: {r['metadata'].get('use_case', 'unknown')} */\n{r['code']}"
                for r in rag_results
            )
            sections.append(
                f"## Relevant Example Formulas\n\n{examples}"
            )

        has_existing_code = bool(current_code.strip())

        if has_existing_code:
            sections.append(
                f"## Current Formula in Editor\n\n```\n{current_code}\n```\n\n"
                "The user wants to modify this existing formula. "
                "Output the complete updated formula incorporating the requested change."
            )

        # Inject formula type template if available
        template = get_template(formula_type)
        if template:
            from datetime import date
            skeleton = template.skeleton.replace("{date_today}", date.today().strftime("%Y/%m/%d"))
            sections.append(
                f"## Formula Type Template\n\n"
                f"**Type:** {template.display_name}\n"
                f"**Naming convention:** {template.naming_pattern}\n\n"
                f"### Skeleton\n```\n{skeleton}\n```\n\n"
                f"### Example Pattern\n```\n{template.example_snippet}\n```\n\n"
                f"Use this skeleton as the structural foundation. "
                f"Replace the placeholder business logic section with the actual implementation. "
                f"Keep the header, DEFAULT/INPUTS, logging, and RETURN structure intact."
            )

        sections.append(
            f"## Request\n\nFormula type: {formula_type}\n\nRequirement: {message}"
        )

        if has_existing_code:
            sections.append(
                "Return the complete modified formula. Do not explain the changes unless asked. "
                "Preserve the header comment block if one exists, updating the Change History."
            )
        else:
            sections.append(
                "Generate a complete Fast Formula that satisfies the requirement. "
                "Follow the skeleton template above exactly — fill in the formula name, "
                "description, author, date, and replace the business logic placeholder. "
                "Derive a proper formula name following the naming convention shown."
            )

        return "\n\n".join(sections)

    def _build_completion_prompt(
        self,
        code: str,
        cursor_line: int,
        cursor_col: int,
    ) -> str:
        """Build a prompt for inline code completion.

        Parameters
        ----------
        code:
            The current formula source code.
        cursor_line:
            1-based line number of the cursor.
        cursor_col:
            0-based column of the cursor.
        """
        rag_results = self._rag.query(code, top_k=2)

        sections: list[str] = []

        if rag_results:
            examples = "\n\n".join(r["code"] for r in rag_results)
            sections.append(f"## Related Formula Examples\n\n{examples}")

        sections.append(
            f"## Current Formula (cursor at line {cursor_line}, col {cursor_col})\n\n"
            f"```\n{code}\n```"
        )
        sections.append(
            "Provide the most likely next tokens or lines to complete this formula. "
            "Return only the completion text, no explanation."
        )

        return "\n\n".join(sections)

    def _build_explain_prompt(
        self,
        code: str,
        selected_range: Optional[dict[str, Any]],
        action: str,
    ) -> str:
        """Build a prompt for explanation or refactoring actions.

        Parameters
        ----------
        code:
            Full formula source code.
        selected_range:
            Optional dict with 'start' and 'end' line keys for a selection.
        action:
            One of 'explain', 'optimize', 'fix', etc.
        """
        if selected_range:
            lines = code.splitlines()
            start = selected_range.get("start", 1) - 1
            end = selected_range.get("end", len(lines))
            snippet = "\n".join(lines[start:end])
            code_section = f"Selected code (lines {start+1}–{end}):\n```\n{snippet}\n```"
        else:
            code_section = f"Formula:\n```\n{code}\n```"

        return f"{code_section}\n\nAction: {action}"

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def generate(
        self,
        message: str,
        formula_type: str,
        history: Optional[list[dict[str, str]]] = None,
        current_code: str = "",
    ) -> str:
        """Generate a Fast Formula synchronously.

        Parameters
        ----------
        message:
            User's natural-language requirement.
        formula_type:
            Target Fast Formula type.
        history:
            Optional list of prior conversation turns, each with 'role' and 'content'.
        current_code:
            The current formula in the editor for modification requests.

        Returns
        -------
        str
            The generated formula text.
        """
        messages = list(history or [])
        messages.append({"role": "user", "content": self._build_generation_prompt(message, formula_type, current_code)})

        response = self._client.messages.create(
            model=_CHAT_MODEL,
            max_tokens=_MAX_TOKENS_CHAT,
            system=self._build_system_prompt(),
            messages=messages,
        )
        return fix_default_types(response.content[0].text)

    def stream_generate(
        self,
        message: str,
        formula_type: str,
        history: Optional[list[dict[str, str]]] = None,
        current_code: str = "",
    ) -> Generator[str, None, None]:
        """Stream-generate a Fast Formula, yielding text chunks.

        Parameters
        ----------
        message:
            User's natural-language requirement.
        formula_type:
            Target Fast Formula type.
        history:
            Optional prior conversation turns.
        current_code:
            The current formula in the editor for modification requests.

        Yields
        ------
        str
            Successive text deltas from the model.
        """
        messages = list(history or [])
        messages.append({"role": "user", "content": self._build_generation_prompt(message, formula_type, current_code)})

        with self._client.messages.stream(
            model=_CHAT_MODEL,
            max_tokens=_MAX_TOKENS_CHAT,
            system=self._build_system_prompt(),
            messages=messages,
        ) as stream:
            for text in stream.text_stream:
                yield text

    def complete(
        self,
        code: str,
        cursor_line: int,
        cursor_col: int,
    ) -> list[str]:
        """Return inline completion suggestions for a partial formula.

        Parameters
        ----------
        code:
            Current formula source.
        cursor_line:
            1-based line where completion is requested.
        cursor_col:
            0-based column where completion is requested.

        Returns
        -------
        list[str]
            A list of completion strings (may be empty).
        """
        prompt = self._build_completion_prompt(code, cursor_line, cursor_col)

        response = self._client.messages.create(
            model=_COMPLETION_MODEL,
            max_tokens=_MAX_TOKENS_COMPLETION,
            system=self._build_system_prompt(),
            messages=[{"role": "user", "content": prompt}],
        )
        raw = response.content[0].text.strip()
        if not raw:
            return []
        return [raw]

    def explain(
        self,
        code: str,
        selected_range: Optional[dict[str, Any]] = None,
        action: str = "explain",
    ) -> Generator[str, None, None]:
        """Stream an explanation or transformation for a formula.

        Parameters
        ----------
        code:
            The full formula source code.
        selected_range:
            Optional selection dict with 'start'/'end' line keys.
        action:
            What to do: 'explain', 'optimize', 'fix', etc.

        Yields
        ------
        str
            Successive text deltas from the model.
        """
        prompt = self._build_explain_prompt(code, selected_range, action)

        with self._client.messages.stream(
            model=_CHAT_MODEL,
            max_tokens=_MAX_TOKENS_CHAT,
            system=self._build_system_prompt(),
            messages=[{"role": "user", "content": prompt}],
        ) as stream:
            for text in stream.text_stream:
                yield text
