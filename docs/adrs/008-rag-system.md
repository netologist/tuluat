# ADR 008: Retrieval-Augmented Generation (RAG) System

* **Status:** Accepted
* **Date:** 2026-08-07
* **Deciders:** Software Architecture Team

---

## Context and Problem Statement

Agents need domain knowledge beyond model training data and session memory. The platform already has pgvector (`session_long_memory`, `vector(1536)`) for long-term memory, but no document ingestion pipeline: no chunking, no object storage for source documents, and no query-time retrieval that agents can consume. Evre 2.5 (RAG) adds a first-class retrieval layer for agents.

## Decision Drivers
* **Grounding:** Agents must retrieve relevant document context before answering.
* **Separation of Storage:** Raw source documents (PDFs, markdown, reports) are large and binary — they belong in object storage, not in vector rows. Vector rows hold chunk text + embedding only.
* **S3 Compatibility:** Kubernetes-native deployments use MinIO / S3-compatible object storage; local dev and CI must work without it.
* **Deterministic Tests:** Embedding providers must be swappable so unit tests do not require external model APIs.
* **Reuse:** The existing pgvector extension and Postgres deployment are reused rather than introducing a new vector store.

## Decision Outcome

A **RAG pipeline in `tuluat-engine`** (`com.tuluat.engine.rag`) with four swappable SPIs and one orchestrator:

### 1. Chunking — `DocumentChunker`
* `Chunker` interface: `List<TextChunk> chunk(String content, ChunkConfig config)`.
* Default: **recursive character chunking** (split on paragraph → newline → sentence → word boundaries, then hard split) with `chunkSize` and `overlap` (default 1200 / 150 chars).
* A `TextChunk` carries `chunkIndex`, `content`, and `sourceRef`.

### 2. Object Storage — `ObjectStorage` SPI
* `put(key, bytes, contentType)`, `get(key)`, `delete(key)`, `list(prefix)`.
* **`LocalObjectStorage`** (default profile): files under `tuluat.rag.storage.local-dir` (`./data/rag`). Used in dev and CI.
* **`S3ObjectStorage`** (MinIO SDK, `minio://` endpoints): production path in `tuluat-system`; endpoint/bucket/credentials from env (`RAG_S3_ENDPOINT`, `RAG_S3_BUCKET`, `RAG_S3_ACCESS_KEY`, `RAG_S3_SECRET_KEY`). No plain-text secrets in manifests.
* Selection via `tuluat.rag.storage.type=local|s3` (default `local`).

### 3. Embedding — `EmbeddingProvider` SPI
* `float[] embed(String text)` — **1536-dimensional** to match the existing pgvector column.
* **`LocalHashEmbeddingProvider`** (default): deterministic bag-of-char-ngram hashing → reproducible vectors, no external API. Suitable for dev, CI, and E2E.
* **`SpringAiEmbeddingProvider`** (optional): wraps Spring AI `EmbeddingModel` when an OpenAI/Ollama embedding model bean is present — production quality.

### 4. Retrieval — `Retriever` SPI
* `List<RetrievedChunk> retrieve(String query, int topK)` — returns chunk text, source, and similarity score.
* **`PgVectorRetriever`** (default): cosine similarity via `<=>` on `rag_chunks.embedding` (`ORDER BY embedding <=> ?::vector LIMIT ?`).
* **`InMemoryRetriever`**: cosine similarity over an in-memory list — used in unit tests and as fallback when Postgres is unavailable.

### 5. Orchestrator — `RagService`
* `ingest(String sourceRef, String content, String metadataJson)`:
  chunk → embed each chunk → persist to `rag_chunks` (pgvector) → store raw document in object storage (key: `documents/<sourceRef>/<docId>.txt`).
* `retrieve(String query, int topK)`: embed query → `Retriever.retrieve` → `RagContext` that agents merge into system prompts.
* `deleteDocument(String sourceRef)`: remove chunks + object.

### 6. Persistence — `rag_chunks` table (new migration V4)
```sql
CREATE TABLE IF NOT EXISTS rag_chunks (
    id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(255) NOT NULL,
    source_ref VARCHAR(512) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rag_chunks_source ON rag_chunks(source_ref);
```

### Agent Consumption
`RagService.retrieve` output is exposed as a `RagContext` (list of `RetrievedChunk`). A future wiring step (ADR 007 placement) merges it into `AgentExecutionService` system prompts when the agent declares `spec.rag: { enabled, topK, sources[] }` — the CRD field is out of scope for this ADR's implementation and added with agent wiring.

## Positives
* **Swappable SPIs:** local dev, CI (LocalObjectStorage + InMemoryRetriever) and production (S3 + pgvector) run the same code paths.
* **Reuses pgvector:** no new infrastructure; `session_long_memory` pattern extended.
* **Deterministic embeddings:** E2E and unit tests do not depend on external embedding APIs or paid keys.

## Negatives
* **LocalHashEmbeddingProvider** is not semantically strong — acceptable for dev/E2E; production must configure a real embedding model.
* S3 path requires a MinIO/S3 deployment in-cluster (out of scope; documented in A5/ops).
