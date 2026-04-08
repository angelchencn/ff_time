"""Formula type templates — loads skeletons and naming conventions from JSON.

All 123 formula type templates are stored in `data/formula_type_templates.json`.
"""
from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class FormulaTemplate:
    """Immutable template for a specific formula type."""
    type_name: str
    display_name: str
    description: str
    naming_pattern: str
    skeleton: str
    example_snippet: str
    sample_prompts: tuple[str, ...] = ()


_TEMPLATES_PATH = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "..", "data", "formula_type_templates.json")
)

_templates: Optional[dict[str, FormulaTemplate]] = None


def _load_templates() -> dict[str, FormulaTemplate]:
    """Load all templates from JSON (cached after first call)."""
    global _templates  # noqa: PLW0603
    if _templates is not None:
        return _templates

    _templates = {}
    try:
        with open(_TEMPLATES_PATH, encoding="utf-8") as fh:
            data = json.load(fh)
        for type_name, entry in data.items():
            _templates[type_name] = FormulaTemplate(
                type_name=type_name,
                display_name=entry.get("display_name", type_name),
                description=entry.get("description", ""),
                naming_pattern=entry.get("naming_pattern", ""),
                skeleton=entry.get("skeleton", ""),
                example_snippet=entry.get("example_snippet", ""),
            )
    except (OSError, json.JSONDecodeError):
        pass
    return _templates


def get_template(formula_type: str) -> FormulaTemplate | None:
    """Look up a template by type name."""
    return _load_templates().get(formula_type)


def get_all_types() -> list[dict]:
    """Return a list of all available formula types for the frontend dropdown."""
    return [
        {
            "type_name": t.type_name,
            "display_name": t.display_name,
            "description": t.description,
            "sample_prompts": list(t.sample_prompts),
        }
        for t in _load_templates().values()
    ]
