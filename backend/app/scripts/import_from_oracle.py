"""Import Fast Formula data from Oracle database into ChromaDB knowledge base.

Usage
-----
    python -m app.scripts.import_from_oracle [--chroma-dir PATH] [--batch-size N] [--dry-run]

Connects to the Oracle FF_FORMULAS_VL table and bulk-imports formulas into the
RAG engine for retrieval-augmented generation.
"""
from __future__ import annotations

import argparse
import logging
import sys
import time
from pathlib import Path
from typing import Any

import oracledb

from app.services.rag_engine import RAGEngine

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
logger = logging.getLogger(__name__)

_BACKEND_DIR = Path(__file__).resolve().parent.parent.parent
_DEFAULT_CHROMA_DIR = _BACKEND_DIR / "data" / "chroma"

# Oracle connection defaults
_ORACLE_DSN = "phxvm72.appsdev1.fusionappsdphx1.oraclevcn.com:1572/ems5150_FDB"
_ORACLE_USER = "fusion"
_ORACLE_PASSWORD = "fusion"

_QUERY = """\
SELECT FFL.base_formula_name,
       FFL.formula_name,
       FFL.description,
       FFL.formula_text,
       FFT.formula_type_name
  FROM FF_FORMULAS_VL FFL,
       FF_FORMULA_TYPES FFT
 WHERE FFL.FORMULA_TYPE_ID = FFT.FORMULA_TYPE_ID
   AND FFL.formula_text IS NOT NULL
"""

_BATCH_SIZE = 500


def _parse_dsn(dsn: str) -> str:
    """Convert a simple DSN string to an oracledb-compatible connect string.

    Accepts formats:
      - host:port:dbname  (colon-separated, treated as service_name)
      - host:port/dbname  (slash-separated, passed as-is)
      - Full TNS descriptor (passed as-is)
    """
    if dsn.startswith("("):
        return dsn
    if "/" in dsn.split(":")[-1] if ":" in dsn else False:
        return dsn
    parts = dsn.split(":")
    if len(parts) == 3:
        host, port, db = parts
        return oracledb.makedsn(host, int(port), service_name=db)
    return dsn


def _connect_oracle(dsn: str, user: str, password: str) -> oracledb.Connection:
    """Create an Oracle thin-mode connection."""
    resolved_dsn = _parse_dsn(dsn)
    logger.info("Connecting to Oracle: %s@%s", user, resolved_dsn)
    conn = oracledb.connect(user=user, password=password, dsn=resolved_dsn)
    logger.info("Connected successfully.")
    return conn


def _fetch_formulas(conn: oracledb.Connection) -> list[dict[str, Any]]:
    """Fetch all formulas from Oracle."""
    # Disable CLOB fetching — return strings directly for better performance.
    # This tells oracledb to fetch CLOB/NCLOB as plain strings.
    oracledb.defaults.fetch_lobs = False

    cursor = conn.cursor()
    cursor.prefetchrows = 1000
    cursor.arraysize = 1000
    cursor.execute(_QUERY)

    columns = [col[0].lower() for col in cursor.description]
    rows = []
    for row in cursor:
        rows.append(dict(zip(columns, row)))

    cursor.close()
    logger.info("Fetched %d formulas from Oracle.", len(rows))
    return rows


def _import_batch(
    engine: RAGEngine,
    formulas: list[dict[str, Any]],
    dry_run: bool = False,
) -> int:
    """Import a list of formulas into the RAG engine. Returns count imported."""
    imported = 0
    for formula in formulas:
        code = formula.get("formula_text") or ""
        if not code.strip():
            continue

        base_name = formula.get("base_formula_name") or "unknown"
        formula_name = formula.get("formula_name") or base_name
        formula_type = formula.get("formula_type_name") or "unknown"
        description = formula.get("description") or ""

        doc_id = f"oracle_{base_name}"

        metadata: dict[str, Any] = {
            "source": "oracle_db",
            "formula_name": str(formula_name),
            "base_formula_name": str(base_name),
            "formula_type": str(formula_type),
        }
        if description:
            metadata["description"] = str(description)[:500]

        if dry_run:
            logger.info("[DRY RUN] Would upsert: %s (type=%s, %d chars)",
                        doc_id, formula_type, len(code))
        else:
            engine.add_formula(doc_id=doc_id, code=code, metadata=metadata)

        imported += 1

    return imported


def run(
    chroma_dir: Path,
    dsn: str,
    user: str,
    password: str,
    batch_size: int = _BATCH_SIZE,
    dry_run: bool = False,
    formula_type: str | None = None,
) -> int:
    """Main import logic. Returns total number of formulas imported."""
    conn = _connect_oracle(dsn, user, password)
    try:
        formulas = _fetch_formulas(conn)
    finally:
        conn.close()

    if formula_type:
        before = len(formulas)
        formulas = [
            f for f in formulas
            if (f.get("formula_type_name") or "").upper() == formula_type.upper()
        ]
        logger.info("Filtered to %d formulas (type=%s, was %d)",
                     len(formulas), formula_type, before)

    if not formulas:
        logger.warning("No formulas to import.")
        return 0

    engine = RAGEngine(persist_dir=str(chroma_dir)) if not dry_run else None

    total = 0
    start = time.time()

    for i in range(0, len(formulas), batch_size):
        batch = formulas[i : i + batch_size]
        count = _import_batch(engine, batch, dry_run=dry_run)
        total += count
        elapsed = time.time() - start
        logger.info("Progress: %d / %d imported (%.1fs elapsed)",
                     total, len(formulas), elapsed)

    elapsed = time.time() - start
    logger.info("Done. Imported %d formulas in %.1fs.", total, elapsed)
    return total


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Import Fast Formulas from Oracle DB into ChromaDB."
    )
    parser.add_argument(
        "--chroma-dir", type=Path, default=_DEFAULT_CHROMA_DIR,
        help=f"ChromaDB persistence directory (default: {_DEFAULT_CHROMA_DIR})",
    )
    parser.add_argument(
        "--dsn", default=_ORACLE_DSN,
        help="Oracle DSN (default: %(default)s)",
    )
    parser.add_argument(
        "--user", default=_ORACLE_USER,
        help="Oracle username (default: %(default)s)",
    )
    parser.add_argument(
        "--password", default=_ORACLE_PASSWORD,
        help="Oracle password",
    )
    parser.add_argument(
        "--batch-size", type=int, default=_BATCH_SIZE,
        help="Batch size for import progress logging (default: %(default)s)",
    )
    parser.add_argument(
        "--formula-type", default=None,
        help="Only import formulas of this type (e.g. 'Oracle Payroll')",
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Preview what would be imported without writing to ChromaDB",
    )
    return parser


def main(argv: list[str] | None = None) -> None:
    args = _build_parser().parse_args(argv)
    args.chroma_dir.mkdir(parents=True, exist_ok=True)

    run(
        chroma_dir=args.chroma_dir,
        dsn=args.dsn,
        user=args.user,
        password=args.password,
        batch_size=args.batch_size,
        dry_run=args.dry_run,
        formula_type=args.formula_type,
    )


if __name__ == "__main__":
    main()
