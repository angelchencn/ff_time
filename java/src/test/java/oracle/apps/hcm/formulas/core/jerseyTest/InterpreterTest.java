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
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for the AST Interpreter — mirrors Python's test_simulator.py.
 */
public class InterpreterTest {

    @Test
    public void simpleArithmetic() {
        var program = new Program(List.of(
                new DefaultDecl("hours", new NumberLiteral(0)),
                new DefaultDecl("rate", new NumberLiteral(0)),
                new Assignment("pay", new BinaryOp("*",
                        new Identifier("hours"), new Identifier("rate")))
        ));

        var interpreter = new Interpreter(Map.of("hours", 40, "rate", 25));
        var result = interpreter.run(program);

        assertEquals(1000.0, result.get("pay"));
    }

    @Test
    public void ifBranching() {
        var program = new Program(List.of(
                new DefaultDecl("hours", new NumberLiteral(0)),
                new IfStatement(
                        new BinaryOp(">", new Identifier("hours"), new NumberLiteral(40)),
                        List.of(new Assignment("overtime",
                                new BinaryOp("-", new Identifier("hours"), new NumberLiteral(40)))),
                        List.of(),
                        List.of(new Assignment("overtime", new NumberLiteral(0)))
                )
        ));

        var interpreter = new Interpreter(Map.of("hours", 50));
        var result = interpreter.run(program);
        assertEquals(10.0, result.get("overtime"));

        var interpreter2 = new Interpreter(Map.of("hours", 35));
        var result2 = interpreter2.run(program);
        assertEquals(0.0, result2.get("overtime"));
    }

    @Test
    public void stringConcat() {
        var program = new Program(List.of(
                new DefaultDecl("first_name", new StringLiteral("")),
                new DefaultDecl("last_name", new StringLiteral("")),
                new Assignment("full_name", new Concat(
                        new Concat(new Identifier("first_name"), new StringLiteral(" ")),
                        new Identifier("last_name")))
        ));

        var interpreter = new Interpreter(Map.of("first_name", "John", "last_name", "Doe"));
        var result = interpreter.run(program);
        assertEquals("John Doe", result.get("full_name"));
    }

    @Test(expected = Interpreter.SimulationError.class)
    public void divisionByZero() {
        var program = new Program(List.of(
                new Assignment("x", new BinaryOp("/", new NumberLiteral(10), new NumberLiteral(0)))
        ));

        var interpreter = new Interpreter(Map.of());
        interpreter.run(program);
    }

    @Test
    public void executionTraceIsRecorded() {
        var program = new Program(List.of(
                new DefaultDecl("hours", new NumberLiteral(0)),
                new Assignment("pay", new BinaryOp("*",
                        new Identifier("hours"), new NumberLiteral(25)))
        ));

        var interpreter = new Interpreter(Map.of("hours", 40));
        interpreter.run(program);

        var trace = interpreter.getTrace();
        assertFalse(trace.isEmpty());
        assertEquals("DECL hours", trace.get(0).get("statement"));
    }
}
