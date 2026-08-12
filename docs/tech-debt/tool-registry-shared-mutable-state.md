# Tech Debt: `ToolRegistry` — Shared Mutable State Across Concurrent Agent Executions

- **Status:** Accepted (PoC trade-off)
- **Date:** 2026-08-12
- **Severity:** High
- **Module:** `tuluat-engine` → `ToolRegistry`

## Current State

`ToolRegistry` is a singleton `@Service` with a `ConcurrentHashMap<String, Tool>` that accumulates
tools at runtime:

```java
// Called once per executeTools() call — every time an agent runs
skillRegistry.ifPresent(sr -> sr.loadSkillSources(spec.skillSources()));
toolRegistry.loadToolSources(spec.toolSources());      // ← mutates shared map
```

`loadToolSources()` calls `loadFromFolder()` on every invocation — meaning every agent execution
attempt to register its JAR/FOLDER tools into the **single global registry**, even if they were
already loaded.

### Problems

| Problem | Detail |
|---|---|
| **Accumulation without eviction** | Tools are added to `registeredTools` but never removed when an `AiAgent` CR is deleted or its `toolSources` changes. Old tools remain available to all agents. |
| **No agent-level isolation** | Agent A's custom JAR tool is visible to Agent B — even if Agent B has no `toolSources`. Any agent can invoke any registered tool by name. |
| **Redundant JAR classloading** | `loadFromFolder()` is called on every agent execution, reloading the same JARs repeatedly. No freshness check or de-duplication. |
| **`ToolJarLoader` uses custom classloaders** | Each `LoadedProvider` from `ToolJarLoader` creates a new `URLClassLoader`. On repeated loads these are never closed → potential classloader leak in long-running pods. |
| **Race on `loadedProviders` map** | `loadedProviders.put(source.path(), found)` replaces the entry on every call. Under concurrent agent executions the map entry can flip between old and new providers mid-flight. |

### Code path

```
AgentExecutionService.processAgentPrompt()
  → toolRegistry.loadToolSources(spec.toolSources())   // mutates singleton
  → ToolJarLoader.loadFromFolder()                     // new URLClassLoader
  → registerProvider()                                 // put into shared map
  → executeActiveTools()                               // reads shared map
```

## Impact

- **Security:** Agent with low privilege can "see" tools registered by a high-privilege agent.
- **Correctness:** A tool name collision between two agents silently overwrites the first.
- **Memory:** Unclosed `URLClassLoader` instances accumulate; JVM metaspace pressure increases over time in pods with many agent activations.
- **Latency:** Redundant disk I/O to reload tool JARs on every agent call.

## Proposed Fix

1. **Per-agent `ToolRegistry` scopes** — hold an `AgentToolScope` per `AiAgent` CR name, keyed by agent name. The scope is created on reconciliation and destroyed on CR deletion.

2. **One-time eager loading** — load tool sources during reconciliation (`AiAgentReconciler`), not on every execution call. Cache via `loadedProviders` keyed by `(agentName, sourcePath, contentHash)`.

3. **`URLClassLoader` lifecycle** — close classloaders in `@PreDestroy` or on scope eviction (currently `shutdown()` only closes the `ExecutorService`, not the classloaders).

4. **Immutable execution view** — `executeActiveTools` receives a snapshot of the agent's own tools, not the global map.

## Risk if not addressed

- Multi-tenant tool pollution is exploitable as a lateral-movement vector in a shared operator deployment.
- Memory leak worsens with operator uptime and agent count.
