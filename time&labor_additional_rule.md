⏺ Time & Labor Specific Rules (Additional Rule)

  Apply these rules in addition to the general Fast Formula rules. They override
  conflicting general rules where noted.

  1. Decision Before Drafting

  - Map the user's requirement to one of these Time & Labor formula families:
    - Time Advanced Category Rule
    - Time Calculation Rule
    - Time Device Rule
    - Time Entry Rule
    - Time Submission Rule
    - Workforce Compliance Rule
  - If an existing Oracle-delivered formula already supports the requirement, STOP.
  Return the formula name with required inputs and outputs. Do NOT generate a new
  formula.
  - If the requirement mentions employee record or employee schedule, only use approved
  DBIs. Never invent DBIs.
  - If any essential detail is missing (rule purpose, inputs, outputs, parameters,
  defaults), STOP and ask for clarification before drafting.

  2. Naming and Suffix

  - Base formula name MUST end with _AP.
  - Keep DBIs, arrays, and context identifiers in their original UPPER_SNAKE_CASE (for
  example HWM_CTXARY_RECORD_POSITIONS).
  - Use lowerCamelCase for all new local variables (for example measurePeriod,
  weekTotalHours, eveningPremiumTotal).

  3. Body Structure (5 Blocks)

  Use comments to clearly separate these sections:

  /* ----- 1. Default declarations ----- */
  /* ----- 2. Inputs ----- */
  /* ----- 3. Context and parameter setup ----- */
  /* ----- 4. Processing logic ----- */
  /* ----- 5. Outputs ----- */

  - Declare null sentinels AFTER INPUTS ARE and BEFORE any GET_CONTEXT calls.
  - Do NOT declare nulls before INPUTS ARE.

  4. Required Null Sentinels

  NullDate     = '01-JAN-1900'(DATE)
  NullDateTime = '1900/01/01 00:00:00'(DATE)
  NullText     = '**FF_NULL**'

  Use GET_NULL_FF_TEXT, GET_NULL_FF_NUM, GET_NULL_FF_DATE only when Oracle's built-in
  null constants are required instead of local sentinels.

  5. Mandatory Context Initialization

  Immediately after INPUTS ARE:

  ffs_id  = GET_CONTEXT(HWM_FFS_ID, 0)
  rule_id = GET_CONTEXT(HWM_RULE_ID, 0)

  Add additional contexts only when needed:

  ctx_personId        = GET_CONTEXT(HWM_RESOURCE_ID, 0)
  ctx_subResource     = GET_CONTEXT(HWM_SUBRESOURCE_ID, 0)
  ctx_SearchStartDate = GET_CONTEXT(HWM_CTX_SEARCH_START_DATE, NullDate)
  ctx_SearchEndDate   = GET_CONTEXT(HWM_CTX_SEARCH_END_DATE, NullDate)

  Use CHANGE_CONTEXTS to change search date range:

  CHANGE_CONTEXTS(HWM_CTX_SEARCH_START_DATE = start_date,
                  HWM_CTX_SEARCH_END_DATE   = stop_date)

  6. Rule Parameter Access

  Use GET_RVALUE_NUMBER, GET_RVALUE_TEXT, or GET_RVALUE_DATE. These functions require:
  1. rule_id (must already have HWM_RULE_ID context)
  2. UPPER_CASE parameter name matching what the rule template exposes
  3. A default value

  thresholdHours = GET_RVALUE_NUMBER(rule_id, 'DEFINED_LIMIT', 40)

  Prefer Oracle-delivered parameter names like WORKED_TIME_CONDITION, DEFINED_LIMIT. If
  a required parameter is not covered by existing templates, ask the user for the exact
  name and default before drafting.

  7. Rule Header Metadata

  Read header values inside the formula:

  hSumLvl   = Get_Hdr_Text(rule_id, 'RUN_SUMMATION_LEVEL', 'TIMECARD')
  hExecType = Get_Hdr_Text(rule_id, 'RULE_EXEC_TYPE', 'CREATE')

  Use these values when branching on summation level or execution type. Do NOT ask the
  user for these.

  When the rule processes entire timecards or evaluates empty days, normalize
  INCLUDE_EMPTY_TC:

  processEmptyTc = Get_Hdr_Text(rule_id, 'INCLUDE_EMPTY_TC', 'Y')
  includeEmpty   = 'N'
  IF UPPER(processEmptyTc) = 'YES' OR UPPER(processEmptyTc) = 'Y' THEN
    includeEmpty = 'Y'

  8. Default Input Sets by Rule Family

  Reuse the curated Oracle input sets unless the user instructs otherwise. Default all
  arrays to their EMPTY_* equivalents.

  ┌───────────────┬─────────────────────────────────────────────────────────────────┐
  │  Rule Family  │          Required Inputs (in DEFAULT FOR + INPUTS ARE)          │
  ├───────────────┼─────────────────────────────────────────────────────────────────┤
  │ Time          │ HWM_CTXARY_RECORD_POSITIONS, HWM_CTXARY_HWM_MEASURE_DAY,        │
  │ Calculation   │ measure, StartTime, StopTime, TimeRecordType, PayrollTimeType,  │
  │ Rule          │ AbsenceType, UnitOfMeasure                                      │
  ├───────────────┼─────────────────────────────────────────────────────────────────┤
  │ Time Entry    │ HWM_CTXARY_RECORD_POSITIONS, HWM_CTXARY_HWM_MEASURE_DAY,        │
  │ Rule          │ measure, StartTime, StopTime, PayrollTimeType                   │
  ├───────────────┼─────────────────────────────────────────────────────────────────┤
  │ Time Device   │ HWM_CTXARY_RECORD_POSITIONS, HWM_CTXARY_HWM_MEASURE_DAY,        │
  │ Rule          │ HWM_CTXARY_SUBRESOURCE_ID, measure, StartTime, StopTime,        │
  │               │ TimeRecordType, SupplierEventOut                                │
  ├───────────────┼─────────────────────────────────────────────────────────────────┤
  │ Time          │ HWM_CTXARY_RECORD_POSITIONS, HWM_CTXARY_HWM_MEASURE_DAY,        │
  │ Submission    │ measure, StartTime, StopTime                                    │
  │ Rule          │                                                                 │
  └───────────────┴─────────────────────────────────────────────────────────────────┘

  Notes:
  - HWM_CTXARY_HWM_MEASURE_DAY returns an array of numbers representing total hours per
  day.
  - User-provided time inputs (startTime, stopTime) must use the same format as Standard
   Times.
  - Remove any inputs that are not used in the logic.

  9. DBI Rules

  - Only use Oracle-delivered DBIs. If a requested DBI is not in the approved list, ask
  for an alternative.
  - Set DBI defaults only with DEFAULT_DATA_VALUE.
  - Do NOT use DEFAULT_DATA_VALUE for non-DBI inputs.

  10. Logging (use ADD_RLOG, NOT PAY_INTERNAL_LOG_WRITE)

  rLog = ADD_RLOG(ffs_id, rule_id, logText)

  Log at entry, key decision points, and exit.

  11. Helper Functions

  Function: ADD_RLOG(ffs_id, rule_id, logText)
  Purpose: Diagnostic logging
  ────────────────────────────────────────
  Function: GET_OUTPUT_MSG, GET_OUTPUT_MSG1, GET_OUTPUT_MSG2
  Purpose: Format Oracle FND messages with 0/1/2 tokens
  ────────────────────────────────────────
  Function: GET_MSG_TAGS
  Purpose: Compliance rule message tags
  ────────────────────────────────────────
  Function: TIME_HHMM_TO_DEC
  Purpose: Convert HHMM number to decimal hours
  ────────────────────────────────────────
  Function: GET_MEASURE_FROM_TIME
  Purpose: Derive duration from start/stop timestamps
  ────────────────────────────────────────
  Function: GET_DATE_DAY_OF_WEEK, GET_IS_DATE_SAME_AS_DOW, IS_DATE_BETWEEN,
    GET_CURRENT_DATE
  Purpose: Date and day-of-week comparisons
  ────────────────────────────────────────
  Function: DAVE_TIME_SCAN_SET, DAVE_TIME_SCAN_RESET_INDEX, DAVE_TIME_SCAN_REC_DAY,
    DAVE_TIME_SCAN_REC_DTL, DAVE_TIME_SCAN_REC_DTL2, DAVE_TIME_SCAN_REC_TOTAL
  Purpose: Preload and iterate timecard data. Capture row-set key, pass eff_date or
    row_index, check status/status_log every call
  ────────────────────────────────────────
  Function: GET_DURATION_START_TO_NOW
  Purpose: Hours from start time to now (timezone-aware). Capture status,
    o_calculated_hours, o_status_log
  ────────────────────────────────────────
  Function: GET_UNPROCESSED_EVENT_SET, GET_UNPROCESSED_EVENT_REC
  Purpose: Retrieve cached time-device events
  ────────────────────────────────────────
  Function: DAVE_FIND_TIME_GAP
  Purpose: Scan prior timecards for gaps. All required parameters must be populated.
    Inspect status/log/gap details
  ────────────────────────────────────────
  Function: GET_REPEATING_PERIOD_ID
  Purpose: Get overtime repeating period for a person
  ────────────────────────────────────────
  Function: GET_PERIOD_ID_BY_BAL_DIM_NAME
  Purpose: Locate overtime repeating period by balance dimension name
  ────────────────────────────────────────
  Function: RAISE_ERROR(ffs_id, rule_id, messageText)
  Purpose: Stop processing with an error. Format: ex = RAISE_ERROR(ffs_id, rule_id,
    messageText)

  12. Business Logic Conventions

  - Weekend = Saturday + Sunday by default. Do NOT parameterize individual days unless
  explicitly requested.
  - Time crossing midnight: the calculated time belongs to the day the entry STARTS,
  unless otherwise specified.
  - Weekend premium: only the part of time overlapping weekend days counts toward the
  premium.
  - If only start time is provided and stop time is missing, do NOT derive stop time.
  - Use time categories to filter time entries. Do NOT manually re-filter arrays.

  13. Syntax Constraints (Override General Rules)

  - FORBIDDEN: EXIT, LEAVE, BREAK (overrides any general guidance about loop exit)
  - FORBIDDEN: SQL statements
  - FORBIDDEN: IS as comparison operator
  - FORBIDDEN: TO_CHAR on arrays
  - FORBIDDEN: Using HWM_CTXARY_HWM_MEASURE_DAY for context changes
  - All WHILE loops must have a guard against runaway iteration

  WHILE syntax:
  WHILE (nidx < max_ary) LOOP
  (
    /* body */
  )

  IF syntax:
  IF (OUT_MSG <> NullText) THEN
  (
    /* then body */
  )
  ELSE IF (OUT_MSG = NullText) THEN
  (
    /* else body */
  )

  14. Time Conversion and Data Types

  - Verify all time conversions. Data types must be consistent on assignment.
  - Use GET_MEASURE_FROM_TIME when you have start and stop time and need duration.

  15. RETURN

  - Single RETURN statement at the end, in the order expected by the consuming process.
  - Time Calculation rules return hours and time only. Do NOT return other Time
  Attributes like Time Types — these are set at the rule level.
  - Output group names must match the template's expected names.

  16. Self-Review Before Delivering

  Verify:
  - All 5 blocks are present (defaults, inputs, context, logic, outputs)
  - Logging at entry, key points, and exit
  - WHILE loops have guards
  - RETURN order matches consumer expectations
  - Unused inputs removed
  - Time conversion data types consistent
  - No invented DBIs, inputs, or contexts
  - No forbidden syntax (SQL, EXIT, IS, TO_CHAR on array)
  - Effective dates are valid ISO values

  If any check fails, revise or seek clarification. Do NOT deliver the formula.

  17. Output Format

  - Return ONLY the Fast Formula code block. No XML wrappers. No explanatory text.
  - When unsure about syntax, mirror the closest seed formula and ask the user instead
  of guessing.

  ---