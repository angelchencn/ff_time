from app.services.rag_engine import RAGEngine


def test_add_and_query_formula(tmp_path):
    engine = RAGEngine(persist_dir=str(tmp_path / "chroma"))
    engine.add_formula(
        doc_id="test1",
        code="DEFAULT FOR hours IS 0\not_pay = hours * 1.5\nRETURN ot_pay",
        metadata={"formula_type": "TIME_LABOR", "use_case": "overtime"},
    )
    results = engine.query("calculate overtime pay", top_k=3)
    assert len(results) > 0
    assert "ot_pay" in results[0]["code"]


def test_query_empty_collection(tmp_path):
    engine = RAGEngine(persist_dir=str(tmp_path / "chroma"))
    results = engine.query("anything", top_k=3)
    assert results == []


def test_add_document_and_query(tmp_path):
    engine = RAGEngine(persist_dir=str(tmp_path / "chroma"))
    engine.add_document(
        doc_id="doc1",
        text="This formula calculates base pay from hourly rate and hours worked.",
        metadata={"source": "docs", "category": "pay"},
    )
    results = engine.query("hourly pay calculation", top_k=3)
    assert len(results) > 0
    assert results[0]["id"] == "doc1"


def test_similarity_filter(tmp_path):
    engine = RAGEngine(persist_dir=str(tmp_path / "chroma"))
    engine.add_formula(
        doc_id="formula1",
        code="base_pay = rate * hours\nRETURN base_pay",
        metadata={"formula_type": "PAY"},
    )
    # Query with very high min_similarity threshold should return empty
    results = engine.query("completely unrelated query", top_k=3, min_similarity=0.99)
    assert results == []


def test_query_returns_expected_fields(tmp_path):
    engine = RAGEngine(persist_dir=str(tmp_path / "chroma"))
    engine.add_formula(
        doc_id="formula2",
        code="vacation_pay = days * daily_rate\nRETURN vacation_pay",
        metadata={"formula_type": "LEAVE"},
    )
    results = engine.query("vacation pay", top_k=1)
    assert len(results) > 0
    result = results[0]
    assert "id" in result
    assert "code" in result
    assert "metadata" in result
    assert "similarity" in result


def test_upsert_existing_formula(tmp_path):
    engine = RAGEngine(persist_dir=str(tmp_path / "chroma"))
    engine.add_formula(
        doc_id="formula3",
        code="bonus = salary * 0.1\nRETURN bonus",
        metadata={"formula_type": "BONUS"},
    )
    # Upsert same doc_id with updated code
    engine.add_formula(
        doc_id="formula3",
        code="bonus = salary * 0.15\nRETURN bonus",
        metadata={"formula_type": "BONUS", "updated": True},
    )
    results = engine.query("bonus calculation", top_k=1)
    assert len(results) > 0
    # Should reflect updated code
    assert "0.15" in results[0]["code"]
