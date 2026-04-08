package oracle.apps.hcm.formulas.core.jerseyTest;

import oracle.apps.hcm.formulas.core.jersey.parser.*;
import oracle.apps.hcm.formulas.core.jersey.service.*;
import oracle.apps.hcm.formulas.core.jersey.config.*;
import oracle.apps.hcm.formulas.core.jersey.model.*;
import oracle.apps.hcm.formulas.core.jersey.api.*;

import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes.*;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Mirrors test_parser_extended.py (20 tests).
 */
public class FFParserExtendedTest {

    private Program parse(String code) {
        var result = FFParser.parse(code);
        assertNotNull("Parse failed: " + result.diagnostics(), result.program());
        return result.program();
    }

    // -- String concatenation (||) ---------------------------------------------

    @Test
    public void concatOperator() {
        var p = parse("result = 'Hello' || ' ' || 'World'\nRETURN result");
        var assign = (Assignment) p.statements().get(0);
        assertTrue(assign.value() instanceof Concat);
    }

    @Test
    public void concatWithFunction() {
        var p = parse("msg = 'Hours=' || TO_CHAR(hours)\nRETURN msg");
        var assign = (Assignment) p.statements().get(0);
        assertTrue(assign.value() instanceof Concat);
    }

    // -- WAS NOT DEFAULTED -----------------------------------------------------

    @Test
    public void wasNotDefaulted() {
        var p = parse("""
                DEFAULT FOR hours IS 0
                IF hours WAS NOT DEFAULTED THEN
                    result = hours
                ELSE
                    result = 0
                ENDIF
                RETURN result
                """);
        var ifStmt = (IfStatement) p.statements().get(1);
        assertTrue(ifStmt.condition() instanceof WasNotDefaulted);
    }

    // -- Quoted identifiers ----------------------------------------------------

    @Test
    public void quotedNameAssignment() {
        var p = parse("""
                DEFAULT FOR l_state IS 0
                "AREA1" = l_state
                RETURN l_state
                """);
        var assign = (Assignment) p.statements().get(1);
        assertEquals("AREA1", assign.target());
    }

    @Test
    public void quotedNameInReturn() {
        var p = parse("""
                "AREA1" = 100
                RETURN "AREA1"
                """);
        assertTrue(p.statements().get(1) instanceof ReturnStatement);
    }

    // -- Array access and assignment -------------------------------------------

    @Test
    public void arrayAccess() {
        var p = parse("val = items[1]\nRETURN val");
        var assign = (Assignment) p.statements().get(0);
        assertTrue(assign.value() instanceof ArrayAccess);
        assertEquals("items", ((ArrayAccess) assign.value()).name());
    }

    @Test
    public void arrayAssignment() {
        var p = parse("items[1] = 100\nRETURN items[1]");
        assertTrue(p.statements().get(0) instanceof ArrayAssignment);
        assertEquals("items", ((ArrayAssignment) p.statements().get(0)).target());
    }

    // -- Method calls ----------------------------------------------------------

    @Test
    public void methodCall() {
        var p = parse("idx = arr.FIRST(-1)\nRETURN idx");
        var assign = (Assignment) p.statements().get(0);
        assertTrue(assign.value() instanceof MethodCall);
        var mc = (MethodCall) assign.value();
        assertEquals("arr", mc.object());
        assertEquals("FIRST", mc.method());
    }

    // -- CALL_FORMULA ----------------------------------------------------------

    @Test
    public void callFormulaSimple() {
        var p = parse("""
                CALL_FORMULA('MY_SUB_FORMULA',
                  l_input > 'param_in',
                  l_output < 'param_out' DEFAULT 0
                )
                RETURN l_output
                """);
        assertTrue(p.statements().get(0) instanceof CallFormula);
    }

    // -- CHANGE_CONTEXTS -------------------------------------------------------

    @Test
    public void changeContexts() {
        var p = parse("""
                DEFAULT FOR x IS 0
                CHANGE_CONTEXTS(PART_NAME = 'GB_PENSION')
                x = 1
                RETURN x
                """);
        assertTrue(p.statements().get(1) instanceof ChangeContexts);
    }

    // -- EXECUTE ---------------------------------------------------------------

    @Test
    public void executeStatement() {
        var p = parse("""
                DEFAULT FOR x IS 0
                EXECUTE('HRX_MX_EARN_CALCULATION')
                x = 1
                RETURN x
                """);
        assertTrue(p.statements().get(1) instanceof Execute);
        assertEquals("HRX_MX_EARN_CALCULATION", ((Execute) p.statements().get(1)).formulaName());
    }

    // -- Bare function call ----------------------------------------------------

    @Test
    public void bareFunctionCall() {
        var p = parse("""
                SET_INPUT('state', 'CA')
                EXECUTE('MY_FORMULA')
                result = GET_OUTPUT('amount', 0)
                RETURN result
                """);
        assertTrue(p.statements().get(0) instanceof FunctionCall);
        assertEquals("SET_INPUT", ((FunctionCall) p.statements().get(0)).name());
    }

    // -- DEFAULT_DATA_VALUE ----------------------------------------------------

    @Test
    public void defaultDataValue() {
        var p = parse("""
                DEFAULT_DATA_VALUE FOR MY_VAR IS 'default_val'
                RETURN MY_VAR
                """);
        assertTrue(p.statements().get(0) instanceof DefaultDataValue);
        assertEquals("MY_VAR", ((DefaultDataValue) p.statements().get(0)).name());
    }

    // -- OUTPUTS ARE -----------------------------------------------------------

    @Test
    public void outputsAre() {
        var p = parse("""
                OUTPUTS ARE result1, result2
                result1 = 1
                result2 = 2
                RETURN result1, result2
                """);
        long outputCount = p.statements().stream()
                .filter(s -> s instanceof OutputDecl)
                .count();
        assertEquals(2, outputCount);
    }

    // -- Empty RETURN ----------------------------------------------------------

    @Test
    public void emptyReturn() {
        var p = parse("""
                DEFAULT FOR x IS 0
                IF x = 0 THEN
                (
                  RETURN
                )
                RETURN x
                """);
        assertNotNull(p);
    }

    // -- Typed string ----------------------------------------------------------

    @Test
    public void typedStringDate() {
        var result = FFParser.parse("""
                DEFAULT FOR dt IS '01-JAN-1900'(DATE)
                dt = '2024-01-15 00:00:00'(DATE)
                RETURN dt
                """);
        assertNotNull(result.program());
        assertTrue(result.diagnostics().isEmpty());
    }

    // -- PAY_INTERNAL_LOG_WRITE with concat ------------------------------------

    @Test
    public void logWriteWithConcat() {
        var result = FFParser.parse("""
                DEFAULT FOR HOURS IS 0
                INPUTS ARE HOURS
                l_log = PAY_INTERNAL_LOG_WRITE('Entry - Hours=' || TO_CHAR(HOURS))
                RETURN HOURS
                """);
        assertNotNull(result.program());
        assertTrue(result.diagnostics().isEmpty());
    }

    // -- GET_CONTEXT calls -----------------------------------------------------

    @Test
    public void getContextCalls() {
        var result = FFParser.parse("""
                ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)
                rule_id = GET_CONTEXT(HWM_RULE_ID, 0)
                RETURN ffs_id
                """);
        assertNotNull(result.program());
        assertTrue(result.diagnostics().isEmpty());
    }

    // -- Real-world formula ----------------------------------------------------

    @Test
    public void realWorldTimeEntryRule() {
        var result = FFParser.parse("""
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
                """);
        assertNotNull("Parse failed: " + result.diagnostics(), result.program());
        assertTrue("Errors: " + result.diagnostics(), result.diagnostics().isEmpty());
    }
}
