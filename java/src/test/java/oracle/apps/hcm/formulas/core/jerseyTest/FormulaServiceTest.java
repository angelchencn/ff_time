package oracle.apps.hcm.formulas.core.jerseyTest;

import oracle.apps.hcm.formulas.core.jersey.api.*;
import oracle.apps.hcm.formulas.core.jersey.config.*;
import oracle.apps.hcm.formulas.core.jersey.model.*;
import oracle.apps.hcm.formulas.core.jersey.parser.*;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes.*;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes;
import oracle.apps.hcm.formulas.core.jersey.service.*;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Mirrors test_models.py formula CRUD tests (3 tests).
 */
public class FormulaServiceTest {

    private FormulaService service;

    @Before
    public void setUp() {
        service = new FormulaService();
    }

    @Test
    public void createAndRead() {
        var formula = service.create(Map.of(
                "name", "Overtime Calc",
                "description", "Calculate overtime pay",
                "formula_type", "TIME_LABOR",
                "code", "DEFAULT FOR hours IS 0\nRETURN hours",
                "status", "DRAFT"
        ));
        assertNotNull(formula.get("id"));
        assertEquals("Overtime Calc", formula.get("name"));
        assertEquals("TIME_LABOR", formula.get("formula_type"));
        assertEquals("DRAFT", formula.get("status"));

        var found = service.findById((String) formula.get("id"));
        assertNotNull(found);
        assertEquals("Overtime Calc", found.get("name"));
    }

    @Test
    public void updateFormula() {
        var formula = service.create(Map.of("name", "Test", "code", "x = 1"));
        String id = (String) formula.get("id");

        var updated = service.update(id, Map.of("code", "x = 2", "name", "Updated"));
        assertNotNull(updated);
        assertEquals("x = 2", updated.get("code"));
        assertEquals("Updated", updated.get("name"));
        assertEquals(2, updated.get("version"));
    }

    @Test
    public void listAll() {
        service.create(Map.of("name", "A"));
        service.create(Map.of("name", "B"));

        var all = service.listAll();
        assertEquals(2, all.size());
    }
}
