# TODO — FF Time Enhancement Roadmap

## Priority 1: High Value / Low Effort

### Type Inference + Type Checking
- Track variable types (NUMBER/TEXT/DATE) through assignments and expressions
- Detect type mismatches: `NUMBER + TEXT`, `DATE * NUMBER`, etc.
- Flag in validator as Layer 2 semantic errors with clear messages
- This is the #1 source of Fast Formula runtime errors in Oracle

### DBI Hover Tooltip + Existence Check
- Monaco HoverProvider: mouse over DBI name → show data type + description from registry
- Validator: check if referenced DBIs exist in `time_labor_dbis.json`
- Report unknown DBIs as warnings (not errors — could be custom DBIs)
- Data already exists in registry, just needs wiring

### AI Auto-Fix on Validation Error
- When validator reports errors, show "Fix with AI" button next to each error
- Send current code + error diagnostics to Claude → get corrected code
- Auto-correction loop: validate → fix → validate (max 3 rounds)
- Close the generate → validate → fix loop without manual editing

### Code Formatter
- One-click format: normalize indentation (2 spaces), align assignments
- Uppercase keywords (`if` → `IF`, `return` → `RETURN`)
- Consistent spacing around operators
- Add to Toolbar as "Format" button and as Monaco right-click action

## Priority 2: High Value / Medium Effort

### Batch Testing (CSV)
- Upload CSV with columns matching INPUT/DEFAULT variable names
- Run simulator for each row, collect results in a table
- Show pass/fail per row with expected vs actual comparison
- Export results as CSV
- UI: new sub-tab under Simulate panel

### Breakpoint Debugger
- Click line number in Monaco to toggle breakpoint
- "Step" button to execute one statement at a time
- Variable watch panel: show current values of all variables at each step
- Highlight current execution line in editor
- Requires refactoring interpreter to support step-by-step execution

### Statement Ordering Validation
- Validator Layer 3 rule: check ALIAS → DEFAULT → INPUTS → other statements order
- Report out-of-order statements as warnings with suggested fix position

### RETURN Reachability Analysis
- Check if all code paths lead to a RETURN statement
- Detect unreachable code after RETURN
- Flag IF blocks where only some branches have RETURN

## Priority 3: Medium Value

### Parser: Array Processing
- Support `A.FIRST`, `A.NEXT(I, -1)`, `A.EXISTS(I)`, `A.COUNT`, `A.DELETE`
- Array variable declarations and indexing: `A[I] = value`
- Required for complex payroll formulas with element iteration

### Parser: CHANGE_CONTEXTS
- Syntax: `CHANGE_CONTEXTS(context1 = value1, context2 = value2) ( statements )`
- Switches DBI context for enclosed statements
- Common in payroll formulas accessing multiple assignments

### Parser: CALL_FORMULA
- Syntax: `CALL_FORMULA('formula_name', input1 = val1, output1 = var1)`
- Call sub-formulas with input/output mapping
- Simulator: stub with configurable mock return values

### Formula Diff + AI Explain Changes
- Side-by-side diff view of two formula versions
- AI explains what changed in business terms
- Useful for audit trail and code review

### Formula Library Management
- List/search/filter saved formulas by type, use case, date
- Duplicate, rename, delete formulas
- Tag/categorize formulas
- Currently only basic CRUD exists

### Import/Export Oracle Format
- Export: generate format compatible with Oracle HCM formula upload utility
- Import: parse Oracle HCM exported formula files
- Include metadata (formula type, description, effective date)

## Priority 4: Future / Post-MVP

### Unused Variable Detection
- Warn on variables declared (DEFAULT/LOCAL) but never referenced
- Warn on variables assigned but never used in RETURN or further logic

### Dead Code Detection
- Detect IF branches with constant conditions (`IF 1 > 2 THEN ...`)
- Detect code after unconditional RETURN

### Multi-Value RETURN Simulation
- Simulator currently only handles single return value
- Support `RETURN var1, var2, var3` and display all output values

### Version History
- Track formula edit history with timestamps
- View any previous version, diff between versions
- Restore previous versions

### User Authentication
- Login/signup (JWT or OAuth)
- Per-user formula storage
- Team sharing and collaboration

### Oracle Environment Integration
- Connect to Oracle HCM instance
- Pull real DBI definitions dynamically
- Deploy formulas directly to Oracle
- Validate against actual Oracle runtime

### Performance Profiling
- Count loop iterations, total execution steps
- Warn on formulas that may timeout in Oracle (>60s execution)
- Suggest optimizations for slow formulas
