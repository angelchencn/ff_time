"""Formula types API route — GET /api/formula-types."""
from __future__ import annotations

import json
import os
from typing import Any

from fastapi import APIRouter

from app.services.formula_templates import get_template

router = APIRouter()

_REGISTRY_PATH = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "..", "data", "formula_types_registry.json")
)


def _load_registry() -> list[dict[str, Any]]:
    """Load all formula types from the JSON registry, merging template data where available."""
    try:
        with open(_REGISTRY_PATH, encoding="utf-8") as fh:
            registry = json.load(fh)
    except (OSError, json.JSONDecodeError):
        # Fallback to templates-only if registry file is missing
        from app.services.formula_templates import get_all_types
        return get_all_types()

    result: list[dict[str, Any]] = []
    for entry in registry:
        type_name = entry["type_name"]
        template = get_template(type_name)

        item: dict[str, Any] = {
            "type_name": type_name,
            "display_name": template.display_name if template else entry.get("display_name", type_name),
            "description": template.description if template else "",
            "formula_count": entry.get("formula_count", 0),
            "sample_prompts": entry.get("sample_prompts", []),
        }

        # If we have a template with richer sample_prompts, prefer those
        if template and len(template.sample_prompts) > len(item["sample_prompts"]):
            item["sample_prompts"] = list(template.sample_prompts)

        result.append(item)

    return result


@router.get("/api/formula-types")
def list_formula_types() -> list[dict[str, Any]]:
    """Return all available formula types with display names and sample prompts."""
    return _load_registry()


@router.get("/api/formula-types/{type_name}/template")
def get_formula_template(type_name: str) -> dict:
    """Return the skeleton template for a specific formula type."""
    template = get_template(type_name)
    if template is None:
        return {"error": f"Unknown formula type: {type_name}"}
    return {
        "type_name": template.type_name,
        "display_name": template.display_name,
        "description": template.description,
        "naming_pattern": template.naming_pattern,
        "skeleton": template.skeleton,
        "example_snippet": template.example_snippet,
    }
