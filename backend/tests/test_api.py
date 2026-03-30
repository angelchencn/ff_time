"""API route tests — validate, simulate, dbi endpoints."""
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_validate_valid_formula():
    resp = client.post("/api/validate", json={"code": "DEFAULT FOR hours IS 0\nRETURN hours"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["valid"] is True


def test_validate_invalid_formula():
    resp = client.post("/api/validate", json={"code": "IF hours > THEN"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["valid"] is False
    assert len(data["diagnostics"]) > 0


def test_simulate_formula():
    resp = client.post("/api/simulate", json={
        "code": "DEFAULT FOR hours IS 0\not_pay = hours * 1.5\nRETURN ot_pay",
        "input_data": {"hours": 10}
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "SUCCESS"


def test_dbi_list():
    resp = client.get("/api/dbi")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) > 0
    assert "name" in data[0]


def test_dbi_search():
    resp = client.get("/api/dbi?search=hours")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) > 0
    for item in data:
        assert "hours" in item["name"].lower() or "hours" in item.get("description", "").lower()


def test_validate_missing_code():
    resp = client.post("/api/validate", json={})
    assert resp.status_code == 422


def test_simulate_missing_fields():
    resp = client.post("/api/simulate", json={})
    assert resp.status_code == 422
