-- ============================================================================
-- Seed data: System Prompt template row
--
-- This inserts one row into FF_FORMULA_TEMPLATES with SYSTEMPROMPT_FLAG='Y'.
-- AiService loads this row's FORMULA_TEXT as the LLM system prompt at runtime.
-- When ACTIVE_FLAG='Y', the DB-stored prompt is used; when 'N', AiService
-- falls back to the hardcoded DEFAULT_SYSTEM_PROMPT constant.
--
-- TEMPLATE_CODE: ORA_HCM_FF_SYSTEM_PROMPT
-- FORMULA_TYPE_ID: NULL (not tied to any formula type)
--
-- To update the system prompt: edit the FORMULA_TEXT in this row via the
-- Manage Templates UI (or UPDATE SQL), no code deployment needed.
-- ============================================================================

SET DEFINE OFF;

DECLARE
  v_template_id NUMBER;
  v_exists      NUMBER;
BEGIN
  -- Check if system prompt row already exists
  SELECT COUNT(*) INTO v_exists
    FROM FUSION."ff_formula_templates"
   WHERE TEMPLATE_CODE = 'ORA_HCM_FF_SYSTEM_PROMPT';

  IF v_exists > 0 THEN
    DBMS_OUTPUT.PUT_LINE('System prompt template already exists, skipping INSERT.');
    RETURN;
  END IF;

  SELECT FUSION.S_ROW_ID_SEQ.NEXTVAL INTO v_template_id FROM DUAL;

  INSERT INTO FUSION."ff_formula_templates" (
    TEMPLATE_ID, FORMULA_TYPE_ID, TEMPLATE_CODE, FORMULA_TEXT,
    ADDITIONAL_PROMPT_TEXT, SOURCE_TYPE, ACTIVE_FLAG, SEMANTIC_FLAG,
    SYSTEMPROMPT_FLAG, SORT_ORDER, OBJECT_VERSION_NUMBER,
    CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE,
    ENTERPRISE_ID, SEED_DATA_SOURCE, MODULE_ID, ORA_SEED_SET1, ORA_SEED_SET2
  ) VALUES (
    v_template_id,
    NULL,                           -- FORMULA_TYPE_ID: not tied to a type
    'ORA_HCM_FF_SYSTEM_PROMPT',     -- TEMPLATE_CODE
    -- FORMULA_TEXT: the full system prompt (AiService.DEFAULT_SYSTEM_PROMPT)
    'You are an expert assistant for Oracle Fusion Cloud HCM Fast Formula ' ||
    '— a domain-specific language used to configure payroll, time, and absence ' ||
    'rules in Oracle Fusion Cloud.' || CHR(10) || CHR(10) ||
    'IMPORTANT: This is Oracle Fusion Cloud HCM ONLY. Do NOT use EBS (E-Business Suite) or legacy ' ||
    'Oracle Applications Fast Formula syntax, APIs, or patterns. If you are unsure whether a feature ' ||
    'exists in Fusion Cloud, say so rather than guessing from EBS documentation.' || CHR(10) || CHR(10) ||
    '## 0. Formula Type First (CRITICAL)' || CHR(10) || CHR(10) ||
    'Before generating any formula, FIRST identify the formula type. The formula type determines:' || CHR(10) ||
    '- Which contexts are available (and therefore which DBIs can be accessed)' || CHR(10) ||
    '- Which input variables are valid' || CHR(10) ||
    '- Which RETURN variables are expected (the output contract)' || CHR(10) ||
    '- Whether INPUTS ARE is required or optional' || CHR(10) || CHR(10) ||
    'If the user does not specify the formula type, ASK before generating. ' ||
    'Do NOT assume a generic structure — each type has specific constraints.',
    NULL,                           -- ADDITIONAL_PROMPT_TEXT
    'SEEDED',                       -- SOURCE_TYPE
    'Y',                            -- ACTIVE_FLAG
    'N',                            -- SEMANTIC_FLAG (not for RAG search)
    'Y',                            -- SYSTEMPROMPT_FLAG
    0,                              -- SORT_ORDER
    1,                              -- OBJECT_VERSION_NUMBER
    'SEED_DATA_FROM_APPLICATION',   -- CREATED_BY
    SYSTIMESTAMP,                    -- CREATION_DATE
    'SEED_DATA_FROM_APPLICATION',   -- LAST_UPDATED_BY
    SYSTIMESTAMP,                    -- LAST_UPDATE_DATE
    0,                              -- ENTERPRISE_ID
    'BULK_SEED_DATA_SCRIPT',        -- SEED_DATA_SOURCE
    'HXT',                          -- MODULE_ID
    'Y',                            -- ORA_SEED_SET1
    'Y'                             -- ORA_SEED_SET2
  );

  INSERT INTO FUSION."ff_formula_templates_tl" (
    TEMPLATE_ID, LANGUAGE, SOURCE_LANG, NAME, DESCRIPTION,
    OBJECT_VERSION_NUMBER,
    CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE,
    ENTERPRISE_ID, SEED_DATA_SOURCE, ORA_SEED_SET1, ORA_SEED_SET2
  ) VALUES (
    v_template_id,
    'US',                                       -- LANGUAGE
    'US',                                       -- SOURCE_LANG
    'AI System Prompt',                         -- NAME
    'The base system prompt sent to the LLM on every Fast Formula generation request. ' ||
    'Edit FORMULA_TEXT to change the AI behavior without code deployment.',
    1,                                          -- OBJECT_VERSION_NUMBER
    'SEED_DATA_FROM_APPLICATION', SYSTIMESTAMP,
    'SEED_DATA_FROM_APPLICATION', SYSTIMESTAMP,
    0, 'BULK_SEED_DATA_SCRIPT', 'Y', 'Y'
  );

  COMMIT;
  DBMS_OUTPUT.PUT_LINE('System prompt template created: TEMPLATE_ID=' || v_template_id);
END;
/

SET DEFINE ON;
