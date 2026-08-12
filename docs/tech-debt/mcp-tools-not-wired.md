# Tech Debt: MCP Tools Not Routed Through `AgentExecutionService` Execution Pipeline

- **Status:** Accepted (PoC trade-off)
- **Date:** 2026-08-12
- **Severity:** Medium
- **Module:** `tuluat-protocols` → `McpClientRegistryImpl`, `tuluat-engine` → `AgentExecutionService`

## Current State

`McpClientRegistryImpl` is a fully implemented MCP client registry — it registers `McpServer` CRs, 
maintains live `McpClientConnection` entries, and can invoke remote MCP tools over JSON-RPC 2.0:

```java
public McpToolResult invokeTool(String clientName, String toolName,
    Map<String, Object> arguments) { ... }
```

`AiAgentSpec` already has an `mcpServers` field:

```java
@JsonProperty("mcpServers") List<McpServerRef> mcpServers
```

### Problems

| Problem | Detail |
|---|---|
| **`mcpServers` field on `AiAgentSpec` is a no-op** | `AgentExecutionService` reads `spec.tools()` and `spec.toolSources()` but never reads `spec.mcpServers()`. An agent that declares `mcpServers:` in its YAML never gets MCP tools injected. |
| **`McpClientRegistry` not injected into `AgentExecutionService`** | `McpClientRegistryImpl` is a `@Service` but is not a constructor dependency of `AgentExecutionService`. The MCP infrastructure is entirely disconnected from the agent execution pipeline. |
| **`McpServerReconciler` registers but nobody consumes** | `McpServerReconciler` presumably calls `mcpClientRegistry.registerFromCr()` on reconcile, keeping the registry live — but the registered connections are never invoked for agent calls. |
| **Tool result format mismatch** | `McpToolResult` (from `tuluat-protocols`) and `ToolResult` (from `tuluat-engine`) are separate types with no bridge. MCP results cannot be passed to `buildToolContext()` without conversion. |
| **No guardrail coverage for MCP outputs** | Local tools pass through `GuardrailPipeline` via the standard execution path. MCP-sourced tool outputs bypass guardrails entirely — the outputs are trusted without PII masking or content validation. |

### Code path that should exist (but doesn't)

```
AgentExecutionService.executeTools()
  → toolRegistry.executeActiveTools(spec.tools(), query)   // ✅ local tools
  → mcpClientRegistry.findClient(mcpServerRef.name())      // ❌ missing
      .map(conn -> mcpClientRegistry.invokeTool(...))       // ❌ missing
  → merge McpToolResult into ToolResult list                // ❌ missing
  → buildToolContext([local tools + mcp tools])             // ❌ missing
```

## Impact

- The entire MCP integration is infrastructure with no observable effect. A demo that shows `mcpServers:` in a YAML will appear to work (no error) but MCP tools will never be called.
- The `McpServerController` REST API (`/api/v1/mcp-servers`) shows registered MCP servers, making the feature appear functional when it is not wired end-to-end.
- Guardrail bypass: when MCP tools are eventually wired, outputs must be routed through `GuardrailPipeline` — this must be designed in from the start, not retrofitted.

## Proposed Fix

1. **Inject `Optional<McpClientRegistry>` into `AgentExecutionService`** — consistent with ADR 005 optional injection pattern.

2. **Extend `executeTools()`** — after local tool execution, iterate `spec.mcpServers()`:

```java
spec.mcpServers().stream()
    .filter(ref -> mcpClientRegistry.findClient(ref.name()).isPresent())
    .flatMap(ref -> invokeMcpTools(ref))
    .collect(...)
```

3. **Bridge `McpToolResult` → `ToolResult`** — add a `McpToolResult.toToolResult()` conversion method or a mapper in `tuluat-engine`.

4. **Route MCP outputs through guardrails** — apply `guardrailPipeline.validateOutput()` on each MCP result before injecting into the system prompt.

5. **Update `AiAgentStatus`** — expose `activeMcpServers` in agent status (alongside `activeSkills`, `activeTools`) so operators can verify the wiring via `kubectl describe aiagent`.

## Risk if not addressed

- MCP is a key protocol capability. Shipping with a no-op `mcpServers` field damages trust when demonstrated.
- Retrofitting guardrails onto MCP outputs after wiring is harder than designing them in from the start — an ungoverned external tool call is a prompt-injection vector.
