"""Tests for the simulator service."""
from app.services.simulator import simulate_formula


def test_simple_arithmetic():
    code = """
DEFAULT FOR hours IS 0
DEFAULT FOR rate IS 0
ot_pay = hours * rate
RETURN ot_pay
"""
    result = simulate_formula(code, {"hours": 10, "rate": 1.5})
    assert result.status == "SUCCESS"
    assert result.output_data["ot_pay"] == 15.0


def test_if_else_branching():
    code = """
DEFAULT FOR hours IS 0
IF hours > 40 THEN
    ot_hours = hours - 40
ELSE
    ot_hours = 0
ENDIF
RETURN ot_hours
"""
    result = simulate_formula(code, {"hours": 45})
    assert result.output_data["ot_hours"] == 5.0
    result2 = simulate_formula(code, {"hours": 30})
    assert result2.output_data["ot_hours"] == 0.0


def test_execution_trace_populated():
    code = """
DEFAULT FOR hours IS 0
ot_pay = hours * 2
RETURN ot_pay
"""
    result = simulate_formula(code, {"hours": 10})
    assert len(result.execution_trace) > 0
    assert any("ot_pay" in step.get("statement", "") for step in result.execution_trace)


def test_division_by_zero_error():
    code = """
DEFAULT FOR x IS 0
result = 10 / x
RETURN result
"""
    result = simulate_formula(code, {"x": 0})
    assert result.status == "ERROR"
    assert result.error is not None
