# Repository Guidelines

## Project Structure & Module Organization

This repository contains an Oracle HCM Fast Formula generator. `java/` is the primary Jersey backend, with source in `java/src/main/java`, tests in `java/src/test/java`, schema artifacts in `java/schema`, and seed/helper SQL in `java/scripts`. `backend/` is the legacy FastAPI implementation, with app code in `backend/app`, tests in `backend/tests`, and registry/RAG data in `backend/data`. `frontend/` is the React/Vite UI under `frontend/src`. UI smoke tests live in `qa/`; docs in `docs/`; seed data in `seeddata/`.

## Build, Test, and Development Commands

Run Java commands from `java/`:

```bash
mvn compile exec:java   # start Jersey server on port 8000
mvn clean test          # compile from scratch and run JUnit tests
mvn test -Dtest=ValidatorTest
```

Run frontend commands from `frontend/`:

```bash
npm run dev     # Vite dev server
npm run build   # TypeScript check and production bundle
npm run lint    # ESLint
```

Run legacy Python backend tests from `backend/`:

```bash
./venv/bin/python -m pytest tests --ignore=tests/test_rag_engine.py -q
```

Run UI smoke tests with `cd qa && ./run_smoke.sh`.

## Coding Style & Naming Conventions

Follow existing local style. Java uses package names under `oracle.apps.hcm.formulas.core.jersey`, JUnit 4 tests ending in `Test`, and service/resource names such as `TemplateService` and `FastFormulaResource`. TypeScript uses React function components, Zustand stores, and PascalCase component files. Keep API payload fields snake_case where they mirror backend JSON, for example `template_code` and `systemprompt_flag`. Prefer clear, small changes over broad refactors.

## Testing Guidelines

Add tests close to the changed layer: Java JUnit for parser, validator, simulator, services, and REST behavior; Python pytest only for the legacy backend; Selenium smoke tests for core UI flows. Before handing off backend changes, run `mvn clean test`. Before handing off UI changes, run `npm run build` and `npm run lint` when practical.

## Commit & Pull Request Guidelines

Recent history is mixed, but use descriptive conventional-style commits for new work, for example `fix: handle system prompt seed templates` or `docs: document FormulaTemplates seed data`. PRs should include a summary, affected areas, validation results, linked issue or bug number when available, and screenshots for UI changes.

## Security & Configuration Tips

Do not commit secrets, local `.env` files, generated databases, `node_modules`, virtualenvs, or Maven `target/` output. Review proxy and Fusion credentials before sharing frontend config. Formula templates are source controlled through seed data; update seed XML and documentation instead of patching target pods manually.
