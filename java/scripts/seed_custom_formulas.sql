-- ============================================================================
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
  ins(
    'OVERTIME_PAY_CALCULATION_001',
    q'[Overtime Pay Calculation]',
    q'[Calculate overtime pay for weekly hours over 40]',
    q'[/******************************************************************************
 *
 * Formula Name : CUSTOM_OVERTIME_PAY_CALC
 *
 * Formula Type : Custom
 *
 * Description  : Calculate overtime pay for hours exceeding 40 per week
 *
 * Change History
 * --------------
 *  Who             Ver    Date          Description
 * ---------------  ----   -----------   --------------------------------
 * Payroll Admin    1.0    2026/04/03    Created.
 *
 ******************************************************************************/

DEFAULT FOR HOURS_WORKED IS 0
DEFAULT FOR REGULAR_RATE IS 0
DEFAULT FOR OVERTIME_THRESHOLD IS 40
DEFAULT FOR OVERTIME_MULTIPLIER IS 1.5

INPUTS ARE
  HOURS_WORKED,
  REGULAR_RATE,
  OVERTIME_THRESHOLD,
  OVERTIME_MULTIPLIER

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_OVERTIME_PAY_CALC - Enter')

regular_hours = 0
overtime_hours = 0
regular_pay = 0
overtime_pay = 0
total_pay = 0

IF HOURS_WORKED > OVERTIME_THRESHOLD THEN
(
  regular_hours = OVERTIME_THRESHOLD
  overtime_hours = HOURS_WORKED - OVERTIME_THRESHOLD
)
ELSE
(
  regular_hours = HOURS_WORKED
  overtime_hours = 0
)

regular_pay = regular_hours * REGULAR_RATE
overtime_pay = overtime_hours * REGULAR_RATE * OVERTIME_MULTIPLIER
total_pay = regular_pay + overtime_pay

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_OVERTIME_PAY_CALC - Exit, total=' || TO_CHAR(total_pay))

RETURN regular_hours, overtime_hours, regular_pay, overtime_pay, total_pay

/* End Formula Text */]',
    NULL
  );
  ins(
    'SHIFT_DIFFERENTIAL_002',
    q'[Shift Differential]',
    q'[Apply shift differential premium for night/weekend shifts]',
    q'[/******************************************************************************
 *
 * Formula Name : CUSTOM_SHIFT_DIFFERENTIAL
 *
 * Formula Type : Custom
 *
 * Description  : Apply shift differential premium based on shift type
 *
 * Change History
 * --------------
 *  Who             Ver    Date          Description
 * ---------------  ----   -----------   --------------------------------
 * Payroll Admin    1.0    2026/04/03    Created.
 *
 ******************************************************************************/

DEFAULT FOR HOURS_WORKED IS 0
DEFAULT FOR BASE_RATE IS 0
DEFAULT FOR SHIFT_TYPE IS ' '

INPUTS ARE
  HOURS_WORKED,
  BASE_RATE,
  SHIFT_TYPE

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_SHIFT_DIFFERENTIAL - Enter')

l_differential_rate = 0
l_differential_pay = 0
l_base_pay = 0

IF SHIFT_TYPE = 'NIGHT' THEN
(
  l_differential_rate = 0.15
)
ELSIF SHIFT_TYPE = 'WEEKEND' THEN
(
  l_differential_rate = 0.10
)
ELSIF SHIFT_TYPE = 'HOLIDAY' THEN
(
  l_differential_rate = 0.25
)
ELSE
(
  l_differential_rate = 0
)

l_base_pay = HOURS_WORKED * BASE_RATE
l_differential_pay = l_base_pay * l_differential_rate

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_SHIFT_DIFFERENTIAL - Exit')

RETURN l_base_pay, l_differential_pay, l_differential_rate

/* End Formula Text */]',
    NULL
  );
  ins(
    'HOLIDAY_PAY_003',
    q'[Holiday Pay]',
    q'[Calculate holiday pay with configurable multiplier]',
    q'[/******************************************************************************
 *
 * Formula Name : CUSTOM_HOLIDAY_PAY_CALC
 *
 * Formula Type : Custom
 *
 * Description  : Calculate holiday pay with double-time or custom multiplier
 *
 * Change History
 * --------------
 *  Who             Ver    Date          Description
 * ---------------  ----   -----------   --------------------------------
 * Payroll Admin    1.0    2026/04/03    Created.
 *
 ******************************************************************************/

DEFAULT FOR HOURS_WORKED IS 0
DEFAULT FOR REGULAR_RATE IS 0
DEFAULT FOR HOLIDAY_FLAG IS 'N'
DEFAULT FOR HOLIDAY_MULTIPLIER IS 2.0

INPUTS ARE
  HOURS_WORKED,
  REGULAR_RATE,
  HOLIDAY_FLAG,
  HOLIDAY_MULTIPLIER

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_HOLIDAY_PAY_CALC - Enter')

holiday_pay = 0
regular_pay = 0

IF HOLIDAY_FLAG = 'Y' THEN
(
  holiday_pay = HOURS_WORKED * REGULAR_RATE * HOLIDAY_MULTIPLIER
  regular_pay = 0
)
ELSE
(
  holiday_pay = 0
  regular_pay = HOURS_WORKED * REGULAR_RATE
)

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_HOLIDAY_PAY_CALC - Exit')

RETURN holiday_pay, regular_pay

/* End Formula Text */]',
    NULL
  );
  ins(
    'WEEKLY_HOURS_CAP_004',
    q'[Weekly Hours Cap]',
    q'[Cap weekly hours at maximum and calculate excess]',
    q'[/******************************************************************************
 *
 * Formula Name : CUSTOM_WEEKLY_HOURS_CAP
 *
 * Formula Type : Custom
 *
 * Description  : Cap weekly hours at a configurable maximum, track excess
 *
 * Change History
 * --------------
 *  Who             Ver    Date          Description
 * ---------------  ----   -----------   --------------------------------
 * Payroll Admin    1.0    2026/04/03    Created.
 *
 ******************************************************************************/

DEFAULT FOR TOTAL_HOURS IS 0
DEFAULT FOR MAX_WEEKLY_HOURS IS 60
DEFAULT FOR EMPLOYEE_NAME IS ' '

INPUTS ARE
  TOTAL_HOURS,
  MAX_WEEKLY_HOURS,
  EMPLOYEE_NAME

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_WEEKLY_HOURS_CAP - Enter, employee=' || EMPLOYEE_NAME)

capped_hours = 0
excess_hours = 0
l_message_code = ' '
l_message_severity = ' '

IF TOTAL_HOURS > MAX_WEEKLY_HOURS THEN
(
  capped_hours = MAX_WEEKLY_HOURS
  excess_hours = TOTAL_HOURS - MAX_WEEKLY_HOURS
  l_message_code = 'HOURS_EXCEEDED_CAP'
  l_message_severity = 'W'
  l_log = PAY_INTERNAL_LOG_WRITE('WARNING: ' || EMPLOYEE_NAME || ' exceeded cap by ' || TO_CHAR(excess_hours) || ' hours')
)
ELSE
(
  capped_hours = TOTAL_HOURS
  excess_hours = 0
)

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_WEEKLY_HOURS_CAP - Exit')

RETURN capped_hours, excess_hours, l_message_code, l_message_severity

/* End Formula Text */]',
    NULL
  );
  ins(
    'TIME_ENTRY_VALIDATION_005',
    q'[Time Entry Validation]',
    q'[Validate time card entries for missing job or excessive hours]',
    q'[/******************************************************************************
 *
 * Formula Name : CUSTOM_TIME_ENTRY_VALIDATION
 *
 * Formula Type : Custom
 *
 * Description  : Validate time card entries — check for missing job assignment
 *                and daily hours exceeding 24
 *
 * Change History
 * --------------
 *  Who             Ver    Date          Description
 * ---------------  ----   -----------   --------------------------------
 * Payroll Admin    1.0    2026/04/03    Created.
 *
 ******************************************************************************/

DEFAULT FOR measure IS 0
DEFAULT FOR StartTime IS '01-JAN-0001'(DATE)
DEFAULT FOR StopTime IS '01-JAN-0001'(DATE)

INPUTS ARE
  measure,
  StartTime,
  StopTime

ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)
rule_id = GET_CONTEXT(HWM_RULE_ID, 0)

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_TIME_ENTRY_VALIDATION - Enter')

l_message_code = ' '
l_message_severity = ' '

l_job_id = GET_CONTEXT(HWM_JOB_ID, 0)
l_assignment_id = GET_CONTEXT(HWM_ASSIGNMENT_ID, 0)

/* Check for missing job assignment */
IF l_job_id = 0 THEN
(
  l_message_code = 'HWM_FF_TER_NO_JOB_ERR'
  l_message_severity = 'E'
  l_log = PAY_INTERNAL_LOG_WRITE('ERROR: No job assignment found')
)

/* Check for excessive daily hours */
IF l_message_severity != 'E' AND measure > 24 THEN
(
  l_message_code = 'HWM_FF_TER_DAILY_GT_MAX_ERR'
  l_message_severity = 'E'
  l_log = PAY_INTERNAL_LOG_WRITE('ERROR: Daily hours exceed 24: ' || TO_CHAR(measure))
)

l_log = PAY_INTERNAL_LOG_WRITE('CUSTOM_TIME_ENTRY_VALIDATION - Exit, code=' || l_message_code)

RETURN l_message_code, l_message_severity

/* End Formula Text */]',
    NULL
  );
  ins(
    'NEW_FORMULA_006',
    q'[New Formula]',
    q'[Descriptionsadfadsf]',
    q'[/******************************************************************************
 *
 * Formula Name : NEW_CUSTOM_FORMULA
 *
 * Formula Type : Custom
 *
 * Description  : New custom formula
 *
 ******************************************************************************/

DEFAULT FOR HOURS_WORKED IS 0

INPUTS ARE HOURS_WORKED

l_log = PAY_INTERNAL_LOG_WRITE('NEW_CUSTOM_FORMULA - Enter')

l_result = HOURS_WORKED

l_log = PAY_INTERNAL_LOG_WRITE('NEW_CUSTOM_FORMULA - Exit')

RETURN l_result

/* End Formula Text */]',
    NULL
  );

  DBMS_OUTPUT.PUT_LINE('============================================');
  DBMS_OUTPUT.PUT_LINE('Custom Formula import summary:');
  DBMS_OUTPUT.PUT_LINE('  Inserted: ' || v_inserted);
  DBMS_OUTPUT.PUT_LINE('============================================');
  DBMS_OUTPUT.PUT_LINE('Review output. Then run COMMIT; to save, or ROLLBACK; to discard.');
END;
/

SET FEEDBACK ON
