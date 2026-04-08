package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads formula types from JSON registry files.
 * Priority: classpath (data/) → filesystem fallback.
 */
public class FormulaTypesService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<Map<String, Object>> cachedTypes;
    private Map<String, Map<String, Object>> cachedTemplates;

    public List<Map<String, Object>> listAll() {
        if (cachedTypes == null) {
            cachedTypes = new ArrayList<>(loadJsonList("oracle/apps/hcm/formulas/core/jersey/data/formula_types_registry.json"));
            cachedTypes.add(0, CustomFormulaService.getInstance().getTypeInfo());
        }
        return cachedTypes;
    }

    public Map<String, Object> getTemplate(String typeName) {
        if (cachedTemplates == null) {
            cachedTemplates = loadTemplates();
            cachedTemplates.put("Custom", Map.of(
                    "type_name", "Custom",
                    "display_name", "Custom Formulas",
                    "naming_pattern", "CUSTOM_<BUSINESS_DESCRIPTION>  (e.g. CUSTOM_OVERTIME_PAY_CALC)",
                    "skeleton", CUSTOM_SKELETON,
                    "example_snippet", ""
            ));
        }
        return cachedTemplates.get(typeName);
    }

    private static final String CUSTOM_SKELETON = """
/******************************************************************************
 *
 * Formula Name : {formula_name}
 *
 * Formula Type : Custom
 *
 * Description  : {description}
 *
 * Change History
 * --------------
 *
 *  Who             Ver    Date          Description
 * ---------------  ----   -----------   --------------------------------
 * Payroll Admin    1.0    {date_today}  Created.
 *
 ******************************************************************************/

/* DEFAULT and INPUTS declarations here */

l_log = PAY_INTERNAL_LOG_WRITE('{formula_name} - Enter')

/* ---- Business logic here ---- */

l_log = PAY_INTERNAL_LOG_WRITE('{formula_name} - Exit')

RETURN /* return variables here */

/* End Formula Text */
""";

    private Map<String, Map<String, Object>> loadTemplates() {
        var raw = loadJsonMap("oracle/apps/hcm/formulas/core/jersey/data/formula_type_templates.json");
        var result = new HashMap<String, Map<String, Object>>();
        for (var entry : raw.entrySet()) {
            var tmpl = new HashMap<>(entry.getValue());
            tmpl.putIfAbsent("type_name", entry.getKey());
            result.put(entry.getKey(), tmpl);
        }
        return result;
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

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadJsonMap(String classpathResource) {
        // Classpath
        InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource);
        if (is != null) {
            try { return MAPPER.readValue(is, new TypeReference<>() {}); }
            catch (Exception ignored) {}
        }
        // Filesystem fallback
        for (String base : List.of("src/main/resources/", "../java/src/main/resources/", "../backend/data/", "backend/data/")) {
            String path = classpathResource.startsWith("data/") ? base + classpathResource.substring(5) : base + classpathResource;
            if (base.contains("resources")) path = base + classpathResource;
            var file = new File(path);
            if (file.exists()) {
                try { return MAPPER.readValue(file, new TypeReference<>() {}); }
                catch (Exception ignored) {}
            }
        }
        return Map.of();
    }
}
