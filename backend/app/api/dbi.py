"""DBI registry API route — GET /api/dbi."""
from __future__ import annotations

import json
import os
from typing import Any, Optional

from fastapi import APIRouter, Query

router = APIRouter()

_DBI_JSON_PATH = os.path.normpath(
    os.path.join(
        os.path.dirname(__file__),
        "..",
        "..",
        "data",
        "dbi_registry",
        "time_labor_dbis.json",
    )
)


def _load_dbis() -> list[dict[str, Any]]:
    """Load DBI records from the JSON registry file."""
    with open(_DBI_JSON_PATH, encoding="utf-8") as fh:
        return json.load(fh)


@router.get("/api/dbi")
def list_dbis(
    module: Optional[str] = Query(default=None, description="Filter by module name"),
    search: Optional[str] = Query(default=None, description="Search term for name or description"),
) -> list[dict[str, Any]]:
    """Return DBI records, optionally filtered by module or search term."""
    records = _load_dbis()

    if module:
        records = [r for r in records if r.get("module", "").lower() == module.lower()]

    if search:
        term = search.lower()
        records = [
            r for r in records
            if term in r.get("name", "").lower() or term in r.get("description", "").lower()
        ]

    return records
