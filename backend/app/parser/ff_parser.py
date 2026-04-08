"""Fast Formula Lark parser — converts source text to AST."""
from __future__ import annotations

import os
from typing import Any, Union

from lark import Lark, Transformer, Token, Tree
from lark.exceptions import UnexpectedInput, VisitError

from app.parser.ast_nodes import (
    ArrayAccess,
    ArrayAssignment,
    Assignment,
    BinaryOp,
    CallFormulaStatement,
    ChangeContextsStatement,
    Diagnostic,
    ExecuteStatement,
    FunctionCall,
    IfStatement,
    MethodCall,
    NumberLiteral,
    ParseResult,
    Program,
    ReturnStatement,
    StringLiteral,
    UnaryOp,
    VariableDecl,
    VariableRef,
    WhileLoop,
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
    "ALIAS", "AS", "DEFAULT", "DEFAULT_DATA_VALUE", "DEFAULTED", "FOR",
    "INPUT", "INPUTS", "OUTPUT", "OUTPUTS", "LOCAL", "IS", "ARE",
    "IF", "THEN", "ELSIF", "ELSE", "ENDIF",
    "WHILE", "LOOP", "ENDLOOP", "EXIT",
    "RETURN", "OR", "AND", "NOT", "WAS", "LIKE", "USING",
    "EXECUTE", "CALL_FORMULA", "CHANGE_CONTEXTS", "NULL_KW",
    "NUMBER_TYPE", "TEXT_TYPE", "DATE_TYPE",
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

    def single_string(self, children):
        raw = str(children[0])
        # Remove outer quotes and unescape doubled single quotes
        return StringLiteral(value=raw[1:-1].replace("''", "'"))

    def typed_string(self, children):
        raw = str(children[0])
        # String with type cast like '01-Jan-2010'(DATE) — treat as string
        return StringLiteral(value=raw[1:-1].replace("''", "'"))

    def var_ref(self, children):
        return VariableRef(name=str(children[0]))

    def quoted_var_ref(self, children):
        raw = str(children[0])
        # Strip surrounding double quotes
        return VariableRef(name=raw.strip('"'))

    # ── expressions ──────────────────────────────────────────────────────────
    def neg(self, children):
        return UnaryOp(op="-", operand=children[0])

    def not_op(self, children):
        return UnaryOp(op="NOT", operand=children[0])

    def like_op(self, children):
        return BinaryOp(op="LIKE", left=children[0], right=children[1])

    def not_like_op(self, children):
        return BinaryOp(op="NOT LIKE", left=children[0], right=children[1])

    def was_defaulted_op(self, children):
        filtered = _filter_keywords(children)
        return BinaryOp(op="WAS DEFAULTED", left=filtered[0], right=NumberLiteral(value=1))

    def was_not_defaulted_op(self, children):
        filtered = _filter_keywords(children)
        return UnaryOp(op="NOT", operand=BinaryOp(op="WAS DEFAULTED", left=filtered[0], right=NumberLiteral(value=1)))

    def was_found_op(self, children):
        filtered = _filter_keywords(children)
        return BinaryOp(op="WAS FOUND", left=filtered[0], right=NumberLiteral(value=1))

    def was_not_found_op(self, children):
        filtered = _filter_keywords(children)
        return UnaryOp(op="NOT", operand=BinaryOp(op="WAS FOUND", left=filtered[0], right=NumberLiteral(value=1)))

    def is_null_op(self, children):
        filtered = _filter_keywords(children)
        return BinaryOp(op="IS NULL", left=filtered[0], right=NumberLiteral(value=0))

    def is_not_null_op(self, children):
        filtered = _filter_keywords(children)
        return UnaryOp(op="NOT", operand=BinaryOp(op="IS NULL", left=filtered[0], right=NumberLiteral(value=0)))

    def func_call(self, children):
        name = str(children[0])
        args_raw = children[1:]
        if len(args_raw) == 1 and isinstance(args_raw[0], list):
            args = tuple(args_raw[0])
        else:
            args = tuple(args_raw)
        return FunctionCall(name=name, args=args)

    def method_call(self, children):
        obj = str(children[0])
        method = str(children[1])
        args_raw = children[2:]
        if len(args_raw) == 1 and isinstance(args_raw[0], list):
            args = tuple(args_raw[0])
        else:
            args = tuple(args_raw)
        return MethodCall(object_name=obj, method_name=method, args=args)

    def array_access(self, children):
        return ArrayAccess(name=str(children[0]), index=children[1])

    def array_assign(self, children):
        return ArrayAssignment(name=str(children[0]), index=children[1], value=children[2])

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
        # filtered may be: [name, expr] or [name, type, expr] or [name, expr, type] or [name, type, expr, type]
        name = str(filtered[0])
        data_type = None
        default_value = None
        for item in filtered[1:]:
            if isinstance(item, str) and item.upper() in ("NUMBER", "TEXT", "DATE"):
                data_type = item.upper()
            else:
                default_value = item
        return VariableDecl(
            kind="default",
            var_name=name,
            data_type=data_type,
            default_value=default_value,
        )

    def input_decl(self, children):
        filtered = _filter_keywords(children)
        return VariableDecl(kind="input", var_name=str(filtered[0]))

    def name_list(self, children):
        # children may contain NAME tokens and data_type strings; extract names
        return [str(c) for c in children if isinstance(c, Token) and c.type == "NAME"]

    def inputs_decl(self, children):
        filtered = _filter_keywords(children)
        names = filtered[0] if filtered and isinstance(filtered[0], list) else [str(filtered[0])]
        return [VariableDecl(kind="input", var_name=n) for n in names]

    def default_data_value_decl(self, children):
        filtered = _filter_keywords(children)
        name = str(filtered[0])
        default_value = filtered[1] if len(filtered) > 1 else None
        return VariableDecl(kind="default", var_name=name, default_value=default_value)

    def output_decl(self, children):
        filtered = _filter_keywords(children)
        return VariableDecl(kind="output", var_name=str(filtered[0]))

    def outputs_decl(self, children):
        filtered = _filter_keywords(children)
        names = filtered[0] if filtered and isinstance(filtered[0], list) else [str(filtered[0])]
        return [VariableDecl(kind="output", var_name=n) for n in names]

    def local_decl(self, children):
        filtered = _filter_keywords(children)
        name = str(filtered[0])
        data_type = filtered[1] if len(filtered) > 1 else None
        return VariableDecl(kind="local", var_name=name, data_type=data_type)

    def alias_decl(self, children):
        filtered = _filter_keywords(children)
        # ALIAS old AS new — treat as assignment alias, store as VariableDecl
        return VariableDecl(kind="alias", var_name=str(filtered[1]), data_type=None,
                            default_value=VariableRef(name=str(filtered[0])))

    def data_type(self, children):
        return str(children[0]).upper()

    # ── statements ───────────────────────────────────────────────────────────
    def assignment(self, children):
        name, value = children
        return Assignment(var_name=str(name), value=value)

    def quoted_assignment(self, children):
        raw_name = str(children[0]).strip('"')
        return Assignment(var_name=raw_name, value=children[1])

    def then_body(self, children):
        return list(children)

    def else_body(self, children):
        return list(children)

    def elsif_clause(self, children):
        # children after keyword filtering: [condition, *statements]
        filtered = _filter_keywords(children)
        condition = filtered[0]
        body = filtered[1:]
        return (condition, body)

    def if_stmt(self, children):
        filtered = _filter_keywords(children)
        # filtered: [condition, then_body_list, *(elsif_tuples), (else_body_list)?]
        condition = filtered[0]
        then_list = filtered[1]
        if not isinstance(then_list, list):
            then_list = [then_list]

        # Collect elsif clauses and else body
        elsif_clauses = []
        else_list = None
        for item in filtered[2:]:
            if isinstance(item, tuple) and len(item) == 2:
                # elsif clause: (condition, body)
                elsif_clauses.append(item)
            elif isinstance(item, list):
                else_list = item

        # Convert ELSIF chain into nested IfStatements in the else_body
        if elsif_clauses:
            # Build from last elsif backwards
            inner = else_list
            for elsif_cond, elsif_body in reversed(elsif_clauses):
                if not isinstance(elsif_body, list):
                    elsif_body = [elsif_body]
                inner_else = tuple(inner) if inner else None
                inner = [IfStatement(
                    condition=elsif_cond,
                    then_body=tuple(elsif_body),
                    else_body=inner_else,
                )]
            else_list = inner

        return IfStatement(
            condition=condition,
            then_body=tuple(then_list),
            else_body=tuple(else_list) if else_list else None,
        )

    def while_body(self, children):
        return list(children)

    def while_stmt(self, children):
        filtered = _filter_keywords(children)
        condition = filtered[0]
        body = filtered[1]  # while_body returns a list
        if not isinstance(body, list):
            body = filtered[1:]
        return WhileLoop(condition=condition, body=tuple(body))

    def return_stmt(self, children):
        filtered = _filter_keywords(children)
        # Can return multiple values; for now use first
        return ReturnStatement(value=filtered[0])

    def empty_return(self, children):
        return ReturnStatement(value=NumberLiteral(value=0))

    def call_formula_full(self, children):
        return CallFormulaStatement(formula_name="", params=())

    def call_formula_args(self, children):
        return children

    def call_formula_arg(self, children):
        return children

    def bare_func_call(self, children):
        name = str(children[0])
        args_raw = children[1:]
        if len(args_raw) == 1 and isinstance(args_raw[0], list):
            args = tuple(args_raw[0])
        else:
            args = tuple(args_raw)
        return Assignment(var_name=f"__{name}_result", value=FunctionCall(name=name, args=args))

    def change_contexts(self, children):
        return ChangeContextsStatement(assignments=())

    def context_assignment(self, children):
        return children

    def execute_call(self, children):
        filtered = _filter_keywords(children)
        name = str(filtered[0]) if filtered else ""
        if name.startswith("'"):
            name = name[1:-1]
        return ExecuteStatement(formula_name=name)

    # ── top-level ────────────────────────────────────────────────────────────
    def statement(self, children):
        return children[0]

    def start(self, children):
        # Flatten any lists from inputs_decl
        flat: list = []
        for child in children:
            if isinstance(child, list):
                flat.extend(child)
            else:
                flat.append(child)
        return Program(statements=tuple(flat))


def parse_formula(
    code: str,
    return_diagnostics: bool = False,
) -> Union[Program, ParseResult]:
    """Parse Fast Formula source code."""
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
            layer="syntax",
        )
        if return_diagnostics:
            return ParseResult(program=None, diagnostics=(diag,))
        raise
    except VisitError as exc:
        raise exc.orig_exc from exc
