package oracle.apps.hcm.formulas.core.jerseyTest;

import oracle.apps.hcm.formulas.core.jersey.parser.*;

import oracle.apps.hcm.formulas.core.jersey.service.*;
import oracle.apps.hcm.formulas.core.jersey.config.*;
import oracle.apps.hcm.formulas.core.jersey.model.*;
import oracle.apps.hcm.formulas.core.jersey.api.*;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Mirrors test_validator.py (4 tests) + test_validator_extended.py (8 tests).
 * Note: Java validator currently only does syntax validation.
 * Semantic/rules tests check that valid formulas parse cleanly.
 */
public class ValidatorTest {

    private final ValidatorService validator = new ValidatorService();

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> diagnostics(String code) {
        var result = validator.validate(code);
        return (List<Map<String, Object>>) result.get("diagnostics");
    }

    private boolean isValid(String code) {
        return (boolean) validator.validate(code).get("valid");
    }

    // -- test_validator.py -----------------------------------------------------

    @Test
    public void undeclaredVariableError() {
        assertFalse(isValid("""
                DEFAULT FOR hours IS 0
                result = hours * unknown_var
                RETURN result
                """));
        var diags = diagnostics("""
                DEFAULT FOR hours IS 0
                result = hours * unknown_var
                RETURN result
                """);
        assertTrue(diags.stream().anyMatch(d ->
                "semantic".equals(d.get("layer")) && d.get("message").toString().contains("unknown_var")));
    }

    @Test
    public void outputVariableNotAssignedWarning() {
        var diags = diagnostics("""
                OUTPUT IS result
                DEFAULT FOR hours IS 0
                RETURN hours
                """);
        assertTrue(diags.stream().anyMatch(d ->
                "warning".equals(d.get("severity")) && d.get("message").toString().contains("result")));
    }

    @Test
    public void getContextArgsNotFlaggedAsUndeclared() {
        assertTrue(isValid("""
                DEFAULT FOR StartTime IS 0
                ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)
                rule_id = GET_CONTEXT(HWM_RULE_ID, 0)
                l_job_id = GET_CONTEXT(HWM_JOB_ID, 0)
                l_date = GET_CONTEXT(HWM_EFFECTIVE_DATE, StartTime)
                RETURN ffs_id
                """));
    }

    @Test
    public void setInputGetOutputArgsNotFlagged() {
        assertTrue(isValid("""
                SET_INPUT('P_MODE', 'CALC')
                EXECUTE('MY_FORMULA')
                result = GET_OUTPUT('amount', 0)
                RETURN result
                """));
    }

    @Test
    public void validFormulaReturnsNoErrors() {
        assertTrue(isValid("""
                DEFAULT FOR hours IS 0
                INPUT IS rate
                ot_pay = hours * rate
                RETURN ot_pay
                """));
    }

    @Test
    public void syntaxError() {
        assertFalse(isValid("IF hours > THEN"));
        var diags = diagnostics("IF hours > THEN");
        assertTrue(diags.stream().anyMatch(d -> "syntax".equals(d.get("layer"))));
    }

    // -- test_validator_extended.py --------------------------------------------

    @Test
    public void concatOperatorValidates() {
        assertTrue(isValid("""
                DEFAULT FOR name IS 'World'
                msg = 'Hello ' || name
                RETURN msg
                """));
    }

    @Test
    public void wasNotDefaultedValidates() {
        assertTrue(isValid("""
                DEFAULT FOR hours IS 0
                IF hours WAS NOT DEFAULTED THEN
                    result = hours
                ELSE
                    result = 0
                ENDIF
                RETURN result
                """));
    }

    @Test
    public void quotedIdentifierValidates() {
        assertTrue(isValid("""
                DEFAULT FOR l_state IS 0
                "AREA1" = l_state
                RETURN "AREA1"
                """));
    }

    @Test
    public void typedStringValidates() {
        assertTrue(isValid("""
                DEFAULT FOR dt IS '01-JAN-1900'(DATE)
                RETURN dt
                """));
    }

    @Test
    public void emptyReturnValidates() {
        assertTrue(isValid("""
                DEFAULT FOR x IS 0
                IF x = 0 THEN
                (
                  RETURN
                )
                RETURN x
                """));
    }

    // -- New rules -------------------------------------------------------------

    @Test
    public void reservedWordAsVariableName() {
        // "ALIAS" used as an assigned variable name
        var diags = diagnostics("""
                DEFAULT FOR hours IS 0
                ALIAS = hours * 2
                RETURN ALIAS
                """);
        // Parser may catch this as syntax error, or semantic check catches it
        assertFalse(diags.isEmpty());
    }

    @Test
    public void variableNameTooLong() {
        String longName = "A".repeat(81);
        var diags = diagnostics(
                "DEFAULT FOR " + longName + " IS 0\n" + longName + " = 1\nRETURN " + longName);
        assertTrue(diags.stream().anyMatch(d ->
                d.get("message").toString().contains("exceeds 80 characters")));
    }

    @Test
    public void inputVariableAssignedWarning() {
        var diags = diagnostics("""
                DEFAULT FOR hours IS 0
                INPUTS ARE hours
                hours = 99
                RETURN hours
                """);
        assertTrue(diags.stream().anyMatch(d ->
                "warning".equals(d.get("severity"))
                        && d.get("message").toString().contains("INPUT variable")
                        && d.get("message").toString().contains("read-only")));
    }

    @Test
    public void statementOrderWarning() {
        var diags = diagnostics("""
                INPUTS ARE hours
                DEFAULT FOR hours IS 0
                result = hours * 2
                RETURN result
                """);
        assertTrue(diags.stream().anyMatch(d ->
                d.get("message").toString().contains("Recommended order")));
    }

    @Test
    public void inputWithoutDefaultWarning() {
        var diags = diagnostics("""
                INPUTS ARE hours, rate
                result = hours * rate
                RETURN result
                """);
        assertTrue(diags.stream().anyMatch(d ->
                d.get("message").toString().contains("no DEFAULT FOR")));
    }

    @Test
    public void inputWithDefaultNoWarning() {
        var diags = diagnostics("""
                DEFAULT FOR hours IS 0
                DEFAULT FOR rate IS 0
                INPUTS ARE hours, rate
                result = hours * rate
                RETURN result
                """);
        // Should NOT have "no DEFAULT FOR" warning
        assertFalse(diags.stream().anyMatch(d ->
                d.get("message").toString().contains("no DEFAULT FOR")));
    }

    @Test
    public void missingReturnError() {
        assertFalse(isValid("""
                DEFAULT FOR hours IS 0
                result = hours * 2
                """));
        var diags = diagnostics("""
                DEFAULT FOR hours IS 0
                result = hours * 2
                """);
        assertTrue(diags.stream().anyMatch(d ->
                d.get("message").toString().contains("no RETURN")));
    }

    @Test
    public void realFormulaWithLogAndContext() {
        assertTrue(isValid("""
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
                """));
    }
}
