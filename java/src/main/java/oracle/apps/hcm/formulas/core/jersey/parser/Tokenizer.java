package oracle.apps.hcm.formulas.core.jersey.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-written lexer for Oracle Fast Formula.
 * Produces a stream of Token objects from source code.
 * All keywords are case-insensitive.
 */
public final class Tokenizer {

    // ── Token type enum ────────────────────────────────────────────────────

    public enum TokenType {
        // Keywords
        ALIAS, AS, DEFAULT_DATA_VALUE, DEFAULT, DEFAULTED, FOUND, FOR,
        INPUT, INPUTS, OUTPUT, OUTPUTS, LOCAL, IS, ARE,
        IF, THEN, ELSIF, ELSE, ENDIF, WHILE, LOOP, ENDLOOP,
        RETURN, EXIT, WHEN, EXECUTE, CALL_FORMULA, CHANGE_CONTEXTS,
        OR, AND, NOT, WAS, LIKE, BETWEEN, IN, USING, NULL_KW,
        CURSOR, SELECT, FROM, WHERE, ORDER, BY, ASC, DESC,
        NUMBER_TYPE, TEXT_TYPE, DATE_TYPE,
        FOR_KW, // unused alias — FOR is the canonical token

        // Operators
        CONCAT,     // ||
        DOTDOT,     // ..
        SEMI,       // ;
        EQ,         // =
        NEQ,        // !=
        LTGT,       // <>
        GTLT,       // ><
        LT,         // <
        GT,         // >
        LTEQ,       // <=
        GTEQ,       // >=
        EQGT,       // =>
        EQLT,       // =<
        PLUS,       // +
        MINUS,      // -
        STAR,       // *
        SLASH,      // /

        // Delimiters
        LPAREN,     // (
        RPAREN,     // )
        LBRACKET,   // [
        RBRACKET,   // ]
        COMMA,      // ,
        DOT,        // .

        // Literals
        NUMBER,
        SINGLE_STRING,
        QUOTED_NAME,

        // Identifier
        NAME,

        // End of file
        EOF
    }

    // ── Token ──────────────────────────────────────────────────────────────

    public static final class Token {
        private final TokenType type;
        private final String text;
        private final int line;
        private final int col;

        public Token(TokenType type, String text, int line, int col) {
            this.type = type;
            this.text = text;
            this.line = line;
            this.col = col;
        }

        public TokenType type() { return type; }
        public String text() { return text; }
        public int line() { return line; }
        public int col() { return col; }

        @Override
        public String toString() {
            return type + "(" + text + ") at " + line + ":" + col;
        }
    }

    // ── Keyword map (uppercase -> TokenType) ───────────────────────────────

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("ALIAS", TokenType.ALIAS),
            Map.entry("AS", TokenType.AS),
            Map.entry("DEFAULT_DATA_VALUE", TokenType.DEFAULT_DATA_VALUE),
            Map.entry("DEFAULT", TokenType.DEFAULT),
            Map.entry("DEFAULTED", TokenType.DEFAULTED),
            Map.entry("FOUND", TokenType.FOUND),
            Map.entry("FOR", TokenType.FOR),
            Map.entry("INPUT", TokenType.INPUT),
            Map.entry("INPUTS", TokenType.INPUTS),
            Map.entry("OUTPUT", TokenType.OUTPUT),
            Map.entry("OUTPUTS", TokenType.OUTPUTS),
            Map.entry("LOCAL", TokenType.LOCAL),
            Map.entry("IS", TokenType.IS),
            Map.entry("ARE", TokenType.ARE),
            Map.entry("IF", TokenType.IF),
            Map.entry("THEN", TokenType.THEN),
            Map.entry("ELSIF", TokenType.ELSIF),
            Map.entry("ELSE", TokenType.ELSE),
            Map.entry("WHILE", TokenType.WHILE),
            Map.entry("LOOP", TokenType.LOOP),
            Map.entry("RETURN", TokenType.RETURN),
            Map.entry("EXIT", TokenType.EXIT),
            Map.entry("WHEN", TokenType.WHEN),
            Map.entry("EXECUTE", TokenType.EXECUTE),
            Map.entry("CALL_FORMULA", TokenType.CALL_FORMULA),
            Map.entry("CHANGE_CONTEXTS", TokenType.CHANGE_CONTEXTS),
            Map.entry("OR", TokenType.OR),
            Map.entry("AND", TokenType.AND),
            Map.entry("NOT", TokenType.NOT),
            Map.entry("WAS", TokenType.WAS),
            Map.entry("LIKE", TokenType.LIKE),
            Map.entry("BETWEEN", TokenType.BETWEEN),
            Map.entry("IN", TokenType.IN),
            Map.entry("USING", TokenType.USING),
            Map.entry("NULL", TokenType.NULL_KW),
            Map.entry("CURSOR", TokenType.CURSOR),
            Map.entry("SELECT", TokenType.SELECT),
            Map.entry("FROM", TokenType.FROM),
            Map.entry("WHERE", TokenType.WHERE),
            Map.entry("ORDER", TokenType.ORDER),
            Map.entry("BY", TokenType.BY),
            Map.entry("ASC", TokenType.ASC),
            Map.entry("DESC", TokenType.DESC),
            Map.entry("ENDIF", TokenType.ENDIF),
            Map.entry("ENDLOOP", TokenType.ENDLOOP),
            Map.entry("NUMBER", TokenType.NUMBER_TYPE),
            Map.entry("TEXT", TokenType.TEXT_TYPE),
            Map.entry("DATE", TokenType.DATE_TYPE)
    );

    // Keywords that can also appear as identifiers in certain contexts
    // (e.g. END is part of ENDIF/ENDLOOP but is not a standalone keyword)
    private static final Set<String> COMPOUND_END_FOLLOWERS = Set.of("IF", "LOOP");

    // ── Instance fields ────────────────────────────────────────────────────

    private final String source;
    private final int length;
    private int pos;
    private int line;
    private int col;
    private final List<String> errors;

    // ── Constructor ────────────────────────────────────────────────────────

    public Tokenizer(String source) {
        this.source = source;
        this.length = source.length();
        this.pos = 0;
        this.line = 1;
        this.col = 1;
        this.errors = new ArrayList<>();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < length) {
            skipWhitespaceAndComments();
            if (pos >= length) break;
            Token t = nextToken();
            if (t != null) {
                tokens.add(t);
            }
        }
        tokens.add(new Token(TokenType.EOF, "", line, col));
        return tokens;
    }

    public List<String> getErrors() {
        return errors;
    }

    // ── Tokenization ───────────────────────────────────────────────────────

    private Token nextToken() {
        int startLine = line;
        int startCol = col;
        char c = source.charAt(pos);

        // Single-quoted string
        if (c == '\'') {
            return readSingleString(startLine, startCol);
        }

        // Double-quoted name
        if (c == '"') {
            return readQuotedName(startLine, startCol);
        }

        // Number literal
        if (Character.isDigit(c)) {
            return readNumber(startLine, startCol);
        }

        // Identifier or keyword (including compound keywords with underscore like CALL_FORMULA)
        if (Character.isLetter(c) || c == '_') {
            return readNameOrKeyword(startLine, startCol);
        }

        // Two-character operators
        if (pos + 1 < length) {
            char c2 = source.charAt(pos + 1);
            if (c == '|' && c2 == '|') { advance(); advance(); return new Token(TokenType.CONCAT, "||", startLine, startCol); }
            if (c == '.' && c2 == '.') { advance(); advance(); return new Token(TokenType.DOTDOT, "..", startLine, startCol); }
            if (c == '!' && c2 == '=') { advance(); advance(); return new Token(TokenType.NEQ, "!=", startLine, startCol); }
            if (c == '<' && c2 == '>') { advance(); advance(); return new Token(TokenType.LTGT, "<>", startLine, startCol); }
            if (c == '>' && c2 == '<') { advance(); advance(); return new Token(TokenType.GTLT, "><", startLine, startCol); }
            if (c == '<' && c2 == '=') { advance(); advance(); return new Token(TokenType.LTEQ, "<=", startLine, startCol); }
            if (c == '>' && c2 == '=') { advance(); advance(); return new Token(TokenType.GTEQ, ">=", startLine, startCol); }
            if (c == '=' && c2 == '>') { advance(); advance(); return new Token(TokenType.EQGT, "=>", startLine, startCol); }
            if (c == '=' && c2 == '<') { advance(); advance(); return new Token(TokenType.EQLT, "=<", startLine, startCol); }
        }

        // Single-character operators and delimiters
        advance();
        switch (c) {
            case '=': return new Token(TokenType.EQ, "=", startLine, startCol);
            case '<': return new Token(TokenType.LT, "<", startLine, startCol);
            case '>': return new Token(TokenType.GT, ">", startLine, startCol);
            case '+': return new Token(TokenType.PLUS, "+", startLine, startCol);
            case '-': return new Token(TokenType.MINUS, "-", startLine, startCol);
            case '*': return new Token(TokenType.STAR, "*", startLine, startCol);
            case '/': return new Token(TokenType.SLASH, "/", startLine, startCol);
            case ';': return new Token(TokenType.SEMI, ";", startLine, startCol);
            case '(': return new Token(TokenType.LPAREN, "(", startLine, startCol);
            case ')': return new Token(TokenType.RPAREN, ")", startLine, startCol);
            case '[': return new Token(TokenType.LBRACKET, "[", startLine, startCol);
            case ']': return new Token(TokenType.RBRACKET, "]", startLine, startCol);
            case ',': return new Token(TokenType.COMMA, ",", startLine, startCol);
            case '.': return new Token(TokenType.DOT, ".", startLine, startCol);
            default:
                errors.add("Unexpected character '" + c + "' at line " + startLine + ":" + startCol);
                return null;
        }
    }

    private Token readSingleString(int startLine, int startCol) {
        advance(); // skip opening '
        var sb = new StringBuilder();
        sb.append('\'');
        while (pos < length) {
            char c = source.charAt(pos);
            if (c == '\'') {
                sb.append('\'');
                advance();
                // Check for escaped quote ''
                if (pos < length && source.charAt(pos) == '\'') {
                    sb.append('\'');
                    advance();
                } else {
                    return new Token(TokenType.SINGLE_STRING, sb.toString(), startLine, startCol);
                }
            } else {
                sb.append(c);
                advance();
            }
        }
        errors.add("Unterminated string starting at line " + startLine + ":" + startCol);
        return new Token(TokenType.SINGLE_STRING, sb.toString(), startLine, startCol);
    }

    private Token readQuotedName(int startLine, int startCol) {
        advance(); // skip opening "
        var sb = new StringBuilder();
        sb.append('"');
        while (pos < length) {
            char c = source.charAt(pos);
            if (c == '"') {
                sb.append('"');
                advance();
                return new Token(TokenType.QUOTED_NAME, sb.toString(), startLine, startCol);
            }
            sb.append(c);
            advance();
        }
        errors.add("Unterminated quoted name starting at line " + startLine + ":" + startCol);
        return new Token(TokenType.QUOTED_NAME, sb.toString(), startLine, startCol);
    }

    private Token readNumber(int startLine, int startCol) {
        var sb = new StringBuilder();
        while (pos < length && Character.isDigit(source.charAt(pos))) {
            sb.append(source.charAt(pos));
            advance();
        }
        if (pos < length && source.charAt(pos) == '.' && pos + 1 < length && Character.isDigit(source.charAt(pos + 1))) {
            sb.append('.');
            advance();
            while (pos < length && Character.isDigit(source.charAt(pos))) {
                sb.append(source.charAt(pos));
                advance();
            }
        }
        return new Token(TokenType.NUMBER, sb.toString(), startLine, startCol);
    }

    private Token readNameOrKeyword(int startLine, int startCol) {
        var sb = new StringBuilder();
        while (pos < length && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
            sb.append(source.charAt(pos));
            advance();
        }
        String text = sb.toString();
        String upper = text.toUpperCase();

        // Handle END IF / END LOOP as compound keywords
        if ("END".equals(upper)) {
            int savedPos = pos;
            int savedLine = line;
            int savedCol = col;
            skipWhitespaceOnly();
            if (pos < length) {
                int wordStart = pos;
                var sb2 = new StringBuilder();
                while (pos < length && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
                    sb2.append(source.charAt(pos));
                    advance();
                }
                String nextWord = sb2.toString().toUpperCase();
                if ("IF".equals(nextWord)) {
                    return new Token(TokenType.ENDIF, text + " " + sb2, startLine, startCol);
                }
                if ("LOOP".equals(nextWord)) {
                    return new Token(TokenType.ENDLOOP, text + " " + sb2, startLine, startCol);
                }
                // Not a compound keyword — restore position
                pos = savedPos;
                line = savedLine;
                col = savedCol;
            }
            // "END" alone — treat as NAME
            return new Token(TokenType.NAME, text, startLine, startCol);
        }

        // Check for keyword match
        TokenType kwType = KEYWORDS.get(upper);
        if (kwType != null) {
            return new Token(kwType, text, startLine, startCol);
        }

        return new Token(TokenType.NAME, text, startLine, startCol);
    }

    // ── Whitespace and comment skipping ─────────────────────────────────────

    private void skipWhitespaceAndComments() {
        while (pos < length) {
            char c = source.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
            } else if (c == '/' && pos + 1 < length && source.charAt(pos + 1) == '*') {
                skipBlockComment();
            } else if (c == '-' && pos + 1 < length && source.charAt(pos + 1) == '-') {
                skipLineComment();
            } else {
                break;
            }
        }
    }

    private void skipWhitespaceOnly() {
        while (pos < length) {
            char c = source.charAt(pos);
            if (c == ' ' || c == '\t') {
                advance();
            } else {
                break;
            }
        }
    }

    private void skipBlockComment() {
        advance(); advance(); // skip /*
        while (pos < length) {
            if (source.charAt(pos) == '*' && pos + 1 < length && source.charAt(pos + 1) == '/') {
                advance(); advance();
                return;
            }
            advance();
        }
        errors.add("Unterminated block comment");
    }

    private void skipLineComment() {
        while (pos < length && source.charAt(pos) != '\n') {
            advance();
        }
    }

    // ── Character advancement ──────────────────────────────────────────────

    private void advance() {
        if (pos < length) {
            if (source.charAt(pos) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            pos++;
        }
    }
}
