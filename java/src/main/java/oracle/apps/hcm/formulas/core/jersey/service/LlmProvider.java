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
     * Non-streaming completion. Returns the full response text.
     */
    String complete(List<Map<String, String>> messages, int maxTokens);

    /**
     * Check if this provider is available (API key set, service reachable, etc.)
     */
    boolean isAvailable();

    /**
     * Provider name for logging.
     */
    String name();
}
