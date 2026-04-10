# FF_FORMULA_TEMPLATES — Table Design

**Date:** 2026-04-08
**Status:** Draft — awaiting user review
**Owner:** Alex Chen

## Goal

Introduce a persistent store for Fast Formula templates so the system can serve both
Oracle-seeded samples and user-saved templates from the database instead of the current
`custom_formulas.json` file.

## Decisions

| # | Decision | Chosen | Rationale |
|---|---|---|---|
| 1 | Purpose | Both seeded + user-created in one table | Unified retrieval, single API path |
| 2 | Ownership scope | Globally shared | Simplest model; no per-user/tenant filtering |
| 3 | Translation | Multilingual MLS pair (`<table>` + `<table>_tl` + `<TABLE>_VL`) | Matches Fusion HCM convention (base table has no `_B` suffix — see sample `pay_generic_ana_types`) |
| 4 | Formula type binding | 1:1 via `FORMULA_TYPE_ID` FK (nullable) | Users browse templates filtered by formula type; `NULL` marks the template as belonging to the built-in Custom type |
| 5 | Source discriminator | `SOURCE_TYPE` column (`SEEDED` / `USER_CREATED`) | Clearer than a boolean flag, extensible |
| 6 | Seed data framework | Full Fusion SDF columns (`SEED_DATA_SOURCE`, `MODULE_ID`, `ORA_SEED_SET1`, `ORA_SEED_SET2`, `ENTERPRISE_ID`) | Follows Fusion sample `pay_generic_ana_types` exactly so seed extract/apply works without custom wiring |
| 7 | File format | Fusion composite XML (`<COMPOSITE xmlns="http://xmlns.oracle.com/ku">`) | Matches sample in `sample/` directory, deployable via `oracle.apps.fnd.applxdf.comp.XdfSchemaDeploy` |

## Tables

### FF_FORMULA_TEMPLATES (Base — file `ff_formula_templates.table`)

Language-independent columns. **Note**: the base table name has no `_B` suffix, following
the Fusion MLS naming convention (e.g. sample `pay_generic_ana_types` + `pay_generic_ana_types_tl`).

| # | Column | Type | Null | Default | Notes |
|---|---|---|---|---|---|
| 1 | `TEMPLATE_ID` | `NUMBER(18,0)` | NOT NULL | — | PK, populated by the EO layer (no DB sequence; the Java EO assigns the ID before insert) |
| 2 | `FORMULA_TYPE_ID` | `NUMBER(18,0)` | NULL | — | FK → `FF_FORMULA_TYPES.FORMULA_TYPE_ID`. `NULL` = built-in Custom type |
| 3 | `TEMPLATE_CODE` | `VARCHAR2(150)` | NOT NULL | — | Unique machine-readable code (e.g. `ORA_OVERTIME_PAY_CALC`) |
| 4 | `FORMULA_TEXT` | `CLOB` | NULL | — | Fast Formula source code. NULL is allowed for templates that only provide prompt guidance without a starter code body |
| 5 | `ADDITIONAL_PROMPT_TEXT` | `CLOB` | NULL | — | Template-specific guidance appended to the AI system prompt |
| 6 | `SOURCE_TYPE` | `VARCHAR2(30)` | NOT NULL | — | `SEEDED` or `USER_CREATED` |
| 7 | `ACTIVE_FLAG` | `VARCHAR2(1)` | NOT NULL | `'Y'` | `Y` / `N` |
| 8 | `SEMANTIC_FLAG` | `VARCHAR2(1)` | NOT NULL | `'Y'` | `Y` = participates in semantic (vector-embedding) search via the RAG pipeline; `N` = excluded from semantic retrieval |
| 9 | `SORT_ORDER` | `NUMBER(9,0)` | NULL | — | Display order within a formula type |
| 10 | `OBJECT_VERSION_NUMBER` | `NUMBER(9,0)` | NOT NULL | `1` | Optimistic lock |
| 11 | `LAST_UPDATE_DATE` | `TIMESTAMP(6)` | NOT NULL | — | WHO column |
| 12 | `CREATED_BY` | `VARCHAR2(64)` | NOT NULL | — | WHO column |
| 13 | `CREATION_DATE` | `TIMESTAMP(6)` | NOT NULL | — | WHO column |
| 14 | `LAST_UPDATED_BY` | `VARCHAR2(64)` | NOT NULL | — | WHO column |
| 15 | `LAST_UPDATE_LOGIN` | `VARCHAR2(32)` | NULL | — | WHO column |
| 16 | `ENTERPRISE_ID` | `NUMBER(18,0)` | NOT NULL | `nvl(SYS_CONTEXT('FND_VPD_CTX','FND_ENTERPRISE_ID'), 0)` | Fusion VPD multi-tenant key |
| 17 | `SEED_DATA_SOURCE` | `VARCHAR2(512)` | NOT NULL | `SYS_CONTEXT('FND_SDF_CTX', 'SDFFILE')` | Seed Data Framework source file |
| 18 | `MODULE_ID` | `VARCHAR2(32)` | NULL | — | Fusion module owning this row (FK → `FND_APPL_TAXONOMY.MODULE_ID`). Required for seed extract |
| 19 | `ORA_SEED_SET1` | `VARCHAR2(1)` | NOT NULL | `'Y'` | EBR (Edition-Based Redefinition) context for SET1. Oracle internal |
| 20 | `ORA_SEED_SET2` | `VARCHAR2(1)` | NOT NULL | `'Y'` | EBR context for SET2. Oracle internal |

**Total: 20 columns** (9 business + 1 lock + 5 WHO + 5 seed/EBR framework)

### FF_FORMULA_TEMPLATES_TL (Translation — file `ff_formula_templates_tl.table`)

Per-language display strings. `NAME` and `DESCRIPTION` are marked `translateFlag=Y`.

| # | Column | Type | Null | Default | Notes |
|---|---|---|---|---|---|
| 1 | `TEMPLATE_ID` | `NUMBER(18,0)` | NOT NULL | — | PK part 1; FK → `ff_formula_templates.TEMPLATE_ID` |
| 2 | `LANGUAGE` | `VARCHAR2(4)` | NOT NULL | — | PK part 2 (e.g. `US`, `ZHS`) |
| 3 | `NAME` | `VARCHAR2(240)` | NOT NULL | — | Translated display name (`translateFlag=Y`) |
| 4 | `SOURCE_LANG` | `VARCHAR2(4)` | NOT NULL | — | MLS source-language column |
| 5 | `DESCRIPTION` | `VARCHAR2(4000)` | NULL | — | Translated description (`translateFlag=Y`) |
| 6 | `OBJECT_VERSION_NUMBER` | `NUMBER(9,0)` | NOT NULL | `1` | Optimistic lock |
| 7 | `CREATED_BY` | `VARCHAR2(64)` | NOT NULL | — | WHO |
| 8 | `CREATION_DATE` | `TIMESTAMP(6)` | NOT NULL | — | WHO |
| 9 | `LAST_UPDATE_DATE` | `TIMESTAMP(6)` | NOT NULL | — | WHO |
| 10 | `LAST_UPDATE_LOGIN` | `VARCHAR2(32)` | NULL | — | WHO |
| 11 | `LAST_UPDATED_BY` | `VARCHAR2(64)` | NOT NULL | — | WHO |
| 12 | `ENTERPRISE_ID` | `NUMBER(18,0)` | NOT NULL | `nvl(SYS_CONTEXT(...), 0)` | VPD key |
| 13 | `SEED_DATA_SOURCE` | `VARCHAR2(512)` | NOT NULL | `SYS_CONTEXT('FND_SDF_CTX', 'SDFFILE')` | Seed source |
| 14 | `ORA_SEED_SET1` | `VARCHAR2(1)` | NOT NULL | `'Y'` | EBR |
| 15 | `ORA_SEED_SET2` | `VARCHAR2(1)` | NOT NULL | `'Y'` | EBR |

**Total: 15 columns.** `_TL` does **not** carry `MODULE_ID` — module ownership lives on the
base row only, following the Fusion sample pattern (`pay_generic_ana_types_tl` also omits it).

### FF_FORMULA_TEMPLATES_VL (View — file `FF_FORMULA_TEMPLATES_VL.view`)

Read-only view joining the base and `_TL` filtered on `USERENV('LANG')`. Supplies the
translated row for the current session's language. Used by ADF VO and REST queries.

```sql
SELECT
    fft.TEMPLATE_ID,
    fft.FORMULA_TYPE_ID,
    fft.TEMPLATE_CODE,
    fft.FORMULA_TEXT,
    fft.ADDITIONAL_PROMPT_TEXT,
    fft.SOURCE_TYPE,
    fft.ACTIVE_FLAG,
    fft.SORT_ORDER,
    fft.SEED_DATA_SOURCE,
    fft.CREATED_BY,
    fft.CREATION_DATE,
    fft.LAST_UPDATE_DATE,
    fft.LAST_UPDATE_LOGIN,
    fft.LAST_UPDATED_BY,
    fft.MODULE_ID,
    fft.ENTERPRISE_ID,
    fft.OBJECT_VERSION_NUMBER,
    ffttl.NAME,
    ffttl.DESCRIPTION
FROM FF_FORMULA_TEMPLATES   fft,
     FF_FORMULA_TEMPLATES_TL ffttl
WHERE fft.TEMPLATE_ID = ffttl.TEMPLATE_ID
  AND ffttl.LANGUAGE  = USERENV('LANG');
```

## Constraints & Indexes

### Base table (`ff_formula_templates`)

| Name | Type | Columns | Notes |
|---|---|---|---|
| `FF_FORMULA_TEMPLATES_PK` | PRIMARY KEY (disabled) | `(TEMPLATE_ID)` | Fusion convention: PK declared but `<DISABLE/>`; uniqueness enforced via the two `_Ux` EBR indexes |
| `FF_FORMULA_TEMPLATES_U1` | UNIQUE INDEX | `(TEMPLATE_ID, ORA_SEED_SET1)` | EBR set 1 |
| `FF_FORMULA_TEMPLATES_U2` | UNIQUE INDEX | `(TEMPLATE_ID, ORA_SEED_SET2)` | EBR set 2 |
| `FF_FORMULA_TEMPLATES_U3` | UNIQUE INDEX | `(TEMPLATE_CODE)` | Business-key uniqueness |
| `FF_FORMULA_TEMPLATES_N1` | NON-UNIQUE INDEX | `(FORMULA_TYPE_ID)` | Accelerates "find templates for type X" |

### Translation table (`ff_formula_templates_tl`)

| Name | Type | Columns |
|---|---|---|
| `FF_FORMULA_TEMPLATES_TL_PK` | PRIMARY KEY (disabled) + UNIQUE INDEX | PK: `(TEMPLATE_ID, LANGUAGE)`; Index: `(TEMPLATE_ID, LANGUAGE, ORA_SEED_SET1)` |
| `FF_FORMULA_TEMPLATES_TL_PK1` | UNIQUE INDEX | `(TEMPLATE_ID, LANGUAGE, ORA_SEED_SET2)` |
| `FF_FORMULA_TEMPLATES_TL_FK` | FOREIGN KEY | `(TEMPLATE_ID)` → `ff_formula_templates.TEMPLATE_ID` |

## TEMPLATE_ID Generation

No DB sequence. `TEMPLATE_ID` is assigned by the Java EO layer before insert
(typically via `HrcDmlOperationsHelper.getNextSeqValue()` or similar Fusion
sequence-pool helper). This avoids the need for a separate `.sequence` file in
the schema directory.

## File Layout

```
java/schema/oracle/apps/hcm/ff/
  ff_formula_templates.table         # base, lowercase filename
  ff_formula_templates_tl.table      # translation, lowercase filename
  FF_FORMULA_TEMPLATES_VL.view       # view, uppercase filename, .view extension
```

This mirrors the Oracle Fusion sample in `sample/` (`pay_generic_ana_types.table` +
`pay_generic_ana_types_tl.table` + `PAY_GENERIC_ANA_TYPES_VL.view`). Files can be
copied directly into the Fusion view layer under
`fusionapps/hcm/components/hcmPayroll/formulas/core/dbSchema/database/fusionDB/FUSION/`.

## Seed Data Framework Integration

Unlike the earlier draft (which used BC4J DTD format), the current `.table` files follow
the full Fusion SDF composite XML format. SDF participation is **built into the schema**
via these columns (all present on the base, `MODULE_ID` omitted on `_TL`):

| Column | Role |
|---|---|
| `SEED_DATA_SOURCE` | Populated automatically from `SYS_CONTEXT('FND_SDF_CTX', 'SDFFILE')` during apply. `'BULK_SEED_DATA_SCRIPT'` for bulk-loaded rows |
| `MODULE_ID` | Declares module ownership (FK → `FND_APPL_TAXONOMY`). Required for SDF extract — rows without it are **not** extracted |
| `ORA_SEED_SET1` / `ORA_SEED_SET2` | EBR (Edition-Based Redefinition) context flags. Default `'Y'` |
| `ENTERPRISE_ID` | Fusion VPD multi-tenant partition |

**Follow-up steps that still live outside the `.table` files:**

1. **LoaderEntities.xml** — register `ff_formula_templates` and `ff_formula_templates_tl`
   as a paired loader entity. `_TL` references base as its parent.
2. **BRM registration** — add to Bulk Registration Mechanism so the table participates
   in seed extract/apply jars and CSV round-trips.
3. **MODULE_ID value** — decide the owning module code (likely `HXT` for HCM Fast Formula).
   Every seeded row must set this.
4. **`CREATED_BY = 'SEED_DATA_FROM_APPLICATION'`** — convention for seeded rows; the REST
   API must refuse to update rows whose `SOURCE_TYPE = 'SEEDED'`.
5. **Seed data CSV / XML** — author the initial seed dataset under the owning module's
   seed data directory (typically `seed/<module>/data/` in the view layer).

## Out of Scope (Future Work)

- Java entity / EO / VO mapping
- REST endpoints (`/api/templates`) — `GET`, `POST`, `PUT`, `DELETE`
- Migration of existing `custom_formulas.json` rows into seeded data
- Frontend UI for listing / saving templates
- The five SDF registration steps above
