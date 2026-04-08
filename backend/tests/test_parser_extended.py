"""Extended parser tests for grammar constructs added to support real Oracle Fast Formulas.

Covers: ||, WAS NOT DEFAULTED, quoted identifiers, array access/assignment,
method calls, CALL_FORMULA, CHANGE_CONTEXTS, EXECUTE, bare function calls,
DEFAULT_DATA_VALUE, OUTPUTS ARE, empty RETURN, typed strings.
"""
from app.parser.ff_parser import parse_formula
from app.parser.ast_nodes import (
    ArrayAccess,
    ArrayAssignment,
    Assignment,
    BinaryOp,
    CallFormulaStatement,
    ChangeContextsStatement,
    ExecuteStatement,
    FunctionCall,
    MethodCall,
    NumberLiteral,
    Program,
    ReturnStatement,
    StringLiteral,
    UnaryOp,
    VariableDecl,
    VariableRef,
)


# ── String concatenation (||) ──────────────────────────────────────────────

def test_concat_operator():
    code = "result = 'Hello' || ' ' || 'World'\nRETURN result"
    ast = parse_formula(code)
    assert isinstance(ast, Program)
    assign = ast.statements[0]
    assert isinstance(assign, Assignment)
    assert isinstance(assign.value, BinaryOp)
    assert assign.value.op == "||"


def test_concat_with_function():
    code = "msg = 'Hours=' || TO_CHAR(hours)\nRETURN msg"
    ast = parse_formula(code)
    assign = ast.statements[0]
    assert isinstance(assign.value, BinaryOp)
    assert assign.value.op == "||"


# ── WAS NOT DEFAULTED ──────────────────────────────────────────────────────

def test_was_not_defaulted():
    code = """\
DEFAULT FOR hours IS 0
IF hours WAS NOT DEFAULTED THEN
    result = hours
ELSE
    result = 0
ENDIF
RETURN result
"""
    ast = parse_formula(code)
    assert isinstance(ast, Program)
    if_stmt = ast.statements[1]
    # WAS NOT DEFAULTED is parsed as NOT(WAS DEFAULTED)
    assert isinstance(if_stmt.condition, UnaryOp)
    assert if_stmt.condition.op == "NOT"


# ── Quoted identifiers ("NAME") ────────────────────────────────────────────

def test_quoted_name_assignment():
    code = '''\
DEFAULT FOR l_state IS 0
"AREA1" = l_state
RETURN l_state
'''
    ast = parse_formula(code)
    assign = ast.statements[1]
    assert isinstance(assign, Assignment)
    assert assign.var_name == "AREA1"


def test_quoted_name_in_return():
    code = '''\
"AREA1" = 100
RETURN "AREA1"
'''
    ast = parse_formula(code)
    ret = ast.statements[1]
    assert isinstance(ret, ReturnStatement)
    # Quoted name in RETURN is parsed as StringLiteral (grammar treats it as QUOTED_NAME)
    assert ret.value.value == "AREA1"


# ── Array access and assignment ────────────────────────────────────────────

def test_array_access():
    code = "val = items[1]\nRETURN val"
    ast = parse_formula(code)
    assign = ast.statements[0]
    assert isinstance(assign.value, ArrayAccess)
    assert assign.value.name == "items"


def test_array_assignment():
    code = "items[1] = 100\nRETURN items[1]"
    ast = parse_formula(code)
    arr_assign = ast.statements[0]
    assert isinstance(arr_assign, ArrayAssignment)
    assert arr_assign.name == "items"


# ── Method calls (array methods like .FIRST, .NEXT) ────────────────────────

def test_method_call():
    code = "idx = arr.FIRST(-1)\nRETURN idx"
    ast = parse_formula(code)
    assign = ast.statements[0]
    assert isinstance(assign.value, MethodCall)
    assert assign.value.object_name == "arr"
    assert assign.value.method_name == "FIRST"


# ── CALL_FORMULA statement ─────────────────────────────────────────────────

def test_call_formula_simple():
    code = """\
CALL_FORMULA('MY_SUB_FORMULA',
  l_input > 'param_in',
  l_output < 'param_out' DEFAULT 0
)
RETURN l_output
"""
    ast = parse_formula(code)
    assert isinstance(ast.statements[0], CallFormulaStatement)


# ── CHANGE_CONTEXTS statement ──────────────────────────────────────────────

def test_change_contexts():
    code = """\
DEFAULT FOR x IS 0
CHANGE_CONTEXTS(PART_NAME = 'GB_PENSION')
x = 1
RETURN x
"""
    ast = parse_formula(code)
    assert isinstance(ast.statements[1], ChangeContextsStatement)


# ── EXECUTE statement ──────────────────────────────────────────────────────

def test_execute_statement():
    code = """\
DEFAULT FOR x IS 0
EXECUTE('HRX_MX_EARN_CALCULATION')
x = 1
RETURN x
"""
    ast = parse_formula(code)
    assert isinstance(ast.statements[1], ExecuteStatement)
    assert ast.statements[1].formula_name == "HRX_MX_EARN_CALCULATION"


# ── Bare function call as statement ────────────────────────────────────────

def test_bare_function_call():
    code = """\
SET_INPUT('state', 'CA')
EXECUTE('MY_FORMULA')
result = GET_OUTPUT('amount', 0)
RETURN result
"""
    ast = parse_formula(code)
    # SET_INPUT is parsed as bare_func_call -> Assignment with __SET_INPUT_result
    assert isinstance(ast.statements[0], Assignment)
    assert isinstance(ast.statements[0].value, FunctionCall)
    assert ast.statements[0].value.name == "SET_INPUT"


# ── DEFAULT_DATA_VALUE ─────────────────────────────────────────────────────

def test_default_data_value():
    code = """\
DEFAULT_DATA_VALUE FOR MY_VAR IS 'default_val'
RETURN MY_VAR
"""
    ast = parse_formula(code)
    decl = ast.statements[0]
    assert isinstance(decl, VariableDecl)
    assert decl.kind == "default"
    assert decl.var_name == "MY_VAR"


# ── OUTPUTS ARE ────────────────────────────────────────────────────────────

def test_outputs_are():
    code = """\
OUTPUTS ARE result1, result2
result1 = 1
result2 = 2
RETURN result1, result2
"""
    ast = parse_formula(code)
    # OUTPUTS ARE produces multiple VariableDecl(kind="output")
    outputs = [s for s in ast.statements if isinstance(s, VariableDecl) and s.kind == "output"]
    assert len(outputs) == 2


# ── Empty RETURN ───────────────────────────────────────────────────────────

def test_empty_return():
    code = """\
DEFAULT FOR x IS 0
IF x = 0 THEN
(
  RETURN
)
RETURN x
"""
    ast = parse_formula(code)
    assert isinstance(ast, Program)


# ── Typed string literal ──────────────────────────────────────────────────

def test_typed_string_date():
    code = """\
DEFAULT FOR dt IS '01-JAN-1900'(DATE)
dt = '2024-01-15 00:00:00'(DATE)
RETURN dt
"""
    ast = parse_formula(code)
    assert isinstance(ast, Program)
    assert len(ast.diagnostics if hasattr(ast, 'diagnostics') else []) == 0


# ── PAY_INTERNAL_LOG_WRITE with concat ─────────────────────────────────────

def test_log_write_with_concat():
    code = """\
DEFAULT FOR HOURS IS 0
INPUTS ARE HOURS
l_log = PAY_INTERNAL_LOG_WRITE('Entry - Hours=' || TO_CHAR(HOURS))
RETURN HOURS
"""
    result = parse_formula(code, return_diagnostics=True)
    assert result.program is not None
    assert len(result.diagnostics) == 0


# ── GET_CONTEXT calls ─────────────────────────────────────────────────────

def test_get_context_calls():
    code = """\
ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)
rule_id = GET_CONTEXT(HWM_RULE_ID, 0)
RETURN ffs_id
"""
    result = parse_formula(code, return_diagnostics=True)
    assert result.program is not None
    assert len(result.diagnostics) == 0


# ── Complex real-world formula ────────────────────────────────────────────

def test_real_world_time_entry_rule():
    """Simplified version of a real ORA_WFM_TER formula."""
    code = """\
DEFAULT FOR HWM_CTXARY_RECORD_POSITIONS IS 'x'
DEFAULT FOR measure IS 0
DEFAULT FOR StartTime IS 0
DEFAULT FOR StopTime IS 0

INPUTS ARE
    measure,
    StartTime,
    StopTime

ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)
rule_id = GET_CONTEXT(HWM_RULE_ID, 0)

l_log = PAY_INTERNAL_LOG_WRITE('TER_VALIDATION - Enter')

l_message_code = ' '
l_message_severity = ' '

l_job_id = GET_CONTEXT(HWM_JOB_ID, 0)
l_assignment_id = GET_CONTEXT(HWM_ASSIGNMENT_ID, 0)

IF l_job_id = 0 OR ISNULL(l_job_id) THEN
(
  l_message_code = 'HWM_FF_TER_NO_JOB_ERR'
  l_message_severity = 'E'
)

l_log = PAY_INTERNAL_LOG_WRITE('TER_VALIDATION - Exit')

RETURN l_message_code, l_message_severity
"""
    result = parse_formula(code, return_diagnostics=True)
    assert result.program is not None
    assert len(result.diagnostics) == 0
