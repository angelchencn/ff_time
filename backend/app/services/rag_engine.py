"""RAG engine service — stores and retrieves Fast Formula snippets via ChromaDB."""
from __future__ import annotations

import logging
from typing import Any, Optional

import chromadb
from chromadb.utils.embedding_functions import SentenceTransformerEmbeddingFunction

logger = logging.getLogger(__name__)

_COLLECTION_NAME = "fast_formulas"
_EMBEDDING_MODEL = "all-MiniLM-L6-v2"


class RAGEngine:
    """Retrieval-augmented generation engine backed by ChromaDB.

    Parameters
    ----------
    persist_dir:
        Directory where ChromaDB will persist its data.  If *None*, an
        in-memory client is used (useful for testing when tmp_path is not
        available, though tests should prefer tmp_path for isolation).
    """

    def __init__(self, persist_dir: Optional[str] = None) -> None:
        self._embedding_fn = SentenceTransformerEmbeddingFunction(
            model_name=_EMBEDDING_MODEL
        )

        if persist_dir is not None:
            self._client = chromadb.PersistentClient(path=persist_dir)
        else:
            self._client = chromadb.Client()

        self._collection = self._client.get_or_create_collection(
            name=_COLLECTION_NAME,
            embedding_function=self._embedding_fn,
            metadata={"hnsw:space": "cosine"},
        )

    # ------------------------------------------------------------------
    # Ingestion helpers
    # ------------------------------------------------------------------

    def add_formula(
        self,
        doc_id: str,
        code: str,
        metadata: Optional[dict[str, Any]] = None,
    ) -> None:
        """Upsert a Fast Formula snippet into the collection.

        Parameters
        ----------
        doc_id:
            Stable identifier for the document (used for upsert semantics).
        code:
            The Fast Formula source code.
        metadata:
            Optional key/value pairs stored alongside the document.
        """
        meta = dict(metadata or {})
        meta["code"] = code  # store raw code so we can return it on query

        self._collection.upsert(
            ids=[doc_id],
            documents=[code],
            metadatas=[meta],
        )
        logger.debug("Upserted formula doc_id=%s", doc_id)

    def add_document(
        self,
        doc_id: str,
        text: str,
        metadata: Optional[dict[str, Any]] = None,
    ) -> None:
        """Upsert an arbitrary text document into the collection.

        Parameters
        ----------
        doc_id:
            Stable identifier for the document.
        text:
            Plain text content to embed and store.
        metadata:
            Optional key/value pairs stored alongside the document.
        """
        meta = dict(metadata or {})
        # For non-formula documents there is no "code" field; store text as
        # the canonical content so callers get it back via the "code" key.
        meta["code"] = text

        self._collection.upsert(
            ids=[doc_id],
            documents=[text],
            metadatas=[meta],
        )
        logger.debug("Upserted document doc_id=%s", doc_id)

    # ------------------------------------------------------------------
    # Retrieval
    # ------------------------------------------------------------------

    def query(
        self,
        query_text: str,
        top_k: int = 5,
        min_similarity: float = 0.6,
        filter_metadata: Optional[dict[str, Any]] = None,
    ) -> list[dict[str, Any]]:
        """Query the collection for documents similar to *query_text*.

        Parameters
        ----------
        query_text:
            Natural language description of what the caller is looking for.
        top_k:
            Maximum number of results to return before similarity filtering.
        min_similarity:
            Minimum cosine similarity score (0–1) a result must meet.
            Results below this threshold are excluded.
        filter_metadata:
            Optional ChromaDB ``where`` filter applied server-side.

        Returns
        -------
        list[dict]
            Each dict has keys ``id``, ``code``, ``metadata``, ``similarity``.
            Returns an empty list when the collection is empty or no results
            pass the similarity threshold.
        """
        count = self._collection.count()
        if count == 0:
            return []

        effective_k = min(top_k, count)

        query_kwargs: dict[str, Any] = {
            "query_texts": [query_text],
            "n_results": effective_k,
            "include": ["metadatas", "distances"],
        }
        if filter_metadata:
            query_kwargs["where"] = filter_metadata

        raw = self._collection.query(**query_kwargs)

        results: list[dict[str, Any]] = []
        ids = raw.get("ids", [[]])[0]
        distances = raw.get("distances", [[]])[0]
        metadatas = raw.get("metadatas", [[]])[0]

        for doc_id, distance, meta in zip(ids, distances, metadatas):
            # ChromaDB cosine distance is in [0, 2]; convert to similarity in [0, 1].
            similarity = 1.0 - (distance / 2.0)
            if similarity < min_similarity:
                continue

            code = meta.get("code", "")
            # Return a clean metadata copy without the internal "code" field.
            clean_meta = {k: v for k, v in meta.items() if k != "code"}

            results.append(
                {
                    "id": doc_id,
                    "code": code,
                    "metadata": clean_meta,
                    "similarity": round(similarity, 4),
                }
            )

        return results
