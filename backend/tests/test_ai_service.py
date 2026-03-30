"""Tests for AIService prompt building — no real API calls."""
from unittest.mock import MagicMock

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
    assert "DEFAULT FOR hours" in prompt


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
