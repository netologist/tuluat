# Design Specification: Workflow Operator (AiWorkflow & WorkflowSession)

**Date:** 2026-08-06  
**Status:** Approved  
**Author:** Pair Programming Agent & User  

---

## 1. Overview

The **Workflow Operator** is a Kubernetes Operator extension designed to orchestrate multi-agent workflows (`AiAgent`) with support for graph state machines, agent-to-agent communication, hybrid execution triggering (K8s CRD + HTTP REST API), and stateful memory management (short-term session memory and long-term vector embeddings via PostgreSQL and Pgvector).

---

## 2. Goals & Non-Goals

### Goals
- **Declarative Workflows:** Define multi-agent execution graphs (`AiWorkflow`) declaratively using Kubernetes Custom Resource Definitions (CRDs).
- **Hybrid Triggering:** Support spawning workflow sessions via both `WorkflowSession` CRDs (K8s/GitOps native) and HTTP REST API calls.
- **Graph State Machine Engine:** Execute nodes (`AGENT`, `CONDITION`, `TOOL`) and transitions/edges with conditional evaluation (SpEL) and loop guards.
- **Agent-to-Agent Communication:** Enable agents within a workflow to hand off data, evaluate responses, and loop conditionally.
- **Unified Memory Architecture:** Store session state, short-term conversational history, and long-term vector embeddings (`pgvector`) in PostgreSQL.
- **Real-time Observability:** Stream execution progress and token usage via WebSocket endpoints.
- **Resilience:** Transactional node state persistence to allow pod crash recovery and state resumption.

### Non-Goals
- External event brokers (Kafka, RabbitMQ) are excluded for simplicity and low latency (YAGNI).
- Distributed multi-cluster pod per session deployment (Embedded Async State Machine approach is used).

---

## 3. Architecture & Components

```
+-----------------------------------------------------------------------------------+
|                                 Trigger Layer                                     |
|  +-----------------------------------+     +-----------------------------------+  |
|  |     WorkflowSession CRD           |     |         HTTP / WebSocket          |  |
|  +-----------------+-----------------+     +-----------------+-----------------+  |
+--------------------|-----------------------------------------|--------------------+
                     |                                         |
                     v                                         v
+--------------------+-----------------------------------------+--------------------+
|                             Core Workflow Operator                                |
|  +-----------------------------------------------------------------------------+  |
|  | WorkflowExecutionService                                                     |  |
|  |   - Orchestrates session lifecycle (create, start, pause, resume)           |  |
|  +--------------------------------------+--------------------------------------+  |
|                                         |                                         |
|                                         v                                         |
|  +-----------------------------------------------------------------------------+  |
|  | GraphStateMachineEngine                                                      |  |
|  |   - Evaluates AiWorkflow node graph, handles AGENT, CONDITION, TOOL nodes    |  |
|  |   - SpEL condition evaluation, maxLoops enforcement                         |  |
|  +---------------+-------------------------------------+-----------------------+  |
|                  |                                     |                          |
|                  v                                     v                          |
|  +---------------+---------------+     +---------------+---------------+          |
|  | AgentExecutionService         |     | SessionMemoryManager          |          |
|  |  - Calls LLM via ChatModel    |     |  - Short memory (Message history) |          |
|  |  - Executes active skills     |     |  - Long memory (Pgvector)     |          |
|  +---------------+---------------+     +---------------+---------------+          |
+------------------|-------------------------------------|--------------------------+
                   |                                     |
                   v                                     v
         +---------+---------+                 +---------+---------+
         | External LLMs     |                 | PostgreSQL      |
         | (OpenAI/DeepSeek) |                 | (+ Pgvector)    |
         +-------------------+                 +-------------------+
```

---

## 4. Custom Resource Definitions (CRDs)

### 4.1 `AiWorkflow` CRD (`ai.tuluat.com/v1alpha1`)
Defines the structure of a multi-agent execution graph.

```yaml
apiVersion: ai.tuluat.com/v1alpha1
kind: AiWorkflow
metadata:
  name: multi-agent-researcher
  namespace: default
spec:
  description: "Research and Report Multi-Agent Workflow"
  initialNode: "researcher-node"
  nodes:
    - id: "researcher-node"
      type: "AGENT"
      agentRef: "web-researcher-agent"
      inputTemplate: "Topic: {{session.input}}"
      outputKey: "research_data"
    - id: "evaluator-node"
      type: "CONDITION"
      expression: "session.data.research_data.contains('SUFFICIENT')"
    - id: "writer-node"
      type: "AGENT"
      agentRef: "report-writer-agent"
      inputTemplate: "Data: {{session.data.research_data}}"
      outputKey: "final_report"
  edges:
    - from: "researcher-node"
      to: "evaluator-node"
    - from: "evaluator-node"
      to: "writer-node"
      condition: "true"
    - from: "evaluator-node"
      to: "researcher-node"
      condition: "false"
  memoryConfig:
    shortMemorySize: 20
    enableLongMemory: true
    vectorTableName: "workflow_vectors"
```

### 4.2 `WorkflowSession` CRD (`ai.tuluat.com/v1alpha1`)
Declarative trigger and status representation for a workflow execution session.

```yaml
apiVersion: ai.tuluat.com/v1alpha1
kind: WorkflowSession
metadata:
  name: research-session-001
  namespace: default
spec:
  workflowRef: "multi-agent-researcher"
  input: "Kubernetes CRD Operator Best Practices 2026"
  parameters:
    maxLoops: 5
status:
  sessionId: "550e8400-e29b-41d4-a716-446655440000"
  phase: "RUNNING" # PENDING, RUNNING, COMPLETED, FAILED, PAUSED
  currentNode: "researcher-node"
  output: ""
  startTime: "2026-08-06T18:50:00Z"
  endTime: null
```

---

## 5. Database Schema (PostgreSQL + Pgvector)

### 5.1 `workflow_sessions`
Stores runtime session state and context variables.
- `session_id` (`UUID`, Primary Key)
- `workflow_name` (`VARCHAR(255)`, Not Null)
- `status` (`VARCHAR(50)`, Not Null - `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `PAUSED`)
- `current_node_id` (`VARCHAR(255)`)
- `loop_count` (`INT`, Default 0)
- `context_data` (`JSONB`, Default `{}`)
- `created_at` (`TIMESTAMP`, Default `NOW()`)
- `updated_at` (`TIMESTAMP`, Default `NOW()`)

### 5.2 `session_short_memory`
Stores short-term conversational message history for the active session.
- `id` (`BIGSERIAL`, Primary Key)
- `session_id` (`UUID`, Foreign Key -> `workflow_sessions.session_id`)
- `agent_name` (`VARCHAR(255)`)
- `role` (`VARCHAR(50)` - `system`, `user`, `assistant`, `tool`)
- `content` (`TEXT`)
- `created_at` (`TIMESTAMP`, Default `NOW()`)

### 5.3 `session_long_memory` (Pgvector)
Stores semantic embeddings for long-term recall across sessions or globally per workflow.
- `id` (`BIGSERIAL`, Primary Key)
- `session_id` (`UUID`, Nullable, Foreign Key)
- `workflow_name` (`VARCHAR(255)`, Not Null)
- `content` (`TEXT`, Not Null)
- `embedding` (`VECTOR(1536)`, Not Null) -- Configurable dimension based on model
- `metadata` (`JSONB`, Default `{}`)
- `created_at` (`TIMESTAMP`, Default `NOW()`)

---

## 6. Execution Engine & State Machine

1. **Session Lifecycle:**
   - Client creates `WorkflowSession` CRD OR sends `POST /api/v1/workflows/{name}/sessions`.
   - `WorkflowExecutionService` validates `AiWorkflow` definition exists, creates `workflow_sessions` DB record in state `PENDING`, and passes it to `GraphStateMachineEngine`.

2. **Graph Traversal:**
   - Engine starts at `initialNode`.
   - Saves current step state in DB transaction.
   - For `AGENT` node: Calls `AgentExecutionService` with resolved template prompt and memory context; stores output in `session.data[outputKey]`.
   - For `CONDITION` node: Evaluates `expression` via SpEL against `session.data`.
   - Resolves outgoing `edge` based on condition result.
   - Increments `loop_count`. If `loop_count > maxLoops`, throws `MaxLoopsExceededException` and sets status to `FAILED`.

3. **Crash Recovery:**
   - If pod restarts during execution, engine scans DB for sessions with status `RUNNING` or `PENDING` and resumes graph evaluation from `current_node_id`.

---

## 7. HTTP REST & WebSocket API Specs

- `POST /api/v1/workflows/{workflowName}/sessions`
  - Input: `{ "input": "...", "parameters": { "maxLoops": 5 } }`
  - Output: `{ "sessionId": "UUID", "status": "RUNNING", "currentNode": "researcher-node" }`

- `GET /api/v1/sessions/{sessionId}`
  - Output: Returns current session state, execution path history, and final output if completed.

- `POST /api/v1/sessions/{sessionId}/resume`
  - Resumes paused sessions (e.g. human-in-the-loop nodes).

- `GET /api/v1/sessions/{sessionId}/memory/search?q={query}&limit=5`
  - Searches Pgvector long memory for semantic matches.

- `WS /ws/sessions/{sessionId}`
  - Real-time WebSocket event stream emitting `NODE_STARTED`, `NODE_COMPLETED`, `AGENT_TOKEN_USAGE`, and `WORKFLOW_COMPLETED`.

---

## 8. Error Handling & Resilience

- **LLM Call Retries:** `AgentExecutionService` uses exponential backoff retry (3 attempts) on LLM 5xx or rate limit responses.
- **Loop Guards:** `maxLoops` enforced per session (default: 10) to prevent infinite graph loops.
- **DB State Safety:** State updates use PostgreSQL transactions (`@Transactional`).

---

## 9. Testing Strategy

1. **Unit Tests:** State Machine Graph traversal, SpEL evaluation, memory manager methods.
2. **Integration Tests:** Testcontainers PostgreSQL + Pgvector container for integration tests of `SessionMemoryManager` and VectorStore queries.
3. **API & WebSocket Tests:** `@SpringBootTest` with `MockMvc` and STOMP test clients for REST and WebSocket endpoints.
4. **Operator Reconciler Tests:** Fabric8 Kubernetes Server mock for `AiWorkflowReconciler` and `WorkflowSessionReconciler`.
