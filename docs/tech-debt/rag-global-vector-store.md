# Tech Debt: Global RAG Vector Store — Must Move to Agent/Workflow-Level Isolation

- **Status:** Accepted (PoC trade-off)
- **Date:** 2026-08-11
- **Related:** [ADR 008 — RAG System](../adrs/008-rag-system.md)

## Current State

`RagService` is a singleton `@Service` bean. Every agent execution — regardless of
which `AiAgent` CRD triggered it — retrieves context from the **same pgvector
`rag_chunks` table** and the **same MinIO S3 bucket**. There is no per-agent
isolation, filtering, or configuration in the `AiAgentSpec` CRD.

### Code path
```
Any AiAgent → AgentExecutionService.executeAgent() / processAgentPrompt()
  → invokeResolvedAgent() / buildSystemPrompt()
    → retrieveRagContext(query)         ← no agent discriminator
    → appends ALL matching chunks
```

### What works
- Document ingestion (`POST /api/v1/rag/ingest`, `RagService.ingest`)
- Retrieval with source attribution (`[sourceRef #N (sim X.XX)]`)
- Embabel goal-agent path includes RAG context (fixed 2026-08-11)

### What is missing (per-agent RAG config)

| Feature | Gap |
|---|---|
| **Agent-scoped sources** | Agent A ingests `reports/acme`; Agent B queries → Agent B sees Acme data. No namespace/isolation. |
| **RAG on/off per agent** | `AiAgentSpec` has no `rag` field. All agents get RAG context or none do. |
| **Source allowlist/denylist** | No way to restrict an agent to `sourceRef` prefixes (e.g. `reports/finance/*`). |
| **Top-K per agent** | `RAG_RESULT_COUNT = 3` is hardcoded in `AgentExecutionService`. |
| **Embedding model per agent** | Single `EmbeddingProvider` bean; can't use different embeddings for different knowledge domains. |
| **Chunk config per source** | `ChunkConfig.defaults()` used for all ingestions. No per-document-type chunk sizing. |

## Proposed `AiAgentSpec.rag` (future CRD field)

```yaml
spec:
  rag:
    enabled: true
    sources:
      - prefix: "reports/finance/"
      - prefix: "runbooks/"
    topK: 5
    minSimilarity: 0.6
```

Backward-compatible: if `rag` is absent, current global behaviour is preserved.


## Rationale: Why Agent/Workflow-Level Isolation Is Required

In a multi-department organization, different teams ingest documents into the
same operator instance:

| Department | Source prefix | Sensitivity |
|---|---|---|
| Finance | `reports/finance/` | Confidential: earnings, M&A, forecasts |
| Legal | `contracts/legal/` | Attorney-client privileged |
| HR | `policies/hr/` | PII: compensation, performance reviews |
| Engineering | `runbooks/eng/` | Internal: incident post-mortems |

With the current global vector store, a **Finance Analyst agent** retrieving
context for an earnings query could surface chunks from **HR compensation data**
or **Legal contracts** if cosine similarity happens to match. This is:

- **A privacy violation** when PII crosses departmental boundaries.
- **A compliance risk** for regulated industries (SOX, GDPR, HIPAA).
- **A data leakage vector** between tenants in a shared operator deployment.

Each `AiAgent` (or `AiWorkflow`) must own its retrieval scope so that:

- Finance agents query **only** `reports/finance/*` vector partitions.
- Legal agents query **only** `contracts/legal/*`.
- Cross-department queries are explicitly configured, never accidental.

## Risk if not addressed
 
1. **Privacy / PII leakage:** HR documents ingested for one agent can surface in
   another agent's RAG context with no access control boundary.
2. **Departmental data isolation:** Finance, Legal, HR, and Engineering
   documents share one vector space — no `WHERE source_ref LIKE 'finance/%'`
   filter is applied at query time.
3. **Noise / context pollution:** Irrelevant chunks waste prompt tokens and
   degrade answer quality by flooding the system prompt with unrelated data.
4. **Multi-tenancy:** Impossible to host multiple tenants on one operator
   instance with isolated knowledge bases; a tenant's documents are globally
   searchable.

## Mitigation (PoC phase — temporary)

- Use descriptive `sourceRef` prefixes (e.g. `reports/{department}/{doc}`) so
  that when per-agent filtering IS implemented, data is already partitioned.
- Rely on cosine similarity to naturally rank relevant chunks higher — adequate
  for single-department demos where all ingested docs belong to the same domain.
- **Must be resolved before** any multi-department or multi-tenant production
  deployment.
