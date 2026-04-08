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
