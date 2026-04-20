package oracle.apps.hcm.formulas.core.jersey.service;

/**
 * Structured prompt context passed to LLM providers.
 *
 * <p>Field names use {@code message} (matching the Agent Studio API)
 * for the user's natural language request. The Spectra placeholder key
 * remains {@code userPrompt} via {@link #KEY_USER_PROMPT} for backward
 * compatibility with existing prompt templates in
 * {@code hr_gen_ai_prompts_seed_b.prompt_tmpl}.
 *
 * <p>FusionAiProvider sends fields as named properties to Spectra.
 * AgentStudioProvider passes them as Agent Studio workflow parameters.
 * OpenAiProvider flattens them into a system + user message pair.
 *
 * <p>All fields are nullable; nulls are treated as empty strings at
 * send time.
 */
public record PromptContext(
        String systemPrompt,
        String message,
        String formulaType,
        String referenceFormula,
        String editorCode,
        String additionalRules,
        String chatHistory,
        String promptCode
) {
    // Property key names — must match {placeholder} names in the Spectra
    // prompt template (hr_gen_ai_prompts_seed_b.prompt_tmpl).
    // Note: Spectra uses "userPrompt" as the placeholder name; the record
    // field is "message" to align with Agent Studio's API convention.
    public static final String KEY_SYSTEM_PROMPT     = "systemPrompt";
    public static final String KEY_USER_PROMPT       = "userPrompt";
    public static final String KEY_FORMULA_TYPE      = "formulaType";
    public static final String KEY_REFERENCE_FORMULA = "referenceFormula";
    public static final String KEY_EDITOR_CODE       = "editorCode";
    public static final String KEY_ADDITIONAL_RULES  = "additionalRules";
    public static final String KEY_CHAT_HISTORY      = "chatHistory";

    /**
     * Minimal constructor for the common case — just system prompt + message
     * with a formula type. Other fields default to empty.
     */
    public static PromptContext of(String systemPrompt, String message, String formulaType) {
        return new PromptContext(systemPrompt, message, formulaType, "", "", "", "", null);
    }

    /** Null-safe accessor: returns "" instead of null. */
    public String systemPromptOrEmpty()     { return systemPrompt == null     ? "" : systemPrompt; }
    public String messageOrEmpty()          { return message == null          ? "" : message; }
    public String formulaTypeOrEmpty()      { return formulaType == null      ? "" : formulaType; }
    public String referenceFormulaOrEmpty() { return referenceFormula == null ? "" : referenceFormula; }
    public String editorCodeOrEmpty()       { return editorCode == null       ? "" : editorCode; }
    public String additionalRulesOrEmpty()  { return additionalRules == null  ? "" : additionalRules; }
    public String chatHistoryOrEmpty()      { return chatHistory == null      ? "" : chatHistory; }
    public String promptCodeOrNull()        { return promptCode; }
}
