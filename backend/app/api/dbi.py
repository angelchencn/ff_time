"""DBI registry API route — GET /api/dbi."""
from __future__ import annotations

import json
import os
from typing import Any, Optional

from fastapi import APIRouter, Query

router = APIRouter()

_DBI_JSON_PATH = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "..", "data", "dbi_registry", "all_formula_dbis.json")
)

_FALLBACK_JSON_PATH = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "..", "data", "dbi_registry", "time_labor_dbis.json")
)

_cached_dbis: list[dict[str, Any]] | None = None


def _load_dbis() -> list[dict[str, Any]]:
    """Load DBI records from the JSON registry file (cached)."""
    global _cached_dbis  # noqa: PLW0603
    if _cached_dbis is not None:
        return _cached_dbis

    path = _DBI_JSON_PATH if os.path.exists(_DBI_JSON_PATH) else _FALLBACK_JSON_PATH
    try:
        with open(path, encoding="utf-8") as fh:
            _cached_dbis = json.load(fh)
    except (OSError, json.JSONDecodeError):
        _cached_dbis = []
    return _cached_dbis


@router.get("/api/dbi")
def list_dbis(
    module: Optional[str] = Query(default=None, description="Filter by module name"),
    search: Optional[str] = Query(default=None, description="Search term for name or description"),
    data_type: Optional[str] = Query(default=None, description="Filter by data type (NUMBER, TEXT, DATE)"),
    limit: int = Query(default=500, description="Max results to return"),
    offset: int = Query(default=0, description="Offset for pagination"),
) -> dict[str, Any]:
    """Return DBI records with filtering, pagination, and total count."""
    records = _load_dbis()

    if module:
        records = [r for r in records if r.get("module", "").lower() == module.lower()]

    if data_type:
        records = [r for r in records if r.get("data_type", "").upper() == data_type.upper()]

    if search:
        term = search.lower()
        records = [
            r for r in records
            if term in r.get("name", "").lower() or term in r.get("description", "").lower()
        ]

    total = len(records)
    page = records[offset:offset + limit]

    return {"total": total, "items": page}


@router.get("/api/dbi/modules")
def list_dbi_modules() -> list[dict[str, Any]]:
    """Return distinct modules with counts."""
    from collections import Counter
    records = _load_dbis()
    counts = Counter(r.get("module", "OTHER") for r in records)
    return [{"module": mod, "count": cnt} for mod, cnt in counts.most_common()]
