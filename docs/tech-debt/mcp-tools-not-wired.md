# Tech Debt: MCP Tools Not Routed Through `AgentExecutionService` Execution Pipeline [RESOLVED]

- **Status:** Resolved (2026-08-12)
- **Resolution:** Injected `McpClientRegistry` into `AgentExecutionService` as `Optional<McpClientRegistry>`. MCP tools declared in `spec.mcpServers[]` are now invoked alongside local tools during execution. `McpToolResult` converted to engine `ToolResult` with `mcp:` namespaced tool names. MCP failures handled gracefully — non-fatal, logged, and execution continues. `activeMcpServers` field added to `AiAgentStatus` CRD for Kubernetes-native visibility.

*See commit history for implementation details.*
