package oracle.apps.hcm.formulas.core.jersey.parser;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable AST nodes for Fast Formula.
 * Traditional classes with final fields — equivalent to Python's frozen dataclasses.
 */
public interface AstNodes {

    static final class Program implements AstNodes {
        private final List<AstNodes> statements;
        public Program(List<AstNodes> statements) { this.statements = statements; }
        public List<AstNodes> statements() { return statements; }
        @Override public boolean equals(Object o) { return o instanceof Program && Objects.equals(statements, ((Program) o).statements); }
        @Override public int hashCode() { return Objects.hash(statements); }
        @Override public String toString() { return "Program[statements=" + statements + "]"; }
    }

    static final class DefaultDecl implements AstNodes {
        private final String name;
        private final AstNodes value;
        public DefaultDecl(String name, AstNodes value) { this.name = name; this.value = value; }
        public String name() { return name; }
        public AstNodes value() { return value; }
        @Override public boolean equals(Object o) { return o instanceof DefaultDecl && Objects.equals(name, ((DefaultDecl) o).name) && Objects.equals(value, ((DefaultDecl) o).value); }
        @Override public int hashCode() { return Objects.hash(name, value); }
        @Override public String toString() { return "DefaultDecl[name=" + name + ", value=" + value + "]"; }
    }

    static final class DefaultDataValue implements AstNodes {
        private final String name;
        private final AstNodes value;
        public DefaultDataValue(String name, AstNodes value) { this.name = name; this.value = value; }
        public String name() { return name; }
        public AstNodes value() { return value; }
        @Override public boolean equals(Object o) { return o instanceof DefaultDataValue && Objects.equals(name, ((DefaultDataValue) o).name) && Objects.equals(value, ((DefaultDataValue) o).value); }
        @Override public int hashCode() { return Objects.hash(name, value); }
        @Override public String toString() { return "DefaultDataValue[name=" + name + ", value=" + value + "]"; }
    }

    static final class InputDecl implements AstNodes {
        private final List<String> names;
        public InputDecl(List<String> names) { this.names = names; }
        public List<String> names() { return names; }
        @Override public boolean equals(Object o) { return o instanceof InputDecl && Objects.equals(names, ((InputDecl) o).names); }
        @Override public int hashCode() { return Objects.hash(names); }
        @Override public String toString() { return "InputDecl[names=" + names + "]"; }
    }

    static final class OutputDecl implements AstNodes {
        private final List<String> names;
        public OutputDecl(List<String> names) { this.names = names; }
        public List<String> names() { return names; }
        @Override public boolean equals(Object o) { return o instanceof OutputDecl && Objects.equals(names, ((OutputDecl) o).names); }
        @Override public int hashCode() { return Objects.hash(names); }
        @Override public String toString() { return "OutputDecl[names=" + names + "]"; }
    }

    static final class LocalDecl implements AstNodes {
        private final String name;
        public LocalDecl(String name) { this.name = name; }
        public String name() { return name; }
        @Override public boolean equals(Object o) { return o instanceof LocalDecl && Objects.equals(name, ((LocalDecl) o).name); }
        @Override public int hashCode() { return Objects.hash(name); }
        @Override public String toString() { return "LocalDecl[name=" + name + "]"; }
    }

    static final class AliasDecl implements AstNodes {
        private final String longName;
        private final String shortName;
        public AliasDecl(String longName, String shortName) { this.longName = longName; this.shortName = shortName; }
        public String longName() { return longName; }
        public String shortName() { return shortName; }
        @Override public boolean equals(Object o) { return o instanceof AliasDecl && Objects.equals(longName, ((AliasDecl) o).longName) && Objects.equals(shortName, ((AliasDecl) o).shortName); }
        @Override public int hashCode() { return Objects.hash(longName, shortName); }
        @Override public String toString() { return "AliasDecl[longName=" + longName + ", shortName=" + shortName + "]"; }
    }

    static final class Assignment implements AstNodes {
        private final String target;
        private final AstNodes value;
        public Assignment(String target, AstNodes value) { this.target = target; this.value = value; }
        public String target() { return target; }
        public AstNodes value() { return value; }
        @Override public boolean equals(Object o) { return o instanceof Assignment && Objects.equals(target, ((Assignment) o).target) && Objects.equals(value, ((Assignment) o).value); }
        @Override public int hashCode() { return Objects.hash(target, value); }
        @Override public String toString() { return "Assignment[target=" + target + ", value=" + value + "]"; }
    }

    static final class ArrayAssignment implements AstNodes {
        private final String target;
        private final AstNodes index;
        private final AstNodes value;
        public ArrayAssignment(String target, AstNodes index, AstNodes value) { this.target = target; this.index = index; this.value = value; }
        public String target() { return target; }
        public AstNodes index() { return index; }
        public AstNodes value() { return value; }
        @Override public boolean equals(Object o) { return o instanceof ArrayAssignment && Objects.equals(target, ((ArrayAssignment) o).target) && Objects.equals(index, ((ArrayAssignment) o).index) && Objects.equals(value, ((ArrayAssignment) o).value); }
        @Override public int hashCode() { return Objects.hash(target, index, value); }
        @Override public String toString() { return "ArrayAssignment[target=" + target + ", index=" + index + ", value=" + value + "]"; }
    }

    static final class IfStatement implements AstNodes {
        private final AstNodes condition;
        private final List<AstNodes> thenBody;
        private final List<ElsifClause> elsifClauses;
        private final List<AstNodes> elseBody;
        public IfStatement(AstNodes condition, List<AstNodes> thenBody, List<ElsifClause> elsifClauses, List<AstNodes> elseBody) {
            this.condition = condition; this.thenBody = thenBody; this.elsifClauses = elsifClauses; this.elseBody = elseBody;
        }
        public AstNodes condition() { return condition; }
        public List<AstNodes> thenBody() { return thenBody; }
        public List<ElsifClause> elsifClauses() { return elsifClauses; }
        public List<AstNodes> elseBody() { return elseBody; }
        @Override public boolean equals(Object o) { return o instanceof IfStatement && Objects.equals(condition, ((IfStatement) o).condition) && Objects.equals(thenBody, ((IfStatement) o).thenBody) && Objects.equals(elsifClauses, ((IfStatement) o).elsifClauses) && Objects.equals(elseBody, ((IfStatement) o).elseBody); }
        @Override public int hashCode() { return Objects.hash(condition, thenBody, elsifClauses, elseBody); }
        @Override public String toString() { return "IfStatement[condition=" + condition + ", thenBody=" + thenBody + ", elsifClauses=" + elsifClauses + ", elseBody=" + elseBody + "]"; }
    }

    static final class ElsifClause implements AstNodes {
        private final AstNodes condition;
        private final List<AstNodes> body;
        public ElsifClause(AstNodes condition, List<AstNodes> body) { this.condition = condition; this.body = body; }
        public AstNodes condition() { return condition; }
        public List<AstNodes> body() { return body; }
        @Override public boolean equals(Object o) { return o instanceof ElsifClause && Objects.equals(condition, ((ElsifClause) o).condition) && Objects.equals(body, ((ElsifClause) o).body); }
        @Override public int hashCode() { return Objects.hash(condition, body); }
        @Override public String toString() { return "ElsifClause[condition=" + condition + ", body=" + body + "]"; }
    }

    static final class WhileLoop implements AstNodes {
        private final AstNodes condition;
        private final List<AstNodes> body;
        public WhileLoop(AstNodes condition, List<AstNodes> body) { this.condition = condition; this.body = body; }
        public AstNodes condition() { return condition; }
        public List<AstNodes> body() { return body; }
        @Override public boolean equals(Object o) { return o instanceof WhileLoop && Objects.equals(condition, ((WhileLoop) o).condition) && Objects.equals(body, ((WhileLoop) o).body); }
        @Override public int hashCode() { return Objects.hash(condition, body); }
        @Override public String toString() { return "WhileLoop[condition=" + condition + ", body=" + body + "]"; }
    }

    static final class ForLoop implements AstNodes {
        private final String variable;
        private final AstNodes start;
        private final AstNodes end;
        private final List<AstNodes> body;
        public ForLoop(String variable, AstNodes start, AstNodes end, List<AstNodes> body) {
            this.variable = variable; this.start = start; this.end = end; this.body = body;
        }
        public String variable() { return variable; }
        public AstNodes start() { return start; }
        public AstNodes end() { return end; }
        public List<AstNodes> body() { return body; }
        @Override public boolean equals(Object o) { return o instanceof ForLoop && Objects.equals(variable, ((ForLoop) o).variable) && Objects.equals(start, ((ForLoop) o).start) && Objects.equals(end, ((ForLoop) o).end) && Objects.equals(body, ((ForLoop) o).body); }
        @Override public int hashCode() { return Objects.hash(variable, start, end, body); }
        @Override public String toString() { return "ForLoop[variable=" + variable + ", start=" + start + ", end=" + end + ", body=" + body + "]"; }
    }

    static final class CursorDecl implements AstNodes {
        private final String name;
        private final String tableName;
        private final List<AstNodes> selectItems;
        private final AstNodes whereClause;
        public CursorDecl(String name, String tableName, List<AstNodes> selectItems, AstNodes whereClause) {
            this.name = name; this.tableName = tableName; this.selectItems = selectItems; this.whereClause = whereClause;
        }
        public String name() { return name; }
        public String tableName() { return tableName; }
        public List<AstNodes> selectItems() { return selectItems; }
        public AstNodes whereClause() { return whereClause; }
        @Override public boolean equals(Object o) { return o instanceof CursorDecl && Objects.equals(name, ((CursorDecl) o).name) && Objects.equals(tableName, ((CursorDecl) o).tableName) && Objects.equals(selectItems, ((CursorDecl) o).selectItems) && Objects.equals(whereClause, ((CursorDecl) o).whereClause); }
        @Override public int hashCode() { return Objects.hash(name, tableName, selectItems, whereClause); }
        @Override public String toString() { return "CursorDecl[name=" + name + ", tableName=" + tableName + ", selectItems=" + selectItems + ", whereClause=" + whereClause + "]"; }
    }

    static final class ForCursorLoop implements AstNodes {
        private final String variable;
        private final String cursorName;
        private final List<AstNodes> body;
        public ForCursorLoop(String variable, String cursorName, List<AstNodes> body) {
            this.variable = variable; this.cursorName = cursorName; this.body = body;
        }
        public String variable() { return variable; }
        public String cursorName() { return cursorName; }
        public List<AstNodes> body() { return body; }
        @Override public boolean equals(Object o) { return o instanceof ForCursorLoop && Objects.equals(variable, ((ForCursorLoop) o).variable) && Objects.equals(cursorName, ((ForCursorLoop) o).cursorName) && Objects.equals(body, ((ForCursorLoop) o).body); }
        @Override public int hashCode() { return Objects.hash(variable, cursorName, body); }
        @Override public String toString() { return "ForCursorLoop[variable=" + variable + ", cursorName=" + cursorName + ", body=" + body + "]"; }
    }

    static final class ExitStatement implements AstNodes {
        private final AstNodes condition;
        public ExitStatement(AstNodes condition) { this.condition = condition; }
        public AstNodes condition() { return condition; }
        @Override public boolean equals(Object o) { return o instanceof ExitStatement && Objects.equals(condition, ((ExitStatement) o).condition); }
        @Override public int hashCode() { return Objects.hash(condition); }
        @Override public String toString() { return "ExitStatement[condition=" + condition + "]"; }
    }

    static final class ReturnStatement implements AstNodes {
        private final List<String> variables;
        public ReturnStatement(List<String> variables) { this.variables = variables; }
        public List<String> variables() { return variables; }
        @Override public boolean equals(Object o) { return o instanceof ReturnStatement && Objects.equals(variables, ((ReturnStatement) o).variables); }
        @Override public int hashCode() { return Objects.hash(variables); }
        @Override public String toString() { return "ReturnStatement[variables=" + variables + "]"; }
    }

    static final class FunctionCall implements AstNodes {
        private final String name;
        private final List<AstNodes> args;
        public FunctionCall(String name, List<AstNodes> args) { this.name = name; this.args = args; }
        public String name() { return name; }
        public List<AstNodes> args() { return args; }
        @Override public boolean equals(Object o) { return o instanceof FunctionCall && Objects.equals(name, ((FunctionCall) o).name) && Objects.equals(args, ((FunctionCall) o).args); }
        @Override public int hashCode() { return Objects.hash(name, args); }
        @Override public String toString() { return "FunctionCall[name=" + name + ", args=" + args + "]"; }
    }

    static final class CallFormula implements AstNodes {
        private final String name;
        private final List<InParam> inParams;
        private final List<OutParam> outParams;
        public CallFormula(String name, List<InParam> inParams, List<OutParam> outParams) {
            this.name = name; this.inParams = inParams; this.outParams = outParams;
        }
        public String name() { return name; }
        public List<InParam> inParams() { return inParams; }
        public List<OutParam> outParams() { return outParams; }
        @Override public boolean equals(Object o) { return o instanceof CallFormula && Objects.equals(name, ((CallFormula) o).name) && Objects.equals(inParams, ((CallFormula) o).inParams) && Objects.equals(outParams, ((CallFormula) o).outParams); }
        @Override public int hashCode() { return Objects.hash(name, inParams, outParams); }
        @Override public String toString() { return "CallFormula[name=" + name + ", inParams=" + inParams + ", outParams=" + outParams + "]"; }
    }

    static final class InParam implements AstNodes {
        private final String variable;
        private final String paramName;
        public InParam(String variable, String paramName) { this.variable = variable; this.paramName = paramName; }
        public String variable() { return variable; }
        public String paramName() { return paramName; }
        @Override public boolean equals(Object o) { return o instanceof InParam && Objects.equals(variable, ((InParam) o).variable) && Objects.equals(paramName, ((InParam) o).paramName); }
        @Override public int hashCode() { return Objects.hash(variable, paramName); }
        @Override public String toString() { return "InParam[variable=" + variable + ", paramName=" + paramName + "]"; }
    }

    static final class OutParam implements AstNodes {
        private final String variable;
        private final String paramName;
        private final Object defaultValue;
        public OutParam(String variable, String paramName, Object defaultValue) {
            this.variable = variable; this.paramName = paramName; this.defaultValue = defaultValue;
        }
        public String variable() { return variable; }
        public String paramName() { return paramName; }
        public Object defaultValue() { return defaultValue; }
        @Override public boolean equals(Object o) { return o instanceof OutParam && Objects.equals(variable, ((OutParam) o).variable) && Objects.equals(paramName, ((OutParam) o).paramName) && Objects.equals(defaultValue, ((OutParam) o).defaultValue); }
        @Override public int hashCode() { return Objects.hash(variable, paramName, defaultValue); }
        @Override public String toString() { return "OutParam[variable=" + variable + ", paramName=" + paramName + ", defaultValue=" + defaultValue + "]"; }
    }

    static final class ChangeContexts implements AstNodes {
        private final Map<String, AstNodes> contexts;
        public ChangeContexts(Map<String, AstNodes> contexts) { this.contexts = contexts; }
        public Map<String, AstNodes> contexts() { return contexts; }
        @Override public boolean equals(Object o) { return o instanceof ChangeContexts && Objects.equals(contexts, ((ChangeContexts) o).contexts); }
        @Override public int hashCode() { return Objects.hash(contexts); }
        @Override public String toString() { return "ChangeContexts[contexts=" + contexts + "]"; }
    }

    static final class Execute implements AstNodes {
        private final String formulaName;
        public Execute(String formulaName) { this.formulaName = formulaName; }
        public String formulaName() { return formulaName; }
        @Override public boolean equals(Object o) { return o instanceof Execute && Objects.equals(formulaName, ((Execute) o).formulaName); }
        @Override public int hashCode() { return Objects.hash(formulaName); }
        @Override public String toString() { return "Execute[formulaName=" + formulaName + "]"; }
    }

    static final class BinaryOp implements AstNodes {
        private final String operator;
        private final AstNodes left;
        private final AstNodes right;
        public BinaryOp(String operator, AstNodes left, AstNodes right) {
            this.operator = operator; this.left = left; this.right = right;
        }
        public String operator() { return operator; }
        public AstNodes left() { return left; }
        public AstNodes right() { return right; }
        @Override public boolean equals(Object o) { return o instanceof BinaryOp && Objects.equals(operator, ((BinaryOp) o).operator) && Objects.equals(left, ((BinaryOp) o).left) && Objects.equals(right, ((BinaryOp) o).right); }
        @Override public int hashCode() { return Objects.hash(operator, left, right); }
        @Override public String toString() { return "BinaryOp[operator=" + operator + ", left=" + left + ", right=" + right + "]"; }
    }

    static final class UnaryOp implements AstNodes {
        private final String operator;
        private final AstNodes operand;
        public UnaryOp(String operator, AstNodes operand) { this.operator = operator; this.operand = operand; }
        public String operator() { return operator; }
        public AstNodes operand() { return operand; }
        @Override public boolean equals(Object o) { return o instanceof UnaryOp && Objects.equals(operator, ((UnaryOp) o).operator) && Objects.equals(operand, ((UnaryOp) o).operand); }
        @Override public int hashCode() { return Objects.hash(operator, operand); }
        @Override public String toString() { return "UnaryOp[operator=" + operator + ", operand=" + operand + "]"; }
    }

    static final class WasDefaulted implements AstNodes {
        private final String variable;
        public WasDefaulted(String variable) { this.variable = variable; }
        public String variable() { return variable; }
        @Override public boolean equals(Object o) { return o instanceof WasDefaulted && Objects.equals(variable, ((WasDefaulted) o).variable); }
        @Override public int hashCode() { return Objects.hash(variable); }
        @Override public String toString() { return "WasDefaulted[variable=" + variable + "]"; }
    }

    static final class WasNotDefaulted implements AstNodes {
        private final String variable;
        public WasNotDefaulted(String variable) { this.variable = variable; }
        public String variable() { return variable; }
        @Override public boolean equals(Object o) { return o instanceof WasNotDefaulted && Objects.equals(variable, ((WasNotDefaulted) o).variable); }
        @Override public int hashCode() { return Objects.hash(variable); }
        @Override public String toString() { return "WasNotDefaulted[variable=" + variable + "]"; }
    }

    static final class LikeExpr implements AstNodes {
        private final AstNodes value;
        private final AstNodes pattern;
        private final boolean negated;
        public LikeExpr(AstNodes value, AstNodes pattern, boolean negated) {
            this.value = value; this.pattern = pattern; this.negated = negated;
        }
        public AstNodes value() { return value; }
        public AstNodes pattern() { return pattern; }
        public boolean negated() { return negated; }
        @Override public boolean equals(Object o) { return o instanceof LikeExpr && Objects.equals(value, ((LikeExpr) o).value) && Objects.equals(pattern, ((LikeExpr) o).pattern) && negated == ((LikeExpr) o).negated; }
        @Override public int hashCode() { return Objects.hash(value, pattern, negated); }
        @Override public String toString() { return "LikeExpr[value=" + value + ", pattern=" + pattern + ", negated=" + negated + "]"; }
    }

    static final class BetweenExpr implements AstNodes {
        private final AstNodes value;
        private final AstNodes low;
        private final AstNodes high;
        private final boolean negated;
        public BetweenExpr(AstNodes value, AstNodes low, AstNodes high, boolean negated) {
            this.value = value; this.low = low; this.high = high; this.negated = negated;
        }
        public AstNodes value() { return value; }
        public AstNodes low() { return low; }
        public AstNodes high() { return high; }
        public boolean negated() { return negated; }
        @Override public boolean equals(Object o) { return o instanceof BetweenExpr && Objects.equals(value, ((BetweenExpr) o).value) && Objects.equals(low, ((BetweenExpr) o).low) && Objects.equals(high, ((BetweenExpr) o).high) && negated == ((BetweenExpr) o).negated; }
        @Override public int hashCode() { return Objects.hash(value, low, high, negated); }
        @Override public String toString() { return "BetweenExpr[value=" + value + ", low=" + low + ", high=" + high + ", negated=" + negated + "]"; }
    }

    static final class InExpr implements AstNodes {
        private final AstNodes value;
        private final List<AstNodes> items;
        private final boolean negated;
        public InExpr(AstNodes value, List<AstNodes> items, boolean negated) {
            this.value = value; this.items = items; this.negated = negated;
        }
        public AstNodes value() { return value; }
        public List<AstNodes> items() { return items; }
        public boolean negated() { return negated; }
        @Override public boolean equals(Object o) { return o instanceof InExpr && Objects.equals(value, ((InExpr) o).value) && Objects.equals(items, ((InExpr) o).items) && negated == ((InExpr) o).negated; }
        @Override public int hashCode() { return Objects.hash(value, items, negated); }
        @Override public String toString() { return "InExpr[value=" + value + ", items=" + items + ", negated=" + negated + "]"; }
    }

    static final class StringLiteral implements AstNodes {
        private final String value;
        public StringLiteral(String value) { this.value = value; }
        public String value() { return value; }
        @Override public boolean equals(Object o) { return o instanceof StringLiteral && Objects.equals(value, ((StringLiteral) o).value); }
        @Override public int hashCode() { return Objects.hash(value); }
        @Override public String toString() { return "StringLiteral[value=" + value + "]"; }
    }

    static final class NumberLiteral implements AstNodes {
        private final double value;
        public NumberLiteral(double value) { this.value = value; }
        public double value() { return value; }
        @Override public boolean equals(Object o) { return o instanceof NumberLiteral && value == ((NumberLiteral) o).value; }
        @Override public int hashCode() { return Objects.hash(value); }
        @Override public String toString() { return "NumberLiteral[value=" + value + "]"; }
    }

    static final class DateLiteral implements AstNodes {
        private final String value;
        public DateLiteral(String value) { this.value = value; }
        public String value() { return value; }
        @Override public boolean equals(Object o) { return o instanceof DateLiteral && Objects.equals(value, ((DateLiteral) o).value); }
        @Override public int hashCode() { return Objects.hash(value); }
        @Override public String toString() { return "DateLiteral[value=" + value + "]"; }
    }

    static final class TypedString implements AstNodes {
        private final String value;
        private final String type;
        public TypedString(String value, String type) { this.value = value; this.type = type; }
        public String value() { return value; }
        public String type() { return type; }
        @Override public boolean equals(Object o) { return o instanceof TypedString && Objects.equals(value, ((TypedString) o).value) && Objects.equals(type, ((TypedString) o).type); }
        @Override public int hashCode() { return Objects.hash(value, type); }
        @Override public String toString() { return "TypedString[value=" + value + ", type=" + type + "]"; }
    }

    static final class Identifier implements AstNodes {
        private final String name;
        public Identifier(String name) { this.name = name; }
        public String name() { return name; }
        @Override public boolean equals(Object o) { return o instanceof Identifier && Objects.equals(name, ((Identifier) o).name); }
        @Override public int hashCode() { return Objects.hash(name); }
        @Override public String toString() { return "Identifier[name=" + name + "]"; }
    }

    static final class QuotedIdentifier implements AstNodes {
        private final String name;
        public QuotedIdentifier(String name) { this.name = name; }
        public String name() { return name; }
        @Override public boolean equals(Object o) { return o instanceof QuotedIdentifier && Objects.equals(name, ((QuotedIdentifier) o).name); }
        @Override public int hashCode() { return Objects.hash(name); }
        @Override public String toString() { return "QuotedIdentifier[name=" + name + "]"; }
    }

    static final class ArrayAccess implements AstNodes {
        private final String name;
        private final AstNodes index;
        public ArrayAccess(String name, AstNodes index) { this.name = name; this.index = index; }
        public String name() { return name; }
        public AstNodes index() { return index; }
        @Override public boolean equals(Object o) { return o instanceof ArrayAccess && Objects.equals(name, ((ArrayAccess) o).name) && Objects.equals(index, ((ArrayAccess) o).index); }
        @Override public int hashCode() { return Objects.hash(name, index); }
        @Override public String toString() { return "ArrayAccess[name=" + name + ", index=" + index + "]"; }
    }

    static final class MethodCall implements AstNodes {
        private final String object;
        private final String method;
        private final List<AstNodes> args;
        public MethodCall(String object, String method, List<AstNodes> args) {
            this.object = object; this.method = method; this.args = args;
        }
        public String object() { return object; }
        public String method() { return method; }
        public List<AstNodes> args() { return args; }
        @Override public boolean equals(Object o) { return o instanceof MethodCall && Objects.equals(object, ((MethodCall) o).object) && Objects.equals(method, ((MethodCall) o).method) && Objects.equals(args, ((MethodCall) o).args); }
        @Override public int hashCode() { return Objects.hash(object, method, args); }
        @Override public String toString() { return "MethodCall[object=" + object + ", method=" + method + ", args=" + args + "]"; }
    }

    static final class Concat implements AstNodes {
        private final AstNodes left;
        private final AstNodes right;
        public Concat(AstNodes left, AstNodes right) { this.left = left; this.right = right; }
        public AstNodes left() { return left; }
        public AstNodes right() { return right; }
        @Override public boolean equals(Object o) { return o instanceof Concat && Objects.equals(left, ((Concat) o).left) && Objects.equals(right, ((Concat) o).right); }
        @Override public int hashCode() { return Objects.hash(left, right); }
        @Override public String toString() { return "Concat[left=" + left + ", right=" + right + "]"; }
    }

    static final class BlockGroup implements AstNodes {
        private final List<AstNodes> statements;
        public BlockGroup(List<AstNodes> statements) { this.statements = statements; }
        public List<AstNodes> statements() { return statements; }
        @Override public boolean equals(Object o) { return o instanceof BlockGroup && Objects.equals(statements, ((BlockGroup) o).statements); }
        @Override public int hashCode() { return Objects.hash(statements); }
        @Override public String toString() { return "BlockGroup[statements=" + statements + "]"; }
    }
}
