# Low-Level Design — AI Runtime Platform

This document details the low-level Java class models, database schemas, state machine transition logic, and guardrail pipelines.

---

## 1. Domain & Entity Models

### 1.1 Custom Resource Models
- `AiWorkflow` -> `AiWorkflowSpec`: Contains `initialNode`, `List<NodeDefinition>`, `List<EdgeDefinition>`, `MemoryConfig`.
- `NodeDefinition`: Fields `id`, `type` (`AGENT`, `CONDITION`, `TOOL`), `agentRef`, `inputTemplate`, `outputKey`, `expression`.
- `EdgeDefinition`: Fields `from`, `to`, `condition`.
- `WorkflowSession` -> `WorkflowSessionSpec` & `WorkflowSessionStatus`.

### 1.2 JPA Entities
```
+--------------------------+       +----------------------------+
|  WorkflowSessionEntity   |       |  SessionShortMemoryEntity  |
+--------------------------+       +----------------------------+
| PK: sessionId (UUID)     |       | PK: id (Long)              |
| workflowName (String)    | <---+ | FK: sessionId (UUID)       |
| status (String)          |       | agentName (String)         |
| currentNodeId (String)   |       | role (String)              |
| loopCount (int)          |       | content (Text)             |
| contextData (@JdbcType)  |       | createdAt (OffsetDateTime) |
| createdAt (OffsetDateTime|       +----------------------------+
| updatedAt (OffsetDateTime|
+--------------------------+       +----------------------------+
                                   |  WorkflowSessionLogEntity  |
                                   +----------------------------+
                                   | PK: id (Long)              |
                                   | FK: sessionId (UUID)       |
                                   | nodeId (String)            |
                                   | logLevel (String)          |
                                   | message (Text)             |
                                   | createdAt (OffsetDateTime) |
                                   +----------------------------+
```

---

## 2. State Machine Execution Engine Logic

The `GraphStateMachineEngine` processes nodes deterministically:

```java
public WorkflowSessionEntity executeNextStep(AiWorkflowSpec workflowSpec, WorkflowSessionEntity session, int maxLoops) {
    // 1. Loop Safety Check
    if (session.getLoopCount() >= maxLoops) {
        session.setStatus("FAILED");
        return session;
    }
    
    // 2. Resolve Current Node
    NodeDefinition currentNode = resolveNode(workflowSpec, session.getCurrentNodeId());
    
    // 3. Node Type Execution
    if ("AGENT".equalsIgnoreCase(currentNode.getType())) {
        String prompt = resolvePromptTemplate(currentNode.getInputTemplate(), contextData);
        AgentResponse response = agentExecutionService.executeAgent(currentNode.getAgentRef(), prompt, null);
        contextData.put(currentNode.getOutputKey(), response.answer());
    } else if ("CONDITION".equalsIgnoreCase(currentNode.getType())) {
        boolean result = evaluateCondition(currentNode.getExpression(), contextData);
        String nextNodeId = resolveNextNodeId(workflowSpec, currentNode.getId(), result);
        session.setCurrentNodeId(nextNodeId);
    }
    
    // 4. Update Loop Count and Return
    session.setLoopCount(session.getLoopCount() + 1);
    return session;
}
```

---

## 3. Database Schema (PostgreSQL DDL)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE workflow_sessions (
    session_id UUID PRIMARY KEY,
    workflow_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_node_id VARCHAR(255),
    loop_count INT NOT NULL DEFAULT 0,
    context_data JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE session_short_memory (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES workflow_sessions(session_id) ON DELETE CASCADE,
    agent_name VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE session_long_memory (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID REFERENCES workflow_sessions(session_id) ON DELETE SET NULL,
    workflow_name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE workflow_session_logs (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES workflow_sessions(session_id) ON DELETE CASCADE,
    node_id VARCHAR(255),
    log_level VARCHAR(20) NOT NULL DEFAULT 'INFO',
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```
