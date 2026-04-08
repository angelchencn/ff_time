"""Extended validator tests for semantic and rule improvements.

Covers: GET_CONTEXT identifiers not flagged as undeclared,
formula templates API, formula type templates.
"""
from app.services.validator import validate_formula


def test_get_context_args_not_flagged_as_undeclared():
    """GET_CONTEXT first arg is an identifier name, not a variable."""
    code = """\
DEFAULT FOR StartTime IS 0
ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)
rule_id = GET_CONTEXT(HWM_RULE_ID, 0)
l_job_id = GET_CONTEXT(HWM_JOB_ID, 0)
l_assignment_id = GET_CONTEXT(HWM_ASSIGNMENT_ID, 0)
l_person_id = GET_CONTEXT(HWM_PERSON_ID, 0)
l_date = GET_CONTEXT(HWM_EFFECTIVE_DATE, StartTime)
RETURN ffs_id
"""
    result = validate_formula(code)
    # None of the HWM_* context names should trigger undeclared variable errors
    semantic_errors = [d for d in result.diagnostics if d.layer == "semantic" and d.severity == "error"]
    undeclared_names = [d.message for d in semantic_errors if "Undeclared" in d.message]
    assert len(undeclared_names) == 0, f"False positives: {undeclared_names}"


def test_set_input_get_output_args_not_flagged():
    """SET_INPUT/GET_OUTPUT first arg is a string key, not a variable."""
    code = """\
SET_INPUT('P_MODE', 'CALC')
EXECUTE('MY_FORMULA')
result = GET_OUTPUT('amount', 0)
RETURN result
"""
    result = validate_formula(code)
    semantic_errors = [d for d in result.diagnostics if d.layer == "semantic" and "Undeclared" in d.message]
    # P_MODE and amount are string literals, not var refs — should not appear
    assert not any("P_MODE" in d for d in semantic_errors)


def test_concat_operator_validates():
    code = """\
DEFAULT FOR name IS 'World'
msg = 'Hello ' || name
RETURN msg
"""
    result = validate_formula(code)
    assert result.valid is True


def test_was_not_defaulted_validates():
    code = """\
DEFAULT FOR hours IS 0
IF hours WAS NOT DEFAULTED THEN
    result = hours
ELSE
    result = 0
ENDIF
RETURN result
"""
    result = validate_formula(code)
    assert result.valid is True


def test_quoted_identifier_validates():
    code = '''\
DEFAULT FOR l_state IS 0
"AREA1" = l_state
RETURN "AREA1"
'''
    result = validate_formula(code)
    assert result.valid is True


def test_typed_string_validates():
    code = """\
DEFAULT FOR dt IS '01-JAN-1900'(DATE)
RETURN dt
"""
    result = validate_formula(code)
    assert result.valid is True


def test_empty_return_validates():
    code = """\
DEFAULT FOR x IS 0
IF x = 0 THEN
(
  RETURN
)
RETURN x
"""
    result = validate_formula(code)
    assert result.valid is True


def test_real_formula_with_log_and_context():
    """Full WFM-style formula should validate cleanly."""
    code = """\
DEFAULT FOR measure IS 0
DEFAULT FOR StartTime IS 0
DEFAULT FOR StopTime IS 0

INPUTS ARE measure, StartTime, StopTime

ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)
rule_id = GET_CONTEXT(HWM_RULE_ID, 0)

l_log = PAY_INTERNAL_LOG_WRITE('Formula - Enter')

l_message_code = ' '
l_message_severity = ' '

IF measure > 24 THEN
(
  l_message_code = 'HWM_FF_TER_DAILY_GT_MAX_ERR'
  l_message_severity = 'E'
)

l_log = PAY_INTERNAL_LOG_WRITE('Formula - Exit, code=' || l_message_code)

RETURN l_message_code, l_message_severity
"""
    result = validate_formula(code)
    assert result.valid is True, f"Errors: {[d.message for d in result.diagnostics]}"
