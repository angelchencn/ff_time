"""Seed the ChromaDB knowledge base with Fast Formula samples.

Usage
-----
    python -m app.scripts.seed_knowledge_base [--samples-dir PATH] [--chroma-dir PATH]

By default the script reads ``.ff`` files from ``backend/data/samples/`` and
writes the ChromaDB index to ``backend/data/chroma``.  For each ``.ff`` file
the script also looks for a companion ``.json`` file with the same stem to
load additional metadata.
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

from app.services.rag_engine import RAGEngine

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
logger = logging.getLogger(__name__)

# Resolve paths relative to the package root (backend/)
_BACKEND_DIR = Path(__file__).resolve().parent.parent.parent
_DEFAULT_SAMPLES_DIR = _BACKEND_DIR / "data" / "samples"
_DEFAULT_CHROMA_DIR = _BACKEND_DIR / "data" / "chroma"


def _load_metadata(json_path: Path) -> dict:
    """Load metadata from a companion JSON file, returning empty dict on error."""
    if not json_path.exists():
        return {}
    try:
        data = json.loads(json_path.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            logger.warning("Metadata file %s is not a JSON object — skipping.", json_path)
            return {}
        return data
    except json.JSONDecodeError as exc:
        logger.warning("Failed to parse %s: %s — skipping metadata.", json_path, exc)
        return {}


def seed(samples_dir: Path, chroma_dir: Path) -> int:
    """Seed the knowledge base and return the number of upserted documents."""
    engine = RAGEngine(persist_dir=str(chroma_dir))
    upserted = 0

    # 1. Ingest .ff formula samples
    ff_files = sorted(samples_dir.glob("*.ff"))
    for ff_path in ff_files:
        doc_id = ff_path.stem
        code = ff_path.read_text(encoding="utf-8")

        companion_json = ff_path.with_suffix(".json")
        metadata = _load_metadata(companion_json)
        metadata.setdefault("source_file", ff_path.name)

        engine.add_formula(doc_id=doc_id, code=code, metadata=metadata)
        logger.info("Upserted formula: %s", doc_id)
        upserted += 1

    # 2. Ingest documentation files (.txt, .md) from data/docs/
    docs_dir = samples_dir.parent / "docs"
    if docs_dir.exists():
        for doc_path in sorted(docs_dir.glob("*.*")):
            if doc_path.suffix in (".txt", ".md"):
                doc_id = f"doc_{doc_path.stem}"
                text = doc_path.read_text(encoding="utf-8")
                metadata = {"source_file": doc_path.name, "doc_type": "reference"}
                engine.add_document(doc_id=doc_id, text=text, metadata=metadata)
                logger.info("Upserted doc: %s", doc_id)
                upserted += 1

    logger.info("Seeded %d document(s) into ChromaDB at %s", upserted, chroma_dir)
    return upserted


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Seed the ChromaDB knowledge base with Fast Formula samples."
    )
    parser.add_argument(
        "--samples-dir",
        type=Path,
        default=_DEFAULT_SAMPLES_DIR,
        help=f"Directory containing .ff files (default: {_DEFAULT_SAMPLES_DIR})",
    )
    parser.add_argument(
        "--chroma-dir",
        type=Path,
        default=_DEFAULT_CHROMA_DIR,
        help=f"Directory for ChromaDB persistence (default: {_DEFAULT_CHROMA_DIR})",
    )
    return parser


def main(argv: list[str] | None = None) -> None:
    args = _build_parser().parse_args(argv)

    samples_dir: Path = args.samples_dir
    chroma_dir: Path = args.chroma_dir

    if not samples_dir.exists():
        logger.error("Samples directory does not exist: %s", samples_dir)
        sys.exit(1)

    chroma_dir.mkdir(parents=True, exist_ok=True)

    seed(samples_dir=samples_dir, chroma_dir=chroma_dir)


if __name__ == "__main__":
    main()
