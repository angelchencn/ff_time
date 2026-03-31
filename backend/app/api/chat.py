"""Chat API route — POST /api/chat (SSE streaming)."""
from __future__ import annotations

import json
import uuid
from typing import Any, AsyncGenerator, Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session
from sse_starlette.sse import EventSourceResponse

from app.models.base import get_session
from app.models.chat_session import ChatSession

router = APIRouter()

# Lazy-init AI service to avoid requiring a valid API key at import time.
_ai_service = None


def get_ai_service():
    global _ai_service  # noqa: PLW0603
    if _ai_service is None:
        from app.services.ai_service import AIService
        _ai_service = AIService()
    return _ai_service


class ChatRequest(BaseModel):
    session_id: Optional[str] = None
    message: str
    formula_type: str = "TIME_LABOR"
    code: str = ""


@router.post("/api/chat")
async def chat(
    req: ChatRequest,
    db: Session = Depends(get_session),
) -> EventSourceResponse:
    """Stream a Fast Formula AI response via SSE."""

    # Load or create chat session
    session_id = req.session_id or str(uuid.uuid4())
    session = db.get(ChatSession, session_id)
    if session is None:
        session = ChatSession(id=session_id, messages=[])
        db.add(session)
        db.commit()
        db.refresh(session)

    history: list[dict[str, str]] = list(session.messages or [])

    async def event_generator() -> AsyncGenerator[dict[str, Any], None]:
        ai = get_ai_service()
        full_response = ""

        for chunk in ai.stream_generate(req.message, req.formula_type, history=history, current_code=req.code):
            full_response += chunk
            yield {"data": json.dumps({"text": chunk})}

        # Persist conversation turns after streaming completes
        updated_messages = list(history) + [
            {"role": "user", "content": req.message},
            {"role": "assistant", "content": full_response},
        ]
        session.messages = updated_messages  # type: ignore[assignment]
        db.add(session)
        db.commit()

        yield {"data": "[DONE]"}

    return EventSourceResponse(event_generator())
