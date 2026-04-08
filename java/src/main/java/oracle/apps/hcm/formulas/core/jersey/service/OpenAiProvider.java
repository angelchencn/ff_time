package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OpenAI GPT API provider — SSE streaming + synchronous completion.
 */
public class OpenAiProvider implements LlmProvider {

    private static final Logger LOG = Logger.getLogger(OpenAiProvider.class.getName());
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String CHAT_MODEL = "gpt-5.4";
    private static final String COMPLETION_MODEL = "gpt-5.4-mini";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = buildHttpClient();

    private final String apiKey;

    public OpenAiProvider() {
        this.apiKey = System.getenv("OPENAI_API_KEY");
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
            LOG.info("[OpenAI] Calling " + API_URL + " model=" + CHAT_MODEL);

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
            LOG.info("[OpenAI] Response status: " + response.statusCode());

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body()), 1)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("\"error\"")) {
                        LOG.warning("[OpenAI] API Error: " + line);
                        try {
                            JsonNode err = MAPPER.readTree(line);
                            String msg = err.at("/error/message").asText(line);
                            tokenCallback.accept("API Error: " + msg);
                        } catch (Exception e2) {
                            tokenCallback.accept("API Error: " + line);
                        }
                        return;
                    }

                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) continue;

                    try {
                        JsonNode event = MAPPER.readTree(data);
                        String content = event.at("/choices/0/delta/content").asText("");
                        if (!content.isEmpty()) {
                            tokenCallback.accept(content);
                        }
                    } catch (Exception ignored) {
                        // skip malformed SSE lines
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[OpenAI] Exception: " + e.getMessage(), e);
            tokenCallback.accept("Error: " + e.getMessage());
        }
    }

    @Override
    public String complete(List<Map<String, String>> messages, int maxTokens) {
        String sysPrompt = messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> m.get("content"))
                .findFirst().orElse("");
        LlmDebugLog.getInstance().record(COMPLETION_MODEL, maxTokens, sysPrompt, messages,
                "complete", "code completion");

        try {
            String body = MAPPER.writeValueAsString(Map.of(
                    "model", COMPLETION_MODEL,
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
            JsonNode json = MAPPER.readTree(response.body());
            return json.at("/choices/0/message/content").asText("").trim();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[OpenAI] Complete failed: " + e.getMessage(), e);
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
                return HttpClient.newBuilder().proxy(proxy).build();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to configure proxy: " + proxyHost, e);
            }
        }
        return HttpClient.newHttpClient();
    }
}
