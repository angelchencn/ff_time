#!/usr/bin/env python3
"""
Generate seed_custom_formulas.sql from custom_formulas.json.

Reads the 6 Custom Formula samples that currently live in the classpath JSON
file and emits a PL/SQL script that inserts them into the FF_FORMULA_TEMPLATES
tables as FORMULA_TYPE_ID=NULL ("Custom Formula" bucket) rows.

Run:
    python3 java/scripts/generate_custom_formulas_sql.py
Output:
    java/scripts/seed_custom_formulas.sql
"""
import json
import re
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
JSON_PATH = (
    PROJECT_ROOT
    / "java"
    / "src"
    / "main"
    / "java"
    / "oracle"
    / "apps"
    / "hcm"
    / "formulas"
    / "core"
    / "jersey"
    / "data"
    / "custom_formulas.json"
)
OUTPUT_PATH = PROJECT_ROOT / "java" / "scripts" / "seed_custom_formulas.sql"


def sanitize_for_code(name: str) -> str:
    """Make an UPPER_SNAKE token safe for TEMPLATE_CODE."""
    cleaned = re.sub(r"[^A-Za-z0-9]+", "_", name).upper().strip("_")
    return cleaned[:130] or "CUSTOM"


def q_string(text: str) -> str:
    """Wrap a string in Oracle Q-quoted literal using a delimiter that does
    not appear in the content. Handles multi-line strings and embedded single
    quotes without needing to double them up."""
    for left, right in (("[", "]"), ("<", ">"), ("{", "}"), ("(", ")")):
        if left not in text and right not in text:
            return f"q'{left}{text}{right}'"
    # Fallback: pick a character that definitely isn't present
    for ch in "#~^|!@%&*":
        if ch not in text:
            return f"q'{ch}{text}{ch}'"
    # Last resort: double-up single quotes
    return "'" + text.replace("'", "''") + "'"


HEADER = """-- ============================================================================
-- Seed the 6 Custom Formula samples from custom_formulas.json into
-- FUSION."ff_formula_templates" + FUSION."ff_formula_templates_tl".
--
-- Rows are inserted with:
--   FORMULA_TYPE_ID  = NULL           (Custom Formula bucket)
--   SOURCE_TYPE      = 'SEEDED'
--   ACTIVE_FLAG      = 'Y'
--   SEMANTIC_FLAG    = 'Y'
--   MODULE_ID        = 'HXT'
--   LANGUAGE         = 'US'
--
-- TEMPLATE_CODE uses the ORA_FFT_CUSTOM_<SLUG> prefix so it never collides
-- with the ORA_FFT_<TYPE>_NNN codes used by seed_ff_formula_templates.sql.
--
-- NOT committed automatically — review DBMS_OUTPUT then COMMIT; or ROLLBACK;
-- ============================================================================

SET SERVEROUTPUT ON SIZE UNLIMITED
SET DEFINE OFF
SET FEEDBACK OFF

-- Pre-flight: make sure the physical tables exist.
DECLARE
  v_count NUMBER;
BEGIN
  FOR t IN (
    SELECT 'ff_formula_templates'    AS obj FROM DUAL UNION ALL
    SELECT 'ff_formula_templates_tl' FROM DUAL
  ) LOOP
    SELECT COUNT(*) INTO v_count
      FROM (SELECT table_name AS name FROM all_tables WHERE owner='FUSION'
            UNION ALL
            SELECT view_name  AS name FROM all_views  WHERE owner='FUSION')
     WHERE LOWER(name) = LOWER(t.obj);
    IF v_count = 0 THEN
      RAISE_APPLICATION_ERROR(-20001, 'Missing FF object: FUSION.' || t.obj);
    END IF;
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('Pre-flight OK.');
END;
/

-- Warn if these custom rows already exist.
DECLARE
  v_existing NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_existing
    FROM FUSION."ff_formula_templates"
   WHERE TEMPLATE_CODE LIKE 'ORA_FFT_CUSTOM_%';
  IF v_existing > 0 THEN
    DBMS_OUTPUT.PUT_LINE('WARNING: ' || v_existing || ' ORA_FFT_CUSTOM_ rows already present.');
    DBMS_OUTPUT.PUT_LINE('To reimport cleanly run:');
    DBMS_OUTPUT.PUT_LINE('  DELETE FROM FUSION."ff_formula_templates_tl"');
    DBMS_OUTPUT.PUT_LINE('    WHERE TEMPLATE_ID IN (SELECT TEMPLATE_ID FROM FUSION."ff_formula_templates"');
    DBMS_OUTPUT.PUT_LINE('                          WHERE TEMPLATE_CODE LIKE ''ORA_FFT_CUSTOM_%'');');
    DBMS_OUTPUT.PUT_LINE('  DELETE FROM FUSION."ff_formula_templates" WHERE TEMPLATE_CODE LIKE ''ORA_FFT_CUSTOM_%'';');
    DBMS_OUTPUT.PUT_LINE('  COMMIT;');
  END IF;
END;
/

-- Main insert block.
DECLARE
  v_id        NUMBER(18);
  v_inserted  NUMBER := 0;
  v_sort      NUMBER := 0;

  PROCEDURE ins (
    p_code_suffix IN VARCHAR2,
    p_name        IN VARCHAR2,
    p_description IN VARCHAR2,
    p_formula     IN CLOB,
    p_rule        IN CLOB
  ) IS
  BEGIN
    v_id   := FUSION.S_ROW_ID_SEQ.NEXTVAL;
    v_sort := v_sort + 1;

    INSERT INTO FUSION."ff_formula_templates" (
      TEMPLATE_ID, FORMULA_TYPE_ID, TEMPLATE_CODE,
      FORMULA_TEXT, ADDITIONAL_PROMPT_TEXT,
      SOURCE_TYPE, ACTIVE_FLAG, SEMANTIC_FLAG, SORT_ORDER,
      OBJECT_VERSION_NUMBER, LAST_UPDATE_DATE, CREATED_BY, CREATION_DATE,
      LAST_UPDATED_BY, LAST_UPDATE_LOGIN,
      ENTERPRISE_ID, SEED_DATA_SOURCE, MODULE_ID,
      ORA_SEED_SET1, ORA_SEED_SET2
    ) VALUES (
      v_id, NULL, 'ORA_FFT_CUSTOM_' || p_code_suffix,
      p_formula, p_rule,
      'SEEDED', 'Y', 'Y', v_sort,
      1, SYSTIMESTAMP, 'SEED_DATA_FROM_APPLICATION', SYSTIMESTAMP,
      'SEED_DATA_FROM_APPLICATION', NULL,
      0, 'BULK_SEED_DATA_SCRIPT', 'HXT',
      'Y', 'Y'
    );

    INSERT INTO FUSION."ff_formula_templates_tl" (
      TEMPLATE_ID, LANGUAGE, NAME, SOURCE_LANG, DESCRIPTION,
      OBJECT_VERSION_NUMBER,
      CREATED_BY, CREATION_DATE, LAST_UPDATE_DATE,
      LAST_UPDATE_LOGIN, LAST_UPDATED_BY,
      ENTERPRISE_ID, SEED_DATA_SOURCE,
      ORA_SEED_SET1, ORA_SEED_SET2
    ) VALUES (
      v_id, 'US', p_name, 'US', p_description,
      1,
      'SEED_DATA_FROM_APPLICATION', SYSTIMESTAMP, SYSTIMESTAMP,
      NULL, 'SEED_DATA_FROM_APPLICATION',
      0, 'BULK_SEED_DATA_SCRIPT',
      'Y', 'Y'
    );

    v_inserted := v_inserted + 1;
  END ins;

BEGIN
"""

FOOTER = """
  DBMS_OUTPUT.PUT_LINE('============================================');
  DBMS_OUTPUT.PUT_LINE('Custom Formula import summary:');
  DBMS_OUTPUT.PUT_LINE('  Inserted: ' || v_inserted);
  DBMS_OUTPUT.PUT_LINE('============================================');
  DBMS_OUTPUT.PUT_LINE('Review output. Then run COMMIT; to save, or ROLLBACK; to discard.');
END;
/

SET FEEDBACK ON
"""


def main() -> int:
    if not JSON_PATH.exists():
        print(f"ERROR: {JSON_PATH} not found", file=sys.stderr)
        return 1

    with JSON_PATH.open() as f:
        data = json.load(f)

    samples = data.get("samples", [])
    if not samples:
        print("ERROR: no samples in custom_formulas.json", file=sys.stderr)
        return 1

    lines: list[str] = []
    for idx, sample in enumerate(samples, start=1):
        name = (sample.get("name") or "").strip() or f"Custom {idx}"
        desc = (sample.get("description") or "").strip()
        code = sample.get("code") or ""
        rule = sample.get("rule") or None

        slug = sanitize_for_code(name)
        code_suffix = f"{slug[:120]}_{idx:03d}"  # keep TEMPLATE_CODE well under 150 chars

        # NAME is VARCHAR2(240), DESCRIPTION is VARCHAR2(4000) — truncate to be safe
        name_lit = q_string(name[:240])
        desc_lit = q_string(desc[:4000])
        code_lit = q_string(code)
        rule_lit = q_string(rule) if rule else "NULL"

        lines.append(f"  ins(")
        lines.append(f"    '{code_suffix}',")
        lines.append(f"    {name_lit},")
        lines.append(f"    {desc_lit},")
        lines.append(f"    {code_lit},")
        lines.append(f"    {rule_lit}")
        lines.append(f"  );")

    sql = HEADER + "\n".join(lines) + "\n" + FOOTER
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(sql, encoding="utf-8")

    print(f"Generated {OUTPUT_PATH}")
    print(f"  Samples: {len(samples)}")
    print(f"  Size:    {len(sql) / 1024:.1f} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
