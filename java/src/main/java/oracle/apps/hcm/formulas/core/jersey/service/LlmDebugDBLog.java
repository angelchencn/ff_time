package oracle.apps.hcm.formulas.core.jersey.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.apps.hcm.formulas.core.jersey.config.DbConfig;

/**
 * Persistent LLM debug log backed by {@code PAY_ACTION_LOGS} (header) and
 * {@code PAY_ACTION_LOG_LINES} (detail). Unlike {@link LlmDebugLog} which
 * stores entries in JVM memory (lost on restart, not shared across
 * ServiceServer instances), this version persists to the database so logs
 * are visible from any server in the cluster.
 *
 * <h3>Table layout</h3>
 * <pre>
 * PAY_ACTION_LOGS        1 row per LLM request
 *   ACTION_LOG_ID (PK)   S_ROW_ID_SEQ.NEXTVAL
 *   NAME                 'FF_AI_GENERATE'
 *   STATUS               'S' success / 'E' error
 *   LOG_TYPE             'LLM_DEBUG'
 *   SOURCE_TYPE          provider type (AGENT_STUDIO / SPECTRA / OPENAI)
 *   CREATOR_TYPE         endpoint or workflow code
 *
 * PAY_ACTION_LOG_LINES   N rows per request (4000 char per line)
 *   LINE_NUMBER ranges:
 *     1       = Summary
 *     2       = Message (user request)
 *     10-19   = SystemPrompt (split)
 *     20      = FormulaType
 *     30-39   = ReferenceFormula (split)
 *     40-49   = AdditionalRules (split)
 *     50-59   = EditorCode (split)
 *     60-69   = ChatHistory (split)
 *     90      = TokenBreakdown
 *     100-199 = Response (split)
 * </pre>
 */
public class LlmDebugDBLog {

    private static final LlmDebugDBLog INSTANCE = new LlmDebugDBLog();
    private static final int MAX_LINE_LENGTH = 4000;

    public static LlmDebugDBLog getInstance() { return INSTANCE; }

    private static final String NEXT_ID_SQL =
            "SELECT S_ROW_ID_SEQ.NEXTVAL FROM DUAL";

    private static final String INSERT_LOG_SQL =
            "INSERT INTO PAY_ACTION_LOGS ("
            + "ACTION_LOG_ID, NAME, STATUS, LOG_TYPE, SOURCE_TYPE, CREATOR_TYPE, "
            + "CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE"
            + ") VALUES ("
            + "?, ?, ?, 'LLM_DEBUG', ?, ?, "
            + "'SEED_DATA_FROM_APPLICATION', SYSTIMESTAMP, 'SEED_DATA_FROM_APPLICATION', SYSTIMESTAMP"
            + ")";

    private static final String INSERT_LINE_SQL =
            "INSERT INTO PAY_ACTION_LOG_LINES ("
            + "ACTION_LOG_LINE_ID, ACTION_LOG_ID, LINE_NUMBER, LINE_TEXT, "
            + "CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE"
            + ") VALUES ("
            + "S_ROW_ID_SEQ.NEXTVAL, ?, ?, ?, "
            + "'SEED_DATA_FROM_APPLICATION', SYSTIMESTAMP, 'SEED_DATA_FROM_APPLICATION', SYSTIMESTAMP"
            + ")";

    private static final String QUERY_LOGS_SQL =
            "SELECT ACTION_LOG_ID, NAME, STATUS, LOG_TYPE, SOURCE_TYPE, CREATOR_TYPE, "
            + "CREATION_DATE "
            + "FROM PAY_ACTION_LOGS WHERE LOG_TYPE = 'LLM_DEBUG' "
            + "ORDER BY CREATION_DATE DESC FETCH FIRST 20 ROWS ONLY";

    private static final String QUERY_LINES_SQL =
            "SELECT LINE_NUMBER, LINE_TEXT "
            + "FROM PAY_ACTION_LOG_LINES WHERE ACTION_LOG_ID = ? "
            + "ORDER BY LINE_NUMBER";

    private static final String QUERY_LATEST_LOG_SQL =
            "SELECT ACTION_LOG_ID FROM PAY_ACTION_LOGS "
            + "WHERE LOG_TYPE = 'LLM_DEBUG' "
            + "ORDER BY CREATION_DATE DESC FETCH FIRST 1 ROWS ONLY";

    /**
     * Record a structured PromptContext to the database.
     */
    public void record(String model, int maxTokens, String endpoint,
                       PromptContext context) {
        record(model, maxTokens, endpoint, context, null);
    }

    /**
     * Record with optional response text.
     */
    public void record(String model, int maxTokens, String endpoint,
                       PromptContext context, String response) {
        try (Connection conn = DbConfig.getConnection()) {
            // 1. Pre-fetch sequence value (getGeneratedKeys() not reliable
            //    on ADF BC DBTransaction proxy connections in Fusion).
            long logId;
            try (PreparedStatement seqPs = conn.prepareStatement(NEXT_ID_SQL);
                 ResultSet seqRs = seqPs.executeQuery()) {
                if (!seqRs.next()) {
                    throw new SQLException("S_ROW_ID_SEQ.NEXTVAL returned no rows");
                }
                logId = seqRs.getLong(1);
            }

            // Insert header with pre-fetched ID
            try (PreparedStatement ps = conn.prepareStatement(INSERT_LOG_SQL)) {
                ps.setLong(1, logId);
                ps.setString(2, "FF_AI_GENERATE");
                ps.setString(3, response != null && response.startsWith("Error:") ? "E" : "S");
                ps.setString(4, truncate(model, 30));
                ps.setString(5, truncate(endpoint, 30));
                ps.executeUpdate();
            }

            // 2. Insert lines — one line per field, truncated to 3900 chars.
            //    Token breakdown uses original (untruncated) lengths so the
            //    UI still shows accurate token estimates.
            try (PreparedStatement ps = conn.prepareStatement(INSERT_LINE_SQL)) {
                // Line 1: Summary (uses original lengths for accuracy)
                String summary = "model=" + model
                        + "|endpoint=" + endpoint
                        + "|maxTokens=" + maxTokens
                        + "|formulaType=" + context.formulaTypeOrEmpty()
                        + "|totalChars=" + totalChars(context)
                        + "|estTokens=" + (totalChars(context) / 4);
                insertLine(ps, logId, 1, summary);

                // Line 2: Message
                safeInsertLine(ps, logId, 2, "MESSAGE|" + truncateWithEllipsis(context.messageOrEmpty()));

                // Line 10: SystemPrompt (truncated preview)
                safeInsertLine(ps, logId, 10, "SYSTEM_PROMPT|" + truncateWithEllipsis(context.systemPromptOrEmpty()));

                // Line 20: FormulaType
                safeInsertLine(ps, logId, 20, "FORMULA_TYPE|" + context.formulaTypeOrEmpty());

                // Line 30: ReferenceFormula
                safeInsertLine(ps, logId, 30, "REFERENCE_FORMULA|" + truncateWithEllipsis(context.referenceFormulaOrEmpty()));

                // Line 40: AdditionalRules
                safeInsertLine(ps, logId, 40, "ADDITIONAL_RULES|" + truncateWithEllipsis(context.additionalRulesOrEmpty()));

                // Line 50: EditorCode
                safeInsertLine(ps, logId, 50, "EDITOR_CODE|" + truncateWithEllipsis(context.editorCodeOrEmpty()));

                // Line 90: Token breakdown (uses original lengths)
                String breakdown = buildTokenBreakdown(context);
                safeInsertLine(ps, logId, 90, "TOKEN_BREAKDOWN|" + breakdown);

                // Line 100: Response
                if (response != null && !response.isBlank()) {
                    safeInsertLine(ps, logId, 100, "RESPONSE|" + truncateWithEllipsis(response));
                }
            }

            conn.commit();

            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "[LlmDebugDBLog] Recorded logId=" + logId
                                + " model=" + model + " endpoint=" + endpoint,
                        AppsLogger.FINER);
            }

        } catch (Exception e) {
            // Debug logging should never break the main flow
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "[LlmDebugDBLog] Failed to record: " + e.getMessage(),
                        AppsLogger.WARNING);
            }
        }
    }

    /**
     * Append session_id / conversationId to the latest log entry (LINE 3).
     * Called from FastFormulaResource after the session_id is resolved.
     */
    public void recordSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        try (Connection conn = DbConfig.getConnection()) {
            // Find the latest log id
            long logId;
            try (PreparedStatement ps = conn.prepareStatement(QUERY_LATEST_LOG_SQL);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                logId = rs.getLong("ACTION_LOG_ID");
            }
            try (PreparedStatement ps = conn.prepareStatement(INSERT_LINE_SQL)) {
                insertLine(ps, logId, 3, "SESSION_ID|" + sessionId);
            }
            conn.commit();
        } catch (Exception e) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "[LlmDebugDBLog] Failed to record sessionId: " + e.getMessage(),
                        AppsLogger.WARNING);
            }
        }
    }

    /**
     * Update an existing log entry with the LLM response text.
     */
    public void recordResponse(long logId, String response, boolean isError) {
        try (Connection conn = DbConfig.getConnection()) {
            if (isError) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE PAY_ACTION_LOGS SET STATUS = 'E', "
                        + "LAST_UPDATE_DATE = SYSTIMESTAMP "
                        + "WHERE ACTION_LOG_ID = ?")) {
                    ps.setLong(1, logId);
                    ps.executeUpdate();
                }
            }

            if (response != null && !response.isBlank()) {
                try (PreparedStatement ps = conn.prepareStatement(INSERT_LINE_SQL)) {
                    insertLine(ps, logId, 100, "RESPONSE|" + truncateWithEllipsis(response));
                }
            }

            conn.commit();
        } catch (Exception e) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "[LlmDebugDBLog] Failed to record response: " + e.getMessage(),
                        AppsLogger.WARNING);
            }
        }
    }

    /**
     * Get all recent LLM debug logs (last 20).
     */
    public List<Map<String, Object>> getAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(QUERY_LOGS_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                var entry = new LinkedHashMap<String, Object>();
                long logId = rs.getLong("ACTION_LOG_ID");
                entry.put("log_id", logId);
                entry.put("name", rs.getString("NAME"));
                entry.put("status", rs.getString("STATUS"));
                entry.put("source_type", rs.getString("SOURCE_TYPE"));
                entry.put("creator_type", rs.getString("CREATOR_TYPE"));
                entry.put("timestamp", rs.getTimestamp("CREATION_DATE").toString());
                entry.put("summary", getLineText(conn, logId, 1));
                String msgLine = getLineText(conn, logId, 2);
                entry.put("message", stripPrefix(msgLine, "MESSAGE|"));
                result.add(entry);
            }
        } catch (Exception e) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "[LlmDebugDBLog] getAll failed: " + e.getMessage(),
                        AppsLogger.WARNING);
            }
        }
        return result;
    }

    /**
     * Get the latest log entry with all lines.
     */
    public Map<String, Object> getLatest() {
        try (Connection conn = DbConfig.getConnection()) {
            long logId;
            try (PreparedStatement ps = conn.prepareStatement(QUERY_LATEST_LOG_SQL);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Map.of();
                logId = rs.getLong("ACTION_LOG_ID");
            }
            return getLogDetail(conn, logId);
        } catch (Exception e) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "[LlmDebugDBLog] getLatest failed: " + e.getMessage(),
                        AppsLogger.WARNING);
            }
            return Map.of();
        }
    }

    /**
     * Get a specific log entry with all lines, reassembled into fields.
     */
    public Map<String, Object> getLogDetail(long logId) {
        try (Connection conn = DbConfig.getConnection()) {
            return getLogDetail(conn, logId);
        } catch (Exception e) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "[LlmDebugDBLog] getLogDetail failed: " + e.getMessage(),
                        AppsLogger.WARNING);
            }
            return Map.of();
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private Map<String, Object> getLogDetail(Connection conn, long logId) throws SQLException {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("log_id", logId);

        try (PreparedStatement ps = conn.prepareStatement(QUERY_LINES_SQL)) {
            ps.setLong(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int lineNum = rs.getInt("LINE_NUMBER");
                    String text = rs.getString("LINE_TEXT");
                    if (text == null) text = "";

                    if (lineNum == 1) {
                        entry.put("summary", text);
                    } else if (lineNum == 2) {
                        entry.put("message", stripPrefix(text, "MESSAGE|"));
                    } else if (lineNum == 3) {
                        entry.put("session_id", stripPrefix(text, "SESSION_ID|"));
                    } else if (lineNum == 10) {
                        String sp = stripPrefix(text, "SYSTEM_PROMPT|");
                        entry.put("system_prompt", sp);
                        entry.put("system_prompt_length", sp.length());
                    } else if (lineNum == 20) {
                        entry.put("formula_type", stripPrefix(text, "FORMULA_TYPE|"));
                    } else if (lineNum == 30) {
                        entry.put("reference_formula", stripPrefix(text, "REFERENCE_FORMULA|"));
                    } else if (lineNum == 40) {
                        entry.put("additional_rules", stripPrefix(text, "ADDITIONAL_RULES|"));
                    } else if (lineNum == 50) {
                        entry.put("editor_code", stripPrefix(text, "EDITOR_CODE|"));
                    } else if (lineNum == 90) {
                        entry.put("token_breakdown", stripPrefix(text, "TOKEN_BREAKDOWN|"));
                    } else if (lineNum == 100) {
                        entry.put("response", stripPrefix(text, "RESPONSE|"));
                    }
                }
            }
        }

        return entry;
    }

    private static void safeInsertLine(PreparedStatement ps, long logId,
                                      int lineNumber, String text) {
        try {
            insertLine(ps, logId, lineNumber, text);
        } catch (SQLException e) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(LlmDebugDBLog.class,
                        "[LlmDebugDBLog] Failed to insert line " + lineNumber
                                + " textLen=" + (text == null ? 0 : text.length())
                                + ": " + e.getMessage(),
                        AppsLogger.WARNING);
            }
        }
    }

    private static void insertLine(PreparedStatement ps, long logId,
                                   int lineNumber, String text) throws SQLException {
        ps.clearParameters();
        ps.setLong(1, logId);
        ps.setInt(2, lineNumber);
        // Sanitize: replace non-ASCII chars that may cause Oracle encoding issues
        String sanitized = truncate(text, MAX_LINE_LENGTH);
        if (sanitized != null) {
            sanitized = sanitized.replace('\u2014', '-')   // em dash
                                 .replace('\u2013', '-')   // en dash
                                 .replace('\u2018', '\'')  // left single quote
                                 .replace('\u2019', '\'')  // right single quote
                                 .replace('\u201C', '"')   // left double quote
                                 .replace('\u201D', '"');   // right double quote
        }
        ps.setString(3, sanitized);
        ps.executeUpdate();
    }

    private static String getLineText(Connection conn, long logId,
                                      int lineNumber) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT LINE_TEXT FROM PAY_ACTION_LOG_LINES "
                + "WHERE ACTION_LOG_ID = ? AND LINE_NUMBER = ?")) {
            ps.setLong(1, logId);
            ps.setInt(2, lineNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("LINE_TEXT") : "";
            }
        }
    }

    private static String buildTokenBreakdown(PromptContext context) {
        return "system_prompt:" + context.systemPromptOrEmpty().length()
                + "/" + (context.systemPromptOrEmpty().length() / 4)
                + ",message:" + context.messageOrEmpty().length()
                + "/" + (context.messageOrEmpty().length() / 4)
                + ",formula_type:" + context.formulaTypeOrEmpty().length()
                + "/" + (context.formulaTypeOrEmpty().length() / 4)
                + ",reference_formula:" + context.referenceFormulaOrEmpty().length()
                + "/" + (context.referenceFormulaOrEmpty().length() / 4)
                + ",editor_code:" + context.editorCodeOrEmpty().length()
                + "/" + (context.editorCodeOrEmpty().length() / 4)
                + ",additional_rules:" + context.additionalRulesOrEmpty().length()
                + "/" + (context.additionalRulesOrEmpty().length() / 4);
    }

    private static int totalChars(PromptContext context) {
        return context.systemPromptOrEmpty().length()
                + context.messageOrEmpty().length()
                + context.formulaTypeOrEmpty().length()
                + context.referenceFormulaOrEmpty().length()
                + context.editorCodeOrEmpty().length()
                + context.additionalRulesOrEmpty().length();
    }

    private static final int TRUNCATE_LIMIT = 3900;

    /**
     * Truncate text to 3900 chars. If truncated, append "..." so the
     * reader knows the content was cut. The full content length is still
     * recorded in the TOKEN_BREAKDOWN line for accurate token estimates.
     */
    private static String truncateWithEllipsis(String s) {
        if (s == null || s.isBlank()) return "";
        if (s.length() <= TRUNCATE_LIMIT) return s;
        return s.substring(0, TRUNCATE_LIMIT) + "...";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private static String stripPrefix(String text, String prefix) {
        if (text != null && text.startsWith(prefix)) {
            return text.substring(prefix.length());
        }
        return text != null ? text : "";
    }
}
