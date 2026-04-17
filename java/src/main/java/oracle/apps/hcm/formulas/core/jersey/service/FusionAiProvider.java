package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import oracle.apps.fnd.applcore.log.AppsLogger;

/**
 * Fusion AI Spectra completions provider.
 *
 * Routes all LLM calls through the FAI Orchestrator's
 * {@code /orchestrator/llm/v1/internal-completions} endpoint via the
 * {@code FAICompletionsClient} SDK (loaded by reflection so the SDK jar
 * is not a compile-time dependency).
 *
 * <p>The SDK's parent class {@code FAIOrchestratorClient} handles OAuth
 * token acquisition, host resolution via TopologyManager, and bearer-token
 * injection — this provider just assembles the promptCode + properties
 * and hands them off.
 *
 * <p>Requires profile option {@code ORA_FAI_SDK_ENABLE_SPECTRA_ROUTE = Y}.
 * When disabled (or SDK jar absent), returns a descriptive error message
 * to the UI instead of silently failing.
 */
public class FusionAiProvider implements LlmProvider {

    // promptCode registered in hr_gen_ai_prompts_seed_b. The template
    // (prompt_tmpl column) declares these placeholders:
    //   {systemPrompt} {userPrompt} {formulaType}
    //   {referenceFormula} {editorCode} {additionalRules} {chatHistory}
    private static final String PROMPT_CODE = "HCM_RAG_DOCUMENTS";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // FAI Spectra SDK class names — loaded via reflection so this class
    // compiles and runs even when the SDK jar isn't on the classpath.
    private static final String FAI_SDK_UTIL_CLASS =
            "oracle.apps.hcm.fai.genAiSdk.util.FaiSdkUtil";
    private static final String FAI_COMPLETIONS_CLIENT_CLASS =
            "oracle.apps.hcm.fai.genAiSdk.FAICompletionsClient";

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String name() {
        return "Fusion AI Spectra (/orchestrator/llm/v1/internal-completions)";
    }

    // ── LlmProvider interface (legacy flat-message methods) ─────────────────
    // AiService no longer calls these directly — it uses
    // completeWithContext / streamChatWithContext instead. These stubs
    // satisfy the interface contract and delegate through PromptContext
    // so they still work if any other caller uses the flat API.

    @Override
    public String complete(List<Map<String, String>> messages, int maxTokens) {
        return completeWithContext(toPromptContext(messages), maxTokens);
    }

    @Override
    public void streamChat(List<Map<String, String>> messages, int maxTokens,
                           Consumer<String> tokenCallback) {
        tokenCallback.accept(complete(messages, maxTokens));
    }

    // ── Structured prompt context (primary path) ────────────────────────────

    @Override
    public String completeWithContext(PromptContext context, int maxTokens) {
        if (!isSpectraRoutingEnabled()) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "[FusionAI] ORA_FAI_SDK_ENABLE_SPECTRA_ROUTE is not enabled",
                        AppsLogger.WARNING);
            }
            return "Error: ORA_FAI_SDK_ENABLE_SPECTRA_ROUTE is not enabled. "
                    + "Please contact your administrator to enable Spectra routing.";
        }

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "[FusionAI] completeWithContext → Spectra "
                            + "promptCode=" + PROMPT_CODE
                            + " sysLen=" + context.systemPromptOrEmpty().length()
                            + " userLen=" + context.userPromptOrEmpty().length()
                            + " formulaType=" + context.formulaTypeOrEmpty()
                            + " refLen=" + context.referenceFormulaOrEmpty().length()
                            + " editorLen=" + context.editorCodeOrEmpty().length()
                            + " rulesLen=" + context.additionalRulesOrEmpty().length()
                            + " historyLen=" + context.chatHistoryOrEmpty().length(),
                    AppsLogger.INFO);
        }
        LlmDebugLog.getInstance().record(
                "fusion-ai-apps", maxTokens, "fusion-spectra", context);

        String spectraText = callSpectraCompletions(
                PROMPT_CODE,
                context.systemPromptOrEmpty(),
                context.userPromptOrEmpty(),
                context.formulaTypeOrEmpty(),
                context.referenceFormulaOrEmpty(),
                context.editorCodeOrEmpty(),
                context.additionalRulesOrEmpty(),
                context.chatHistoryOrEmpty()
        );
        if (spectraText != null && !spectraText.isEmpty()) {
            return spectraText;
        }
        if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
            AppsLogger.write(this,
                    "[FusionAI] Spectra returned null/empty for promptCode=" + PROMPT_CODE,
                    AppsLogger.WARNING);
        }
        return "Error: Fusion AI Spectra returned empty response. "
                + "Check promptCode registration for '" + PROMPT_CODE + "'.";
    }

    @Override
    public void streamChatWithContext(PromptContext context, int maxTokens,
                                      Consumer<String> tokenCallback) {
        tokenCallback.accept(completeWithContext(context, maxTokens));
    }

    // ── Spectra SDK helpers (reflection-based) ──────────────────────────────

    private static boolean isSpectraRoutingEnabled() {
        try {
            Class<?> clazz = Class.forName(FAI_SDK_UTIL_CLASS);
            java.lang.reflect.Method method = clazz.getMethod("isSpectraRoutingEnabled");
            Object result = method.invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(FusionAiProvider.class,
                        "[FusionAI] Spectra routing probe failed: " + t.getMessage(),
                        AppsLogger.FINER);
            }
            return false;
        }
    }

    /**
     * Calls FAICompletionsClient.getCompletions(promptCode, properties)
     * via reflection. Each property maps to one {placeholder} in the
     * Spectra prompt template (hr_gen_ai_prompts_seed_b.prompt_tmpl).
     *
     * Response shape (LLMCompletionResponse):
     *   {"id": "...", "choices": [{"index": 0, "text": "..."}]}
     */
    private String callSpectraCompletions(String promptCode,
                                          String systemPrompt,
                                          String userPrompt,
                                          String formulaType,
                                          String referenceFormula,
                                          String editorCode,
                                          String additionalRules,
                                          String chatHistory) {
        try {
            Class<?> clientClass = Class.forName(FAI_COMPLETIONS_CLIENT_CLASS);
            Object client = clientClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method getCompletions =
                    clientClass.getMethod("getCompletions", String.class, List.class);

            List<Map<String, String>> properties = new ArrayList<>();
            addProperty(properties, "systemPrompt",     systemPrompt);
            addProperty(properties, "userPrompt",       userPrompt);
            addProperty(properties, "formulaType",      formulaType);
            addProperty(properties, "referenceFormula", referenceFormula);
            addProperty(properties, "editorCode",       editorCode);
            addProperty(properties, "additionalRules",  additionalRules);
            addProperty(properties, "chatHistory",      chatHistory);

            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "[FusionAI] Spectra call: promptCode=" + promptCode
                                + " properties=" + properties.size()
                                + " sysLen=" + (systemPrompt == null ? 0 : systemPrompt.length())
                                + " userLen=" + (userPrompt == null ? 0 : userPrompt.length()),
                        AppsLogger.FINER);
            }

            Object response = getCompletions.invoke(client, promptCode, properties);
            if (response == null) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "[FusionAI] FAICompletionsClient.getCompletions returned null "
                                    + "(check ORA_FAI_SDK_ENABLE_SPECTRA_ROUTE profile "
                                    + "and promptCode registration for " + promptCode + ")",
                            AppsLogger.WARNING);
                }
                return null;
            }

            String json = MAPPER.writeValueAsString(response);
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "[FusionAI] Spectra raw response: " + json, AppsLogger.FINER);
            }

            JsonNode root = MAPPER.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "[FusionAI] Spectra response has no choices: " + json,
                            AppsLogger.WARNING);
                }
                return null;
            }

            JsonNode first = choices.get(0);
            JsonNode textNode = first.path("text");
            if (!textNode.isMissingNode() && !textNode.isNull() && !textNode.asText().isEmpty()) {
                return textNode.asText();
            }
            JsonNode contentNode = first.path("message").path("content");
            if (!contentNode.isMissingNode() && !contentNode.isNull() && !contentNode.asText().isEmpty()) {
                return contentNode.asText();
            }
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "[FusionAI] Spectra choices[0] had no extractable text: " + first,
                        AppsLogger.WARNING);
            }
            return null;

        } catch (Throwable t) {
            AppsLogger.write(this, t, AppsLogger.SEVERE);
            return null;
        }
    }

    private static void addProperty(List<Map<String, String>> properties,
                                    String key, String value) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("key", key);
        p.put("value", value == null ? "" : value);
        properties.add(p);
    }

    /**
     * Converts a legacy flat messages list into a minimal PromptContext
     * so the flat {@link #complete} / {@link #streamChat} interface
     * methods can delegate to {@link #completeWithContext}.
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
}
