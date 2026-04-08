"""Tree-walking interpreter for Fast Formula AST."""
from __future__ import annotations

import calendar
from datetime import datetime, timedelta
from typing import Any

from app.parser.ast_nodes import (
    ArrayAccess,
    ArrayAssignment,
    Assignment,
    BinaryOp,
    CallFormulaStatement,
    ChangeContextsStatement,
    ExecuteStatement,
    FunctionCall,
    IfStatement,
    MethodCall,
    NumberLiteral,
    Program,
    ReturnStatement,
    StringLiteral,
    UnaryOp,
    VariableDecl,
    VariableRef,
    WhileLoop,
)

_MAX_ITERATIONS = 10_000

_DATE_FORMATS = [
    "%d-%b-%Y",    # 2-Apr-2026
    "%d-%B-%Y",    # 2-April-2026
    "%Y-%m-%d",    # 2026-04-02
    "%d/%m/%Y",    # 02/04/2026
    "%m/%d/%Y",    # 04/02/2026
    "%Y/%m/%d",    # 2026/04/02
]


def _parse_date(val: Any) -> datetime:
    """Parse a date value from string, datetime, or passthrough."""
    if isinstance(val, datetime):
        return val
    s = str(val).strip().strip("'\"")
    for fmt in _DATE_FORMATS:
        try:
            return datetime.strptime(s, fmt)
        except ValueError:
            continue
    raise SimulationError(f"Cannot parse date: {val!r}")


def _last_day_of_month(year: int, month: int) -> int:
    return calendar.monthrange(year, month)[1]


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
        # keys declared via INPUT IS / INPUTS ARE (true inputs to exclude from output)
        self._declared_input_keys: set[str] = set()
        # keys declared via DEFAULT FOR (may be reassigned — only exclude if not reassigned)
        self._default_keys: set[str] = set()
        # keys that were assigned after declaration (computed values)
        self._assigned_keys: set[str] = set()

        # Seed env with input data
        for k, v in input_data.items():
            self.env[k] = float(v) if isinstance(v, (int, float)) else v

    def _add_trace(self, message: str) -> None:
        """Append a trace entry for simulated/logged operations."""
        self.trace.append({"statement": message, "env_snapshot": dict(self.env)})

    # ── public entry point ────────────────────────────────────────────────────

    def run(self, program: Program) -> dict[str, Any]:
        """Execute all statements; return only computed (non-input/declared) vars."""
        try:
            for stmt in program.statements:
                self._exec_statement(stmt)
        except ReturnSignal:
            pass

        # Exclude caller-provided inputs and INPUT IS/INPUTS ARE declarations.
        # DEFAULT FOR variables are included if they were reassigned (computed outputs).
        excluded = self._input_keys | self._declared_input_keys
        # Default-only vars that were never reassigned are also excluded
        excluded |= self._default_keys - self._assigned_keys
        result: dict[str, Any] = {}
        for k, v in self.env.items():
            if k not in excluded:
                result[k] = v.strftime("%-d-%b-%Y") if isinstance(v, datetime) else v
        return result

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
        elif isinstance(stmt, ArrayAssignment):
            self._exec_array_assignment(stmt)
        elif isinstance(stmt, (CallFormulaStatement, ChangeContextsStatement, ExecuteStatement)):
            self._add_trace(f"SIMULATED: {type(stmt).__name__}")
        else:
            raise SimulationError(f"Unknown statement type: {type(stmt).__name__}")

    def _exec_array_assignment(self, stmt: ArrayAssignment) -> None:
        value = self._eval(stmt.value)
        key = f"{stmt.name}[{self._eval(stmt.index)}]"
        self.env = {**self.env, key: value}
        self.trace.append({"statement": f"{key} = {value}", "env_snapshot": dict(self.env)})

    def _exec_decl(self, stmt: VariableDecl) -> None:
        name = stmt.var_name
        if stmt.kind in ('input', 'output', 'local', 'alias'):
            self._declared_input_keys.add(name)
        else:
            self._default_keys.add(name)
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
        self._assigned_keys.add(stmt.var_name)
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
        if isinstance(node, ArrayAccess):
            key = f"{node.name}[{self._eval(node.index)}]"
            return self.env.get(key, 0)
        if isinstance(node, MethodCall):
            self._add_trace(f"SIMULATED: {node.object_name}.{node.method_name}()")
            return 0
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
        if op == "||":
            return str(left) + str(right)
        if op == "WAS DEFAULTED":
            return False
        if op == "WAS FOUND":
            return True
        if op == "IS NULL":
            return left is None or left == '' or left == 0
        raise SimulationError(f"Unknown binary operator: {op}")

    def _call_function(self, node: FunctionCall) -> Any:
        import math

        name = node.name.upper()

        # Functions whose first argument is an identifier name (not a variable).
        # Evaluate the first arg as its literal name string, not as a var lookup.
        _CONTEXT_FUNCS = {"GET_CONTEXT", "SET_CONTEXT", "NEED_CONTEXT",
                          "SET_INPUT", "GET_INPUT", "GET_OUTPUT"}
        if name in _CONTEXT_FUNCS and node.args:
            first = node.args[0]
            if isinstance(first, VariableRef):
                first_val = first.name  # Use the name as a string
            else:
                first_val = self._eval(first)
            args = [first_val] + [self._eval(a) for a in node.args[1:]]
        else:
            args = [self._eval(a) for a in node.args]

        # -- Numeric functions --
        if name == "ABS":
            return abs(args[0])
        if name == "CEIL":
            return math.ceil(args[0])
        if name == "FLOOR":
            return math.floor(args[0])
        if name == "POWER":
            return args[0] ** args[1]
        if name == "ROUND":
            decimals = int(args[1]) if len(args) >= 2 else 0
            return round(args[0], decimals)
        if name in ("ROUNDUP", "ROUND_UP"):
            decimals = int(args[1]) if len(args) >= 2 else 0
            factor = 10 ** decimals
            return math.ceil(args[0] * factor) / factor
        if name in ("TRUNC", "TRUNCATE"):
            decimals = int(args[1]) if len(args) >= 2 else 0
            factor = 10 ** decimals
            return int(args[0] * factor) / factor
        if name in ("GREATEST", "GREATEST_OF"):
            return max(args) if args else 0
        if name in ("LEAST", "LEAST_OF"):
            return min(args) if args else 0

        # -- String functions --
        if name == "LENGTH":
            return len(str(args[0])) if args else 0
        if name in ("SUBSTR", "SUBSTRING", "SUBSTRB"):
            s = str(args[0])
            start = int(args[1]) - 1 if len(args) >= 2 else 0  # 1-based
            length = int(args[2]) if len(args) >= 3 else len(s)
            return s[start:start + length]
        if name in ("INSTR", "INSTRB"):
            s = str(args[0])
            search = str(args[1])
            start = int(args[2]) - 1 if len(args) >= 3 else 0
            pos = s.find(search, start)
            return pos + 1 if pos >= 0 else 0  # 1-based, 0 = not found
        if name == "UPPER":
            return str(args[0]).upper()
        if name == "LOWER":
            return str(args[0]).lower()
        if name == "INITCAP":
            return str(args[0]).title()
        if name == "LPAD":
            s = str(args[0])
            width = int(args[1])
            pad = str(args[2]) if len(args) >= 3 else ' '
            return s.rjust(width, pad[0]) if pad else s
        if name == "RPAD":
            s = str(args[0])
            width = int(args[1])
            pad = str(args[2]) if len(args) >= 3 else ' '
            return s.ljust(width, pad[0]) if pad else s
        if name == "LTRIM":
            s = str(args[0])
            chars = str(args[1]) if len(args) >= 2 else ' '
            return s.lstrip(chars)
        if name == "RTRIM":
            s = str(args[0])
            chars = str(args[1]) if len(args) >= 2 else ' '
            return s.rstrip(chars)
        if name == "TRIM":
            return str(args[0]).strip() if args else ''
        if name == "REPLACE":
            s = str(args[0])
            old = str(args[1]) if len(args) >= 2 else ''
            new = str(args[2]) if len(args) >= 3 else ''
            return s.replace(old, new)
        if name == "TRANSLATE":
            s = str(args[0])
            frm = str(args[1]) if len(args) >= 2 else ''
            to = str(args[2]) if len(args) >= 3 else ''
            table = str.maketrans(frm, to[:len(frm)])
            return s.translate(table)
        if name == "CHR":
            return chr(int(args[0])) if args else ''

        # -- Conversion functions --
        if name in ("TO_NUMBER", "TO_NUM"):
            val = args[0] if args else 0
            if val == '' or val is None:
                return 0
            try:
                return float(val)
            except (ValueError, TypeError):
                raise SimulationError(f"TO_NUMBER: cannot convert {val!r}")
        if name in ("TO_CHAR", "TO_TEXT", "NUM_TO_CHAR", "DATE_TO_TEXT"):
            val = args[0]
            fmt = str(args[1]) if len(args) > 1 else ""
            if isinstance(val, datetime):
                if "YYYY" in fmt.upper() or "MON" in fmt.upper():
                    return val.strftime("%-d-%b-%Y")
                return val.strftime("%-d-%b-%Y")
            return str(val)
        if name == "TO_DATE":
            return _parse_date(str(args[0]))

        # -- Date functions (real date arithmetic) --
        if name == "ADD_DAYS":
            base = _parse_date(args[0]) if not isinstance(args[0], datetime) else args[0]
            return base + timedelta(days=int(args[1]))
        if name == "ADD_MONTHS":
            base = _parse_date(args[0]) if not isinstance(args[0], datetime) else args[0]
            months = int(args[1])
            month = base.month - 1 + months
            year = base.year + month // 12
            month = month % 12 + 1
            day = min(base.day, _last_day_of_month(year, month))
            return base.replace(year=year, month=month, day=day)
        if name == "ADD_YEARS":
            base = _parse_date(args[0]) if not isinstance(args[0], datetime) else args[0]
            years = int(args[1])
            try:
                return base.replace(year=base.year + years)
            except ValueError:
                return base.replace(year=base.year + years, day=28)
        if name == "DAYS_BETWEEN":
            d0 = _parse_date(args[0]) if not isinstance(args[0], datetime) else args[0]
            d1 = _parse_date(args[1]) if not isinstance(args[1], datetime) else args[1]
            return (d0 - d1).days
        if name == "MONTHS_BETWEEN":
            d0 = _parse_date(args[0]) if not isinstance(args[0], datetime) else args[0]
            d1 = _parse_date(args[1]) if not isinstance(args[1], datetime) else args[1]
            return (d0.year - d1.year) * 12 + (d0.month - d1.month)
        if name == "HOURS_BETWEEN":
            d0 = _parse_date(args[0]) if not isinstance(args[0], datetime) else args[0]
            d1 = _parse_date(args[1]) if not isinstance(args[1], datetime) else args[1]
            return (d0 - d1).days * 24
        if name == "LAST_DAY":
            base = _parse_date(args[0]) if not isinstance(args[0], datetime) else args[0]
            day = _last_day_of_month(base.year, base.month)
            return base.replace(day=day)
        if name == "NEXT_DAY":
            return _parse_date(args[0]) if not isinstance(args[0], datetime) else args[0]
        if name == "NEW_TIME":
            return _parse_date(args[0]) if not isinstance(args[0], datetime) else args[0]

        # -- Lookup functions (return placeholder in simulation) --
        if name == "GET_TABLE_VALUE":
            self._add_trace(f"SIMULATED: GET_TABLE_VALUE({', '.join(str(a) for a in args)}) → 0")
            return 0
        if name == "GET_LOOKUP_MEANING":
            self._add_trace(f"SIMULATED: GET_LOOKUP_MEANING({', '.join(str(a) for a in args)}) → ''")
            return ''
        if name == "RATES_HISTORY":
            self._add_trace(f"SIMULATED: RATES_HISTORY({', '.join(str(a) for a in args)}) → 0")
            return 0
        if name == "CALCULATE_HOURS_WORKED":
            self._add_trace(f"SIMULATED: CALCULATE_HOURS_WORKED → 0")
            return 0

        # -- Global variable functions (simulated) --
        if name in ("SET_TEXT", "SET_NUMBER", "SET_DATE"):
            return 0  # 0 = success
        if name in ("GET_TEXT", "GET_NUMBER", "GET_DATE"):
            return 0
        if name == "ISNULL":
            return 'N'
        if name in ("CLEAR_GLOBALS", "REMOVE_GLOBALS"):
            return 0

        # -- Accrual functions (simulated) --
        if name in ("GET_ABSENCE", "GET_ACCRUAL_BAND", "GET_CARRY_OVER",
                     "GET_NET_ACCRUAL", "GET_WORKING_DAYS", "GET_PAYROLL_PERIOD",
                     "GET_PERIOD_DATES", "GET_START_DATE", "GET_ASSIGNMENT_STATUS",
                     "GET_OTHER_NET_CONTRIBUTION", "GET_ASG_INACTIVE_DAYS",
                     "CALCULATE_PAYROLL_PERIODS"):
            self._add_trace(f"SIMULATED: {name}() → 0")
            return 0

        # -- Formula functions (simulated) --
        if name == "CALL_FORMULA":
            self._add_trace(f"SIMULATED: CALL_FORMULA({args[0] if args else '?'})")
            return 0
        if name == "LOOP_CONTROL":
            return 0
        if name in ("PUT_MESSAGE", "DEBUG", "RAISE_ERROR"):
            self._add_trace(f"{name}: {args[0] if args else ''}")
            return 0
        if name == "PAY_INTERNAL_LOG_WRITE":
            self._add_trace(f"LOG: {args[0] if args else ''}")
            return 0
        if name == "GET_CONTEXT":
            self._add_trace(f"SIMULATED: GET_CONTEXT({args[0] if args else '?'}) → 0")
            return 0

        raise SimulationError(f"Unknown function: {node.name}")
