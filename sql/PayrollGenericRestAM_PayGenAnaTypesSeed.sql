REM $Header: fusionapps/hcm/pay/db/sql/bulkseed/PayrollGenericRestAM_PayGenAnaTypesSeed.sql xiaojuch_bug-36928092/1 2024/08/10 20:56:54 xiaojuch Exp $
REM
REM PayrollGenericRestAM_PayGenAnaTypesSeed.sql  &&1  &&2
REM &&1 = source schema (ie. fusion_seed)   &&2 = target schema (ie. fusion)
REM
REM Copyright (c) 2007, 2024, Oracle and/or its affiliates.
REM All rights reserved.
REM
REM    NAME
REM          PayrollGenericRestAM_PayGenAnaTypesSeed.sql
REM
REM    DESCRIPTION
REM      Script to copy seed data from fusion_seed schema to fusion
REM
REM    NOTES
REM
REM    MODIFIED (MM/DD/YYYY)
REM    Initially generated 10-Dec-20
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
   -- adm_ddl.GET_SCHEMA_NAME( P_NAME => 'FUSION' , X_SCHEMA_NAME => :fusion_schema);
   -- :seed_schema := <atg function to return the FUSION_SEED schema name>
   -- ATG can then call the ADM_DDL script to get the schema name once it is seeded in LCM. For now, you can hard-code it within the ATG function

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

PROMPT Starting script -> PayrollGenericRestAM_PayGenAnaTypesSeed.sql

declare

l_fusion_schema varchar2(30) := upper('&1');
l_stage_schema varchar2(30) := 'FUSION_SEED';
l_batch_size   number       := &3;
l_record_count number;
start_date date;
end_date   date;
l_script_name varchar2(4000) := '$Source: fusionapps/hcm/pay/db/sql/bulkseed/PayrollGenericRestAM_PayGenAnaTypesSeed.sql $';
l_table_list fnd_bulk_seed.table_list_type := fnd_bulk_seed.table_list_type('PAY_GENERIC_ANA_TYPES','PAY_GENERIC_ANA_TYPES_TL','PAY_GENERIC_ANA_ATT_DEFS','PAY_GENERIC_ANA_ATT_DEFS_TL');

begin

    $IF $$BULKPROCESSENABLED = TRUE $THEN

      start_date := sysdate;
      if (not fnd_bulk_seed.start_processing(l_script_name, l_table_list)) then
        dbms_output.put_line('Script already processed, aborting.');
        return;
      end if;

      dbms_output.put_line('---------- PAY_GENERIC_ANA_TYPES ----------');
   IF (NVL(fnd_profile.value('ORA_FND_BULK_SQL_MODE'), 'SDF') = 'BULKTEST') THEN
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..PAY_GENERIC_ANA_TYPES;
      dbms_output.put_line('   FUSION_SEED ROWS=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..PAY_GENERIC_ANA_TYPES
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION_SEED != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..PAY_GENERIC_ANA_TYPES
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..PAY_GENERIC_ANA_TYPES;
      dbms_output.put_line('        FUSION ROWS=>'||TO_CHAR(l_record_count));
   END IF;

      MERGE INTO &l_fusion_schema..PAY_GENERIC_ANA_TYPES QUERY_TAR
      USING (SELECT 
         PAY_GENERIC_ANA_TYPES_SRC.SEED_DATA_SOURCE AS SEED_DATA_SOURCE,
         PAY_GENERIC_ANA_TYPES_SRC.PAY_GEN_ANA_TYPE_ID AS PAY_GEN_ANA_TYPE_ID,
         PAY_GENERIC_ANA_TYPES_SRC.BASE_GEN_ANA_TYPE AS BASE_GEN_ANA_TYPE,
         PAY_GENERIC_ANA_TYPES_SRC.ANALYTICS_CATEGORY AS ANALYTICS_CATEGORY,
         PAY_GENERIC_ANA_TYPES_SRC.LEGISLATIVE_DATA_GROUP_ID AS LEGISLATIVE_DATA_GROUP_ID,
         PAY_GENERIC_ANA_TYPES_SRC.LEGISLATION_CODE AS LEGISLATION_CODE,
         PAY_GENERIC_ANA_TYPES_SRC.OBJECT_VERSION_NUMBER AS OBJECT_VERSION_NUMBER,
         PAY_GENERIC_ANA_TYPES_SRC.METHOD_TYPE AS METHOD_TYPE,
         PAY_GENERIC_ANA_TYPES_SRC.METHOD AS METHOD,
         PAY_GENERIC_ANA_TYPES_SRC.MODULE_ID AS MODULE_ID,
         PAY_GENERIC_ANA_TYPES_SRC.ENTERPRISE_ID AS ENTERPRISE_ID
         FROM &l_seed_schema..PAY_GENERIC_ANA_TYPES PAY_GENERIC_ANA_TYPES_SRC 
      ) QUERY_SRC
      ON (QUERY_SRC.BASE_GEN_ANA_TYPE = QUERY_TAR.BASE_GEN_ANA_TYPE
      AND NVL(QUERY_SRC.LEGISLATION_CODE, '_NULL_') = NVL(QUERY_TAR.LEGISLATION_CODE, '_NULL_')
      )
      WHEN MATCHED THEN UPDATE SET 
         SEED_DATA_SOURCE = QUERY_SRC.SEED_DATA_SOURCE,
         --LEGISLATIVE_DATA_GROUP_ID = QUERY_SRC.LEGISLATIVE_DATA_GROUP_ID,
         --LEGISLATION_CODE = QUERY_SRC.LEGISLATION_CODE,
         OBJECT_VERSION_NUMBER = QUERY_SRC.OBJECT_VERSION_NUMBER,
         ENTERPRISE_ID = QUERY_SRC.ENTERPRISE_ID,
         METHOD_TYPE = QUERY_SRC.METHOD_TYPE,
         METHOD = QUERY_SRC.METHOD,
         LAST_UPDATE_DATE = systimestamp,
         LAST_UPDATE_LOGIN = -1,
         LAST_UPDATED_BY = 'SEED_DATA_FROM_APPLICATION',
         MODULE_ID = QUERY_SRC.MODULE_ID
         WHERE last_updated_by IN ('SEED_DATA_FROM_APPLICATION', '0')
      WHEN NOT MATCHED THEN INSERT (
         SEED_DATA_SOURCE,
         PAY_GEN_ANA_TYPE_ID,
         BASE_GEN_ANA_TYPE,
         ANALYTICS_CATEGORY,
         LEGISLATIVE_DATA_GROUP_ID,
         LEGISLATION_CODE,
         METHOD_TYPE,
         METHOD,
         CREATED_BY,
         CREATION_DATE,
         LAST_UPDATE_DATE,
         LAST_UPDATE_LOGIN,
         LAST_UPDATED_BY,
         MODULE_ID,
         ENTERPRISE_ID,
         OBJECT_VERSION_NUMBER
         )
      VALUES (
         QUERY_SRC.SEED_DATA_SOURCE,
         S_ROW_ID_SEQ.NEXTVAL,
         QUERY_SRC.BASE_GEN_ANA_TYPE,
         QUERY_SRC.ANALYTICS_CATEGORY,
         QUERY_SRC.LEGISLATIVE_DATA_GROUP_ID,
         QUERY_SRC.LEGISLATION_CODE,
         QUERY_SRC.METHOD_TYPE,
         QUERY_SRC.METHOD,
         'SEED_DATA_FROM_APPLICATION',
         systimestamp,
         systimestamp,
         -1,
         'SEED_DATA_FROM_APPLICATION',
         QUERY_SRC.MODULE_ID,
         QUERY_SRC.ENTERPRISE_ID,
         QUERY_SRC.OBJECT_VERSION_NUMBER
         )
      LOG ERRORS INTO &l_fusion_schema..FND_BULK_MERGE_ERRORS ('PAY_GENERIC_ANA_TYPES::'||to_char(sysdate)) REJECT LIMIT UNLIMITED;

   dbms_output.put_line('PAY_GENERIC_ANA_TYPES      MERGED=>'||TO_CHAR(SQL%ROWCOUNT));
   


      dbms_output.put_line('---------- PAY_GENERIC_ANA_TYPES_TL ----------');
   IF (NVL(fnd_profile.value('ORA_FND_BULK_SQL_MODE'), 'SDF') = 'BULKTEST') THEN
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..PAY_GENERIC_ANA_TYPES_TL;
      dbms_output.put_line('   FUSION_SEED ROWS=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..PAY_GENERIC_ANA_TYPES_TL
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION_SEED != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..PAY_GENERIC_ANA_TYPES_TL
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..PAY_GENERIC_ANA_TYPES_TL;
      dbms_output.put_line('        FUSION ROWS=>'||TO_CHAR(l_record_count));
   END IF;

      MERGE INTO &l_fusion_schema..PAY_GENERIC_ANA_TYPES_TL QUERY_TAR
      USING (SELECT 
         GENERIC_ANA_TYPES_TL_SRC.SEED_DATA_SOURCE AS SEED_DATA_SOURCE,
         GENERIC_ANA_TYPES0_TAR.PAY_GEN_ANA_TYPE_ID AS PAY_GEN_ANA_TYPE_ID,
         LANG.LANGUAGE_CODE AS LANGUAGE,
         GENERIC_ANA_TYPES_TL_SRC.GEN_ANA_TYPE_NAME AS GEN_ANA_TYPE_NAME,
         GENERIC_ANA_TYPES_TL_SRC.SOURCE_LANG AS SOURCE_LANG,
         GENERIC_ANA_TYPES_TL_SRC.DESCRIPTION AS DESCRIPTION,
         GENERIC_ANA_TYPES_TL_SRC.OBJECT_VERSION_NUMBER AS OBJECT_VERSION_NUMBER,
         GENERIC_ANA_TYPES_TL_SRC.ENTERPRISE_ID AS ENTERPRISE_ID
         FROM &l_seed_schema..PAY_GENERIC_ANA_TYPES_TL GENERIC_ANA_TYPES_TL_SRC 
            JOIN
            (SELECT 
            GENERIC_ANA_TYPES_TAR.PAY_GEN_ANA_TYPE_ID PAY_GEN_ANA_TYPE_ID,
            GENERIC_ANA_TYPES_SRC.PAY_GEN_ANA_TYPE_ID PAY_GEN_ANA_TYPE_ID_SRC
            FROM &l_seed_schema..PAY_GENERIC_ANA_TYPES GENERIC_ANA_TYPES_SRC,
                 &l_fusion_schema..PAY_GENERIC_ANA_TYPES GENERIC_ANA_TYPES_TAR
            WHERE GENERIC_ANA_TYPES_SRC.BASE_GEN_ANA_TYPE = GENERIC_ANA_TYPES_TAR.BASE_GEN_ANA_TYPE
            AND GENERIC_ANA_TYPES_SRC.ENTERPRISE_ID = GENERIC_ANA_TYPES_TAR.ENTERPRISE_ID
            AND NVL(GENERIC_ANA_TYPES_SRC.LEGISLATION_CODE, '_NULL_') = NVL(GENERIC_ANA_TYPES_TAR.LEGISLATION_CODE, '_NULL_')
            AND NVL(GENERIC_ANA_TYPES_SRC.LEGISLATIVE_DATA_GROUP_ID, 1) = NVL(GENERIC_ANA_TYPES_TAR.LEGISLATIVE_DATA_GROUP_ID, 1)) GENERIC_ANA_TYPES0_TAR
            ON GENERIC_ANA_TYPES_TL_SRC.PAY_GEN_ANA_TYPE_ID = GENERIC_ANA_TYPES0_TAR.PAY_GEN_ANA_TYPE_ID_SRC
            ,&l_fusion_schema..FND_LANGUAGES_B LANG
                WHERE GENERIC_ANA_TYPES_TL_SRC.LANGUAGE = 'US'
                AND GENERIC_ANA_TYPES_TL_SRC.LANGUAGE = LANG.LANGUAGE_CODE
                AND LANG.INSTALLED_FLAG IN ('B', 'I')
) QUERY_SRC
      ON ( QUERY_SRC.PAY_GEN_ANA_TYPE_ID = QUERY_TAR.PAY_GEN_ANA_TYPE_ID
         AND QUERY_SRC.LANGUAGE = QUERY_TAR.LANGUAGE)
      WHEN MATCHED THEN UPDATE SET 
         SEED_DATA_SOURCE = QUERY_SRC.SEED_DATA_SOURCE,
         GEN_ANA_TYPE_NAME = QUERY_SRC.GEN_ANA_TYPE_NAME,
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
         PAY_GEN_ANA_TYPE_ID,
         LANGUAGE,
         GEN_ANA_TYPE_NAME,
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
         QUERY_SRC.PAY_GEN_ANA_TYPE_ID,
         QUERY_SRC.LANGUAGE,
         QUERY_SRC.GEN_ANA_TYPE_NAME,
         QUERY_SRC.SOURCE_LANG,
         QUERY_SRC.DESCRIPTION,
         QUERY_SRC.OBJECT_VERSION_NUMBER,
         'SEED_DATA_FROM_APPLICATION',
         systimestamp,
         systimestamp,
         -1,
         'SEED_DATA_FROM_APPLICATION',
         QUERY_SRC.ENTERPRISE_ID      )
      LOG ERRORS INTO &l_fusion_schema..FND_BULK_MERGE_ERRORS ('PAY_GENERIC_ANA_TYPES_TL::'||to_char(sysdate)) REJECT LIMIT UNLIMITED;

      dbms_output.put_line('      MERGED=>'||TO_CHAR(SQL%ROWCOUNT));



dbms_output.put_line('---------- PAY_GENERIC_ANA_ATT_DEFS ----------');
   IF (NVL(fnd_profile.value('ORA_FND_BULK_SQL_MODE'), 'SDF') = 'BULKTEST') THEN
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..PAY_GENERIC_ANA_ATT_DEFS;
      dbms_output.put_line('   FUSION_SEED ROWS=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..PAY_GENERIC_ANA_ATT_DEFS
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION_SEED != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..PAY_GENERIC_ANA_ATT_DEFS
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..PAY_GENERIC_ANA_ATT_DEFS;
      dbms_output.put_line('        FUSION ROWS=>'||TO_CHAR(l_record_count));
   END IF;


MERGE INTO &l_fusion_schema..PAY_GENERIC_ANA_ATT_DEFS QUERY_TAR
      USING  (
         SELECT
        PAY_GENERIC_ANA_TYPES_TAR.PAY_GEN_ANA_TYPE_ID,
        PAY_GENERIC_ANA_ATT_DEFS_TAR.PAY_GEN_ANA_ATTDEF_ID,
        PAY_GENERIC_ANA_ATT_DEFS_SRC.SEED_DATA_SOURCE,
        PAY_GENERIC_ANA_ATT_DEFS_SRC.MODULE_ID,
        PAY_GENERIC_ANA_ATT_DEFS_SRC.BASE_ATTRDEF_NAME as BASE_ATTRDEF_NAME,
        PAY_GENERIC_ANA_ATT_DEFS_SRC.DATA_COLUMN as DATA_COLUMN,
        PAY_GENERIC_ANA_ATT_DEFS_SRC.DISPLAY_SEQUENCE as DISPLAY_SEQUENCE,
        PAY_GENERIC_ANA_ATT_DEFS_SRC.DISPLAY_LABEL as DISPLAY_LABEL,
        PAY_GENERIC_ANA_ATT_DEFS_SRC.OBJECT_VERSION_NUMBER as OBJECT_VERSION_NUMBER,
        PAY_GENERIC_ANA_ATT_DEFS_SRC.ENTERPRISE_ID as ENTERPRISE_ID
        FROM
        &l_fusion_schema..PAY_GENERIC_ANA_ATT_DEFS PAY_GENERIC_ANA_ATT_DEFS_TAR,
        &l_fusion_schema..PAY_GENERIC_ANA_TYPES PAY_GENERIC_ANA_TYPES_TAR,
        (SELECT
         PAY_GENERIC_ANA_ATT_DEFS.SEED_DATA_SOURCE AS SEED_DATA_SOURCE,
         PAY_GENERIC_ANA_ATT_DEFS.MODULE_ID AS MODULE_ID,
         PAY_GENERIC_ANA_ATT_DEFS.BASE_ATTRDEF_NAME as BASE_ATTRDEF_NAME,
         PAY_GENERIC_ANA_ATT_DEFS.DATA_COLUMN as DATA_COLUMN,
         PAY_GENERIC_ANA_ATT_DEFS.DISPLAY_SEQUENCE as DISPLAY_SEQUENCE,
         PAY_GENERIC_ANA_ATT_DEFS.DISPLAY_LABEL as DISPLAY_LABEL,
         PAY_GENERIC_ANA_ATT_DEFS.OBJECT_VERSION_NUMBER as OBJECT_VERSION_NUMBER,
         PAY_GENERIC_ANA_ATT_DEFS.ENTERPRISE_ID as ENTERPRISE_ID,
         PAY_GENERIC_ANA_ATT_DEFS.PAY_GEN_ANA_ATTDEF_ID AS PAY_GEN_ANA_ATTDEF_ID,
         PAY_GENERIC_ANA_TYPES.PAY_GEN_ANA_TYPE_ID AS PAY_GEN_ANA_TYPE_ID,
         PAY_GENERIC_ANA_TYPES.BASE_GEN_ANA_TYPE AS BASE_GEN_ANA_TYPE,
         PAY_GENERIC_ANA_TYPES.LEGISLATION_CODE AS LEGISLATION_CODE
         FROM &l_seed_schema..PAY_GENERIC_ANA_ATT_DEFS, &l_seed_schema..PAY_GENERIC_ANA_TYPES
         where PAY_GENERIC_ANA_ATT_DEFS.PAY_GEN_ANA_TYPE_ID = PAY_GENERIC_ANA_TYPES.PAY_GEN_ANA_TYPE_ID) PAY_GENERIC_ANA_ATT_DEFS_SRC
         where PAY_GENERIC_ANA_ATT_DEFS_SRC.BASE_ATTRDEF_NAME = PAY_GENERIC_ANA_ATT_DEFS_TAR.BASE_ATTRDEF_NAME (+)
         and PAY_GENERIC_ANA_ATT_DEFS_SRC.BASE_GEN_ANA_TYPE = PAY_GENERIC_ANA_TYPES_TAR.BASE_GEN_ANA_TYPE
         and PAY_GENERIC_ANA_TYPES_TAR.PAY_GEN_ANA_TYPE_ID = PAY_GENERIC_ANA_ATT_DEFS_TAR.PAY_GEN_ANA_TYPE_ID (+)
         and NVL(PAY_GENERIC_ANA_TYPES_TAR.LEGISLATION_CODE, '_NULL_') = NVL(PAY_GENERIC_ANA_TYPES_TAR.LEGISLATION_CODE, '_NULL_')
      ) QUERY_SRC
      ON (QUERY_SRC.PAY_GEN_ANA_ATTDEF_ID = QUERY_TAR.PAY_GEN_ANA_ATTDEF_ID)
      WHEN MATCHED THEN UPDATE SET
         SEED_DATA_SOURCE = QUERY_SRC.SEED_DATA_SOURCE,
         LAST_UPDATE_DATE = systimestamp,
         LAST_UPDATE_LOGIN = -1,
         LAST_UPDATED_BY = 'SEED_DATA_FROM_APPLICATION',
         MODULE_ID = QUERY_SRC.MODULE_ID,
         BASE_ATTRDEF_NAME = QUERY_SRC.BASE_ATTRDEF_NAME,
         DATA_COLUMN = QUERY_SRC.DATA_COLUMN,
         DISPLAY_SEQUENCE = QUERY_SRC.DISPLAY_SEQUENCE,
         DISPLAY_LABEL = QUERY_SRC.DISPLAY_LABEL,
         OBJECT_VERSION_NUMBER = QUERY_SRC.OBJECT_VERSION_NUMBER,
         ENTERPRISE_ID = QUERY_SRC.ENTERPRISE_ID
         WHERE last_updated_by IN ('SEED_DATA_FROM_APPLICATION', '0')
      WHEN NOT MATCHED THEN INSERT (
         SEED_DATA_SOURCE,
         PAY_GEN_ANA_ATTDEF_ID,
         PAY_GEN_ANA_TYPE_ID,
         BASE_ATTRDEF_NAME,
         DATA_COLUMN,
         DISPLAY_SEQUENCE,
         DISPLAY_LABEL,
         OBJECT_VERSION_NUMBER,
         ENTERPRISE_ID,
         CREATED_BY,
         CREATION_DATE,
         LAST_UPDATE_DATE,
         LAST_UPDATE_LOGIN,
         LAST_UPDATED_BY,
         MODULE_ID
         )
      VALUES (
         QUERY_SRC.SEED_DATA_SOURCE,
         S_ROW_ID_SEQ.NEXTVAL,
         QUERY_SRC.PAY_GEN_ANA_TYPE_ID,
         QUERY_SRC.BASE_ATTRDEF_NAME,
         QUERY_SRC.DATA_COLUMN,
         QUERY_SRC.DISPLAY_SEQUENCE,
         QUERY_SRC.DISPLAY_LABEL,
         QUERY_SRC.OBJECT_VERSION_NUMBER,
         QUERY_SRC.ENTERPRISE_ID,
         'SEED_DATA_FROM_APPLICATION',
         systimestamp,
         systimestamp,
         -1,
         'SEED_DATA_FROM_APPLICATION',
         QUERY_SRC.MODULE_ID
         )
      LOG ERRORS INTO &l_fusion_schema..FND_BULK_MERGE_ERRORS ('PAY_GENERIC_ANA_ATT_DEFS::'||to_char(sysdate)) REJECT LIMIT UNLIMITED;

      dbms_output.put_line('PAY_GENERIC_ANA_ATT_DEFS      MERGED=>'||TO_CHAR(SQL%ROWCOUNT));


      dbms_output.put_line('---------- PAY_GENERIC_ANA_ATT_DEFS_TL ----------');
   IF (NVL(fnd_profile.value('ORA_FND_BULK_SQL_MODE'), 'SDF') = 'BULKTEST') THEN
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..PAY_GENERIC_ANA_ATT_DEFS_TL;
      dbms_output.put_line('   FUSION_SEED ROWS=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_seed_schema..PAY_GENERIC_ANA_ATT_DEFS_TL
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION_SEED != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..PAY_GENERIC_ANA_ATT_DEFS_TL
WHERE last_updated_by NOT IN ('SEED_DATA_FROM_APPLICATION', '0');
      dbms_output.put_line('FUSION != SEED_FROM_APPL=>'||TO_CHAR(l_record_count));
      SELECT COUNT(1) INTO l_record_count FROM &l_fusion_schema..PAY_GENERIC_ANA_ATT_DEFS_TL;
      dbms_output.put_line('        FUSION ROWS=>'||TO_CHAR(l_record_count));
   END IF;

      MERGE INTO &l_fusion_schema..PAY_GENERIC_ANA_ATT_DEFS_TL QUERY_TAR
      USING (SELECT 
         GENERIC_ANA_ATT_DEFS_TL_SRC.SEED_DATA_SOURCE AS SEED_DATA_SOURCE,
         GENERIC_ANA_ATT_DEFS0_TAR.PAY_GEN_ANA_ATTDEF_ID AS PAY_GEN_ANA_ATTDEF_ID,
         LANG.LANGUAGE_CODE AS LANGUAGE,
         GENERIC_ANA_ATT_DEFS_TL_SRC.PAY_GEN_ANA_ATTDEF_NAME AS PAY_GEN_ANA_ATTDEF_NAME,
         GENERIC_ANA_ATT_DEFS_TL_SRC.SOURCE_LANG AS SOURCE_LANG,
         GENERIC_ANA_ATT_DEFS_TL_SRC.DESCRIPTION AS DESCRIPTION,
         GENERIC_ANA_ATT_DEFS_TL_SRC.OBJECT_VERSION_NUMBER AS OBJECT_VERSION_NUMBER,
         GENERIC_ANA_ATT_DEFS_TL_SRC.ENTERPRISE_ID AS ENTERPRISE_ID
         FROM &l_seed_schema..PAY_GENERIC_ANA_ATT_DEFS_TL GENERIC_ANA_ATT_DEFS_TL_SRC 
            JOIN
            (SELECT 
            GENERIC_ANA_ATT_DEFS_TAR.PAY_GEN_ANA_ATTDEF_ID PAY_GEN_ANA_ATTDEF_ID,
            GENERIC_ANA_ATT_DEFS_SRC.PAY_GEN_ANA_ATTDEF_ID PAY_GEN_ANA_ATTDEF_ID_SRC
            FROM &l_seed_schema..PAY_GENERIC_ANA_ATT_DEFS GENERIC_ANA_ATT_DEFS_SRC,
                 &l_seed_schema..PAY_GENERIC_ANA_TYPES GENERIC_ANA_TYPES_SRC,
                 &l_fusion_schema..PAY_GENERIC_ANA_TYPES GENERIC_ANA_TYPES_TAR,
                 &l_fusion_schema..PAY_GENERIC_ANA_ATT_DEFS GENERIC_ANA_ATT_DEFS_TAR
            WHERE GENERIC_ANA_ATT_DEFS_SRC.BASE_ATTRDEF_NAME = GENERIC_ANA_ATT_DEFS_TAR.BASE_ATTRDEF_NAME
            AND GENERIC_ANA_TYPES_SRC.ENTERPRISE_ID = GENERIC_ANA_TYPES_TAR.ENTERPRISE_ID
            AND NVL(GENERIC_ANA_TYPES_SRC.LEGISLATION_CODE, '_NULL_') = NVL(GENERIC_ANA_TYPES_TAR.LEGISLATION_CODE, '_NULL_')
            AND NVL(GENERIC_ANA_TYPES_SRC.LEGISLATIVE_DATA_GROUP_ID, 1) = NVL(GENERIC_ANA_TYPES_TAR.LEGISLATIVE_DATA_GROUP_ID, 1)
            AND GENERIC_ANA_TYPES_SRC.BASE_GEN_ANA_TYPE = GENERIC_ANA_TYPES_TAR.BASE_GEN_ANA_TYPE
            AND GENERIC_ANA_TYPES_SRC.PAY_GEN_ANA_TYPE_ID = GENERIC_ANA_ATT_DEFS_SRC.PAY_GEN_ANA_TYPE_ID
            AND GENERIC_ANA_TYPES_TAR.PAY_GEN_ANA_TYPE_ID = GENERIC_ANA_ATT_DEFS_TAR.PAY_GEN_ANA_TYPE_ID) GENERIC_ANA_ATT_DEFS0_TAR
            ON GENERIC_ANA_ATT_DEFS_TL_SRC.PAY_GEN_ANA_ATTDEF_ID = GENERIC_ANA_ATT_DEFS0_TAR.PAY_GEN_ANA_ATTDEF_ID_SRC
            ,&l_fusion_schema..FND_LANGUAGES_B LANG
                WHERE GENERIC_ANA_ATT_DEFS_TL_SRC.LANGUAGE = 'US'
                AND GENERIC_ANA_ATT_DEFS_TL_SRC.LANGUAGE = LANG.LANGUAGE_CODE
                AND LANG.INSTALLED_FLAG IN ('B', 'I')
) QUERY_SRC
      ON ( QUERY_SRC.PAY_GEN_ANA_ATTDEF_ID = QUERY_TAR.PAY_GEN_ANA_ATTDEF_ID
         AND QUERY_SRC.LANGUAGE = QUERY_TAR.LANGUAGE)
      WHEN MATCHED THEN UPDATE SET 
         SEED_DATA_SOURCE = QUERY_SRC.SEED_DATA_SOURCE,
         PAY_GEN_ANA_ATTDEF_NAME = QUERY_SRC.PAY_GEN_ANA_ATTDEF_NAME,
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
         PAY_GEN_ANA_ATTDEF_ID,
         LANGUAGE,
         PAY_GEN_ANA_ATTDEF_NAME,
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
         QUERY_SRC.PAY_GEN_ANA_ATTDEF_ID,
         QUERY_SRC.LANGUAGE,
         QUERY_SRC.PAY_GEN_ANA_ATTDEF_NAME,
         QUERY_SRC.SOURCE_LANG,
         QUERY_SRC.DESCRIPTION,
         QUERY_SRC.OBJECT_VERSION_NUMBER,
         'SEED_DATA_FROM_APPLICATION',
         systimestamp,
         systimestamp,
         -1,
         'SEED_DATA_FROM_APPLICATION',
         QUERY_SRC.ENTERPRISE_ID      )
      LOG ERRORS INTO &l_fusion_schema..FND_BULK_MERGE_ERRORS ('PAY_GENERIC_ANA_ATT_DEFS_TL::'||to_char(sysdate)) REJECT LIMIT UNLIMITED;

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
