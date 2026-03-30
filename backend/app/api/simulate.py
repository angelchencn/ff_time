"""Simulate API route — POST /api/simulate."""
from __future__ import annotations

from typing import Any, Optional

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.simulator import simulate_formula

router = APIRouter()


class SimulateRequest(BaseModel):
    code: str
    input_data: dict[str, Any] = {}


class SimulateResponse(BaseModel):
    status: str
    output_data: dict[str, Any]
    execution_trace: list[dict[str, Any]]
    error: Optional[str]


@router.post("/api/simulate", response_model=SimulateResponse)
def simulate(req: SimulateRequest) -> SimulateResponse:
    """Parse and run a Fast Formula with the given input data."""
    result = simulate_formula(req.code, req.input_data)
    return SimulateResponse(
        status=result.status,
        output_data=result.output_data,
        execution_trace=result.execution_trace,
        error=result.error,
    )
