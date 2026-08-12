# Tech Debt: `ToolRegistry` — Shared Mutable State Across Concurrent Agent Executions [RESOLVED]

- **Status:** Resolved (2026-08-12)
- **Commit:** `2ac652d`
- **Resolution:** Per-agent `AgentToolScope` instances with scoped tool loading and execution. `URLClassLoader` lifecycle managed via `evictAgent()`. Builtin tools shared as read-only base. Backward-compatible global `getAvailableToolNames()` / `findTool()` kept for Embabel interoperability.

## Resolution Summary

| Change | Detail |
|---|---|
| **`AgentToolScope` inner class** | Per-agent `tools`, `providers`, `classLoaders` maps — full isolation |
| **Scoped `loadToolSources`** | `loadToolSources(String agentName, List<ToolSource>)` — loads into agent's scope |
| **Scoped `executeActiveTools`** | `executeActiveTools(String agentName, ...)` — executes only agent's tools |
| **`evictAgent(agentName)`** | Closes all `URLClassLoader` instances; removes scope from registry |
| **`@PreDestroy` cleanup** | Evicts all agent scopes on shutdown |
| **Global union fallback** | `getAvailableToolNames()` / `findTool()` union all scopes — Embabel `TuluatToolGroup` compat |
| **Builtin tools** | Calculator, WebSearch, Weather shared as read-only base; copied into each new scope |
| **Caller updates** | `AgentExecutionService.processAgentPrompt()` + `invokeResolvedAgent()` pass `agentName` |

## Original Analysis

- **Date:** 2026-08-12
- **Severity:** High
- **Module:** `tuluat-engine` → `ToolRegistry`

### Problems (pre-resolution)

| Problem | Detail |
|---|---|
| **Accumulation without eviction** | Tools added but never removed on AiAgent CR deletion or toolSources change. |
| **No agent-level isolation** | Agent A's custom JAR tool visible to Agent B; any agent could invoke any tool. |
| **Redundant JAR classloading** | `loadFromFolder()` called on every execution; same JARs reloaded repeatedly. |
| **`URLClassLoader` leak** | Each `LoadedProvider` created new `URLClassLoader`; never closed → metaspace pressure. |
| **Race on `loadedProviders` map** | Concurrent agent executions could flip `loadedProviders` entries mid-flight. |

### Risk if not addressed

- Multi-tenant tool pollution exploitable as lateral-movement vector.
- Memory leak worsens with operator uptime and agent count.
