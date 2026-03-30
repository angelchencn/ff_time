"""Fast Formula Lark parser — converts source text to AST."""
from __future__ import annotations

import os
from typing import Any, Union

from lark import Lark, Transformer, Token, Tree
from lark.exceptions import UnexpectedInput, VisitError

from app.parser.ast_nodes import (
    Diagnostic,
    ParseResult,
    Program,
    VariableDecl,
    Assignment,
    IfStatement,
    WhileLoop,
    ReturnStatement,
    BinaryOp,
    UnaryOp,
    FunctionCall,
    NumberLiteral,
    StringLiteral,
    VariableRef,
)

_GRAMMAR_PATH = os.path.join(os.path.dirname(__file__), "grammar.lark")

with open(_GRAMMAR_PATH, encoding="utf-8") as _f:
    _GRAMMAR_TEXT = _f.read()

_parser = Lark(
    _GRAMMAR_TEXT,
    parser="earley",
    propagate_positions=True,
    ambiguity="resolve",
)

# Keyword terminal names that should be filtered from children
_KEYWORD_TERMINALS = {
    "DEFAULT", "FOR", "INPUT", "OUTPUT", "LOCAL", "IS",
    "IF", "THEN", "ELSE", "ENDIF", "WHILE", "LOOP", "ENDLOOP",
    "RETURN", "OR", "AND", "NOT", "NUMBER_TYPE", "TEXT_TYPE", "DATE_TYPE",
}


def _filter_keywords(children: list) -> list:
    """Remove keyword tokens from a children list."""
    return [
        c for c in children
        if not (isinstance(c, Token) and c.type in _KEYWORD_TERMINALS)
    ]


def _fold_binary(items: list) -> Any:
    """Left-fold a flat [left, op-token, right, op-token, right, …] sequence."""
    if len(items) == 1:
        return items[0]
    result = items[0]
    i = 1
    while i < len(items):
        op = str(items[i])
        right = items[i + 1]
        result = BinaryOp(op=op, left=result, right=right)
        i += 2
    return result


class FFTransformer(Transformer):
    # ── literals ─────────────────────────────────────────────────────────────
    def number(self, children):
        return NumberLiteral(value=float(children[0]))

    def string(self, children):
        raw = str(children[0])
        return StringLiteral(value=raw[1:-1])

    def var_ref(self, children):
        return VariableRef(name=str(children[0]))

    # ── expressions ──────────────────────────────────────────────────────────
    def neg(self, children):
        return UnaryOp(op="-", operand=children[0])

    def not_op(self, children):
        return UnaryOp(op="NOT", operand=children[0])

    def func_call(self, children):
        # children: [NAME_token, *expr_list_items]
        name = str(children[0])
        # expr_list returns a plain list; func_call may receive it directly
        args_raw = children[1:]
        if len(args_raw) == 1 and isinstance(args_raw[0], list):
            args = tuple(args_raw[0])
        else:
            args = tuple(args_raw)
        return FunctionCall(name=name, args=args)

    def expr_list(self, children):
        return list(children)

    def comparison(self, children):
        if len(children) == 1:
            return children[0]
        left, op, right = children
        return BinaryOp(op=str(op), left=left, right=right)

    def add_expr(self, children):
        return _fold_binary(children)

    def mul_expr(self, children):
        return _fold_binary(children)

    def or_expr(self, children):
        return _fold_binary(children)

    def and_expr(self, children):
        return _fold_binary(children)

    # ── variable declarations ────────────────────────────────────────────────
    def default_decl(self, children):
        filtered = _filter_keywords(children)
        # filtered: [NAME, (data_type_str)?, expr]
        name = str(filtered[0])
        if len(filtered) == 2:
            data_type = None
            default_value = filtered[1]
        else:
            data_type = filtered[1]
            default_value = filtered[2]
        return VariableDecl(
            kind="default",
            var_name=name,
            data_type=data_type,
            default_value=default_value,
        )

    def input_decl(self, children):
        filtered = _filter_keywords(children)
        return VariableDecl(kind="input", var_name=str(filtered[0]))

    def output_decl(self, children):
        filtered = _filter_keywords(children)
        return VariableDecl(kind="output", var_name=str(filtered[0]))

    def local_decl(self, children):
        filtered = _filter_keywords(children)
        name = str(filtered[0])
        data_type = filtered[1] if len(filtered) > 1 else None
        return VariableDecl(kind="local", var_name=name, data_type=data_type)

    def data_type(self, children):
        return str(children[0]).upper()

    # ── statements ───────────────────────────────────────────────────────────
    def assignment(self, children):
        name, value = children
        return Assignment(var_name=str(name), value=value)

    def then_body(self, children):
        return list(children)

    def else_body(self, children):
        return list(children)

    def if_stmt(self, children):
        # children includes keyword tokens; filter them first
        filtered = _filter_keywords(children)
        # After filtering: [condition, then_body_list, (else_body_list)?]
        condition = filtered[0]
        then_list = filtered[1]  # returned by then_body as list
        else_list = filtered[2] if len(filtered) > 2 else None
        return IfStatement(
            condition=condition,
            then_body=tuple(then_list),
            else_body=tuple(else_list) if else_list is not None else None,
        )

    def while_stmt(self, children):
        filtered = _filter_keywords(children)
        condition = filtered[0]
        body = tuple(filtered[1:])
        return WhileLoop(condition=condition, body=body)

    def return_stmt(self, children):
        filtered = _filter_keywords(children)
        return ReturnStatement(value=filtered[0])

    # ── top-level ────────────────────────────────────────────────────────────
    def statement(self, children):
        return children[0]

    def start(self, children):
        return Program(statements=tuple(children))


def parse_formula(
    code: str,
    return_diagnostics: bool = False,
) -> Union[Program, ParseResult]:
    """Parse Fast Formula source code.

    Args:
        code: Source text to parse.
        return_diagnostics: When True always return a ParseResult even on
            success.  When False (default) return a Program directly, or
            raise on parse error.

    Returns:
        Program when return_diagnostics is False and parse succeeds.
        ParseResult when return_diagnostics is True.
    """
    try:
        tree = _parser.parse(code)
        program: Program = FFTransformer().transform(tree)
        if return_diagnostics:
            return ParseResult(program=program, diagnostics=())
        return program
    except UnexpectedInput as exc:
        line = getattr(exc, "line", 1) or 1
        col = getattr(exc, "column", 1) or 1
        diag = Diagnostic(
            line=line,
            col=col,
            end_col=col + 1,
            severity="error",
            message=str(exc).split("\n")[0],
            layer="parser",
        )
        if return_diagnostics:
            return ParseResult(program=None, diagnostics=(diag,))
        raise
    except VisitError as exc:
        # Unwrap transformer errors and re-raise the original
        raise exc.orig_exc from exc
