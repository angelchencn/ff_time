package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import oracle.apps.fnd.applcore.log.AppsLogger;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG (Retrieval-Augmented Generation) service.
 *
 * Strategy:
 *   1. Try ChromaDB HTTP API (if running at localhost:8001)
 *   2. Fallback: keyword search on rag_formulas.json (501 real formulas exported from ChromaDB)
 */
public class RagService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String CHROMA_BASE = "http://localhost:8001";
    private static final String COLLECTION_NAME = "fast_formulas";

    private Boolean chromaAvailable;
    private String collectionId;

    // Fallback: in-memory index of real formula code
    private List<Map<String, Object>> formulaIndex;

    /**
     * Query for formulas similar to the given text.
     *
     * @return list of maps with "code" and "metadata" keys
     */
    public List<Map<String, Object>> query(String queryText, int topK) {
        if (queryText == null || queryText.isBlank()) return List.of();
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "query: textLen=" + queryText.length() + " topK=" + topK,
                    AppsLogger.FINER);
        }

        // Try ChromaDB first
        if (isChromaAvailable()) {
            var results = queryChroma(queryText, topK);
            if (!results.isEmpty()) {
                if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                    AppsLogger.write(this,
                            "query: ChromaDB returned " + results.size() + " results",
                            AppsLogger.FINER);
                }
                return results;
            }
        }

        // Fallback: keyword search on exported formulas
        var results = keywordSearch(queryText, topK);
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "query: keyword fallback returned " + results.size() + " results",
                    AppsLogger.FINER);
        }
        return results;
    }

    // ── ChromaDB HTTP API ───────────────────────────────────────────────────

    private boolean isChromaAvailable() {
        if (chromaAvailable != null) return chromaAvailable;
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_BASE + "/api/v1/heartbeat"))
                    .GET().timeout(java.time.Duration.ofSeconds(2)).build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                chromaAvailable = true;
                if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                    AppsLogger.write(this,
                            "ChromaDB heartbeat OK at " + CHROMA_BASE, AppsLogger.INFO);
                }
                resolveCollectionId();
                return true;
            }
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "ChromaDB heartbeat returned " + resp.statusCode()
                                + " — falling back to keyword search",
                        AppsLogger.WARNING);
            }
        } catch (Exception e) {
            // INFO not WARNING — Chroma being absent is the common case for
            // local dev / Fusion deployment, so don't spam the log; just
            // record once at info level so ops can see what mode we're in.
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "ChromaDB heartbeat failed (" + e.getMessage()
                                + ") — keyword search will be used",
                        AppsLogger.INFO);
            }
        }
        chromaAvailable = false;
        return false;
    }

    private void resolveCollectionId() {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_BASE + "/api/v1/collections"))
                    .GET().build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            var collections = MAPPER.readTree(resp.body());
            for (JsonNode c : collections) {
                if (COLLECTION_NAME.equals(c.path("name").asText())) {
                    collectionId = c.path("id").asText();
                    if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                        AppsLogger.write(this,
                                "Resolved Chroma collection '" + COLLECTION_NAME
                                        + "' → " + collectionId,
                                AppsLogger.INFO);
                    }
                    return;
                }
            }
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "Chroma collection '" + COLLECTION_NAME + "' not found",
                        AppsLogger.WARNING);
            }
        } catch (Exception e) {
            // SEVERE inside catch — Chroma is up but we couldn't list its
            // collections, which is unexpected and worth a stack trace.
            AppsLogger.write(this, e, AppsLogger.SEVERE);
        }
    }

    private List<Map<String, Object>> queryChroma(String queryText, int topK) {
        if (collectionId == null) return List.of();
        try {
            var body = MAPPER.writeValueAsString(Map.of(
                    "query_texts", List.of(queryText),
                    "n_results", topK,
                    "include", List.of("metadatas", "distances")
            ));
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(CHROMA_BASE + "/api/v1/collections/" + collectionId + "/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            var json = MAPPER.readTree(resp.body());

            var ids = json.path("ids").path(0);
            var metadatas = json.path("metadatas").path(0);
            var distances = json.path("distances").path(0);

            var results = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < ids.size(); i++) {
                double distance = distances.path(i).asDouble(2.0);
                double similarity = 1.0 - (distance / 2.0);
                if (similarity < 0.6) continue;

                var meta = MAPPER.convertValue(metadatas.path(i), new TypeReference<Map<String, Object>>() {});
                String code = (String) meta.getOrDefault("code", "");
                var cleanMeta = new HashMap<>(meta);
                cleanMeta.remove("code");

                results.add(Map.of(
                        "code", code,
                        "metadata", cleanMeta,
                        "similarity", similarity
                ));
            }
            return results;
        } catch (Exception e) {
            // SEVERE inside catch — query failure on a known-good collection
            // means the embedding model died or the network blew up; both
            // worth a stack trace.
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return List.of();
        }
    }

    // ── Keyword search on exported formulas ─────────────────────────────────

    private void loadFormulaIndex() {
        if (formulaIndex != null) return;

        // Try classpath first (rag_formulas.json in resources)
        InputStream is = getClass().getClassLoader().getResourceAsStream("oracle/apps/hcm/formulas/core/jersey/data/rag_formulas.json");
        if (is != null) {
            try {
                formulaIndex = MAPPER.readValue(is, new TypeReference<>() {});
                if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                    AppsLogger.write(this,
                            "Loaded rag_formulas.json from classpath ("
                                    + formulaIndex.size() + " formulas)",
                            AppsLogger.INFO);
                }
                return;
            } catch (Exception e) {
                AppsLogger.write(this, e, AppsLogger.SEVERE);
            }
        }

        // Try file paths
        var paths = List.of(
                "../java/src/main/resources/rag_formulas.json",
                "src/main/resources/rag_formulas.json",
                "../backend/data/rag_formulas.json"
        );
        for (String path : paths) {
            var file = new File(path);
            if (file.exists()) {
                try {
                    formulaIndex = MAPPER.readValue(file, new TypeReference<>() {});
                    if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                        AppsLogger.write(this,
                                "Loaded rag_formulas.json from " + path
                                        + " (" + formulaIndex.size() + " formulas)",
                                AppsLogger.INFO);
                    }
                    return;
                } catch (Exception e) {
                    AppsLogger.write(this, e, AppsLogger.SEVERE);
                }
            }
        }

        if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
            AppsLogger.write(this,
                    "rag_formulas.json not found on classpath or filesystem; RAG keyword search disabled",
                    AppsLogger.WARNING);
        }
        formulaIndex = List.of();
    }

    private List<Map<String, Object>> keywordSearch(String queryText, int topK) {
        loadFormulaIndex();
        if (formulaIndex.isEmpty()) return List.of();

        String[] queryWords = queryText.toLowerCase().split("\\s+");
        var scored = new ArrayList<Map.Entry<Map<String, Object>, Integer>>();

        for (var entry : formulaIndex) {
            String code = ((String) entry.getOrDefault("code", "")).toLowerCase();
            String useCase = ((String) entry.getOrDefault("use_case", "")).toLowerCase();
            String searchText = useCase + " " + code;

            int score = 0;
            for (String word : queryWords) {
                if (word.length() > 2 && searchText.contains(word)) score++;
            }
            if (score > 0) {
                scored.add(Map.entry(entry, score));
            }
        }

        scored.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        var results = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            var entry = scored.get(i).getKey();
            results.add(Map.of(
                    "code", entry.getOrDefault("code", ""),
                    "metadata", Map.of(
                            "use_case", entry.getOrDefault("use_case", ""),
                            "formula_type", entry.getOrDefault("formula_type", "")
                    )
            ));
        }
        return results;
    }
}
