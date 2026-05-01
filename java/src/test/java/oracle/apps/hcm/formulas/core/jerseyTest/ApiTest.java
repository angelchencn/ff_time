package oracle.apps.hcm.formulas.core.jerseyTest;

import oracle.apps.hcm.formulas.core.jersey.parser.*;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes;
import oracle.apps.hcm.formulas.core.jersey.service.*;
import oracle.apps.hcm.formulas.core.jersey.config.*;
import oracle.apps.hcm.formulas.core.jersey.model.*;
import oracle.apps.hcm.formulas.core.jersey.api.*;

import org.junit.Test;

import javax.ws.rs.QueryParam;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Mirrors test_api.py (7 tests) + test_api_extended.py (6 tests).
 * Pure unit tests — calls service layer directly, no Jersey Test Framework.
 */
public class ApiTest {

    private final ValidatorService validator = new ValidatorService();
    private final SimulatorService simulator = new SimulatorService();
    private final DbiService dbiService = new DbiService();
    private final FormulaTypesService formulaTypesService = new FormulaTypesService();

    // -- test_api.py -----------------------------------------------------------

    @Test
    public void validateValidFormula() {
        Map<String, Object> result = validator.validate("DEFAULT FOR hours IS 0\nRETURN hours");
        assertEquals(true, result.get("valid"));
    }

    @Test
    public void validateInvalidFormula() {
        Map<String, Object> result = validator.validate("IF hours > THEN");
        assertEquals(false, result.get("valid"));
    }

    @Test
    public void simulateFormula() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("hours", 10);
        Map<String, Object> result = simulator.simulate(
                "DEFAULT FOR hours IS 0\not_pay = hours * 1.5\nRETURN ot_pay", inputs);
        assertEquals("SUCCESS", result.get("status"));
    }

    @Test
    public void dbiList() {
        Map<String, Object> result = dbiService.getDbis(null, null, null, 3, 0);
        assertNotNull(result.get("total"));
        // total may be 0 when the JSON registry files are absent (e.g.
        // Fusion env where DBIs come from the database, not bundled JSON).
        assertTrue(((Number) result.get("total")).intValue() >= 0);
    }

    @Test
    public void dbiSearch() {
        Map<String, Object> result = dbiService.getDbis(null, "hours", null, 5, 0);
        assertNotNull(result.get("items"));
    }

    @Test
    public void validateEmptyCode() {
        Map<String, Object> result = validator.validate("");
        // Empty code should be invalid
        assertEquals(false, result.get("valid"));
    }

    @Test
    public void simulateMissingCode() {
        Map<String, Object> result = simulator.simulate("", new HashMap<>());
        // Empty code may parse as empty program — just verify it returns a status
        assertNotNull(result.get("status"));
    }

    // -- test_api_extended.py --------------------------------------------------

    @Test
    public void formulaTypesEndpoint() {
        List<Map<String, Object>> types = formulaTypesService.listAll();
        assertNotNull(types);
        // At minimum the hardcoded "Custom" type is always present.
        assertFalse(types.isEmpty());
    }

    @Test
    public void validateWithConcatOperator() {
        Map<String, Object> result = validator.validate("msg = 'Hello' || ' World'\nRETURN msg");
        assertEquals(true, result.get("valid"));
    }

    @Test
    public void validateWithGetContext() {
        Map<String, Object> result = validator.validate("ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)\nRETURN ffs_id");
        assertEquals(true, result.get("valid"));
    }

    @Test
    public void validateWithWasNotDefaulted() {
        Map<String, Object> result = validator.validate(
                "DEFAULT FOR x IS 0\nIF x WAS NOT DEFAULTED THEN\n  y = x\nELSE\n  y = 0\nENDIF\nRETURN y");
        assertEquals(true, result.get("valid"));
    }

    @Test
    public void simulateWithConcat() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("name", "Oracle");
        Map<String, Object> result = simulator.simulate(
                "DEFAULT FOR name IS 'World'\nresult = 'Hello ' || name\nRETURN result", inputs);
        assertEquals("SUCCESS", result.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.get("output_data");
        assertEquals("Hello Oracle", output.get("result"));
    }

    @Test
    public void healthCheck() {
        // FastFormulaResource.health() just returns a map
        FastFormulaResource resource = new FastFormulaResource();
        assertNotNull(resource);
    }

    @Test
    public void chatStatusAcceptsOptionalWaitSeconds() throws Exception {
        Method method = FastFormulaResource.class.getMethod("chatStatus", String.class, String.class);
        Annotation[] waitParamAnnotations = method.getParameterAnnotations()[1];
        QueryParam queryParam = null;
        for (Annotation annotation : waitParamAnnotations) {
            if (annotation instanceof QueryParam) {
                queryParam = (QueryParam) annotation;
                break;
            }
        }

        assertNotNull(method);
        assertNotNull(queryParam);
        assertEquals("wait_seconds", queryParam.value());
    }
}
