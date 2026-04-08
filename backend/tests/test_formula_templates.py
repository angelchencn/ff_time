"""Tests for the formula templates system."""
from app.services.formula_templates import get_all_types, get_template, _load_templates


def test_all_types_loads():
    templates = _load_templates()
    assert len(templates) >= 100


def test_each_type_has_skeleton():
    for name, tmpl in _load_templates().items():
        assert len(tmpl.skeleton) > 50, f"{name} has empty skeleton"


def test_get_template_returns_template():
    tmpl = get_template("WORKFORCE_MANAGEMENT_TIME_CALCULATION_RULES")
    assert tmpl is not None
    assert tmpl.display_name == "Time Calculation Rule"
    assert len(tmpl.skeleton) > 100


def test_get_template_unknown_returns_none():
    assert get_template("NONEXISTENT_TYPE") is None


def test_oracle_payroll_template():
    tmpl = get_template("Oracle Payroll")
    assert tmpl is not None
    assert "Oracle Payroll" in tmpl.skeleton


def test_skeleton_has_formula_name_placeholder():
    for name, tmpl in _load_templates().items():
        assert "{formula_name}" in tmpl.skeleton, f"{name} skeleton missing {{formula_name}}"


def test_auto_generated_types_available():
    """Types that previously had no template should now be available."""
    for tn in ["Flow Schedule", "Batch Loader", "Net to Gross", "Element Skip"]:
        tmpl = get_template(tn)
        assert tmpl is not None, f"{tn} template not found"
        assert len(tmpl.skeleton) > 50
