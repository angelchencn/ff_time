from app.services.validator import validate_formula


def test_valid_formula_returns_no_errors():
    code = """
DEFAULT FOR hours IS 0
INPUT IS rate
ot_pay = hours * rate
RETURN ot_pay
"""
    result = validate_formula(code)
    assert result.valid is True
    assert len(result.diagnostics) == 0


def test_undeclared_variable_error():
    code = """
DEFAULT FOR hours IS 0
result = hours * unknown_var
RETURN result
"""
    result = validate_formula(code)
    assert result.valid is False
    assert any(d.layer == "semantic" and "unknown_var" in d.message for d in result.diagnostics)


def test_syntax_error():
    code = "IF hours > THEN"
    result = validate_formula(code)
    assert result.valid is False
    assert any(d.layer == "syntax" for d in result.diagnostics)


def test_output_variable_not_assigned_warning():
    code = """
OUTPUT IS result
DEFAULT FOR hours IS 0
RETURN hours
"""
    result = validate_formula(code)
    assert any(d.severity == "warning" and "result" in d.message for d in result.diagnostics)
