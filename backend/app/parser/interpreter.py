"""Tree-walking interpreter for Fast Formula AST."""
from __future__ import annotations

from typing import Any

from app.parser.ast_nodes import (
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

_MAX_ITERATIONS = 10_000


class ReturnSignal(Exception):
    """Raised to implement RETURN control flow."""

    def __init__(self, value: Any) -> None:
        self.value = value


class SimulationError(Exception):
    """Raised for runtime errors during interpretation."""


class Interpreter:
    """Tree-walking interpreter that executes a Fast Formula AST."""

    def __init__(self, input_data: dict[str, Any]) -> None:
        # env holds all variable values during execution
        self.env: dict[str, Any] = {}
        self.trace: list[dict] = []
        # keys provided by the caller
        self._input_keys: set[str] = set(input_data.keys())
        # keys declared via DEFAULT/INPUT/OUTPUT/LOCAL declarations
        self._declared_input_keys: set[str] = set()

        # Seed env with input data
        for k, v in input_data.items():
            self.env[k] = float(v) if isinstance(v, (int, float)) else v

    # ── public entry point ────────────────────────────────────────────────────

    def run(self, program: Program) -> dict[str, Any]:
        """Execute all statements; return only computed (non-input/declared) vars."""
        try:
            for stmt in program.statements:
                self._exec_statement(stmt)
        except ReturnSignal:
            pass

        # Exclude variables that were declared as inputs/defaults or provided as input
        excluded = self._input_keys | self._declared_input_keys
        return {k: v for k, v in self.env.items() if k not in excluded}

    # ── statement execution ───────────────────────────────────────────────────

    def _exec_statement(self, stmt: Any) -> None:
        if isinstance(stmt, VariableDecl):
            self._exec_decl(stmt)
        elif isinstance(stmt, Assignment):
            self._exec_assignment(stmt)
        elif isinstance(stmt, IfStatement):
            self._exec_if(stmt)
        elif isinstance(stmt, WhileLoop):
            self._exec_while(stmt)
        elif isinstance(stmt, ReturnStatement):
            self._exec_return(stmt)
        else:
            raise SimulationError(f"Unknown statement type: {type(stmt).__name__}")

    def _exec_decl(self, stmt: VariableDecl) -> None:
        name = stmt.var_name
        self._declared_input_keys.add(name)
        if name not in self.env:
            # Apply default value if provided; otherwise use 0 or ""
            if stmt.default_value is not None:
                self.env[name] = self._eval(stmt.default_value)
            else:
                self.env[name] = 0.0

        self.trace.append({
            "statement": f"DECL {name}",
            "env_snapshot": dict(self.env),
        })

    def _exec_assignment(self, stmt: Assignment) -> None:
        value = self._eval(stmt.value)
        self.env = {**self.env, stmt.var_name: value}
        self.trace.append({
            "statement": f"{stmt.var_name} = {value}",
            "env_snapshot": dict(self.env),
        })

    def _exec_if(self, stmt: IfStatement) -> None:
        condition_value = self._eval(stmt.condition)
        self.trace.append({
            "statement": f"IF condition={condition_value}",
            "env_snapshot": dict(self.env),
        })
        if condition_value:
            for s in stmt.then_body:
                self._exec_statement(s)
        elif stmt.else_body is not None:
            for s in stmt.else_body:
                self._exec_statement(s)

    def _exec_while(self, stmt: WhileLoop) -> None:
        iterations = 0
        while self._eval(stmt.condition):
            if iterations >= _MAX_ITERATIONS:
                raise SimulationError(
                    f"Infinite loop detected: exceeded {_MAX_ITERATIONS} iterations"
                )
            for s in stmt.body:
                self._exec_statement(s)
            iterations += 1

    def _exec_return(self, stmt: ReturnStatement) -> None:
        value = self._eval(stmt.value)
        self.trace.append({
            "statement": f"RETURN {value}",
            "env_snapshot": dict(self.env),
        })
        raise ReturnSignal(value)

    # ── expression evaluation ─────────────────────────────────────────────────

    def _eval(self, node: Any) -> Any:
        if isinstance(node, NumberLiteral):
            return node.value
        if isinstance(node, StringLiteral):
            return node.value
        if isinstance(node, VariableRef):
            if node.name not in self.env:
                raise SimulationError(f"Undefined variable: {node.name}")
            return self.env[node.name]
        if isinstance(node, BinaryOp):
            left = self._eval(node.left)
            right = self._eval(node.right)
            return self._binary_op(node.op, left, right)
        if isinstance(node, UnaryOp):
            operand = self._eval(node.operand)
            if node.op == "-":
                return -operand
            if node.op == "NOT":
                return not operand
            raise SimulationError(f"Unknown unary operator: {node.op}")
        if isinstance(node, FunctionCall):
            return self._call_function(node)
        raise SimulationError(f"Unknown AST node type: {type(node).__name__}")

    def _binary_op(self, op: str, left: Any, right: Any) -> Any:
        if op == "+":
            return left + right
        if op == "-":
            return left - right
        if op == "*":
            return left * right
        if op == "/":
            if right == 0:
                raise SimulationError("Division by zero")
            return left / right
        if op == ">":
            return left > right
        if op == "<":
            return left < right
        if op == ">=":
            return left >= right
        if op == "<=":
            return left <= right
        if op == "=":
            return left == right
        if op == "!=":
            return left != right
        if op in ("OR", "or"):
            return left or right
        if op in ("AND", "and"):
            return left and right
        raise SimulationError(f"Unknown binary operator: {op}")

    def _call_function(self, node: FunctionCall) -> Any:
        name = node.name.upper()
        args = [self._eval(a) for a in node.args]

        if name == "TO_NUMBER":
            if len(args) != 1:
                raise SimulationError("TO_NUMBER requires exactly 1 argument")
            try:
                return float(args[0])
            except (ValueError, TypeError) as exc:
                raise SimulationError(f"TO_NUMBER: cannot convert {args[0]!r}") from exc

        if name == "TO_CHAR":
            if len(args) < 1:
                raise SimulationError("TO_CHAR requires at least 1 argument")
            return str(args[0])

        if name == "ABS":
            if len(args) != 1:
                raise SimulationError("ABS requires exactly 1 argument")
            return abs(args[0])

        if name == "ROUND":
            if len(args) not in (1, 2):
                raise SimulationError("ROUND requires 1 or 2 arguments")
            decimals = int(args[1]) if len(args) == 2 else 0
            return round(args[0], decimals)

        if name == "GREATEST":
            if not args:
                raise SimulationError("GREATEST requires at least 1 argument")
            return max(args)

        if name == "LEAST":
            if not args:
                raise SimulationError("LEAST requires at least 1 argument")
            return min(args)

        raise SimulationError(f"Unknown function: {node.name}")
