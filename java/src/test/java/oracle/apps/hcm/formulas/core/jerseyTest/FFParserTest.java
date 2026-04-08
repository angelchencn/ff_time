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
 * Mirrors test_parser.py (3 tests).
 */
public class FFParserTest {

    @Test
    public void parseSimpleFormula() {
        String code = """
                DEFAULT FOR hours IS 0
                INPUT IS rate
                ot_pay = hours * rate
                RETURN ot_pay
                """;
        var result = FFParser.parse(code);
        assertNotNull(result.program());
        assertTrue(result.diagnostics().isEmpty());
        var stmts = result.program().statements();
        assertEquals(4, stmts.size());
        assertTrue(stmts.get(0) instanceof DefaultDecl);
        assertEquals("hours", ((DefaultDecl) stmts.get(0)).name());
    }

    @Test
    public void parseIfElse() {
        String code = """
                IF hours > 40 THEN
                    ot_hours = hours - 40
                ELSE
                    ot_hours = 0
                ENDIF
                RETURN ot_hours
                """;
        var result = FFParser.parse(code);
        assertNotNull(result.program());
        assertTrue(result.program().statements().get(0) instanceof IfStatement);
        assertTrue(result.program().statements().get(1) instanceof ReturnStatement);
    }

    @Test
    public void parseSyntaxErrorReturnsDiagnostics() {
        var result = FFParser.parse("IF hours > THEN");
        assertFalse(result.diagnostics().isEmpty());
        assertEquals("error", result.diagnostics().get(0).severity());
    }
}
