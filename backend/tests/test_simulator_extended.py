"""Extended simulator tests for new interpreter features."""
from app.services.simulator import simulate_formula


def test_concat_operator_simulation():
    code = """\
DEFAULT FOR name IS 'World'
result = 'Hello ' || name
RETURN result
"""
    result = simulate_formula(code, {"name": "Oracle"})
    assert result.status == "SUCCESS"
    assert result.output_data["result"] == "Hello Oracle"


def test_concat_chain():
    code = """\
a = 'A' || 'B' || 'C'
RETURN a
"""
    result = simulate_formula(code, {})
    assert result.status == "SUCCESS"
    assert result.output_data["a"] == "ABC"


def test_pay_internal_log_write():
    code = """\
DEFAULT FOR hours IS 0
l_log = PAY_INTERNAL_LOG_WRITE('Entry - hours=' || TO_CHAR(hours))
result = hours * 2
RETURN result
"""
    result = simulate_formula(code, {"hours": 10})
    assert result.status == "SUCCESS"
    assert result.output_data["result"] == 20.0


def test_get_context_simulation():
    code = """\
ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)
RETURN ffs_id
"""
    result = simulate_formula(code, {})
    assert result.status == "SUCCESS"
    # GET_CONTEXT returns simulated 0
    assert result.output_data["ffs_id"] == 0


def test_isnull_function():
    code = """\
DEFAULT FOR x IS 0
result = ISNULL(x)
RETURN result
"""
    result = simulate_formula(code, {"x": 0})
    assert result.status == "SUCCESS"


def test_was_not_defaulted_simulation():
    code = """\
DEFAULT FOR hours IS 0
IF hours WAS NOT DEFAULTED THEN
    result = hours
ELSE
    result = 99
ENDIF
RETURN result
"""
    # WAS DEFAULTED compares hours with 1 (AST placeholder).
    # For hours=10: 10 == 1 is False, NOT(False) is True -> THEN branch.
    result = simulate_formula(code, {"hours": 10})
    assert result.status == "SUCCESS"
    assert result.output_data["result"] == 10.0
