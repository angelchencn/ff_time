package oracle.apps.hcm.formulas.core.jersey.service;

/**
 * Structured prompt context passed to LLM providers. Maps 1:1 to the
 * placeholders in the FAI Spectra template stored in
 * {@code hr_gen_ai_prompts_seed_b.prompt_tmpl} for promptCode
 * {@code docio_generic_extract_payload}:
 * <pre>
 *   {systemPrompt}      {userPrompt}        {formulaType}
 *   {referenceFormula}  {editorCode}        {additionalRules}
 *   {chatHistory}
 * </pre>
 *
 * <p>FusionAiProvider sends these verbatim as named properties to Spectra,
 * letting the server-side template render each section in its own XML tag.
 * OpenAiProvider flattens them into a single system + user messages pair
 * via the default implementation in {@link LlmProvider#completeWithContext}.
 *
 * <p>All fields are nullable; nulls are treated as empty strings at
 * send time so the template's empty-tag guards activate correctly.
 */
public record PromptContext(
        String systemPrompt,
        String userPrompt,
        String formulaType,
        String referenceFormula,
        String editorCode,
        String additionalRules,
        String chatHistory,
        String promptCode
) {
    // Property key names — must match {placeholder} names in the Spectra
    // prompt template (hr_gen_ai_prompts_seed_b.prompt_tmpl).
    public static final String KEY_SYSTEM_PROMPT     = "systemPrompt";
    public static final String KEY_USER_PROMPT       = "userPrompt";
    public static final String KEY_FORMULA_TYPE      = "formulaType";
    public static final String KEY_REFERENCE_FORMULA = "referenceFormula";
    public static final String KEY_EDITOR_CODE       = "editorCode";
    public static final String KEY_ADDITIONAL_RULES  = "additionalRules";
    public static final String KEY_CHAT_HISTORY      = "chatHistory";

    /**
     * Minimal constructor for the common case — just system + user prompt
     * with a formula type. Other fields default to empty.
     */
    public static PromptContext of(String systemPrompt, String userPrompt, String formulaType) {
        return new PromptContext(systemPrompt, userPrompt, formulaType, "", "", "", "", null);
    }

    /** Null-safe accessor: returns "" instead of null. */
    public String systemPromptOrEmpty()     { return systemPrompt == null     ? "" : systemPrompt; }
    public String userPromptOrEmpty()       { return userPrompt == null       ? "" : userPrompt; }
    public String formulaTypeOrEmpty()      { return formulaType == null      ? "" : formulaType; }
    public String referenceFormulaOrEmpty() { return referenceFormula == null ? "" : referenceFormula; }
    public String editorCodeOrEmpty()       { return editorCode == null       ? "" : editorCode; }
    public String additionalRulesOrEmpty()  { return additionalRules == null  ? "" : additionalRules; }
    public String chatHistoryOrEmpty()      { return chatHistory == null      ? "" : chatHistory; }
    public String promptCodeOrNull()        { return promptCode; }
}
