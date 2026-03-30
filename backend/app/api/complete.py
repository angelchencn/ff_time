"""Complete API route — POST /api/complete."""
from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()

# Lazy-init AI service to avoid requiring a valid API key at import time.
_ai_service = None


def get_ai_service():
    global _ai_service  # noqa: PLW0603
    if _ai_service is None:
        from app.services.ai_service import AIService
        _ai_service = AIService()
    return _ai_service


class CompleteRequest(BaseModel):
    code: str
    cursor_line: int = 1
    cursor_col: int = 0


class CompleteResponse(BaseModel):
    suggestions: list[str]


@router.post("/api/complete", response_model=CompleteResponse)
def complete(req: CompleteRequest) -> CompleteResponse:
    """Return inline completion suggestions for a partial Fast Formula."""
    ai = get_ai_service()
    suggestions = ai.complete(req.code, req.cursor_line, req.cursor_col)
    return CompleteResponse(suggestions=suggestions)
