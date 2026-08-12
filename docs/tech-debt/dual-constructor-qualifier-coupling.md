# Tech Debt: Dual Constructors & Hardcoded `@Qualifier` Coupling [RESOLVED]

- **Status:** Resolved (2026-08-12)
- **Resolution:** `SkillRegistry` made non-optional in the main constructor (always available as `@Service`). Two backward-compatible convenience constructors provided (8-param and 10-param) that auto-create `new SkillRegistry()`. `ProviderBeanNames` shared constant extracted — eliminates duplicated `Map.of("OPENAI", "openAiChatModel", ...)` in `ModelGateway` and `CrdEmbabelConfiguration`.
