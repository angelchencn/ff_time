package oracle.apps.hcm.formulas.core.jersey.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records LLM requests for debugging.
 * Stores the last 20 requests in memory.
 */
public class LlmDebugLog {

    private static final LlmDebugLog INSTANCE = new LlmDebugLog();
    private static final int MAX_ENTRIES = 20;

    public static LlmDebugLog getInstance() { return INSTANCE; }

    private final List<Map<String, Object>> entries = new ArrayList<>();

    public synchronized void record(String model, int maxTokens, String systemPrompt,
                                     List<Map<String, String>> messages, String endpoint,
                                     String userMessage) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("endpoint", endpoint);
        entry.put("model", model);
        entry.put("max_completion_tokens", maxTokens);
        entry.put("mode", "flat");
        entry.put("system_prompt", systemPrompt);
        entry.put("system_prompt_length", systemPrompt.length());
        entry.put("messages", messages);
        entry.put("user_message", userMessage != null ? userMessage : "");

        var tokenBreakdown = new ArrayList<Map<String, Object>>();
        int totalChars = 0;

        int sysChars = systemPrompt.length();
        totalChars += sysChars;
        tokenBreakdown.add(Map.of("part", "system_prompt", "chars", sysChars, "est_tokens", sysChars / 4));

        for (var msg : messages) {
            String role = msg.getOrDefault("role", "unknown");
            String content = msg.getOrDefault("content", "");
            if ("system".equals(role)) continue;
            int chars = content.length();
            totalChars += chars;
            tokenBreakdown.add(Map.of("part", role, "chars", chars, "est_tokens", chars / 4));
        }

        entry.put("token_breakdown", tokenBreakdown);
        entry.put("total_chars", totalChars);
        entry.put("estimated_input_tokens", totalChars / 4);

        addEntry(entry);
    }

    /**
     * Structured-context overload (Plan B). Captures each {@link PromptContext}
     * field independently so the debug UI can show per-field size and content.
     * Use this from providers that hand a {@code PromptContext} to a
     * named-property template (e.g. FusionAiProvider → Spectra).
     *
     * <p>The legacy 6-arg {@link #record} overload still works and is used
     * by OpenAI / hybrid paths that only have a flattened messages array.
     */
    public synchronized void record(String model, int maxTokens, String endpoint,
                                     PromptContext context) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("endpoint", endpoint);
        entry.put("model", model);
        entry.put("max_completion_tokens", maxTokens);
        entry.put("mode", "structured");

        // Keep top-level legacy keys populated for UIs that still read them.
        String systemPrompt = context.systemPromptOrEmpty();
        String userPrompt = context.userPromptOrEmpty();
        entry.put("system_prompt", systemPrompt);
        entry.put("system_prompt_length", systemPrompt.length());
        entry.put("user_message", userPrompt);
        entry.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        // Structured fields — one entry per PromptContext placeholder.
        var pc = new LinkedHashMap<String, Object>();
        putFieldWithLength(pc, "system_prompt",     context.systemPromptOrEmpty());
        putFieldWithLength(pc, "user_prompt",       context.userPromptOrEmpty());
        putFieldWithLength(pc, "formula_type",      context.formulaTypeOrEmpty());
        putFieldWithLength(pc, "reference_formula", context.referenceFormulaOrEmpty());
        putFieldWithLength(pc, "editor_code",       context.editorCodeOrEmpty());
        putFieldWithLength(pc, "additional_rules",  context.additionalRulesOrEmpty());
        putFieldWithLength(pc, "chat_history",      context.chatHistoryOrEmpty());
        entry.put("prompt_context", pc);

        // Token breakdown per field so the UI can show which section is
        // eating the budget — critical when investigating 4081-style caps.
        var tokenBreakdown = new ArrayList<Map<String, Object>>();
        int totalChars = 0;
        totalChars += addBreakdown(tokenBreakdown, "system_prompt",     context.systemPromptOrEmpty());
        totalChars += addBreakdown(tokenBreakdown, "user_prompt",       context.userPromptOrEmpty());
        totalChars += addBreakdown(tokenBreakdown, "formula_type",      context.formulaTypeOrEmpty());
        totalChars += addBreakdown(tokenBreakdown, "reference_formula", context.referenceFormulaOrEmpty());
        totalChars += addBreakdown(tokenBreakdown, "editor_code",       context.editorCodeOrEmpty());
        totalChars += addBreakdown(tokenBreakdown, "additional_rules",  context.additionalRulesOrEmpty());
        totalChars += addBreakdown(tokenBreakdown, "chat_history",      context.chatHistoryOrEmpty());
        entry.put("token_breakdown", tokenBreakdown);
        entry.put("total_chars", totalChars);
        entry.put("estimated_input_tokens", totalChars / 4);

        addEntry(entry);
    }

    private static void putFieldWithLength(Map<String, Object> target, String key, String value) {
        target.put(key, value);
        target.put(key + "_length", value.length());
    }

    private static int addBreakdown(List<Map<String, Object>> breakdown, String part, String value) {
        int chars = value.length();
        breakdown.add(Map.of("part", part, "chars", chars, "est_tokens", chars / 4));
        return chars;
    }

    private void addEntry(Map<String, Object> entry) {
        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    public synchronized List<Map<String, Object>> getAll() {
        return List.copyOf(entries);
    }

    public synchronized Map<String, Object> getLatest() {
        return entries.isEmpty() ? Map.of() : entries.get(0);
    }

    public synchronized void clear() {
        entries.clear();
    }
}
