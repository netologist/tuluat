# Tuluat AI Operator — Feature & Capability Matrix

This document lists all completed and in-progress features across **Phase 1**, **Phase 2**, and **Phase 3** of the Tuluat Kubernetes AI Operator architecture.

---

## Roadmap & Phase Status Summary

| Phase | Category | Feature | Reactor Module | Status |
|---|---|---|---|---|
| **Phase 1** | CRD Domain | 5 Custom Resource Definitions (`LlmProvider`, `AiAgent`, `AiWorkflow`, `WorkflowSession`, `McpServer`) | `tuluat-crd-domain` | ✅ Complete |
| **Phase 1** | Engine | Durable Execution Engine with Temporal & Graph State Machine | `tuluat-engine` | ✅ Complete |
| **Phase 1** | RAG | In-Memory & PgVector Hybrid Memory Store | `tuluat-engine` | ✅ Complete |
| **Phase 1** | Operator | Operator SDK Reconcilers (`LlmProvider`, `AiAgent`, `McpServer`) | `tuluat-operator` | ✅ Complete |
| **Phase 2** | Security | Safety Guardrails Engine (PII Masking, Prompt Injection, Output Validation) | `tuluat-guardrails` | ✅ Complete |
| **Phase 2** | Protocol | Model Context Protocol (MCP) Client Registry & Tool Discovery | `tuluat-protocols` | ✅ Complete |
| **Phase 2** | Protocol | Agent-to-Agent (A2A) Card & Remote Inter-Agent Relay | `tuluat-protocols` | ✅ Complete |
| **Phase 3** | Integration | Human-in-the-Loop (HITL) Signal & Approval Inbox API | `tuluat-engine` / `tuluat-app` | ✅ Complete |
| **Phase 3** | Packaging | Production Helm Chart & Bundled Infrastructure Deployment | `helm/tuluat-operator` | ✅ Complete |
| **Phase 3** | E2E Testing | Fully Automated KinD Cluster E2E Acceptance Test Suite | `.github/workflows/ci.yml` | ✅ Complete |
| **Phase 3** | Web GUI | Visual Workflow Builder & Interactive Approval Inbox UI | `tuluat-app` (Frontend) | 🔄 Underway |

---

## Detailed Feature Index

- **[Phase 2 Features — Guardrails, MCP & A2A Protocols](phase2-guardrails-mcp-a2a.md)**
  - Guardrail Pipeline (`PiiMaskingFilter`, `PromptInjectionFilter`, `OutputValidationFilter`)
  - MCP Client Registry & Dynamic Tool Discovery
  - A2A Agent Card Specification & Remote Agent Relay
- **[Phase 3 Features — HITL, Helm & Multi-Agent Orchestration](phase3-hitl-helm-orchestration.md)**
  - Human-in-the-Loop Approval Inbox API (`/api/v1/sessions/{id}/approve`)
  - Single-Chart Helm Deployment Architecture
  - KinD E2E Automated Integration Suite
- **[RAG Subsystem & Storage Guide](rag-system-guide.md)**
  - `RecursiveCharacterChunker` (1200 chars / 150 overlap)
  - MinIO / S3 (`S3ObjectStorage`) & Local Filesystem (`LocalObjectStorage`)
  - PostgreSQL `pgvector` HNSW Cosine Similarity Index
