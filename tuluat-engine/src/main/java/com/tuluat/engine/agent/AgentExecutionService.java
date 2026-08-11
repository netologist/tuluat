package com.tuluat.engine.agent;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.engine.gateway.ModelGateway;
import com.tuluat.engine.gateway.ProviderResolver;
import com.tuluat.engine.rag.RagService;
import com.tuluat.engine.skill.SkillRegistry;
import com.tuluat.engine.skill.SkillResult;
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

/**
 * Core engine for executing AI Agent prompts with Skills, Guardrails, and Model
 * Gateway.
 *
 * <h3>Execution pipeline:</h3>
 * <ol>
 * <li>Resolve model and query from agent spec</li>
 * <li>Apply pre-execution guardrails (PII masking, injection defense)</li>
 * <li>Execute active skills on virtual threads</li>
 * <li>Build system prompt with skill context and RAG</li>
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

	private final SkillRegistry skillRegistry;
	private final Optional<ChatModel> chatModel;
	private final GuardrailPipeline guardrailPipeline;
	private final Optional<ModelGateway> modelGateway;
	private final Optional<ProviderResolver> providerResolver;
	private final Optional<AgentResolver> agentResolver;
	private final Optional<RagService> ragService;

	public AgentExecutionService(SkillRegistry skillRegistry,
			@Qualifier("openAiChatModel") Optional<ChatModel> chatModel, GuardrailPipeline guardrailPipeline,
			Optional<ModelGateway> modelGateway, Optional<ProviderResolver> providerResolver,
			Optional<AgentResolver> agentResolver, Optional<RagService> ragService) {
		this.skillRegistry = skillRegistry;
		this.chatModel = chatModel;
		this.guardrailPipeline = guardrailPipeline;
		this.modelGateway = modelGateway;
		this.providerResolver = providerResolver;
		this.agentResolver = agentResolver;
		this.ragService = ragService;
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

		var skillResults = executeSkills(agentName, spec.skills(), safeQuery);
		var systemPrompt = buildSystemPrompt(spec.systemPrompt(), safeQuery, skillResults);
		var prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(safeQuery)));

		var llmResult = invokeLlm(agentName, model, provider, prompt, systemPrompt, safeQuery, skillResults);
		if (llmResult.blocked()) {
			return AgentResponse.blocked(agentName, llmResult.blockReason(), llmResult.errorMessage());
		}

		validateOutput(agentName, llmResult.answer(), spec.guardrails());

		var latency = System.currentTimeMillis() - startTime;
		var usage = buildUsage(llmResult, model, latency);
		return AgentResponse.create(agentName, model, systemPrompt, llmResult.answer(), skillResults, usage);
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

	/**
	 * Workflow-node invocation for a resolved agent CR: applies the spec's model
	 * and provider, invokes the LLM via ModelGateway → ChatModel → simulated
	 * fallback, then validates the output against the agent's guardrails.
	 */
	private AgentResponse invokeResolvedAgent(String name, AiAgentSpec spec, String safePrompt,
			com.tuluat.crd.agent.GuardrailsConfig guardrails) {
		var provider = resolveProvider(spec);
		var model = resolveModel(spec, provider);
		var systemPrompt = spec.systemPrompt() != null ? spec.systemPrompt() : DEFAULT_SYSTEM_PROMPT;
		var prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(safePrompt)));

		var llmResult = invokeLlm(name, model, provider, prompt, systemPrompt, safePrompt, List.of());
		if (llmResult.blocked()) {
			return AgentResponse.blocked(name, llmResult.blockReason(), llmResult.errorMessage());
		}

		validateOutput(name, llmResult.answer(), guardrails);
		var usage = buildUsage(llmResult, model, 0);
		return AgentResponse.create(name, model, systemPrompt, llmResult.answer(), List.of(), usage);
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

	private List<SkillResult> executeSkills(String agentName, List<com.tuluat.crd.agent.SkillDefinition> skillDefs,
			String query) {
		log.info("Executing skills for Agent '{}' on Virtual Thread", agentName);
		return skillRegistry.executeActiveSkills(skillDefs, query).values().stream().toList();
	}

	private String buildSystemPrompt(String basePrompt, String query, List<SkillResult> skills) {
		var prompt = basePrompt != null ? basePrompt : DEFAULT_SYSTEM_PROMPT;
		return prompt + buildSkillContext(skills) + retrieveRagContext(query);
	}

	private String buildSkillContext(List<SkillResult> skills) {
		if (skills.isEmpty()) {
			return "";
		}
		return "\n\nAvailable Context from Tools/Skills:\n" + skills.stream()
				.map(s -> "[%s]: %s".formatted(s.skillName(), s.output())).reduce((a, b) -> a + "\n" + b).orElse("");
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
			String systemPrompt, String query, List<SkillResult> skills) {

		if (modelGateway.isPresent() && chatModel.isPresent()) {
			return invokeViaGateway(agentName, model, provider, prompt, systemPrompt, query, skills);
		}
		if (chatModel.isPresent()) {
			return invokeViaChatModel(agentName, model, prompt, systemPrompt, query, skills);
		}
		return invokeSimulated(agentName, model, systemPrompt, query, skills);
	}

	private LlmResult invokeViaGateway(String agentName, String model, LlmProvider provider, Prompt prompt,
			String systemPrompt, String query, List<SkillResult> skills) {
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
			return invokeSimulated(agentName, model, systemPrompt, query, skills);
		}
	}

	private LlmResult invokeViaChatModel(String agentName, String model, Prompt prompt, String systemPrompt,
			String query, List<SkillResult> skills) {
		try {
			log.info("Calling Spring AI ChatModel [{}] for agent '{}'", model, agentName);
			var response = chatModel.get().call(prompt);
			var answer = response.getResult().getOutput().getText();
			var usage = extractUsage(response, systemPrompt, query, answer);
			return LlmResult.success(answer, usage.inputTokens, usage.outputTokens, 0.0, false);
		} catch (Exception e) {
			log.warn("Spring AI call failed for '{}', falling back: {}", agentName, e.getMessage());
			return invokeSimulated(agentName, model, systemPrompt, query, skills);
		}
	}

	private TokenCount extractUsage(ChatResponse response, String systemPrompt, String query, String answer) {

		return Optional.of(response).map(ChatResponse::getMetadata).map(ChatResponseMetadata::getUsage)
				.map(u -> new TokenCount(u.getPromptTokens().intValue(), u.getCompletionTokens().intValue()))
				.orElse(new TokenCount(estimateTokens(systemPrompt + query), estimateTokens(answer)));
	}

	private LlmResult invokeSimulated(String agentName, String model, String systemPrompt, String query,
			List<SkillResult> skills) {
		log.info("Generating simulated response for '{}'", agentName);
		var answer = generateSimulatedResponse(agentName, model, systemPrompt, query, skills);
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
			List<SkillResult> skills) {
		var sb = new StringBuilder();
		sb.append("[Simulated response from agent '%s' using model '%s']\n\n".formatted(agentName, model));
		sb.append("Query: %s\n\n".formatted(query));
		if (!skills.isEmpty()) {
			sb.append("Skills executed:\n");
			skills.forEach(s -> sb.append("  - %s: %s\n".formatted(s.skillName(), s.output())));
		}
		sb.append("System prompt: %s\n".formatted(systemPrompt));
		return sb.toString();
	}
}
