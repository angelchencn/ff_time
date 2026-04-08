package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves hardcoded Custom formula samples.
 * When user selects Custom type and picks a sample, the formula code
 * is returned directly — no LLM call needed.
 */
public class CustomFormulaService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CustomFormulaService INSTANCE = new CustomFormulaService();

    public static CustomFormulaService getInstance() { return INSTANCE; }

    private Map<String, Object> customType;

    @SuppressWarnings("unchecked")
    private Map<String, Object> load() {
        if (customType != null) return customType;

        // Try classpath
        InputStream is = getClass().getClassLoader().getResourceAsStream("oracle/apps/hcm/formulas/core/jersey/data/custom_formulas.json");
        if (is != null) {
            try {
                customType = MAPPER.readValue(is, new TypeReference<>() {});
                return customType;
            } catch (Exception ignored) {}
        }

        // Try file paths
        for (String path : List.of(
                "src/main/resources/custom_formulas.json",
                "../java/src/main/resources/custom_formulas.json")) {
            var file = new File(path);
            if (file.exists()) {
                try {
                    customType = MAPPER.readValue(file, new TypeReference<>() {});
                    return customType;
                } catch (Exception ignored) {}
            }
        }

        customType = Map.of();
        return customType;
    }

    /** Check if this is the Custom type */
    public boolean isCustomType(String formulaType) {
        var data = load();
        String typeName = (String) data.getOrDefault("type_name", "Custom");
        return typeName.equalsIgnoreCase(formulaType) || "Custom".equalsIgnoreCase(formulaType);
    }

    /** Get all sample names/descriptions for the formula-types listing */
    @SuppressWarnings("unchecked")
    public List<String> getSamplePrompts() {
        var data = load();
        var samples = (List<Map<String, Object>>) data.getOrDefault("samples", List.of());
        return samples.stream()
                .map(s -> (String) s.getOrDefault("description", s.getOrDefault("name", "")))
                .toList();
    }

    /** Get formula type info for the /api/formula-types listing */
    public Map<String, Object> getTypeInfo() {
        var data = load();
        return Map.of(
                "type_name", data.getOrDefault("type_name", "Custom"),
                "display_name", data.getOrDefault("display_name", "Custom Formulas"),
                "formula_count", getSamplePrompts().size(),
                "sample_prompts", getSamplePrompts()
        );
    }

    /** Find a sample by name or description (exact or partial match) */
    @SuppressWarnings("unchecked")
    public Map<String, Object> findSample(String query) {
        var data = load();
        var samples = (List<Map<String, Object>>) data.getOrDefault("samples", List.of());

        // Exact match on name or description
        for (var s : samples) {
            String name = (String) s.getOrDefault("name", "");
            String desc = (String) s.getOrDefault("description", "");
            if (name.equalsIgnoreCase(query) || desc.equalsIgnoreCase(query)) {
                return s;
            }
        }

        // Partial match
        String lower = query.toLowerCase();
        for (var s : samples) {
            String name = ((String) s.getOrDefault("name", "")).toLowerCase();
            String desc = ((String) s.getOrDefault("description", "")).toLowerCase();
            if (name.contains(lower) || desc.contains(lower) || lower.contains(name)) {
                return s;
            }
        }

        return null;
    }

    /** Get all samples */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAllSamples() {
        var data = load();
        return (List<Map<String, Object>>) data.getOrDefault("samples", List.of());
    }

    /** Replace all samples and persist to JSON file */
    public void replaceSamples(List<Map<String, Object>> newSamples) {
        var data = new LinkedHashMap<String, Object>();
        data.put("type_name", "Custom");
        data.put("display_name", "Custom Formulas");
        data.put("description", "Pre-built formula templates for common use cases");
        data.put("samples", newSamples);
        customType = data;

        // Persist to file
        for (String path : List.of(
                "src/main/resources/custom_formulas.json",
                "../java/src/main/resources/custom_formulas.json")) {
            var file = new File(path);
            if (file.getParentFile() != null && file.getParentFile().exists()) {
                try {
                    MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, data);
                    return;
                } catch (Exception ignored) {}
            }
        }
    }
}
