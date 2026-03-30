from app.parser.ff_parser import parse_formula
from app.parser.ast_nodes import Program, VariableDecl, IfStatement, ReturnStatement


def test_parse_simple_formula():
    code = """
DEFAULT FOR hours IS 0
INPUT IS rate
ot_pay = hours * rate
RETURN ot_pay
"""
    ast = parse_formula(code)
    assert isinstance(ast, Program)
    assert len(ast.statements) == 4
    assert isinstance(ast.statements[0], VariableDecl)
    assert ast.statements[0].var_name == "hours"


def test_parse_if_else():
    code = """
IF hours > 40 THEN
    ot_hours = hours - 40
ELSE
    ot_hours = 0
ENDIF
RETURN ot_hours
"""
    ast = parse_formula(code)
    assert isinstance(ast.statements[0], IfStatement)
    assert isinstance(ast.statements[1], ReturnStatement)


def test_parse_syntax_error_returns_diagnostics():
    code = "IF hours > THEN"
    result = parse_formula(code, return_diagnostics=True)
    assert result.diagnostics is not None
    assert len(result.diagnostics) > 0
    assert result.diagnostics[0].severity == "error"
