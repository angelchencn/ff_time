"""Validate API route — POST /api/validate."""
from __future__ import annotations

from dataclasses import asdict
from typing import Any

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.validator import validate_formula

router = APIRouter()


class ValidateRequest(BaseModel):
    code: str


class ValidateResponse(BaseModel):
    valid: bool
    diagnostics: list[dict[str, Any]]


@router.post("/api/validate", response_model=ValidateResponse)
def validate(req: ValidateRequest) -> ValidateResponse:
    """Validate a Fast Formula string and return diagnostics."""
    result = validate_formula(req.code)
    diagnostics = [asdict(d) for d in result.diagnostics]
    return ValidateResponse(valid=result.valid, diagnostics=diagnostics)
