"""Tests for bugs discovered during the 2026-04-01 session.

Covers:
- WAS FOUND / WAS NOT FOUND syntax
- Date arithmetic (ADD_DAYS, ADD_MONTHS, ADD_YEARS, DAYS_BETWEEN, etc.)
- DEFAULT FOR variables appearing in output when reassigned
- fix_default_types post-processor
- TO_CHAR date formatting
"""
from app.parser.ff_parser import parse_formula
from app.parser.interpreter import Interpreter
from app.services.ai_service import fix_default_types


# ── WAS FOUND / WAS NOT FOUND ──────────────────────────────────────────────

class TestWasFound:
    def test_was_not_found_parses(self):
        code = """
DEFAULT FOR l_flag IS 'N'
l_flag = GET_LOOKUP_MEANING('HR_TEST', 'Y')
IF l_flag WAS NOT FOUND THEN
  l_flag = 'N'
END IF
RETURN l_flag
"""
        ast = parse_formula(code)
        assert ast is not None

    def test_was_found_parses(self):
        code = """
DEFAULT FOR l_flag IS 'N'
l_flag = GET_LOOKUP_MEANING('HR_TEST', 'Y')
IF l_flag WAS FOUND THEN
  l_flag = 'Y'
END IF
RETURN l_flag
"""
        ast = parse_formula(code)
        assert ast is not None

    def test_was_not_found_simulates(self):
        code = """
DEFAULT FOR l_val IS 'X'
IF l_val WAS NOT FOUND THEN
  l_result = 1
ELSE
  l_result = 0
END IF
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"l_val": "X"})
        result = interp.run(ast)
        # WAS FOUND defaults to True, so WAS NOT FOUND → False → else branch
        assert result["l_result"] == 0

    def test_was_found_simulates(self):
        code = """
DEFAULT FOR l_val IS 'X'
IF l_val WAS FOUND THEN
  l_result = 1
ELSE
  l_result = 0
END IF
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"l_val": "X"})
        result = interp.run(ast)
        assert result["l_result"] == 1


# ── Date arithmetic ────────────────────────────────────────────────────────

class TestDateArithmetic:
    def test_add_days(self):
        code = """
DEFAULT FOR d IS '01-JAN-0001'
INPUTS ARE d
l_result = ADD_DAYS(d, 14)
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"d": "2-Apr-2026"})
        result = interp.run(ast)
        assert result["l_result"] == "16-Apr-2026"

    def test_add_months(self):
        code = """
DEFAULT FOR d IS '01-JAN-0001'
INPUTS ARE d
l_result = ADD_MONTHS(d, 1)
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"d": "1-Apr-2026"})
        result = interp.run(ast)
        assert result["l_result"] == "1-May-2026"

    def test_add_months_end_of_month(self):
        """Adding 1 month to Jan 31 should give Feb 28."""
        code = """
DEFAULT FOR d IS '01-JAN-0001'
INPUTS ARE d
l_result = ADD_MONTHS(d, 1)
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"d": "31-Jan-2026"})
        result = interp.run(ast)
        assert result["l_result"] == "28-Feb-2026"

    def test_add_years(self):
        code = """
DEFAULT FOR d IS '01-JAN-0001'
INPUTS ARE d
l_result = ADD_YEARS(d, 2)
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"d": "1-Apr-2026"})
        result = interp.run(ast)
        assert result["l_result"] == "1-Apr-2028"

    def test_days_between(self):
        code = """
DEFAULT FOR d1 IS '01-JAN-0001'
DEFAULT FOR d2 IS '01-JAN-0001'
INPUTS ARE d1, d2
l_result = DAYS_BETWEEN(d1, d2)
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"d1": "15-Apr-2026", "d2": "1-Apr-2026"})
        result = interp.run(ast)
        assert result["l_result"] == 14

    def test_months_between(self):
        code = """
DEFAULT FOR d1 IS '01-JAN-0001'
DEFAULT FOR d2 IS '01-JAN-0001'
INPUTS ARE d1, d2
l_result = MONTHS_BETWEEN(d1, d2)
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"d1": "1-Jun-2026", "d2": "1-Jan-2026"})
        result = interp.run(ast)
        assert result["l_result"] == 5

    def test_to_char_date(self):
        code = """
DEFAULT FOR d IS '01-JAN-0001'
INPUTS ARE d
l_next = ADD_DAYS(d, 7)
l_result = TO_CHAR(l_next, 'DD-MON-YYYY')
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"d": "1-Apr-2026"})
        result = interp.run(ast)
        assert result["l_result"] == "8-Apr-2026"

    def test_to_date(self):
        code = """
DEFAULT FOR s IS ' '
INPUTS ARE s
l_result = ADD_DAYS(TO_DATE(s), 1)
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"s": "1-Apr-2026"})
        result = interp.run(ast)
        assert result["l_result"] == "2-Apr-2026"

    def test_last_day(self):
        code = """
DEFAULT FOR d IS '01-JAN-0001'
INPUTS ARE d
l_result = LAST_DAY(d)
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"d": "15-Feb-2026"})
        result = interp.run(ast)
        assert result["l_result"] == "28-Feb-2026"


# ── DEFAULT FOR output visibility ──────────────────────────────────────────

class TestDefaultOutputVisibility:
    def test_reassigned_default_appears_in_output(self):
        """A DEFAULT FOR variable that is reassigned should appear in output."""
        code = """
DEFAULT FOR SCHEDULE_DATE IS '01-JAN-0001'
DEFAULT FOR l_next IS '01-JAN-0001'
INPUTS ARE SCHEDULE_DATE
l_next = ADD_DAYS(SCHEDULE_DATE, 14)
RETURN l_next
"""
        ast = parse_formula(code)
        interp = Interpreter({"SCHEDULE_DATE": "1-Apr-2026"})
        result = interp.run(ast)
        assert "l_next" in result
        assert result["l_next"] == "15-Apr-2026"

    def test_unassigned_default_excluded_from_output(self):
        """A DEFAULT FOR variable that is never reassigned should not appear."""
        code = """
DEFAULT FOR x IS 0
DEFAULT FOR y IS 0
INPUTS ARE x
l_result = x + 1
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"x": 5})
        result = interp.run(ast)
        assert "l_result" in result
        assert result["l_result"] == 6
        assert "y" not in result

    def test_input_variable_excluded_from_output(self):
        """INPUTS ARE variables should never appear in output."""
        code = """
DEFAULT FOR x IS 0
INPUTS ARE x
l_result = x * 2
RETURN l_result
"""
        ast = parse_formula(code)
        interp = Interpreter({"x": 10})
        result = interp.run(ast)
        assert "x" not in result
        assert result["l_result"] == 20


# ── fix_default_types post-processor ───────────────────────────────────────

class TestFixDefaultTypes:
    def test_name_variable_gets_string_default(self):
        code = "DEFAULT FOR BASE_TASK_NAME IS 0"
        fixed = fix_default_types(code)
        assert fixed == "DEFAULT FOR BASE_TASK_NAME IS ' '"

    def test_counter_variable_stays_numeric(self):
        code = "DEFAULT FOR REPEAT_COUNTER IS 0"
        fixed = fix_default_types(code)
        assert fixed == "DEFAULT FOR REPEAT_COUNTER IS 0"

    def test_date_variable_gets_date_default(self):
        code = "DEFAULT FOR EFFECTIVE_DATE IS 0"
        fixed = fix_default_types(code)
        assert fixed == "DEFAULT FOR EFFECTIVE_DATE IS '01-JAN-0001'(DATE)"

    def test_status_variable_gets_string_default(self):
        code = "DEFAULT FOR ROLLBACK_STATUS IS 0"
        fixed = fix_default_types(code)
        assert fixed == "DEFAULT FOR ROLLBACK_STATUS IS ' '"

    def test_type_variable_gets_string_default(self):
        code = "DEFAULT FOR PROCESS_TYPE IS 0"
        fixed = fix_default_types(code)
        assert fixed == "DEFAULT FOR PROCESS_TYPE IS ' '"

    def test_already_correct_string_not_changed(self):
        code = "DEFAULT FOR BASE_TASK_NAME IS ' '"
        fixed = fix_default_types(code)
        assert fixed == "DEFAULT FOR BASE_TASK_NAME IS ' '"

    def test_already_correct_number_not_changed(self):
        code = "DEFAULT FOR HOURS_WORKED IS 0"
        fixed = fix_default_types(code)
        assert fixed == "DEFAULT FOR HOURS_WORKED IS 0"

    def test_multiline(self):
        code = """DEFAULT FOR BASE_TASK_NAME IS 0
DEFAULT FOR REPEAT_COUNTER IS 0
DEFAULT FOR EFFECTIVE_DATE IS 0
DEFAULT FOR PROCESS_TYPE IS 0"""
        fixed = fix_default_types(code)
        lines = fixed.strip().split("\n")
        assert "IS ' '" in lines[0]        # NAME → string
        assert "IS 0" in lines[1]           # COUNTER → number
        assert "(DATE)" in lines[2]         # DATE → date
        assert "IS ' '" in lines[3]         # TYPE → string

    def test_id_variable_stays_numeric(self):
        code = "DEFAULT FOR PAYROLL_RUN_ID IS 0"
        fixed = fix_default_types(code)
        assert fixed == "DEFAULT FOR PAYROLL_RUN_ID IS 0"
