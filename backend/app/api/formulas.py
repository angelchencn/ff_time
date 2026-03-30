"""Formulas API routes — CRUD for Formula records."""
from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import Any, Optional

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.models.base import get_session
from app.models.formula import Formula

router = APIRouter()


# ---------------------------------------------------------------------------
# Pydantic schemas
# ---------------------------------------------------------------------------

class FormulaCreate(BaseModel):
    name: str
    description: str = ""
    formula_type: str
    use_case: str = ""
    code: str = ""
    status: str = "DRAFT"
    user_id: Optional[str] = None


class FormulaUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    formula_type: Optional[str] = None
    use_case: Optional[str] = None
    code: Optional[str] = None
    status: Optional[str] = None


class FormulaResponse(BaseModel):
    id: str
    name: str
    description: str
    formula_type: str
    use_case: str
    code: str
    version: int
    status: str
    user_id: Optional[str]
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@router.get("/api/formulas", response_model=list[FormulaResponse])
def list_formulas(db: Session = Depends(get_session)) -> list[Formula]:
    """Return all formulas."""
    return db.query(Formula).order_by(Formula.created_at.desc()).all()


@router.post("/api/formulas", response_model=FormulaResponse, status_code=status.HTTP_201_CREATED)
def create_formula(body: FormulaCreate, db: Session = Depends(get_session)) -> Formula:
    """Create a new formula record."""
    formula = Formula(
        id=str(uuid.uuid4()),
        name=body.name,
        description=body.description,
        formula_type=body.formula_type,
        use_case=body.use_case,
        code=body.code,
        status=body.status,
        user_id=body.user_id,
    )
    db.add(formula)
    db.commit()
    db.refresh(formula)
    return formula


@router.get("/api/formulas/{formula_id}", response_model=FormulaResponse)
def get_formula(formula_id: str, db: Session = Depends(get_session)) -> Formula:
    """Return a single formula by ID."""
    formula = db.get(Formula, formula_id)
    if formula is None:
        raise HTTPException(status_code=404, detail="Formula not found")
    return formula


@router.put("/api/formulas/{formula_id}", response_model=FormulaResponse)
def update_formula(
    formula_id: str,
    body: FormulaUpdate,
    db: Session = Depends(get_session),
) -> Formula:
    """Update an existing formula record."""
    formula = db.get(Formula, formula_id)
    if formula is None:
        raise HTTPException(status_code=404, detail="Formula not found")

    update_data = body.model_dump(exclude_none=True)
    for field, value in update_data.items():
        setattr(formula, field, value)

    formula.updated_at = datetime.now(timezone.utc)
    formula.version = (formula.version or 1) + 1
    db.add(formula)
    db.commit()
    db.refresh(formula)
    return formula


@router.post("/api/formulas/{formula_id}/export")
def export_formula(formula_id: str, db: Session = Depends(get_session)) -> dict[str, Any]:
    """Export a formula as plain text."""
    formula = db.get(Formula, formula_id)
    if formula is None:
        raise HTTPException(status_code=404, detail="Formula not found")
    return {
        "id": formula.id,
        "name": formula.name,
        "formula_type": formula.formula_type,
        "code": formula.code,
        "exported_at": datetime.now(timezone.utc).isoformat(),
    }
