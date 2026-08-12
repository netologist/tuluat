# Tech Debt: `GraphStateMachineEngine` — Direct Session Entity Mutation (No Domain Boundary)

- **Status:** Resolved (ObjectMapper injection + SessionStatus enum only)
- **Resolved:** 2026-08-12

## Resolution Summary

Two changes applied; remaining issues deferred per PoC trade-off acceptance:

| Change | Detail |
|---|---|
| **Inject Spring `ObjectMapper`** | `GraphStateMachineEngine` now accepts `ObjectMapper` via constructor injection instead of `new ObjectMapper()`. Same Spring-managed instance as `WorkflowExecutionService`. |
| **`SessionStatus` enum** | Created `com.tuluat.engine.entity.SessionStatus` with `RUNNING, WAITING_APPROVAL, COMPLETED, FAILED, REJECTED`. `WorkflowSessionEntity.status` uses `@Enumerated(EnumType.STRING)`. All raw `session.setStatus("…")` strings replaced across engine, service, reconciler, controllers, and tests. |

### Deferred

- `WorkflowExecutionState` value object (record) — accepted PoC trade-off
- `contextData` JSON blob → typed map — accepted PoC trade-off
- **Severity:** Medium
- **Module:** `tuluat-engine` → `GraphStateMachineEngine`, `WorkflowExecutionService`

## Current State

`GraphStateMachineEngine.executeNextStep()` receives a `WorkflowSessionEntity` (a JPA entity) and
mutates it directly in-place:

```java
session.setCurrentNodeId(nextNodeId);
session.setStatus("FAILED");
session.setContextData(writeContext(contextData));
session.setLoopCount(session.getLoopCount() + 1);
```

The caller `WorkflowExecutionService` then `@Transactional`-saves this entity. The engine also
does its own `ObjectMapper` JSON parsing via a private `mapper = new ObjectMapper()`:

```java
private final ObjectMapper mapper = new ObjectMapper();           // NOT the Spring-managed one
```

### Problems

| Problem | Detail |
|---|---|
| **JPA entity as domain model** | `WorkflowSessionEntity` mixes persistence concerns (JPA annotations, `@Version`, `@CreationTimestamp`) with workflow execution state. The engine should operate on a plain domain object, not a JPA entity. |
| **Private `ObjectMapper`** | `GraphStateMachineEngine` creates its own `new ObjectMapper()` instead of injecting the Spring-managed `Jackson2ObjectMapperBuilder`-configured instance (which has modules like `JavaTimeModule`, custom serializers, etc.). This can cause silent deserialization differences between session context data and the rest of the app. |
| **`contextData` as a stringly-typed JSON blob** | Session context is `String contextData = "{}"` on the entity, parsed on every node step. No type safety. A typo in `currentNode.outputKey()` or `contextData.put(...)` results in a missing key that is silently propagated to the next node. |
| **Status strings, not enum** | `session.setStatus("FAILED")` / `"WAITING_APPROVAL"` / `"COMPLETED"` — raw strings. One typo produces an inconsistent state that no compiler catches. |
| **SpEL `ExpressionParser` is shared and not thread-safe for contexts** | `StandardEvaluationContext` is created fresh but `SpelExpressionParser` is shared. Per Spring docs `SpelExpressionParser` is thread-safe, but `StandardEvaluationContext` with variable sets is not. Currently safe because context is created per-call, but this pattern is a refactor-trap. |

### Code path

```
WorkflowExecutionService.startSession()       // @Transactional
  → GraphStateMachineEngine.executeNextStep() // mutates JPA entity directly
    → parseContext()                          // ObjectMapper (unmanaged)
    → session.setStatus("FAILED")            // raw string
    → session.setContextData(...)            // JSON blob back
  → sessionRepository.save(session)          // persist mutated entity
```

## Impact

- `contextData` JSON mismatches are invisible at runtime until a downstream node tries to read a key that was written with a different name.
- The private `ObjectMapper` skips `JavaTimeModule` — any `OffsetDateTime` or `ZonedDateTime` in context data will serialize differently from the main application context.
- Refactoring the entity (e.g. adding a column) requires touching the engine logic — violates the separation of persistence and execution concerns.

## Proposed Fix

1. **Introduce `WorkflowExecutionState` value object** — a plain record carrying `currentNodeId`, `status` (enum), `contextData` (`Map<String, Object>`), and `loopCount`. The engine works on this; `WorkflowExecutionService` maps to/from entity.

2. **Inject `ObjectMapper`** — remove `private final ObjectMapper mapper = new ObjectMapper()` and accept it as a constructor parameter (already done in `WorkflowExecutionService`; apply the same to the engine).

3. **`WorkflowSessionStatus` enum** — already exists in `tuluat-crd-domain`; add a corresponding `SessionStatus` enum in `tuluat-engine` to eliminate raw strings.

## Risk if not addressed

- Silent context-data bugs in complex multi-node workflows will be very hard to trace.
- The `ObjectMapper` divergence will cause `OffsetDateTime` serialization bugs when context carries timestamp fields.
