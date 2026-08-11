package com.tuluat.engine.agent;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.ToolDefinition;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.engine.gateway.ModelGateway;
import com.tuluat.engine.gateway.ProviderResolver;
import com.tuluat.engine.rag.RagService;
import com.tuluat.engine.skill.SkillRegistry;
import com.tuluat.engine.tool.ToolRegistry;
import com.tuluat.engine.tool.ToolResult;
import com.tuluat.guardrails.GuardrailBlockedException;
import com.tuluat.guardrails.GuardrailPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core engine for executing AI Agent prompts with Tools, Agent Skills
 * (SKILL.md), Guardrails, and Model Gateway.
 *
 * <h3>Execution pipeline:</h3>
 * <ol>
 * <li>Resolve model and query from agent spec</li>
 * <li>Apply pre-execution guardrails (PII masking, injection defense)</li>
 * <li>Execute active tools on virtual threads</li>
 * <li>Build system prompt with tool context, agent skills (SKILL.md), and
 * RAG</li>
 * <li>Invoke LLM via ModelGateway → ChatModel → simulated fallback</li>
 * <li>Validate output against guardrail policy</li>
 * </ol>
 *
 * <h3>Optional dependencies:</h3> All optional collaborators use
 * {@link Optional}{@code <T>} constructor injection (Spring 4.3+ idiom). See
 * ADR 005.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Service
@Slf4j
public class AgentExecutionService {

	private static final String DEFAULT_MODEL = "deepseek-chat";
	private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistant.";
	private static final String DEFAULT_USER_PROMPT = "Hello AI Agent";
	private static final int RAG_RESULT_COUNT = 3;

	private final ToolRegistry toolRegistry;
	private final Optional<SkillRegistry> skillRegistry;
	private final Optional<ChatModel> chatModel;
	private final GuardrailPipeline guardrailPipeline;
	private final Optional<ModelGateway> modelGateway;
	private final Optional<ProviderResolver> providerResolver;
	private final Optional<AgentResolver> agentResolver;
	private final Optional<RagService> ragService;

	@org.springframework.beans.factory.annotation.Autowired
	public AgentExecutionService(ToolRegistry toolRegistry, Optional<SkillRegistry> skillRegistry,
			@Qualifier("openAiChatModel") Optional<ChatModel> chatModel, GuardrailPipeline guardrailPipeline,
			Optional<ModelGateway> modelGateway, Optional<ProviderResolver> providerResolver,
			Optional<AgentResolver> agentResolver, Optional<RagService> ragService) {
		this.toolRegistry = toolRegistry;
		this.skillRegistry = skillRegistry;
		this.chatModel = chatModel;
		this.guardrailPipeline = guardrailPipeline;
		this.modelGateway = modelGateway;
		this.providerResolver = providerResolver;
		this.agentResolver = agentResolver;
		this.ragService = ragService;
	}

	public AgentExecutionService(ToolRegistry toolRegistry, @Qualifier("openAiChatModel") Optional<ChatModel> chatModel,
			GuardrailPipeline guardrailPipeline, Optional<ModelGateway> modelGateway,
			Optional<ProviderResolver> providerResolver, Optional<AgentResolver> agentResolver,
			Optional<RagService> ragService) {
		this(toolRegistry, Optional.empty(), chatModel, guardrailPipeline, modelGateway, providerResolver,
				agentResolver, ragService);
	}

	// ── Public API ──────────────────────────────────────────────────────────

	public AgentResponse processAgentPrompt(AiAgent agent, LlmProvider provider, String customInput) {
		var startTime = System.currentTimeMillis();
		var spec = agent.getSpec();
		var agentName = agent.getMetadata().getName();

		var model = resolveModel(spec, provider);
		var query = resolveQuery(spec, customInput);

		String safeQuery;
		try {
			safeQuery = guardrailPipeline.processPrompt(query, spec.guardrails());
		} catch (GuardrailBlockedException e) {
			log.warn("Agent '{}' blocked by guardrail [{}]: {}", agentName, e.getFilterName(), e.getMessage());
			return AgentResponse.blocked(agentName, e.getFilterName(), e.getMessage());
		}

		// Load Agent Skills (SKILL.md) and Tools
		skillRegistry.ifPresent(sr -> sr.loadSkillSources(spec.skillSources()));
		toolRegistry.loadToolSources(spec.toolSources());

		var toolResults = executeTools(agentName, spec.tools(), safeQuery);
		var systemPrompt = buildSystemPrompt(spec.systemPrompt(), safeQuery, toolResults);
		var prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(safeQuery)));

		var llmResult = invokeLlm(agentName, model, provider, prompt, systemPrompt, safeQuery, toolResults);
		if (llmResult.blocked()) {
			return AgentResponse.blocked(agentName, llmResult.blockReason(), llmResult.errorMessage());
		}

		validateOutput(agentName, llmResult.answer(), spec.guardrails());

		var latency = System.currentTimeMillis() - startTime;
		var usage = buildUsage(llmResult, model, latency);
		return AgentResponse.create(agentName, model, systemPrompt, llmResult.answer(), toolResults, usage);
	}

	public AgentResponse executeAgent(String agentRef, String prompt, String context) {
		log.info("Executing agentRef '{}'", agentRef);
		var namespace = (context != null && !context.isBlank()) ? context : null;
		var name = agentRef != null ? agentRef : "default-agent";

		var agent = agentResolver.flatMap(r -> r.resolve(agentRef, namespace));
		var guardrails = agent.map(a -> a.getSpec() != null ? a.getSpec().guardrails() : null).orElse(null);

		String safePrompt;
		try {
			safePrompt = guardrailPipeline.processPrompt(prompt, guardrails);
		} catch (GuardrailBlockedException e) {
			log.warn("Agent '{}' blocked by guardrail [{}]: {}", name, e.getFilterName(), e.getMessage());
			return AgentResponse.blocked(name, e.getFilterName(), e.getMessage());
		}

		if (agent.isPresent() && agent.get().getSpec() != null) {
			return invokeResolvedAgent(name, agent.get().getSpec(), safePrompt, guardrails);
		}

		var response = AgentResponse.create(name, DEFAULT_MODEL, "Workflow Agent System Prompt",
				"Execution completed for: " + safePrompt, List.of(), UsageStats.calculate(10, 10, DEFAULT_MODEL, 50));

		validateOutput(name, response.answer(), guardrails);
		return response;
	}

	private AgentResponse invokeResolvedAgent(String name, AiAgentSpec spec, String safePrompt,
			com.tuluat.crd.agent.GuardrailsConfig guardrails) {
		var provider = resolveProvider(spec);
		var model = resolveModel(spec, provider);
		var systemPrompt = spec.systemPrompt() != null ? spec.systemPrompt() : DEFAULT_SYSTEM_PROMPT;

		skillRegistry.ifPresent(sr -> sr.loadSkillSources(spec.skillSources()));
		toolRegistry.loadToolSources(spec.toolSources());

		var systemPromptWithSkills = systemPrompt + buildAgentSkillContext();
		var prompt = new Prompt(List.of(new SystemMessage(systemPromptWithSkills), new UserMessage(safePrompt)));

		var llmResult = invokeLlm(name, model, provider, prompt, systemPromptWithSkills, safePrompt, List.of());
		if (llmResult.blocked()) {
			return AgentResponse.blocked(name, llmResult.blockReason(), llmResult.errorMessage());
		}

		validateOutput(name, llmResult.answer(), guardrails);
		var usage = buildUsage(llmResult, model, 0);
		return AgentResponse.create(name, model, systemPromptWithSkills, llmResult.answer(), List.of(), usage);
	}

	private LlmProvider resolveProvider(AiAgentSpec spec) {
		var ref = spec.providerRef();
		if (ref == null) {
			return null;
		}
		return providerResolver.flatMap(r -> r.resolve(ref.name(), ref.namespace())).orElse(null);
	}

	// ── Pipeline steps ─────────────────────────────────────────────────────

	private String resolveModel(AiAgentSpec spec, LlmProvider provider) {
		if (spec.model() != null && !spec.model().isBlank()) {
			return spec.model();
		}
		if (provider != null && provider.getSpec() != null && provider.getSpec().defaultModel() != null) {
			return provider.getSpec().defaultModel();
		}
		return DEFAULT_MODEL;
	}

	private String resolveQuery(AiAgentSpec spec, String customInput) {
		if (customInput != null && !customInput.isBlank()) {
			return customInput;
		}
		return spec.userPrompt() != null ? spec.userPrompt() : DEFAULT_USER_PROMPT;
	}

	private List<ToolResult> executeTools(String agentName, List<ToolDefinition> toolDefs, String query) {
		log.info("Executing tools for Agent '{}' on Virtual Thread", agentName);
		return toolRegistry.executeActiveTools(toolDefs, query).values().stream().toList();
	}

	private String buildSystemPrompt(String basePrompt, String query, List<ToolResult> tools) {
		var prompt = basePrompt != null ? basePrompt : DEFAULT_SYSTEM_PROMPT;
		return prompt + buildToolContext(tools) + buildAgentSkillContext() + retrieveRagContext(query);
	}

	private String buildToolContext(List<ToolResult> tools) {
		if (tools.isEmpty()) {
			return "";
		}
		return "\n\nAvailable Context from Tools:\n" + tools.stream()
				.map(t -> "[%s]: %s".formatted(t.toolName(), t.output())).reduce((a, b) -> a + "\n" + b).orElse("");
	}

	private String buildAgentSkillContext() {
		if (skillRegistry.isEmpty() || skillRegistry.get().getRegisteredSkills().isEmpty()) {
			return "";
		}
		String skillsText = skillRegistry.get().getRegisteredSkills().values().stream()
				.map(s -> "### Skill: %s\n%s\n\n%s".formatted(s.name(), s.description(), s.instructions()))
				.collect(Collectors.joining("\n---\n"));
		return "\n\nAgent Skills & Guidelines (SKILL.md):\n" + skillsText;
	}

	private String retrieveRagContext(String query) {
		if (query == null || query.isBlank()) {
			return "";
		}
		return ragService.map(rag -> rag.retrieveAsPrompt(query, RAG_RESULT_COUNT)).filter(ctx -> !ctx.isBlank())
				.orElse("");
	}

	// ── LLM invocation ─────────────────────────────────────────────────────

	private record LlmResult(String answer, int inputTokens, int outputTokens, double costUsd, boolean usedFallback,
			boolean blocked, String blockReason, String errorMessage) {

		static LlmResult success(String answer, int inputTokens, int outputTokens, double costUsd,
				boolean usedFallback) {
			return new LlmResult(answer, inputTokens, outputTokens, costUsd, usedFallback, false, null, null);
		}

		static LlmResult simulated(String answer, int inputTokens, int outputTokens) {
			return success(answer, inputTokens, outputTokens, 0.0, true);
		}

		static LlmResult blocked(String reason, String message) {
			return new LlmResult("", 0, 0, 0.0, false, true, reason, message);
		}
	}

	private LlmResult invokeLlm(String agentName, String model, LlmProvider provider, Prompt prompt,
			String systemPrompt, String query, List<ToolResult> tools) {

		if (modelGateway.isPresent() && chatModel.isPresent()) {
			return invokeViaGateway(agentName, model, provider, prompt, systemPrompt, query, tools);
		}
		if (chatModel.isPresent()) {
			return invokeViaChatModel(agentName, model, prompt, systemPrompt, query, tools);
		}
		return invokeSimulated(agentName, model, systemPrompt, query, tools);
	}

	private LlmResult invokeViaGateway(String agentName, String model, LlmProvider provider, Prompt prompt,
			String systemPrompt, String query, List<ToolResult> tools) {
		try {
			var result = modelGateway.get().invoke(prompt, provider, model, providerResolver.orElse(null), null,
					agentName);
			return LlmResult.success(result.answer(), result.inputTokens(), result.outputTokens(), result.costUsd(),
					result.usedFallback());
		} catch (ModelGateway.BudgetExceededException e) {
			log.warn("Agent '{}' budget exceeded: {}", agentName, e.getMessage());
			return LlmResult.blocked("model-gateway-budget", e.getMessage());
		} catch (ModelGateway.ModelGatewayException e) {
			log.warn("Model Gateway failed for '{}', falling back: {}", agentName, e.getMessage());
			return invokeSimulated(agentName, model, systemPrompt, query, tools);
		}
	}

	private LlmResult invokeViaChatModel(String agentName, String model, Prompt prompt, String systemPrompt,
			String query, List<ToolResult> tools) {
		try {
			log.info("Calling Spring AI ChatModel [{}] for agent '{}'", model, agentName);
			var response = chatModel.get().call(prompt);
			var answer = response.getResult().getOutput().getText();
			var usage = extractUsage(response, systemPrompt, query, answer);
			return LlmResult.success(answer, usage.inputTokens, usage.outputTokens, 0.0, false);
		} catch (Exception e) {
			log.warn("Spring AI call failed for '{}', falling back: {}", agentName, e.getMessage());
			return invokeSimulated(agentName, model, systemPrompt, query, tools);
		}
	}

	private TokenCount extractUsage(ChatResponse response, String systemPrompt, String query, String answer) {
		return Optional.of(response).map(ChatResponse::getMetadata).map(ChatResponseMetadata::getUsage)
				.map(u -> new TokenCount(u.getPromptTokens().intValue(), u.getCompletionTokens().intValue()))
				.orElse(new TokenCount(estimateTokens(systemPrompt + query), estimateTokens(answer)));
	}

	private LlmResult invokeSimulated(String agentName, String model, String systemPrompt, String query,
			List<ToolResult> tools) {
		log.info("Generating simulated response for '{}'", agentName);
		var answer = generateSimulatedResponse(agentName, model, systemPrompt, query, tools);
		return LlmResult.simulated(answer, estimateTokens(systemPrompt + query), estimateTokens(answer));
	}

	// ── Output validation ──────────────────────────────────────────────────

	private void validateOutput(String agentName, String answer, com.tuluat.crd.agent.GuardrailsConfig guardrails) {
		if (guardrails == null || guardrails.outputValidation() == null || !guardrails.outputValidation().isEnabled()) {
			return;
		}
		var result = guardrailPipeline.validateOutput(answer, guardrails, null);
		if (!result.valid()) {
			log.warn("Agent '{}' output rejected: confidence={}, errors={}", agentName, result.confidence(),
					result.errors());
		}
	}

	// ── Utility ────────────────────────────────────────────────────────────

	private record TokenCount(int inputTokens, int outputTokens) {
	}

	private UsageStats buildUsage(LlmResult result, String model, long latencyMs) {
		var usage = UsageStats.calculate(result.inputTokens, result.outputTokens, model, latencyMs);
		return result.costUsd > 0 ? usage.withCostUsd(result.costUsd) : usage;
	}

	private int estimateTokens(String text) {
		return text != null ? (int) Math.ceil(text.length() / 4.0) : 0;
	}

	private String generateSimulatedResponse(String agentName, String model, String systemPrompt, String query,
			List<ToolResult> tools) {
		var sb = new StringBuilder();
		sb.append("[Simulated response from agent '%s' using model '%s']\n\n".formatted(agentName, model));
		sb.append("Query: %s\n\n".formatted(query));
		if (!tools.isEmpty()) {
			sb.append("Tools executed:\n");
			tools.forEach(t -> sb.append("  - %s: %s\n".formatted(t.toolName(), t.output())));
		}
		sb.append("System prompt: %s\n".formatted(systemPrompt));
		return sb.toString();
	}
}
