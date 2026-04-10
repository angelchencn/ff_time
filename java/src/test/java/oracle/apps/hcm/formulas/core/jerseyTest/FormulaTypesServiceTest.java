package oracle.apps.hcm.formulas.core.jerseyTest;

import oracle.apps.hcm.formulas.core.jersey.service.*;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for FormulaTypesService — listing formula types from the registry.
 */
public class FormulaTypesServiceTest {

    private final FormulaTypesService service = new FormulaTypesService();

    @Test
    public void allTypesLoads() {
        var types = service.listAll();
        // At minimum the hardcoded "Custom" type is always present even
        // when the JSON registry file is absent.
        assertFalse("Expected at least the Custom type", types.isEmpty());
        assertEquals("Custom", types.get(0).get("type_name"));
    }

    @Test
    public void eachTypeHasRequiredFields() {
        for (var t : service.listAll()) {
            assertNotNull("Missing type_name", t.get("type_name"));
        }
    }
}
