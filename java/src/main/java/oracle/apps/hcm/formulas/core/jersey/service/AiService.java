package oracle.apps.hcm.formulas.core.jersey.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI service with pluggable LLM backend.
 *
 * Provider selection (via LLM_PROVIDER env var):
 *   - "openai"  → OpenAI GPT-5.4 (default)
 *   - "fusion"  → Oracle Fusion Completions API
 *
 * Prompt building, RAG, templates, and post-processing are provider-independent.
 */
public class AiService {

    private static final Logger LOG = Logger.getLogger(AiService.class.getName());
    private static final int MAX_TOKENS_CHAT = 10240;
    private static final int MAX_TOKENS_COMPLETION = 512;

    private final LlmProvider provider;
    private final FormulaTypesService templateService = new FormulaTypesService();
    private final RagService ragService = new RagService();

    public AiService() {
        String providerName = System.getenv("LLM_PROVIDER");
        if ("openai".equalsIgnoreCase(providerName)) {
            this.provider = new OpenAiProvider();
        } else {
            // Default: Fusion
            this.provider = new FusionAiProvider();
        }
        LOG.info("AiService initialized with provider: " + provider.name()
                + " (available=" + provider.isAvailable() + ")");
    }

    /** Constructor for testing — inject a custom provider. */
    public AiService(LlmProvider provider) {
        this.provider = provider;
    }

    // ── Streaming Chat ──────────────────────────────────────────────────────

    public void streamChat(String message, String code, String formulaType,
                           List<Map<String, String>> history,
                           String customSampleCode,
                           String customRule,
                           Consumer<String> tokenCallback) {
        if (!provider.isAvailable()) {
            tokenCallback.accept("Error: " + provider.name() + " is not available.");
            return;
        }

        String userPrompt = buildGenerationPrompt(message, formulaType, code, customSampleCode);

        // Build system prompt, append custom rule if provided
        String sysPrompt = SYSTEM_PROMPT;
        if (customRule != null && !customRule.isBlank()) {
            sysPrompt = sysPrompt + "\n\n## 19. Custom Rule\n\n" + customRule;
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", sysPrompt));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userPrompt));

        provider.streamChat(messages, MAX_TOKENS_CHAT, tokenCallback);
    }

    /** Convenience overload without history/custom sample/rule */
    public void streamChat(String message, String code, String formulaType,
                           Consumer<String> tokenCallback) {
        streamChat(message, code, formulaType, List.of(), null, null, tokenCallback);
    }

    // ── Complete ────────────────────────────────────────────────────────────

    public String complete(String code, int cursorLine) {
        if (!provider.isAvailable()) return "";

        String prompt = "## Current Formula (cursor at line " + cursorLine + ")\n\n```\n" + code
                + "\n```\n\nProvide the most likely next tokens to complete this formula. "
                + "Return only the completion text, no explanation.";

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)
        );

        return provider.complete(messages, MAX_TOKENS_COMPLETION);
    }

    // ── Streaming Explain ───────────────────────────────────────────────────

    public void streamExplain(String code, Consumer<String> tokenCallback) {
        if (!provider.isAvailable()) {
            tokenCallback.accept("Error: " + provider.name() + " is not available.");
            return;
        }

        String prompt = "Formula:\n```\n" + code + "\n```\n\nAction: explain";
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)
        );
        provider.streamChat(messages, MAX_TOKENS_CHAT, tokenCallback);
    }

    // ── Prompt Builders ─────────────────────────────────────────────────────

    private String buildGenerationPrompt(String message, String formulaType, String currentCode) {
        return buildGenerationPrompt(message, formulaType, currentCode, null);
    }

    @SuppressWarnings("unchecked")
    private String buildGenerationPrompt(String message, String formulaType,
                                          String currentCode, String customSampleCode) {
        List<String> sections = new ArrayList<>();

        if (customSampleCode != null && !customSampleCode.isBlank()) {
            sections.add("## Reference Formula\n\nUse the following formula as the base template. "
                    + "Modify it according to the user's request.\n\n```\n"
                    + customSampleCode + "\n```");
        } else if (customSampleCode == null) {
            List<Map<String, Object>> ragResults = ragService.query(message, 3);
            if (!ragResults.isEmpty()) {
                StringBuilder examples = new StringBuilder();
                for (Map<String, Object> r : ragResults) {
                    String useCase = "";
                    Map<String, Object> meta = (Map<String, Object>) r.get("metadata");
                    if (meta != null) useCase = String.valueOf(meta.getOrDefault("use_case", ""));
                    examples.append("/* use_case: ").append(useCase).append(" */\n");
                    examples.append(r.get("code")).append("\n\n");
                }
                sections.add("## Relevant Example Formulas\n\n" + examples);
            }
        }

        boolean hasCode = currentCode != null && !currentCode.isBlank();
        if (hasCode) {
            sections.add("## Current Formula in Editor\n\n```\n" + currentCode + "\n```\n\n"
                    + "The user wants to modify this existing formula. "
                    + "Output the complete updated formula incorporating the requested change.");
        }

        // Skip formula type template for Custom formulas — the reference formula is the template
        boolean isCustom = customSampleCode != null;
        Map<String, Object> template = isCustom ? null : templateService.getTemplate(formulaType);
        if (template != null) {
            String skeleton = ((String) template.getOrDefault("skeleton", ""))
                    .replace("{date_today}", LocalDate.now().toString());
            String exampleSnippet = (String) template.getOrDefault("example_snippet", "");
            sections.add("## Formula Type Template\n\n"
                    + "**Type:** " + template.get("display_name") + "\n"
                    + "**Naming convention:** " + template.getOrDefault("naming_pattern", "") + "\n\n"
                    + "### Skeleton\n```\n" + skeleton + "\n```\n\n"
                    + (exampleSnippet.isEmpty() ? "" : "### Example Pattern\n```\n" + exampleSnippet + "\n```\n\n")
                    + "Use this skeleton as the structural foundation. "
                    + "Replace the placeholder business logic section with the actual implementation. "
                    + "Keep the header, DEFAULT/INPUTS, logging, and RETURN structure intact.");
        }

        if (isCustom) {
            sections.add("## Request\n\n"
                    + "Formula type: Custom (general-purpose formula, not bound to a specific Fusion formula type contract). "
                    + "Do NOT ask the user to confirm the formula type. "
                    + "Use the Reference Formula above as the structural template. "
                    + "RETURN variables should match the reference formula pattern or the user's requirement.\n\n"
                    + "Requirement: " + message);
        } else {
            sections.add("## Request\n\nFormula type: " + formulaType + "\n\nRequirement: " + message);
        }

        if (hasCode) {
            sections.add("Return the complete modified formula. Do not explain the changes unless asked. "
                    + "Preserve the header comment block if one exists, updating the Change History.");
        } else {
            sections.add("Generate a complete Fast Formula that satisfies the requirement. "
                    + "Follow the skeleton template above exactly — fill in the formula name, "
                    + "description, author, date, and replace the business logic placeholder. "
                    + "Derive a proper formula name following the naming convention shown.\n\n"
                    + "CRITICAL REQUIREMENTS:\n"
                    + "1. MUST include a professional header comment block\n"
                    + "2. Include DEFAULT FOR statements for input variables that need fallback values\n"
                    + "3. Include INPUTS ARE if the formula type requires input variables\n"
                    + "4. MUST include PAY_INTERNAL_LOG_WRITE at entry and exit\n"
                    + "5. MUST end with RETURN — variables must match the formula type's output contract\n"
                    + "6. Return ONLY the formula code, no markdown fences, no explanation\n"
                    + "7. Do NOT invent DBI names, context names, or return variable names — use placeholders if unsure");
        }

        return String.join("\n\n", sections);
    }

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

    public static final String SYSTEM_PROMPT = "You are an expert assistant for Oracle Fusion Cloud HCM Fast Formula — a domain-specific language used to "
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
