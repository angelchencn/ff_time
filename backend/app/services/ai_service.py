"""AI service — wraps Anthropic Claude for Fast Formula generation, completion, and explanation."""
from __future__ import annotations

import logging
from typing import Any, Generator, Optional

import anthropic

from app.config import settings
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
WHILE, LOOP, END LOOP, AND, OR, NOT, WAS, WAS DEFAULTED, INPUTS ARE

## Supported Built-in Functions

ADD_MONTHS, CEIL, FLOOR, ROUND, TRUNC, ABS, GREATEST, LEAST,
TO_CHAR, TO_DATE, TO_NUMBER, LENGTH, SUBSTR, INSTR,
GET_TABLE_VALUE, INITIALIZE_TABLE_SS,
DAYS_BETWEEN, MONTHS_BETWEEN

## Formula Types

TIME_LABOR, PAYROLL, ABSENCE_DURATION, PRORATION_CALCULATION, PAY_VALUE

## Output Format

- Return only valid Fast Formula code unless explicitly asked for an explanation.
- Use consistent 2-space indentation for nested blocks.
- Include DEFAULT statements for all input variables.
- End every formula with a RETURN statement.
"""


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
    ) -> str:
        """Build a user prompt for formula generation with RAG context.

        Parameters
        ----------
        message:
            Natural language description of what the formula should do.
        formula_type:
            The Fast Formula type (e.g. TIME_LABOR, PAYROLL).
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

        sections.append(
            f"## Request\n\nFormula type: {formula_type}\n\nRequirement: {message}"
        )
        sections.append("Generate a complete Fast Formula that satisfies the requirement.")

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

        Returns
        -------
        str
            The generated formula text.
        """
        messages = list(history or [])
        messages.append({"role": "user", "content": self._build_generation_prompt(message, formula_type)})

        response = self._client.messages.create(
            model=_CHAT_MODEL,
            max_tokens=_MAX_TOKENS_CHAT,
            system=self._build_system_prompt(),
            messages=messages,
        )
        return response.content[0].text

    def stream_generate(
        self,
        message: str,
        formula_type: str,
        history: Optional[list[dict[str, str]]] = None,
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

        Yields
        ------
        str
            Successive text deltas from the model.
        """
        messages = list(history or [])
        messages.append({"role": "user", "content": self._build_generation_prompt(message, formula_type)})

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
