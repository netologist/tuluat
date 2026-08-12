# Tech Debt: Dual Constructors & Hardcoded `@Qualifier` Coupling

- **Status:** Accepted (PoC trade-off)
- **Date:** 2026-08-12
- **Severity:** Medium
- **Module:** `tuluat-engine` → `AgentExecutionService`
- **Related:** ADR 005 (Optional dependency injection)

## Current State

`AgentExecutionService` has **two public constructors**:

```java
// Constructor 1 — full (autowired)
@Autowired
public AgentExecutionService(ToolRegistry toolRegistry,
    Optional<SkillRegistry> skillRegistry,
    @Qualifier("openAiChatModel") Optional<ChatModel> chatModel,
    GuardrailPipeline guardrailPipeline,
    Optional<ModelGateway> modelGateway,
    Optional<ProviderResolver> providerResolver,
    Optional<AgentResolver> agentResolver,
    Optional<RagService> ragService)

// Constructor 2 — test/no-skill shortcut (no @Autowired)
public AgentExecutionService(ToolRegistry toolRegistry,
    @Qualifier("openAiChatModel") Optional<ChatModel> chatModel,
    ...)
```

### Problems

| Problem | Detail |
|---|---|
| **Ambiguous autowiring** | Two constructors force an explicit `@Autowired` to signal which Spring should use. Any refactor that adds/removes parameters risks Spring selecting the wrong one silently. |
| **`@Qualifier("openAiChatModel")` hardcoded** | The bean name `"openAiChatModel"` is a Spring AI starter implementation detail. It appears in both constructors and in `ModelGateway.PROVIDER_BEAN_NAMES`. A starter upgrade or provider rename breaks injection silently at runtime — not at compile time. |
| **Duplicate constructor** | Constructor 2 exists solely for convenience in tests without `SkillRegistry`. This pattern should be replaced by a builder/factory or by making `SkillRegistry` always present (even as a no-op). |
| **`@SuppressWarnings("OptionalUsedAsFieldOrParameterType")`** | Present in both `AgentExecutionService` and `WorkflowExecutionService`. Suppressing this warning is acceptable per ADR 005, but the dual constructor pattern nullifies the clean intent of the ADR. |

## Impact

- A Spring AI version bump that renames `OpenAiChatModel`'s bean name → `NoSuchBeanDefinitionException` at startup, not at compile time.
- Second constructor is dead weight in production; only needed because `SkillRegistry` is optional. If the registry had a no-op default the second constructor disappears.
- Static analysis tools (ArchUnit, Checkstyle) cannot catch this class of coupling without custom rules.

## Proposed Fix

1. **Collapse to one constructor** — make `SkillRegistry` always injectable as a no-op bean:

```java
@ConditionalOnMissingBean
@Bean
SkillRegistry noOpSkillRegistry() { return SkillRegistry.empty(); }
```

2. **Replace `@Qualifier` with an interface** — introduce `PrimaryChatModel` marker interface or use Spring AI's `ChatClient.Builder` which resolves the autoconfigured model without coupling to the bean name.

3. **Extract `PROVIDER_BEAN_NAMES` to a shared constant** — `ModelGateway` and `CrdEmbabelConfiguration` both hardcode `"openAiChatModel"` / `"ollamaChatModel"`. Move to a single `ProviderBeanNames` enum in `tuluat-engine`.

## Risk if not addressed

- Silent runtime breakage on Spring AI or Embabel SNAPSHOT upgrades.
- Tests that use Constructor 2 diverge from production wiring — integration gaps won't surface until E2E.
