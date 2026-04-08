package oracle.apps.hcm.formulas.core.jerseyTest;

import oracle.apps.hcm.formulas.core.jersey.parser.*;

import oracle.apps.hcm.formulas.core.jersey.service.*;
import oracle.apps.hcm.formulas.core.jersey.config.*;
import oracle.apps.hcm.formulas.core.jersey.model.*;
import oracle.apps.hcm.formulas.core.jersey.api.*;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Mirrors test_session_fixes.py (25 tests).
 * WAS FOUND, date arithmetic, DEFAULT output visibility.
 * (fix_default_types tests are skipped -- that's an AI service post-processor.)
 */
public class SessionFixesTest {

    private final SimulatorService simulator = new SimulatorService();

    @SuppressWarnings("unchecked")
    private Map<String, Object> output(String code, Map<String, Object> inputs) {
        var result = simulator.simulate(code, inputs);
        assertEquals("Error: " + result.get("error"), "SUCCESS", result.get("status"));
        return (Map<String, Object>) result.get("output_data");
    }

    // -- WAS FOUND / WAS NOT FOUND (flattened from @Nested WasFoundTests) ------

    @Test
    public void wasFound_wasNotFoundParses() {
        var result = FFParser.parse("""
                DEFAULT FOR l_flag IS 'N'
                l_flag = GET_LOOKUP_MEANING('HR_TEST', 'Y')
                IF l_flag WAS NOT FOUND THEN
                  l_flag = 'N'
                END IF
                RETURN l_flag
                """);
        assertNotNull(result.program());
    }

    @Test
    public void wasFound_wasFoundParses() {
        var result = FFParser.parse("""
                DEFAULT FOR l_flag IS 'N'
                l_flag = GET_LOOKUP_MEANING('HR_TEST', 'Y')
                IF l_flag WAS FOUND THEN
                  l_flag = 'Y'
                END IF
                RETURN l_flag
                """);
        assertNotNull(result.program());
    }

    @Test
    public void wasFound_wasNotFoundSimulates() {
        var out = output("""
                DEFAULT FOR l_val IS 'X'
                IF l_val WAS NOT FOUND THEN
                  l_result = 1
                ELSE
                  l_result = 0
                END IF
                RETURN l_result
                """, Map.of("l_val", "X"));
        // WAS FOUND defaults to True, so WAS NOT FOUND -> False -> else branch
        assertEquals(0.0, out.get("l_result"));
    }

    @Test
    public void wasFound_wasFoundSimulates() {
        var out = output("""
                DEFAULT FOR l_val IS 'X'
                IF l_val WAS FOUND THEN
                  l_result = 1
                ELSE
                  l_result = 0
                END IF
                RETURN l_result
                """, Map.of("l_val", "X"));
        assertEquals(1.0, out.get("l_result"));
    }

    // -- Date arithmetic (flattened from @Nested DateArithmeticTests) -----------

    @Test
    public void dateArithmetic_addDays() {
        var out = output("""
                DEFAULT FOR d IS '01-JAN-0001'
                INPUTS ARE d
                l_result = ADD_DAYS(d, 14)
                RETURN l_result
                """, Map.of("d", "2-Apr-2026"));
        assertEquals("16-Apr-2026", out.get("l_result"));
    }

    @Test
    public void dateArithmetic_addMonths() {
        var out = output("""
                DEFAULT FOR d IS '01-JAN-0001'
                INPUTS ARE d
                l_result = ADD_MONTHS(d, 1)
                RETURN l_result
                """, Map.of("d", "1-Apr-2026"));
        assertEquals("1-May-2026", out.get("l_result"));
    }

    @Test
    public void dateArithmetic_addMonthsEndOfMonth() {
        var out = output("""
                DEFAULT FOR d IS '01-JAN-0001'
                INPUTS ARE d
                l_result = ADD_MONTHS(d, 1)
                RETURN l_result
                """, Map.of("d", "31-Jan-2026"));
        assertEquals("28-Feb-2026", out.get("l_result"));
    }

    @Test
    public void dateArithmetic_addYears() {
        var out = output("""
                DEFAULT FOR d IS '01-JAN-0001'
                INPUTS ARE d
                l_result = ADD_YEARS(d, 2)
                RETURN l_result
                """, Map.of("d", "1-Apr-2026"));
        assertEquals("1-Apr-2028", out.get("l_result"));
    }

    @Test
    public void dateArithmetic_daysBetween() {
        var out = output("""
                DEFAULT FOR d1 IS '01-JAN-0001'
                DEFAULT FOR d2 IS '01-JAN-0001'
                INPUTS ARE d1, d2
                l_result = DAYS_BETWEEN(d1, d2)
                RETURN l_result
                """, Map.of("d1", "15-Apr-2026", "d2", "1-Apr-2026"));
        assertEquals(14.0, out.get("l_result"));
    }

    @Test
    public void dateArithmetic_monthsBetween() {
        var out = output("""
                DEFAULT FOR d1 IS '01-JAN-0001'
                DEFAULT FOR d2 IS '01-JAN-0001'
                INPUTS ARE d1, d2
                l_result = MONTHS_BETWEEN(d1, d2)
                RETURN l_result
                """, Map.of("d1", "1-Jun-2026", "d2", "1-Jan-2026"));
        assertEquals(5.0, out.get("l_result"));
    }

    @Test
    public void dateArithmetic_toCharDate() {
        var out = output("""
                DEFAULT FOR d IS '01-JAN-0001'
                INPUTS ARE d
                l_next = ADD_DAYS(d, 7)
                l_result = TO_CHAR(l_next, 'DD-MON-YYYY')
                RETURN l_result
                """, Map.of("d", "1-Apr-2026"));
        assertEquals("8-Apr-2026", out.get("l_result"));
    }

    @Test
    public void dateArithmetic_toDate() {
        var out = output("""
                DEFAULT FOR s IS ' '
                INPUTS ARE s
                l_result = ADD_DAYS(TO_DATE(s), 1)
                RETURN l_result
                """, Map.of("s", "1-Apr-2026"));
        assertEquals("2-Apr-2026", out.get("l_result"));
    }

    @Test
    public void dateArithmetic_lastDay() {
        var out = output("""
                DEFAULT FOR d IS '01-JAN-0001'
                INPUTS ARE d
                l_result = LAST_DAY(d)
                RETURN l_result
                """, Map.of("d", "15-Feb-2026"));
        assertEquals("28-Feb-2026", out.get("l_result"));
    }

    // -- DEFAULT output visibility (flattened from @Nested DefaultOutputVisibilityTests) --

    @Test
    public void defaultOutputVisibility_reassignedDefaultAppearsInOutput() {
        var out = output("""
                DEFAULT FOR SCHEDULE_DATE IS '01-JAN-0001'
                DEFAULT FOR l_next IS '01-JAN-0001'
                INPUTS ARE SCHEDULE_DATE
                l_next = ADD_DAYS(SCHEDULE_DATE, 14)
                RETURN l_next
                """, Map.of("SCHEDULE_DATE", "1-Apr-2026"));
        assertTrue(out.containsKey("l_next"));
        assertEquals("15-Apr-2026", out.get("l_next"));
    }

    @Test
    public void defaultOutputVisibility_unassignedDefaultExcludedFromOutput() {
        var out = output("""
                DEFAULT FOR x IS 0
                DEFAULT FOR y IS 0
                INPUTS ARE x
                l_result = x + 1
                RETURN l_result
                """, Map.of("x", 5));
        assertTrue(out.containsKey("l_result"));
        assertEquals(6.0, out.get("l_result"));
        assertFalse(out.containsKey("y"));
    }

    @Test
    public void defaultOutputVisibility_inputVariableExcludedFromOutput() {
        var out = output("""
                DEFAULT FOR x IS 0
                INPUTS ARE x
                l_result = x * 2
                RETURN l_result
                """, Map.of("x", 10));
        assertFalse(out.containsKey("x"));
        assertEquals(20.0, out.get("l_result"));
    }
}
