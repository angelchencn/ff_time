# FF Time Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a web application that uses Claude API + RAG to generate, validate, and simulate Oracle HCM Fast Formulas for Time & Labor.

**Architecture:** React frontend with Monaco Editor communicates via REST/SSE with a Python FastAPI backend. The backend has four services: AI Service (Claude API + RAG), Validator (Lark parser), Simulator (AST interpreter), and RAG Engine (ChromaDB). SQLite for persistence.

**Tech Stack:** React 18 + TypeScript + Vite + Ant Design + Monaco Editor | Python 3.12 + FastAPI + Lark + ChromaDB + Anthropic SDK + SQLAlchemy + Alembic

**Spec:** `docs/superpowers/specs/2026-03-30-ff-time-design.md`

---

## File Map

### Backend

| File | Responsibility |
|------|---------------|
| `backend/app/main.py` | FastAPI app factory, CORS, lifespan events |
| `backend/app/config.py` | Settings via pydantic-settings (env vars) |
| `backend/app/models/base.py` | SQLAlchemy declarative base, engine, session |
| `backend/app/models/formula.py` | Formula ORM model |
| `backend/app/models/dbi_registry.py` | DBIRegistry ORM model |
| `backend/app/models/simulation_run.py` | SimulationRun ORM model |
| `backend/app/models/chat_session.py` | ChatSession ORM model |
| `backend/app/parser/grammar.lark` | Lark grammar for Fast Formula |
| `backend/app/parser/ast_nodes.py` | Immutable dataclass AST node types |
| `backend/app/parser/ff_parser.py` | Lark transformer → AST |
| `backend/app/parser/interpreter.py` | Tree-walking interpreter for simulation |
| `backend/app/services/validator.py` | 3-layer validation (syntax, semantic, rules) |
| `backend/app/services/simulator.py` | Simulation orchestration + trace |
| `backend/app/services/rag_engine.py` | ChromaDB knowledge base + retrieval |
| `backend/app/services/ai_service.py` | Claude API calls, prompt assembly, streaming |
| `backend/app/api/validate.py` | POST /api/validate route |
| `backend/app/api/simulate.py` | POST /api/simulate route |
| `backend/app/api/chat.py` | POST /api/chat route (SSE) |
| `backend/app/api/complete.py` | POST /api/complete route |
| `backend/app/api/explain.py` | POST /api/explain route (SSE) |
| `backend/app/api/formulas.py` | Formula CRUD routes |
| `backend/app/api/dbi.py` | GET /api/dbi route |
| `backend/app/scripts/seed_knowledge_base.py` | CLI script to ingest samples into ChromaDB |
| `backend/data/samples/` | User-provided Fast Formula sample files |
| `backend/data/dbi_registry/time_labor_dbis.json` | Time & Labor DBI catalog |
| `backend/tests/` | All backend tests |
| `backend/requirements.txt` | Python dependencies |
| `backend/alembic.ini` | Alembic config |
| `backend/alembic/` | Migration scripts |
| `backend/.env.example` | Example environment variables |

### Frontend

| File | Responsibility |
|------|---------------|
| `frontend/src/App.tsx` | Root component, layout routing |
| `frontend/src/components/Layout/AppLayout.tsx` | Three-column resizable layout |
| `frontend/src/components/Layout/Toolbar.tsx` | Top toolbar (New, Save, Export, Mode toggle) |
| `frontend/src/components/Layout/StatusBar.tsx` | Bottom status bar |
| `frontend/src/components/Editor/FFEditor.tsx` | Monaco Editor wrapper with FF language |
| `frontend/src/components/ChatPanel/ChatPanel.tsx` | Chat UI with message list + input |
| `frontend/src/components/ChatPanel/ChatMessage.tsx` | Single chat message bubble |
| `frontend/src/components/SimulationPanel/SimulationPanel.tsx` | Tabbed right panel (Validate/Simulate/Explain) |
| `frontend/src/components/SimulationPanel/InputForm.tsx` | Auto-generated variable input form |
| `frontend/src/components/SimulationPanel/ExecutionTrace.tsx` | Trace display |
| `frontend/src/components/SimulationPanel/ValidationResults.tsx` | Validation diagnostic display |
| `frontend/src/languages/fast-formula.ts` | Monaco language definition (tokens, keywords) |
| `frontend/src/languages/ff-completion.ts` | Completion provider (DBI, functions, variables) |
| `frontend/src/services/api.ts` | Axios/fetch wrappers for all API calls |
| `frontend/src/services/sse.ts` | SSE client helper for streaming endpoints |
| `frontend/src/stores/editorStore.ts` | Zustand store: code, validation, dirty state |
| `frontend/src/stores/chatStore.ts` | Zustand store: sessions, messages |
| `frontend/src/stores/simulationStore.ts` | Zustand store: inputs, outputs, trace |
| `frontend/src/hooks/useAutoSave.ts` | localStorage auto-save every 10s |
| `frontend/src/hooks/useValidation.ts` | Debounced validation on code change |

---

## Phase 1: Backend Foundation

### Task 1: Project scaffolding and dependencies

**Files:**
- Create: `backend/requirements.txt`
- Create: `backend/.env.example`
- Create: `backend/app/__init__.py`
- Create: `backend/app/config.py`
- Create: `backend/app/main.py`

- [ ] **Step 1: Create backend directory structure**

```bash
mkdir -p backend/app/{api,services,parser,models,scripts}
mkdir -p backend/data/{samples,dbi_registry}
mkdir -p backend/tests
mkdir -p backend/alembic
touch backend/app/__init__.py
touch backend/app/api/__init__.py
touch backend/app/services/__init__.py
touch backend/app/parser/__init__.py
touch backend/app/models/__init__.py
touch backend/app/scripts/__init__.py
touch backend/tests/__init__.py
```

- [ ] **Step 2: Write requirements.txt**

```
fastapi==0.115.6
uvicorn[standard]==0.34.0
sqlalchemy==2.0.36
alembic==1.14.1
pydantic-settings==2.7.1
anthropic==0.43.0
chromadb==0.6.3
sentence-transformers==3.3.1
lark==1.2.2
python-dotenv==1.0.1
httpx==0.28.1
sse-starlette==2.2.1
slowapi==0.1.9
pytest==8.3.4
pytest-asyncio==0.25.0
```

- [ ] **Step 3: Write .env.example**

```
ANTHROPIC_API_KEY=sk-ant-xxx
DATABASE_URL=sqlite:///./ff_time.db
CHROMA_PERSIST_DIR=./chroma_data
CORS_ORIGINS=http://localhost:5173
```

- [ ] **Step 4: Write config.py**

```python
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    anthropic_api_key: str
    database_url: str = "sqlite:///./ff_time.db"
    chroma_persist_dir: str = "./chroma_data"
    cors_origins: str = "http://localhost:5173"

    model_config = {"env_file": ".env"}


settings = Settings()
```

- [ ] **Step 5: Write main.py with FastAPI app**

```python
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

from app.config import settings
from app.models.base import create_tables

limiter = Limiter(key_func=get_remote_address)


@asynccontextmanager
async def lifespan(app: FastAPI):
    create_tables()
    yield


app = FastAPI(title="FF Time", version="0.1.0", lifespan=lifespan)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins.split(","),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/health")
def health():
    return {"status": "ok"}
```

- [ ] **Step 6: Verify the server starts**

Run: `cd backend && pip install -r requirements.txt && uvicorn app.main:app --reload --port 8000`
Expected: Server running, `curl http://localhost:8000/api/health` returns `{"status":"ok"}`

- [ ] **Step 7: Commit**

```bash
git add backend/
git commit -m "feat: scaffold backend with FastAPI, config, and health endpoint"
```

---

### Task 2: Database models and Alembic migrations

**Files:**
- Create: `backend/app/models/base.py`
- Create: `backend/app/models/formula.py`
- Create: `backend/app/models/dbi_registry.py`
- Create: `backend/app/models/simulation_run.py`
- Create: `backend/app/models/chat_session.py`
- Create: `backend/alembic.ini`
- Test: `backend/tests/test_models.py`

- [ ] **Step 1: Write failing test for Formula model**

```python
# backend/tests/test_models.py
import uuid
from app.models.base import engine, get_session
from app.models.formula import Formula
from sqlalchemy import create_engine
from sqlalchemy.orm import Session


def test_formula_create_and_read(tmp_path):
    """Formula can be created and read back with all fields."""
    from app.models.base import Base
    test_engine = create_engine(f"sqlite:///{tmp_path}/test.db")
    Base.metadata.create_all(test_engine)

    with Session(test_engine) as session:
        formula = Formula(
            name="Overtime Calc",
            description="Calculate overtime pay",
            formula_type="TIME_LABOR",
            use_case="overtime",
            code="DEFAULT FOR hours IS 0\nRETURN hours",
            version=1,
            status="DRAFT",
        )
        session.add(formula)
        session.commit()
        session.refresh(formula)

        assert formula.id is not None
        assert formula.name == "Overtime Calc"
        assert formula.formula_type == "TIME_LABOR"
        assert formula.status == "DRAFT"
        assert formula.created_at is not None
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && python -m pytest tests/test_models.py::test_formula_create_and_read -v`
Expected: FAIL (modules not found)

- [ ] **Step 3: Write base.py**

```python
# backend/app/models/base.py
from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session

from app.config import settings

engine = create_engine(settings.database_url, echo=False)


class Base(DeclarativeBase):
    pass


def create_tables():
    Base.metadata.create_all(engine)


def get_session():
    with Session(engine) as session:
        yield session
```

- [ ] **Step 4: Write formula.py model**

```python
# backend/app/models/formula.py
import uuid
from datetime import datetime, timezone

from sqlalchemy import String, Text, Integer, DateTime
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Formula(Base):
    __tablename__ = "formulas"

    id: Mapped[str] = mapped_column(
        String(36), primary_key=True, default=lambda: str(uuid.uuid4())
    )
    name: Mapped[str] = mapped_column(String(255))
    description: Mapped[str] = mapped_column(Text, default="")
    formula_type: Mapped[str] = mapped_column(String(50))
    use_case: Mapped[str] = mapped_column(String(100), default="")
    code: Mapped[str] = mapped_column(Text, default="")
    version: Mapped[int] = mapped_column(Integer, default=1)
    status: Mapped[str] = mapped_column(String(20), default="DRAFT")
    user_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=lambda: datetime.now(timezone.utc)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )
```

- [ ] **Step 5: Write remaining models (dbi_registry, simulation_run, chat_session)**

```python
# backend/app/models/dbi_registry.py
import uuid

from sqlalchemy import String, Boolean
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class DBIRegistry(Base):
    __tablename__ = "dbi_registry"

    id: Mapped[str] = mapped_column(
        String(36), primary_key=True, default=lambda: str(uuid.uuid4())
    )
    name: Mapped[str] = mapped_column(String(255), unique=True)
    data_type: Mapped[str] = mapped_column(String(20))  # NUMBER, TEXT, DATE
    module: Mapped[str] = mapped_column(String(50))
    description: Mapped[str] = mapped_column(String(500), default="")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
```

```python
# backend/app/models/simulation_run.py
import uuid
from datetime import datetime, timezone

from sqlalchemy import String, JSON, DateTime, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class SimulationRun(Base):
    __tablename__ = "simulation_runs"

    id: Mapped[str] = mapped_column(
        String(36), primary_key=True, default=lambda: str(uuid.uuid4())
    )
    formula_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("formulas.id")
    )
    input_data: Mapped[dict] = mapped_column(JSON, default=dict)
    output_data: Mapped[dict] = mapped_column(JSON, default=dict)
    execution_trace: Mapped[list] = mapped_column(JSON, default=list)
    status: Mapped[str] = mapped_column(String(20))  # SUCCESS, ERROR
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=lambda: datetime.now(timezone.utc)
    )
```

```python
# backend/app/models/chat_session.py
import uuid
from datetime import datetime, timezone

from sqlalchemy import String, JSON, DateTime, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class ChatSession(Base):
    __tablename__ = "chat_sessions"

    id: Mapped[str] = mapped_column(
        String(36), primary_key=True, default=lambda: str(uuid.uuid4())
    )
    formula_id: Mapped[str | None] = mapped_column(
        String(36), ForeignKey("formulas.id"), nullable=True
    )
    messages: Mapped[list] = mapped_column(JSON, default=list)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=lambda: datetime.now(timezone.utc)
    )
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && python -m pytest tests/test_models.py -v`
Expected: PASS

- [ ] **Step 7: Initialize Alembic**

```bash
cd backend && alembic init alembic
```

Edit `alembic/env.py` to import models and use `settings.database_url`.
Edit `alembic.ini` to set `sqlalchemy.url` placeholder.
Run: `alembic revision --autogenerate -m "initial tables"`
Run: `alembic upgrade head`

- [ ] **Step 8: Commit**

```bash
git add backend/
git commit -m "feat: add database models and Alembic migrations"
```

---

## Phase 2: Parser, Validator, and Simulator

### Task 3: Fast Formula Lark grammar and parser

**Files:**
- Create: `backend/app/parser/grammar.lark`
- Create: `backend/app/parser/ast_nodes.py`
- Create: `backend/app/parser/ff_parser.py`
- Test: `backend/tests/test_parser.py`

- [ ] **Step 1: Write failing test for parser**

```python
# backend/tests/test_parser.py
from app.parser.ff_parser import parse_formula
from app.parser.ast_nodes import Program, VariableDecl, IfStatement, ReturnStatement


def test_parse_simple_formula():
    code = """
DEFAULT FOR hours IS 0
INPUT IS rate
ot_pay = hours * rate
RETURN ot_pay
"""
    ast = parse_formula(code)
    assert isinstance(ast, Program)
    assert len(ast.statements) == 4
    assert isinstance(ast.statements[0], VariableDecl)
    assert ast.statements[0].var_name == "hours"


def test_parse_if_else():
    code = """
IF hours > 40 THEN
    ot_hours = hours - 40
ELSE
    ot_hours = 0
ENDIF
RETURN ot_hours
"""
    ast = parse_formula(code)
    assert isinstance(ast.statements[0], IfStatement)
    assert isinstance(ast.statements[1], ReturnStatement)


def test_parse_syntax_error_returns_diagnostics():
    code = "IF hours > THEN"
    result = parse_formula(code, return_diagnostics=True)
    assert result.diagnostics is not None
    assert len(result.diagnostics) > 0
    assert result.diagnostics[0].severity == "error"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && python -m pytest tests/test_parser.py -v`
Expected: FAIL

- [ ] **Step 3: Write ast_nodes.py — immutable dataclasses for all AST node types**

```python
# backend/app/parser/ast_nodes.py
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Diagnostic:
    line: int
    col: int
    end_col: int
    severity: str  # "error" | "warning"
    message: str
    layer: str = "syntax"


@dataclass(frozen=True)
class ParseResult:
    program: "Program | None"
    diagnostics: list[Diagnostic] = field(default_factory=list)


@dataclass(frozen=True)
class Program:
    statements: list = field(default_factory=list)


@dataclass(frozen=True)
class VariableDecl:
    kind: str  # DEFAULT, INPUT, OUTPUT, LOCAL
    var_name: str
    data_type: str = "NUMBER"  # NUMBER, TEXT, DATE
    default_value: "Expression | None" = None


@dataclass(frozen=True)
class Assignment:
    var_name: str
    value: "Expression" = None


@dataclass(frozen=True)
class IfStatement:
    condition: "Expression" = None
    then_body: list = field(default_factory=list)
    else_body: list = field(default_factory=list)


@dataclass(frozen=True)
class WhileLoop:
    condition: "Expression" = None
    body: list = field(default_factory=list)


@dataclass(frozen=True)
class ReturnStatement:
    value: "Expression" = None


@dataclass(frozen=True)
class BinaryOp:
    op: str
    left: "Expression" = None
    right: "Expression" = None


@dataclass(frozen=True)
class UnaryOp:
    op: str
    operand: "Expression" = None


@dataclass(frozen=True)
class FunctionCall:
    name: str
    args: list = field(default_factory=list)


@dataclass(frozen=True)
class NumberLiteral:
    value: float


@dataclass(frozen=True)
class StringLiteral:
    value: str


@dataclass(frozen=True)
class VariableRef:
    name: str


# Union type for type hints
Expression = BinaryOp | UnaryOp | FunctionCall | NumberLiteral | StringLiteral | VariableRef
```

- [ ] **Step 4: Write grammar.lark**

```lark
// backend/app/parser/grammar.lark
start: statement+

statement: var_decl
         | assignment
         | if_stmt
         | while_stmt
         | return_stmt
         | comment

var_decl: "DEFAULT" "FOR" NAME data_type? "IS" expr  -> default_decl
        | "INPUT" "IS" NAME                           -> input_decl
        | "OUTPUT" "IS" NAME                          -> output_decl
        | "LOCAL" NAME data_type?                     -> local_decl

data_type: "(" ("NUMBER" | "TEXT" | "DATE") ")"

assignment: NAME "=" expr

if_stmt: "IF" expr "THEN" then_body ("ELSE" else_body)? "ENDIF"
then_body: statement+
else_body: statement+

while_stmt: "WHILE" expr "LOOP" statement+ "ENDLOOP"

return_stmt: "RETURN" expr

comment: BLOCK_COMMENT

?expr: or_expr
?or_expr: and_expr ("OR" and_expr)*
?and_expr: not_expr ("AND" not_expr)*
?not_expr: "NOT" not_expr -> not_op
         | comparison
?comparison: add_expr (COMP_OP add_expr)?
?add_expr: mul_expr (ADD_OP mul_expr)*
?mul_expr: unary_expr (MUL_OP unary_expr)*
?unary_expr: "-" unary_expr -> neg
           | atom
?atom: NUMBER                    -> number
     | ESCAPED_STRING            -> string
     | NAME "(" expr_list? ")"   -> func_call
     | NAME                      -> var_ref
     | "(" expr ")"

expr_list: expr ("," expr)*

COMP_OP: "=" | "!=" | "<>" | "<" | ">" | "<=" | ">="
ADD_OP: "+" | "-"
MUL_OP: "*" | "/"
BLOCK_COMMENT: "/*" /[\s\S]*?/ "*/"

%import common.CNAME -> NAME
%import common.NUMBER
%import common.ESCAPED_STRING
%import common.WS
%ignore WS
%ignore BLOCK_COMMENT
```

- [ ] **Step 5: Write ff_parser.py — Lark transformer to AST**

```python
# backend/app/parser/ff_parser.py
from lark import Lark, Transformer, v_args, UnexpectedInput
from pathlib import Path

from app.parser.ast_nodes import (
    Program, VariableDecl, Assignment, IfStatement, WhileLoop,
    ReturnStatement, BinaryOp, UnaryOp, FunctionCall,
    NumberLiteral, StringLiteral, VariableRef, Diagnostic, ParseResult,
)

GRAMMAR_PATH = Path(__file__).parent / "grammar.lark"


class FFTransformer(Transformer):
    def start(self, items):
        return Program(statements=list(items))

    def default_decl(self, items):
        name = str(items[0])
        dt = str(items[1]) if len(items) > 2 else "NUMBER"
        default_val = items[-1]
        return VariableDecl(kind="DEFAULT", var_name=name, data_type=dt, default_value=default_val)

    def input_decl(self, items):
        return VariableDecl(kind="INPUT", var_name=str(items[0]))

    def output_decl(self, items):
        return VariableDecl(kind="OUTPUT", var_name=str(items[0]))

    def local_decl(self, items):
        name = str(items[0])
        dt = str(items[1]) if len(items) > 1 else "NUMBER"
        return VariableDecl(kind="LOCAL", var_name=name, data_type=dt)

    def data_type(self, items):
        return str(items[0])

    def assignment(self, items):
        return Assignment(var_name=str(items[0]), value=items[1])

    def then_body(self, items):
        return list(items)

    def else_body(self, items):
        return list(items)

    def if_stmt(self, items):
        # items = [condition, then_body_list, else_body_list?]
        condition = items[0]
        then_stmts = items[1] if len(items) > 1 else []
        else_stmts = items[2] if len(items) > 2 else []
        return IfStatement(condition=condition, then_body=then_stmts, else_body=else_stmts)

    def while_stmt(self, items):
        return WhileLoop(condition=items[0], body=list(items[1:]))

    def return_stmt(self, items):
        return ReturnStatement(value=items[0])

    @v_args(inline=True)
    def number(self, token):
        return NumberLiteral(value=float(token))

    @v_args(inline=True)
    def string(self, token):
        return StringLiteral(value=str(token)[1:-1])

    @v_args(inline=True)
    def var_ref(self, token):
        return VariableRef(name=str(token))

    def func_call(self, items):
        name = str(items[0])
        args = list(items[1:]) if len(items) > 1 else []
        # Flatten expr_list
        if args and isinstance(args[0], list):
            args = args[0]
        return FunctionCall(name=name, args=args)

    def expr_list(self, items):
        return list(items)

    def not_op(self, items):
        return UnaryOp(op="NOT", operand=items[0])

    def neg(self, items):
        return UnaryOp(op="-", operand=items[0])

    def or_expr(self, items):
        result = items[0]
        for i in range(1, len(items)):
            result = BinaryOp(op="OR", left=result, right=items[i])
        return result

    def and_expr(self, items):
        result = items[0]
        for i in range(1, len(items)):
            result = BinaryOp(op="AND", left=result, right=items[i])
        return result

    def comparison(self, items):
        if len(items) == 1:
            return items[0]
        return BinaryOp(op=str(items[1]), left=items[0], right=items[2])

    def add_expr(self, items):
        result = items[0]
        i = 1
        while i < len(items):
            op = str(items[i])
            right = items[i + 1]
            result = BinaryOp(op=op, left=result, right=right)
            i += 2
        return result

    def mul_expr(self, items):
        result = items[0]
        i = 1
        while i < len(items):
            op = str(items[i])
            right = items[i + 1]
            result = BinaryOp(op=op, left=result, right=right)
            i += 2
        return result

    def comment(self, items):
        return None  # Comments are ignored in AST


_parser = Lark(
    GRAMMAR_PATH.read_text(),
    parser="earley",
    propagate_positions=True,
)
_transformer = FFTransformer()


def parse_formula(code: str, return_diagnostics: bool = False) -> Program | ParseResult:
    try:
        tree = _parser.parse(code.strip())
        program = _transformer.transform(tree)
        # Filter out None (comments)
        filtered = Program(
            statements=[s for s in program.statements if s is not None]
        )
        if return_diagnostics:
            return ParseResult(program=filtered, diagnostics=[])
        return filtered
    except UnexpectedInput as e:
        diag = Diagnostic(
            line=getattr(e, "line", 1),
            col=getattr(e, "column", 1),
            end_col=getattr(e, "column", 1) + 1,
            severity="error",
            message=str(e),
        )
        if return_diagnostics:
            return ParseResult(program=None, diagnostics=[diag])
        raise
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && python -m pytest tests/test_parser.py -v`
Expected: All 3 tests PASS

- [ ] **Step 7: Commit**

```bash
git add backend/app/parser/ backend/tests/test_parser.py
git commit -m "feat: add Fast Formula Lark grammar and parser with AST"
```

---

### Task 4: Validator service (3 layers)

**Files:**
- Create: `backend/app/services/validator.py`
- Create: `backend/data/dbi_registry/time_labor_dbis.json`
- Test: `backend/tests/test_validator.py`

- [ ] **Step 1: Write failing tests for validator**

```python
# backend/tests/test_validator.py
from app.services.validator import validate_formula


def test_valid_formula_returns_no_errors():
    code = """
DEFAULT FOR hours IS 0
INPUT IS rate
ot_pay = hours * rate
RETURN ot_pay
"""
    result = validate_formula(code)
    assert result.valid is True
    assert len(result.diagnostics) == 0


def test_undeclared_variable_error():
    code = """
DEFAULT FOR hours IS 0
result = hours * unknown_var
RETURN result
"""
    result = validate_formula(code)
    assert result.valid is False
    assert any(d.layer == "semantic" and "unknown_var" in d.message for d in result.diagnostics)


def test_syntax_error():
    code = "IF hours > THEN"
    result = validate_formula(code)
    assert result.valid is False
    assert any(d.layer == "syntax" for d in result.diagnostics)


def test_output_variable_not_assigned_warning():
    code = """
OUTPUT IS result
DEFAULT FOR hours IS 0
RETURN hours
"""
    result = validate_formula(code)
    assert any(
        d.severity == "warning" and "result" in d.message
        for d in result.diagnostics
    )
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && python -m pytest tests/test_validator.py -v`
Expected: FAIL

- [ ] **Step 3: Write DBI registry JSON**

```json
[
  {"name": "HOURS_WORKED", "data_type": "NUMBER", "description": "Total hours worked in the period"},
  {"name": "OVERTIME_HOURS", "data_type": "NUMBER", "description": "Overtime hours in the period"},
  {"name": "OVERTIME_RATE", "data_type": "NUMBER", "description": "Overtime pay rate multiplier"},
  {"name": "REGULAR_RATE", "data_type": "NUMBER", "description": "Regular hourly rate"},
  {"name": "SHIFT_TYPE", "data_type": "TEXT", "description": "Shift type code"},
  {"name": "WORK_DATE", "data_type": "DATE", "description": "Date of work"},
  {"name": "DAY_OF_WEEK", "data_type": "TEXT", "description": "Day of week"},
  {"name": "HOLIDAY_FLAG", "data_type": "TEXT", "description": "Y/N flag for public holiday"},
  {"name": "EMPLOYEE_ID", "data_type": "NUMBER", "description": "Employee identifier"},
  {"name": "ASSIGNMENT_ID", "data_type": "NUMBER", "description": "Assignment identifier"},
  {"name": "ELEMENT_ENTRY_ID", "data_type": "NUMBER", "description": "Element entry identifier"},
  {"name": "PAY_PERIOD_START", "data_type": "DATE", "description": "Pay period start date"},
  {"name": "PAY_PERIOD_END", "data_type": "DATE", "description": "Pay period end date"}
]
```

- [ ] **Step 4: Write validator.py**

```python
# backend/app/services/validator.py
import json
from dataclasses import dataclass, field
from pathlib import Path

from app.parser.ff_parser import parse_formula
from app.parser.ast_nodes import (
    Diagnostic, Program, VariableDecl, Assignment, IfStatement,
    WhileLoop, ReturnStatement, BinaryOp, UnaryOp, FunctionCall,
    VariableRef,
)

DBI_PATH = Path(__file__).parent.parent.parent / "data" / "dbi_registry" / "time_labor_dbis.json"


@dataclass(frozen=True)
class ValidationResult:
    valid: bool
    diagnostics: list[Diagnostic] = field(default_factory=list)


def _load_dbi_names() -> set[str]:
    if DBI_PATH.exists():
        data = json.loads(DBI_PATH.read_text())
        return {item["name"] for item in data}
    return set()


def _collect_refs(node, refs: list[str]):
    """Recursively collect all variable references from an expression."""
    if isinstance(node, VariableRef):
        refs.append(node.name)
    elif isinstance(node, BinaryOp):
        _collect_refs(node.left, refs)
        _collect_refs(node.right, refs)
    elif isinstance(node, UnaryOp):
        _collect_refs(node.operand, refs)
    elif isinstance(node, FunctionCall):
        for arg in node.args:
            _collect_refs(arg, refs)


def _semantic_check(program: Program, dbi_names: set[str]) -> list[Diagnostic]:
    diagnostics = []
    declared_vars: set[str] = set()
    assigned_vars: set[str] = set()
    output_vars: set[str] = set()

    # First pass: collect all declarations
    for stmt in program.statements:
        if isinstance(stmt, VariableDecl):
            declared_vars.add(stmt.var_name)
            if stmt.kind == "OUTPUT":
                output_vars.add(stmt.var_name)
            if stmt.kind == "DEFAULT" and stmt.default_value is not None:
                assigned_vars.add(stmt.var_name)

    # Known names = declared vars + DBI names + built-in functions
    known_names = declared_vars | dbi_names

    # Second pass: check references and assignments
    def check_statements(stmts):
        for stmt in stmts:
            if isinstance(stmt, Assignment):
                assigned_vars.add(stmt.var_name)
                # Also treat assigned vars as declared (implicit local)
                known_names.add(stmt.var_name)
                refs = []
                _collect_refs(stmt.value, refs)
                for ref in refs:
                    if ref not in known_names:
                        diagnostics.append(Diagnostic(
                            line=1, col=1, end_col=1,
                            severity="error",
                            message=f"Undeclared variable: {ref}",
                            layer="semantic",
                        ))
            elif isinstance(stmt, IfStatement):
                refs = []
                _collect_refs(stmt.condition, refs)
                for ref in refs:
                    if ref not in known_names:
                        diagnostics.append(Diagnostic(
                            line=1, col=1, end_col=1,
                            severity="error",
                            message=f"Undeclared variable: {ref}",
                            layer="semantic",
                        ))
                check_statements(stmt.then_body)
                check_statements(stmt.else_body)
            elif isinstance(stmt, WhileLoop):
                refs = []
                _collect_refs(stmt.condition, refs)
                for ref in refs:
                    if ref not in known_names:
                        diagnostics.append(Diagnostic(
                            line=1, col=1, end_col=1,
                            severity="error",
                            message=f"Undeclared variable: {ref}",
                            layer="semantic",
                        ))
                check_statements(stmt.body)
            elif isinstance(stmt, ReturnStatement):
                refs = []
                _collect_refs(stmt.value, refs)
                for ref in refs:
                    if ref not in known_names:
                        diagnostics.append(Diagnostic(
                            line=1, col=1, end_col=1,
                            severity="error",
                            message=f"Undeclared variable: {ref}",
                            layer="semantic",
                        ))

    check_statements(program.statements)

    # Check OUTPUT vars are assigned
    for var in output_vars:
        if var not in assigned_vars:
            diagnostics.append(Diagnostic(
                line=1, col=1, end_col=1,
                severity="warning",
                message=f"OUTPUT variable '{var}' is never assigned a value",
                layer="semantic",
            ))

    return diagnostics


def validate_formula(code: str) -> ValidationResult:
    dbi_names = _load_dbi_names()

    # Layer 1: Syntax
    parse_result = parse_formula(code, return_diagnostics=True)
    if parse_result.diagnostics:
        return ValidationResult(valid=False, diagnostics=parse_result.diagnostics)

    if parse_result.program is None:
        return ValidationResult(valid=False, diagnostics=[
            Diagnostic(line=1, col=1, end_col=1, severity="error", message="Failed to parse formula")
        ])

    # Layer 2: Semantic
    semantic_diags = _semantic_check(parse_result.program, dbi_names)

    # Layer 3: Rule validation
    rule_diags = _rule_check(parse_result.program)

    all_diags = list(semantic_diags) + list(rule_diags)
    has_errors = any(d.severity == "error" for d in all_diags)

    return ValidationResult(valid=not has_errors, diagnostics=all_diags)


def _rule_check(program: Program) -> list[Diagnostic]:
    """Layer 3: Business rule validation (configurable)."""
    diagnostics = []

    # Collect all referenced variable/DBI names
    all_refs: list[str] = []
    for stmt in program.statements:
        if isinstance(stmt, Assignment):
            _collect_refs(stmt.value, all_refs)
        elif isinstance(stmt, IfStatement):
            _collect_refs(stmt.condition, all_refs)
        elif isinstance(stmt, ReturnStatement):
            _collect_refs(stmt.value, all_refs)

    # Check: formulas with overtime-related output should reference HOURS_WORKED
    output_names = {s.var_name.upper() for s in program.statements if isinstance(s, VariableDecl) and s.kind == "OUTPUT"}
    has_ot_output = any("OT" in name or "OVERTIME" in name for name in output_names)
    if has_ot_output and "HOURS_WORKED" not in {r.upper() for r in all_refs}:
        diagnostics.append(Diagnostic(
            line=1, col=1, end_col=1,
            severity="warning",
            message="Overtime formula should reference HOURS_WORKED DBI",
            layer="rule",
        ))

    # Check: formula must have at least one RETURN statement
    has_return = any(isinstance(s, ReturnStatement) for s in program.statements)
    if not has_return:
        diagnostics.append(Diagnostic(
            line=1, col=1, end_col=1,
            severity="warning",
            message="Formula has no RETURN statement",
            layer="rule",
        ))

    return diagnostics
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && python -m pytest tests/test_validator.py -v`
Expected: All 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/app/services/validator.py backend/data/dbi_registry/ backend/tests/test_validator.py
git commit -m "feat: add 3-layer Fast Formula validator with DBI registry"
```

---

### Task 5: Simulator service (AST interpreter)

**Files:**
- Create: `backend/app/parser/interpreter.py`
- Create: `backend/app/services/simulator.py`
- Test: `backend/tests/test_simulator.py`

- [ ] **Step 1: Write failing tests for simulator**

```python
# backend/tests/test_simulator.py
from app.services.simulator import simulate_formula


def test_simple_arithmetic():
    code = """
DEFAULT FOR hours IS 0
DEFAULT FOR rate IS 0
ot_pay = hours * rate
RETURN ot_pay
"""
    result = simulate_formula(code, {"hours": 10, "rate": 1.5})
    assert result.status == "SUCCESS"
    assert result.output_data["ot_pay"] == 15.0


def test_if_else_branching():
    code = """
DEFAULT FOR hours IS 0
IF hours > 40 THEN
    ot_hours = hours - 40
ELSE
    ot_hours = 0
ENDIF
RETURN ot_hours
"""
    result = simulate_formula(code, {"hours": 45})
    assert result.output_data["ot_hours"] == 5.0

    result2 = simulate_formula(code, {"hours": 30})
    assert result2.output_data["ot_hours"] == 0.0


def test_execution_trace_populated():
    code = """
DEFAULT FOR hours IS 0
ot_pay = hours * 2
RETURN ot_pay
"""
    result = simulate_formula(code, {"hours": 10})
    assert len(result.execution_trace) > 0
    assert any("ot_pay" in step.get("statement", "") for step in result.execution_trace)


def test_division_by_zero_error():
    code = """
DEFAULT FOR x IS 0
result = 10 / x
RETURN result
"""
    result = simulate_formula(code, {"x": 0})
    assert result.status == "ERROR"
    assert result.error is not None
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && python -m pytest tests/test_simulator.py -v`
Expected: FAIL

- [ ] **Step 3: Write interpreter.py**

```python
# backend/app/parser/interpreter.py
from app.parser.ast_nodes import (
    Program, VariableDecl, Assignment, IfStatement, WhileLoop,
    ReturnStatement, BinaryOp, UnaryOp, FunctionCall,
    NumberLiteral, StringLiteral, VariableRef,
)


class ReturnSignal(Exception):
    def __init__(self, value):
        self.value = value


class SimulationError(Exception):
    pass


class Interpreter:
    def __init__(self, input_data: dict | None = None):
        self.env: dict = dict(input_data or {})
        self.trace: list[dict] = []
        self._line = 0
        self._input_keys: set[str] = set(input_data.keys()) if input_data else set()
        self._declared_input_keys: set[str] = set()  # DEFAULT/INPUT declared vars

    def _add_trace(self, statement: str, result: str = ""):
        self.trace.append({
            "line": self._line,
            "statement": statement,
            "result": str(result),
        })

    def run(self, program: Program) -> dict:
        try:
            for i, stmt in enumerate(program.statements):
                self._line = i + 1
                self._exec_statement(stmt)
        except ReturnSignal as r:
            self._add_trace(f"RETURN {r.value}", str(r.value))
        # Return only computed/assigned variables, not input variables
        exclude = self._input_keys | self._declared_input_keys
        return {k: v for k, v in self.env.items() if k not in exclude}

    def _exec_statement(self, stmt):
        if isinstance(stmt, VariableDecl):
            self._declared_input_keys.add(stmt.var_name)
            if stmt.var_name not in self.env:
                val = self._eval(stmt.default_value) if stmt.default_value else 0
                self.env[stmt.var_name] = val
                self._add_trace(
                    f"DEFAULT {stmt.var_name} = {val}", str(val)
                )

        elif isinstance(stmt, Assignment):
            val = self._eval(stmt.value)
            self.env[stmt.var_name] = val
            self._add_trace(f"{stmt.var_name} = {val}", str(val))

        elif isinstance(stmt, IfStatement):
            cond = self._eval(stmt.condition)
            self._add_trace(f"IF ... → {bool(cond)}", str(bool(cond)))
            body = stmt.then_body if cond else stmt.else_body
            for s in body:
                self._line += 1
                self._exec_statement(s)

        elif isinstance(stmt, WhileLoop):
            iterations = 0
            max_iterations = 10000
            while self._eval(stmt.condition):
                iterations += 1
                if iterations > max_iterations:
                    raise SimulationError("Infinite loop detected (>10000 iterations)")
                for s in stmt.body:
                    self._line += 1
                    self._exec_statement(s)

        elif isinstance(stmt, ReturnStatement):
            val = self._eval(stmt.value)
            raise ReturnSignal(val)

    def _eval(self, node) -> float | str:
        if node is None:
            return 0

        if isinstance(node, NumberLiteral):
            return node.value

        if isinstance(node, StringLiteral):
            return node.value

        if isinstance(node, VariableRef):
            if node.name in self.env:
                return self.env[node.name]
            return 0  # Undefined vars default to 0

        if isinstance(node, BinaryOp):
            left = self._eval(node.left)
            right = self._eval(node.right)
            return self._binary_op(node.op, left, right)

        if isinstance(node, UnaryOp):
            operand = self._eval(node.operand)
            if node.op == "-":
                return -operand
            if node.op == "NOT":
                return not operand

        if isinstance(node, FunctionCall):
            return self._call_function(node)

        return 0

    def _binary_op(self, op: str, left, right):
        if op == "+":
            return left + right
        if op == "-":
            return left - right
        if op == "*":
            return left * right
        if op == "/":
            if right == 0:
                raise SimulationError("Division by zero")
            return left / right
        if op in ("=", "=="):
            return left == right
        if op in ("!=", "<>"):
            return left != right
        if op == "<":
            return left < right
        if op == ">":
            return left > right
        if op == "<=":
            return left <= right
        if op == ">=":
            return left >= right
        if op == "AND":
            return bool(left) and bool(right)
        if op == "OR":
            return bool(left) or bool(right)
        raise SimulationError(f"Unknown operator: {op}")

    def _call_function(self, node: FunctionCall):
        args = [self._eval(a) for a in node.args]
        name = node.name.upper()

        if name == "TO_NUMBER":
            return float(args[0]) if args else 0
        if name == "TO_CHAR":
            return str(args[0]) if args else ""
        if name == "ABS":
            return abs(args[0]) if args else 0
        if name == "ROUND":
            if len(args) >= 2:
                return round(args[0], int(args[1]))
            return round(args[0]) if args else 0
        if name == "GREATEST":
            return max(args) if args else 0
        if name == "LEAST":
            return min(args) if args else 0

        self._add_trace(f"WARNING: Unknown function {name}, returning 0")
        return 0
```

- [ ] **Step 4: Write simulator.py service wrapper**

```python
# backend/app/services/simulator.py
from dataclasses import dataclass, field

from app.parser.ff_parser import parse_formula
from app.parser.interpreter import Interpreter, SimulationError


@dataclass(frozen=True)
class SimulationResult:
    status: str  # SUCCESS | ERROR
    output_data: dict = field(default_factory=dict)
    execution_trace: list = field(default_factory=list)
    error: str | None = None


def simulate_formula(code: str, input_data: dict) -> SimulationResult:
    parse_result = parse_formula(code, return_diagnostics=True)

    if parse_result.program is None:
        return SimulationResult(
            status="ERROR",
            error=parse_result.diagnostics[0].message if parse_result.diagnostics else "Parse error",
        )

    interpreter = Interpreter(input_data=input_data)
    try:
        env = interpreter.run(parse_result.program)
        return SimulationResult(
            status="SUCCESS",
            output_data=env,
            execution_trace=interpreter.trace,
        )
    except SimulationError as e:
        return SimulationResult(
            status="ERROR",
            execution_trace=interpreter.trace,
            error=str(e),
        )
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && python -m pytest tests/test_simulator.py -v`
Expected: All 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/app/parser/interpreter.py backend/app/services/simulator.py backend/tests/test_simulator.py
git commit -m "feat: add Fast Formula interpreter and simulation service"
```

---

## Phase 3: RAG Engine and AI Service

### Task 6: RAG engine with ChromaDB

**Files:**
- Create: `backend/app/services/rag_engine.py`
- Create: `backend/app/scripts/seed_knowledge_base.py`
- Test: `backend/tests/test_rag_engine.py`

- [ ] **Step 1: Write failing test**

```python
# backend/tests/test_rag_engine.py
import tempfile
from app.services.rag_engine import RAGEngine


def test_add_and_query_formula(tmp_path):
    engine = RAGEngine(persist_dir=str(tmp_path / "chroma"))
    engine.add_formula(
        doc_id="test1",
        code="DEFAULT FOR hours IS 0\not_pay = hours * 1.5\nRETURN ot_pay",
        metadata={"formula_type": "TIME_LABOR", "use_case": "overtime"},
    )
    results = engine.query("calculate overtime pay", top_k=3)
    assert len(results) > 0
    assert "ot_pay" in results[0]["code"]


def test_query_empty_collection(tmp_path):
    engine = RAGEngine(persist_dir=str(tmp_path / "chroma"))
    results = engine.query("anything", top_k=3)
    assert results == []
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && python -m pytest tests/test_rag_engine.py -v`
Expected: FAIL

- [ ] **Step 3: Write rag_engine.py**

```python
# backend/app/services/rag_engine.py
import chromadb
from chromadb.config import Settings as ChromaSettings


class RAGEngine:
    COLLECTION_NAME = "fast_formulas"

    def __init__(self, persist_dir: str | None = None):
        from chromadb.utils.embedding_functions import SentenceTransformerEmbeddingFunction
        self._embed_fn = SentenceTransformerEmbeddingFunction(
            model_name="all-MiniLM-L6-v2"
        )

        if persist_dir:
            self._client = chromadb.PersistentClient(
                path=persist_dir,
                settings=ChromaSettings(anonymized_telemetry=False),
            )
        else:
            self._client = chromadb.Client(
                settings=ChromaSettings(anonymized_telemetry=False),
            )
        self._collection = self._client.get_or_create_collection(
            name=self.COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},
            embedding_function=self._embed_fn,
        )

    def add_formula(self, doc_id: str, code: str, metadata: dict | None = None):
        self._collection.upsert(
            ids=[doc_id],
            documents=[code],
            metadatas=[metadata or {}],
        )

    def add_document(self, doc_id: str, text: str, metadata: dict | None = None):
        self._collection.upsert(
            ids=[doc_id],
            documents=[text],
            metadatas=[metadata or {}],
        )

    def query(
        self, query_text: str, top_k: int = 5, min_similarity: float = 0.6,
        filter_metadata: dict | None = None,
    ) -> list[dict]:
        if self._collection.count() == 0:
            return []

        kwargs = {
            "query_texts": [query_text],
            "n_results": min(top_k, self._collection.count()),
        }
        if filter_metadata:
            kwargs["where"] = filter_metadata

        results = self._collection.query(**kwargs)

        output = []
        for i, doc_id in enumerate(results["ids"][0]):
            distance = results["distances"][0][i] if results.get("distances") else 0
            similarity = 1 - distance  # cosine distance → similarity
            if similarity >= min_similarity:
                output.append({
                    "id": doc_id,
                    "code": results["documents"][0][i],
                    "metadata": results["metadatas"][0][i] if results.get("metadatas") else {},
                    "similarity": similarity,
                })
        return output
```

- [ ] **Step 4: Write seed script**

```python
# backend/app/scripts/seed_knowledge_base.py
"""
Seed the ChromaDB knowledge base from sample files.

Usage: python -m app.scripts.seed_knowledge_base
"""
import json
from pathlib import Path

from app.config import settings
from app.services.rag_engine import RAGEngine

SAMPLES_DIR = Path(__file__).parent.parent.parent / "data" / "samples"


def seed():
    engine = RAGEngine(persist_dir=settings.chroma_persist_dir)
    count = 0

    for ff_file in SAMPLES_DIR.glob("*.ff"):
        code = ff_file.read_text()
        metadata = {"formula_type": "TIME_LABOR", "source": ff_file.name}

        # Check for companion .json metadata file
        meta_file = ff_file.with_suffix(".json")
        if meta_file.exists():
            metadata.update(json.loads(meta_file.read_text()))

        engine.add_formula(doc_id=ff_file.stem, code=code, metadata=metadata)
        count += 1
        print(f"  Added: {ff_file.name}")

    print(f"\nSeeded {count} formulas into ChromaDB.")


if __name__ == "__main__":
    seed()
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && python -m pytest tests/test_rag_engine.py -v`
Expected: All 2 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/app/services/rag_engine.py backend/app/scripts/ backend/tests/test_rag_engine.py
git commit -m "feat: add RAG engine with ChromaDB and seed script"
```

---

### Task 7: AI service with Claude API

**Files:**
- Create: `backend/app/services/ai_service.py`
- Test: `backend/tests/test_ai_service.py`

- [ ] **Step 1: Write failing test**

```python
# backend/tests/test_ai_service.py
from unittest.mock import AsyncMock, patch, MagicMock
from app.services.ai_service import AIService


def test_build_system_prompt():
    service = AIService.__new__(AIService)
    service._rag = MagicMock()
    prompt = service._build_system_prompt()
    assert "Fast Formula" in prompt
    assert "DEFAULT" in prompt


def test_build_user_prompt_with_rag_context():
    service = AIService.__new__(AIService)
    service._rag = MagicMock()
    service._rag.query.return_value = [
        {"code": "DEFAULT FOR hours IS 0\nRETURN hours", "metadata": {"use_case": "overtime"}},
    ]
    prompt = service._build_generation_prompt(
        message="calculate overtime",
        formula_type="TIME_LABOR",
    )
    assert "calculate overtime" in prompt
    assert "DEFAULT FOR hours" in prompt  # RAG context included


def test_build_completion_prompt():
    service = AIService.__new__(AIService)
    service._rag = MagicMock()
    service._rag.query.return_value = []
    prompt = service._build_completion_prompt(
        code="DEFAULT FOR hours IS 0\n",
        cursor_line=2,
        cursor_col=0,
    )
    assert "DEFAULT FOR hours" in prompt
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && python -m pytest tests/test_ai_service.py -v`
Expected: FAIL

- [ ] **Step 3: Write ai_service.py**

```python
# backend/app/services/ai_service.py
from collections.abc import AsyncIterator

import anthropic

from app.config import settings
from app.services.rag_engine import RAGEngine

SYSTEM_PROMPT = """You are an Oracle HCM Fast Formula expert for the Time & Labor module.

You generate syntactically correct Fast Formula code. Follow these rules:
- Declare all variables with DEFAULT, INPUT, OUTPUT, or LOCAL
- Use proper Fast Formula syntax: IF/THEN/ELSE/ENDIF, WHILE/LOOP/ENDLOOP
- End formulas with RETURN
- Use uppercase for keywords and DBI names
- Add comments with /* ... */

Supported data types: NUMBER, TEXT, DATE
Common functions: TO_NUMBER, TO_CHAR, TO_DATE, ROUND, ABS, GREATEST, LEAST, DAYS_BETWEEN, HOURS_BETWEEN
"""


class AIService:
    def __init__(self, rag_persist_dir: str | None = None):
        self._client = anthropic.Anthropic(api_key=settings.anthropic_api_key)
        self._rag = RAGEngine(persist_dir=rag_persist_dir or settings.chroma_persist_dir)

    def _build_system_prompt(self) -> str:
        return SYSTEM_PROMPT

    def _build_generation_prompt(self, message: str, formula_type: str = "TIME_LABOR") -> str:
        # Retrieve similar formulas from RAG
        rag_results = self._rag.query(
            message,
            top_k=5,
            filter_metadata={"formula_type": formula_type} if formula_type else None,
        )

        parts = [f"User request: {message}\n"]

        if rag_results:
            parts.append("Here are similar Fast Formula examples for reference:\n")
            for i, r in enumerate(rag_results, 1):
                parts.append(f"--- Example {i} (use case: {r['metadata'].get('use_case', 'unknown')}) ---")
                parts.append(r["code"])
                parts.append("")

        parts.append("Generate a complete Fast Formula based on the user's request. Output only the formula code.")
        return "\n".join(parts)

    def _build_completion_prompt(self, code: str, cursor_line: int, cursor_col: int) -> str:
        lines = code.split("\n")
        context_before = "\n".join(lines[:cursor_line])
        context_after = "\n".join(lines[cursor_line:]) if cursor_line < len(lines) else ""

        # Light RAG search based on existing code
        rag_results = self._rag.query(code[:200], top_k=2)
        rag_context = ""
        if rag_results:
            rag_context = f"\nReference:\n{rag_results[0]['code'][:300]}\n"

        return (
            f"Complete the following Fast Formula at the cursor position (line {cursor_line}, col {cursor_col}).\n"
            f"{rag_context}\n"
            f"Code before cursor:\n{context_before}\n"
            f"Code after cursor:\n{context_after}\n"
            f"Provide only the completion text, nothing else."
        )

    def generate(self, message: str, formula_type: str = "TIME_LABOR", history: list | None = None):
        """Synchronous generation, returns full text."""
        messages = []
        if history:
            messages.extend(history)
        messages.append({"role": "user", "content": self._build_generation_prompt(message, formula_type)})

        response = self._client.messages.create(
            model="claude-sonnet-4-20250514",
            max_tokens=4096,
            system=self._build_system_prompt(),
            messages=messages,
        )
        return response.content[0].text

    def stream_generate(self, message: str, formula_type: str = "TIME_LABOR", history: list | None = None):
        """Streaming generation, yields text chunks."""
        messages = []
        if history:
            messages.extend(history)
        messages.append({"role": "user", "content": self._build_generation_prompt(message, formula_type)})

        with self._client.messages.stream(
            model="claude-sonnet-4-20250514",
            max_tokens=4096,
            system=self._build_system_prompt(),
            messages=messages,
        ) as stream:
            for text in stream.text_stream:
                yield text

    def complete(self, code: str, cursor_line: int, cursor_col: int) -> list[dict]:
        """Code completion, returns suggestions."""
        prompt = self._build_completion_prompt(code, cursor_line, cursor_col)
        response = self._client.messages.create(
            model="claude-haiku-4-5-20251001",  # Fast model for completion
            max_tokens=256,
            system=self._build_system_prompt(),
            messages=[{"role": "user", "content": prompt}],
        )
        text = response.content[0].text.strip()
        return [{"text": text, "range": {
            "startLine": cursor_line,
            "startCol": cursor_col,
            "endLine": cursor_line,
            "endCol": cursor_col,
        }}]

    def explain(self, code: str, selected_range: dict, action: str = "explain"):
        """Streaming explain/fix, yields text chunks."""
        lines = code.split("\n")
        selected = "\n".join(lines[selected_range["startLine"] - 1:selected_range["endLine"]])

        if action == "fix":
            prompt = f"Fix the following Fast Formula code and explain what was wrong:\n\n{selected}"
        else:
            prompt = f"Explain what this Fast Formula code does, line by line:\n\n{selected}"

        with self._client.messages.stream(
            model="claude-sonnet-4-20250514",
            max_tokens=2048,
            system=self._build_system_prompt(),
            messages=[{"role": "user", "content": prompt}],
        ) as stream:
            for text in stream.text_stream:
                yield text
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && python -m pytest tests/test_ai_service.py -v`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/app/services/ai_service.py backend/tests/test_ai_service.py
git commit -m "feat: add AI service with Claude API integration and RAG"
```

---

### Task 8: Backend API routes

**Files:**
- Create: `backend/app/api/validate.py`
- Create: `backend/app/api/simulate.py`
- Create: `backend/app/api/chat.py`
- Create: `backend/app/api/complete.py`
- Create: `backend/app/api/explain.py`
- Create: `backend/app/api/formulas.py`
- Create: `backend/app/api/dbi.py`
- Modify: `backend/app/main.py` (register routers)
- Test: `backend/tests/test_api.py`

- [ ] **Step 1: Write failing tests for validate and simulate endpoints**

```python
# backend/tests/test_api.py
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_validate_valid_formula():
    resp = client.post("/api/validate", json={
        "code": "DEFAULT FOR hours IS 0\nRETURN hours"
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["valid"] is True


def test_validate_invalid_formula():
    resp = client.post("/api/validate", json={
        "code": "IF hours > THEN"
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["valid"] is False
    assert len(data["diagnostics"]) > 0


def test_simulate_formula():
    resp = client.post("/api/simulate", json={
        "code": "DEFAULT FOR hours IS 0\not_pay = hours * 1.5\nRETURN ot_pay",
        "input_data": {"hours": 10}
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "SUCCESS"
    assert data["output_data"]["ot_pay"] == 15.0


def test_dbi_list():
    resp = client.get("/api/dbi")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) > 0
    assert "name" in data[0]
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && python -m pytest tests/test_api.py -v`
Expected: FAIL (404, routes not registered)

- [ ] **Step 3: Write validate.py route**

```python
# backend/app/api/validate.py
from fastapi import APIRouter
from pydantic import BaseModel

from app.services.validator import validate_formula

router = APIRouter()


class ValidateRequest(BaseModel):
    code: str


@router.post("/api/validate")
def validate(req: ValidateRequest):
    result = validate_formula(req.code)
    return {
        "valid": result.valid,
        "diagnostics": [
            {
                "line": d.line,
                "col": d.col,
                "end_col": d.end_col,
                "severity": d.severity,
                "message": d.message,
                "layer": d.layer,
            }
            for d in result.diagnostics
        ],
    }
```

- [ ] **Step 4: Write simulate.py route**

```python
# backend/app/api/simulate.py
from fastapi import APIRouter
from pydantic import BaseModel

from app.services.simulator import simulate_formula

router = APIRouter()


class SimulateRequest(BaseModel):
    code: str
    input_data: dict


@router.post("/api/simulate")
def simulate(req: SimulateRequest):
    result = simulate_formula(req.code, req.input_data)
    return {
        "status": result.status,
        "output_data": result.output_data,
        "execution_trace": result.execution_trace,
        "error": result.error,
    }
```

- [ ] **Step 5: Write chat.py route (SSE streaming)**

```python
# backend/app/api/chat.py
import json
import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel
from sqlalchemy.orm import Session
from sse_starlette.sse import EventSourceResponse

from app.main import limiter
from app.models.base import get_session
from app.models.chat_session import ChatSession
from app.services.ai_service import AIService

router = APIRouter()
_ai_service: AIService | None = None


def get_ai_service() -> AIService:
    global _ai_service
    if _ai_service is None:
        _ai_service = AIService()
    return _ai_service


class ChatRequest(BaseModel):
    session_id: str | None = None
    message: str
    formula_type: str = "TIME_LABOR"


def _load_history(session: Session, session_id: str | None) -> tuple[str, list[dict]]:
    """Load or create chat session, return (session_id, claude_messages)."""
    if session_id:
        chat = session.get(ChatSession, session_id)
        if chat:
            # Convert stored messages to Claude format (last 10 messages)
            recent = chat.messages[-10:] if len(chat.messages) > 10 else chat.messages
            history = [{"role": m["role"], "content": m["content"]} for m in recent]
            return session_id, history

    # Create new session
    new_id = str(uuid.uuid4())
    chat = ChatSession(id=new_id, messages=[])
    session.add(chat)
    session.commit()
    return new_id, []


def _save_messages(session: Session, session_id: str, user_msg: str, assistant_msg: str):
    """Append user and assistant messages to session."""
    chat = session.get(ChatSession, session_id)
    if chat:
        msgs = list(chat.messages)
        now = datetime.now(timezone.utc).isoformat()
        msgs.append({"role": "user", "content": user_msg, "timestamp": now})
        msgs.append({"role": "assistant", "content": assistant_msg, "timestamp": now})
        chat.messages = msgs
        session.commit()


@router.post("/api/chat")
@limiter.limit("10/minute")
async def chat(request: Request, req: ChatRequest, db: Session = Depends(get_session)):
    service = get_ai_service()
    session_id, history = _load_history(db, req.session_id)

    async def event_stream():
        full_response = ""
        for chunk in service.stream_generate(
            message=req.message,
            formula_type=req.formula_type,
            history=history,
        ):
            full_response += chunk
            yield {"event": "token", "data": json.dumps({"text": chunk})}

        # Persist conversation
        _save_messages(db, session_id, req.message, full_response)
        yield {"event": "done", "data": json.dumps({"session_id": session_id})}

    return EventSourceResponse(event_stream())
```

- [ ] **Step 6: Write complete.py and explain.py routes**

```python
# backend/app/api/complete.py
from fastapi import APIRouter
from pydantic import BaseModel

from app.api.chat import get_ai_service

router = APIRouter()


class CompleteRequest(BaseModel):
    code: str
    cursor_line: int
    cursor_col: int


@router.post("/api/complete")
def complete(req: CompleteRequest):
    service = get_ai_service()
    suggestions = service.complete(req.code, req.cursor_line, req.cursor_col)
    return {"suggestions": suggestions}
```

```python
# backend/app/api/explain.py
import json

from fastapi import APIRouter
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from app.api.chat import get_ai_service

router = APIRouter()


class ExplainRequest(BaseModel):
    code: str
    selected_range: dict
    action: str = "explain"


@router.post("/api/explain")
async def explain(req: ExplainRequest):
    service = get_ai_service()

    async def event_stream():
        for chunk in service.explain(req.code, req.selected_range, req.action):
            yield {"event": "token", "data": json.dumps({"text": chunk})}
        yield {"event": "done", "data": "{}"}

    return EventSourceResponse(event_stream())
```

- [ ] **Step 7: Write formulas.py CRUD routes**

```python
# backend/app/api/formulas.py
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.models.base import get_session
from app.models.formula import Formula

router = APIRouter()


class FormulaCreate(BaseModel):
    name: str
    description: str = ""
    formula_type: str = "TIME_LABOR"
    use_case: str = ""
    code: str = ""


class FormulaUpdate(BaseModel):
    name: str | None = None
    description: str | None = None
    code: str | None = None
    status: str | None = None


@router.get("/api/formulas")
def list_formulas(session: Session = Depends(get_session)):
    formulas = session.query(Formula).order_by(Formula.updated_at.desc()).all()
    return [
        {
            "id": f.id, "name": f.name, "description": f.description,
            "formula_type": f.formula_type, "status": f.status,
            "updated_at": f.updated_at.isoformat() if f.updated_at else None,
        }
        for f in formulas
    ]


@router.post("/api/formulas")
def create_formula(req: FormulaCreate, session: Session = Depends(get_session)):
    formula = Formula(**req.model_dump())
    session.add(formula)
    session.commit()
    session.refresh(formula)
    return {"id": formula.id, "name": formula.name}


@router.get("/api/formulas/{formula_id}")
def get_formula(formula_id: str, session: Session = Depends(get_session)):
    formula = session.get(Formula, formula_id)
    if not formula:
        raise HTTPException(status_code=404, detail="Formula not found")
    return {
        "id": formula.id, "name": formula.name, "description": formula.description,
        "formula_type": formula.formula_type, "use_case": formula.use_case,
        "code": formula.code, "version": formula.version, "status": formula.status,
        "created_at": formula.created_at.isoformat() if formula.created_at else None,
        "updated_at": formula.updated_at.isoformat() if formula.updated_at else None,
    }


@router.put("/api/formulas/{formula_id}")
def update_formula(formula_id: str, req: FormulaUpdate, session: Session = Depends(get_session)):
    formula = session.get(Formula, formula_id)
    if not formula:
        raise HTTPException(status_code=404, detail="Formula not found")
    for key, value in req.model_dump(exclude_unset=True).items():
        setattr(formula, key, value)
    session.commit()
    session.refresh(formula)
    return {"id": formula.id, "status": "updated"}


@router.post("/api/formulas/{formula_id}/export")
def export_formula(formula_id: str, session: Session = Depends(get_session)):
    formula = session.get(Formula, formula_id)
    if not formula:
        raise HTTPException(status_code=404, detail="Formula not found")
    filename = f"{formula.name.lower().replace(' ', '_')}.ff"
    return {"filename": filename, "content": formula.code}
```

- [ ] **Step 8: Write dbi.py route**

```python
# backend/app/api/dbi.py
import json
from pathlib import Path

from fastapi import APIRouter

router = APIRouter()

DBI_PATH = Path(__file__).parent.parent.parent / "data" / "dbi_registry" / "time_labor_dbis.json"


@router.get("/api/dbi")
def list_dbis(module: str | None = None, search: str | None = None):
    if not DBI_PATH.exists():
        return []
    data = json.loads(DBI_PATH.read_text())
    if module:
        data = [d for d in data if d.get("module", "TIME_LABOR") == module]
    if search:
        search_lower = search.lower()
        data = [d for d in data if search_lower in d["name"].lower() or search_lower in d.get("description", "").lower()]
    return data
```

- [ ] **Step 9: Register all routers in main.py**

Add to `backend/app/main.py`:

```python
from app.api import validate, simulate, chat, complete, explain, formulas, dbi

app.include_router(validate.router)
app.include_router(simulate.router)
app.include_router(chat.router)
app.include_router(complete.router)
app.include_router(explain.router)
app.include_router(formulas.router)
app.include_router(dbi.router)
```

- [ ] **Step 10: Run tests to verify they pass**

Run: `cd backend && python -m pytest tests/test_api.py -v`
Expected: All 4 tests PASS

- [ ] **Step 11: Commit**

```bash
git add backend/app/api/ backend/app/main.py backend/tests/test_api.py
git commit -m "feat: add all backend API routes (validate, simulate, chat, complete, explain, formulas, dbi)"
```

---

## Phase 4: Frontend

### Task 9: Frontend scaffolding

**Files:**
- Create: `frontend/` via Vite + React + TypeScript
- Create: `frontend/src/services/api.ts`
- Create: `frontend/src/services/sse.ts`
- Create: `frontend/src/stores/editorStore.ts`
- Create: `frontend/src/stores/chatStore.ts`
- Create: `frontend/src/stores/simulationStore.ts`

- [ ] **Step 1: Scaffold React project with Vite**

```bash
cd /Users/xiaojuch/Claude/ff_time
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npm install antd @ant-design/icons zustand @monaco-editor/react axios
```

- [ ] **Step 2: Write API service**

```typescript
// frontend/src/services/api.ts
import axios from "axios";

const api = axios.create({
  baseURL: "http://localhost:8000",
});

export interface Diagnostic {
  line: number;
  col: number;
  end_col: number;
  severity: "error" | "warning";
  message: string;
  layer: "syntax" | "semantic" | "rule";
}

export interface ValidateResponse {
  valid: boolean;
  diagnostics: Diagnostic[];
}

export interface SimulateResponse {
  status: "SUCCESS" | "ERROR";
  output_data: Record<string, unknown>;
  execution_trace: Array<{ line: number; statement: string; result: string }>;
  error: string | null;
}

export interface CompleteSuggestion {
  text: string;
  range: { startLine: number; startCol: number; endLine: number; endCol: number };
}

export const validateCode = (code: string) =>
  api.post<ValidateResponse>("/api/validate", { code });

export const simulateCode = (code: string, input_data: Record<string, unknown>) =>
  api.post<SimulateResponse>("/api/simulate", { code, input_data });

export const completeCode = (code: string, cursor_line: number, cursor_col: number) =>
  api.post<{ suggestions: CompleteSuggestion[] }>("/api/complete", {
    code,
    cursor_line,
    cursor_col,
  });

export const explainCode = (code: string, selectedRange: object, action: string) =>
  `/api/explain`; // SSE handled separately

export const fetchDBIs = () => api.get<Array<{ name: string; data_type: string; description: string }>>("/api/dbi");

export const fetchFormulas = () => api.get("/api/formulas");
export const createFormula = (data: { name: string; code?: string }) => api.post("/api/formulas", data);
export const getFormula = (id: string) => api.get(`/api/formulas/${id}`);
export const updateFormula = (id: string, data: object) => api.put(`/api/formulas/${id}`, data);
export const exportFormula = (id: string) => api.post(`/api/formulas/${id}/export`);
```

- [ ] **Step 3: Write SSE helper**

```typescript
// frontend/src/services/sse.ts
export function streamSSE(
  url: string,
  body: object,
  onToken: (text: string) => void,
  onDone: () => void,
  onError?: (err: Error) => void
): AbortController {
  const controller = new AbortController();

  fetch(`http://localhost:8000${url}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal: controller.signal,
  })
    .then(async (response) => {
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      if (!reader) return;

      let buffer = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          if (line.startsWith("data:")) {
            const data = line.slice(5).trim();
            if (!data) continue;
            try {
              const parsed = JSON.parse(data);
              if (parsed.text) onToken(parsed.text);
            } catch {
              // Skip unparseable lines
            }
          }
          if (line.includes("event: done") || line.includes("event:done")) {
            onDone();
            return;
          }
        }
      }
      onDone();
    })
    .catch((err) => {
      if (err.name !== "AbortError") onError?.(err);
    });

  return controller;
}
```

- [ ] **Step 4: Write Zustand stores**

```typescript
// frontend/src/stores/editorStore.ts
import { create } from "zustand";
import type { Diagnostic } from "../services/api";

interface EditorState {
  code: string;
  diagnostics: Diagnostic[];
  isValid: boolean;
  isDirty: boolean;
  currentFormulaId: string | null;
  mode: "chat" | "code";
  setCode: (code: string) => void;
  setDiagnostics: (diagnostics: Diagnostic[]) => void;
  setValid: (valid: boolean) => void;
  setDirty: (dirty: boolean) => void;
  setFormulaId: (id: string | null) => void;
  setMode: (mode: "chat" | "code") => void;
}

export const useEditorStore = create<EditorState>((set) => ({
  code: "",
  diagnostics: [],
  isValid: true,
  isDirty: false,
  currentFormulaId: null,
  mode: "chat",
  setCode: (code) => set({ code, isDirty: true }),
  setDiagnostics: (diagnostics) => set({ diagnostics }),
  setValid: (isValid) => set({ isValid }),
  setDirty: (isDirty) => set({ isDirty }),
  setFormulaId: (currentFormulaId) => set({ currentFormulaId }),
  setMode: (mode) => set({ mode }),
}));
```

```typescript
// frontend/src/stores/chatStore.ts
import { create } from "zustand";

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
  timestamp: number;
}

interface ChatState {
  sessionId: string | null;
  messages: ChatMessage[];
  isStreaming: boolean;
  addMessage: (msg: ChatMessage) => void;
  appendToLast: (text: string) => void;
  setStreaming: (streaming: boolean) => void;
  clearMessages: () => void;
  setSessionId: (id: string | null) => void;
}

export const useChatStore = create<ChatState>((set) => ({
  sessionId: null,
  messages: [],
  isStreaming: false,
  addMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
  appendToLast: (text) =>
    set((s) => {
      const msgs = [...s.messages];
      if (msgs.length > 0 && msgs[msgs.length - 1].role === "assistant") {
        msgs[msgs.length - 1] = {
          ...msgs[msgs.length - 1],
          content: msgs[msgs.length - 1].content + text,
        };
      }
      return { messages: msgs };
    }),
  setStreaming: (isStreaming) => set({ isStreaming }),
  clearMessages: () => set({ messages: [], sessionId: null }),
  setSessionId: (sessionId) => set({ sessionId }),
}));
```

```typescript
// frontend/src/stores/simulationStore.ts
import { create } from "zustand";

interface TraceStep {
  line: number;
  statement: string;
  result: string;
}

interface SimulationState {
  inputData: Record<string, unknown>;
  outputData: Record<string, unknown>;
  trace: TraceStep[];
  status: "idle" | "running" | "success" | "error";
  error: string | null;
  activeTab: "validate" | "simulate" | "explain";
  setInputData: (data: Record<string, unknown>) => void;
  setResult: (output: Record<string, unknown>, trace: TraceStep[], status: string, error: string | null) => void;
  setActiveTab: (tab: "validate" | "simulate" | "explain") => void;
  reset: () => void;
}

export const useSimulationStore = create<SimulationState>((set) => ({
  inputData: {},
  outputData: {},
  trace: [],
  status: "idle",
  error: null,
  activeTab: "validate",
  setInputData: (inputData) => set({ inputData }),
  setResult: (outputData, trace, status, error) =>
    set({ outputData, trace, status: status as SimulationState["status"], error }),
  setActiveTab: (activeTab) => set({ activeTab }),
  reset: () => set({ inputData: {}, outputData: {}, trace: [], status: "idle", error: null }),
}));
```

- [ ] **Step 5: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds with no errors

- [ ] **Step 6: Commit**

```bash
git add frontend/
git commit -m "feat: scaffold frontend with React, Zustand stores, API services"
```

---

### Task 10: Monaco Editor with Fast Formula language

**Files:**
- Create: `frontend/src/languages/fast-formula.ts`
- Create: `frontend/src/languages/ff-completion.ts`
- Create: `frontend/src/components/Editor/FFEditor.tsx`
- Create: `frontend/src/hooks/useValidation.ts`

- [ ] **Step 1: Write Fast Formula language definition for Monaco**

```typescript
// frontend/src/languages/fast-formula.ts
import type * as monaco from "monaco-editor";

export const FF_LANGUAGE_ID = "fast-formula";

export const languageConfig: monaco.languages.LanguageConfiguration = {
  comments: { blockComment: ["/*", "*/"] },
  brackets: [["(", ")"]],
  autoClosingPairs: [
    { open: "(", close: ")" },
    { open: '"', close: '"' },
    { open: "'", close: "'" },
    { open: "/*", close: "*/" },
  ],
  folding: {
    markers: {
      start: /\b(IF|WHILE)\b/i,
      end: /\b(ENDIF|ENDLOOP)\b/i,
    },
  },
};

export const monarchTokens: monaco.languages.IMonarchLanguage = {
  ignoreCase: true,
  keywords: [
    "DEFAULT", "FOR", "IS", "INPUT", "OUTPUT", "LOCAL",
    "IF", "THEN", "ELSE", "ENDIF",
    "WHILE", "LOOP", "ENDLOOP",
    "RETURN", "AND", "OR", "NOT",
  ],
  typeKeywords: ["NUMBER", "TEXT", "DATE"],
  builtinFunctions: [
    "TO_NUMBER", "TO_CHAR", "TO_DATE", "ROUND", "ABS",
    "GREATEST", "LEAST", "DAYS_BETWEEN", "HOURS_BETWEEN",
    "GET_VALUE_SET", "TRUNC",
  ],
  operators: ["=", "!=", "<>", "<", ">", "<=", ">=", "+", "-", "*", "/"],
  tokenizer: {
    root: [
      [/\/\*/, "comment", "@comment"],
      [/[a-zA-Z_]\w*/, {
        cases: {
          "@keywords": "keyword",
          "@typeKeywords": "type",
          "@builtinFunctions": "predefined",
          "@default": "identifier",
        },
      }],
      [/\d+(\.\d+)?/, "number"],
      [/"[^"]*"/, "string"],
      [/'[^']*'/, "string"],
      [/[=!<>]=?|<>/, "operator"],
      [/[+\-*/]/, "operator"],
      [/[()]/, "delimiter"],
    ],
    comment: [
      [/\*\//, "comment", "@pop"],
      [/./, "comment"],
    ],
  },
};

export function registerFFLanguage(monacoInstance: typeof monaco) {
  monacoInstance.languages.register({ id: FF_LANGUAGE_ID });
  monacoInstance.languages.setLanguageConfiguration(FF_LANGUAGE_ID, languageConfig);
  monacoInstance.languages.setMonarchTokensProvider(FF_LANGUAGE_ID, monarchTokens);
}
```

- [ ] **Step 2: Write completion provider**

```typescript
// frontend/src/languages/ff-completion.ts
import type * as monaco from "monaco-editor";
import { FF_LANGUAGE_ID } from "./fast-formula";

const KEYWORDS = [
  "DEFAULT", "FOR", "IS", "INPUT", "OUTPUT", "LOCAL",
  "IF", "THEN", "ELSE", "ENDIF", "WHILE", "LOOP", "ENDLOOP",
  "RETURN", "AND", "OR", "NOT",
];

const FUNCTIONS = [
  "TO_NUMBER", "TO_CHAR", "TO_DATE", "ROUND", "ABS",
  "GREATEST", "LEAST", "DAYS_BETWEEN", "HOURS_BETWEEN",
  "GET_VALUE_SET",
];

export function registerFFCompletion(
  monacoInstance: typeof monaco,
  dbiNames: Array<{ name: string; description: string }> = []
) {
  monacoInstance.languages.registerCompletionItemProvider(FF_LANGUAGE_ID, {
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };

      const suggestions: monaco.languages.CompletionItem[] = [
        ...KEYWORDS.map((kw) => ({
          label: kw,
          kind: monacoInstance.languages.CompletionItemKind.Keyword,
          insertText: kw,
          range,
        })),
        ...FUNCTIONS.map((fn) => ({
          label: fn,
          kind: monacoInstance.languages.CompletionItemKind.Function,
          insertText: `${fn}($0)`,
          insertTextRules: monacoInstance.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          range,
        })),
        ...dbiNames.map((dbi) => ({
          label: dbi.name,
          kind: monacoInstance.languages.CompletionItemKind.Variable,
          insertText: dbi.name,
          detail: dbi.description,
          range,
        })),
      ];

      return { suggestions };
    },
  });
}
```

- [ ] **Step 3: Write FFEditor component**

```tsx
// frontend/src/components/Editor/FFEditor.tsx
import { useEffect, useRef } from "react";
import Editor, { OnMount } from "@monaco-editor/react";
import type * as monaco from "monaco-editor";
import { FF_LANGUAGE_ID, registerFFLanguage } from "../../languages/fast-formula";
import { registerFFCompletion } from "../../languages/ff-completion";
import { useEditorStore } from "../../stores/editorStore";

interface FFEditorProps {
  dbiList?: Array<{ name: string; description: string }>;
}

export default function FFEditor({ dbiList = [] }: FFEditorProps) {
  const { code, diagnostics, setCode } = useEditorStore();
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  const monacoRef = useRef<typeof monaco | null>(null);

  const handleMount: OnMount = (editor, monacoInstance) => {
    editorRef.current = editor;
    monacoRef.current = monacoInstance;
    registerFFLanguage(monacoInstance);
    registerFFCompletion(monacoInstance, dbiList);
  };

  // Update diagnostics as Monaco markers
  useEffect(() => {
    if (!monacoRef.current || !editorRef.current) return;
    const model = editorRef.current.getModel();
    if (!model) return;

    const markers: monaco.editor.IMarkerData[] = diagnostics.map((d) => ({
      severity:
        d.severity === "error"
          ? monacoRef.current!.MarkerSeverity.Error
          : monacoRef.current!.MarkerSeverity.Warning,
      message: d.message,
      startLineNumber: d.line,
      startColumn: d.col,
      endLineNumber: d.line,
      endColumn: d.end_col,
    }));

    monacoRef.current.editor.setModelMarkers(model, "ff-validator", markers);
  }, [diagnostics]);

  return (
    <Editor
      height="100%"
      language={FF_LANGUAGE_ID}
      value={code}
      onChange={(value) => setCode(value || "")}
      onMount={handleMount}
      options={{
        minimap: { enabled: false },
        fontSize: 14,
        lineNumbers: "on",
        folding: true,
        automaticLayout: true,
        scrollBeyondLastLine: false,
      }}
    />
  );
}
```

- [ ] **Step 4: Write useValidation hook**

```typescript
// frontend/src/hooks/useValidation.ts
import { useEffect, useRef } from "react";
import { useEditorStore } from "../stores/editorStore";
import { validateCode } from "../services/api";

export function useValidation() {
  const { code, setDiagnostics, setValid } = useEditorStore();
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    if (!code.trim()) {
      setDiagnostics([]);
      setValid(true);
      return;
    }

    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(async () => {
      try {
        const { data } = await validateCode(code);
        setDiagnostics(data.diagnostics);
        setValid(data.valid);
      } catch {
        // Backend unreachable — skip validation
      }
    }, 300);

    return () => clearTimeout(timerRef.current);
  }, [code, setDiagnostics, setValid]);
}
```

- [ ] **Step 5: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 6: Commit**

```bash
git add frontend/src/languages/ frontend/src/components/Editor/ frontend/src/hooks/useValidation.ts
git commit -m "feat: add Monaco Editor with Fast Formula language support and validation"
```

---

### Task 11: Chat Panel component

**Files:**
- Create: `frontend/src/components/ChatPanel/ChatPanel.tsx`
- Create: `frontend/src/components/ChatPanel/ChatMessage.tsx`

- [ ] **Step 1: Write ChatMessage component**

```tsx
// frontend/src/components/ChatPanel/ChatMessage.tsx
import { Typography } from "antd";

interface ChatMessageProps {
  role: "user" | "assistant";
  content: string;
}

export default function ChatMessage({ role, content }: ChatMessageProps) {
  const isUser = role === "user";
  return (
    <div style={{
      display: "flex",
      justifyContent: isUser ? "flex-end" : "flex-start",
      marginBottom: 12,
    }}>
      <div style={{
        maxWidth: "80%",
        padding: "8px 12px",
        borderRadius: 8,
        backgroundColor: isUser ? "#1677ff" : "#f5f5f5",
        color: isUser ? "#fff" : "#000",
      }}>
        <Typography.Text style={{ color: "inherit", whiteSpace: "pre-wrap" }}>
          {content}
        </Typography.Text>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Write ChatPanel component**

```tsx
// frontend/src/components/ChatPanel/ChatPanel.tsx
import { useRef, useEffect, useState } from "react";
import { Input, Button, Space } from "antd";
import { SendOutlined } from "@ant-design/icons";
import ChatMessage from "./ChatMessage";
import { useChatStore } from "../../stores/chatStore";
import { useEditorStore } from "../../stores/editorStore";
import { streamSSE } from "../../services/sse";

export default function ChatPanel() {
  const [input, setInput] = useState("");
  const { messages, isStreaming, addMessage, appendToLast, setStreaming } = useChatStore();
  const { setCode } = useEditorStore();
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSend = () => {
    if (!input.trim() || isStreaming) return;
    const userMsg = input.trim();
    setInput("");

    addMessage({ role: "user", content: userMsg, timestamp: Date.now() });
    addMessage({ role: "assistant", content: "", timestamp: Date.now() });
    setStreaming(true);

    let fullResponse = "";
    streamSSE(
      "/api/chat",
      { message: userMsg, formula_type: "TIME_LABOR" },
      (text) => {
        fullResponse += text;
        appendToLast(text);
      },
      () => {
        setStreaming(false);
        // Extract code block if present
        const codeMatch = fullResponse.match(/```[\s\S]*?\n([\s\S]*?)```/);
        if (codeMatch) {
          setCode(codeMatch[1].trim());
        }
      },
      () => setStreaming(false)
    );
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", padding: 12 }}>
      <div style={{ flex: 1, overflowY: "auto", marginBottom: 12 }}>
        {messages.length === 0 && (
          <div style={{ color: "#999", textAlign: "center", marginTop: 40 }}>
            Describe your Fast Formula requirements in natural language.
          </div>
        )}
        {messages.map((msg, i) => (
          <ChatMessage key={i} role={msg.role} content={msg.content} />
        ))}
        <div ref={bottomRef} />
      </div>
      <Space.Compact style={{ width: "100%" }}>
        <Input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={handleSend}
          placeholder="e.g. Calculate overtime at 1.5x for hours over 40..."
          disabled={isStreaming}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={isStreaming}
        />
      </Space.Compact>
    </div>
  );
}
```

- [ ] **Step 3: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/ChatPanel/
git commit -m "feat: add ChatPanel with streaming SSE chat"
```

---

### Task 12: Simulation Panel component

**Files:**
- Create: `frontend/src/components/SimulationPanel/SimulationPanel.tsx`
- Create: `frontend/src/components/SimulationPanel/InputForm.tsx`
- Create: `frontend/src/components/SimulationPanel/ExecutionTrace.tsx`
- Create: `frontend/src/components/SimulationPanel/ValidationResults.tsx`

- [ ] **Step 1: Write ValidationResults component**

```tsx
// frontend/src/components/SimulationPanel/ValidationResults.tsx
import { Alert, List, Tag } from "antd";
import { useEditorStore } from "../../stores/editorStore";

export default function ValidationResults() {
  const { diagnostics, isValid } = useEditorStore();

  if (diagnostics.length === 0) {
    return <Alert type="success" message="No issues found" showIcon />;
  }

  return (
    <List
      size="small"
      dataSource={diagnostics}
      renderItem={(d) => (
        <List.Item>
          <Tag color={d.severity === "error" ? "red" : "orange"}>{d.severity}</Tag>
          <span>Line {d.line}: {d.message}</span>
          <Tag style={{ marginLeft: 8 }}>{d.layer}</Tag>
        </List.Item>
      )}
    />
  );
}
```

- [ ] **Step 2: Write InputForm component**

```tsx
// frontend/src/components/SimulationPanel/InputForm.tsx
import { Input, Form, Button } from "antd";
import { PlayCircleOutlined } from "@ant-design/icons";
import { useSimulationStore } from "../../stores/simulationStore";
import { useEditorStore } from "../../stores/editorStore";
import { simulateCode } from "../../services/api";

export default function InputForm() {
  const [form] = Form.useForm();
  const { code } = useEditorStore();
  const { setResult } = useSimulationStore();

  // Parse variable names from code to auto-generate form fields
  const varNames: string[] = [];
  const lines = code.split("\n");
  for (const line of lines) {
    const defaultMatch = line.match(/DEFAULT\s+FOR\s+(\w+)/i);
    const inputMatch = line.match(/INPUT\s+IS\s+(\w+)/i);
    if (defaultMatch) varNames.push(defaultMatch[1]);
    if (inputMatch) varNames.push(inputMatch[1]);
  }

  const handleRun = async () => {
    const values = form.getFieldsValue();
    const inputData: Record<string, number> = {};
    for (const [key, val] of Object.entries(values)) {
      inputData[key] = Number(val) || 0;
    }

    try {
      const { data } = await simulateCode(code, inputData);
      setResult(data.output_data, data.execution_trace, data.status, data.error);
    } catch (err) {
      setResult({}, [], "error", "Failed to connect to backend");
    }
  };

  return (
    <Form form={form} layout="vertical" size="small">
      {varNames.map((name) => (
        <Form.Item key={name} label={name} name={name}>
          <Input placeholder="0" />
        </Form.Item>
      ))}
      {varNames.length === 0 && (
        <div style={{ color: "#999" }}>No INPUT/DEFAULT variables found in code.</div>
      )}
      <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleRun} block>
        Run Simulation
      </Button>
    </Form>
  );
}
```

- [ ] **Step 3: Write ExecutionTrace component**

```tsx
// frontend/src/components/SimulationPanel/ExecutionTrace.tsx
import { Collapse, Tag, Typography } from "antd";
import { useSimulationStore } from "../../stores/simulationStore";

export default function ExecutionTrace() {
  const { outputData, trace, status, error } = useSimulationStore();

  if (status === "idle") {
    return <div style={{ color: "#999" }}>Run a simulation to see results.</div>;
  }

  return (
    <div>
      {error && (
        <Tag color="red" style={{ marginBottom: 8 }}>Error: {error}</Tag>
      )}

      {status === "success" && (
        <div style={{ marginBottom: 12 }}>
          <Typography.Text strong>Output:</Typography.Text>
          <pre style={{ background: "#f5f5f5", padding: 8, borderRadius: 4, marginTop: 4 }}>
            {Object.entries(outputData)
              .map(([k, v]) => `${k} = ${v}`)
              .join("\n")}
          </pre>
        </div>
      )}

      {trace.length > 0 && (
        <Collapse ghost>
          <Collapse.Panel header="Execution Trace" key="trace">
            {trace.map((step, i) => (
              <div key={i} style={{ fontFamily: "monospace", fontSize: 12 }}>
                <Tag>{`L${step.line}`}</Tag>
                {step.statement}
                {step.result && <span style={{ color: "#888" }}> → {step.result}</span>}
              </div>
            ))}
          </Collapse.Panel>
        </Collapse>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Write SimulationPanel (tabbed container)**

```tsx
// frontend/src/components/SimulationPanel/SimulationPanel.tsx
import { Tabs } from "antd";
import { useSimulationStore } from "../../stores/simulationStore";
import ValidationResults from "./ValidationResults";
import InputForm from "./InputForm";
import ExecutionTrace from "./ExecutionTrace";

export default function SimulationPanel() {
  const { activeTab, setActiveTab } = useSimulationStore();

  return (
    <Tabs
      activeKey={activeTab}
      onChange={(key) => setActiveTab(key as "validate" | "simulate" | "explain")}
      size="small"
      style={{ height: "100%", padding: "0 8px" }}
      items={[
        {
          key: "validate",
          label: "Validate",
          children: <ValidationResults />,
        },
        {
          key: "simulate",
          label: "Simulate",
          children: (
            <div>
              <InputForm />
              <div style={{ marginTop: 12 }}>
                <ExecutionTrace />
              </div>
            </div>
          ),
        },
        {
          key: "explain",
          label: "Explain",
          children: <div style={{ color: "#999" }}>Select code in editor, then right-click → Explain</div>,
        },
      ]}
    />
  );
}
```

- [ ] **Step 5: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/SimulationPanel/
git commit -m "feat: add SimulationPanel with validation, input form, and execution trace"
```

---

### Task 13: App layout and integration

**Files:**
- Create: `frontend/src/components/Layout/AppLayout.tsx`
- Create: `frontend/src/components/Layout/Toolbar.tsx`
- Create: `frontend/src/components/Layout/StatusBar.tsx`
- Create: `frontend/src/hooks/useAutoSave.ts`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Write Toolbar component**

```tsx
// frontend/src/components/Layout/Toolbar.tsx
import { Button, Space, Segmented } from "antd";
import { FileAddOutlined, SaveOutlined, ExportOutlined } from "@ant-design/icons";
import { useEditorStore } from "../../stores/editorStore";

export default function Toolbar() {
  const { mode, setMode, setCode, code } = useEditorStore();

  return (
    <div style={{
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      padding: "8px 16px",
      borderBottom: "1px solid #f0f0f0",
    }}>
      <Space>
        <Button icon={<FileAddOutlined />} size="small" onClick={() => setCode("")}>
          New
        </Button>
        <Button icon={<SaveOutlined />} size="small">Save</Button>
        <Button icon={<ExportOutlined />} size="small">Export</Button>
      </Space>
      <Segmented
        size="small"
        options={[
          { label: "Chat", value: "chat" },
          { label: "Code", value: "code" },
        ]}
        value={mode}
        onChange={(val) => setMode(val as "chat" | "code")}
      />
    </div>
  );
}
```

- [ ] **Step 2: Write StatusBar component**

```tsx
// frontend/src/components/Layout/StatusBar.tsx
import { Tag } from "antd";
import { useEditorStore } from "../../stores/editorStore";

export default function StatusBar() {
  const { code, isValid, diagnostics } = useEditorStore();
  const lineCount = code.split("\n").length;
  const errorCount = diagnostics.filter((d) => d.severity === "error").length;
  const warnCount = diagnostics.filter((d) => d.severity === "warning").length;

  return (
    <div style={{
      display: "flex",
      gap: 16,
      padding: "4px 16px",
      borderTop: "1px solid #f0f0f0",
      fontSize: 12,
      color: "#666",
    }}>
      <span>
        Syntax: {isValid ? <Tag color="green" style={{ fontSize: 11 }}>OK</Tag> : <Tag color="red" style={{ fontSize: 11 }}>{errorCount} errors</Tag>}
      </span>
      {warnCount > 0 && <span>{warnCount} warnings</span>}
      <span>Lines: {lineCount}</span>
    </div>
  );
}
```

- [ ] **Step 3: Write AppLayout with resizable panels**

```tsx
// frontend/src/components/Layout/AppLayout.tsx
import { useState } from "react";
import Toolbar from "./Toolbar";
import StatusBar from "./StatusBar";
import ChatPanel from "../ChatPanel/ChatPanel";
import FFEditor from "../Editor/FFEditor";
import SimulationPanel from "../SimulationPanel/SimulationPanel";
import { useEditorStore } from "../../stores/editorStore";
import { useValidation } from "../../hooks/useValidation";

export default function AppLayout() {
  const { mode } = useEditorStore();
  useValidation();

  const isChatMode = mode === "chat";

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100vh" }}>
      <Toolbar />
      <div style={{ flex: 1, display: "flex", overflow: "hidden" }}>
        {/* Left: Chat Panel */}
        <div style={{
          width: isChatMode ? "35%" : "20%",
          borderRight: "1px solid #f0f0f0",
          transition: "width 0.2s",
        }}>
          <ChatPanel />
        </div>

        {/* Center: Monaco Editor */}
        <div style={{ flex: 1 }}>
          <FFEditor />
        </div>

        {/* Right: Simulation Panel */}
        <div style={{
          width: isChatMode ? "25%" : "30%",
          borderLeft: "1px solid #f0f0f0",
          transition: "width 0.2s",
          overflowY: "auto",
        }}>
          <SimulationPanel />
        </div>
      </div>
      <StatusBar />
    </div>
  );
}
```

- [ ] **Step 4: Write useAutoSave hook**

```typescript
// frontend/src/hooks/useAutoSave.ts
import { useEffect } from "react";
import { useEditorStore } from "../stores/editorStore";

const STORAGE_KEY = "ff_time_autosave";

export function useAutoSave() {
  const { code, setCode, setDirty } = useEditorStore();

  // Restore on mount
  useEffect(() => {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      setCode(saved);
      setDirty(false);
    }
  }, []);

  // Auto-save every 10 seconds
  useEffect(() => {
    const timer = setInterval(() => {
      if (code.trim()) {
        localStorage.setItem(STORAGE_KEY, code);
      }
    }, 10000);
    return () => clearInterval(timer);
  }, [code]);
}
```

- [ ] **Step 5: Update App.tsx**

```tsx
// frontend/src/App.tsx
import AppLayout from "./components/Layout/AppLayout";
import { useAutoSave } from "./hooks/useAutoSave";

function App() {
  useAutoSave();
  return <AppLayout />;
}

export default App;
```

- [ ] **Step 6: Verify frontend builds**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 7: Commit**

```bash
git add frontend/src/
git commit -m "feat: add app layout with toolbar, status bar, auto-save, and 3-column layout"
```

---

## Phase 5: Integration Testing

### Task 14: End-to-end smoke test

**Files:**
- No new files; testing existing integration

- [ ] **Step 1: Start backend**

```bash
cd backend && uvicorn app.main:app --reload --port 8000
```

- [ ] **Step 2: Start frontend**

```bash
cd frontend && npm run dev
```

- [ ] **Step 3: Verify validate endpoint via curl**

```bash
curl -X POST http://localhost:8000/api/validate \
  -H "Content-Type: application/json" \
  -d '{"code": "DEFAULT FOR hours IS 0\nRETURN hours"}'
```
Expected: `{"valid": true, "diagnostics": []}`

- [ ] **Step 4: Verify simulate endpoint via curl**

```bash
curl -X POST http://localhost:8000/api/simulate \
  -H "Content-Type: application/json" \
  -d '{"code": "DEFAULT FOR hours IS 0\not_pay = hours * 1.5\nRETURN ot_pay", "input_data": {"hours": 10}}'
```
Expected: `{"status": "SUCCESS", "output_data": {...}, "execution_trace": [...], "error": null}`

- [ ] **Step 5: Verify DBI endpoint**

```bash
curl http://localhost:8000/api/dbi
```
Expected: JSON array of DBI entries

- [ ] **Step 6: Open browser at http://localhost:5173 and verify**

- Chat panel appears on left
- Monaco editor in center with Fast Formula syntax highlighting
- Simulation panel on right with tabs
- Mode toggle works (Chat/Code)
- Status bar shows syntax status

- [ ] **Step 7: Commit any fixes**

```bash
git add -A
git commit -m "fix: integration fixes from smoke testing"
```

---

## Summary

| Phase | Tasks | Commits |
|-------|-------|---------|
| 1: Backend Foundation | Task 1-2 | 2 |
| 2: Parser/Validator/Simulator | Task 3-5 | 3 |
| 3: RAG + AI | Task 6-8 | 3 |
| 4: Frontend | Task 9-13 | 5 |
| 5: Integration | Task 14 | 1 |
| **Total** | **14 tasks** | **14 commits** |
