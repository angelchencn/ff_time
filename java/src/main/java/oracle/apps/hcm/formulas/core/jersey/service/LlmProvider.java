package oracle.apps.hcm.formulas.core.jersey.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Interface for LLM backends. Two implementations:
 * - OpenAiProvider: GPT-5.4 via OpenAI API (standalone)
 * - FusionAiProvider: Fusion Completions API (inside Oracle Fusion)
 */
public interface LlmProvider {

    /**
     * Stream a chat completion. Each token is passed to tokenCallback.
     */
    void streamChat(List<Map<String, String>> messages, int maxTokens,
                    Consumer<String> tokenCallback);

    /**
     * Non-streaming chat completion. Returns the full response text.
     *
     * <p>Chat-grade semantics — providers with a single model satisfy this
     * trivially; providers with multiple models (e.g. OpenAI) must use
     * their full-power model here, not a cheaper/faster one.
     * Code-autocomplete callers that need a faster path should use
     * {@link #autocomplete} instead.</p>
     */
    String complete(List<Map<String, String>> messages, int maxTokens);

    /**
     * Short, low-latency completion intended for editor inline-completion
     * (Monaco autocomplete) and similar tab-tab-tab use cases. Providers
     * that have a smaller / faster model should override this; providers
     * with only one model can rely on the default which delegates to
     * {@link #complete}.
     *
     * <p>Distinguishing this from {@link #complete} fixes a real bug we
     * had earlier: chat-sync calls were going through {@code complete()}
     * but {@code OpenAiProvider} hard-coded the cheaper completion model
     * there, so multi-turn chats silently downgraded to {@code gpt-5.4-mini}.
     * Now {@code complete()} is the chat path and {@code autocomplete()}
     * is the only place where the smaller model is allowed.</p>
     */
    default String autocomplete(List<Map<String, String>> messages, int maxTokens) {
        return complete(messages, maxTokens);
    }

    /**
     * Check if this provider is available (API key set, service reachable, etc.)
     */
    boolean isAvailable();

    /**
     * Provider name for logging.
     */
    String name();

    // ───────────────────────────────────────────────────────────────────────
    // Structured prompt context (Plan B) — lets providers with named-property
    // templates (FusionAiProvider → Spectra) receive each prompt section as
    // its own property instead of a flattened blob. Providers that don't
    // support structured input (OpenAiProvider) inherit the default impls
    // which flatten the context back into a system + user message pair.
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Non-streaming completion with structured prompt context. Default impl
     * flattens the context to a 2-message array (system + user) and delegates
     * to {@link #complete}; providers that can consume structured named
     * properties (e.g. FusionAiProvider sending to a Spectra promptCode
     * template) should override and use the fields directly.
     */
    default String completeWithContext(PromptContext context, int maxTokens) {
        return complete(flattenContextToMessages(context), maxTokens);
    }

    /**
     * Streaming counterpart of {@link #completeWithContext}. Default impl
     * flattens and delegates to {@link #streamChat}.
     */
    default void streamChatWithContext(PromptContext context, int maxTokens,
                                       Consumer<String> tokenCallback) {
        streamChat(flattenContextToMessages(context), maxTokens, tokenCallback);
    }

    /**
     * Flattens a {@link PromptContext} into a 2-entry messages list suitable
     * for OpenAI-style chat providers. Everything except {@code userPrompt}
     * is folded into the system message so the LLM gets the same content it
     * would have received under the old single-blob prompt construction.
     *
     * <p>Order inside the system message:
     * <ol>
     *   <li>systemPrompt (the core instructions)</li>
     *   <li>formulaType one-liner (if present)</li>
     *   <li>referenceFormula section (if present)</li>
     *   <li>editorCode section (if present)</li>
     *   <li>additionalRules section (if present)</li>
     *   <li>chatHistory section (if present)</li>
     * </ol>
     * Empty / null fields are skipped entirely — no empty section headers.
     */
    static List<Map<String, String>> flattenContextToMessages(PromptContext ctx) {
        StringBuilder sys = new StringBuilder();

        appendIfPresent(sys, null, ctx.systemPrompt());
        appendIfPresent(sys, "Formula type: ", ctx.formulaType());
        appendIfPresent(sys, "## Reference Formula\n\n", ctx.referenceFormula());
        appendIfPresent(sys, "## Current Editor Code\n\n", ctx.editorCode());
        appendIfPresent(sys, "## Additional Rules\n\n", ctx.additionalRules());
        appendIfPresent(sys, "## Chat History\n\n", ctx.chatHistory());

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", sys.toString().trim());
        messages.add(systemMsg);

        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", ctx.userPromptOrEmpty());
        messages.add(userMsg);

        return messages;
    }

    private static void appendIfPresent(StringBuilder sb, String header, String value) {
        if (value == null || value.isBlank()) return;
        if (sb.length() > 0) sb.append("\n\n");
        if (header != null) sb.append(header);
        sb.append(value);
    }
}
