package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import oracle.apps.fnd.applcore.log.AppsLogger;

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
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this, "Loading formula types registry (cold cache)", AppsLogger.INFO);
            }
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
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "Cached " + cachedTypes.size() + " formula types (Custom + " + (cachedTypes.size() - 1) + " from registry)",
                        AppsLogger.INFO);
            }
        }
        return cachedTypes;
    }

    // ── JSON loading: classpath first, then filesystem fallback ──────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadJsonList(String classpathResource) {
        // Classpath
        InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource);
        if (is != null) {
            try {
                List<Map<String, Object>> list = MAPPER.readValue(is, new TypeReference<>() {});
                if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                    AppsLogger.write(this,
                            "Loaded formula types from classpath: " + classpathResource
                                    + " (" + list.size() + " entries)",
                            AppsLogger.FINER);
                }
                return list;
            } catch (Exception e) {
                // SEVERE inside catch — classpath JSON exists but failed to
                // parse; this is a packaging bug worth a stack trace.
                AppsLogger.write(this, e, AppsLogger.SEVERE);
            }
        }
        // Filesystem fallback
        for (String base : List.of("src/main/resources/", "../java/src/main/resources/", "../backend/data/", "backend/data/")) {
            // Strip "data/" prefix for backend paths
            String path = classpathResource.startsWith("data/") ? base + classpathResource.substring(5) : base + classpathResource;
            if (base.contains("resources")) path = base + classpathResource;
            var file = new File(path);
            if (file.exists()) {
                try {
                    List<Map<String, Object>> list = MAPPER.readValue(file, new TypeReference<>() {});
                    if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                        AppsLogger.write(this,
                                "Loaded formula types from filesystem: " + path
                                        + " (" + list.size() + " entries)",
                                AppsLogger.FINER);
                    }
                    return list;
                } catch (Exception e) {
                    AppsLogger.write(this, e, AppsLogger.SEVERE);
                }
            }
        }
        if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
            AppsLogger.write(this,
                    "Formula types registry not found on classpath or filesystem; returning empty list",
                    AppsLogger.WARNING);
        }
        return List.of();
    }
}
