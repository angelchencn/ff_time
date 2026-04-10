package oracle.apps.hcm.formulas.core.jersey.service;

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
}
