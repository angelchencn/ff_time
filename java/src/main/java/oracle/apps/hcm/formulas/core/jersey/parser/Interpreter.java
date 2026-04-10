package oracle.apps.hcm.formulas.core.jersey.parser;

import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tree-walking interpreter that executes a Fast Formula AST.
 * Equivalent to Python's Interpreter class.
 */
public class Interpreter {

    private static final int MAX_ITERATIONS = 10_000;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d-MMM-yyyy", java.util.Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMMM-yyyy", java.util.Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private final Map<String, Object> env = new HashMap<>();
    private final List<Map<String, Object>> trace = new ArrayList<>();
    private final Set<String> inputKeys;
    private final Set<String> declaredInputKeys = new HashSet<>();
    private final Set<String> defaultKeys = new HashSet<>();
    private final Set<String> assignedKeys = new HashSet<>();

    public Interpreter(Map<String, Object> inputData) {
        this.inputKeys = new HashSet<>(inputData.keySet());
        for (var entry : inputData.entrySet()) {
            Object v = entry.getValue();
            env.put(entry.getKey(), v instanceof Number ? ((Number) v).doubleValue() : v);
        }
    }

    public List<Map<String, Object>> getTrace() {
        return List.copyOf(trace);
    }

    // ── Public entry point ──────────────────────────────────────────────────

    public Map<String, Object> run(Program program) {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "run: stmtCount=" + program.statements().size()
                            + " inputKeys=" + inputKeys,
                    AppsLogger.FINER);
        }
        try {
            for (AstNodes stmt : program.statements()) {
                execStatement(stmt);
            }
        } catch (ReturnSignal ignored) {
            // RETURN control flow — normal exit path, intentionally silent.
        } catch (SimulationError simErr) {
            // SEVERE inside catch — re-thrown to caller (SimulatorService),
            // but we want a stack trace at this layer too because the caller
            // only logs the message.
            AppsLogger.write(this, simErr, AppsLogger.SEVERE);
            throw simErr;
        }

        Set<String> excluded = new HashSet<>(inputKeys);
        excluded.addAll(declaredInputKeys);
        Set<String> defaultOnly = new HashSet<>(defaultKeys);
        defaultOnly.removeAll(assignedKeys);
        excluded.addAll(defaultOnly);

        Map<String, Object> result = new HashMap<>();
        for (var entry : env.entrySet()) {
            if (!excluded.contains(entry.getKey())) {
                Object v = entry.getValue();
                result.put(entry.getKey(),
                        v instanceof LocalDate ? ((LocalDate) v).format(DateTimeFormatter.ofPattern("d-MMM-yyyy")) : v);
            }
        }
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "run: completed, output keys=" + result.keySet()
                            + " trace=" + trace.size(),
                    AppsLogger.FINER);
        }
        return result;
    }

    // ── Statement execution ─────────────────────────────────────────────────

    private void execStatement(AstNodes stmt) {
        if (stmt instanceof DefaultDecl) {
            DefaultDecl d = (DefaultDecl) stmt;
            execDefault(d.name(), evalNode(d.value()));
        } else if (stmt instanceof DefaultDataValue) {
            DefaultDataValue d = (DefaultDataValue) stmt;
            execDefault(d.name(), evalNode(d.value()));
        } else if (stmt instanceof InputDecl) {
            ((InputDecl) stmt).names().forEach(n -> declaredInputKeys.add(n));
        } else if (stmt instanceof OutputDecl) {
            ((OutputDecl) stmt).names().forEach(n -> declaredInputKeys.add(n));
        } else if (stmt instanceof LocalDecl) {
            LocalDecl d = (LocalDecl) stmt;
            declaredInputKeys.add(d.name());
            env.putIfAbsent(d.name(), 0.0);
        } else if (stmt instanceof AliasDecl) {
            declaredInputKeys.add(((AliasDecl) stmt).shortName());
        } else if (stmt instanceof Assignment) {
            execAssignment((Assignment) stmt);
        } else if (stmt instanceof ArrayAssignment) {
            execArrayAssignment((ArrayAssignment) stmt);
        } else if (stmt instanceof IfStatement) {
            execIf((IfStatement) stmt);
        } else if (stmt instanceof WhileLoop) {
            execWhile((WhileLoop) stmt);
        } else if (stmt instanceof ReturnStatement) {
            execReturn((ReturnStatement) stmt);
        } else if (stmt instanceof FunctionCall) {
            FunctionCall f = (FunctionCall) stmt;
            evalFunction(f.name(), evalArgsForFunction(f));
        } else if (stmt instanceof CallFormula) {
            addTrace("SIMULATED: CALL_FORMULA(" + ((CallFormula) stmt).name() + ")");
        } else if (stmt instanceof ChangeContexts) {
            addTrace("SIMULATED: CHANGE_CONTEXTS");
        } else if (stmt instanceof Execute) {
            addTrace("SIMULATED: EXECUTE(" + ((Execute) stmt).formulaName() + ")");
        } else {
            throw new SimulationError("Unknown statement: " + stmt.getClass().getSimpleName());
        }
    }

    private void execDefault(String name, Object value) {
        defaultKeys.add(name);
        env.putIfAbsent(name, value != null ? value : 0.0);
        addTrace("DECL " + name);
    }

    private void execAssignment(Assignment stmt) {
        Object value = evalNode(stmt.value());
        assignedKeys.add(stmt.target());
        env.put(stmt.target(), value);
        addTrace(stmt.target() + " = " + value);
    }

    private void execArrayAssignment(ArrayAssignment stmt) {
        Object value = evalNode(stmt.value());
        String key = stmt.target() + "[" + evalNode(stmt.index()) + "]";
        env.put(key, value);
        addTrace(key + " = " + value);
    }

    private void execIf(IfStatement stmt) {
        Object condValue = evalNode(stmt.condition());
        addTrace("IF condition=" + condValue);

        if (isTruthy(condValue)) {
            stmt.thenBody().forEach(this::execStatement);
            return;
        }

        for (ElsifClause elsif : stmt.elsifClauses()) {
            if (isTruthy(evalNode(elsif.condition()))) {
                elsif.body().forEach(this::execStatement);
                return;
            }
        }

        if (!stmt.elseBody().isEmpty()) {
            stmt.elseBody().forEach(this::execStatement);
        }
    }

    private void execWhile(WhileLoop stmt) {
        int iterations = 0;
        while (isTruthy(evalNode(stmt.condition()))) {
            if (iterations >= MAX_ITERATIONS) {
                throw new SimulationError("Infinite loop: exceeded " + MAX_ITERATIONS + " iterations");
            }
            stmt.body().forEach(this::execStatement);
            iterations++;
        }
    }

    private void execReturn(ReturnStatement stmt) {
        addTrace("RETURN " + stmt.variables());
        throw new ReturnSignal();
    }

    // ── Expression evaluation ───────────────────────────────────────────────

    private Object evalNode(AstNodes node) {
        if (node instanceof NumberLiteral) {
            return ((NumberLiteral) node).value();
        } else if (node instanceof StringLiteral) {
            return ((StringLiteral) node).value();
        } else if (node instanceof DateLiteral) {
            return parseDate(((DateLiteral) node).value());
        } else if (node instanceof TypedString) {
            TypedString t = (TypedString) node;
            return t.type().equals("DATE") ? parseDate(t.value()) : t.value();
        } else if (node instanceof Identifier) {
            Identifier id = (Identifier) node;
            if (!env.containsKey(id.name())) throw new SimulationError("Undefined variable: " + id.name());
            return env.get(id.name());
        } else if (node instanceof QuotedIdentifier) {
            QuotedIdentifier q = (QuotedIdentifier) node;
            if (!env.containsKey(q.name())) throw new SimulationError("Undefined variable: " + q.name());
            return env.get(q.name());
        } else if (node instanceof BinaryOp) {
            BinaryOp b = (BinaryOp) node;
            return evalBinaryOp(b.operator(), evalNode(b.left()), evalNode(b.right()));
        } else if (node instanceof UnaryOp) {
            UnaryOp u = (UnaryOp) node;
            return evalUnaryOp(u.operator(), evalNode(u.operand()));
        } else if (node instanceof Concat) {
            Concat c = (Concat) node;
            return String.valueOf(evalNode(c.left())) + String.valueOf(evalNode(c.right()));
        } else if (node instanceof FunctionCall) {
            FunctionCall f = (FunctionCall) node;
            return evalFunction(f.name(), evalArgsForFunction(f));
        } else if (node instanceof ArrayAccess) {
            ArrayAccess a = (ArrayAccess) node;
            return env.getOrDefault(a.name() + "[" + evalNode(a.index()) + "]", 0);
        } else if (node instanceof MethodCall) {
            MethodCall m = (MethodCall) node;
            addTrace("SIMULATED: " + m.object() + "." + m.method() + "()");
            return 0;
        } else if (node instanceof WasDefaulted) {
            return false;
        } else if (node instanceof WasNotDefaulted) {
            return true;
        } else if (node instanceof LikeExpr) {
            return evalLike((LikeExpr) node);
        } else {
            throw new SimulationError("Unknown node: " + node.getClass().getSimpleName());
        }
    }

    private Object evalBinaryOp(String op, Object left, Object right) {
        switch (op) {
            case "+": return toDouble(left) + toDouble(right);
            case "-": return toDouble(left) - toDouble(right);
            case "*": return toDouble(left) * toDouble(right);
            case "/": {
                double r = toDouble(right);
                if (r == 0) throw new SimulationError("Division by zero");
                return toDouble(left) / r;
            }
            case ">": return toDouble(left) > toDouble(right);
            case "<": return toDouble(left) < toDouble(right);
            case ">=": case "=>": return toDouble(left) >= toDouble(right);
            case "<=": case "=<": return toDouble(left) <= toDouble(right);
            case "=": case "==": return left.equals(right) || toDouble(left) == toDouble(right);
            case "!=": case "<>": case "><": return !left.equals(right);
            case "OR": return isTruthy(left) || isTruthy(right);
            case "AND": return isTruthy(left) && isTruthy(right);
            case "||": return String.valueOf(left) + String.valueOf(right);
            case "WAS DEFAULTED": return false;
            case "WAS FOUND": return true;
            case "IS NULL": return left == null || "".equals(left) || Double.valueOf(0).equals(left);
            default: throw new SimulationError("Unknown operator: " + op);
        }
    }

    private Object evalUnaryOp(String op, Object operand) {
        switch (op) {
            case "-": return -toDouble(operand);
            case "NOT": return !isTruthy(operand);
            default: throw new SimulationError("Unknown unary operator: " + op);
        }
    }

    private boolean evalLike(LikeExpr expr) {
        String value = String.valueOf(evalNode(expr.value()));
        String pattern = String.valueOf(evalNode(expr.pattern()));
        // Convert SQL LIKE pattern to regex
        String regex = pattern.replace("%", ".*").replace("_", ".");
        boolean matches = value.matches(regex);
        return expr.negated() ? !matches : matches;
    }

    // ── Built-in Functions ──────────────────────────────────────────────────

    private Object evalFunction(String name, List<Object> args) {
        String upper = name.toUpperCase();

        // Numeric
        if ("ABS".equals(upper)) return Math.abs(toDouble(args.get(0)));
        if ("CEIL".equals(upper)) return Math.ceil(toDouble(args.get(0)));
        if ("FLOOR".equals(upper)) return Math.floor(toDouble(args.get(0)));
        if ("POWER".equals(upper)) return Math.pow(toDouble(args.get(0)), toDouble(args.get(1)));
        if ("ROUND".equals(upper)) {
            int dec = args.size() >= 2 ? (int) toDouble(args.get(1)) : 0;
            double factor = Math.pow(10, dec);
            return Math.round(toDouble(args.get(0)) * factor) / factor;
        }
        if ("TRUNC".equals(upper) || "TRUNCATE".equals(upper)) {
            int dec = args.size() >= 2 ? (int) toDouble(args.get(1)) : 0;
            double factor = Math.pow(10, dec);
            return (long) (toDouble(args.get(0)) * factor) / factor;
        }
        if ("GREATEST".equals(upper)) return args.stream().mapToDouble(this::toDouble).max().orElse(0);
        if ("LEAST".equals(upper)) return args.stream().mapToDouble(this::toDouble).min().orElse(0);

        // String
        if ("LENGTH".equals(upper)) return (double) String.valueOf(args.get(0)).length();
        if ("UPPER".equals(upper)) return String.valueOf(args.get(0)).toUpperCase();
        if ("LOWER".equals(upper)) return String.valueOf(args.get(0)).toLowerCase();
        if ("INITCAP".equals(upper)) {
            String s = String.valueOf(args.get(0));
            return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
        }
        if ("SUBSTR".equals(upper) || "SUBSTRING".equals(upper)) {
            String s = String.valueOf(args.get(0));
            int start = (int) toDouble(args.get(1)) - 1; // 1-based
            int len = args.size() >= 3 ? (int) toDouble(args.get(2)) : s.length();
            return s.substring(start, Math.min(start + len, s.length()));
        }
        if ("INSTR".equals(upper)) {
            String s = String.valueOf(args.get(0));
            String search = String.valueOf(args.get(1));
            int pos = s.indexOf(search);
            return pos >= 0 ? (double) (pos + 1) : 0.0; // 1-based
        }
        if ("REPLACE".equals(upper)) {
            String s = String.valueOf(args.get(0));
            String old = args.size() >= 2 ? String.valueOf(args.get(1)) : "";
            String rep = args.size() >= 3 ? String.valueOf(args.get(2)) : "";
            return s.replace(old, rep);
        }
        if ("TRIM".equals(upper)) return String.valueOf(args.get(0)).trim();
        if ("LTRIM".equals(upper)) return String.valueOf(args.get(0)).stripLeading();
        if ("RTRIM".equals(upper)) return String.valueOf(args.get(0)).stripTrailing();
        if ("LPAD".equals(upper)) {
            String s = String.valueOf(args.get(0));
            int width = (int) toDouble(args.get(1));
            String pad = args.size() >= 3 ? String.valueOf(args.get(2)) : " ";
            while (s.length() < width) s = pad + s;
            return s.substring(s.length() - width);
        }
        if ("RPAD".equals(upper)) {
            String s = String.valueOf(args.get(0));
            int width = (int) toDouble(args.get(1));
            String pad = args.size() >= 3 ? String.valueOf(args.get(2)) : " ";
            while (s.length() < width) s = s + pad;
            return s.substring(0, width);
        }

        // Conversion
        if ("TO_NUMBER".equals(upper)) {
            Object val = args.get(0);
            if (val == null || "".equals(val)) return 0.0;
            return Double.parseDouble(String.valueOf(val));
        }
        if ("TO_CHAR".equals(upper) || "TO_TEXT".equals(upper)) {
            Object val = args.get(0);
            if (val instanceof LocalDate) return ((LocalDate) val).format(DateTimeFormatter.ofPattern("d-MMM-yyyy"));
            return String.valueOf(val);
        }
        if ("TO_DATE".equals(upper)) return parseDate(String.valueOf(args.get(0)));

        // Date
        if ("ADD_DAYS".equals(upper)) return toDate(args.get(0)).plusDays((long) toDouble(args.get(1)));
        if ("ADD_MONTHS".equals(upper)) return toDate(args.get(0)).plusMonths((long) toDouble(args.get(1)));
        if ("ADD_YEARS".equals(upper)) return toDate(args.get(0)).plusYears((long) toDouble(args.get(1)));
        if ("DAYS_BETWEEN".equals(upper)) return (double) ChronoUnit.DAYS.between(toDate(args.get(1)), toDate(args.get(0)));
        if ("MONTHS_BETWEEN".equals(upper)) return (double) ChronoUnit.MONTHS.between(toDate(args.get(1)), toDate(args.get(0)));
        if ("HOURS_BETWEEN".equals(upper)) return (double) ChronoUnit.DAYS.between(toDate(args.get(1)), toDate(args.get(0))) * 24;
        if ("LAST_DAY".equals(upper)) {
            LocalDate d = toDate(args.get(0));
            return d.withDayOfMonth(YearMonth.from(d).lengthOfMonth());
        }

        // Utility
        if ("ISNULL".equals(upper)) return "N";
        if ("GET_CONTEXT".equals(upper)) { addTrace("SIMULATED: GET_CONTEXT"); return 0; }
        if ("SET_INPUT".equals(upper) || "GET_INPUT".equals(upper) || "GET_OUTPUT".equals(upper)) return 0;
        if ("PAY_INTERNAL_LOG_WRITE".equals(upper)) { addTrace("LOG: " + (args.isEmpty() ? "" : args.get(0))); return 0; }
        if ("PUT_MESSAGE".equals(upper) || "RAISE_ERROR".equals(upper)) { addTrace(upper + ": " + (args.isEmpty() ? "" : args.get(0))); return 0; }
        if ("GET_TABLE_VALUE".equals(upper) || "GET_LOOKUP_MEANING".equals(upper)) { addTrace("SIMULATED: " + upper); return 0; }

        throw new SimulationError("Unknown function: " + name);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static final Set<String> CONTEXT_FUNCS = Set.of(
            "GET_CONTEXT", "SET_CONTEXT", "NEED_CONTEXT",
            "SET_INPUT", "GET_INPUT", "GET_OUTPUT"
    );

    /**
     * Evaluate function args, treating first arg of context functions as a name string.
     */
    private List<Object> evalArgsForFunction(FunctionCall f) {
        String upper = f.name().toUpperCase();
        if (CONTEXT_FUNCS.contains(upper) && !f.args().isEmpty()) {
            List<Object> result = new ArrayList<>();
            AstNodes first = f.args().get(0);
            // First arg: use identifier name as string, not as variable lookup
            if (first instanceof Identifier) {
                result.add(((Identifier) first).name());
            } else {
                result.add(evalNode(first));
            }
            for (int i = 1; i < f.args().size(); i++) {
                result.add(evalNode(f.args().get(i)));
            }
            return result;
        }
        return evalArgs(f.args());
    }

    private List<Object> evalArgs(List<AstNodes> argNodes) {
        List<Object> result = new ArrayList<>();
        for (AstNodes node : argNodes) {
            result.add(evalNode(node));
        }
        return result;
    }

    private double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof Boolean) return ((Boolean) val) ? 1.0 : 0.0;
        try { return Double.parseDouble(String.valueOf(val)); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private LocalDate toDate(Object val) {
        if (val instanceof LocalDate) return (LocalDate) val;
        return parseDate(String.valueOf(val));
    }

    private boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).doubleValue() != 0;
        if (val instanceof String) return !((String) val).isEmpty();
        return true;
    }

    private LocalDate parseDate(String s) {
        s = s.trim().replaceAll("^['\"]|['\"]$", "");

        // Oracle placeholder for empty date — treat as epoch
        if (s.equalsIgnoreCase("01-JAN-0001") || s.equalsIgnoreCase("1-Jan-0001")) {
            return LocalDate.of(1, 1, 1);
        }

        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(s, fmt); }
            catch (DateTimeParseException ignored) {}
        }

        // Try with lenient year parsing for old dates
        try {
            var fmt = new java.time.format.DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("d-MMM-yyyy")
                    .toFormatter(java.util.Locale.ENGLISH);
            return LocalDate.parse(s, fmt);
        } catch (DateTimeParseException ignored) {}

        throw new SimulationError("Cannot parse date: " + s);
    }

    private void addTrace(String message) {
        trace.add(Map.of("statement", message, "env_snapshot", Map.copyOf(env)));
    }

    // ── Exception types ─────────────────────────────────────────────────────

    public static class ReturnSignal extends RuntimeException {
        public ReturnSignal() { super("RETURN"); }
    }

    public static class SimulationError extends RuntimeException {
        public SimulationError(String message) { super(message); }
    }
}
