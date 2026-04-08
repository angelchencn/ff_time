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
 * Mirrors test_simulator.py (4 tests) + test_simulator_extended.py (6 tests).
 */
public class SimulatorTest {

    private final SimulatorService simulator = new SimulatorService();

    private Map<String, Object> simulate(String code, Map<String, Object> inputs) {
        return simulator.simulate(code, inputs);
    }

    // -- test_simulator.py -----------------------------------------------------

    @Test
    public void simpleArithmetic() {
        var result = simulate("""
                DEFAULT FOR hours IS 0
                DEFAULT FOR rate IS 0
                ot_pay = hours * rate
                RETURN ot_pay
                """, Map.of("hours", 10, "rate", 1.5));
        assertEquals("SUCCESS", result.get("status"));
        @SuppressWarnings("unchecked")
        var output = (Map<String, Object>) result.get("output_data");
        assertEquals(15.0, output.get("ot_pay"));
    }

    @Test
    public void ifElseBranching() {
        String code = """
                DEFAULT FOR hours IS 0
                IF hours > 40 THEN
                    ot_hours = hours - 40
                ELSE
                    ot_hours = 0
                ENDIF
                RETURN ot_hours
                """;
        @SuppressWarnings("unchecked")
        var out1 = (Map<String, Object>) simulate(code, Map.of("hours", 45)).get("output_data");
        assertEquals(5.0, out1.get("ot_hours"));

        @SuppressWarnings("unchecked")
        var out2 = (Map<String, Object>) simulate(code, Map.of("hours", 30)).get("output_data");
        assertEquals(0.0, out2.get("ot_hours"));
    }

    @Test
    public void executionTracePopulated() {
        var result = simulate("""
                DEFAULT FOR hours IS 0
                ot_pay = hours * 2
                RETURN ot_pay
                """, Map.of("hours", 10));
        @SuppressWarnings("unchecked")
        var trace = (java.util.List<Map<String, Object>>) result.get("execution_trace");
        assertFalse(trace.isEmpty());
        assertTrue(trace.stream().anyMatch(t -> t.get("statement").toString().contains("ot_pay")));
    }

    @Test
    public void divisionByZeroError() {
        var result = simulate("""
                DEFAULT FOR x IS 0
                result = 10 / x
                RETURN result
                """, Map.of("x", 0));
        assertEquals("ERROR", result.get("status"));
        assertNotNull(result.get("error"));
    }

    // -- test_simulator_extended.py --------------------------------------------

    @Test
    public void concatOperatorSimulation() {
        var result = simulate("""
                DEFAULT FOR name IS 'World'
                result = 'Hello ' || name
                RETURN result
                """, Map.of("name", "Oracle"));
        assertEquals("SUCCESS", result.get("status"));
        @SuppressWarnings("unchecked")
        var output = (Map<String, Object>) result.get("output_data");
        assertEquals("Hello Oracle", output.get("result"));
    }

    @Test
    public void concatChain() {
        var result = simulate("a = 'A' || 'B' || 'C'\nRETURN a", Map.of());
        @SuppressWarnings("unchecked")
        var output = (Map<String, Object>) result.get("output_data");
        assertEquals("ABC", output.get("a"));
    }

    @Test
    public void payInternalLogWrite() {
        var result = simulate("""
                DEFAULT FOR hours IS 0
                l_log = PAY_INTERNAL_LOG_WRITE('Entry - hours=' || TO_CHAR(hours))
                result = hours * 2
                RETURN result
                """, Map.of("hours", 10));
        assertEquals("SUCCESS", result.get("status"));
        @SuppressWarnings("unchecked")
        var output = (Map<String, Object>) result.get("output_data");
        assertEquals(20.0, output.get("result"));
    }

    @Test
    public void getContextSimulation() {
        var result = simulate("""
                ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)
                RETURN ffs_id
                """, Map.of());
        assertEquals("SUCCESS", result.get("status"));
        @SuppressWarnings("unchecked")
        var output = (Map<String, Object>) result.get("output_data");
        assertEquals(0, output.get("ffs_id"));
    }

    @Test
    public void isnullFunction() {
        var result = simulate("""
                DEFAULT FOR x IS 0
                result = ISNULL(x)
                RETURN result
                """, Map.of("x", 0));
        assertEquals("SUCCESS", result.get("status"));
    }

    @Test
    public void wasNotDefaultedSimulation() {
        var result = simulate("""
                DEFAULT FOR hours IS 0
                IF hours WAS NOT DEFAULTED THEN
                    result = hours
                ELSE
                    result = 99
                ENDIF
                RETURN result
                """, Map.of("hours", 10));
        assertEquals("SUCCESS", result.get("status"));
        @SuppressWarnings("unchecked")
        var output = (Map<String, Object>) result.get("output_data");
        assertEquals(10.0, output.get("result"));
    }
}
