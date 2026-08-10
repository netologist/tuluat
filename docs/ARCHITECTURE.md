# Kubernetes AI Operator & Workflow Architecture

This document provides a comprehensive technical overview of the **Kubernetes AI Operator & Workflow Engine** architecture, detailing custom resources, graph state machines, hybrid triggering mechanisms, PostgreSQL/Pgvector memory management, and deployment topology.

---

## 1. System Overview & High-Level Architecture

The operator extends Kubernetes with declarative AI resources (`LlmProvider`, `AiAgent`, `AiWorkflow`, `WorkflowSession`). It orchestrates multi-agent workflows using an embedded **Graph State Machine Engine** and persists session states, conversation history, and vector embeddings in PostgreSQL (with `pgvector`).

```mermaid
graph TD
    subgraph Kubernetes Control Plane
        CRD1[LlmProvider CRD]
        CRD2[AiAgent CRD]
        CRD3[AiWorkflow CRD]
        CRD4[WorkflowSession CRD]
    end

    subgraph AI Operator Service Pod
        Rec1[LlmProviderReconciler]
        Rec2[AiAgentReconciler]
        Rec3[AiWorkflowReconciler]
        Rec4[WorkflowSessionReconciler]
        
        API[REST & WebSocket API Controller]
        ExecSvc[WorkflowExecutionService]
        Engine[GraphStateMachineEngine]
        AgentExec[AgentExecutionService]
        MemMgr[SessionMemoryManager]
    end

    subgraph Data & Persistence Layer
        Postgres[(PostgreSQL DB)]
        PgVector[(Pgvector Vector Store)]
    end

    subgraph External LLM Providers
        OpenAI[OpenAI API / DeepSeek / Ollama]
    end

    CRD1 --> Rec1
    CRD2 --> Rec2
    CRD3 --> Rec3
    CRD4 --> Rec4

    Rec4 --> ExecSvc
    API --> ExecSvc
    ExecSvc --> Engine
    Engine --> AgentExec
    Engine --> MemMgr

    AgentExec --> OpenAI
    MemMgr --> Postgres
    MemMgr --> PgVector
```

---

## 2. Custom Resource Definitions (CRDs)

| CRD Kind | ApiVersion | Scope | Description |
| :--- | :--- | :--- | :--- |
| `LlmProvider` | `ai.tuluat.com/v1alpha1` | Namespaced | Configures LLM backend (OpenAI, DeepSeek, Ollama, WireMock) and secret credentials. |
| `AiAgent` | `ai.tuluat.com/v1alpha1` | Namespaced | Defines an AI agent with custom system prompts, model overrides, active skills, guardrails, and Ingress config. |
| `AiWorkflow` | `ai.tuluat.com/v1alpha1` | Namespaced | Declarative graph template containing nodes (`AGENT`, `CONDITION`, `HUMAN_APPROVAL`, `TOOL`), edges, and memory policies. |
| `WorkflowSession` | `ai.tuluat.com/v1alpha1` | Namespaced | Kubernetes-native declarative trigger for executing a workflow session and tracking phase/status. |
| `McpServer` | `ai.tuluat.com/v1alpha1` | Namespaced | Registers external Model Context Protocol (MCP) server endpoints over SSE or stdio. |
---

## 3. Hybrid Triggering & Lifecycle Sequence

Oturumlar (Sessions) hem **Declarative Kubernetes GitOps** (`WorkflowSession` CRD) hem de **Imperative HTTP REST / WebSocket API** üzerinden başlatılabilir.

```mermaid
sequenceDiagram
    autonumber
    actor Client as User / Frontend / GitOps
    participant K8s as Kubernetes API Server
    participant Rec as WorkflowSessionReconciler
    participant API as WorkflowSessionController
    participant Svc as WorkflowExecutionService
    participant Engine as GraphStateMachineEngine
    participant DB as PostgreSQL + Pgvector

    alt Trigger via K8s CRD (GitOps)
        Client->>K8s: kubectl apply -f session.yaml
        K8s->>Rec: Event: WorkflowSession Created
        Rec->>Svc: startSession(workflowName, spec, input)
    else Trigger via REST API
        Client->>API: POST /api/v1/workflows/{name}/sessions
        API->>Svc: startSession(workflowName, spec, input)
    end

    Svc->>DB: INSERT INTO workflow_sessions (status = 'RUNNING')
    
    loop Graph Traversal (Until COMPLETED/FAILED)
        Svc->>Engine: executeNextStep(workflowSpec, session)
        Engine->>Engine: Evaluate Node (AGENT / CONDITION / TOOL)
        
        alt Node is AGENT
            Engine->>DB: Load & Save Short Memory
            Engine->>DB: Query Vector Embedding (Long Memory)
            Engine->>Engine: Call AgentExecutionService -> LLM
            Engine->>DB: Store Output in Context JSONB
        else Node is CONDITION
            Engine->>Engine: SpEL Evaluation against session.data
        end
        
        Engine->>DB: UPDATE workflow_sessions (current_node_id, loop_count)
    end

    Svc->>DB: UPDATE workflow_sessions (status = 'COMPLETED')
    
    opt Update CRD Status if triggered via K8s
        Rec->>K8s: Update WorkflowSession.status (phase = 'COMPLETED')
    end

    Svc-->>Client: Final Response / WS Event STREAM
```

---

## 4. Graph State Machine & Node Execution Flow

`GraphStateMachineEngine` düğümleri ve kenarları aşağıdaki mantıkla işler:

```mermaid
flowchart TD
    Start([Session Initiated]) --> FetchInitialNode[Fetch Initial Node from AiWorkflow]
    FetchInitialNode --> CheckLoopGuard{loop_count >= maxLoops?}
    
    CheckLoopGuard -- Yes --> SetFailed[Set Status = FAILED & Return]
    CheckLoopGuard -- No --> CheckNodeType{Node Type?}
    
    CheckNodeType -- AGENT --> ExecAgent[Resolve Prompt & Inject Memory]
    ExecAgent --> CallLLM[Execute Agent via AgentExecutionService]
    CallLLM --> SaveAgentOutput[Save Output to context_data JSONB]
    SaveAgentOutput --> FindNextEdge[Find Outgoing Edge]

    CheckNodeType -- CONDITION --> EvalSpEL[Evaluate SpEL Expression on context_data]
    EvalSpEL --> BranchEdge{Expression Result?}
    BranchEdge -- true --> EdgeTrue[Select Edge with condition='true']
    BranchEdge -- false --> EdgeFalse[Select Edge with condition='false']
    EdgeTrue --> FindNextEdge
    EdgeFalse --> FindNextEdge

    FindNextEdge --> NextNodeExists{Has Next Node?}
    NextNodeExists -- Yes --> IncrementLoop[Increment loop_count & Update DB]
    IncrementLoop --> CheckLoopGuard
    NextNodeExists -- No --> SetCompleted[Set Status = COMPLETED & Return]
```

---

## 5. PostgreSQL + Pgvector Memory Architecture

Memory iki katmandan oluşur:
1. **Short Memory:** Oturum içi konuşma geçmişi (`session_short_memory`).
2. **Long Memory:** `pgvector` uzantısı kullanılarak metin ve semantik vektör araması yapılan ortak veya oturum-bazlı bellek (`session_long_memory`).

```mermaid
erDiagram
    WORKFLOW_SESSIONS ||--o{ SESSION_SHORT_MEMORY : "has short memory"
    WORKFLOW_SESSIONS ||--o{ SESSION_LONG_MEMORY : "references long memory"

    WORKFLOW_SESSIONS {
        uuid session_id PK
        string workflow_name
        string status
        string current_node_id
        int loop_count
        jsonb context_data
        timestamp created_at
        timestamp updated_at
    }

    SESSION_SHORT_MEMORY {
        bigserial id PK
        uuid session_id FK
        string agent_name
        string role
        text content
        timestamp created_at
    }

    SESSION_LONG_MEMORY {
        bigserial id PK
        uuid session_id FK
        string workflow_name
        text content
        vector_1536 embedding
        jsonb metadata
        timestamp created_at
    }
```

---

## 6. Resilience & Pod Crash Recovery

- **Transactional Node State:** Her düğüm geçişinde oturum durumu PostgreSQL veritabanında `@Transactional` olarak güncellenir.
- **Crash Recovery:** Operator pod'u herhangi bir nedenle kapanıp tekrar açıldığında, `workflow_sessions` tablosunda `RUNNING` veya `PENDING` olan oturumlar sorgulanır ve kalınan `current_node_id` üzerinden akış kaldığı yerden devam ettirilir.
- **Loop Guards:** Graf içinde oluşabilecek sonsuz döngülere karşı varsayılan `maxLoops` (örn. 10) kontrolü ile oturum güvenle `FAILED` durumuna çekilir.

---

## 7. Service Connections & Port-Forwarding Guide

The application connects to infrastructure services as follows:
- **PostgreSQL Database (`postgres-service:5432`):** Connected via `spring.datasource.url` for session state, chat history, and vector embeddings.
- **Prometheus Telemetry:** Exposes metrics on `/actuator/prometheus` scraped by Prometheus Server (`prometheus-service:9090`).
- **Temporal Engine (`temporal-service:7233`):** Connected via `spring.temporal.target` for durable execution and human approval signals.

### Port-Forward Commands
```bash
# Control Portal REST API & Telemetry (8089 -> 8080)
kubectl port-forward svc/tuluat-operator-service 8089:8080 -n tuluat-system

# MinIO S3 Object Storage (API: 9000, Console: 9001)
kubectl port-forward svc/minio-service 9000:9000 9001:9001 -n tuluat-system

# Temporal Web UI (8233)
kubectl port-forward svc/temporal-ui-service 8233:8233 -n tuluat-system

# Grafana Dashboard (3000)
kubectl port-forward svc/grafana-service 3000:3000 -n tuluat-system

# Prometheus Metrics Server (9090)
kubectl port-forward svc/prometheus-service 9090:9090 -n tuluat-system

# PostgreSQL + Pgvector Database (5432)
kubectl port-forward svc/postgres-service 5432:5432 -n tuluat-system
```
