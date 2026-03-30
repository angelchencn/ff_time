import uuid
from app.models.base import Base
from app.models.formula import Formula
from app.models.dbi_registry import DBIRegistry
from app.models.simulation_run import SimulationRun
from app.models.chat_session import ChatSession
from sqlalchemy import create_engine
from sqlalchemy.orm import Session


def test_formula_create_and_read(tmp_path):
    """Formula can be created and read back with all fields."""
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


def test_dbi_registry_create_and_read(tmp_path):
    """DBIRegistry can be created and read back with all fields."""
    test_engine = create_engine(f"sqlite:///{tmp_path}/test.db")
    Base.metadata.create_all(test_engine)

    with Session(test_engine) as session:
        dbi = DBIRegistry(
            name="EMPLOYEE_RATE",
            data_type="NUMBER",
            module="HR",
            description="Employee pay rate",
            is_active=True,
        )
        session.add(dbi)
        session.commit()
        session.refresh(dbi)

        assert dbi.id is not None
        assert dbi.name == "EMPLOYEE_RATE"
        assert dbi.data_type == "NUMBER"
        assert dbi.is_active is True


def test_simulation_run_create_and_read(tmp_path):
    """SimulationRun can be created and read back with all fields."""
    test_engine = create_engine(f"sqlite:///{tmp_path}/test.db")
    Base.metadata.create_all(test_engine)

    with Session(test_engine) as session:
        formula = Formula(
            name="Test Formula",
            formula_type="TIME_LABOR",
        )
        session.add(formula)
        session.commit()
        session.refresh(formula)

        run = SimulationRun(
            formula_id=formula.id,
            input_data={"hours": 40},
            output_data={"pay": 800},
            execution_trace=[{"step": 1, "result": 40}],
            status="COMPLETED",
        )
        session.add(run)
        session.commit()
        session.refresh(run)

        assert run.id is not None
        assert run.formula_id == formula.id
        assert run.input_data == {"hours": 40}
        assert run.status == "COMPLETED"
        assert run.created_at is not None


def test_chat_session_create_and_read(tmp_path):
    """ChatSession can be created and read back with all fields."""
    test_engine = create_engine(f"sqlite:///{tmp_path}/test.db")
    Base.metadata.create_all(test_engine)

    with Session(test_engine) as session:
        chat = ChatSession(
            messages=[{"role": "user", "content": "Hello"}],
        )
        session.add(chat)
        session.commit()
        session.refresh(chat)

        assert chat.id is not None
        assert chat.formula_id is None
        assert chat.messages == [{"role": "user", "content": "Hello"}]
        assert chat.created_at is not None


def test_chat_session_with_formula(tmp_path):
    """ChatSession can be linked to a Formula."""
    test_engine = create_engine(f"sqlite:///{tmp_path}/test.db")
    Base.metadata.create_all(test_engine)

    with Session(test_engine) as session:
        formula = Formula(
            name="Linked Formula",
            formula_type="TIME_LABOR",
        )
        session.add(formula)
        session.commit()
        session.refresh(formula)

        chat = ChatSession(
            formula_id=formula.id,
            messages=[],
        )
        session.add(chat)
        session.commit()
        session.refresh(chat)

        assert chat.formula_id == formula.id
