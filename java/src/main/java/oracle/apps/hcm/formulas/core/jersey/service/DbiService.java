package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Database Items service.
 * Loads from classpath (data/dbi_registry/) first, filesystem fallback.
 */
public class DbiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private List<Map<String, Object>> cachedDbis;

    private List<Map<String, Object>> loadDbis() {
        if (cachedDbis != null) return cachedDbis;

        // Try classpath first
        for (String resource : List.of(
                "oracle/apps/hcm/formulas/core/jersey/data/dbi_registry/all_formula_dbis.json",
                "oracle/apps/hcm/formulas/core/jersey/data/dbi_registry/time_labor_dbis.json")) {
            InputStream is = getClass().getClassLoader().getResourceAsStream(resource);
            if (is != null) {
                try {
                    cachedDbis = MAPPER.readValue(is, new TypeReference<>() {});
                    return cachedDbis;
                } catch (Exception ignored) {}
            }
        }

        // Filesystem fallback
        var paths = List.of(
                "src/main/resources/data/dbi_registry/all_formula_dbis.json",
                "src/main/resources/data/dbi_registry/time_labor_dbis.json",
                "../backend/data/dbi_registry/all_formula_dbis.json",
                "../backend/data/dbi_registry/time_labor_dbis.json",
                "backend/data/dbi_registry/all_formula_dbis.json",
                "backend/data/dbi_registry/time_labor_dbis.json"
        );

        for (String path : paths) {
            var file = new File(path);
            if (file.exists()) {
                try {
                    cachedDbis = MAPPER.readValue(file, new TypeReference<>() {});
                    return cachedDbis;
                } catch (Exception ignored) {}
            }
        }

        cachedDbis = List.of();
        return cachedDbis;
    }

    public Map<String, Object> getDbis(String module, String search, String dataType,
                                        int limit, int offset) {
        var records = new ArrayList<>(loadDbis());

        if (module != null && !module.isBlank()) {
            records.removeIf(r -> !module.equalsIgnoreCase((String) r.get("module")));
        }
        if (dataType != null && !dataType.isBlank()) {
            records.removeIf(r -> !dataType.equalsIgnoreCase((String) r.get("data_type")));
        }
        if (search != null && !search.isBlank()) {
            String term = search.toLowerCase();
            records.removeIf(r -> {
                String name = String.valueOf(r.getOrDefault("name", "")).toLowerCase();
                String desc = String.valueOf(r.getOrDefault("description", "")).toLowerCase();
                return !name.contains(term) && !desc.contains(term);
            });
        }

        int total = records.size();
        int end = Math.min(offset + limit, total);
        var page = offset < total ? records.subList(offset, end) : List.<Map<String, Object>>of();

        return Map.of("total", total, "items", page);
    }

    public List<Map<String, Object>> getModules() {
        var records = loadDbis();
        var counts = records.stream()
                .collect(Collectors.groupingBy(
                        r -> String.valueOf(r.getOrDefault("module", "OTHER")),
                        Collectors.counting()
                ));

        var result = new ArrayList<Map<String, Object>>();
        counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> result.add(Map.of("module", e.getKey(), "count", e.getValue())));
        return result;
    }
}
