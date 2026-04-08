"""Extended API route tests for new endpoints."""
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_formula_types_endpoint():
    resp = client.get("/api/formula-types")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) >= 9
    # Each type has required fields
    for t in data:
        assert "type_name" in t
        assert "display_name" in t
        assert "sample_prompts" in t


def test_formula_types_template_endpoint():
    resp = client.get("/api/formula-types/Oracle%20Payroll/template")
    assert resp.status_code == 200
    data = resp.json()
    assert data["display_name"] == "Oracle Payroll"
    assert "skeleton" in data
    assert len(data["skeleton"]) > 100


def test_formula_types_template_unknown():
    resp = client.get("/api/formula-types/NONEXISTENT/template")
    assert resp.status_code == 200
    data = resp.json()
    assert "error" in data


def test_validate_with_concat_operator():
    code = "msg = 'Hello' || ' World'\nRETURN msg"
    resp = client.post("/api/validate", json={"code": code})
    assert resp.status_code == 200
    assert resp.json()["valid"] is True


def test_validate_with_get_context():
    code = "ffs_id = GET_CONTEXT(HWM_FFS_ID, 0)\nRETURN ffs_id"
    resp = client.post("/api/validate", json={"code": code})
    assert resp.status_code == 200
    assert resp.json()["valid"] is True


def test_validate_with_was_not_defaulted():
    code = "DEFAULT FOR x IS 0\nIF x WAS NOT DEFAULTED THEN\n  y = x\nELSE\n  y = 0\nENDIF\nRETURN y"
    resp = client.post("/api/validate", json={"code": code})
    assert resp.status_code == 200
    assert resp.json()["valid"] is True


def test_simulate_with_concat():
    code = "DEFAULT FOR name IS 'World'\nresult = 'Hello ' || name\nRETURN result"
    resp = client.post("/api/simulate", json={"code": code, "input_data": {"name": "Oracle"}})
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "SUCCESS"
    assert data["output_data"]["result"] == "Hello Oracle"
