REM $Header: fusionapps/hcm/pay/db/sql/bulkseed/FastFormulaServiceAM_FormulaTemplates.sql xiaojuch_ff_time/1 2026/04/18 00:00:00 xiaojuch Exp $
REM
REM FastFormulaServiceAM_FormulaTemplates.sql  &&1  &&2
REM &&1 = source schema (ie. fusion_seed)   &&2 = target schema (ie. fusion)
REM
REM Copyright (c) 2007, 2026, Oracle and/or its affiliates.
REM All rights reserved.
REM
REM    NAME
REM          FastFormulaServiceAM_FormulaTemplates.sql
REM
REM    DESCRIPTION
REM      Script to copy seed data from fusion_seed schema to fusion
REM      for FF_FORMULA_TEMPLATES and FF_FORMULA_TEMPLATES_TL tables.
REM
REM    NOTES
REM
REM    MODIFIED (MM/DD/YYYY)
REM    Initially generated 18-Apr-26
REM
REM    adxml: <src_file bootstrap="~BOOTSTRAP" version="~VERSION"
REM    adxml: translation_level="~TRANS_LEVEL" techstack_level ="~TXK_LEVEL"
REM    adxml: verticalisation_level="~VERT_LEVEL" custom_level="~CUST_LEVEL"
REM    adxml: language="~LANG" needs_translation="~TRANSLATION" ship="~SHIP"
REM    adxml: localization="~LOCALIZATION" object_type="~OBJECT_TYPE">
REM    adxml: <abstract_file te="~PROD" subdir="~PATH"
REM    adxml: file_basename="~BASENAME" file_type="~FILE_TYPE"/>
REM    adxml: <metadata>
REM    adxml: <action identifier="SEED" category="SD_BULK_SQL+15"
REM    adxml: portion="D" online_category="N">
REM    adxml: <apply_action>
REM    adxml: <action_details name="sqlplus" >
REM    adxml: <args>#batchsize#</args>
REM    adxml: </action_details>
REM    adxml: </apply_action>
REM    adxml: <ahead_of_time_run_conditions>
REM    adxml: <check_file check="N"/>
REM    adxml: </ahead_of_time_run_conditions>
REM    adxml: </action>
REM    adxml: </metadata>
REM    adxml: </src_file>

set serveroutput on;

REM
REM The SQL logic between the BEGIN - NO EDIT and the END - NO EDIT must NOT be modified. This logic is essential for LCM integration.
REM
REM BEGIN - NO EDIT
REM

SET VERIFY OFF;
WHENEVER OSERROR EXIT FAILURE ROLLBACK;

SET DEFINE ON;
WHENEVER SQLERROR EXIT FAILURE ROLLBACK;

define l_fusion_schema=&&1
variable seed_schema varchar2(30)
column seed_schema new_value l_seed_schema noprint
begin
   :seed_schema := 'FUSION_SEED';

   IF (SUBSTR(NVL(fnd_profile.value('ORA_FND_BULK_SQL_MODE'), 'SDF'),1, 4) = 'BULK') THEN
      execute immediate 'alter session set PLSQL_CCFlags = ''BULKPROCESSENABLED:TRUE''';
   ELSE
   execute immediate 'alter session set PLSQL_CCFlags = ''BULKPROCESSENABLED:FALSE''';
   END IF;

end;
/

select :seed_schema seed_schema from dual;

REM
REM END - NO EDIT
REM

PROMPT Starting script -> FastFormulaServiceAM_FormulaTemplates.sql

declare

l_fusion_schema varchar2(30) := upper('&1');
l_stage_schema varchar2(30) := 'FUSION_SEED';
l_batch_size   number       := &3;
l_record_count number;
start_date date;
end_date   date;
l_script_name varchar2(4000) := '$Source: fusionapps/hcm/pay/db/sql/bulkseed/FastFormulaServiceAM_FormulaTemplates.sql $';
l_table_list fnd_bulk_seed.table_list_type := fnd_bulk_seed.table_list_type('FF_FORMULA_TEMPLATES','FF_FORMULA_TEMPLATES_TL');

begin

    $IF $$BULKPROCESSENABLED = TRUE $THEN

      start_date := sysdate;
      if (not fnd_bulk_seed.start_processing(l_script_name, l_table_list)) then
        dbms_output.put_line('Script already processed, aborting.');
        return;
      end if;

      dbms_output.put_line('---------- FF_FORMULA_TEMPLATES ----------');
   IF (NVL(fnd_profile.value('ORA_FND_BULK_SQL_MODE'), 'SDF') = 'BULKTEST') THEN
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..FF_FORMULA_TEMPLATES;
      dbms_output.put_line('   FUSION_SEED ROWS=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..FF_FORMULA_TEMPLATES
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION_SEED != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..FF_FORMULA_TEMPLATES
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..FF_FORMULA_TEMPLATES;
      dbms_output.put_line('        FUSION ROWS=>'||TO_CHAR(l_record_count));
   END IF;

      MERGE INTO &l_fusion_schema..FF_FORMULA_TEMPLATES QUERY_TAR
      USING (SELECT
         FF_FORMULA_TEMPLATES_SRC.SEED_DATA_SOURCE AS SEED_DATA_SOURCE,
         FF_FORMULA_TEMPLATES_SRC.TEMPLATE_ID AS TEMPLATE_ID,
         FF_FORMULA_TEMPLATES_SRC.FORMULA_TYPE_ID AS FORMULA_TYPE_ID,
         FF_FORMULA_TEMPLATES_SRC.TEMPLATE_CODE AS TEMPLATE_CODE,
         FF_FORMULA_TEMPLATES_SRC.FORMULA_TEXT AS FORMULA_TEXT,
         FF_FORMULA_TEMPLATES_SRC.ADDITIONAL_PROMPT_TEXT AS ADDITIONAL_PROMPT_TEXT,
         FF_FORMULA_TEMPLATES_SRC.SOURCE_TYPE AS SOURCE_TYPE,
         FF_FORMULA_TEMPLATES_SRC.ACTIVE_FLAG AS ACTIVE_FLAG,
         FF_FORMULA_TEMPLATES_SRC.SEMANTIC_FLAG AS SEMANTIC_FLAG,
         FF_FORMULA_TEMPLATES_SRC.SYSTEMPROMPT_FLAG AS SYSTEMPROMPT_FLAG,
         FF_FORMULA_TEMPLATES_SRC.USE_SYSTEM_PROMPT_FLAG AS USE_SYSTEM_PROMPT_FLAG,
         FF_FORMULA_TEMPLATES_SRC.SORT_ORDER AS SORT_ORDER,
         FF_FORMULA_TEMPLATES_SRC.OBJECT_VERSION_NUMBER AS OBJECT_VERSION_NUMBER,
         FF_FORMULA_TEMPLATES_SRC.MODULE_ID AS MODULE_ID,
         FF_FORMULA_TEMPLATES_SRC.ENTERPRISE_ID AS ENTERPRISE_ID
         FROM &l_seed_schema..FF_FORMULA_TEMPLATES FF_FORMULA_TEMPLATES_SRC
      ) QUERY_SRC
      ON (QUERY_SRC.TEMPLATE_CODE = QUERY_TAR.TEMPLATE_CODE)
      WHEN MATCHED THEN UPDATE SET
         SEED_DATA_SOURCE = QUERY_SRC.SEED_DATA_SOURCE,
         FORMULA_TYPE_ID = QUERY_SRC.FORMULA_TYPE_ID,
         FORMULA_TEXT = QUERY_SRC.FORMULA_TEXT,
         ADDITIONAL_PROMPT_TEXT = QUERY_SRC.ADDITIONAL_PROMPT_TEXT,
         SOURCE_TYPE = QUERY_SRC.SOURCE_TYPE,
         ACTIVE_FLAG = QUERY_SRC.ACTIVE_FLAG,
         SEMANTIC_FLAG = QUERY_SRC.SEMANTIC_FLAG,
         SYSTEMPROMPT_FLAG = QUERY_SRC.SYSTEMPROMPT_FLAG,
         USE_SYSTEM_PROMPT_FLAG = QUERY_SRC.USE_SYSTEM_PROMPT_FLAG,
         SORT_ORDER = QUERY_SRC.SORT_ORDER,
         OBJECT_VERSION_NUMBER = QUERY_SRC.OBJECT_VERSION_NUMBER,
         LAST_UPDATE_DATE = systimestamp,
         LAST_UPDATE_LOGIN = -1,
         LAST_UPDATED_BY = 'SEED_DATA_FROM_APPLICATION',
         MODULE_ID = QUERY_SRC.MODULE_ID,
         ENTERPRISE_ID = QUERY_SRC.ENTERPRISE_ID
         WHERE last_updated_by IN ('SEED_DATA_FROM_APPLICATION', '0')
      WHEN NOT MATCHED THEN INSERT (
         SEED_DATA_SOURCE,
         TEMPLATE_ID,
         FORMULA_TYPE_ID,
         TEMPLATE_CODE,
         FORMULA_TEXT,
         ADDITIONAL_PROMPT_TEXT,
         SOURCE_TYPE,
         ACTIVE_FLAG,
         SEMANTIC_FLAG,
         SYSTEMPROMPT_FLAG,
         USE_SYSTEM_PROMPT_FLAG,
         SORT_ORDER,
         OBJECT_VERSION_NUMBER,
         CREATED_BY,
         CREATION_DATE,
         LAST_UPDATE_DATE,
         LAST_UPDATE_LOGIN,
         LAST_UPDATED_BY,
         MODULE_ID,
         ENTERPRISE_ID
         )
      VALUES (
         QUERY_SRC.SEED_DATA_SOURCE,
         S_ROW_ID_SEQ.NEXTVAL,
         QUERY_SRC.FORMULA_TYPE_ID,
         QUERY_SRC.TEMPLATE_CODE,
         QUERY_SRC.FORMULA_TEXT,
         QUERY_SRC.ADDITIONAL_PROMPT_TEXT,
         QUERY_SRC.SOURCE_TYPE,
         QUERY_SRC.ACTIVE_FLAG,
         QUERY_SRC.SEMANTIC_FLAG,
         QUERY_SRC.SYSTEMPROMPT_FLAG,
         QUERY_SRC.USE_SYSTEM_PROMPT_FLAG,
         QUERY_SRC.SORT_ORDER,
         QUERY_SRC.OBJECT_VERSION_NUMBER,
         'SEED_DATA_FROM_APPLICATION',
         systimestamp,
         systimestamp,
         -1,
         'SEED_DATA_FROM_APPLICATION',
         QUERY_SRC.MODULE_ID,
         QUERY_SRC.ENTERPRISE_ID
         )
      LOG ERRORS INTO &l_fusion_schema..FND_BULK_MERGE_ERRORS ('FF_FORMULA_TEMPLATES::'||to_char(sysdate)) REJECT LIMIT UNLIMITED;

   dbms_output.put_line('FF_FORMULA_TEMPLATES      MERGED=>'||TO_CHAR(SQL%ROWCOUNT));



      dbms_output.put_line('---------- FF_FORMULA_TEMPLATES_TL ----------');
   IF (NVL(fnd_profile.value('ORA_FND_BULK_SQL_MODE'), 'SDF') = 'BULKTEST') THEN
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..FF_FORMULA_TEMPLATES_TL;
      dbms_output.put_line('   FUSION_SEED ROWS=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..FF_FORMULA_TEMPLATES_TL
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION_SEED != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..FF_FORMULA_TEMPLATES_TL
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..FF_FORMULA_TEMPLATES_TL;
      dbms_output.put_line('        FUSION ROWS=>'||TO_CHAR(l_record_count));
   END IF;

      MERGE INTO &l_fusion_schema..FF_FORMULA_TEMPLATES_TL QUERY_TAR
      USING (SELECT
         FF_FORMULA_TEMPLATES_TL_SRC.SEED_DATA_SOURCE AS SEED_DATA_SOURCE,
         FF_FORMULA_TEMPLATES0_TAR.TEMPLATE_ID AS TEMPLATE_ID,
         LANG.LANGUAGE_CODE AS LANGUAGE,
         FF_FORMULA_TEMPLATES_TL_SRC.NAME AS NAME,
         FF_FORMULA_TEMPLATES_TL_SRC.SOURCE_LANG AS SOURCE_LANG,
         FF_FORMULA_TEMPLATES_TL_SRC.DESCRIPTION AS DESCRIPTION,
         FF_FORMULA_TEMPLATES_TL_SRC.OBJECT_VERSION_NUMBER AS OBJECT_VERSION_NUMBER,
         FF_FORMULA_TEMPLATES_TL_SRC.ENTERPRISE_ID AS ENTERPRISE_ID
         FROM &l_seed_schema..FF_FORMULA_TEMPLATES_TL FF_FORMULA_TEMPLATES_TL_SRC
            JOIN
            (SELECT
            FF_FORMULA_TEMPLATES_TAR.TEMPLATE_ID TEMPLATE_ID,
            FF_FORMULA_TEMPLATES_SRC.TEMPLATE_ID TEMPLATE_ID_SRC
            FROM &l_seed_schema..FF_FORMULA_TEMPLATES FF_FORMULA_TEMPLATES_SRC,
                 &l_fusion_schema..FF_FORMULA_TEMPLATES FF_FORMULA_TEMPLATES_TAR
            WHERE FF_FORMULA_TEMPLATES_SRC.TEMPLATE_CODE = FF_FORMULA_TEMPLATES_TAR.TEMPLATE_CODE
            AND FF_FORMULA_TEMPLATES_SRC.ENTERPRISE_ID = FF_FORMULA_TEMPLATES_TAR.ENTERPRISE_ID) FF_FORMULA_TEMPLATES0_TAR
            ON FF_FORMULA_TEMPLATES_TL_SRC.TEMPLATE_ID = FF_FORMULA_TEMPLATES0_TAR.TEMPLATE_ID_SRC
            ,&l_fusion_schema..FND_LANGUAGES_B LANG
                WHERE FF_FORMULA_TEMPLATES_TL_SRC.LANGUAGE = 'US'
                AND FF_FORMULA_TEMPLATES_TL_SRC.LANGUAGE = LANG.LANGUAGE_CODE
                AND LANG.INSTALLED_FLAG IN ('B', 'I')
) QUERY_SRC
      ON ( QUERY_SRC.TEMPLATE_ID = QUERY_TAR.TEMPLATE_ID
         AND QUERY_SRC.LANGUAGE = QUERY_TAR.LANGUAGE)
      WHEN MATCHED THEN UPDATE SET
         SEED_DATA_SOURCE = QUERY_SRC.SEED_DATA_SOURCE,
         NAME = QUERY_SRC.NAME,
         SOURCE_LANG = QUERY_SRC.SOURCE_LANG,
         DESCRIPTION = QUERY_SRC.DESCRIPTION,
         OBJECT_VERSION_NUMBER = QUERY_SRC.OBJECT_VERSION_NUMBER,
         LAST_UPDATE_DATE = systimestamp,
         LAST_UPDATE_LOGIN = -1,
         LAST_UPDATED_BY = 'SEED_DATA_FROM_APPLICATION',
         ENTERPRISE_ID = QUERY_SRC.ENTERPRISE_ID
         WHERE QUERY_TAR.last_updated_by IN ('SEED_DATA_FROM_APPLICATION', '0')
         AND   QUERY_TAR.SOURCE_LANG = QUERY_SRC.SOURCE_LANG
      WHEN NOT MATCHED THEN INSERT (
         SEED_DATA_SOURCE,
         TEMPLATE_ID,
         LANGUAGE,
         NAME,
         SOURCE_LANG,
         DESCRIPTION,
         OBJECT_VERSION_NUMBER,
         CREATED_BY,
         CREATION_DATE,
         LAST_UPDATE_DATE,
         LAST_UPDATE_LOGIN,
         LAST_UPDATED_BY,
         ENTERPRISE_ID      )
      VALUES (
         QUERY_SRC.SEED_DATA_SOURCE,
         QUERY_SRC.TEMPLATE_ID,
         QUERY_SRC.LANGUAGE,
         QUERY_SRC.NAME,
         QUERY_SRC.SOURCE_LANG,
         QUERY_SRC.DESCRIPTION,
         QUERY_SRC.OBJECT_VERSION_NUMBER,
         'SEED_DATA_FROM_APPLICATION',
         systimestamp,
         systimestamp,
         -1,
         'SEED_DATA_FROM_APPLICATION',
         QUERY_SRC.ENTERPRISE_ID      )
      LOG ERRORS INTO &l_fusion_schema..FND_BULK_MERGE_ERRORS ('FF_FORMULA_TEMPLATES_TL::'||to_char(sysdate)) REJECT LIMIT UNLIMITED;

      dbms_output.put_line('      MERGED=>'||TO_CHAR(SQL%ROWCOUNT));

      end_date := sysdate;
      dbms_output.put_line('Start :'||to_char(start_date, 'SSSSS'));
      dbms_output.put_line('End   :'||to_char(end_date, 'SSSSS'));
      dbms_output.put_line('Elapsed sec :'||(to_number(to_char(end_date, 'SSSSS')) - to_number(to_char(start_date, 'SSSSS'))));
      fnd_bulk_seed.end_processing(l_script_name);
   $ELSE

      dbms_output.put_line('Bulk mode is NOT enabled so script has been skipped');

   $END

   null;
    exception
      when others then begin
      fnd_bulk_seed.record_error(l_script_name, sqlerrm);
      raise;
    end;
end;
/

commit;
exit;
