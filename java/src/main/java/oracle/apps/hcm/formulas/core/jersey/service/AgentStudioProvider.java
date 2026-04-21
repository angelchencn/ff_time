package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import oracle.apps.fnd.applcore.log.AppsLogger;

/**
 * AI Agent Studio provider.
 *
 * Routes LLM calls through the Fusion AI Agent Studio workflow engine
 * using the {@code FAIOrchestratorAgentClientV2} SDK. The provider submits
 * an async job via {@code invokeAgentAsyncAsUser} and polls for completion
 * via {@code getAgentRequestStatusAsUser}.
 *
 * <p>The Agent Studio workflow (identified by {@code WORKFLOW_CODE}) is
 * configured in Agent Studio with LLM nodes, variables, and prompt
 * templates. This provider passes the PromptContext fields as workflow
 * parameters so the agent can use them in its LLM nodes.
 *
 * <p>Like {@link FusionAiProvider}, this class loads the SDK via
 * reflection so it compiles without the SDK jar on the classpath.
 */
public class AgentStudioProvider implements LlmProvider {

    private static final String DEFAULT_WORKFLOW_CODE = "HCM_FF_GENERATOR";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Poll interval in milliseconds. */
    private static final int POLL_INTERVAL_MS = 2000;

    /** Maximum number of poll attempts (2s * 150 = 5 minutes max). */
    private static final int MAX_POLL_ATTEMPTS = 150;

    // SDK class names — loaded via reflection.
    private static final String AGENT_CLIENT_CLASS =
            "oracle.apps.hcm.fai.genAiSdk.agent.v2.FAIOrchestratorAgentClientV2";
    private static final String AGENT_REQUEST_CLASS =
            "oracle.apps.hcm.fai.genAiSdk.agent.v2.pojo.AgentRequestV2";

    // Cached reflection handles.
    private static volatile java.lang.reflect.Constructor<?> cachedClientConstructor;
    private static volatile java.lang.reflect.Method cachedInvokeAsync;
    private static volatile java.lang.reflect.Method cachedGetStatus;
    private static volatile java.lang.reflect.Constructor<?> cachedRequestConstructor;

    /**
     * Maps our internal session key → Agent Studio conversationId.
     * First turn creates a new conversation (empty conversationId);
     * subsequent turns reuse the Agent Studio conversationId so the
     * agent retains Conversation-scoped variables and chat history.
     *
     * <p>Key is derived from chatHistory: empty = first turn.
     * The conversationId is extracted from the Agent Studio response
     * after the first successful call.
     */
    private static final ConcurrentHashMap<String, String> conversationIds =
            new ConcurrentHashMap<>();

    /**
     * Stores the conversationId from the most recent completed request
     * on the current thread, so the caller can retrieve it via
     * {@link #getLastConversationId()} after {@code completeWithContext} returns.
     */
    private static final ThreadLocal<String> lastConversationId = new ThreadLocal<>();

    @Override
    public String getLastConversationId() {
        return lastConversationId.get();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String name() {
        return "AI Agent Studio (/orchestrator/agent/v2)";
    }

    // ── LlmProvider interface (flat-message methods) ───────────────────────

    @Override
    public String complete(List<Map<String, String>> messages, int maxTokens) {
        return completeWithContext(toPromptContext(messages), maxTokens);
    }

    @Override
    public void streamChat(List<Map<String, String>> messages, int maxTokens,
                           Consumer<String> tokenCallback) {
        tokenCallback.accept(complete(messages, maxTokens));
    }

    // ── Async job submission ──────────────────────────────────────────────

    @Override
    public String submitAsync(PromptContext context) {
        String workflowCode = DEFAULT_WORKFLOW_CODE;
        String chatHistory = context.chatHistoryOrEmpty();
        boolean isFirstTurn = chatHistory.isBlank();
        String sessionKey = deriveSessionKey(context);
        String conversationId = isFirstTurn ? "" : conversationIds.getOrDefault(sessionKey, "");

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "[AgentStudio] submitAsync"
                            + " workflowCode=" + workflowCode
                            + " isFirstTurn=" + isFirstTurn,
                    AppsLogger.INFO);
        }

        try {
            Object agentRequest = buildAgentRequest(context, isFirstTurn, conversationId);
            String response = invokeAsync(workflowCode, agentRequest);
            return response;
        } catch (Throwable t) {
            Throwable cause = unwrap(t);
            AppsLogger.write(this, cause, AppsLogger.SEVERE);
            return "{\"error\":\"" + errorMessage(t).replace("\"", "'") + "\"}";
        }
    }

    @Override
    public String getJobStatus(String jobId) {
        String workflowCode = DEFAULT_WORKFLOW_CODE;
        try {
            ensureClientReflection();
            Object client = cachedClientConstructor.newInstance();
            String response = (String) cachedGetStatus.invoke(client, workflowCode, jobId);

            // Store conversationId if job is complete
            if (response != null) {
                JsonNode node = MAPPER.readTree(response);
                String status = node.path("status").asText("");
                if ("COMPLETE".equalsIgnoreCase(status)) {
                    // Use a generic session key since we don't have context here
                    storeConversationId(workflowCode + "|" + jobId, node);
                }
            }

            return response;
        } catch (Throwable t) {
            Throwable cause = unwrap(t);
            AppsLogger.write(this, cause, AppsLogger.SEVERE);
            return "{\"status\":\"ERROR\",\"error\":\"" + errorMessage(t).replace("\"", "'") + "\"}";
        }
    }

    // ── Structured prompt context (primary path) ───────────────────────────

    @Override
    public String completeWithContext(PromptContext context, int maxTokens) {
        String workflowCode = DEFAULT_WORKFLOW_CODE;

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "[AgentStudio] completeWithContext"
                            + " workflowCode=" + workflowCode
                            + " sysLen=" + context.systemPromptOrEmpty().length()
                            + " userLen=" + context.messageOrEmpty().length()
                            + " formulaType=" + context.formulaTypeOrEmpty()
                            + " refLen=" + context.referenceFormulaOrEmpty().length()
                            + " editorLen=" + context.editorCodeOrEmpty().length()
                            + " rulesLen=" + context.additionalRulesOrEmpty().length()
                            + " historyLen=" + context.chatHistoryOrEmpty().length(),
                    AppsLogger.INFO);
        }
        // Detect first vs subsequent turn.
        // In Agent Studio mode, the chatHistory field may carry a previous
        // conversationId (passed through from the frontend's session_id).
        // If empty, this is the first turn — Agent Studio creates a new conversation.
        String chatHistory = context.chatHistoryOrEmpty();
        boolean isFirstTurn = chatHistory.isBlank();

        // Look up existing Agent Studio conversationId.
        // Priority: chatHistory (if it looks like a conversationId, not formatted history),
        // then the conversationIds map, then empty (new conversation).
        String sessionKey = deriveSessionKey(context);
        String conversationId;
        if (!isFirstTurn && !chatHistory.contains("\n") && !chatHistory.startsWith("User:")) {
            // chatHistory looks like a raw conversationId, not formatted history
            conversationId = chatHistory;
        } else if (!isFirstTurn) {
            conversationId = conversationIds.getOrDefault(sessionKey, "");
        } else {
            conversationId = "";
        }

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "[AgentStudio] isFirstTurn=" + isFirstTurn
                            + " conversationId=" + (conversationId.isBlank() ? "(new)" : conversationId),
                    AppsLogger.INFO);
        }

        try {
            // 1. Build the AgentRequestV2 via reflection
            Object agentRequest = buildAgentRequest(context, isFirstTurn, conversationId);

            // 2. Submit async job
            String submitResponse = invokeAsync(workflowCode, agentRequest);
            if (submitResponse == null || submitResponse.isBlank()) {
                return "Error: Agent Studio invokeAsync returned empty response.";
            }

            // Extract jobId from submit response
            JsonNode submitNode = MAPPER.readTree(submitResponse);
            String jobId = extractJobId(submitNode);
            if (jobId == null || jobId.isBlank()) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "[AgentStudio] No jobId in submit response: " + submitResponse,
                            AppsLogger.WARNING);
                }
                // If response already contains output (synchronous completion),
                // try to extract it directly
                String directOutput = extractOutput(submitNode);
                if (directOutput != null && !directOutput.isBlank()) {
                    // Store conversationId from response
                    storeConversationId(sessionKey, submitNode);
                    return directOutput;
                }
                return "Error: Agent Studio returned no jobId. Response: " + submitResponse;
            }

            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "[AgentStudio] Job submitted: jobId=" + jobId,
                        AppsLogger.INFO);
            }

            // 3. Poll for completion
            String result = pollForCompletion(workflowCode, jobId, sessionKey);
            return result;

        } catch (Throwable t) {
            Throwable cause = unwrap(t);
            AppsLogger.write(this, cause, AppsLogger.SEVERE);
            return "Error: Agent Studio call failed: " + errorMessage(t);
        }
    }

    @Override
    public void streamChatWithContext(PromptContext context, int maxTokens,
                                      Consumer<String> tokenCallback) {
        String workflowCode = DEFAULT_WORKFLOW_CODE;
        String chatHistory = context.chatHistoryOrEmpty();
        boolean isFirstTurn = chatHistory.isBlank();
        String sessionKey = deriveSessionKey(context);
        String conversationId = isFirstTurn ? "" : conversationIds.getOrDefault(sessionKey, "");

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "[AgentStudio] streamChatWithContext"
                            + " workflowCode=" + workflowCode
                            + " isFirstTurn=" + isFirstTurn,
                    AppsLogger.INFO);
        }

        try {
            Object agentRequest = buildAgentRequest(context, isFirstTurn, conversationId);
            // Enable token-by-token streaming on the request
            invokeSetter(agentRequest.getClass(), agentRequest, "setStreamOutput", boolean.class, true);

            // Use invokeStream via the parent FAIOrchestratorClient's post()
            // to get an SSE stream. The endpoint is the same as invokeAsync
            // but with /invokeStream path.
            String streamResponse = invokeStream(workflowCode, agentRequest);

            // The invokeStream returns SSE frames. Each frame has an accumulated
            // "output" field. We diff to extract incremental tokens.
            parseStreamResponse(streamResponse, sessionKey, tokenCallback);

        } catch (Throwable t) {
            Throwable cause = unwrap(t);
            AppsLogger.write(this, cause, AppsLogger.SEVERE);
            tokenCallback.accept("Error: Agent Studio stream failed: " + errorMessage(t));
        }
    }

    // ── Agent Studio SDK helpers (reflection-based) ────────────────────────

    /**
     * Build an {@code AgentRequestV2} instance via reflection.
     *
     * <p>First turn ({@code isFirstTurn=true}): passes ALL variables as
     * parameters — both Conversation-scoped (SystemPrompt, FormulaType,
     * ReferenceFormula, AdditionalRules) and User Question-scoped
     * (EditorCode). Agent Studio stores Conversation-scoped variables
     * for the lifetime of the conversation.
     *
     * <p>Subsequent turns ({@code isFirstTurn=false}): passes ONLY
     * User Question-scoped variables (EditorCode). Conversation-scoped
     * variables are already stored by Agent Studio. The {@code message}
     * field carries the new user request, and {@code conversationId}
     * resumes the existing conversation.
     */
    private Object buildAgentRequest(PromptContext context,
                                     boolean isFirstTurn,
                                     String conversationId) throws Exception {
        if (cachedRequestConstructor == null) {
            Class<?> reqClass = Class.forName(AGENT_REQUEST_CLASS);
            cachedRequestConstructor = reqClass.getDeclaredConstructor();
        }
        Object request = cachedRequestConstructor.newInstance();
        Class<?> reqClass = request.getClass();

        // message = user's natural language request
        invokeSetter(reqClass, request, "setMessage", String.class, context.messageOrEmpty());

        // conversational = true (always multi-turn capable)
        invokeSetter(reqClass, request, "setConversational", boolean.class, true);

        // conversationId — empty on first turn, reused on subsequent turns
        if (conversationId != null && !conversationId.isBlank()) {
            invokeSetter(reqClass, request, "setConversationId", String.class, conversationId);
        }

        // invocationMode = ADMIN (includes node execution details in response)
        invokeSetter(reqClass, request, "setInvocationMode", String.class, "ADMIN");

        // status = DRAFT (use draft version of the workflow)
        invokeSetter(reqClass, request, "setStatus", String.class, "PUBLISHED");

        // Build parameters based on turn
        Map<String, Object> params = new HashMap<>();

        if (isFirstTurn) {
            // First turn: pass ALL variables (Conversation + User Question scope)
            putParam(params, "SystemPrompt",     context.systemPromptOrEmpty());
            putParam(params, "FormulaType",      context.formulaTypeOrEmpty());
            putParam(params, "ReferenceFormula",  context.referenceFormulaOrEmpty());
            putParam(params, "AdditionalRules",   context.additionalRulesOrEmpty());
            putParam(params, "EditorCode",        context.editorCodeOrEmpty());
            // LLM model selector (e.g. "GPT5MINI", "GPT41MINI")
            String llm = context.promptCodeOrNull();
            if (llm != null && !llm.isBlank()) {
                putParam(params, "Llm", llm);
            }
        } else {
            // Subsequent turns: only User Question scope variables
            putParam(params, "EditorCode",        context.editorCodeOrEmpty());
        }

        try {
            java.lang.reflect.Method setParams = reqClass.getMethod("setParameters", Map.class);
            setParams.invoke(request, params);
        } catch (NoSuchMethodException e) {
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "[AgentStudio] setParameters(Map) not found, wrapping as additionalContext",
                        AppsLogger.FINER);
            }
            try {
                String paramJson = MAPPER.writeValueAsString(params);
                Map<String, Object> wrappedParams = new HashMap<>();
                wrappedParams.put("additionalContext", paramJson);
                java.lang.reflect.Method setParams2 = reqClass.getMethod("setParameters", Map.class);
                setParams2.invoke(request, wrappedParams);
            } catch (Exception e2) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "[AgentStudio] Could not set parameters: " + e2.getMessage(),
                            AppsLogger.WARNING);
                }
            }
        }

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "[AgentStudio] buildAgentRequest: isFirstTurn=" + isFirstTurn
                            + " paramKeys=" + params.keySet()
                            + " conversationId=" + (conversationId == null ? "" : conversationId),
                    AppsLogger.INFO);
        }

        return request;
    }

    private static void putParam(Map<String, Object> params, String key, String value) {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("value", value);
        params.put(key, wrapper);
    }

    private static void invokeSetter(Class<?> clazz, Object obj, String methodName,
                                     Class<?> paramType, Object value) {
        try {
            java.lang.reflect.Method m = clazz.getMethod(methodName, paramType);
            m.invoke(obj, value);
        } catch (Exception e) {
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(AgentStudioProvider.class,
                        "[AgentStudio] " + methodName + " failed: " + e.getMessage(),
                        AppsLogger.FINER);
            }
        }
    }

    /**
     * Submit an async job via {@code invokeAgentAsyncAsUser}.
     */
    private String invokeAsync(String workflowCode, Object agentRequest) throws Exception {
        ensureClientReflection();
        Object client = cachedClientConstructor.newInstance();
        return (String) cachedInvokeAsync.invoke(client, workflowCode, agentRequest);
    }

    /**
     * Call the Agent Studio invokeStream endpoint. Reuses the SDK's
     * parent {@code FAIOrchestratorClient.post()} method which handles
     * OAuth token acquisition and host resolution. The invokeStream
     * endpoint returns SSE frames concatenated into a single response
     * string when called via the SDK's synchronous post().
     *
     * <p>The stream path is {@code /orchestrator/agent/v2/{code}/invokeStream}.
     */
    private static final String AGENT_INVOKE_STREAM_ENDPOINT = "/orchestrator/agent/v2/%s/invokeStream";

    private static volatile java.lang.reflect.Method cachedPostMethod;

    private String invokeStream(String workflowCode, Object agentRequest) throws Exception {
        ensureClientReflection();
        // Resolve the post() method from FAIOrchestratorClient (parent class)
        if (cachedPostMethod == null) {
            Class<?> clientClass = Class.forName(AGENT_CLIENT_CLASS);
            // post(String path, Object body, Map headers, Class responseType, boolean asUser)
            cachedPostMethod = clientClass.getMethod("post",
                    String.class, Object.class, java.util.Map.class, Class.class, boolean.class);
        }
        Object client = cachedClientConstructor.newInstance();
        String path = String.format(AGENT_INVOKE_STREAM_ENDPOINT, workflowCode);

        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "[AgentStudio] invokeStream: path=" + path, AppsLogger.FINER);
        }

        return (String) cachedPostMethod.invoke(client, path, agentRequest,
                new HashMap<>(), String.class, true);
    }

    /**
     * Parse the SSE response from invokeStream. The response contains
     * one or more {@code data: {...}} frames. Each frame's {@code output}
     * field is the <b>accumulated</b> text so far (not incremental).
     * We diff consecutive outputs to extract new tokens and forward them
     * via the callback.
     *
     * <p>From the Agent Studio docs: "this field shows collected token
     * till that time so ui does not need to append it just can replace."
     */
    private void parseStreamResponse(String sseResponse, String sessionKey,
                                      Consumer<String> tokenCallback) {
        if (sseResponse == null || sseResponse.isBlank()) {
            tokenCallback.accept("Error: Agent Studio invokeStream returned empty.");
            return;
        }

        String lastOutput = "";
        String[] lines = sseResponse.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) continue;

            String dataStr = trimmed.substring(5).trim();
            if (dataStr.isEmpty() || "[DONE]".equals(dataStr)) continue;

            try {
                JsonNode node = MAPPER.readTree(dataStr);

                // Check for errors
                String status = node.path("status").asText("");
                if ("ERROR".equalsIgnoreCase(status)) {
                    String error = node.path("error").asText("Unknown error");
                    tokenCallback.accept("Error: " + error);
                    return;
                }

                // Extract accumulated output and diff
                String currentOutput = node.path("output").asText("");
                if (currentOutput.length() > lastOutput.length()) {
                    String newTokens = currentOutput.substring(lastOutput.length());
                    tokenCallback.accept(newTokens);
                    lastOutput = currentOutput;
                }

                // Store conversationId when complete
                if ("COMPLETE".equalsIgnoreCase(status)) {
                    storeConversationId(sessionKey, node);
                }

            } catch (Exception e) {
                if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                    AppsLogger.write(this,
                            "[AgentStudio] Failed to parse SSE frame: " + trimmed,
                            AppsLogger.FINER);
                }
            }
        }
    }

    /**
     * Poll for job completion via {@code getAgentRequestStatusAsUser}.
     * Returns the {@code output} field from the completed response.
     * Stores the Agent Studio conversationId for subsequent turns.
     */
    private String pollForCompletion(String workflowCode, String jobId,
                                     String sessionKey) throws Exception {
        ensureClientReflection();

        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            Object client = cachedClientConstructor.newInstance();
            String statusResponse = (String) cachedGetStatus.invoke(client, workflowCode, jobId);

            if (statusResponse == null || statusResponse.isBlank()) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "[AgentStudio] Poll attempt " + attempt + " returned empty",
                            AppsLogger.WARNING);
                }
                Thread.sleep(POLL_INTERVAL_MS);
                continue;
            }

            JsonNode statusNode = MAPPER.readTree(statusResponse);
            String status = statusNode.path("status").asText("");

            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "[AgentStudio] Poll attempt " + attempt
                                + " status=" + status
                                + " jobId=" + jobId,
                        AppsLogger.FINER);
            }

            if ("COMPLETE".equalsIgnoreCase(status)) {
                // Store conversationId for subsequent turns
                storeConversationId(sessionKey, statusNode);

                String output = extractOutput(statusNode);
                if (output != null && !output.isBlank()) {
                    if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                        AppsLogger.write(this,
                                "[AgentStudio] Job completed: jobId=" + jobId
                                        + " outputLen=" + output.length(),
                                AppsLogger.INFO);
                    }
                    return output;
                }
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "[AgentStudio] Job COMPLETE but output is empty: " + statusResponse,
                            AppsLogger.WARNING);
                }
                return "Error: Agent Studio job completed but returned empty output.";
            }

            if ("ERROR".equalsIgnoreCase(status)) {
                String error = statusNode.path("error").asText("Unknown error");
                if (AppsLogger.isEnabled(AppsLogger.SEVERE)) {
                    AppsLogger.write(this,
                            "[AgentStudio] Job failed: jobId=" + jobId + " error=" + error,
                            AppsLogger.SEVERE);
                }
                return "Error: Agent Studio job failed: " + error;
            }

            // RUNNING or WAITING — keep polling
            Thread.sleep(POLL_INTERVAL_MS);
        }

        if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
            AppsLogger.write(this,
                    "[AgentStudio] Polling timed out after " + MAX_POLL_ATTEMPTS
                            + " attempts for jobId=" + jobId,
                    AppsLogger.WARNING);
        }
        return "Error: Agent Studio job timed out after "
                + (MAX_POLL_ATTEMPTS * POLL_INTERVAL_MS / 1000) + " seconds.";
    }

    /**
     * Derive a stable session key from the PromptContext so we can map
     * our internal conversation to an Agent Studio conversationId.
     * Uses formulaType + first 64 chars of systemPrompt hash — stable
     * across turns within the same conversation.
     */
    private static String deriveSessionKey(PromptContext context) {
        String ft = context.formulaTypeOrEmpty();
        String sp = context.systemPromptOrEmpty();
        int spHash = sp.length() > 64 ? sp.substring(0, 64).hashCode() : sp.hashCode();
        return ft + "|" + spHash;
    }

    /**
     * Extract and store the Agent Studio conversationId from a response
     * so subsequent turns can reuse the same conversation.
     */
    private void storeConversationId(String sessionKey, JsonNode responseNode) {
        String convId = responseNode.path("conversationId").asText(null);
        if (convId != null && !convId.isBlank()) {
            conversationIds.put(sessionKey, convId);
            lastConversationId.set(convId);
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "[AgentStudio] Stored conversationId=" + convId
                                + " for sessionKey=" + sessionKey,
                        AppsLogger.INFO);
            }
        }
    }

    private void ensureClientReflection() throws Exception {
        if (cachedClientConstructor == null) {
            Class<?> clientClass = Class.forName(AGENT_CLIENT_CLASS);
            cachedClientConstructor = clientClass.getDeclaredConstructor();

            // AgentRequestV2 is the parameter type for invokeAgentAsyncAsUser
            Class<?> reqClass = Class.forName(AGENT_REQUEST_CLASS);
            cachedInvokeAsync = clientClass.getMethod(
                    "invokeAgentAsyncAsUser", String.class, reqClass);
            cachedGetStatus = clientClass.getMethod(
                    "getAgentRequestStatusAsUser", String.class, String.class);
        }
    }

    private static String extractJobId(JsonNode node) {
        if (node.has("jobId")) {
            return node.get("jobId").asText(null);
        }
        if (node.has("data") && node.get("data").has("jobId")) {
            return node.get("data").get("jobId").asText(null);
        }
        return null;
    }

    private static String extractOutput(JsonNode node) {
        if (node.has("output") && !node.get("output").isNull()) {
            return node.get("output").asText(null);
        }
        return null;
    }

    /**
     * Converts a legacy flat messages list into a minimal PromptContext.
     */
    private static PromptContext toPromptContext(List<Map<String, String>> messages) {
        String sys = "";
        String user = "";
        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");
            if ("system".equals(role)) {
                sys = content;
            } else if ("user".equals(role)) {
                user = content;
            }
        }
        return PromptContext.of(sys, user, "");
    }

    /**
     * Unwrap {@link java.lang.reflect.InvocationTargetException} to get the
     * real cause. Reflection wraps the actual exception — {@code getMessage()}
     * on the wrapper is null but the cause has the real error message.
     */
    private static Throwable unwrap(Throwable t) {
        if (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    private static String errorMessage(Throwable t) {
        Throwable cause = unwrap(t);
        String msg = cause.getMessage();
        return msg != null ? msg : cause.toString();
    }
}
