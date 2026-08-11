# Autonomous Agents & Dynamic Skill Registry

This feature details agent personality configuration, prompt templating, and dynamic tool/skill execution.

---

## 1. Autonomous AI Agents (`AiAgent` CRD)

An `AiAgent` defines a domain-specialized AI persona:
- **System Prompt**: Core instructions grounding agent persona and output rules.
- **Provider Reference**: Link to an `LlmProvider` resource for model execution.
- **Model Override**: Optional agent-specific model override (e.g. `gpt-4o-mini`).
- **Skills**: List of active capabilities enabled for this agent.
- **Guardrails**: Safety policy definitions for prompt sanitization and output validation.

---

## 2. Built-in Skills & Dynamic Skill Registry (`SkillRegistry`)

Skills provide tools that agents can execute during execution pipelines:

| Skill | Description | Usage Example |
| :--- | :--- | :--- |
| `calculator` | Evaluates basic mathematical and financial calculations (`+`, `-`, `*`, `/`). | `"Calculate total order cost with 18% VAT"` |
| `web-search` | Performs real-time web search for technical documentation. | `"Search latest Spring Boot 4.1 release notes"` |
| `weather` | Retrieves real-time weather information for specified locations. | `"Check weather forecast for San Francisco"` |

### 2.1 Virtual Thread Skill Execution
Active skills execute concurrently on Java 25 **Virtual Threads** via `SkillRegistry.executeActiveSkills()`, collecting outputs and injecting them into the LLM system prompt context before inference.

---

## 3. Dynamic Skill Loading (`skillSources`)

Agents can load custom skills at runtime without redeploying operator code:
- **`CONFIGMAP`**: Load JavaScript/Python tool scripts from Kubernetes ConfigMaps.
- **`JAR`**: Load compiled Java skill plugins dynamically from a shared volume or object storage.
- **`FOLDER`**: Watch local filesystem directories for hot-reloadable tool definitions.
