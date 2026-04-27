package oracle.apps.hcm.formulas.core.jersey.parser;

import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes.*;
import oracle.apps.hcm.formulas.core.jersey.parser.Tokenizer.Token;
import oracle.apps.hcm.formulas.core.jersey.parser.Tokenizer.TokenType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-written recursive descent parser for Oracle Fast Formula.
 * Produces the same AST nodes as the previous ANTLR-based parser.
 * Zero external dependencies — pure Java only.
 */
public class FFParser {

    public static final class ParseResult {
        private final Program program;
        private final List<Diagnostic> diagnostics;
        public ParseResult(Program program, List<Diagnostic> diagnostics) { this.program = program; this.diagnostics = diagnostics; }
        public Program program() { return program; }
        public List<Diagnostic> diagnostics() { return diagnostics; }
    }

    public static final class Diagnostic {
        private final int line;
        private final int col;
        private final String severity;
        private final String message;
        public Diagnostic(int line, int col, String severity, String message) {
            this.line = line; this.col = col; this.severity = severity; this.message = message;
        }
        public int line() { return line; }
        public int col() { return col; }
        public String severity() { return severity; }
        public String message() { return message; }
    }

    // ── Comparison operators ────────────────────────────────────────────────

    private static final Set<TokenType> COMP_OPS = Set.of(
            TokenType.EQ, TokenType.NEQ, TokenType.LTGT, TokenType.GTLT,
            TokenType.LT, TokenType.GT, TokenType.LTEQ, TokenType.GTEQ,
            TokenType.EQGT, TokenType.EQLT
    );

    // ── Statement-starting keywords ────────────────────────────────────────

    private static final Set<TokenType> STMT_START_KEYWORDS = Set.of(
            TokenType.ALIAS, TokenType.DEFAULT, TokenType.DEFAULT_DATA_VALUE,
            TokenType.INPUT, TokenType.INPUTS, TokenType.OUTPUT, TokenType.OUTPUTS,
            TokenType.LOCAL, TokenType.IF, TokenType.WHILE, TokenType.FOR,
            TokenType.CURSOR, TokenType.RETURN, TokenType.EXIT,
            TokenType.CALL_FORMULA, TokenType.CHANGE_CONTEXTS, TokenType.EXECUTE
    );

    // ── Body-terminating keywords (stop reading body statements) ────────────

    private static final Set<TokenType> BODY_TERMINATORS = Set.of(
            TokenType.ELSIF, TokenType.ELSE, TokenType.ENDIF, TokenType.ENDLOOP,
            TokenType.EOF
    );

    // ── Instance fields ────────────────────────────────────────────────────

    private final List<Token> tokens;
    private int pos;
    private final List<Diagnostic> diagnostics;

    // ── Constructor ────────────────────────────────────────────────────────

    private FFParser(List<Token> tokens, List<Diagnostic> diagnostics) {
        this.tokens = tokens;
        this.pos = 0;
        this.diagnostics = diagnostics;
    }

    // ── Public entry point ─────────────────────────────────────────────────

    /**
     * Parse Fast Formula source code into an AST.
     *
     * @param code the formula source code
     * @return ParseResult with program (if successful) and any diagnostics
     */
    public static ParseResult parse(String code) {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(FFParser.class,
                    "parse: codeLen=" + (code == null ? 0 : code.length()),
                    AppsLogger.FINER);
        }
        var tokenizer = new Tokenizer(code);
        var tokens = tokenizer.tokenize();
        var diagnostics = new ArrayList<Diagnostic>();

        // Add tokenizer errors as diagnostics
        for (String err : tokenizer.getErrors()) {
            diagnostics.add(new Diagnostic(1, 1, "error", err));
        }

        if (!diagnostics.isEmpty()) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(FFParser.class,
                        "parse: tokenizer produced " + diagnostics.size() + " errors",
                        AppsLogger.WARNING);
            }
            return new ParseResult(null, diagnostics);
        }

        var parser = new FFParser(tokens, diagnostics);
        try {
            var program = parser.parseProgram();
            if (!diagnostics.isEmpty()) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(FFParser.class,
                            "parse: completed with " + diagnostics.size() + " diagnostics",
                            AppsLogger.WARNING);
                }
                return new ParseResult(null, diagnostics);
            }
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(FFParser.class,
                        "parse: success, program has " + program.statements().size() + " stmts",
                        AppsLogger.FINER);
            }
            return new ParseResult(program, List.of());
        } catch (ParseError e) {
            // ParseError is the parser's internal control-flow signal — it
            // already carries line/col, so this is a normal validation
            // failure rather than a programmer bug. Stays at WARNING.
            if (diagnostics.isEmpty()) {
                diagnostics.add(new Diagnostic(e.line, e.col, "error", e.getMessage()));
            }
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(FFParser.class,
                        "parse: ParseError at " + e.line + ":" + e.col + " — " + e.getMessage(),
                        AppsLogger.WARNING);
            }
            return new ParseResult(null, diagnostics);
        } catch (Exception e) {
            // SEVERE inside catch — anything that isn't a ParseError leaking
            // out of parseProgram() is a parser bug; we want a stack trace.
            AppsLogger.write(FFParser.class, e, AppsLogger.SEVERE);
            diagnostics.add(new Diagnostic(1, 1, "error",
                    "Internal parser error: " + e.getMessage()));
            return new ParseResult(null, diagnostics);
        }
    }

    // ── Program ────────────────────────────────────────────────────────────

    private Program parseProgram() {
        List<AstNodes> statements = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            AstNodes node = parseStatement();
            // Flatten multi-name declarations
            if (node instanceof InputDecl && ((InputDecl) node).names().size() > 1) {
                for (String name : ((InputDecl) node).names()) {
                    statements.add(new InputDecl(List.of(name)));
                }
            } else if (node instanceof OutputDecl && ((OutputDecl) node).names().size() > 1) {
                for (String name : ((OutputDecl) node).names()) {
                    statements.add(new OutputDecl(List.of(name)));
                }
            } else {
                statements.add(node);
            }
        }
        return new Program(statements);
    }

    // ── Statement ──────────────────────────────────────────────────────────

    private AstNodes parseStatement() {
        AstNodes stmt = parseStatementInner();
        // Consume optional semicolon
        if (check(TokenType.SEMI)) {
            advance();
        }
        return stmt;
    }

    private AstNodes parseStatementInner() {
        TokenType type = peek().type();

        switch (type) {
            case ALIAS: return parseAliasStmt();
            case DEFAULT: return parseDefaultDecl();
            case DEFAULT_DATA_VALUE: return parseDefaultDataValueDecl();
            case INPUT: return parseInputDecl();
            case INPUTS: return parseInputsDecl();
            case OUTPUT: return parseOutputDecl();
            case OUTPUTS: return parseOutputsDecl();
            case LOCAL: return parseLocalDecl();
            case IF: return parseIfStmt();
            case WHILE: return parseWhileStmt();
            case FOR: return parseForStmt();
            case CURSOR: return parseCursorDecl();
            case RETURN: return parseReturnStmt();
            case EXIT: return parseExitStmt();
            case CALL_FORMULA: return parseCallFormulaStmt();
            case CHANGE_CONTEXTS: return parseChangeContextsStmt();
            case EXECUTE: return parseExecuteStmt();
            case LPAREN: return parseBlockStmt();
            case QUOTED_NAME: return parseQuotedAssignment();
            case NAME: return parseNameStartStatement();
            default:
                throw error("Unexpected token: " + peek().text());
        }
    }

    // ── Alias ──────────────────────────────────────────────────────────────

    private AstNodes parseAliasStmt() {
        expect(TokenType.ALIAS);
        String longName = expectName();
        expect(TokenType.AS);
        String shortName = expectName();
        return new AliasDecl(longName, shortName);
    }

    // ── DEFAULT FOR ────────────────────────────────────────────────────────

    private AstNodes parseDefaultDecl() {
        expect(TokenType.DEFAULT);
        expect(TokenType.FOR);
        String name = expectName();
        consumeOptionalDataType();
        expect(TokenType.IS);
        AstNodes value = parseExpr();
        consumeOptionalDataType();
        return new DefaultDecl(name, value);
    }

    // ── DEFAULT_DATA_VALUE FOR ─────────────────────────────────────────────

    private AstNodes parseDefaultDataValueDecl() {
        expect(TokenType.DEFAULT_DATA_VALUE);
        expect(TokenType.FOR);
        String name = expectName();
        expect(TokenType.IS);
        AstNodes value = parseExpr();
        consumeOptionalDataType();
        return new DefaultDataValue(name, value);
    }

    // ── INPUT IS ───────────────────────────────────────────────────────────

    private AstNodes parseInputDecl() {
        expect(TokenType.INPUT);
        expect(TokenType.IS);
        String name = expectName();
        consumeOptionalDataType();
        return new InputDecl(List.of(name));
    }

    // ── INPUTS ARE ─────────────────────────────────────────────────────────

    private AstNodes parseInputsDecl() {
        expect(TokenType.INPUTS);
        expect(TokenType.ARE);
        List<String> names = parseNameList();
        return new InputDecl(names);
    }

    // ── OUTPUT IS ──────────────────────────────────────────────────────────

    private AstNodes parseOutputDecl() {
        expect(TokenType.OUTPUT);
        expect(TokenType.IS);
        String name = expectName();
        consumeOptionalDataType();
        return new OutputDecl(List.of(name));
    }

    // ── OUTPUTS ARE ────────────────────────────────────────────────────────

    private AstNodes parseOutputsDecl() {
        expect(TokenType.OUTPUTS);
        expect(TokenType.ARE);
        List<String> names = parseNameList();
        return new OutputDecl(names);
    }

    // ── LOCAL ──────────────────────────────────────────────────────────────

    private AstNodes parseLocalDecl() {
        expect(TokenType.LOCAL);
        String name = expectName();
        consumeOptionalDataType();
        return new LocalDecl(name);
    }

    // ── Name list: NAME (',' NAME)* with optional data types ───────────────

    private List<String> parseNameList() {
        List<String> names = new ArrayList<>();
        names.add(expectName());
        consumeOptionalDataType();
        while (check(TokenType.COMMA)) {
            advance();
            names.add(expectName());
            consumeOptionalDataType();
        }
        return names;
    }

    // ── Data type: '(' (NUMBER | TEXT | DATE | compound_name) ')' ────────────

    private void consumeOptionalDataType() {
        if (check(TokenType.LPAREN) && isDataTypeAhead()) {
            advance(); // (
            advance(); // NUMBER_TYPE | TEXT_TYPE | DATE_TYPE | compound NAME like number_number
            expect(TokenType.RPAREN);
        }
    }

    private boolean isDataTypeAhead() {
        if (pos + 2 >= tokens.size()) return false;
        TokenType next = tokens.get(pos + 1).type();
        TokenType afterNext = tokens.get(pos + 2).type();
        if (afterNext != TokenType.RPAREN) return false;
        // Standard scalar types and Oracle compound array types (number_number, text_number, etc.)
        return next == TokenType.NUMBER_TYPE || next == TokenType.TEXT_TYPE
                || next == TokenType.DATE_TYPE || next == TokenType.NAME;
    }

    // ── IF / THEN / ELSIF / ELSE / END IF ──────────────────────────────────

    private AstNodes parseIfStmt() {
        expect(TokenType.IF);
        AstNodes condition = parseExpr();
        expect(TokenType.THEN);
        List<AstNodes> thenBody = parseBody(true);

        List<ElsifClause> elsifClauses = new ArrayList<>();
        while (check(TokenType.ELSIF)) {
            advance();
            AstNodes elsifCond = parseExpr();
            expect(TokenType.THEN);
            List<AstNodes> elsifBody = parseBody(true);
            elsifClauses.add(new ElsifClause(elsifCond, elsifBody));
        }

        List<AstNodes> elseBody = List.of();
        if (check(TokenType.ELSE)) {
            advance();
            elseBody = parseBody(true);
        }

        // Optional ENDIF
        if (check(TokenType.ENDIF)) {
            advance();
        }

        return new IfStatement(condition, thenBody, elsifClauses, elseBody);
    }

    // ── WHILE / LOOP / END LOOP ────────────────────────────────────────────

    private AstNodes parseWhileStmt() {
        expect(TokenType.WHILE);
        AstNodes condition = parseExpr();
        expect(TokenType.LOOP);
        boolean paren = check(TokenType.LPAREN);
        List<AstNodes> body = parseBody(false);
        // Oracle FF: WHILE cond LOOP (stmts) — parenthesized body, no END LOOP written.
        // WHILE cond LOOP stmts END LOOP — non-paren body, END LOOP required.
        if (!paren) {
            expect(TokenType.ENDLOOP);
        } else if (check(TokenType.ENDLOOP)) {
            advance(); // optional END LOOP when body was parenthesized
        }
        return new WhileLoop(condition, body);
    }

    // ── FOR (range or cursor) ──────────────────────────────────────────────

    private AstNodes parseForStmt() {
        expect(TokenType.FOR);
        String variable = expectName();
        expect(TokenType.IN);

        // Distinguish FOR range vs FOR cursor
        // FOR cursor: NAME LOOP
        // FOR range: expr '..' expr LOOP
        if (check(TokenType.NAME) && lookahead(1, TokenType.LOOP)) {
            // FOR cursor
            String cursorName = expectName();
            expect(TokenType.LOOP);
            boolean paren = check(TokenType.LPAREN);
            List<AstNodes> body = parseBody(false);
            if (!paren) {
                expect(TokenType.ENDLOOP);
            } else if (check(TokenType.ENDLOOP)) {
                advance();
            }
            return new ForCursorLoop(variable, cursorName, body);
        }

        // FOR range
        AstNodes start = parseExpr();
        expect(TokenType.DOTDOT);
        AstNodes end = parseExpr();
        expect(TokenType.LOOP);
        boolean paren = check(TokenType.LPAREN);
        List<AstNodes> body = parseBody(false);
        if (!paren) {
            expect(TokenType.ENDLOOP);
        } else if (check(TokenType.ENDLOOP)) {
            advance();
        }
        return new ForLoop(variable, start, end, body);
    }

    // ── CURSOR declaration ─────────────────────────────────────────────────

    private AstNodes parseCursorDecl() {
        expect(TokenType.CURSOR);
        String name = expectName();
        expect(TokenType.IS);
        expect(TokenType.SELECT);

        // Select items
        List<AstNodes> selectItems = new ArrayList<>();
        selectItems.add(parseSelectItem());
        while (check(TokenType.COMMA)) {
            advance();
            selectItems.add(parseSelectItem());
        }

        expect(TokenType.FROM);
        String tableName = expectName();

        AstNodes whereClause = null;
        if (check(TokenType.WHERE)) {
            advance();
            whereClause = parseExpr();
        }

        // ORDER BY (consume but ignore for AST)
        if (check(TokenType.ORDER)) {
            advance();
            expect(TokenType.BY);
            parseOrderItem();
            while (check(TokenType.COMMA)) {
                advance();
                parseOrderItem();
            }
        }

        return new CursorDecl(name, tableName, selectItems, whereClause);
    }

    private AstNodes parseSelectItem() {
        AstNodes expr = parseExpr();
        if (check(TokenType.AS)) {
            advance();
            expectName(); // alias — consumed but not stored in current AST
        }
        return expr;
    }

    private void parseOrderItem() {
        parseExpr();
        if (check(TokenType.ASC) || check(TokenType.DESC)) {
            advance();
        }
    }

    // ── RETURN ─────────────────────────────────────────────────────────────

    private AstNodes parseReturnStmt() {
        expect(TokenType.RETURN);

        // Empty return: next token is EOF, SEMI, a body terminator, or a keyword
        // that cannot start an expression
        if (check(TokenType.EOF) || check(TokenType.SEMI) || isBodyTerminator()
                || isNonExpressionStatementStart()) {
            return new ReturnStatement(List.of());
        }

        // RETURN expr (',' expr)*
        List<String> variables = new ArrayList<>();
        AstNodes node = parseExpr();
        if (node instanceof Identifier) {
            variables.add(((Identifier) node).name());
        }
        while (check(TokenType.COMMA)) {
            advance();
            AstNodes next = parseExpr();
            if (next instanceof Identifier) {
                variables.add(((Identifier) next).name());
            }
        }
        return new ReturnStatement(variables);
    }

    // ── EXIT / EXIT WHEN ───────────────────────────────────────────────────

    private AstNodes parseExitStmt() {
        expect(TokenType.EXIT);
        if (check(TokenType.WHEN)) {
            advance();
            AstNodes condition = parseExpr();
            return new ExitStatement(condition);
        }
        return new ExitStatement(null);
    }

    // ── CALL_FORMULA ───────────────────────────────────────────────────────

    private AstNodes parseCallFormulaStmt() {
        expect(TokenType.CALL_FORMULA);
        expect(TokenType.LPAREN);

        List<InParam> inParams = new ArrayList<>();
        List<OutParam> outParams = new ArrayList<>();
        String formulaName = "";

        boolean first = true;
        do {
            if (!first) advance(); // consume comma

            // Parse expr at concat level to avoid consuming > or < as comparison
            AstNodes expr = parseConcat();

            if (check(TokenType.GT)) {
                // in param: expr > param_expr  (param_expr may be 'literal', NAME, or TO_CHAR(x))
                advance();
                AstNodes paramExpr = parseConcat();
                String paramName = paramExpr instanceof StringLiteral ? ((StringLiteral) paramExpr).value()
                        : paramExpr instanceof Identifier ? ((Identifier) paramExpr).name()
                        : paramExpr.toString();
                String varName = expr instanceof Identifier ? ((Identifier) expr).name() : expr.toString();
                inParams.add(new InParam(varName, paramName));
            } else if (check(TokenType.LT)) {
                // out param: expr < param_expr (DEFAULT expr)?
                advance();
                AstNodes paramExpr2 = parseConcat();
                String paramName = paramExpr2 instanceof StringLiteral ? ((StringLiteral) paramExpr2).value()
                        : paramExpr2 instanceof Identifier ? ((Identifier) paramExpr2).name()
                        : paramExpr2.toString();
                Object defaultVal = null;
                if (check(TokenType.DEFAULT)) {
                    advance();
                    defaultVal = parseExpr();
                }
                String varName = expr instanceof Identifier ? ((Identifier) expr).name() : expr.toString();
                outParams.add(new OutParam(varName, paramName, defaultVal));
            } else if (first) {
                // First arg without direction — it's the formula name
                formulaName = expr instanceof StringLiteral ? ((StringLiteral) expr).value() : "";
            }
            first = false;
        } while (check(TokenType.COMMA));

        expect(TokenType.RPAREN);
        return new CallFormula(formulaName, inParams, outParams);
    }

    // ── CHANGE_CONTEXTS ────────────────────────────────────────────────────

    private AstNodes parseChangeContextsStmt() {
        expect(TokenType.CHANGE_CONTEXTS);
        expect(TokenType.LPAREN);
        Map<String, AstNodes> contexts = new LinkedHashMap<>();
        String name = expectContextKey();
        expect(TokenType.EQ);
        AstNodes value = parseExpr();
        contexts.put(name, value);
        while (check(TokenType.COMMA)) {
            advance();
            name = expectContextKey();
            expect(TokenType.EQ);
            value = parseExpr();
            contexts.put(name, value);
        }
        expect(TokenType.RPAREN);
        return new ChangeContexts(contexts);
    }

    private String expectContextKey() {
        String name = expectName();
        // Oracle FF allows array-indexed context keys: CHANGE_CONTEXTS(arr[idx] = val)
        if (check(TokenType.LBRACKET)) {
            advance();
            parseExpr();
            expect(TokenType.RBRACKET);
        }
        return name;
    }

    // ── EXECUTE ────────────────────────────────────────────────────────────

    private AstNodes parseExecuteStmt() {
        expect(TokenType.EXECUTE);
        expect(TokenType.LPAREN);
        // Oracle FF allows EXECUTE('literal'), EXECUTE(variable), or EXECUTE(array[idx])
        AstNodes formulaRef = parseExpr();
        String formulaName = formulaRef instanceof StringLiteral ? ((StringLiteral) formulaRef).value()
                : formulaRef instanceof Identifier ? ((Identifier) formulaRef).name()
                : formulaRef.toString();
        expect(TokenType.RPAREN);
        return new Execute(formulaName);
    }

    // ── Block statement: ( stmt+ ) ─────────────────────────────────────────

    private AstNodes parseBlockStmt() {
        expect(TokenType.LPAREN);
        List<AstNodes> statements = new ArrayList<>();
        while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
            statements.add(parseStatement());
        }
        expect(TokenType.RPAREN);
        return new BlockGroup(statements);
    }

    // ── Quoted name assignment: "NAME" = expr  or  "NAME"[idx] = expr ────────

    private AstNodes parseQuotedAssignment() {
        Token qt = advance(); // QUOTED_NAME
        String name = qt.text().substring(1, qt.text().length() - 1);
        // Oracle FF allows "AREA1"[idx] = expr for indexed array assignment
        if (check(TokenType.LBRACKET)) {
            advance();
            AstNodes index = parseExpr();
            expect(TokenType.RBRACKET);
            expect(TokenType.EQ);
            AstNodes value = parseExpr();
            return new ArrayAssignment(name, index, value);
        }
        expect(TokenType.EQ);
        AstNodes value = parseExpr();
        return new Assignment(name, value);
    }

    // ── NAME-starting statement: assignment, array assignment, or bare func call

    private AstNodes parseNameStartStatement() {
        Token nameToken = peek();
        String name = nameToken.text();

        // NAME '(' → bare function call
        if (lookahead(1, TokenType.LPAREN)) {
            advance(); // consume NAME
            advance(); // consume (
            List<AstNodes> args = List.of();
            if (!check(TokenType.RPAREN)) {
                args = parseExprList();
            }
            expect(TokenType.RPAREN);
            return new FunctionCall(name, args);
        }

        // NAME '[' → array assignment
        if (lookahead(1, TokenType.LBRACKET)) {
            advance(); // consume NAME
            advance(); // consume [
            AstNodes index = parseExpr();
            expect(TokenType.RBRACKET);
            expect(TokenType.EQ);
            AstNodes value = parseExpr();
            return new ArrayAssignment(name, index, value);
        }

        // NAME '=' → assignment
        if (lookahead(1, TokenType.EQ) && !lookahead(1, TokenType.EQGT) && !lookahead(1, TokenType.EQLT)) {
            advance(); // consume NAME
            advance(); // consume =
            AstNodes value = parseExpr();
            return new Assignment(name, value);
        }

        // NAME '.' METHOD ['(' args ')'] → method call as statement (e.g. arr.DELETE(1), arr.TRIM)
        if (lookahead(1, TokenType.DOT)) {
            advance(); // consume NAME
            advance(); // consume .
            String method = expectName();
            List<AstNodes> args = List.of();
            if (check(TokenType.LPAREN)) {
                advance();
                if (!check(TokenType.RPAREN)) {
                    args = parseExprList();
                }
                expect(TokenType.RPAREN);
            }
            return new MethodCall(name, method, args);
        }

        throw error("Expected assignment, function call, or array assignment after '" + name + "'");
    }

    // ── Body parsing (for if/while/for) ────────────────────────────────────

    /**
     * Parse a body: either '(' statement+ ')' or statement+.
     * For if bodies, stops at ELSIF/ELSE/ENDIF.
     * For loop bodies, stops at ENDLOOP.
     */
    private List<AstNodes> parseBody(boolean isIfBody) {
        if (check(TokenType.LPAREN)) {
            // Parenthesized body
            advance();
            List<AstNodes> body = new ArrayList<>();
            while (!check(TokenType.RPAREN) && !check(TokenType.EOF)) {
                body.add(parseStatement());
            }
            expect(TokenType.RPAREN);
            return body;
        }

        List<AstNodes> body = new ArrayList<>();

        if (!isIfBody) {
            // Non-paren loop body: read until ENDLOOP
            while (!check(TokenType.EOF) && !check(TokenType.ENDLOOP)) {
                body.add(parseStatement());
            }
            return body;
        }

        // Non-paren IF/ELSIF/ELSE body: Oracle FF allows "IF cond THEN stmt" without
        // END IF. Read exactly ONE statement. Multi-statement non-paren bodies require
        // explicit END IF and the single-statement read will leave remaining statements
        // at the top level where ENDIF will be consumed by parseIfStmt normally.
        if (!check(TokenType.ELSIF) && !check(TokenType.ELSE)
                && !check(TokenType.ENDIF) && !check(TokenType.EOF)) {
            body.add(parseStatement());
        }
        return body;
    }

    // ── Expression parsing (precedence climbing) ───────────────────────────

    private AstNodes parseExpr() {
        return parseOr();
    }

    // 1. OR
    private AstNodes parseOr() {
        AstNodes left = parseAnd();
        while (check(TokenType.OR)) {
            advance();
            AstNodes right = parseAnd();
            left = new BinaryOp("OR", left, right);
        }
        return left;
    }

    // 2. AND
    private AstNodes parseAnd() {
        AstNodes left = parseNot();
        while (check(TokenType.AND)) {
            advance();
            AstNodes right = parseNot();
            left = new BinaryOp("AND", left, right);
        }
        return left;
    }

    // 3. NOT (unary prefix)
    private AstNodes parseNot() {
        if (check(TokenType.NOT)) {
            advance();
            AstNodes operand = parseNot();
            return new UnaryOp("NOT", operand);
        }
        return parsePostfixExprs();
    }

    // 4-8. Postfix/infix: WAS DEFAULTED, IS NULL, LIKE, BETWEEN, IN, comparison
    private AstNodes parsePostfixExprs() {
        AstNodes left = parseComparison();

        // WAS [NOT] DEFAULTED / WAS [NOT] FOUND
        if (check(TokenType.WAS)) {
            advance();
            if (check(TokenType.NOT)) {
                advance();
                if (check(TokenType.DEFAULTED)) {
                    advance();
                    return new WasNotDefaulted(left.toString());
                }
                if (check(TokenType.FOUND)) {
                    advance();
                    return new UnaryOp("NOT", new BinaryOp("WAS FOUND", left, new NumberLiteral(1)));
                }
                throw error("Expected DEFAULTED or FOUND after WAS NOT");
            }
            if (check(TokenType.DEFAULTED)) {
                advance();
                return new WasDefaulted(left.toString());
            }
            if (check(TokenType.FOUND)) {
                advance();
                return new BinaryOp("WAS FOUND", left, new NumberLiteral(1));
            }
            throw error("Expected DEFAULTED or FOUND after WAS");
        }

        // IS [NOT] NULL
        if (check(TokenType.IS)) {
            int savedPos = pos;
            advance();
            if (check(TokenType.NOT)) {
                advance();
                if (check(TokenType.NULL_KW)) {
                    advance();
                    return new UnaryOp("NOT", new BinaryOp("IS NULL", left, new NumberLiteral(0)));
                }
                // Not IS NOT NULL — restore
                pos = savedPos;
            } else if (check(TokenType.NULL_KW)) {
                advance();
                return new BinaryOp("IS NULL", left, new NumberLiteral(0));
            } else {
                // Not IS NULL — restore
                pos = savedPos;
            }
        }

        // [NOT] LIKE
        if (check(TokenType.LIKE)) {
            advance();
            AstNodes pattern = parseComparison();
            return new LikeExpr(left, pattern, false);
        }
        if (check(TokenType.NOT)) {
            int savedPos = pos;
            advance();
            if (check(TokenType.LIKE)) {
                advance();
                AstNodes pattern = parseComparison();
                return new LikeExpr(left, pattern, true);
            }
            if (check(TokenType.BETWEEN)) {
                advance();
                AstNodes low = parseComparison();
                expect(TokenType.AND);
                AstNodes high = parseComparison();
                return new BetweenExpr(left, low, high, true);
            }
            if (check(TokenType.IN)) {
                advance();
                expect(TokenType.LPAREN);
                List<AstNodes> items = parseExprList();
                expect(TokenType.RPAREN);
                return new InExpr(left, items, true);
            }
            // Not a NOT LIKE/BETWEEN/IN — restore
            pos = savedPos;
        }

        // BETWEEN ... AND ...
        if (check(TokenType.BETWEEN)) {
            advance();
            AstNodes low = parseComparison();
            expect(TokenType.AND);
            AstNodes high = parseComparison();
            return new BetweenExpr(left, low, high, false);
        }

        // IN (...)
        if (check(TokenType.IN)) {
            int savedPos = pos;
            advance();
            if (check(TokenType.LPAREN)) {
                advance();
                List<AstNodes> items = parseExprList();
                expect(TokenType.RPAREN);
                return new InExpr(left, items, false);
            }
            // Not IN (...) — restore
            pos = savedPos;
        }

        return left;
    }

    // 9. Comparison
    private AstNodes parseComparison() {
        AstNodes left = parseConcat();
        if (COMP_OPS.contains(peek().type())) {
            String op = advance().text();
            AstNodes right = parseConcat();
            left = new BinaryOp(op, left, right);
        }
        return left;
    }

    // 10. Concatenation (||)
    private AstNodes parseConcat() {
        AstNodes left = parseAddition();
        while (check(TokenType.CONCAT)) {
            advance();
            AstNodes right = parseAddition();
            left = new Concat(left, right);
        }
        return left;
    }

    // 11. Addition (+, -)
    private AstNodes parseAddition() {
        AstNodes left = parseMultiplication();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            String op = advance().text();
            AstNodes right = parseMultiplication();
            left = new BinaryOp(op, left, right);
        }
        return left;
    }

    // 12. Multiplication (*, /)
    private AstNodes parseMultiplication() {
        AstNodes left = parseUnaryMinus();
        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            String op = advance().text();
            AstNodes right = parseUnaryMinus();
            left = new BinaryOp(op, left, right);
        }
        return left;
    }

    // 13. Unary minus
    private AstNodes parseUnaryMinus() {
        if (check(TokenType.MINUS)) {
            advance();
            AstNodes operand = parseUnaryMinus();
            return new UnaryOp("-", operand);
        }
        return parseAtom();
    }

    // 14. Atom
    private AstNodes parseAtom() {
        Token t = peek();

        // Number literal
        if (check(TokenType.NUMBER)) {
            advance();
            return new NumberLiteral(Double.parseDouble(t.text()));
        }

        // Single string literal (may be typed)
        if (check(TokenType.SINGLE_STRING)) {
            advance();
            String raw = t.text();
            String value = raw.substring(1, raw.length() - 1).replace("''", "'");

            // Check for typed string: 'string'(TYPE)
            if (check(TokenType.LPAREN) && isDataTypeAhead()) {
                advance(); // (
                String typeName = advance().text().toUpperCase(); // NUMBER_TYPE | TEXT_TYPE | DATE_TYPE
                expect(TokenType.RPAREN);
                return new TypedString(value, typeName);
            }

            return new StringLiteral(value);
        }

        // Quoted name (identifier) — may be followed by [idx] for array element access
        if (check(TokenType.QUOTED_NAME)) {
            advance();
            String raw = t.text();
            String name = raw.substring(1, raw.length() - 1);
            if (check(TokenType.LBRACKET)) {
                advance();
                AstNodes index = parseExpr();
                expect(TokenType.RBRACKET);
                return new ArrayAccess(name, index);
            }
            return new QuotedIdentifier(name);
        }

        // NAME — could be variable, function call, method call, or array access
        if (check(TokenType.NAME)) {
            return parseNameExpr();
        }

        // Parenthesized expression
        if (check(TokenType.LPAREN)) {
            advance();
            AstNodes expr = parseExpr();
            expect(TokenType.RPAREN);
            return expr;
        }

        throw error("Expected expression, got: " + t.text());
    }

    /**
     * Parse a NAME-starting expression:
     * NAME '.' NAME '(' args ')' → MethodCall
     * NAME '(' args ')' → FunctionCall
     * NAME '[' expr ']' → ArrayAccess
     * NAME → Identifier
     */
    private AstNodes parseNameExpr() {
        Token nameToken = advance();
        String name = nameToken.text();

        // NAME '.' NAME ['(' args ')'] → method call OR collection attribute
        //
        // Both forms are valid in Oracle Fast Formula / PL/SQL context:
        //   arr.COUNT              — parameterless attribute on a PL/SQL collection
        //   arr.FIRST              — same, returns first index
        //   arr.EXISTS(i)          — method with a single arg
        //   arr.DELETE(i, j)       — method with multiple args
        //
        // The AST collapses both into a single MethodCall node with an empty
        // args list for the bare-attribute case; the interpreter treats them
        // uniformly (traces the call and returns 0), so an empty-args
        // MethodCall is safe to construct here.
        if (check(TokenType.DOT)) {
            advance();
            String method = expectName();
            List<AstNodes> args = List.of();
            if (check(TokenType.LPAREN)) {
                advance();
                if (!check(TokenType.RPAREN)) {
                    args = parseExprList();
                }
                expect(TokenType.RPAREN);
            }
            return new MethodCall(name, method, args);
        }

        // NAME '(' → function call
        if (check(TokenType.LPAREN)) {
            advance();
            List<AstNodes> args = List.of();
            if (!check(TokenType.RPAREN)) {
                args = parseExprList();
            }
            expect(TokenType.RPAREN);
            return new FunctionCall(name, args);
        }

        // NAME '[' → array access
        if (check(TokenType.LBRACKET)) {
            advance();
            AstNodes index = parseExpr();
            expect(TokenType.RBRACKET);
            return new ArrayAccess(name, index);
        }

        // Plain identifier
        return new Identifier(name);
    }

    // ── Expression list ────────────────────────────────────────────────────

    private List<AstNodes> parseExprList() {
        List<AstNodes> exprs = new ArrayList<>();
        exprs.add(parseExpr());
        while (check(TokenType.COMMA)) {
            advance();
            exprs.add(parseExpr());
        }
        return exprs;
    }

    // ── Helper: single string value (strip quotes) ─────────────────────────

    private String expectSingleString() {
        Token t = expect(TokenType.SINGLE_STRING);
        String raw = t.text();
        return raw.substring(1, raw.length() - 1);
    }

    // ── Helper: expect NAME token, return its text ─────────────────────────

    private String expectName() {
        Token t = peek();
        if (t.type() == TokenType.NAME) {
            advance();
            return t.text();
        }
        // Quoted identifiers like "EFFECTIVE_DATE" are valid in name positions
        if (t.type() == TokenType.QUOTED_NAME) {
            advance();
            return t.text().substring(1, t.text().length() - 1);
        }
        if (isKeywordUsableAsName(t.type())) {
            advance();
            return t.text();
        }
        throw error("Expected identifier, got: " + t.text());
    }

    private boolean isKeywordUsableAsName(TokenType type) {
        // In Oracle Fast Formula, many keyword-like words can appear as variable names
        // in certain positions. Allow common ones.
        switch (type) {
            case DEFAULTED:
            case FOUND:
            case ASC:
            case DESC:
            case BY:
            case ORDER:
            case WHERE:
            case FROM:
            case SELECT:
            case CURSOR:
            case NULL_KW:
            case USING:
            case NUMBER_TYPE:
            case TEXT_TYPE:
            case DATE_TYPE:
                return true;
            default:
                return false;
        }
    }

    // ── Helper: detect body terminators ─────────────────────────────────────

    private boolean isBodyTerminator() {
        return BODY_TERMINATORS.contains(peek().type());
    }

    private boolean isStatementStart() {
        TokenType type = peek().type();
        return STMT_START_KEYWORDS.contains(type) || type == TokenType.NAME
                || type == TokenType.QUOTED_NAME || type == TokenType.LPAREN;
    }

    /**
     * Returns true if the current token starts a statement but cannot start an expression.
     * Used to detect empty RETURN: RETURN followed by a keyword like IF, DEFAULT, etc.
     */
    private boolean isNonExpressionStatementStart() {
        return STMT_START_KEYWORDS.contains(peek().type()) || peek().type() == TokenType.RPAREN;
    }

    // ── Token stream helpers ───────────────────────────────────────────────

    private Token peek() {
        return tokens.get(pos);
    }

    private boolean check(TokenType type) {
        return tokens.get(pos).type() == type;
    }

    private boolean lookahead(int offset, TokenType type) {
        int idx = pos + offset;
        return idx < tokens.size() && tokens.get(idx).type() == type;
    }

    private Token advance() {
        Token t = tokens.get(pos);
        if (t.type() != TokenType.EOF) {
            pos++;
        }
        return t;
    }

    private Token expect(TokenType type) {
        Token t = peek();
        if (t.type() != type) {
            throw error("Expected " + type + ", got: " + t.text());
        }
        return advance();
    }

    // ── Error handling ─────────────────────────────────────────────────────

    private ParseError error(String message) {
        Token t = peek();
        diagnostics.add(new Diagnostic(t.line(), t.col(), "error", message));
        return new ParseError(message, t.line(), t.col());
    }

    private static class ParseError extends RuntimeException {
        final int line;
        final int col;
        ParseError(String message, int line, int col) {
            super(message);
            this.line = line;
            this.col = col;
        }
    }

}
