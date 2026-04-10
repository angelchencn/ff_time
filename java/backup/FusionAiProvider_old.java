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
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO: uncomment in Fusion environment
// import oracle.apps.fnd.applcore.log.AppsLogger;
// import oracle.apps.hcm.indexSearch.publicModel.util.aiapps.connection.ConnectionHelper;
// import oracle.apps.topologyManager.exception.TMException;
// import oracle.topologyManager.client.deployedInfo.EndPointProvider;
// import oracle.wsm.metadata.feature.PolicyReferenceFeature;
// import oracle.wsm.metadata.feature.PolicySetFeature;
// import oracle.wsm.metadata.feature.PropertyFeature;
// import oracle.wsm.security.oauth2.OAuth2ClientTokenManager;
// import oracle.wsm.security.oauth2.OAuth2TokenContext;
// import oracle.wsm.security.oauth2.OAuth2TokenResponse;
// import oracle.wsm.security.util.SecurityConstants;
// import org.apache.commons.lang3.StringUtils;

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

    private static final Logger LOG = Logger.getLogger(FusionAiProvider.class.getName());
    private static final String COMPLETION_PATH = "ai-common/llm/rest/v2/completion";
    private static final String USECASE = "HCM_FAST_FORMULA";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String AIMASTER_EXTERNAL_ENDPOINT_KEY = "ORA_AIAPP";

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

    @Override
    public String complete(List<Map<String, String>> messages, int maxTokens) {
        try {
            StringBuilder prompt = new StringBuilder();
            for (Map<String, String> msg : messages) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("system".equals(role)) {
                    prompt.append(content).append("\n\n");
                } else if ("user".equals(role)) {
                    prompt.append(content).append("\n\n");
                } else if ("assistant".equals(role)) {
                    prompt.append("Assistant: ").append(content).append("\n\n");
                }
            }

            // Fusion AI expects: { "prompt": "...", "usecase": "...", "properties": [...] }
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("prompt", prompt.toString().trim());
            requestBody.put("usecase", USECASE);
            requestBody.put("properties", new ArrayList<>());

            String jsonPayload = MAPPER.writeValueAsString(requestBody);

            LOG.info("[FusionAI] Calling ai-common/llm/rest/v2/completion");
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

            // ?? Fusion environment: uncomment this block, comment out callDebugEndpoint ??
            // Object result = invokeGenerativeAiRestService(jsonPayload);
            // if (result == null) {
            //     LOG.warning("[FusionAI] invokeGenerativeAiRestService returned null");
            //     return null;
            // }
            // return extractResponse(result.toString());

            // ?? Debug/standalone mode ??
            return callDebugEndpoint(jsonPayload);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[FusionAI] Exception: " + e.getMessage(), e);
            return null;
        }
    }

    // ???????????????????????????????????????????????????????????????????????
    // Fusion AI Apps methods (from AIAppsConnector)
    // TODO: uncomment in Fusion environment
    // ???????????????????????????????????????????????????????????????????????

    /**
     * Invoke AI rest service from TopologyManager.
     * Flow: get host ? get OAuth token ? POST to ai-common/llm/rest/v2/completion
     */
    // public static Object invokeGenerativeAiRestService(String pJsonPayload) throws Exception {
    //     logFusion("Method Start: invokeGenerativeAiRestService");
    //     logFusion("Request payload:\n" + pJsonPayload);
    //
    //     Object restResult = null;
    //     String hostTMRaw = null;
    //     String hostTM = null;
    //     String restURL = null;
    //     String authenticationToken = null;
    //     ConnectionHelper connectionFactory = new ConnectionHelper();
    //
    //     URL hostURL = getHostFromTopologyManager(AIMASTER_EXTERNAL_ENDPOINT_KEY);
    //     if (hostURL == null) {
    //         logFusion("No host from Topology Manager found for key: " + AIMASTER_EXTERNAL_ENDPOINT_KEY);
    //         return null;
    //     } else {
    //         try {
    //             hostTMRaw = hostURL.toString();
    //
    //             if (authenticationToken == null || authenticationToken.length() <= 0) {
    //                 authenticationToken = getAuthToken(hostTMRaw);
    //                 logFusion("New authentication token obtained");
    //             }
    //
    //             if (!hostTMRaw.endsWith("/")) {
    //                 hostTM = hostTMRaw + "/";
    //             }
    //
    //             restURL = hostTM + "ai-common/llm/rest/v2/completion";
    //             logFusion("Final REST URL: " + restURL);
    //
    //             restResult = connectionFactory.doPost(restURL, pJsonPayload, authenticationToken);
    //
    //         } catch (Exception e) {
    //             logFusionError(e);
    //             throw e;
    //         }
    //     }
    //
    //     logFusion("Rest response: " + (restResult != null ? restResult.toString() : "null"));
    //     logFusion("Method End: invokeGenerativeAiRestService");
    //     return restResult;
    // }

    /**
     * Returns AIApps HOST URL from the Topology Manager.
     */
    // public static URL getHostFromTopologyManager(String key) {
    //     logFusion("Method Start: getHostFromTopologyManager, Key: " + key);
    //
    //     URL aiCloudHostURL = null;
    //     try {
    //         String cloudHost = EndPointProvider.getExternalEndpointWithProtocolByAppShortName(key);
    //         aiCloudHostURL = (cloudHost != null && !"".equals(cloudHost.trim())) ? new URL(cloudHost.trim()) : null;
    //
    //         if (AppsLogger.isEnabled(AppsLogger.SEVERE)) {
    //             AppsLogger.write(FusionAiProvider.class, "Host from Topology Manager: " + aiCloudHostURL, AppsLogger.SEVERE);
    //         }
    //     } catch (TMException tme) {
    //         if (AppsLogger.isEnabled(AppsLogger.SEVERE)) {
    //             AppsLogger.write(FusionAiProvider.class,
    //                     "Topology Manager exception: " + tme, AppsLogger.SEVERE);
    //         }
    //     } catch (MalformedURLException mue) {
    //         if (AppsLogger.isEnabled(AppsLogger.SEVERE)) {
    //             AppsLogger.write(FusionAiProvider.class,
    //                     "Malformed URL from Topology Manager: " + mue, AppsLogger.SEVERE);
    //         }
    //     } catch (Exception e) {
    //         if (AppsLogger.isEnabled(AppsLogger.SEVERE)) {
    //             AppsLogger.write(FusionAiProvider.class, "Exception: " + e, AppsLogger.SEVERE);
    //         }
    //     } finally {
    //         if (aiCloudHostURL == null && AppsLogger.isEnabled(AppsLogger.SEVERE)) {
    //             AppsLogger.write(FusionAiProvider.class,
    //                     "AI Apps Host not found in TopologyManager for key: " + key, AppsLogger.SEVERE);
    //         }
    //     }
    //
    //     logFusion("Method End: getHostFromTopologyManager");
    //     return aiCloudHostURL;
    // }

    /**
     * Get OAuth2 auth token from TopologyManager host.
     */
    // private static String getAuthToken(String hostTM) throws Exception {
    //     logFusion("Method Start: getAuthToken");
    //
    //     String authenticationTokenInfo = null;
    //     if (StringUtils.isNotBlank(hostTM)) {
    //         authenticationTokenInfo = getAccessTokenFromTopologyManager(hostTM);
    //         logFusion("Authentication token retrieved from Topology Manager");
    //         if (authenticationTokenInfo == null) {
    //             logFusionError("Could not obtain authentication token for host: " + hostTM);
    //             throw new RuntimeException("getAuthToken || Error getting authentication token");
    //         }
    //     } else {
    //         logFusion("hostTM string is empty");
    //         throw new RuntimeException("getAuthToken || hostTM string is empty");
    //     }
    //
    //     logFusion("Method End: getAuthToken");
    //     return authenticationTokenInfo;
    // }

    /**
     * Create PolicySetFeature for OAuth2 token request.
     */
    // private static PolicySetFeature createPolicySetFeature(String host) throws Exception {
    //     logFusion("Method Start: createPolicySetFeature, host: " + host);
    //
    //     if (host == null) {
    //         throw new Exception("Endpoint URL is null");
    //     }
    //
    //     String scopeUri = host + "urn:opc:resource:consumer::all";
    //     PropertyFeature scope = new PropertyFeature(SecurityConstants.ConfigOverride.CO_SCOPE, scopeUri);
    //     PropertyFeature subjectPrecedence =
    //         new PropertyFeature(SecurityConstants.ConfigOverride.CO_SUBJECT_PRECEDENCE, "false");
    //
    //     PolicyReferenceFeature[] clientPRF = new PolicyReferenceFeature[] {
    //         new PolicyReferenceFeature("oracle/http_oauth2_token_over_ssl_idcs_client_policy",
    //                                    new PropertyFeature[] { scope, subjectPrecedence })
    //     };
    //
    //     PolicySetFeature policySetFeature = new PolicySetFeature(clientPRF);
    //
    //     if (AppsLogger.isEnabled(AppsLogger.SEVERE)) {
    //         AppsLogger.write(FusionAiProvider.class,
    //                 "PolicySetFeature created: scope=" + scopeUri, AppsLogger.SEVERE);
    //     }
    //
    //     logFusion("Method End: createPolicySetFeature");
    //     return policySetFeature;
    // }

    /**
     * Get OAuth2TokenResponse for the given host.
     */
    // public static OAuth2TokenResponse getOAuthAccessToken(String host) throws Exception {
    //     logFusion("Method Start: getOAuthAccessToken, Host: " + host);
    //
    //     OAuth2ClientTokenManager oauth2ClientTokenManager = OAuth2ClientTokenManager.getInstance();
    //     OAuth2TokenContext oauth2TokenContext = OAuth2ClientTokenManager.getOAuth2TokenContext();
    //     PolicySetFeature policySetFeature = createPolicySetFeature(host);
    //     oauth2TokenContext.setPolicySetFeature(policySetFeature);
    //
    //     oauth2ClientTokenManager.getAccessToken(oauth2TokenContext);
    //     OAuth2TokenResponse oAuthToken = oauth2TokenContext.getOAuth2TokenResponse();
    //
    //     logFusion("Method End: getOAuthAccessToken");
    //     return oAuthToken;
    // }

    /**
     * Get AIApps Access Token for the given host.
     */
    // public static String getAccessTokenFromTopologyManager(String host) {
    //     logFusion("Method Start: getAccessTokenFromTopologyManager, Host: " + host);
    //     try {
    //         OAuth2TokenResponse oauthTokenResponse = getOAuthAccessToken(host);
    //         if (oauthTokenResponse != null && oauthTokenResponse.getAccessToken() != null) {
    //             if (AppsLogger.isEnabled(AppsLogger.SEVERE)) {
    //                 AppsLogger.write(FusionAiProvider.class,
    //                         "Token Type: " + oauthTokenResponse.getTokenType()
    //                         + " | Expires In: " + oauthTokenResponse.getExpiresIn(), AppsLogger.SEVERE);
    //             }
    //             return oauthTokenResponse.getAccessToken();
    //         }
    //         if (oauthTokenResponse != null && oauthTokenResponse.getError() != null) {
    //             logFusionError("OAuth2 error: " + oauthTokenResponse.getError());
    //         }
    //     } catch (Exception e) {
    //         if (AppsLogger.isEnabled(AppsLogger.SEVERE)) {
    //             AppsLogger.write(FusionAiProvider.class,
    //                     "Exception getting OAuth token: " + e.getMessage(), AppsLogger.SEVERE);
    //         }
    //     }
    //
    //     logFusion("Method End: getAccessTokenFromTopologyManager");
    //     return null;
    // }

    // ?? Fusion logging helpers (uncomment in Fusion environment) ??????????

    // private static void logFusion(String message) {
    //     if (AppsLogger.isEnabled(AppsLogger.FINER)) {
    //         AppsLogger.write(FusionAiProvider.class, message, AppsLogger.FINER);
    //     }
    // }

    // private static void logFusionError(String message) {
    //     if (AppsLogger.isEnabled(AppsLogger.SEVERE)) {
    //         AppsLogger.write(FusionAiProvider.class, message, AppsLogger.SEVERE);
    //     }
    // }

    // private static void logFusionError(Exception e) {
    //     if (AppsLogger.isEnabled(AppsLogger.SEVERE)) {
    //         AppsLogger.write(FusionAiProvider.class, e, AppsLogger.SEVERE);
    //     }
    // }

    // ???????????????????????????????????????????????????????????????????????
    // Response parser + Debug/standalone mode
    // ???????????????????????????????????????????????????????????????????????

    /**
     * Extract AI response text from Fusion AI API response JSON.
     */
    private String extractResponse(String responseBody) {
        try {
            JsonNode json = MAPPER.readTree(responseBody);
            // Fusion AI response: { "id": "...", "choices": [{ "index": 0, "text": "..." }] }
            if (json.has("choices")) {
                // Try choices[0].text first (Fusion AI format)
                String text = json.at("/choices/0/text").asText("");
                if (!text.isEmpty()) return text;
                // Try choices[0].message.content (OpenAI format)
                text = json.at("/choices/0/message/content").asText("");
                if (!text.isEmpty()) return text;
            }
            if (json.has("response")) {
                return json.get("response").asText("");
            }
            if (json.has("text")) {
                return json.get("text").asText("");
            }
            if (json.has("generatedText")) {
                return json.get("generatedText").asText("");
            }
            LOG.info("[FusionAI] Unknown response format, returning raw body");
            return responseBody;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[FusionAI] Failed to parse response JSON", e);
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
            LOG.warning("[FusionAI] Neither Fusion environment nor FUSION_AI_DEBUG_URL available");
            return null;
        }

        try {
            if (!debugUrl.endsWith("/")) {
                debugUrl = debugUrl + "/";
            }
            String fullUrl = debugUrl + COMPLETION_PATH;
            LOG.info("[FusionAI] Debug mode: calling " + fullUrl);

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
            LOG.info("[FusionAI] Debug response status: " + status);

            if (status == 200) {
                return extractResponse(response.body());
            } else {
                LOG.warning("[FusionAI] Debug error: " + status + " body: " + response.body());
                return null;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[FusionAI] Debug endpoint exception: " + e.getMessage(), e);
            return null;
        }
    }
}
