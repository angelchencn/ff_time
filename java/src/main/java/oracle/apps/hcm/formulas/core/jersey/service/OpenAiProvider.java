package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import oracle.apps.fnd.applcore.log.AppsLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI GPT API provider — SSE streaming + synchronous completion.
 */
public class OpenAiProvider implements LlmProvider {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String CHAT_MODEL = "gpt-5.4";
    private static final String COMPLETION_MODEL = "gpt-5.4-mini";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = buildHttpClient();

    private final String apiKey;

    public OpenAiProvider() {
        this.apiKey = System.getenv("OPENAI_API_KEY");
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "OpenAiProvider initialized: apiKey "
                            + (apiKey == null || apiKey.isBlank() ? "MISSING" : "present")
                            + " chatModel=" + CHAT_MODEL,
                    AppsLogger.INFO);
        }
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String name() {
        return "OpenAI GPT-5.4";
    }

    @Override
    public void streamChat(List<Map<String, String>> messages, int maxTokens,
                           Consumer<String> tokenCallback) {
        String sysPrompt = messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> m.get("content"))
                .findFirst().orElse("");
        LlmDebugLog.getInstance().record(CHAT_MODEL, maxTokens, sysPrompt, messages, "stream", "");

        try {
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "[OpenAI] Calling " + API_URL + " model=" + CHAT_MODEL
                                + " maxTokens=" + maxTokens + " messages=" + messages.size(),
                        AppsLogger.INFO);
            }

            String body = MAPPER.writeValueAsString(Map.of(
                    "model", CHAT_MODEL,
                    "max_completion_tokens", maxTokens,
                    "stream", true,
                    "messages", messages
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<java.io.InputStream> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "[OpenAI] Response status: " + response.statusCode(),
                        AppsLogger.INFO);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body()), 1)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) continue;

                    try {
                        JsonNode event = MAPPER.readTree(data);

                        // Check for a top-level "error" object — OpenAI
                        // returns {"error":{"message":"...","type":"..."}}
                        // when the request itself failed. Do NOT match on
                        // line.contains("error") because the LLM can
                        // legitimately generate the word "error" as content
                        // (e.g. RAISE_ERROR in a formula).
                        if (event.has("error") && event.get("error").isObject()) {
                            String msg = event.at("/error/message").asText(data);
                            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                                AppsLogger.write(this,
                                        "[OpenAI] API Error: " + msg, AppsLogger.WARNING);
                            }
                            tokenCallback.accept("API Error: " + msg);
                            return;
                        }

                        String content = event.at("/choices/0/delta/content").asText("");
                        if (!content.isEmpty()) {
                            tokenCallback.accept(content);
                        }
                    } catch (Exception e3) {
                        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                            AppsLogger.write(this,
                                    "[OpenAI] skipping malformed SSE line: " + data,
                                    AppsLogger.FINER);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // SEVERE inside catch — full exception path failure (network,
            // HTTP, OOM). Worth a stack trace so ops can diagnose.
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            tokenCallback.accept("Error: " + e.getMessage());
        }
    }

    /**
     * Non-streaming chat completion. Used by {@code AiService.chatOnce()}
     * for the {@code /chat/sync} endpoint.
     *
     * <p>This <em>must</em> use {@link #CHAT_MODEL}, not the smaller
     * {@code COMPLETION_MODEL}. Earlier versions of this class hard-coded
     * the cheaper completion model here, which silently downgraded every
     * sync chat call to {@code gpt-5.4-mini} — wrong model for multi-turn
     * conversations. Code-autocomplete callers (Monaco inline suggestions)
     * route through {@link #autocomplete} instead, which is the only place
     * the smaller model is allowed.</p>
     */
    @Override
    public String complete(List<Map<String, String>> messages, int maxTokens) {
        return callChatCompletion(messages, maxTokens, CHAT_MODEL, "complete");
    }

    /**
     * Short, low-latency completion for editor inline suggestions. Uses the
     * cheaper / faster {@link #COMPLETION_MODEL} (gpt-5.4-mini) which is the
     * appropriate trade-off for tab-tab-tab autocomplete where latency
     * matters more than depth-of-reasoning.
     */
    @Override
    public String autocomplete(List<Map<String, String>> messages, int maxTokens) {
        return callChatCompletion(messages, maxTokens, COMPLETION_MODEL, "autocomplete");
    }

    /**
     * Shared HTTP path for both {@link #complete} and {@link #autocomplete}.
     * The only thing that varies is the model name and the debug-log
     * endpoint label.
     */
    private String callChatCompletion(List<Map<String, String>> messages, int maxTokens,
                                      String model, String endpointLabel) {
        String sysPrompt = messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> m.get("content"))
                .findFirst().orElse("");
        LlmDebugLog.getInstance().record(model, maxTokens, sysPrompt, messages,
                endpointLabel, "");

        try {
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "[OpenAI] " + endpointLabel + ": model=" + model
                                + " maxTokens=" + maxTokens
                                + " messages=" + messages.size(),
                        AppsLogger.INFO);
            }
            String body = MAPPER.writeValueAsString(Map.of(
                    "model", model,
                    "max_completion_tokens", maxTokens,
                    "messages", messages
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "[OpenAI] " + endpointLabel + " status=" + response.statusCode(),
                        AppsLogger.FINER);
            }
            JsonNode json = MAPPER.readTree(response.body());
            return json.at("/choices/0/message/content").asText("").trim();
        } catch (Exception e) {
            // SEVERE inside catch — chat/autocomplete failure is rare in
            // practice, and when it does happen we want to know why.
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return "";
        }
    }

    private static HttpClient buildHttpClient() {
        String proxyHost = System.getenv("https_proxy");
        if (proxyHost == null) proxyHost = System.getenv("http_proxy");
        if (proxyHost == null) proxyHost = System.getenv("HTTPS_PROXY");
        if (proxyHost == null) proxyHost = System.getenv("HTTP_PROXY");

        if (proxyHost != null && !proxyHost.isBlank()) {
            try {
                URI uri = URI.create(proxyHost);
                java.net.ProxySelector proxy = java.net.ProxySelector.of(
                        new java.net.InetSocketAddress(uri.getHost(), uri.getPort()));
                if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                    AppsLogger.write(OpenAiProvider.class,
                            "HTTP client configured with proxy " + proxyHost,
                            AppsLogger.INFO);
                }
                return HttpClient.newBuilder().proxy(proxy).build();
            } catch (Exception e) {
                // SEVERE inside catch — bad proxy config is a startup
                // problem worth raising loudly; falling back to no proxy
                // can mask the misconfiguration.
                AppsLogger.write(OpenAiProvider.class, e, AppsLogger.SEVERE);
            }
        }
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(OpenAiProvider.class,
                    "HTTP client configured without proxy", AppsLogger.INFO);
        }
        return HttpClient.newHttpClient();
    }
}
