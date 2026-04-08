package oracle.apps.hcm.formulas.core.jerseyTest;

import oracle.apps.hcm.formulas.core.jersey.parser.*;
import oracle.apps.hcm.formulas.core.jersey.service.*;
import oracle.apps.hcm.formulas.core.jersey.config.*;
import oracle.apps.hcm.formulas.core.jersey.model.*;
import oracle.apps.hcm.formulas.core.jersey.api.*;

import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes.*;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes;

import org.junit.Test;
import java.util.List;

import static org.junit.Assert.*;

public class AstNodesTest {

    @Test
    public void classesAreImmutable() {
        var lit = new AstNodes.NumberLiteral(42.0);
        assertEquals(42.0, lit.value(), 0.0);

        var id = new AstNodes.Identifier("hours_worked");
        assertEquals("hours_worked", id.name());
    }

    @Test
    public void programHoldsStatements() {
        var input = new AstNodes.InputDecl(List.of("hours"));
        var output = new AstNodes.OutputDecl(List.of("pay"));
        var program = new AstNodes.Program(List.of(input, output));

        assertEquals(2, program.statements().size());
        assertTrue(program.statements().get(0) instanceof AstNodes.InputDecl);
    }

    @Test
    public void interfaceTypeChecking() {
        AstNodes node = new AstNodes.StringLiteral("hello");

        String result;
        if (node instanceof AstNodes.StringLiteral) {
            result = ((AstNodes.StringLiteral) node).value();
        } else if (node instanceof AstNodes.NumberLiteral) {
            result = String.valueOf(((AstNodes.NumberLiteral) node).value());
        } else {
            result = "unknown";
        }

        assertEquals("hello", result);
    }
}
