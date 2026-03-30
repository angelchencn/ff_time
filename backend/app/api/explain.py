"""Explain API route — POST /api/explain (SSE streaming)."""
from __future__ import annotations

from typing import Any, AsyncGenerator, Optional

from fastapi import APIRouter
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

router = APIRouter()

# Lazy-init AI service to avoid requiring a valid API key at import time.
_ai_service = None


def get_ai_service():
    global _ai_service  # noqa: PLW0603
    if _ai_service is None:
        from app.services.ai_service import AIService
        _ai_service = AIService()
    return _ai_service


class ExplainRequest(BaseModel):
    code: str
    selected_range: Optional[dict[str, Any]] = None
    action: str = "explain"


@router.post("/api/explain")
async def explain(req: ExplainRequest) -> EventSourceResponse:
    """Stream an explanation or transformation for a Fast Formula via SSE."""

    async def event_generator() -> AsyncGenerator[dict[str, Any], None]:
        ai = get_ai_service()
        for chunk in ai.explain(req.code, req.selected_range, req.action):
            yield {"data": chunk}
        yield {"data": "[DONE]"}

    return EventSourceResponse(event_generator())
