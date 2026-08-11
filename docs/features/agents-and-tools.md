# Autonomous Agents & Dynamic Tool Registry

This feature details agent personality configuration, prompt templating, and dynamic tool execution.

---

## 1. Agent Personality & Specification (`AiAgentSpec`)

Each `AiAgent` CR defines a declarative agent configuration:
- **System Prompt**: Core persona definition.
- **User Prompt Template**: Default query template supporting `{{input}}` parameter substitution.
- **Provider Reference**: Links to `LlmProvider` CR (`providerRef.name`).
- **Model Override**: Optional agent-specific model override (e.g. `gpt-4o-mini`).
- **Tools**: List of active capabilities enabled for this agent.
- **Guardrails**: Safety policy definitions for prompt sanitization and output validation.

---

## 2. Built-in Tools & Dynamic Tool Registry (`ToolRegistry`)

Tools provide capabilities that agents can execute during execution pipelines:

| Tool | Description | Usage Example |
| :--- | :--- | :--- |
| `calculator` | Evaluates basic mathematical and financial calculations (`+`, `-`, `*`, `/`). | `"Calculate total order cost with 18% VAT"` |
| `web-search` | Performs real-time web search for technical documentation. | `"Search latest Spring Boot 4.1 release notes"` |
| `weather` | Retrieves real-time simulated weather data for specified locations. | `"Check current weather in Istanbul"` |

### 2.1 Virtual Thread Tool Execution
Active tools execute concurrently on Java 25 **Virtual Threads** via `ToolRegistry.executeActiveTools()`, collecting outputs and injecting them into the LLM system prompt context before inference.

---

## 3. Dynamic Tool Loading (`toolSources`)

Agents can load custom tools at runtime without redeploying operator code:
- **`CONFIGMAP`**: Load JavaScript/Python tool scripts from Kubernetes ConfigMaps.
- **`JAR`**: Load compiled Java tool plugins dynamically from a shared volume or object storage.
- **`FOLDER`**: Watch local filesystem directories for hot-reloadable tool definitions.
