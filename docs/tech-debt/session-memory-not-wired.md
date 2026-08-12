# Tech Debt: `SessionMemoryManager` — Short-Term Memory Not Wired Into Agent Prompt Context

- **Status:** Accepted (PoC trade-off)
- **Date:** 2026-08-12
- **Severity:** Medium
- **Module:** `tuluat-engine` → `SessionMemoryManager`, `AgentExecutionService`, `GraphStateMachineEngine`

## Current State

`SessionMemoryManager` can store and retrieve per-session conversation turns:

```java
public SessionShortMemoryEntity saveShortMemory(UUID sessionId, String agentName,
    String role, String content) { ... }

public List<SessionShortMemoryEntity> getShortMemory(UUID sessionId) { ... }
```

The `AiWorkflowSpec` already carries a `MemoryConfig`:

```java
// AiWorkflowSpec
MemoryConfig memory();
```

### Problems

| Problem | Detail |
|---|---|
| **Not injected into `AgentExecutionService`** | `SessionMemoryManager` is declared as a `@Service` but is never injected into `AgentExecutionService`. The agent's system prompt and user prompt are built without any conversation history. |
| **Not called in `GraphStateMachineEngine`** | When a workflow node invokes `agentExecutionService.executeAgent()`, previous node outputs are stored in `contextData` (JSON blob) but **not** as structured memory entries. The `saveShortMemory` method is never called. |
| **`MemoryConfig` in `AiWorkflowSpec` is a no-op** | The CRD supports a `memory` configuration block but nothing reads it at runtime. A user can declare `memory: { type: SHORT_TERM, windowSize: 10 }` in their YAML and nothing happens. |
| **Multi-turn `AgentChatController`** | The `/api/v1/chat/{agentName}` endpoint accepts sessions but does not persist or inject prior turns into the next prompt. Every call is stateless. |
| **`getShortMemory` never called** | `findBySessionIdOrderByCreatedAtAsc` is a clean query method that's never invoked anywhere in production code paths. |

### Code path that should exist (but doesn't)

```
GraphStateMachineEngine.executeNextStep()
  → AGENT node executes
  → agentExecutionService.executeAgent()
      ← should inject: memoryManager.getShortMemory(sessionId) as conversation history
  → agent produces response
  → should call: memoryManager.saveShortMemory(sessionId, agentRef, "assistant", answer)
```

## Impact

- Every agent node call in a workflow is stateless — there is no "memory" of what previous nodes produced, beyond the raw `contextData` JSON blob injected via prompt template.
- The `AgentChatController` multi-turn chat (`/api/v1/chat/{agentName}?sessionId=...`) always responds as if it's the first turn.
- Users who declare `memory:` in their workflow YAML will silently get no-op behavior.

## Proposed Fix

1. **Inject `SessionMemoryManager` into `AgentExecutionService`** — as an `Optional<SessionMemoryManager>` (consistent with ADR 005 pattern).

2. **Build `ChatMessages` from history** — before building the prompt, call `getShortMemory(sessionId)` and prepend prior turns as `AssistantMessage`/`UserMessage` objects in the `Prompt`.

3. **Save output to memory** — after `llmResult` succeeds, call `saveShortMemory(sessionId, agentName, "assistant", answer)`.

4. **Respect `MemoryConfig.windowSize`** — truncate history list to the last N turns before injecting into prompt to avoid context overflow.

5. **Wire into `GraphStateMachineEngine`** — pass `sessionId` through to the agent execution call; this requires adding `sessionId` as a parameter to `AgentExecutionService.executeAgent()`.

## Risk if not addressed

- Multi-step reasoning workflows that depend on conversational context (e.g., "remember the risk score you computed in step 2") will fail or hallucinate since the context is only available as a raw string in the prompt template — not as typed conversation history.
- The `MemoryConfig` CRD field misleads users into thinking memory is functional.
