package oracle.apps.hcm.formulas.core.jersey.service;

import oracle.apps.fnd.applcore.log.AppsLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI service with pluggable LLM backend.
 *
 * Provider selection (via LLM_PROVIDER env var):
 *   - unset / anything else → Oracle Fusion Completions API (default)
 *   - "openai"              → OpenAI GPT-5.4
 *
 * Prompt building, RAG, and post-processing are provider-independent.
 */
public class AiService {

    private static final int MAX_TOKENS_CHAT = 10240;
    private static final int MAX_TOKENS_COMPLETION = 10240;

    /**
     * Fully-qualified name of {@code FusionAiProvider}, loaded via
     * reflection so this class can compile even when the FusionAiProvider
     * source file is excluded from the build (the {@code local-dev} Maven
     * profile excludes it because the version that lives in the Fusion
     * ADE workspace pulls in TopologyManager / OAuth2 / WSM jars that we
     * don't have on a developer laptop).
     */
    private static final String FUSION_PROVIDER_CLASS =
            "oracle.apps.hcm.formulas.core.jersey.service.FusionAiProvider";

    /**
     * Fully-qualified name of {@code AgentStudioProvider}, loaded via
     * reflection for the same reason as FusionAiProvider — the SDK jar
     * ({@code FAIOrchestratorAgentClientV2}) is only on the Fusion classpath.
     */
    private static final String AGENT_STUDIO_PROVIDER_CLASS =
            "oracle.apps.hcm.formulas.core.jersey.service.AgentStudioProvider";

    private final LlmProvider provider;
    private final RagService ragService = new RagService();

    /** Cached system prompt — loaded from DB on first use, then held in memory. */
    private volatile String cachedSystemPrompt;

    public AiService() {
        this.provider = selectProvider();
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(AiService.class,
                    "AiService initialized with provider: " + provider.name()
                            + " (available=" + provider.isAvailable() + ")",
                    AppsLogger.INFO);
        }
    }

    /**
     * Select the LLM provider based on {@code LLM_PROVIDER} env var:
     *
     *   - "openai"  → OpenAiProvider (local dev, GPT-5.4)
     *   - "spectra" → FusionAiProvider (Spectra completions, direct)
     *   - unset / anything else → AgentStudioProvider (default, Agent Studio workflow)
     */
    private static LlmProvider selectProvider() {
        String providerName = System.getenv("LLM_PROVIDER");
        if ("openai".equalsIgnoreCase(providerName)) {
            return new OpenAiProvider();
        }
        if ("spectra".equalsIgnoreCase(providerName)) {
            return loadFusionProviderOrFallback();
        }
        // Default: Agent Studio
        return loadAgentStudioProviderOrFallback();
    }

    /**
     * Returns the effective system prompt. On first call, attempts to load
     * from the {@code FF_FORMULA_TEMPLATES} table (the single row with
     * {@code SYSTEMPROMPT_FLAG='Y'} and {@code ACTIVE_FLAG='Y'}). If the
     * DB is unreachable or the row is missing/inactive, falls back to the
     * hardcoded {@link #DEFAULT_SYSTEM_PROMPT} constant. The result is
     * cached for the lifetime of this instance.
     */
    /**
     * Returns the effective system prompt. On first call, queries
     * {@code FF_FORMULA_TEMPLATES} for all active rows with
     * {@code SYSTEMPROMPT_FLAG='Y'}, ordered by {@code SORT_ORDER}.
     * Their {@code FORMULA_TEXT} values are concatenated (separated by
     * blank lines) to form the final prompt — so each row can be a
     * self-contained section (base rules, formula-type contracts,
     * anti-hallucination rules, etc.) and ops can reorder, enable, or
     * disable individual sections from the Manage Templates UI without
     * touching code.
     *
     * <p>Falls back to {@link #DEFAULT_SYSTEM_PROMPT} when the DB is
     * unreachable or no active rows exist.</p>
     */
    public String getSystemPrompt() {
        String cached = cachedSystemPrompt;
        if (cached != null) return cached;

        try {
            TemplateService ts = new TemplateService();
            List<Map<String, Object>> rows = ts.findSystemPrompts();
            if (!rows.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> row : rows) {
                    String text = (String) row.get("code"); // FORMULA_TEXT
                    if (text != null && !text.isBlank()) {
                        if (sb.length() > 0) sb.append("\n\n");
                        sb.append(text);
                    }
                }
                if (sb.length() > 0) {
                    String combined = sb.toString();
                    if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                        AppsLogger.write(this,
                                "System prompt loaded from DB: " + rows.size()
                                        + " template(s), " + combined.length() + " chars",
                                AppsLogger.INFO);
                    }
                    cachedSystemPrompt = combined;
                    return combined;
                }
            }
        } catch (Exception e) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "Cannot load system prompt from DB, returning empty: "
                                + e.getMessage(),
                        AppsLogger.WARNING);
            }
        }

        cachedSystemPrompt = "";
        return "";
    }

    /**
     * Try to construct {@code FusionAiProvider} reflectively. If the class
     * isn't on the classpath (because the local-dev Maven profile excluded
     * it from compilation) we silently fall back to {@link OpenAiProvider}
     * — the developer is running outside Fusion anyway, so a Fusion-only
     * provider would not work even if it compiled.
     *
     * <p>This mirrors the pattern in {@code DbConfig} which loads ADF BC
     * classes the same way: source-level compatibility with both build
     * profiles, runtime detection of which path is actually available.</p>
     */
    private static LlmProvider loadFusionProviderOrFallback() {
        try {
            Class<?> cls = Class.forName(FUSION_PROVIDER_CLASS);
            return (LlmProvider) cls.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException cnfe) {
            // Local-dev build: FusionAiProvider was deliberately excluded.
            // Fall back to OpenAi without raising — this is normal.
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(AiService.class,
                        FUSION_PROVIDER_CLASS + " not on classpath (local-dev build); "
                                + "falling back to OpenAiProvider",
                        AppsLogger.INFO);
            }
            return new OpenAiProvider();
        } catch (ReflectiveOperationException roe) {
            // Class is on the classpath but failed to instantiate — that's
            // a real bug, not a missing-by-design exclusion. SEVERE inside
            // catch.
            AppsLogger.write(AiService.class, roe, AppsLogger.SEVERE);
            return new OpenAiProvider();
        }
    }

    /**
     * Try to construct {@code AgentStudioProvider} reflectively. Falls back
     * to {@link FusionAiProvider} if the Agent Studio SDK is missing, then
     * to {@link OpenAiProvider} if Spectra SDK is also missing.
     */
    private static LlmProvider loadAgentStudioProviderOrFallback() {
        try {
            Class<?> cls = Class.forName(AGENT_STUDIO_PROVIDER_CLASS);
            return (LlmProvider) cls.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException cnfe) {
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(AiService.class,
                        AGENT_STUDIO_PROVIDER_CLASS + " not on classpath; "
                                + "falling back to FusionAiProvider",
                        AppsLogger.INFO);
            }
            return loadFusionProviderOrFallback();
        } catch (ReflectiveOperationException roe) {
            AppsLogger.write(AiService.class, roe, AppsLogger.SEVERE);
            return loadFusionProviderOrFallback();
        }
    }

    /** Constructor for testing — inject a custom provider. */
    public AiService(LlmProvider provider) {
        this.provider = provider;
    }

    /** Expose the underlying provider for direct calls (e.g. extract-prompt). */
    public LlmProvider getProvider() {
        return provider;
    }

    /**
     * Single source of truth for "are we running in a Fusion central
     * environment?". Reads {@code LLM_PROVIDER} env var:
     *
     *   - "openai" (case-insensitive)   → false (local dev)
     *   - unset / "spectra" / anything else → true (Fusion: Agent Studio or Spectra)
     *
     * Used by {@code DbConfig} to decide whether to open a JDBC connection
     * via ADF BC ApplicationModule (Fusion path) or via the plain
     * DriverManager + FF_DB_URL env vars (local dev path). Keeping the
     * check static + public means the two layers always agree on the
     * runtime mode.
     */
    public static boolean isFusionProviderActive() {
        String providerName = System.getenv("LLM_PROVIDER");
        return !"openai".equalsIgnoreCase(providerName);
    }

    // ── Chat ────────────────────────────────────────────────────────────────
    //
    // Two variants share the same prompt-building logic:
    //   streamChat(...)  — server-sent events, tokens pushed via a callback
    //   chatOnce(...)    — blocking, returns the full text at once
    //
    // Both delegate to buildPromptContext() which splits the prompt into 7
    // named fields (systemPrompt, userPrompt, formulaType, referenceFormula,
    // editorCode, additionalRules, chatHistory) and hands them to the
    // provider via completeWithContext / streamChatWithContext.

    public void streamChat(String message, String editorCode, String formulaType,
                           List<Map<String, String>> history,
                           String customSampleCode,
                           String customRule,
                           String promptCode,
                           Consumer<String> tokenCallback) {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "streamChat: provider=" + provider.name()
                            + " formulaType=" + formulaType
                            + " historyTurns=" + history.size()
                            + " hasSample=" + (customSampleCode != null)
                            + " hasRule=" + (customRule != null && !customRule.isBlank())
                            + " promptCode=" + promptCode,
                    AppsLogger.FINER);
        }
        if (!provider.isAvailable()) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "streamChat aborted: provider " + provider.name() + " not available",
                        AppsLogger.WARNING);
            }
            tokenCallback.accept("Error: " + provider.name() + " is not available.");
            return;
        }
        PromptContext context = buildPromptContext(
                message, editorCode, formulaType, history, customSampleCode, customRule, promptCode);
        provider.streamChatWithContext(context, MAX_TOKENS_CHAT, tokenCallback);
    }

    /** Convenience overload without history/custom sample/rule/promptCode */
    public void streamChat(String message, String editorCode, String formulaType,
                           Consumer<String> tokenCallback) {
        streamChat(message, editorCode, formulaType, List.of(), null, null, null, tokenCallback);
    }

    /**
     * Blocking counterpart of {@link #streamChat} — sends the same prompt to
     * the LLM and waits for the entire response before returning it as a
     * single string. Useful for clients that would rather poll than parse SSE.
     *
     * Returns a short error string (never null) when the provider is
     * unavailable so callers can forward it straight to the user.
     */
    public String chatOnce(String message, String editorCode, String formulaType,
                           List<Map<String, String>> history,
                           String customSampleCode,
                           String customRule,
                           String promptCode) {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "chatOnce: provider=" + provider.name()
                            + " formulaType=" + formulaType
                            + " historyTurns=" + history.size()
                            + " promptCode=" + promptCode,
                    AppsLogger.FINER);
        }
        if (!provider.isAvailable()) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "chatOnce aborted: provider " + provider.name() + " not available",
                        AppsLogger.WARNING);
            }
            return "Error: " + provider.name() + " is not available.";
        }
        PromptContext context = buildPromptContext(
                message, editorCode, formulaType, history, customSampleCode, customRule, promptCode);
        String response = provider.completeWithContext(context, MAX_TOKENS_CHAT);
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "chatOnce returned " + (response == null ? 0 : response.length()) + " chars",
                    AppsLogger.FINER);
        }
        return response == null ? "" : response;
    }

    // ── Structured prompt context ───────────────────────────────────────────
    //
    // buildPromptContext assembles a PromptContext with one field per
    // Spectra template placeholder ({systemPrompt}, {userPrompt},
    // {formulaType}, {referenceFormula}, {editorCode}, {additionalRules},
    // {chatHistory}). FusionAiProvider sends each field as a named
    // property to the Spectra template; OpenAiProvider's default impl
    // flattens them into a system + user message pair.
    //
    // Meta-instructions (output format, CRITICAL requirements, "Don't ask
    // for formula type") live in the Spectra template
    // (hr_gen_ai_prompts_seed_b.prompt_tmpl), NOT here — this code
    // assembles data only.

    /**
     * Assemble a structured {@link PromptContext} for the chat endpoints.
     * Each field maps to one placeholder in the Spectra promptCode template.
     */
    PromptContext buildPromptContext(
            String message, String editorCode, String formulaType,
            List<Map<String, String>> history,
            String customSampleCode, String customRule, String promptCode) {

        String systemPrompt = getSystemPrompt();
        String msg = extractUserRequestText(message, formulaType);
        String referenceFormula = extractReferenceFormula(message, customSampleCode);
        String normalizedEditor = (editorCode == null || editorCode.isBlank()) ? "" : editorCode;
        String additionalRules = (customRule == null || customRule.isBlank()) ? "" : customRule;
        String chatHistoryText = formatChatHistory(history);

        return new PromptContext(
                systemPrompt,
                msg,
                formulaType == null ? "" : formulaType,
                referenceFormula,
                normalizedEditor,
                additionalRules,
                chatHistoryText,
                promptCode
        );
    }

    /**
     * Resolve the reference formula for the {@code {referenceFormula}}
     * placeholder. Returns:
     * <ul>
     *   <li>the {@code customSampleCode} verbatim when supplied (template
     *       path — user chose a specific starter)</li>
     *   <li>a concatenation of up to 3 RAG-retrieved examples when
     *       {@code customSampleCode} is {@code null} (RAG path)</li>
     *   <li>empty string when {@code customSampleCode} is "" (explicit
     *       suppress — e.g. Custom formula type with no reference)</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    String extractReferenceFormula(String message, String customSampleCode) {
        if (customSampleCode != null && !customSampleCode.isBlank()) {
            return customSampleCode;
        }
        if (customSampleCode != null) {
            // "" sentinel — caller explicitly suppressed RAG (e.g. Custom type)
            return "";
        }
        try {
            List<Map<String, Object>> ragResults = ragService.query(message, 3);
            if (ragResults == null || ragResults.isEmpty()) return "";
            StringBuilder examples = new StringBuilder();
            for (Map<String, Object> r : ragResults) {
                String useCase = "";
                Map<String, Object> meta = (Map<String, Object>) r.get("metadata");
                if (meta != null) useCase = String.valueOf(meta.getOrDefault("use_case", ""));
                examples.append("/* use_case: ").append(useCase).append(" */\n");
                Object code = r.get("code");
                if (code != null) examples.append(code).append("\n\n");
            }
            return examples.toString().trim();
        } catch (Exception e) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "extractReferenceFormula: RAG query failed: " + e.getMessage(),
                        AppsLogger.WARNING);
            }
            return "";
        }
    }

    /**
     * Produce the user-request text for the {@code {userPrompt}} placeholder.
     * Returns only the user's actual message — formula type, reference
     * formula, editor code, and meta-instructions are carried in their own
     * PromptContext fields and rendered by the Spectra template.
     */
    String extractUserRequestText(String message, String formulaType) {
        return message == null ? "" : message;
    }

    /**
     * Render a history list of {@code [{role, content}, ...]} as plain text
     * suitable for the {@code {chatHistory}} placeholder. Returns empty
     * string when history is empty or null so the template skips the
     * {@code <chat_history>} XML tag.
     */
    String formatChatHistory(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> turn : history) {
            String role = turn.get("role");
            String content = turn.get("content");
            if (content == null || content.isBlank()) continue;
            String label = "assistant".equalsIgnoreCase(role) ? "Assistant" : "User";
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(label).append(": ").append(content);
        }
        return sb.toString();
    }

    // ── Complete ────────────────────────────────────────────────────────────

    public String complete(String code, int cursorLine) {
        if (!provider.isAvailable()) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "complete aborted: provider " + provider.name() + " not available",
                        AppsLogger.WARNING);
            }
            return "";
        }
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "complete: codeLen=" + code.length() + " line=" + cursorLine,
                    AppsLogger.FINER);
        }

        String prompt = "## Current Formula (cursor at line " + cursorLine + ")\n\n```\n" + code
                + "\n```\n\nProvide the most likely next tokens to complete this formula. "
                + "Return only the completion text, no explanation.";

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", getSystemPrompt()),
                Map.of("role", "user", "content", prompt)
        );

        // Use autocomplete (cheaper / faster model on providers that have
        // one) — this is editor inline-completion, not a multi-turn chat.
        return provider.autocomplete(messages, MAX_TOKENS_COMPLETION);
    }

    // ── Streaming Explain ───────────────────────────────────────────────────

    public void streamExplain(String code, Consumer<String> tokenCallback) {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "streamExplain: codeLen=" + (code == null ? 0 : code.length()),
                    AppsLogger.FINER);
        }
        if (!provider.isAvailable()) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this,
                        "streamExplain aborted: provider " + provider.name() + " not available",
                        AppsLogger.WARNING);
            }
            tokenCallback.accept("Error: " + provider.name() + " is not available.");
            return;
        }

        String prompt = "Formula:\n```\n" + code + "\n```\n\nAction: explain";
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", getSystemPrompt()),
                Map.of("role", "user", "content", prompt)
        );
        provider.streamChat(messages, MAX_TOKENS_CHAT, tokenCallback);
    }

    // buildGenerationPrompt / buildFollowUpPrompt / buildChatMessages removed
    // — these were the legacy flat-prompt builders that baked reference
    // formula, editor code, CRITICAL REQUIREMENTS, and meta-instructions
    // into a single merged user message. Replaced by buildPromptContext()
    // which passes each piece as an independent PromptContext field,
    // and the template on the Spectra side (prompt_tmpl) handles all
    // output-format / meta-instruction rules.

    // ── fix_default_types post-processor ────────────────────────────────────

    private static final Set<String> STRING_KEYWORDS = Set.of(
            "NAME", "TEXT", "DESC", "CODE", "TYPE", "STATUS", "FLAG", "MESSAGE",
            "MSG", "LABEL", "CATEGORY", "TITLE", "MODE", "REASON", "COMMENT",
            "NOTE", "KEY", "TAG", "LEVEL", "CLASS", "GROUP", "ROLE", "UNIT",
            "TASK", "PROCESS", "ACTION", "METHOD", "FORMAT", "PATTERN",
            "PREFIX", "SUFFIX", "STRING", "CHAR", "CURRENCY"
    );

    private static final Set<String> DATE_KEYWORDS = Set.of(
            "DATE", "START", "END", "EFFECTIVE", "EXPIRY", "HIRE",
            "TERMINATION", "BIRTH"
    );

    private static final Pattern DEFAULT_LINE_RE = Pattern.compile(
            "^(\\s*DEFAULT\\s+FOR\\s+)(\\w+)(\\s+IS\\s+)(\\S+.*)$",
            Pattern.CASE_INSENSITIVE
    );

    public static String fixDefaultTypes(String code) {
        String[] lines = code.split("\n", -1);
        List<String> fixed = new ArrayList<>();

        for (String line : lines) {
            Matcher m = DEFAULT_LINE_RE.matcher(line);
            if (m.matches()) {
                String prefix = m.group(1);
                String varName = m.group(2);
                String isPart = m.group(3);
                String value = m.group(4).trim();
                String[] parts = varName.toUpperCase().split("_");

                boolean isNumeric = value.equals("0") || value.equals("0.0") || value.equals("0.00");
                if (isNumeric) {
                    boolean isString = false, isDate = false;
                    for (String p : parts) {
                        if (STRING_KEYWORDS.contains(p)) isString = true;
                        if (DATE_KEYWORDS.contains(p)) isDate = true;
                    }
                    if (isString) {
                        line = prefix + varName + isPart + "' '";
                    } else if (isDate) {
                        line = prefix + varName + isPart + "'01-JAN-0001'(DATE)";
                    }
                }
            }
            fixed.add(line);
        }

        return String.join("\n", fixed);
    }

    // ── System Prompt ───────────────────────────────────────────────────────

    /** Hardcoded fallback used when no DB system prompt template is available. */
    public static final String DEFAULT_SYSTEM_PROMPT = "You are an expert assistant for Oracle Fusion Cloud HCM Fast Formula — a domain-specific language used to "
            + "configure payroll, time, and absence rules in Oracle Fusion Cloud.\n\n"
            + "IMPORTANT: This is Oracle Fusion Cloud HCM ONLY. Do NOT use EBS (E-Business Suite) or legacy "
            + "Oracle Applications Fast Formula syntax, APIs, or patterns. If you are unsure whether a feature "
            + "exists in Fusion Cloud, say so rather than guessing from EBS documentation.\n\n"
            + "## 0. Formula Type First (CRITICAL)\n\n"
            + "Before generating any formula, FIRST identify the formula type. The formula type determines:\n"
            + "- Which contexts are available (and therefore which DBIs can be accessed)\n"
            + "- Which input variables are valid\n"
            + "- Which RETURN variables are expected (the output contract)\n"
            + "- Whether INPUTS ARE is required or optional\n\n"
            + "Key formula type output contracts:\n"
            + "- **Oracle Payroll**: RETURN depends on element classification (earnings, deductions, etc.)\n"
            + "- **Element Skip**: RETURN skip_flag ('Y'/'N')\n"
            + "- **Payroll Run Proration**: RETURN prorate_start, prorate_end\n"
            + "- **Extract Record/Rule**: RETURN depends on extract definition\n"
            + "- **Validation**: RETURN formula_status ('S'/'E'/'W'), formula_message\n"
            + "- **WFM Time Calculation Rules**: RETURN as defined by time calculation rule\n"
            + "- **WFM Time Entry Rules**: RETURN as defined by time entry validation\n"
            + "- **Global Absence Accrual**: RETURN accrual amount and related variables\n"
            + "- **Flow Schedule / Task Repeat**: RETURN REPEATFLAG and flow-specific parameters\n"
            + "- **Net to Gross**: RETURN net_amount, gross_amount\n\n"
            + "If the user does not specify the formula type, ASK before generating. "
            + "Do NOT assume a generic structure — each type has specific constraints.\n\n"
            + "## 1. Data Types\n\n"
            + "Fast Formula has exactly THREE data types:\n"
            + "- NUMBER — integers and decimals (no scientific notation, no commas). Negative via leading minus.\n"
            + "- TEXT — enclosed in single quotes. Escape quotes by doubling: 'O''Brien'. Max concatenation result: 255 chars.\n"
            + "- DATE — format 'DD-MON-YYYY'(DATE), e.g. '01-JAN-2024'(DATE)\n"
            + "- Type inference: if not specified, NUMBER is assumed. Type inferred from first use.\n"
            + "- Array types (Fusion/Cloud): NUMBER_NUMBER, TEXT_NUMBER, TEXT_TEXT, DATE_NUMBER, DATE_TEXT, NUMBER_TEXT\n"
            + "- Empty arrays: EMPTY_NUMBER_NUMBER, EMPTY_TEXT_TEXT, etc.\n\n"
            + "## 2. Statement Ordering (MANDATORY)\n\n"
            + "Statements MUST appear in this order:\n"
            + "1. ALIAS statements\n"
            + "2. DEFAULT / DEFAULT_DATA_VALUE statements\n"
            + "3. INPUT / INPUTS statement\n"
            + "4. All other statements (LOCAL, assignment, IF, WHILE, RETURN, etc.)\n\n"
            + "## 3. Variable Declarations\n\n"
            + "ALIAS long_name AS short_name — alternative name for database items\n"
            + "DEFAULT FOR var IS value — fallback when input/DBI is NULL\n"
            + "DEFAULT_DATA_VALUE FOR var IS value — fallback for database items\n"
            + "INPUT IS var / INPUTS ARE var1, var2(date), var3(text) — max 15 inputs per element\n"
            + "OUTPUT IS var / OUTPUTS ARE var1, var2 — declare output variables\n"
            + "LOCAL var (type) — explicit local variable declaration\n\n"
            + "Variable naming: A-Z, 0-9, underscores. Max 80 chars. Case-insensitive. Cannot be reserved words.\n"
            + "Reserved words: ALIAS, AND, ARE, AS, DEFAULT, DEFAULTED, ELSE, EXECUTE, FOR, IF, INPUTS, IS, NOT, OR, RETURN, THEN, USING, WAS\n\n"
            + "## 4. Variable Scope\n\n"
            + "- Local variables: read-write, created by assignment or LOCAL\n"
            + "- Input values: READ-ONLY within the formula\n"
            + "- Database Items (DBI): READ-ONLY, resolved by context\n"
            + "- Global values: READ-ONLY, date-tracked, accessible from any formula\n"
            + "- Local variables MUST be initialized before use (error APP-FF-33005)\n\n"
            + "## 5. Operators (Precedence high to low)\n\n"
            + "1. Unary minus (-)\n"
            + "2. *, /\n"
            + "3. +, -, || (concatenation)\n"
            + "4. =, !=, <>, ><, <, >, <=, >=, =>, =<, LIKE, NOT LIKE\n"
            + "5. NOT\n"
            + "6. AND\n"
            + "7. OR\n"
            + "- Parentheses override precedence. Left-to-right within same level.\n"
            + "- Text comparison is CASE-SENSITIVE\n"
            + "- WAS DEFAULTED / WAS NOT DEFAULTED — test if default value was applied\n"
            + "- IS NULL / IS NOT NULL (Fusion/Cloud)\n\n"
            + "## 6. Control Flow\n\n"
            + "### IF/THEN/ELSE\n"
            + "IF condition THEN\n"
            + "(  /* CRITICAL: parentheses required for multiple statements */\n"
            + "  statement1\n"
            + "  statement2\n"
            + ")\n"
            + "ELSIF condition THEN\n"
            + "  single_statement\n"
            + "ELSE\n"
            + "(\n"
            + "  statement1\n"
            + "  statement2\n"
            + ")\n"
            + "END IF\n\n"
            + "WITHOUT parentheses, only the FIRST statement is conditional; rest execute unconditionally.\n\n"
            + "### WHILE/LOOP\n"
            + "WHILE condition LOOP\n"
            + "(\n"
            + "  statements\n"
            + ")\n"
            + "END LOOP\n"
            + "- EXIT to leave loop early (exits innermost loop only)\n"
            + "- No BREAK, CONTINUE, or labeled loops\n\n"
            + "### RETURN\n"
            + "RETURN var1, var2 — returns values and STOPS execution\n"
            + "RETURN — empty return, stops execution\n"
            + "- Statements after RETURN are never executed\n\n"
            + "## 7. CALL_FORMULA\n\n"
            + "CALL_FORMULA('formula_name',\n"
            + "  local_var > 'input_param',        /* > means IN */\n"
            + "  output_var < 'output_param' DEFAULT 0)  /* < means OUT */\n"
            + "- No recursive calls allowed\n"
            + "- Called formula must be compiled\n\n"
            + "## 8. EXECUTE Pattern (Alternative)\n\n"
            + "SET_INPUT('input_name', value)\n"
            + "EXECUTE('formula_name')\n"
            + "result = GET_OUTPUT('output_name', default_value)\n\n"
            + "## 9. CHANGE_CONTEXTS\n\n"
            + "CHANGE_CONTEXTS(CONTEXT_NAME = value)\n"
            + "(\n"
            + "  /* DBIs resolve under new context within this block only */\n"
            + ")\n"
            + "- Context reverts after block exits\n\n"
            + "## 10. Array Handling (Fusion/Cloud)\n\n"
            + "array_name[index] = value\n"
            + "value = array_name[index]\n"
            + "Methods: .FIRST(default), .LAST(default), .NEXT(index, default), .PRIOR(index, default), .EXISTS(index), .COUNT, .DELETE(index)\n"
            + "- Always check .EXISTS before accessing — nonexistent index causes runtime error\n"
            + "- Arrays cannot be passed to/from functions\n\n"
            + "## 11. Built-in Functions\n\n"
            + "Numeric: ABS, CEIL, FLOOR, GREATEST, LEAST, POWER, ROUND, ROUNDUP, TRUNC, ISNULL\n"
            + "String: CHR, INITCAP, INSTR, LENGTH, LOWER, LPAD, LTRIM, REPLACE, RPAD, RTRIM, SUBSTR, TRANSLATE, TRIM, UPPER\n"
            + "Date: ADD_DAYS, ADD_MONTHS, ADD_YEARS, DAYS_BETWEEN, HOURS_BETWEEN, LAST_DAY, MONTHS_BETWEEN, NEXT_DAY, NEW_TIME, GET_SYSDATE\n"
            + "Conversion: TO_CHAR, TO_DATE, TO_NUMBER, NUM_TO_CHAR\n"
            + "Context: GET_CONTEXT, SET_CONTEXT, NEED_CONTEXT\n"
            + "Payroll I/O: SET_INPUT, GET_INPUT, GET_OUTPUT, EXECUTE\n"
            + "Globals: SET_TEXT, SET_NUMBER, SET_DATE, GET_TEXT, GET_NUMBER, GET_DATE\n"
            + "Formula: CALL_FORMULA, PAY_INTERNAL_LOG_WRITE, PUT_MESSAGE, RAISE_ERROR\n"
            + "Lookup: GET_TABLE_VALUE, GET_LOOKUP_MEANING, CALCULATE_HOURS_WORKED, GET_WORKING_DAYS\n\n"
            + "## 12. Formula Types and Their Contracts\n\n"
            + "Each formula type has specific input/output contracts. Use ONLY the DBIs and return variables "
            + "available to the specified formula type. Key types:\n\n"
            + "| Type | Typical RETURN Variables | Notes |\n"
            + "|------|------------------------|-------|\n"
            + "| Oracle Payroll | varies by element classification | Earnings, deductions, info elements |\n"
            + "| Auto Indirect | indirect_result | Feeds indirect element entries |\n"
            + "| Element Skip | skip_flag ('Y'/'N') | Controls payroll run element processing |\n"
            + "| Payroll Run Proration | prorate_start, prorate_end | Proration event handling |\n"
            + "| Extract Record | varies by extract definition | BI Publisher / HCM Extracts |\n"
            + "| Extract Rule | include_flag ('Y'/'N') | Filter records in extract |\n"
            + "| Validation | formula_status ('S'/'E'/'W'), formula_message | Value set / field validation |\n"
            + "| WFM Time Calculation Rules | as defined by time rule | Prefix: ORA_WFM_TCR_ |\n"
            + "| WFM Time Entry Rules | as defined by entry rule | Prefix: ORA_WFM_TER_ |\n"
            + "| WFM Time Submission Rules | as defined by submit rule | Prefix: ORA_WFM_TSR_ |\n"
            + "| WFM Time Compliance Rules | as defined by compliance rule | Prefix: ORA_WFM_WCR_ |\n"
            + "| Global Absence Accrual | accrual_value, ceiling | Absence plan accrual calculation |\n"
            + "| Global Absence Entry Validation | formula_status, formula_message | Absence entry validation |\n"
            + "| Flow Schedule | schedule parameters | Payroll flow scheduling |\n"
            + "| Task Repeat | REPEATFLAG, task-specific params | Controls flow task iteration |\n"
            + "| Net to Gross | net_amount, gross_amount | Net-to-gross calculation |\n"
            + "| Calculation Utility | varies | Reusable calculation subroutine |\n"
            + "| Batch Loader | varies | Data loader formulas |\n\n"
            + "Other types: WFM Time Device Rules, WFM Time Advance Category Rules, "
            + "WFM Subroutine, WFM Utility, Global Absence Plan Entitlement, ACCRUAL.\n\n"
            + "RULE: RETURN variables MUST match the expected output contract for the formula type. "
            + "Do NOT invent return variable names — use the documented ones for each type.\n\n"
            + "## 13. Formula Structure Convention\n\n"
            + "### Header Comment Block (REQUIRED)\n"
            + "/******************************************************************************\n"
            + " * Formula Name : <FORMULA_NAME>\n"
            + " * Formula Type : <FORMULA_TYPE>\n"
            + " * Description  : <Brief description>\n"
            + " * Change History\n"
            + " * --------------\n"
            + " *  Who             Ver    Date          Description\n"
            + " * ---------------  ----   -----------   --------------------------------\n"
            + " * Payroll Admin    1.0    <YYYY/MM/DD>  Created.\n"
            + " ******************************************************************************/\n\n"
            + "### Naming Convention\n"
            + "Pattern: <PREFIX>_<BUSINESS_DESCRIPTION>_<SUFFIX>\n"
            + "Suffixes: _EARN, _RESULTS, _PRORATION, _BASE, _CALCULATOR, _DEDN, _AUTO_INDIRECT\n"
            + "Prefixes: ORA_WFM_, HRX_, _TCR_ (Time Calc), _TER_ (Time Entry), _TSR_ (Time Submit), _TDR_ (Time Device)\n\n"
            + "### Body Order (REQUIRED)\n"
            + "1. Header comment block\n"
            + "2. ALIAS statements (if any)\n"
            + "3. DEFAULT FOR statements (for each input that needs a fallback)\n"
            + "4. INPUTS ARE statement (if the formula type requires inputs)\n"
            + "5. LOCAL declarations (if any)\n"
            + "6. PAY_INTERNAL_LOG_WRITE entry log\n"
            + "7. Business logic\n"
            + "8. PAY_INTERNAL_LOG_WRITE exit log\n"
            + "9. RETURN statement (variables MUST match formula type output contract)\n"
            + "10. /* End Formula Text */\n\n"
            + "## 14. Output Format\n\n"
            + "- Return ONLY valid Fast Formula code — NO markdown fences, NO explanation.\n"
            + "- Use 2-space indentation for nested blocks.\n"
            + "- ALWAYS use parentheses ( ) for multiple statements in IF/ELSE/WHILE blocks.\n"
            + "- Include DEFAULT FOR for input variables that need fallback values (not all types require this).\n"
            + "- Include INPUTS ARE when the formula type requires input variables (some types like Extract Rule may not).\n"
            + "- ALWAYS end with RETURN. RETURN variables MUST match the formula type's expected output contract.\n"
            + "- ALWAYS include header comment block.\n"
            + "- Prefer INPUTS ARE over DBI reads for element inputs — INPUTS is more efficient.\n\n"
            + "## 15. DEFAULT Value Type Rules\n\n"
            + "Choose DEFAULT value by variable name:\n"
            + "- String (' '): NAME, TEXT, DESC, CODE, TYPE, STATUS, FLAG, MESSAGE, MSG, LABEL, CATEGORY, "
            + "TITLE, MODE, REASON, COMMENT, NOTE, KEY, TAG, LEVEL, CLASS, GROUP, ROLE, UNIT.\n"
            + "- Numeric (0): COUNT, COUNTER, TOTAL, AMOUNT, AMT, RATE, HOURS, DAYS, SALARY, PAY, WAGE, "
            + "BALANCE, QTY, QUANTITY, NUMBER, NUM, PCT, PERCENT, FACTOR, LIMIT, MAX, MIN, "
            + "THRESHOLD, INDEX, ID, CYCLE, ITERATION, RESULT, VALUE, SCORE.\n"
            + "- Date ('01-JAN-0001'(DATE)): DATE, START, END, FROM, TO, EFFECTIVE, EXPIRY, HIRE, TERMINATION, BIRTH.\n"
            + "- When in doubt, infer from usage (compared with string -> string; arithmetic -> numeric).\n\n"
            + "## 16. Limitations\n\n"
            + "- No recursive formula calls\n"
            + "- String concat max 255 characters\n"
            + "- No SWITCH/CASE — use nested IF/ELSIF\n"
            + "- No BREAK/CONTINUE in loops — use EXIT\n"
            + "- Max 15 input values per element\n"
            + "- Division by zero causes runtime error\n"
            + "- Uninitialized variable causes compile error\n"
            + "- Keywords are case-insensitive but text comparisons are case-sensitive\n"
            + "- Comments cannot be nested\n\n"
            + "## 17. Anti-Hallucination Rules\n\n"
            + "Do NOT invent or guess any of the following. If the user has not provided them, "
            + "either ask for clarification or generate a safe version with clearly marked placeholders "
            + "(e.g. <PLACEHOLDER_DBI_NAME>):\n"
            + "- Database Item (DBI) names — each formula type has a specific set of available DBIs\n"
            + "- Context names — only use contexts available to the formula type\n"
            + "- RETURN variable names — must match the formula type's output contract\n"
            + "- Task names, value set names, element names\n"
            + "- Formula names in CALL_FORMULA or EXECUTE\n\n"
            + "If you are uncertain about a DBI name or context, say: "
            + "'I used <PLACEHOLDER> — please replace with the actual DBI/context name from your environment.'\n\n"
            + "## 18. Compile Self-Check\n\n"
            + "Before returning generated code, mentally verify:\n"
            + "1. Statement order: ALIAS -> DEFAULT -> INPUTS -> LOCAL -> logic -> RETURN\n"
            + "2. No assignment to DBI or INPUT variables (they are READ-ONLY)\n"
            + "3. No context changes outside CHANGE_CONTEXTS block\n"
            + "4. All local variables initialized before first use\n"
            + "5. Array access guarded by .EXISTS check\n"
            + "6. No division by zero risk without guard (check divisor != 0)\n"
            + "7. WHILE loops have EXIT condition to prevent infinite loop\n"
            + "8. RETURN variables match the formula type's expected output contract\n"
            + "9. All string comparisons account for case sensitivity\n"
            + "10. No use of EBS-only functions or patterns in Fusion Cloud formulas\n";
}
