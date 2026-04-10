package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads formula types from JSON registry files.
 * Priority: classpath (data/) → filesystem fallback.
 */
public class FormulaTypesService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<Map<String, Object>> cachedTypes;

    public List<Map<String, Object>> listAll() {
        if (cachedTypes == null) {
            cachedTypes = new ArrayList<>(loadJsonList("oracle/apps/hcm/formulas/core/jersey/data/formula_types_registry.json"));
            // The built-in "Custom Formula" bucket is injected at position 0
            // so it's always first in the UI dropdowns. Its samples now come
            // from the FF_FORMULA_TEMPLATES DB table (rows with
            // FORMULA_TYPE_ID IS NULL) via TemplateService, so we hardcode
            // an empty sample_prompts list here — the frontend sample picker
            // reads from /api/templates, not from this registry.
            Map<String, Object> custom = new LinkedHashMap<>();
            custom.put("type_name", "Custom");
            custom.put("display_name", "Custom Formula");
            custom.put("formula_count", 0);
            custom.put("sample_prompts", List.of());
            cachedTypes.add(0, custom);
        }
        return cachedTypes;
    }

    // ── JSON loading: classpath first, then filesystem fallback ──────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadJsonList(String classpathResource) {
        // Classpath
        InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource);
        if (is != null) {
            try { return MAPPER.readValue(is, new TypeReference<>() {}); }
            catch (Exception ignored) {}
        }
        // Filesystem fallback
        for (String base : List.of("src/main/resources/", "../java/src/main/resources/", "../backend/data/", "backend/data/")) {
            // Strip "data/" prefix for backend paths
            String path = classpathResource.startsWith("data/") ? base + classpathResource.substring(5) : base + classpathResource;
            if (base.contains("resources")) path = base + classpathResource;
            var file = new File(path);
            if (file.exists()) {
                try { return MAPPER.readValue(file, new TypeReference<>() {}); }
                catch (Exception ignored) {}
            }
        }
        return List.of();
    }
}
