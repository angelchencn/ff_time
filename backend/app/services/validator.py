"""Validator service — 3-layer validation for Fast Formula code.

Layer 1 (syntax):   Parse via the Lark-based ff_parser; surface parse errors.
Layer 2 (semantic): Check undeclared variable references; warn on output vars
                    that are never assigned.
Layer 3 (rules):    Business-rule checks (RETURN presence, overtime conventions).
"""
from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Any

from app.parser.ff_parser import parse_formula
from app.parser.ast_nodes import (
    Assignment,
    BinaryOp,
    Diagnostic,
    FunctionCall,
    IfStatement,
    Program,
    ReturnStatement,
    UnaryOp,
    VariableDecl,
    VariableRef,
    WhileLoop,
)

_DBI_JSON_PATH = os.path.join(
    os.path.dirname(__file__),
    "..",
    "..",
    "data",
    "dbi_registry",
    "time_labor_dbis.json",
)

_OVERTIME_KEYWORDS = ("OVERTIME", "OT_PAY", "OT_RATE", "OVERTIME_PAY")


# ---------------------------------------------------------------------------
# Public result type
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class ValidationResult:
    valid: bool
    diagnostics: tuple


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _load_dbi_names() -> frozenset[str]:
    """Return the set of known DBI names (upper-cased) from the JSON registry."""
    path = os.path.normpath(_DBI_JSON_PATH)
    try:
        with open(path, encoding="utf-8") as fh:
            records = json.load(fh)
        return frozenset(r["name"].upper() for r in records)
    except (OSError, json.JSONDecodeError, KeyError):
        return frozenset()


# Functions whose first argument is an identifier name (not a variable reference).
# These should not trigger "undeclared variable" errors for their first argument.
_CONTEXT_FUNCTIONS = frozenset({
    "GET_CONTEXT", "SET_CONTEXT", "NEED_CONTEXT",
    "SET_INPUT", "GET_OUTPUT", "GET_INPUT",
})

# Built-in system variables that do not need explicit declaration.
_BUILTIN_VARIABLES = frozenset({
    "SYSDATE",       # Oracle current date
    "TRUE", "FALSE", # Boolean literals
})


def _collect_refs(node: Any, refs: set[str]) -> None:
    """Recursively collect all VariableRef names from an AST subtree."""
    if isinstance(node, VariableRef):
        refs.add(node.name)
    elif isinstance(node, BinaryOp):
        _collect_refs(node.left, refs)
        _collect_refs(node.right, refs)
    elif isinstance(node, UnaryOp):
        _collect_refs(node.operand, refs)
    elif isinstance(node, FunctionCall):
        func_upper = node.name.upper()
        for i, arg in enumerate(node.args):
            # Skip the first argument of context/input/output functions —
            # it is an identifier name, not a variable reference.
            if i == 0 and func_upper in _CONTEXT_FUNCTIONS:
                continue
            _collect_refs(arg, refs)
    elif isinstance(node, Assignment):
        _collect_refs(node.value, refs)
    elif isinstance(node, IfStatement):
        _collect_refs(node.condition, refs)
        for stmt in node.then_body:
            _collect_refs(stmt, refs)
        if node.else_body:
            for stmt in node.else_body:
                _collect_refs(stmt, refs)
    elif isinstance(node, WhileLoop):
        _collect_refs(node.condition, refs)
        for stmt in node.body:
            _collect_refs(stmt, refs)
    elif isinstance(node, ReturnStatement):
        _collect_refs(node.value, refs)


def _collect_assignments(statements: tuple) -> set[str]:
    """Return the set of variable names that are explicitly assigned."""
    assigned: set[str] = set()
    for stmt in statements:
        if isinstance(stmt, Assignment):
            assigned.add(stmt.var_name)
        elif isinstance(stmt, IfStatement):
            assigned |= _collect_assignments(stmt.then_body)
            if stmt.else_body:
                assigned |= _collect_assignments(stmt.else_body)
        elif isinstance(stmt, WhileLoop):
            assigned |= _collect_assignments(stmt.body)
    return assigned


# ---------------------------------------------------------------------------
# Layer 2 — semantic checks
# ---------------------------------------------------------------------------

def _semantic_check(program: Program, dbi_names: frozenset[str]) -> list[Diagnostic]:
    """Check for undeclared variable references and unassigned OUTPUT variables."""
    diagnostics: list[Diagnostic] = []

    # Build the set of declared names: var decls, assignment LHS names, DBIs, builtins
    declared: set[str] = set(dbi_names) | _BUILTIN_VARIABLES
    output_vars: set[str] = set()

    for stmt in program.statements:
        if isinstance(stmt, VariableDecl):
            declared.add(stmt.var_name)
            if stmt.kind == "output":
                output_vars.add(stmt.var_name)

    # Assignments declare names too
    assigned = _collect_assignments(program.statements)
    declared |= assigned

    # Built-in functions — not variables, so not checked here
    # Collect all VariableRef names across the entire program
    all_refs: set[str] = set()
    for stmt in program.statements:
        _collect_refs(stmt, all_refs)

    # Undeclared references (case-sensitive; FF is typically upper-case for DBIs)
    for name in sorted(all_refs):
        if name not in declared:
            diagnostics.append(
                Diagnostic(
                    line=0,
                    col=0,
                    end_col=0,
                    severity="error",
                    message=f"Undeclared variable '{name}' referenced but never declared or assigned.",
                    layer="semantic",
                )
            )

    # OUTPUT vars that are never assigned
    for name in sorted(output_vars):
        if name not in assigned:
            diagnostics.append(
                Diagnostic(
                    line=0,
                    col=0,
                    end_col=0,
                    severity="warning",
                    message=f"OUTPUT variable '{name}' is declared but never assigned a value.",
                    layer="semantic",
                )
            )

    return diagnostics


# ---------------------------------------------------------------------------
# Layer 3 — business-rule checks
# ---------------------------------------------------------------------------

def _has_return(statements: tuple) -> bool:
    """Return True if the statement list contains at least one ReturnStatement."""
    for stmt in statements:
        if isinstance(stmt, ReturnStatement):
            return True
        if isinstance(stmt, IfStatement):
            if _has_return(stmt.then_body):
                return True
            if stmt.else_body and _has_return(stmt.else_body):
                return True
        if isinstance(stmt, WhileLoop):
            if _has_return(stmt.body):
                return True
    return False


def _rule_check(program: Program) -> list[Diagnostic]:
    """Apply Layer 3 business rules."""
    diagnostics: list[Diagnostic] = []

    # Rule: formula must contain at least one RETURN statement
    if not _has_return(program.statements):
        diagnostics.append(
            Diagnostic(
                line=0,
                col=0,
                end_col=0,
                severity="error",
                message="Formula has no RETURN statement.",
                layer="rule",
            )
        )

    # Rule: OUTPUT vars with overtime-related names should reference HOURS_WORKED
    output_vars: list[str] = [
        stmt.var_name
        for stmt in program.statements
        if isinstance(stmt, VariableDecl) and stmt.kind == "output"
    ]

    all_refs: set[str] = set()
    for stmt in program.statements:
        _collect_refs(stmt, all_refs)

    for var in output_vars:
        if any(kw in var.upper() for kw in _OVERTIME_KEYWORDS):
            if "HOURS_WORKED" not in all_refs:
                diagnostics.append(
                    Diagnostic(
                        line=0,
                        col=0,
                        end_col=0,
                        severity="warning",
                        message=(
                            f"OUTPUT variable '{var}' appears overtime-related but "
                            "HOURS_WORKED is not referenced in the formula."
                        ),
                        layer="rule",
                    )
                )

    return diagnostics


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------

def validate_formula(code: str) -> ValidationResult:
    """Validate a Fast Formula string through 3 layers.

    Returns a :class:`ValidationResult` with ``valid`` set to True only when
    no error-level diagnostics are found across all layers.
    """
    from app.parser.ast_nodes import ParseResult as _ParseResult

    diagnostics: list[Diagnostic] = []

    # Layer 1 — syntax
    parse_result = parse_formula(code, return_diagnostics=True)
    if isinstance(parse_result, _ParseResult):
        if parse_result.diagnostics:
            # Re-tag parser diagnostics as layer="syntax" for the public API
            for d in parse_result.diagnostics:
                diagnostics.append(
                    Diagnostic(
                        line=d.line,
                        col=d.col,
                        end_col=d.end_col,
                        severity=d.severity,
                        message=d.message,
                        layer="syntax",
                    )
                )
        program = parse_result.program
    else:
        program = parse_result  # type: ignore[assignment]

    if program is None:
        # Syntax errors prevent further analysis
        return ValidationResult(valid=False, diagnostics=tuple(diagnostics))

    # Layer 2 — semantic
    dbi_names = _load_dbi_names()
    diagnostics.extend(_semantic_check(program, dbi_names))

    # Layer 3 — business rules
    diagnostics.extend(_rule_check(program))

    has_error = any(d.severity == "error" for d in diagnostics)
    return ValidationResult(valid=not has_error, diagnostics=tuple(diagnostics))
