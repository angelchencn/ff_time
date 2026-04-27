package oracle.apps.hcm.formulas.core.jerseyTest;

import oracle.apps.hcm.formulas.core.jersey.config.DbConfig;
import oracle.apps.hcm.formulas.core.jersey.service.ValidatorService;
import org.junit.Assume;
import org.junit.Test;

import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static org.junit.Assert.assertTrue;

/**
 * Regression harness: fetches real Fast Formulas from FF_FORMULAS_B_F and runs
 * ValidatorService against each one. Since every formula in that table has
 * already been accepted by the Oracle engine, our validator must not produce
 * syntax errors against them. Semantic / rule failures are reported but do not
 * fail the test (they may reflect DBI gaps or overly strict rules).
 *
 * Skipped automatically when FF_DB_URL is not set (CI / local without Oracle).
 * Run manually:
 *   FF_DB_URL=jdbc:oracle:thin:@host:port/SID FF_DB_USER=... FF_DB_PASSWORD=... mvn test -Dtest=ValidatorRegressionTest
 */
public class ValidatorRegressionTest {

    private static final int SAMPLE_SIZE = 5000;
    private static final int MAX_SHOWN_PER_CATEGORY = 50;

    private final ValidatorService validator = new ValidatorService();

    @Test
    public void validateRealFormulas() throws Exception {
        String dbUrl = System.getenv("FF_DB_URL");
        Assume.assumeNotNull("FF_DB_URL not set — skipping validator regression test", dbUrl);

        List<FormulaRecord> formulas = fetchFormulas();
        System.out.printf("%n=== Validator Regression: %d formulas ===%n%n", formulas.size());

        List<FailureReport> syntaxFailures   = new ArrayList<>();
        List<FailureReport> semanticFailures = new ArrayList<>();
        List<FailureReport> ruleFailures     = new ArrayList<>();
        int passed = 0;

        for (FormulaRecord f : formulas) {
            Map<String, Object> result = validator.validate(f.text());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> diags =
                    (List<Map<String, Object>>) result.get("diagnostics");

            List<Map<String, Object>> errors = diags.stream()
                    .filter(d -> "error".equals(d.get("severity")))
                    .toList();

            if (errors.isEmpty()) {
                passed++;
            } else {
                for (Map<String, Object> err : errors) {
                    String layer = (String) err.get("layer");
                    var report = new FailureReport(f.id(), f.name(),
                            (String) err.get("message"), f.text());
                    switch (layer) {
                        case "syntax"   -> syntaxFailures.add(report);
                        case "semantic" -> semanticFailures.add(report);
                        default         -> ruleFailures.add(report);
                    }
                }
            }
        }

        printFailures("SYNTAX  (parser bugs — must fix)", syntaxFailures);
        printFailures("SEMANTIC (undeclared var / name — may be DBI gaps)", semanticFailures);
        printFailures("RULE    (missing RETURN etc.)", ruleFailures);

        System.out.printf(
                "%nSummary: total=%d  passed=%d  syntax_errors=%d  "
                + "semantic_errors=%d  rule_errors=%d%n%n",
                formulas.size(), passed,
                syntaxFailures.size(), semanticFailures.size(), ruleFailures.size());

        assertTrue(
                "Parser produced syntax errors on " + syntaxFailures.size()
                + " known-good formula(s) — these are parser bugs. "
                + "See stdout for details.",
                syntaxFailures.isEmpty());
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private List<FormulaRecord> fetchFormulas() throws Exception {
        var list = new ArrayList<FormulaRecord>();
        String sql = "SELECT FORMULA_ID, BASE_FORMULA_NAME, FORMULA_TEXT"
                   + "  FROM FF_FORMULAS_B_F"
                   + " WHERE FORMULA_TEXT IS NOT NULL"
                   + " ORDER BY FORMULA_ID"
                   + " FETCH FIRST ? ROWS ONLY";

        try (Connection conn = DbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, SAMPLE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id     = rs.getLong("FORMULA_ID");
                    String name = rs.getString("BASE_FORMULA_NAME");
                    String text = clobToString(rs, "FORMULA_TEXT");
                    if (text != null && !text.isBlank()) {
                        list.add(new FormulaRecord(id, name, text));
                    }
                }
            }
        }
        return list;
    }

    private String clobToString(ResultSet rs, String col) throws Exception {
        Reader reader = rs.getCharacterStream(col);
        if (reader == null) return null;
        var sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
        reader.close();
        return sb.toString();
    }

    // ── Reporting ─────────────────────────────────────────────────────────────

    private void printFailures(String label, List<FailureReport> failures) {
        if (failures.isEmpty()) return;
        System.out.printf("--- %s: %d failure(s) ---%n", label, failures.size());
        int shown = 0;
        for (FailureReport r : failures) {
            if (shown++ >= MAX_SHOWN_PER_CATEGORY) {
                System.out.printf("  ... and %d more (increase MAX_SHOWN_PER_CATEGORY to see all)%n",
                        failures.size() - MAX_SHOWN_PER_CATEGORY);
                break;
            }
            String snippet = r.text().replaceAll("\\s+", " ");
            int maxLen = 150;
            if (snippet.length() > maxLen) snippet = snippet.substring(0, maxLen - 3) + "...";
            System.out.printf("  [%d] %-50s%n       error  : %s%n       snippet: %s%n%n",
                    r.id(), r.name(), r.error(), snippet);
        }
    }

    // ── Value types ───────────────────────────────────────────────────────────

    private record FormulaRecord(long id, String name, String text) {}
    private record FailureReport(long id, String name, String error, String text) {}
}
