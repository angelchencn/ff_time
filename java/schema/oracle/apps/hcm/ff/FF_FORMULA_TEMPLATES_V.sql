-- FF_FORMULA_TEMPLATES
-- View returning all rows and columns from "ff_formula_templates".
-- Fusion pattern: the physical table has a lowercase, double-quoted name so
-- that EBR can manage edition-based redefinition; user-facing code references
-- the uppercase view name.

CREATE OR REPLACE VIEW FF_FORMULA_TEMPLATES AS
SELECT
    TEMPLATE_ID,
    FORMULA_TYPE_ID,
    TEMPLATE_CODE,
    FORMULA_TEXT,
    ADDITIONAL_PROMPT_TEXT,
    SOURCE_TYPE,
    ACTIVE_FLAG,
    SEMANTIC_FLAG,
    SORT_ORDER,
    OBJECT_VERSION_NUMBER,
    LAST_UPDATE_DATE,
    CREATED_BY,
    CREATION_DATE,
    LAST_UPDATED_BY,
    LAST_UPDATE_LOGIN,
    ENTERPRISE_ID,
    SEED_DATA_SOURCE,
    MODULE_ID,
    ORA_SEED_SET1,
    ORA_SEED_SET2
FROM "ff_formula_templates";

COMMENT ON TABLE FF_FORMULA_TEMPLATES IS 'View over "ff_formula_templates". Fast Formula templates available in the AI-assisted formula authoring tool. Seeded rows are delivered by Oracle; USER_CREATED rows are saved from the UI.';
