"""Immutable AST node definitions for Fast Formula parser."""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Optional


@dataclass(frozen=True)
class Diagnostic:
    line: int
    col: int
    end_col: int
    severity: str  # "error" | "warning" | "info"
    message: str
    layer: str = "parser"


@dataclass(frozen=True)
class NumberLiteral:
    value: float


@dataclass(frozen=True)
class StringLiteral:
    value: str


@dataclass(frozen=True)
class VariableRef:
    name: str


@dataclass(frozen=True)
class BinaryOp:
    op: str
    left: Any
    right: Any


@dataclass(frozen=True)
class UnaryOp:
    op: str
    operand: Any


@dataclass(frozen=True)
class FunctionCall:
    name: str
    args: tuple


@dataclass(frozen=True)
class VariableDecl:
    kind: str  # "default" | "input" | "output" | "local"
    var_name: str
    data_type: Optional[str] = None
    default_value: Any = None


@dataclass(frozen=True)
class Assignment:
    var_name: str
    value: Any


@dataclass(frozen=True)
class IfStatement:
    condition: Any
    then_body: tuple
    else_body: Optional[tuple] = None


@dataclass(frozen=True)
class WhileLoop:
    condition: Any
    body: tuple


@dataclass(frozen=True)
class ReturnStatement:
    value: Any


@dataclass(frozen=True)
class ArrayAccess:
    name: str
    index: Any


@dataclass(frozen=True)
class ArrayAssignment:
    name: str
    index: Any
    value: Any


@dataclass(frozen=True)
class MethodCall:
    object_name: str
    method_name: str
    args: tuple


@dataclass(frozen=True)
class CallFormulaStatement:
    formula_name: str
    params: tuple


@dataclass(frozen=True)
class ChangeContextsStatement:
    assignments: tuple  # tuple of (name, value) pairs


@dataclass(frozen=True)
class ExecuteStatement:
    formula_name: str


@dataclass(frozen=True)
class Program:
    statements: tuple


@dataclass(frozen=True)
class ParseResult:
    program: Optional[Program]
    diagnostics: Optional[tuple]
