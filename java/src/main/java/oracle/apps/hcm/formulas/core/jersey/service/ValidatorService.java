package oracle.apps.hcm.formulas.core.jersey.service;

import oracle.apps.hcm.formulas.core.jersey.parser.*;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Three-layer validation: syntax, semantic, rules.
 */
public class ValidatorService {

    private static final Set<String> CONTEXT_FUNCTIONS = Set.of(
            "GET_CONTEXT", "SET_CONTEXT", "NEED_CONTEXT",
            "SET_INPUT", "GET_OUTPUT", "GET_INPUT"
    );

    private static final Set<String> BUILTIN_VARIABLES = Set.of(
            "SYSDATE", "TRUE", "FALSE"
    );

    private static final Set<String> RESERVED_WORDS = Set.of(
            "ALIAS", "AND", "ARE", "AS", "DEFAULT", "DEFAULTED", "ELSE",
            "EXECUTE", "FOR", "IF", "INPUTS", "IS", "NOT", "OR",
            "RETURN", "THEN", "USING", "WAS"
    );

    private Set<String> dbiNames;

    private Set<String> loadDbiNames() {
        if (dbiNames != null) return dbiNames;
        var dbiService = new DbiService();
        var result = dbiService.getDbis(null, null, null, 100000, 0);
        @SuppressWarnings("unchecked")
        var items = (List<Map<String, Object>>) result.get("items");
        dbiNames = new HashSet<>();
        for (var item : items) {
            String name = (String) item.get("name");
            if (name != null) dbiNames.add(name.toUpperCase());
        }
        return dbiNames;
    }

    public Map<String, Object> validate(String code) {
        var diagnostics = new ArrayList<Map<String, Object>>();

        // Layer 1 — syntax
        var parseResult = FFParser.parse(code);
        if (!parseResult.diagnostics().isEmpty()) {
            for (var d : parseResult.diagnostics()) {
                diagnostics.add(diag(d.line(), d.col(), d.severity(), d.message(), "syntax"));
            }
        }

        if (parseResult.program() == null) {
            return Map.of("valid", false, "diagnostics", diagnostics);
        }

        var program = parseResult.program();

        // Layer 2 — semantic
        diagnostics.addAll(semanticCheck(program));

        // Layer 3 — rules
        diagnostics.addAll(ruleCheck(program));

        boolean hasError = diagnostics.stream()
                .anyMatch(d -> "error".equals(d.get("severity")));
        return Map.of("valid", !hasError, "diagnostics", diagnostics);
    }

    // ── Layer 2: Semantic checks ────────────────────────────────────────────

    private List<Map<String, Object>> semanticCheck(Program program) {
        var diagnostics = new ArrayList<Map<String, Object>>();

        Set<String> declared = new HashSet<>(loadDbiNames());
        declared.addAll(BUILTIN_VARIABLES);
        Set<String> outputVars = new HashSet<>();
        Set<String> inputVars = new HashSet<>();
        Set<String> defaultVars = new HashSet<>();

        for (AstNodes stmt : program.statements()) {
            if (stmt instanceof DefaultDecl) { DefaultDecl d = (DefaultDecl) stmt; declared.add(d.name()); defaultVars.add(d.name()); }
            if (stmt instanceof DefaultDataValue) { DefaultDataValue d = (DefaultDataValue) stmt; declared.add(d.name()); defaultVars.add(d.name()); }
            if (stmt instanceof InputDecl) { InputDecl d = (InputDecl) stmt; declared.addAll(d.names()); inputVars.addAll(d.names()); }
            if (stmt instanceof OutputDecl) {
                OutputDecl d = (OutputDecl) stmt;
                declared.addAll(d.names());
                outputVars.addAll(d.names());
            }
            if (stmt instanceof LocalDecl) { LocalDecl d = (LocalDecl) stmt; declared.add(d.name()); }
            if (stmt instanceof AliasDecl) { AliasDecl d = (AliasDecl) stmt; declared.add(d.shortName()); }
        }

        Set<String> assigned = collectAssignments(program.statements());
        declared.addAll(assigned);

        Set<String> allRefs = new HashSet<>();
        for (AstNodes stmt : program.statements()) {
            collectRefs(stmt, allRefs);
        }

        // Undeclared variables
        for (String name : allRefs.stream().sorted().toList()) {
            if (!declared.contains(name)) {
                diagnostics.add(diag(0, 0, "error",
                        "Undeclared variable '" + name + "' referenced but never declared or assigned.",
                        "semantic"));
            }
        }

        // Unassigned output variables
        for (String name : outputVars.stream().sorted().toList()) {
            if (!assigned.contains(name)) {
                diagnostics.add(diag(0, 0, "warning",
                        "OUTPUT variable '" + name + "' is declared but never assigned a value.",
                        "semantic"));
            }
        }

        // Input variables assigned (should be read-only)
        for (String name : inputVars) {
            if (assigned.contains(name)) {
                diagnostics.add(diag(0, 0, "warning",
                        "INPUT variable '" + name + "' is assigned a value. Input variables should be read-only.",
                        "semantic"));
            }
        }

        // Variable name exceeds 80 characters
        Set<String> allNames = new HashSet<>();
        allNames.addAll(defaultVars);
        allNames.addAll(inputVars);
        allNames.addAll(outputVars);
        allNames.addAll(assigned);
        for (String name : allNames) {
            if (name.length() > 80) {
                diagnostics.add(diag(0, 0, "error",
                        "Variable name '" + name + "' exceeds 80 characters (length: " + name.length() + ").",
                        "semantic"));
            }
        }

        // Reserved word used as variable name
        for (String name : allNames) {
            if (RESERVED_WORDS.contains(name.toUpperCase())) {
                diagnostics.add(diag(0, 0, "error",
                        "Variable name '" + name + "' is a reserved word and cannot be used.",
                        "semantic"));
            }
        }

        return diagnostics;
    }

    // ── Layer 3: Business rules ─────────────────────────────────────────────

    private List<Map<String, Object>> ruleCheck(Program program) {
        var diagnostics = new ArrayList<Map<String, Object>>();

        // Missing RETURN
        if (!hasReturn(program.statements())) {
            diagnostics.add(diag(0, 0, "error",
                    "Formula has no RETURN statement.", "rule"));
        }

        // Statement ordering: ALIAS → DEFAULT → INPUTS → other
        diagnostics.addAll(checkStatementOrder(program));

        // Input variables without DEFAULT
        diagnostics.addAll(checkInputsHaveDefaults(program));

        return diagnostics;
    }

    private List<Map<String, Object>> checkStatementOrder(Program program) {
        var diagnostics = new ArrayList<Map<String, Object>>();

        // Track phases: 0=alias, 1=default, 2=input, 3=other
        int currentPhase = 0;

        for (AstNodes stmt : program.statements()) {
            int stmtPhase;
            if (stmt instanceof AliasDecl) {
                stmtPhase = 0;
            } else if (stmt instanceof DefaultDecl || stmt instanceof DefaultDataValue) {
                stmtPhase = 1;
            } else if (stmt instanceof InputDecl || stmt instanceof OutputDecl) {
                stmtPhase = 2;
            } else {
                stmtPhase = 3;
            }

            if (stmtPhase < currentPhase) {
                String stmtType;
                switch (stmtPhase) {
                    case 0: stmtType = "ALIAS"; break;
                    case 1: stmtType = "DEFAULT"; break;
                    case 2: stmtType = "INPUTS/OUTPUTS"; break;
                    default: stmtType = "statement"; break;
                }
                String afterType;
                switch (currentPhase) {
                    case 1: afterType = "DEFAULT"; break;
                    case 2: afterType = "INPUTS/OUTPUTS"; break;
                    case 3: afterType = "other statements"; break;
                    default: afterType = "previous statements"; break;
                }
                diagnostics.add(diag(0, 0, "warning",
                        stmtType + " statement appears after " + afterType
                                + ". Recommended order: ALIAS → DEFAULT → INPUTS → other.",
                        "rule"));
                break; // Only report once
            }
            currentPhase = Math.max(currentPhase, stmtPhase);
        }

        return diagnostics;
    }

    private List<Map<String, Object>> checkInputsHaveDefaults(Program program) {
        var diagnostics = new ArrayList<Map<String, Object>>();

        Set<String> defaultedVars = new HashSet<>();
        Set<String> inputVars = new HashSet<>();

        for (AstNodes stmt : program.statements()) {
            if (stmt instanceof DefaultDecl) defaultedVars.add(((DefaultDecl) stmt).name().toUpperCase());
            if (stmt instanceof DefaultDataValue) defaultedVars.add(((DefaultDataValue) stmt).name().toUpperCase());
            if (stmt instanceof InputDecl) ((InputDecl) stmt).names().forEach(n -> inputVars.add(n.toUpperCase()));
        }

        for (String input : inputVars.stream().sorted().toList()) {
            if (!defaultedVars.contains(input)) {
                diagnostics.add(diag(0, 0, "warning",
                        "INPUT variable '" + input + "' has no DEFAULT FOR declaration. "
                                + "Consider adding DEFAULT FOR " + input + " IS <value>.",
                        "rule"));
            }
        }

        return diagnostics;
    }

    private boolean hasReturn(List<AstNodes> stmts) {
        for (AstNodes stmt : stmts) {
            if (stmt instanceof ReturnStatement) return true;
            if (stmt instanceof IfStatement) {
                IfStatement i = (IfStatement) stmt;
                if (hasReturn(i.thenBody())) return true;
                if (!i.elseBody().isEmpty() && hasReturn(i.elseBody())) return true;
                for (ElsifClause elsif : i.elsifClauses()) {
                    if (hasReturn(elsif.body())) return true;
                }
            }
            if (stmt instanceof WhileLoop) {
                WhileLoop w = (WhileLoop) stmt;
                if (hasReturn(w.body())) return true;
            }
        }
        return false;
    }

    // ── AST walkers ─────────────────────────────────────────────────────────

    private void collectRefs(AstNodes node, Set<String> refs) {
        if (node instanceof Identifier) {
            refs.add(((Identifier) node).name());
        } else if (node instanceof QuotedIdentifier) {
            refs.add(((QuotedIdentifier) node).name());
        } else if (node instanceof BinaryOp) {
            BinaryOp b = (BinaryOp) node;
            collectRefs(b.left(), refs);
            collectRefs(b.right(), refs);
        } else if (node instanceof UnaryOp) {
            collectRefs(((UnaryOp) node).operand(), refs);
        } else if (node instanceof Concat) {
            Concat c = (Concat) node;
            collectRefs(c.left(), refs);
            collectRefs(c.right(), refs);
        } else if (node instanceof FunctionCall) {
            FunctionCall f = (FunctionCall) node;
            String upper = f.name().toUpperCase();
            for (int i = 0; i < f.args().size(); i++) {
                if (i == 0 && CONTEXT_FUNCTIONS.contains(upper)) continue;
                collectRefs(f.args().get(i), refs);
            }
        } else if (node instanceof Assignment) {
            collectRefs(((Assignment) node).value(), refs);
        } else if (node instanceof IfStatement) {
            IfStatement i = (IfStatement) node;
            collectRefs(i.condition(), refs);
            i.thenBody().forEach(s -> collectRefs(s, refs));
            i.elseBody().forEach(s -> collectRefs(s, refs));
            i.elsifClauses().forEach(e -> {
                collectRefs(e.condition(), refs);
                e.body().forEach(s -> collectRefs(s, refs));
            });
        } else if (node instanceof WhileLoop) {
            WhileLoop w = (WhileLoop) node;
            collectRefs(w.condition(), refs);
            w.body().forEach(s -> collectRefs(s, refs));
        } else if (node instanceof LikeExpr) {
            LikeExpr l = (LikeExpr) node;
            collectRefs(l.value(), refs);
            collectRefs(l.pattern(), refs);
        } else if (node instanceof ArrayAccess) {
            collectRefs(((ArrayAccess) node).index(), refs);
        }
        // ReturnStatement, WasDefaulted, WasNotDefaulted, and others: no refs to collect
    }

    private Set<String> collectAssignments(List<AstNodes> stmts) {
        Set<String> assigned = new HashSet<>();
        for (AstNodes stmt : stmts) {
            if (stmt instanceof Assignment) assigned.add(((Assignment) stmt).target());
            if (stmt instanceof IfStatement) {
                IfStatement i = (IfStatement) stmt;
                assigned.addAll(collectAssignments(i.thenBody()));
                assigned.addAll(collectAssignments(i.elseBody()));
                for (ElsifClause elsif : i.elsifClauses()) {
                    assigned.addAll(collectAssignments(elsif.body()));
                }
            }
            if (stmt instanceof WhileLoop) {
                WhileLoop w = (WhileLoop) stmt;
                assigned.addAll(collectAssignments(w.body()));
            }
        }
        return assigned;
    }

    private static Map<String, Object> diag(int line, int col, String severity,
                                             String message, String layer) {
        var m = new LinkedHashMap<String, Object>();
        m.put("line", line);
        m.put("col", col);
        m.put("severity", severity);
        m.put("message", message);
        m.put("layer", layer);
        return m;
    }
}
