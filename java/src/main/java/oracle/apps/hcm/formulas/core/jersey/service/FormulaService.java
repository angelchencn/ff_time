package oracle.apps.hcm.formulas.core.jersey.service;

import oracle.apps.fnd.applcore.log.AppsLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory formula CRUD for local development.
 * Replace with JDBC queries to Fusion DB for production.
 */
public class FormulaService {

    private static final FormulaService INSTANCE = new FormulaService();

    public static FormulaService getInstance() { return INSTANCE; }

    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    public List<Map<String, Object>> listAll() {
        return new ArrayList<>(store.values());
    }

    public Map<String, Object> findById(String id) {
        return store.get(id);
    }

    public Map<String, Object> create(Map<String, Object> request) {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "create: id=" + id + " name=" + request.get("name"),
                    AppsLogger.INFO);
        }

        var formula = new LinkedHashMap<String, Object>();
        formula.put("id", id);
        formula.put("name", request.getOrDefault("name", ""));
        formula.put("description", request.getOrDefault("description", ""));
        formula.put("formula_type", request.getOrDefault("formula_type", ""));
        formula.put("use_case", request.getOrDefault("use_case", ""));
        formula.put("code", request.getOrDefault("code", ""));
        formula.put("version", 1);
        formula.put("status", request.getOrDefault("status", "DRAFT"));
        formula.put("user_id", request.get("user_id"));
        formula.put("created_at", now);
        formula.put("updated_at", now);

        store.put(id, formula);
        return formula;
    }

    public Map<String, Object> update(String id, Map<String, Object> updates) {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "update: id=" + id + " keys=" + updates.keySet(),
                    AppsLogger.INFO);
        }
        var existing = store.get(id);
        if (existing == null) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "update: id=" + id + " not in store", AppsLogger.WARNING);
            }
            return null;
        }

        var updated = new LinkedHashMap<>(existing);
        for (var entry : updates.entrySet()) {
            if (entry.getValue() != null && !"id".equals(entry.getKey())) {
                updated.put(entry.getKey(), entry.getValue());
            }
        }
        updated.put("updated_at", Instant.now().toString());
        updated.put("version", ((Number) updated.getOrDefault("version", 1)).intValue() + 1);

        store.put(id, updated);
        return updated;
    }
}
