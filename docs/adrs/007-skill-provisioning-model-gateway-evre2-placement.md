# ADR 007: Skill Provisioning vs Binding, Model Gateway, and Evre 2 Configuration Placement

* **Status:** Accepted
* **Date:** 2026-08-07
* **Deciders:** Software Architecture Team

---

## Context and Problem Statement

Evre 2 introduces Guardrails, MCP, and A2A capabilities, and raises two structural questions:

1. **Skills:** The existing `SkillDefinition` CRD model and `SkillRegistry` conflate two concerns — *how a skill is provided* (compiled-in classes, external JARs dropped into a folder) and *how an agent binds to it* (which skills, with which parameters). The sealed `Skill` interface (`permits CalculatorSkill, WebSearchSkill, WeatherSkill, CustomSkill`) prevents external JARs from contributing implementations at compile time, and `CustomSkill` is a placeholder fallback, not real extensibility.

2. **Model management:** `LlmProviderSpec` is a clean transport definition (providerType, baseUrl, apiKeySecretRef, defaultModel, temperature, maxTokens). Model cost, fallback chains, and budget policies are routing/policy concerns that do not belong to a single provider definition.

Additionally, the placement of Guardrail policy, MCP server definitions, and A2A capability across CRDs must be decided consistently with the existing `providerRef` reference pattern.

## Decision Drivers
* **Extensibility:** Anyone must be able to add a skill by dropping a JAR into a mounted folder — no code change, no recompile.
* **Declarative Contracts:** CRDs declare *what* (policy, references), modules implement *how*.
* **Pattern Consistency:** MCP servers are external resources like `LlmProvider`; agents should reference them via the same `*Ref` pattern already used for providers.
* **Separation of Routing from Transport:** Fallback, budget, and cost tracking operate across providers and must not pollute the provider transport spec.
* **Security:** Guardrail policy is per-agent (PII masking modes differ by domain), but enforcement is a platform pipeline.

## Considered Options

### Skills
1. **Inline everything in `SkillDefinition` (Status Quo):** Skill definitions carry both provisioning hints and binding config. Sealed interface stays; external JARs impossible without recompiling `tuluat-engine`.
2. **Split Provisioning vs Binding:** A `SkillProvider` SPI discovered via `ServiceLoader` from mounted JAR folders (`skillSources`), plus the existing `SkillDefinition` kept purely as the per-agent binding (name, enabled, parameters). `Skill` becomes non-sealed; built-ins register in code, external skills register via SPI.

### Model Management
1. **Extend `LlmProviderSpec`:** Add fallbacks, budget, cost to the provider CRD.
2. **Dedicated `ModelGateway` layer:** Keep `LlmProvider` as transport; add a gateway service in `tuluat-engine` that resolves routes, applies fallback ordering, enforces budgets, and records cost telemetry. Pricing metadata (`costPer1kInputTokens`, `costPer1kOutputTokens`) remains on the provider as data, not policy.

## Decision Outcome

**Chosen Option:** **Option 2 for both.**

### 1. Skill Provisioning vs Binding

* **`Skill` interface is unsealed** so external JARs can implement it (compile-time requirement for SPI extensibility).
* **New SPI:** `SkillProvider` in `com.tuluat.engine.skill.spi` — implementations return `Skill` instances. Built-in skills (Calculator, WebSearch, Weather) register via a default `SkillProvider` in `SkillRegistry`; external providers register via `ServiceLoader`.
* **JAR-drop support:** `SkillJarLoader` scans configured skill folders (`skillSources`), loads `*.jar` with an isolated `URLClassLoader`, discovers `SkillProvider` implementations, and registers them. Optional folder watch enables hot-reload.
* **CRD binding unchanged:** `AiAgentSpec.skills: List<SkillDefinition>` remains the per-agent binding (name, description, enabled, parameters). A skill is *provided* by any source and *bound* by the agent spec.

### 2. Model Gateway

* **`LlmProviderSpec` additions (data only):**
  * `costPer1kInputTokens` / `costPer1kOutputTokens` (pricing metadata)
* **New `ModelGateway` service in `tuluat-engine`:**
  * Route resolution: primary provider/model → ordered fallback list
  * Budget enforcement (per agent/global, limit over a period)
  * Cost estimation from usage + pricing metadata, emitted to `UsageStats` and `WorkflowTelemetryService`
  * External gateway escape hatch: `LlmProviderSpec.baseUrl` already accepts arbitrary URLs (e.g. LiteLLM), so the gateway can front a third-party router with no architectural change.

### 3. Configuration Placement Summary

| Feature | Location | Pattern |
| :--- | :--- | :--- |
| Guardrail policy (PII masking, injection defense, min confidence) | `AiAgentSpec.guardrails` (optional block; platform defaults applied when absent) | Declarative policy, engine-enforced |
| Post-execution output JSON Schema | `NodeDefinition.outputSchema` (workflow node contract) | Node contract, not agent identity |
| MCP server definition | New `McpServer` CRD in `tuluat-crd-domain` | Mirrors `LlmProvider` |
| MCP references from agents | `AiAgentSpec.mcpServers: List<McpServerRef>` | Mirrors `providerRef` |
| MCP runtime | `McpClientRegistryImpl` in `tuluat-protocols` | Operator reconciles `McpServer` CRDs into client connections |
| A2A adapter | `tuluat-protocols`, platform level (gRPC/REST discovery + remote execution) | Gateway capability |
| A2A agent opt-in | `AiAgentSpec.a2a` (optional `{ enabled, remoteDiscovery }`) | Declaration only |

## Positives
* **JAR-drop extensibility:** Third parties extend the platform without recompiling or forking `tuluat-engine`.
* **Clean seam:** CRDs remain declarative contracts; implementation stays in `tuluat-guardrails`, `tuluat-protocols`, and `tuluat-engine`.
* **Consistent reference pattern:** `McpServerRef`/`skillSources`/`a2a` all follow the `providerRef` idiom.
* **Realistic fallback testing:** The WireMock stub provider can act as primary with a real provider as fallback in KinD E2E.

## Negatives
* **Sealed-interface loss:** Removing `sealed` from `Skill` sacrifices a compile-time exhaustiveness guarantee in exchange for extensibility (mitigated by the SPI contract and registry isolation).
* **JAR classloader hygiene:** `URLClassLoader` isolation requires care to avoid leaks on hot-reload (folder watcher must close loaders).
* **Gateway complexity:** Budget/fallback logic adds a layer between `AgentExecutionService` and providers; must be covered by unit tests.
