# Safety Guardrails, MCP & A2A Protocols

This document details the safety guarantees, Model Context Protocol (MCP) tool integration, and Agent-to-Agent (A2A) communication protocols provided by `tuluat-guardrails` and `tuluat-protocols`.

---

## 1. Safety Guardrails Engine (`tuluat-guardrails`)

The `tuluat-guardrails` module implements input sanitization, threat mitigation, and output verification filters applied before and after LLM inference.

```mermaid
graph LR
    UserQuery[User Input / Prompt] --> PII[PiiMaskingFilter]
    PII --> Injection[PromptInjectionFilter]
    Injection --> LLM[LLM Provider / WireMock]
    LLM --> OutputVal[OutputValidationFilter]
    OutputVal --> Response[Verified Agent Response]
```

### 1.1 PII Masking Filter (`PiiMaskingFilter`)
- **Supported Modes**: `EMAIL`, `CREDIT_CARD`, `SSN`, `PHONE`
- **Replacement Token**: Customizable per agent (e.g. `[REDACTED]`)
- **Behavior**: Uses high-performance regex pattern matching to mask sensitive data before transmitting prompts to external LLM providers.

### 1.2 Prompt Injection Filter (`PromptInjectionFilter`)
- **Strategies**:
  - `BLOCK`: Immediately halts execution and throws `GuardrailBlockedException` if injection heuristics (jailbreak phrases, system instruction overrides) match.
  - `SANITIZE`: Strips out malicious payload instructions and allows execution with sanitized text.

### 1.3 Output Validation Filter (`OutputValidationFilter`)
- **Hard Schema Validation**: Validates model output against node-declared JSON Schema specifications.
- **Confidence Scoring**: Assigns a confidence score (`1.0` for schema-valid output, `0.2` for malformed output) and rejects non-compliant responses.

---

## 2. Model Context Protocol (MCP) Client Registry (`tuluat-protocols`)

The MCP registry enables agents to discover and invoke tools exported by external MCP servers (`McpServer` CRD).

### 2.1 Component Specifications
- **`McpServer` CRD**: Defines connection endpoint (`sse` or `stdio`), authentication secret reference, and active tool capabilities.
- **`McpClientRegistryImpl`**:
  - Manages active SSE/stdio transports to registered MCP servers.
  - Discovers tool manifests (`listTools()`) and maps them into dynamic skill definitions.
  - Handles tool invocation (`callTool(serverName, toolName, arguments)`) with structured error handling.

---

## 3. Agent-to-Agent (A2A) Protocol Adapter (`tuluat-protocols`)

The A2A module enables decentralized discovery and communication between autonomous agents running inside or outside the Kubernetes cluster.

### 3.1 A2A Card (`A2aCard`)
- Exports agent metadata: `agentId`, `capabilities`, `inputSchema`, `outputSchema`, `endpointUrl`.
- Enables automatic agent capability matching in multi-agent workflows.

### 3.2 Inter-Agent Message Relay (`A2aAdapterImpl`)
- Relays messages between agents via HTTP/gRPC endpoints.
- Supports asynchronous delegation where a leader agent delegates sub-tasks to remote domain-specialist agents.
