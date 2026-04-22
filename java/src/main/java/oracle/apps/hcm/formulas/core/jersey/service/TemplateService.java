package oracle.apps.hcm.formulas.core.jersey.service;

import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.apps.hcm.formulas.core.jersey.config.DbConfig;

import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC-backed CRUD service for the {@code FF_FORMULA_TEMPLATES} family of tables.
 *
 * <p>The service reads from {@code FF_FORMULA_TEMPLATES_VL} (which joins the base
 * and translation tables on the session language) and writes to the base table
 * {@code FF_FORMULA_TEMPLATES} and {@code FF_FORMULA_TEMPLATES_TL} directly.</p>
 *
 * <p>Formula type filtering: callers pass the formula type <em>name</em> (e.g.
 * {@code "Oracle Payroll"}), not a numeric id. The service joins against
 * {@code FF_FORMULA_TYPES.FORMULA_TYPE_NAME} to resolve the id. The sentinel
 * value {@code "Custom"} maps to {@code FORMULA_TYPE_ID IS NULL} — rows that
 * belong to the built-in Custom Formula category with no FK to a real type.</p>
 */
public class TemplateService {

    /** Sentinel type-name used by the UI for the built-in "Custom Formula" bucket. */
    public static final String CUSTOM_TYPE = "Custom";

    /** Language code for the single TL row we currently write. Multilingual support is future work. */
    private static final String DEFAULT_LANG = "US";

    /** WHO-column default when the request has no authenticated user. */
    private static final String DEFAULT_USER = "FF_TIME_APP";

    /**
     * Returns the list of templates whose formula type name matches {@code formulaTypeName},
     * or all templates whose {@code FORMULA_TYPE_ID} is NULL when the sentinel
     * {@link #CUSTOM_TYPE} is passed. Pass {@code null} to fetch every row.
     *
     * <p>By default only rows with {@code ACTIVE_FLAG='Y'} are returned — that matches
     * the consumer use case (browsing sample templates in the formula editor). The
     * Manage Templates admin UI passes {@code includeInactive=true} so it can see and
     * toggle disabled rows.</p>
     */
    public List<Map<String, Object>> listByFormulaType(String formulaTypeName,
                                                       boolean includeInactive) throws SQLException {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "listByFormulaType: name=" + formulaTypeName
                            + " includeInactive=" + includeInactive,
                    AppsLogger.FINER);
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT v.TEMPLATE_ID, v.FORMULA_TYPE_ID, v.TEMPLATE_CODE, ")
           .append("       v.FORMULA_TEXT, v.ADDITIONAL_PROMPT_TEXT, v.SOURCE_TYPE, ")
           .append("       v.ACTIVE_FLAG, v.SEMANTIC_FLAG, v.SYSTEMPROMPT_FLAG, ")
           .append("       v.USE_SYSTEM_PROMPT_FLAG, v.SORT_ORDER, ")
           .append("       v.NAME, v.DESCRIPTION, v.OBJECT_VERSION_NUMBER, ")
           .append("       ft.FORMULA_TYPE_NAME ")
           .append("  FROM FF_FORMULA_TEMPLATES_VL v ")
           .append("  LEFT JOIN FF_FORMULA_TYPES ft ON v.FORMULA_TYPE_ID = ft.FORMULA_TYPE_ID ")
           .append(" WHERE 1=1 ");

        if (!includeInactive) {
            sql.append("   AND v.ACTIVE_FLAG = 'Y' ");
        }

        boolean filterCustom = CUSTOM_TYPE.equalsIgnoreCase(formulaTypeName);
        boolean filterByName = formulaTypeName != null && !formulaTypeName.isBlank() && !filterCustom;
        if (filterCustom) {
            sql.append("   AND v.FORMULA_TYPE_ID IS NULL ");
        } else if (filterByName) {
            sql.append("   AND ft.FORMULA_TYPE_NAME = ? ");
        }
        sql.append(" ORDER BY v.SORT_ORDER NULLS LAST, v.TEMPLATE_ID");

        try (Connection conn = DbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (filterByName) {
                ps.setString(1, formulaTypeName);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(rowToMap(rs));
                }
                if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                    AppsLogger.write(this,
                            "listByFormulaType returned " + out.size() + " rows",
                            AppsLogger.FINER);
                }
                return out;
            }
        } catch (SQLException e) {
            // SEVERE inside catch — DB query failures bubble up to the REST
            // layer, but we want a stack trace at this layer too because the
            // resource catch only logs the chained exception cause.
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            throw e;
        }
    }

    /** Backward-compatible overload — defaults to active-only. */
    public List<Map<String, Object>> listByFormulaType(String formulaTypeName) throws SQLException {
        return listByFormulaType(formulaTypeName, false);
    }

    /**
     * Returns the distinct formula types that have at least one active,
     * non-system-prompt template row. Used by the frontend Type dropdown
     * so it only shows types the user can actually pick a template from.
     * "Custom" is synthesised for rows with {@code FORMULA_TYPE_ID IS NULL}.
     */
    public List<Map<String, Object>> listDistinctFormulaTypes() throws SQLException {
        String sql =
            "SELECT DISTINCT " +
            "  CASE WHEN v.FORMULA_TYPE_ID IS NULL THEN 'Custom' ELSE ft.FORMULA_TYPE_NAME END AS TYPE_NAME, " +
            "  CASE WHEN v.FORMULA_TYPE_ID IS NULL THEN 'Custom Formula' ELSE ft.FORMULA_TYPE_NAME END AS DISPLAY_NAME " +
            "FROM FF_FORMULA_TEMPLATES_VL v " +
            "LEFT JOIN FF_FORMULA_TYPES ft ON v.FORMULA_TYPE_ID = ft.FORMULA_TYPE_ID " +
            "WHERE v.ACTIVE_FLAG = 'Y' " +
            "  AND (v.SYSTEMPROMPT_FLAG IS NULL OR v.SYSTEMPROMPT_FLAG = 'N') " +
            "ORDER BY TYPE_NAME";

        try (Connection conn = DbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> out = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type_name", rs.getString("TYPE_NAME"));
                m.put("display_name", rs.getString("DISPLAY_NAME"));
                m.put("formula_count", 0);
                m.put("sample_prompts", List.of());
                out.add(m);
            }
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "listDistinctFormulaTypes returned " + out.size() + " types",
                        AppsLogger.FINER);
            }
            return out;
        } catch (SQLException e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            throw e;
        }
    }

    /**
     * Fetch the active system prompt template — the single row with
     * {@code SYSTEMPROMPT_FLAG='Y'} and {@code ACTIVE_FLAG='Y'}. Returns
     * {@code null} when no such row exists or the row is inactive.
     */
    /**
     * Returns all active system prompt templates, ordered by
     * {@code SORT_ORDER}. The caller (AiService) concatenates their
     * {@code FORMULA_TEXT} values to build the final system prompt, so
     * multiple rows can each contribute a section (e.g. base rules,
     * formula-type contracts, anti-hallucination rules) and their
     * display order is controlled by the {@code SORT_ORDER} column in
     * the Manage Templates UI.
     *
     * @return ordered list of template maps, or an empty list when no
     *         active system prompt rows exist.
     */
    public List<Map<String, Object>> findSystemPrompts() throws SQLException {
        String sql =
            "SELECT v.TEMPLATE_ID, v.FORMULA_TYPE_ID, v.TEMPLATE_CODE, " +
            "       v.FORMULA_TEXT, v.ADDITIONAL_PROMPT_TEXT, v.SOURCE_TYPE, " +
            "       v.ACTIVE_FLAG, v.SEMANTIC_FLAG, v.SYSTEMPROMPT_FLAG, " +
            "       v.USE_SYSTEM_PROMPT_FLAG, v.SORT_ORDER, " +
            "       v.NAME, v.DESCRIPTION, v.OBJECT_VERSION_NUMBER, " +
            "       ft.FORMULA_TYPE_NAME " +
            "  FROM FF_FORMULA_TEMPLATES_VL v " +
            "  LEFT JOIN FF_FORMULA_TYPES ft ON v.FORMULA_TYPE_ID = ft.FORMULA_TYPE_ID " +
            " WHERE v.SYSTEMPROMPT_FLAG = 'Y' " +
            "   AND v.ACTIVE_FLAG = 'Y' " +
            " ORDER BY v.SORT_ORDER NULLS LAST, v.TEMPLATE_ID";

        try (Connection conn = DbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rowToMap(rs));
            }
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "findSystemPrompts: " + out.size() + " active row(s) found",
                        AppsLogger.INFO);
            }
            return out;
        } catch (SQLException e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            throw e;
        }
    }

    /** Fetch a single template by id; returns {@code null} when the row does not exist. */
    public Map<String, Object> findById(long templateId) throws SQLException {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this, "findById: " + templateId, AppsLogger.FINER);
        }
        return findByColumn("TEMPLATE_ID = ?", ps -> ps.setLong(1, templateId));
    }

    /**
     * Fetch a single template by its business key — the {@code TEMPLATE_CODE}
     * column value (e.g. {@code ORA_FFT_CUSTOM_OVERTIME_PAY_CALCULATION_001}).
     * This is the lookup the /chat endpoint uses when the frontend passes
     * the picked template's short code instead of the full CLOB body.
     * Returns {@code null} when the row does not exist.
     */
    public Map<String, Object> findByTemplateCode(String templateCode) throws SQLException {
        if (templateCode == null || templateCode.isBlank()) return null;
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this, "findByTemplateCode: " + templateCode, AppsLogger.FINER);
        }
        return findByColumn("TEMPLATE_CODE = ?", ps -> ps.setString(1, templateCode));
    }

    /** Common lookup path — the {@code wherePredicate} must reference {@code v.}-aliased columns. */
    private Map<String, Object> findByColumn(String wherePredicate,
                                              SqlBinder binder) throws SQLException {
        String sql =
            "SELECT v.TEMPLATE_ID, v.FORMULA_TYPE_ID, v.TEMPLATE_CODE, " +
            "       v.FORMULA_TEXT, v.ADDITIONAL_PROMPT_TEXT, v.SOURCE_TYPE, " +
            "       v.ACTIVE_FLAG, v.SEMANTIC_FLAG, v.SYSTEMPROMPT_FLAG, " +
            "       v.USE_SYSTEM_PROMPT_FLAG, v.SORT_ORDER, " +
            "       v.NAME, v.DESCRIPTION, v.OBJECT_VERSION_NUMBER, " +
            "       ft.FORMULA_TYPE_NAME " +
            "  FROM FF_FORMULA_TEMPLATES_VL v " +
            "  LEFT JOIN FF_FORMULA_TYPES ft ON v.FORMULA_TYPE_ID = ft.FORMULA_TYPE_ID " +
            " WHERE v." + wherePredicate;

        try (Connection conn = DbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rowToMap(rs) : null;
            }
        } catch (SQLException e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            throw e;
        }
    }

    /** Tiny functional interface so {@link #findByColumn} can stay typed without lambdas-checked-exception gymnastics. */
    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    /**
     * Insert a new {@code USER_CREATED} template. Writes one base row and one
     * {@code FF_FORMULA_TEMPLATES_TL} row for {@link #DEFAULT_LANG}. The
     * {@code TEMPLATE_ID} is allocated from {@code FUSION.S_ROW_ID_SEQ.NEXTVAL}.
     */
    public Map<String, Object> create(Map<String, Object> req) throws SQLException {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "create: name=" + req.get("name")
                            + " formula_type=" + req.get("formula_type")
                            + " template_code=" + req.get("template_code"),
                    AppsLogger.INFO);
        }
        String name = str(req.get("name"), "Untitled Template");
        String description = str(req.get("description"), "");
        String code = str(req.get("code"), "");
        String rule = str(req.get("rule"), null);
        String templateCode = str(req.get("template_code"),
                generateTemplateCode(name));
        String formulaTypeName = str(req.get("formula_type"), null);
        int sortOrder = asInt(req.get("sort_order"), 0);
        String createdBy = str(req.get("user_id"), DEFAULT_USER);
        String activeFlag = str(req.get("active_flag"), "Y");
        String semanticFlag = str(req.get("semantic_flag"), "N");
        String systempromptFlag = str(req.get("systemprompt_flag"), "N");
        String useSystemPromptFlag = str(req.get("use_system_prompt_flag"), "Y");

        try (Connection conn = DbConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long templateId = nextTemplateId(conn);
                Long formulaTypeId = resolveFormulaTypeId(conn, formulaTypeName);

                insertBase(conn, templateId, formulaTypeId, templateCode, code, rule,
                        sortOrder, createdBy, activeFlag, semanticFlag, systempromptFlag,
                        useSystemPromptFlag);
                insertTl(conn, templateId, name, description, createdBy);

                conn.commit();
                if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                    AppsLogger.write(this,
                            "create committed: template_id=" + templateId,
                            AppsLogger.INFO);
                }
                Map<String, Object> created = findById(templateId);
                if (created == null) {
                    throw new SQLException("Template insert succeeded but row not found, id=" + templateId);
                }
                return created;
            } catch (SQLException e) {
                AppsLogger.write(this, e, AppsLogger.SEVERE);
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Update an existing template. Only the fields present in {@code updates} are
     * written. {@code OBJECT_VERSION_NUMBER} is incremented on both the base and
     * TL rows for optimistic-locking purposes.
     */
    public Map<String, Object> update(long templateId, Map<String, Object> updates) throws SQLException {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "update: template_id=" + templateId + " keys=" + updates.keySet(),
                    AppsLogger.INFO);
        }
        try (Connection conn = DbConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> existing = findById(templateId);
                if (existing == null) {
                    if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                        AppsLogger.write(this,
                                "update: template_id=" + templateId + " not found",
                                AppsLogger.WARNING);
                    }
                    return null;
                }

                String lastUpdatedBy = str(updates.get("user_id"), DEFAULT_USER);
                updateBase(conn, templateId, updates, lastUpdatedBy);
                updateTl(conn, templateId, updates, lastUpdatedBy);

                conn.commit();
                if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                    AppsLogger.write(this,
                            "update committed: template_id=" + templateId,
                            AppsLogger.INFO);
                }
                return findById(templateId);
            } catch (SQLException e) {
                AppsLogger.write(this, e, AppsLogger.SEVERE);
                conn.rollback();
                throw e;
            }
        }
    }

    /** Hard-delete the template and its TL rows. Returns true when a row was removed. */
    public boolean delete(long templateId) throws SQLException {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this, "delete: template_id=" + templateId, AppsLogger.INFO);
        }
        try (Connection conn = DbConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM FF_FORMULA_TEMPLATES_TL WHERE TEMPLATE_ID = ?")) {
                    ps.setLong(1, templateId);
                    ps.executeUpdate();
                }
                int removed;
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM FF_FORMULA_TEMPLATES WHERE TEMPLATE_ID = ?")) {
                    ps.setLong(1, templateId);
                    removed = ps.executeUpdate();
                }
                conn.commit();
                if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                    AppsLogger.write(this,
                            "delete committed: template_id=" + templateId
                                    + " removed=" + removed,
                            AppsLogger.INFO);
                }
                return removed > 0;
            } catch (SQLException e) {
                AppsLogger.write(this, e, AppsLogger.SEVERE);
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Returns ALL formula types from {@code FF_FORMULA_TYPES}, not just
     * those with templates. Used by the Manage Templates detail panel so
     * users can assign any type to a new template.
     */
    public List<Map<String, Object>> listAllFormulaTypes() throws SQLException {
        String sql =
            "SELECT FORMULA_TYPE_ID, FORMULA_TYPE_NAME " +
            "  FROM FF_FORMULA_TYPES " +
            " ORDER BY FORMULA_TYPE_NAME";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> out = new ArrayList<>();
            // Always include Custom at position 0
            out.add(Map.of("type_name", "Custom", "display_name", "Custom Formula"));
            while (rs.next()) {
                String name = rs.getString("FORMULA_TYPE_NAME");
                out.add(Map.of("type_name", name, "display_name", name));
            }
            return out;
        } catch (SQLException e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            throw e;
        }
    }

    // ── Formula lookup (FF_FORMULAS_VL) ─────────────────────────────────────

    /**
     * Search existing formulas in {@code FF_FORMULAS_VL} by formula type
     * and name keyword. Used by the Manage Templates UI to let users pick
     * an existing formula as the basis for a new template's FORMULA_TEXT.
     *
     * @return list of maps with {@code formula_id}, {@code formula_name},
     *         {@code formula_type_name} — no FORMULA_TEXT (large CLOB,
     *         fetched separately via {@link #getFormulaText}).
     */
    public List<Map<String, Object>> searchFormulas(String formulaType, String search,
                                                    int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT f.FORMULA_ID, f.FORMULA_NAME, ft.FORMULA_TYPE_NAME ");
        sql.append("  FROM FF_FORMULAS_VL f ");
        sql.append("  JOIN FF_FORMULA_TYPES ft ON f.FORMULA_TYPE_ID = ft.FORMULA_TYPE_ID ");
        sql.append(" WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        if (formulaType != null && !formulaType.isBlank()) {
            sql.append("   AND ft.FORMULA_TYPE_NAME = ? ");
            params.add(formulaType);
        }
        if (search != null && !search.isBlank()) {
            sql.append("   AND UPPER(f.FORMULA_NAME) LIKE UPPER(?) ");
            params.add("%" + search + "%");
        }
        sql.append(" ORDER BY f.FORMULA_NAME ");
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(limit);

        try (Connection conn = DbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            for (Object p : params) {
                if (p instanceof Integer) {
                    ps.setInt(idx++, (Integer) p);
                } else {
                    ps.setString(idx++, p.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("formula_id", rs.getLong("FORMULA_ID"));
                    m.put("formula_name", rs.getString("FORMULA_NAME"));
                    m.put("formula_type_name", rs.getString("FORMULA_TYPE_NAME"));
                    out.add(m);
                }
                return out;
            }
        } catch (SQLException e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            throw e;
        }
    }

    /**
     * Fetch the full FORMULA_TEXT CLOB for a single formula from
     * {@code FF_FORMULAS_VL}. Called when the user picks a formula in
     * the Manage Templates UI to use as the template body.
     */
    public String getFormulaText(long formulaId) throws SQLException {
        String sql = "SELECT FORMULA_TEXT FROM FF_FORMULAS_VL WHERE FORMULA_ID = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, formulaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readClob(rs, "FORMULA_TEXT");
                }
                return null;
            }
        } catch (SQLException e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            throw e;
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private long nextTemplateId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT FUSION.S_ROW_ID_SEQ.NEXTVAL FROM DUAL");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            throw new SQLException("Failed to allocate TEMPLATE_ID from S_ROW_ID_SEQ");
        }
    }

    /**
     * Resolve a formula type <em>name</em> to its numeric id, or return {@code null}
     * when the caller passed the "Custom" sentinel (or a null / blank name).
     * Unknown names also return {@code null} — the caller treats that as "Custom"
     * so a bad type name degrades into an unfiled template rather than failing
     * the whole insert.
     */
    private Long resolveFormulaTypeId(Connection conn, String formulaTypeName) throws SQLException {
        if (formulaTypeName == null || formulaTypeName.isBlank()
                || CUSTOM_TYPE.equalsIgnoreCase(formulaTypeName)) {
            return null;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT FORMULA_TYPE_ID FROM FF_FORMULA_TYPES WHERE FORMULA_TYPE_NAME = ?")) {
            ps.setString(1, formulaTypeName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    return rs.wasNull() ? null : id;
                }
            }
        }
        if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
            AppsLogger.write(TemplateService.class,
                    "Unknown formula type name '" + formulaTypeName
                            + "' — inserting template with NULL FORMULA_TYPE_ID",
                    AppsLogger.WARNING);
        }
        return null;
    }

    private void insertBase(Connection conn, long templateId, Long formulaTypeId,
                            String templateCode, String formulaText, String additionalPromptText,
                            int sortOrder, String createdBy,
                            String activeFlag, String semanticFlag, String systempromptFlag,
                            String useSystemPromptFlag) throws SQLException {
        String sql =
            "INSERT INTO FF_FORMULA_TEMPLATES ( " +
            "  TEMPLATE_ID, FORMULA_TYPE_ID, TEMPLATE_CODE, FORMULA_TEXT, " +
            "  ADDITIONAL_PROMPT_TEXT, SOURCE_TYPE, ACTIVE_FLAG, SEMANTIC_FLAG, " +
            "  SYSTEMPROMPT_FLAG, USE_SYSTEM_PROMPT_FLAG, SORT_ORDER, OBJECT_VERSION_NUMBER, " +
            "  CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, " +
            "  ENTERPRISE_ID, SEED_DATA_SOURCE, MODULE_ID " +
            ") VALUES ( " +
            "  ?, ?, ?, ?, ?, 'USER_CREATED', ?, ?, ?, ?, ?, 1, " +
            "  ?, SYSTIMESTAMP, ?, SYSTIMESTAMP, " +
            "  nvl(SYS_CONTEXT('FND_VPD_CTX','FND_ENTERPRISE_ID'), 0), " +
            "  'USER_CREATED', 'HXT' " +
            ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, templateId);
            if (formulaTypeId == null) {
                ps.setNull(2, Types.NUMERIC);
            } else {
                ps.setLong(2, formulaTypeId);
            }
            ps.setString(3, templateCode);
            setClob(ps, 4, formulaText);
            setClob(ps, 5, additionalPromptText);
            ps.setString(6, activeFlag);
            ps.setString(7, semanticFlag);
            ps.setString(8, systempromptFlag);
            ps.setString(9, useSystemPromptFlag);
            ps.setInt(10, sortOrder);
            ps.setString(11, createdBy);
            ps.setString(12, createdBy);
            ps.executeUpdate();
        }
    }

    private void insertTl(Connection conn, long templateId, String name, String description,
                          String createdBy) throws SQLException {
        String sql =
            "INSERT INTO FF_FORMULA_TEMPLATES_TL ( " +
            "  TEMPLATE_ID, LANGUAGE, SOURCE_LANG, NAME, DESCRIPTION, OBJECT_VERSION_NUMBER, " +
            "  CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, " +
            "  ENTERPRISE_ID, SEED_DATA_SOURCE " +
            ") VALUES ( " +
            "  ?, ?, ?, ?, ?, 1, " +
            "  ?, SYSTIMESTAMP, ?, SYSTIMESTAMP, " +
            "  nvl(SYS_CONTEXT('FND_VPD_CTX','FND_ENTERPRISE_ID'), 0), " +
            "  'USER_CREATED' " +
            ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, templateId);
            ps.setString(2, DEFAULT_LANG);
            ps.setString(3, DEFAULT_LANG);
            ps.setString(4, name);
            ps.setString(5, description);
            ps.setString(6, createdBy);
            ps.setString(7, createdBy);
            ps.executeUpdate();
        }
    }

    private void updateBase(Connection conn, long templateId,
                            Map<String, Object> updates, String lastUpdatedBy) throws SQLException {
        // Build the SET clause dynamically so we only touch columns the caller provided.
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (updates.containsKey("template_code")) {
            sets.add("TEMPLATE_CODE = ?");
            params.add(str(updates.get("template_code"), ""));
        }
        if (updates.containsKey("code")) {
            sets.add("FORMULA_TEXT = ?");
            params.add(str(updates.get("code"), ""));
        }
        if (updates.containsKey("rule")) {
            sets.add("ADDITIONAL_PROMPT_TEXT = ?");
            params.add(str(updates.get("rule"), null));
        }
        if (updates.containsKey("sort_order")) {
            sets.add("SORT_ORDER = ?");
            params.add(asInt(updates.get("sort_order"), 0));
        }
        if (updates.containsKey("active_flag")) {
            sets.add("ACTIVE_FLAG = ?");
            params.add(str(updates.get("active_flag"), "Y"));
        }
        if (updates.containsKey("semantic_flag")) {
            sets.add("SEMANTIC_FLAG = ?");
            params.add(str(updates.get("semantic_flag"), "Y"));
        }
        if (updates.containsKey("systemprompt_flag")) {
            sets.add("SYSTEMPROMPT_FLAG = ?");
            params.add(str(updates.get("systemprompt_flag"), "N"));
        }
        if (updates.containsKey("use_system_prompt_flag")) {
            sets.add("USE_SYSTEM_PROMPT_FLAG = ?");
            params.add(str(updates.get("use_system_prompt_flag"), "Y"));
        }
        if (updates.containsKey("formula_type")) {
            Long ftId = resolveFormulaTypeId(conn, str(updates.get("formula_type"), null));
            sets.add("FORMULA_TYPE_ID = ?");
            params.add(ftId); // may be null
        }

        if (sets.isEmpty()) {
            return;
        }

        sets.add("LAST_UPDATED_BY = ?");
        params.add(lastUpdatedBy);
        sets.add("LAST_UPDATE_DATE = SYSTIMESTAMP");
        sets.add("OBJECT_VERSION_NUMBER = OBJECT_VERSION_NUMBER + 1");

        String sql = "UPDATE FF_FORMULA_TEMPLATES SET " + String.join(", ", sets)
                + " WHERE TEMPLATE_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : params) {
                if (p == null) {
                    ps.setNull(idx++, Types.VARCHAR);
                } else if (p instanceof Long) {
                    ps.setLong(idx++, (Long) p);
                } else if (p instanceof Integer) {
                    ps.setInt(idx++, (Integer) p);
                } else {
                    // VARCHAR2 and CLOB both bind OK via setString for small-ish values.
                    ps.setString(idx++, p.toString());
                }
            }
            ps.setLong(idx, templateId);
            ps.executeUpdate();
        }
    }

    private void updateTl(Connection conn, long templateId,
                          Map<String, Object> updates, String lastUpdatedBy) throws SQLException {
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (updates.containsKey("name")) {
            sets.add("NAME = ?");
            params.add(str(updates.get("name"), ""));
        }
        if (updates.containsKey("description")) {
            sets.add("DESCRIPTION = ?");
            params.add(str(updates.get("description"), ""));
        }
        if (sets.isEmpty()) {
            return;
        }

        sets.add("LAST_UPDATED_BY = ?");
        params.add(lastUpdatedBy);
        sets.add("LAST_UPDATE_DATE = SYSTIMESTAMP");
        sets.add("OBJECT_VERSION_NUMBER = OBJECT_VERSION_NUMBER + 1");
        sets.add("SOURCE_LANG = ?");
        params.add(DEFAULT_LANG);

        String sql = "UPDATE FF_FORMULA_TEMPLATES_TL SET " + String.join(", ", sets)
                + " WHERE TEMPLATE_ID = ? AND LANGUAGE = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : params) {
                ps.setString(idx++, p == null ? "" : p.toString());
            }
            ps.setLong(idx++, templateId);
            ps.setString(idx, DEFAULT_LANG);
            ps.executeUpdate();
        }
    }

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("template_id", rs.getLong("TEMPLATE_ID"));
        long ftId = rs.getLong("FORMULA_TYPE_ID");
        m.put("formula_type_id", rs.wasNull() ? null : ftId);
        m.put("formula_type_name", rs.getString("FORMULA_TYPE_NAME"));
        m.put("template_code", rs.getString("TEMPLATE_CODE"));
        m.put("code", readClob(rs, "FORMULA_TEXT"));
        m.put("rule", readClob(rs, "ADDITIONAL_PROMPT_TEXT"));
        m.put("source_type", rs.getString("SOURCE_TYPE"));
        m.put("active_flag", rs.getString("ACTIVE_FLAG"));
        m.put("semantic_flag", rs.getString("SEMANTIC_FLAG"));
        m.put("systemprompt_flag", rs.getString("SYSTEMPROMPT_FLAG"));
        String useSpFlag = rs.getString("USE_SYSTEM_PROMPT_FLAG");
        m.put("use_system_prompt_flag", useSpFlag != null ? useSpFlag : "Y");
        int sort = rs.getInt("SORT_ORDER");
        m.put("sort_order", rs.wasNull() ? 0 : sort);
        m.put("name", rs.getString("NAME"));
        m.put("description", rs.getString("DESCRIPTION"));
        m.put("object_version_number", rs.getInt("OBJECT_VERSION_NUMBER"));
        return m;
    }

    private String readClob(ResultSet rs, String column) throws SQLException {
        java.sql.Clob clob = rs.getClob(column);
        if (clob == null) {
            return null;
        }
        try (Reader reader = clob.getCharacterStream()) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (java.io.IOException e) {
            // SEVERE inside catch — CLOB read failure is unusual and worth a
            // full stack trace; the caller still gets a null and degrades
            // gracefully so end users don't see a 500.
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return null;
        }
    }

    private void setClob(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.CLOB);
        } else {
            // For values small enough to fit in a VARCHAR2, Oracle JDBC will convert transparently.
            ps.setString(index, value);
        }
    }

    private static String str(Object v, String fallback) {
        if (v == null) return fallback;
        String s = v.toString();
        return s.isEmpty() ? fallback : s;
    }

    private static int asInt(Object v, int fallback) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v == null) return fallback;
        try { return Integer.parseInt(v.toString()); }
        catch (NumberFormatException e) { return fallback; }
    }

    /** Generate a TEMPLATE_CODE from the human name when the caller doesn't supply one. */
    private static final String TEMPLATE_CODE_PREFIX = "ORA_HCM_FF_";

    private static String generateTemplateCode(String name) {
        String base = (name == null ? "TEMPLATE" : name)
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "TEMPLATE";
        // Prepend the standard prefix if not already present.
        if (!base.startsWith(TEMPLATE_CODE_PREFIX)) {
            base = TEMPLATE_CODE_PREFIX + base;
        }
        // TEMPLATE_CODE is VARCHAR2(150); add a short suffix to reduce collisions.
        String suffix = "_" + (System.currentTimeMillis() % 1_000_000L);
        int max = 150 - suffix.length();
        if (base.length() > max) base = base.substring(0, max);
        return base + suffix;
    }
}
