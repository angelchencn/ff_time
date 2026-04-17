package oracle.apps.hcm.formulas.core.jersey.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.apps.hcm.formulas.core.jersey.util.ConnectionHelper;
import oracle.apps.topologyManager.exception.TMException;

import oracle.topologyManager.client.deployedInfo.EndPointProvider;

import oracle.wsm.metadata.feature.PolicyReferenceFeature;
import oracle.wsm.metadata.feature.PolicySetFeature;
import oracle.wsm.metadata.feature.PropertyFeature;
import oracle.wsm.security.oauth2.OAuth2ClientTokenManager;
import oracle.wsm.security.oauth2.OAuth2TokenContext;
import oracle.wsm.security.oauth2.OAuth2TokenResponse;
import oracle.wsm.security.util.SecurityConstants;

import org.apache.commons.lang3.StringUtils;


/**
 * Oracle Fusion AI Apps Completions provider.
 * Uses ai-common/llm/rest/v2/completion endpoint.
 *
 * Host: EndPointProvider.getExternalEndpointWithProtocolByAppShortName("ORA_AIAPP")
 * Auth: OAuth2 via PolicySetFeature + OAuth2ClientTokenManager
 *
 * In standalone/debug mode (FUSION_AI_DEBUG_URL env var set): uses java.net.http with Basic auth.
 * Inside Fusion: uses TopologyManager + OAuth2 (uncomment the Fusion blocks).
 */
public class FusionAiProvider implements LlmProvider {

    private static final String COMPLETION_PATH = "ai-common/llm/rest/v2/completion";
    // promptCode registered in hr_gen_ai_prompts_seed_b. The template
    // text (prompt_tmpl column) was updated to the HCM Fast Formula
    // generator template with these placeholders:
    //   {systemPrompt} {userPrompt} {formulaType}
    //   {referenceFormula} {editorCode} {additionalRules} {chatHistory}
    private static final String USECASE = "HCM_RAG_DOCUMENTS";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String AIMASTER_EXTERNAL_ENDPOINT_KEY = "ORA_AIAPP";

    // FAI Spectra SDK class names — loaded via reflection so this class
    // compiles and runs even when the FAI SDK jar isn't on the classpath
    // (e.g. local dev). When ORA_FAI_SDK_ENABLE_SPECTRA_ROUTE=Y and the
    // SDK jar is present, complete() routes through Spectra's
    // /orchestrator/llm/v1/internal-completions; otherwise it falls
    // back to the existing ai-common hybrid path.
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
        return "Fusion AI Apps (ai-common/llm/rest/v2/completion)";
    }

    @Override
    public void streamChat(List<Map<String, String>> messages, int maxTokens,
                           Consumer<String> tokenCallback) {
        String response = complete(messages, maxTokens);
        if (response != null && !response.isEmpty()) {
            tokenCallback.accept(response);
        } else {
            tokenCallback.accept("Error: Fusion AI returned empty response.");
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Structured prompt context path (Plan B) — hands each PromptContext
    // field to Spectra as its own named property so the server-side
    // template can render them into dedicated XML tags (<system_instructions>,
    // <formula_type>, <reference_formula>, <current_editor_code>,
    // <additional_rules>, <chat_history>, <user_request>).
    //
    // Falls back to the flattening default (→ complete/streamChat → hybrid
    // ai-common) when Spectra routing is disabled or returns null.
    // ───────────────────────────────────────────────────────────────────────

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
                            + "promptCode=" + USECASE
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
                USECASE,
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
                    "[FusionAI] Spectra returned null/empty for promptCode=" + USECASE,
                    AppsLogger.WARNING);
        }
        return "Error: Fusion AI Spectra returned empty response. "
                + "Check promptCode registration for '" + USECASE + "'.";
    }

    @Override
    public void streamChatWithContext(PromptContext context, int maxTokens,
                                      Consumer<String> tokenCallback) {
        String response = completeWithContext(context, maxTokens);
        tokenCallback.accept(response);
    }

    @Override
    public String complete(List<Map<String, String>> messages, int maxTokens) {
        try {
            // ── Build unified prompt string from messages ────────────────────
            // Both Spectra and Hybrid paths consume a single "prompt" string,
            // so we flatten system/user/assistant messages the same way for
            // both. The promptCode template on the server side is expected
            // to contain a {{prompt}} placeholder that this value binds to.
            StringBuilder prompt = new StringBuilder();
            for (Map<String, String> msg : messages) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("system".equals(role) || "user".equals(role)) {
                    prompt.append(content).append("\n\n");
                } else if ("assistant".equals(role)) {
                    prompt.append("Assistant: ").append(content).append("\n\n");
                }
            }
            String promptText = prompt.toString().trim();

            // Debug log capture (unchanged)
            String sysPrompt = "";
            String userPrompt = "";
            for (Map<String, String> msg : messages) {
                if ("system".equals(msg.get("role"))) {
                    sysPrompt = msg.get("content");
                } else if ("user".equals(msg.get("role"))) {
                    userPrompt = msg.get("content");
                }
            }
            LlmDebugLog.getInstance().record("fusion-ai-apps", maxTokens,
                    sysPrompt, messages, "fusion", userPrompt);

            // ── Route 1: Spectra via FAI Orchestrator SDK ────────────────────
            // Active only when ORA_FAI_SDK_ENABLE_SPECTRA_ROUTE=Y and the
            // HcmFaiGenAiSdk jar is on the classpath. The SDK's parent
            // FAIOrchestratorClient handles OAuth, host resolution, and
            // bearer-token injection, so we just hand it the promptCode
            // and properties list.
            if (isSpectraRoutingEnabled()) {
                if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                    AppsLogger.write(this,
                            "[FusionAI] Spectra routing enabled — calling "
                                    + "/orchestrator/llm/v1/internal-completions "
                                    + "promptCode=" + USECASE
                                    + " maxTokens=" + maxTokens
                                    + " messages=" + messages.size(),
                            AppsLogger.INFO);
                }
                // The template has separate {systemPrompt} and {userPrompt}
                // placeholders, so pass them distinctly. Other placeholders
                // ({formulaType}, {referenceFormula}, {editorCode},
                // {additionalRules}, {chatHistory}) aren't exposed by the
                // current LlmProvider interface yet — pass "" so the
                // template skips those XML tags (template is designed to
                // ignore empty-tag blocks).
                String spectraText = callSpectraCompletions(
                        USECASE,
                        sysPrompt,   // {systemPrompt}
                        userPrompt,  // {userPrompt}
                        "",          // {formulaType}      — TODO: wire from AiService
                        "",          // {referenceFormula} — TODO: wire from AiService
                        "",          // {editorCode}       — TODO: wire from AiService
                        "",          // {additionalRules}  — TODO: wire from AiService
                        ""           // {chatHistory}      — TODO: wire from AiService
                );
                if (spectraText != null && !spectraText.isEmpty()) {
                    return spectraText;
                }
                // Spectra returned null/empty — log and fall through to hybrid
                // so the user still gets a response. If the SDK is genuinely
                // broken, the hybrid path will return its own error message.
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "[FusionAI] Spectra returned null/empty; falling back to hybrid path",
                            AppsLogger.WARNING);
                }
            }

            // ── Route 2: Hybrid (ai-common/llm/rest/v2/completion) ───────────
            // Existing path. Kept as fallback for environments where Spectra
            // routing isn't enabled or the SDK isn't deployed yet.
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("prompt", promptText);
            requestBody.put("usecase", USECASE);
            requestBody.put("properties", new ArrayList<>());

            String jsonPayload = MAPPER.writeValueAsString(requestBody);

            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "[FusionAI] Hybrid path: " + COMPLETION_PATH
                                + " messages=" + messages.size()
                                + " maxTokens=" + maxTokens,
                        AppsLogger.INFO);
            }
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "[FusionAI] hybrid payload=" + jsonPayload, AppsLogger.FINER);
            }

            Object result = invokeGenerativeAiRestService(jsonPayload);
            if (result == null) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "[FusionAI] invokeGenerativeAiRestService returned null",
                            AppsLogger.WARNING);
                }
                return "Error: Fusion AI returned empty response (null).";
            }
            return extractResponse(result.toString());

        } catch (Exception e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return "Error: Fusion AI call failed — " + e.getMessage();
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Spectra (FAI Orchestrator) helpers — reflection-based
    //
    // Both methods use reflection so this class has no compile-time
    // dependency on the HcmFaiGenAiSdk jar. If the jar is missing, or
    // the ORA_FAI_SDK_ENABLE_SPECTRA_ROUTE profile option is not 'Y',
    // these methods return false/null and complete() falls back to the
    // existing ai-common hybrid path.
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Probes FaiSdkUtil.isSpectraRoutingEnabled() via reflection.
     * Returns false if the SDK class is not on the classpath or the
     * profile option check fails for any reason. Never throws.
     */
    private static boolean isSpectraRoutingEnabled() {
        try {
            Class<?> clazz = Class.forName(FAI_SDK_UTIL_CLASS);
            java.lang.reflect.Method method = clazz.getMethod("isSpectraRoutingEnabled");
            Object result = method.invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            // SDK not available or profile check failed — treat as disabled.
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
     * via reflection, serializes the returned FAICompletionsResponse to
     * JSON, and extracts the first choice's text.
     *
     * The properties list has one entry per placeholder in the
     * promptCode template text (hr_gen_ai_prompts_seed_b.prompt_tmpl).
     * The HCM Fast Formula template declares these placeholders:
     *   {systemPrompt}      — system instructions (14 CRITICAL REQUIREMENTS)
     *   {userPrompt}        — user's natural-language requirement
     *   {formulaType}       — selected formula type name
     *   {referenceFormula}  — RAG-retrieved reference formula (optional)
     *   {editorCode}        — current editor content (optional, follow-up turns)
     *   {additionalRules}   — per-type extra rules (optional)
     *   {chatHistory}       — formatted chat history (optional)
     *
     * Optional placeholders accept "" — the template is designed to
     * ignore XML tags whose body is empty/whitespace.
     *
     * Response shape (per fai-orchestrator LLMCompletionResponse):
     *   {"id": "...", "choices": [{"index": 0, "text": "..."}]}
     *
     * Returns null on any failure; caller falls back to the hybrid path.
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
                                + " propertyKeys=" + properties.size()
                                + " systemPromptLen=" + (systemPrompt == null ? 0 : systemPrompt.length())
                                + " userPromptLen=" + (userPrompt == null ? 0 : userPrompt.length()),
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

            // Serialize via Jackson so we don't need to know the exact
            // FAICompletionsResponse class structure. Works with both
            // POJO-style and record-style response objects.
            String json = MAPPER.writeValueAsString(response);
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "[FusionAI] Spectra raw response: " + json, AppsLogger.FINER);
            }

            JsonNode root = MAPPER.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "[FusionAI] Spectra response has no choices: " + json,
                            AppsLogger.WARNING);
                }
                return null;
            }

            JsonNode first = choices.get(0);
            // Preferred: completion-style "text" field.
            JsonNode textNode = first.path("text");
            if (!textNode.isMissingNode() && !textNode.isNull()) {
                String text = textNode.asText();
                if (!text.isEmpty()) {
                    return text;
                }
            }
            // Fallback: OpenAI chat-style "message.content".
            JsonNode contentNode = first.path("message").path("content");
            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                String text = contentNode.asText();
                if (!text.isEmpty()) {
                    return text;
                }
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

    /**
     * Appends a {key, value} entry to the Spectra properties list.
     * Null values become "" so the template placeholder renders empty
     * (the template ignores XML tags with whitespace-only bodies).
     */
    private static void addProperty(List<Map<String, String>> properties,
                                    String key, String value) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("key", key);
        p.put("value", value == null ? "" : value);
        properties.add(p);
    }

    // ???????????????????????????????????????????????????????????????????????
    // Fusion AI Apps methods (from AIAppsConnector)
    // TODO: uncomment in Fusion environment
    // ???????????????????????????????????????????????????????????????????????

    /**
     * Invoke AI rest service from TopologyManager.
     * Flow: get host ? get OAuth token ? POST to ai-common/llm/rest/v2/completion
     */
     public static Object invokeGenerativeAiRestService(String pJsonPayload) throws Exception {
         logFusion("Method Start: invokeGenerativeAiRestService");
         logFusion("Request payload:\n" + pJsonPayload);
    
         Object restResult = null;
         String hostTMRaw = null;
         String hostTM = null;
         String restURL = null;
         String authenticationToken = null;
         ConnectionHelper connectionFactory = new ConnectionHelper();
    
         URL hostURL = getHostFromTopologyManager(AIMASTER_EXTERNAL_ENDPOINT_KEY);
         if (hostURL == null) {
             logFusion("No host from Topology Manager found for key: " + AIMASTER_EXTERNAL_ENDPOINT_KEY);
             return null;
         } else {
             try {
                 hostTMRaw = hostURL.toString();
    
                 if (authenticationToken == null || authenticationToken.length() <= 0) {
                     authenticationToken = getAuthToken(hostTMRaw);
                     logFusion("New authentication token obtained");
                 }
    
                 if (!hostTMRaw.endsWith("/")) {
                     hostTM = hostTMRaw + "/";
                 }
    
                 restURL = hostTM + "ai-common/llm/rest/v2/completion";
                 logFusion("Final REST URL: " + restURL);
    
                 restResult = connectionFactory.doPost(restURL, pJsonPayload, authenticationToken);
    
             } catch (Exception e) {
                 // SEVERE inside catch — inline (not via logFusionError
                 // helper) so PSR sees the catch frame and accepts the
                 // SEVERE level. The helper itself is WARNING because it
                 // is also called from non-catch contexts.
                 AppsLogger.write(FusionAiProvider.class, e, AppsLogger.SEVERE);
                 throw e;
             }
         }
    
         logFusion("Rest response: " + (restResult != null ? restResult.toString() : "null"));
         logFusion("Method End: invokeGenerativeAiRestService");
         return restResult;
     }

    /**
     * Returns AIApps HOST URL from the Topology Manager.
     */
     public static URL getHostFromTopologyManager(String key) {
         logFusion("Method Start: getHostFromTopologyManager, Key: " + key);

         URL aiCloudHostURL = null;
         try {
             String cloudHost = EndPointProvider.getExternalEndpointWithProtocolByAppShortName(key);
             aiCloudHostURL = (cloudHost != null && !"".equals(cloudHost.trim())) ? new URL(cloudHost.trim()) : null;

             // INFO not SEVERE — this is the success path. PSR
             // (oracle.apps.ps java-appslogger-severe-level) rejects
             // AppsLogger.SEVERE outside catch blocks.
             if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                 AppsLogger.write(FusionAiProvider.class,
                         "Host from Topology Manager: " + aiCloudHostURL,
                         AppsLogger.INFO);
             }
         } catch (TMException tme) {
             // SEVERE inside catch — TM is a hard infra dep, this is a
             // legitimate platform failure.
             AppsLogger.write(FusionAiProvider.class, tme, AppsLogger.SEVERE);
         } catch (MalformedURLException mue) {
             AppsLogger.write(FusionAiProvider.class, mue, AppsLogger.SEVERE);
         } catch (Exception e) {
             AppsLogger.write(FusionAiProvider.class, e, AppsLogger.SEVERE);
         } finally {
             // WARNING not SEVERE — finally block is not a catch context;
             // PSR rejects SEVERE here. Caller (invokeGenerativeAiRestService)
             // already handles a null return without throwing, so WARNING
             // is the right severity for "we couldn't resolve a host".
             if (aiCloudHostURL == null && AppsLogger.isEnabled(AppsLogger.WARNING)) {
                 AppsLogger.write(FusionAiProvider.class,
                         "AI Apps Host not found in TopologyManager for key: " + key,
                         AppsLogger.WARNING);
             }
         }

         logFusion("Method End: getHostFromTopologyManager");
         return aiCloudHostURL;
     }

    /**
     * Get OAuth2 auth token from TopologyManager host.
     */
     private static String getAuthToken(String hostTM) throws Exception {
         logFusion("Method Start: getAuthToken");
    
         String authenticationTokenInfo = null;
         if (StringUtils.isNotBlank(hostTM)) {
             authenticationTokenInfo = getAccessTokenFromTopologyManager(hostTM);
             logFusion("Authentication token retrieved from Topology Manager");
             if (authenticationTokenInfo == null) {
                 logFusionError("Could not obtain authentication token for host: " + hostTM);
                 throw new RuntimeException("getAuthToken || Error getting authentication token");
             }
         } else {
             logFusion("hostTM string is empty");
             throw new RuntimeException("getAuthToken || hostTM string is empty");
         }
    
         logFusion("Method End: getAuthToken");
         return authenticationTokenInfo;
     }

    /**
     * Create PolicySetFeature for OAuth2 token request.
     */
     private static PolicySetFeature createPolicySetFeature(String host) throws Exception {
         logFusion("Method Start: createPolicySetFeature, host: " + host);
    
         if (host == null) {
             throw new Exception("Endpoint URL is null");
         }
    
         String scopeUri = host + "urn:opc:resource:consumer::all";
         PropertyFeature scope = new PropertyFeature(SecurityConstants.ConfigOverride.CO_SCOPE, scopeUri);
         PropertyFeature subjectPrecedence =
             new PropertyFeature(SecurityConstants.ConfigOverride.CO_SUBJECT_PRECEDENCE, "false");
    
         PolicyReferenceFeature[] clientPRF = new PolicyReferenceFeature[] {
             new PolicyReferenceFeature("oracle/http_oauth2_token_over_ssl_idcs_client_policy",
                                        new PropertyFeature[] { scope, subjectPrecedence })
         };
    
         PolicySetFeature policySetFeature = new PolicySetFeature(clientPRF);

         // INFO not SEVERE — success path. PSR rejects SEVERE here.
         if (AppsLogger.isEnabled(AppsLogger.INFO)) {
             AppsLogger.write(FusionAiProvider.class,
                     "PolicySetFeature created: scope=" + scopeUri, AppsLogger.INFO);
         }

         logFusion("Method End: createPolicySetFeature");
         return policySetFeature;
     }

    /**
     * Get OAuth2TokenResponse for the given host.
     */
     public static OAuth2TokenResponse getOAuthAccessToken(String host) throws Exception {
         logFusion("Method Start: getOAuthAccessToken, Host: " + host);
    
         OAuth2ClientTokenManager oauth2ClientTokenManager = OAuth2ClientTokenManager.getInstance();
         OAuth2TokenContext oauth2TokenContext = OAuth2ClientTokenManager.getOAuth2TokenContext();
         PolicySetFeature policySetFeature = createPolicySetFeature(host);
         oauth2TokenContext.setPolicySetFeature(policySetFeature);
    
         oauth2ClientTokenManager.getAccessToken(oauth2TokenContext);
         OAuth2TokenResponse oAuthToken = oauth2TokenContext.getOAuth2TokenResponse();
    
         logFusion("Method End: getOAuthAccessToken");
         return oAuthToken;
     }

    /**
     * Get AIApps Access Token for the given host.
     */
     public static String getAccessTokenFromTopologyManager(String host) {
         logFusion("Method Start: getAccessTokenFromTopologyManager, Host: " + host);
         try {
             OAuth2TokenResponse oauthTokenResponse = getOAuthAccessToken(host);
             if (oauthTokenResponse != null && oauthTokenResponse.getAccessToken() != null) {
                 // INFO not SEVERE — success path, PSR rejects SEVERE here.
                 if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                     AppsLogger.write(FusionAiProvider.class,
                             "Token Type: " + oauthTokenResponse.getTokenType()
                             + " | Expires In: " + oauthTokenResponse.getExpiresIn(),
                             AppsLogger.INFO);
                 }
                 return oauthTokenResponse.getAccessToken();
             }
             if (oauthTokenResponse != null && oauthTokenResponse.getError() != null) {
                 logFusionError("OAuth2 error: " + oauthTokenResponse.getError());
             }
         } catch (Exception e) {
             // SEVERE inside catch — token failure means no AI calls work.
             AppsLogger.write(FusionAiProvider.class, e, AppsLogger.SEVERE);
         }

         logFusion("Method End: getAccessTokenFromTopologyManager");
         return null;
     }

    // ?? Fusion logging helpers (uncomment in Fusion environment) ??????????

     private static void logFusion(String message) {
         if (AppsLogger.isEnabled(AppsLogger.FINER)) {
             AppsLogger.write(FusionAiProvider.class, message, AppsLogger.FINER);
         }
     }

     // WARNING-level helpers — PSR's java-appslogger-severe-level rule
     // rejects AppsLogger.SEVERE outside catch blocks, and static analysis
     // can't tell that these helpers are only called from catch contexts.
     // For genuine SEVERE cases inside a catch, inline the
     // AppsLogger.write call directly so the rule sees the catch frame.
     private static void logFusionError(String message) {
         if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
             AppsLogger.write(FusionAiProvider.class, message, AppsLogger.WARNING);
         }
     }

     private static void logFusionError(Exception e) {
         if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
             AppsLogger.write(FusionAiProvider.class, e, AppsLogger.WARNING);
         }
     }

    // ???????????????????????????????????????????????????????????????????????
    // Response parser + Debug/standalone mode
    // ???????????????????????????????????????????????????????????????????????

    /**
     * Extract AI response text from Fusion AI API response JSON.
     */
    private String extractResponse(String responseBody) {
        try {
            JsonNode json = MAPPER.readTree(responseBody);
            if (json.has("response")) {
                return json.get("response").asText("");
            }
            if (json.has("text")) {
                return json.get("text").asText("");
            }
            if (json.has("generatedText")) {
                return json.get("generatedText").asText("");
            }
            if (json.has("choices")) {
                return json.at("/choices/0/text").asText("");
            }
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "[FusionAI] Unknown response format, returning raw body",
                        AppsLogger.WARNING);
            }
            return responseBody;
        } catch (Exception e) {
            // SEVERE inside catch — JSON parse failure means the upstream
            // returned something unexpected; the raw body is logged at
            // FINER for diagnosis.
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                AppsLogger.write(this,
                        "[FusionAI] raw body that failed to parse:\n" + responseBody,
                        AppsLogger.FINER);
            }
            return responseBody;
        }
    }

    /**
     * Debug/standalone mode: call endpoint using java.net.http.
     * Set FUSION_AI_DEBUG_URL, FUSION_AI_DEBUG_USER, FUSION_AI_DEBUG_PASS env vars.
     */
    private String callDebugEndpoint(String jsonPayload) {
        String debugUrl = System.getenv("FUSION_AI_DEBUG_URL");
        if (debugUrl == null || debugUrl.isBlank()) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "[FusionAI] Neither Fusion environment nor FUSION_AI_DEBUG_URL available",
                        AppsLogger.WARNING);
            }
            return null;
        }

        try {
            if (!debugUrl.endsWith("/")) {
                debugUrl = debugUrl + "/";
            }
            String fullUrl = debugUrl + COMPLETION_PATH;
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "[FusionAI] Debug mode: calling " + fullUrl, AppsLogger.INFO);
            }

            String user = System.getenv("FUSION_AI_DEBUG_USER");
            String pass = System.getenv("FUSION_AI_DEBUG_PASS");
            String authHeader = null;
            if (user != null && pass != null) {
                String credentials = user + ":" + pass;
                authHeader = "Basic " + java.util.Base64.getEncoder()
                        .encodeToString(credentials.getBytes());
            }

            java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(fullUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload));

            if (authHeader != null) {
                requestBuilder.header("Authorization", authHeader);
            }

            java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                    .send(requestBuilder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "[FusionAI] Debug response status: " + status, AppsLogger.INFO);
            }

            if (status == 200) {
                return extractResponse(response.body());
            } else {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "[FusionAI] Debug error: " + status + " body: " + response.body(),
                            AppsLogger.WARNING);
                }
                return null;
            }
        } catch (Exception e) {
            // SEVERE inside catch — debug endpoint failure is non-recoverable
            // for the request, log everything we have.
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return null;
        }
    }
}
