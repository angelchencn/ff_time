"""Simulator service — parses and interprets Fast Formula code."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Optional

from app.parser.ff_parser import parse_formula
from app.parser.interpreter import Interpreter, SimulationError


@dataclass(frozen=True)
class SimulationResult:
    status: str                            # "SUCCESS" | "ERROR"
    output_data: dict[str, Any]
    execution_trace: list[dict]
    error: Optional[str]


def simulate_formula(
    code: str,
    input_data: dict[str, Any],
) -> SimulationResult:
    """Parse *code*, run the interpreter with *input_data*, return a result.

    The interpreter is created BEFORE the try block so that the execution trace
    is accessible even when a runtime error is raised.
    """
    interpreter = Interpreter(input_data)

    try:
        program = parse_formula(code)
        output_data = interpreter.run(program)
        return SimulationResult(
            status="SUCCESS",
            output_data=output_data,
            execution_trace=list(interpreter.trace),
            error=None,
        )
    except SimulationError as exc:
        return SimulationResult(
            status="ERROR",
            output_data={},
            execution_trace=list(interpreter.trace),
            error=str(exc),
        )
    except Exception as exc:  # noqa: BLE001 — catch parse errors, etc.
        return SimulationResult(
            status="ERROR",
            output_data={},
            execution_trace=list(interpreter.trace),
            error=str(exc),
        )
