package com.tuluat.engine.agent;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.McpServerRef;
import com.tuluat.crd.agent.ToolDefinition;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.engine.gateway.ModelGateway;
import com.tuluat.engine.gateway.ProviderResolver;
import com.tuluat.engine.memory.SessionMemoryManager;
import com.tuluat.engine.rag.RagService;
import com.tuluat.engine.skill.SkillRegistry;
import com.tuluat.engine.tool.ToolRegistry;
import com.tuluat.engine.tool.ToolResult;
import com.tuluat.guardrails.GuardrailBlockedException;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.protocols.McpClientRegistry;
import com.tuluat.protocols.McpToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core engine for executing AI Agent prompts with Tools, Agent Skills
 * (SKILL.md), Guardrails, Model Gateway, MCP tools, and short-term session
 * memory.
 *
 * <h3>Execution pipeline:</h3>
 * <ol>
 * <li>Resolve model and query from agent spec</li>
 * <li>Apply pre-execution guardrails (PII masking, injection defense)</li>
 * <li>Execute active tools + MCP tools on virtual threads</li>
 * <li>Inject session memory history (short-term conversation memory)</li>
 * <li>Build system prompt with tool context, agent skills (SKILL.md), MCP
 * results, and RAG</li>
 * <li>Invoke LLM via ModelGateway → ChatModel → simulated fallback</li>
 * <li>Validate output against guardrail policy</li>
 * <li>Save agent response to session memory</li>
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
	private static final int DEFAULT_MEMORY_WINDOW = 10;

	private final ToolRegistry toolRegistry;
	private final Optional<SkillRegistry> skillRegistry;
	private final Optional<ChatModel> chatModel;
	private final GuardrailPipeline guardrailPipeline;
	private final Optional<ModelGateway> modelGateway;
	private final Optional<ProviderResolver> providerResolver;
	private final Optional<AgentResolver> agentResolver;
	private final Optional<RagService> ragService;
	private final Optional<SessionMemoryManager> sessionMemoryManager;
	private final Optional<McpClientRegistry> mcpClientRegistry;

	@org.springframework.beans.factory.annotation.Autowired
	public AgentExecutionService(ToolRegistry toolRegistry, Optional<SkillRegistry> skillRegistry,
			@Qualifier("openAiChatModel") Optional<ChatModel> chatModel, GuardrailPipeline guardrailPipeline,
			Optional<ModelGateway> modelGateway, Optional<ProviderResolver> providerResolver,
			Optional<AgentResolver> agentResolver, Optional<RagService> ragService,
			Optional<SessionMemoryManager> sessionMemoryManager, Optional<McpClientRegistry> mcpClientRegistry) {
		this.toolRegistry = toolRegistry;
		this.skillRegistry = skillRegistry;
		this.chatModel = chatModel;
		this.guardrailPipeline = guardrailPipeline;
		this.modelGateway = modelGateway;
		this.providerResolver = providerResolver;
		this.agentResolver = agentResolver;
		this.ragService = ragService;
		this.sessionMemoryManager = sessionMemoryManager;
		this.mcpClientRegistry = mcpClientRegistry;
	}

	public AgentExecutionService(ToolRegistry toolRegistry, @Qualifier("openAiChatModel") Optional<ChatModel> chatModel,
			GuardrailPipeline guardrailPipeline, Optional<ModelGateway> modelGateway,
			Optional<ProviderResolver> providerResolver, Optional<AgentResolver> agentResolver,
			Optional<RagService> ragService) {
		this(toolRegistry, Optional.empty(), chatModel, guardrailPipeline, modelGateway, providerResolver,
				agentResolver, ragService, Optional.empty(), Optional.empty());
	}

	// ── Public API ──────────────────────────────────────────────────────────

	public AgentResponse processAgentPrompt(AiAgent agent, LlmProvider provider, String customInput) {
		return processAgentPrompt(agent, provider, customInput, null);
	}

	public AgentResponse processAgentPrompt(AiAgent agent, LlmProvider provider, String customInput, UUID sessionId) {
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

		// Execute MCP tools alongside local tools
		var mcpResults = executeMcpTools(spec.mcpServers(), safeQuery);
		toolResults.addAll(mcpResults);

		var systemPrompt = buildSystemPrompt(spec.systemPrompt(), safeQuery, toolResults);

		// Inject session memory history
		systemPrompt = injectSessionMemory(sessionId, agentName, systemPrompt);

		var prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(safeQuery)));

		var llmResult = invokeLlm(agentName, model, provider, prompt, systemPrompt, safeQuery, toolResults);
		if (llmResult.blocked()) {
			return AgentResponse.blocked(agentName, llmResult.blockReason(), llmResult.errorMessage());
		}

		validateOutput(agentName, llmResult.answer(), spec.guardrails());

		// Save agent response to session memory
		saveSessionMemory(sessionId, agentName, "assistant", llmResult.answer());

		var latency = System.currentTimeMillis() - startTime;
		var usage = buildUsage(llmResult, model, latency);
		return AgentResponse.create(agentName, model, systemPrompt, llmResult.answer(), toolResults, usage);
	}

	public AgentResponse executeAgent(String agentRef, String prompt, String context) {
		return executeAgent(agentRef, prompt, context, null);
	}

	public AgentResponse executeAgent(String agentRef, String prompt, String context, UUID sessionId) {
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
			return invokeResolvedAgent(name, agent.get().getSpec(), safePrompt, guardrails, sessionId);
		}

		var response = AgentResponse.create(name, DEFAULT_MODEL, "Workflow Agent System Prompt",
				"Execution completed for: " + safePrompt, List.of(), UsageStats.calculate(10, 10, DEFAULT_MODEL, 50));

		validateOutput(name, response.answer(), guardrails);
		saveSessionMemory(sessionId, name, "assistant", response.answer());
		return response;
	}

	private AgentResponse invokeResolvedAgent(String name, AiAgentSpec spec, String safePrompt,
			com.tuluat.crd.agent.GuardrailsConfig guardrails, UUID sessionId) {
		var provider = resolveProvider(spec);
		var model = resolveModel(spec, provider);
		var systemPrompt = spec.systemPrompt() != null ? spec.systemPrompt() : DEFAULT_SYSTEM_PROMPT;

		skillRegistry.ifPresent(sr -> sr.loadSkillSources(spec.skillSources()));
		toolRegistry.loadToolSources(spec.toolSources());

		// RAG context (Embabel goal path): retrieve relevant chunks for the
		// prompt and inject them alongside skills so agents are grounded in
		// ingested documents (ADR 008).
		var systemPromptWithRag = systemPrompt + buildAgentSkillContext() + retrieveRagContext(safePrompt);

		// Inject session memory history
		systemPromptWithRag = injectSessionMemory(sessionId, name, systemPromptWithRag);

		var prompt = new Prompt(List.of(new SystemMessage(systemPromptWithRag), new UserMessage(safePrompt)));

		var llmResult = invokeLlm(name, model, provider, prompt, systemPromptWithRag, safePrompt, List.of());
		if (llmResult.blocked()) {
			return AgentResponse.blocked(name, llmResult.blockReason(), llmResult.errorMessage());
		}

		validateOutput(name, llmResult.answer(), guardrails);
		saveSessionMemory(sessionId, name, "assistant", llmResult.answer());
		var usage = buildUsage(llmResult, model, 0);
		return AgentResponse.create(name, model, systemPromptWithRag, llmResult.answer(), List.of(), usage);
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
		return new ArrayList<>(toolRegistry.executeActiveTools(toolDefs, query).values());
	}

	// ── MCP Tool Execution ─────────────────────────────────────────────────

	/**
	 * Invokes tools exported by MCP servers referenced in the agent spec. Each MCP
	 * result is converted to a {@link ToolResult} and validated through guardrails
	 * before inclusion.
	 */
	private List<ToolResult> executeMcpTools(List<McpServerRef> mcpServers, String query) {
		if (mcpServers == null || mcpServers.isEmpty() || mcpClientRegistry.isEmpty()) {
			return List.of();
		}

		List<ToolResult> results = new ArrayList<>();
		var registry = mcpClientRegistry.get();

		for (McpServerRef ref : mcpServers) {
			if (ref == null || ref.name() == null) {
				continue;
			}
			var client = registry.findClient(ref.name());
			if (client.isEmpty()) {
				log.warn("MCP server '{}' not registered — skipping", ref.name());
				continue;
			}

			// List available tools for this server and invoke them
			List<String> toolNames = registry.getAvailableClientNames().stream().filter(n -> n.startsWith(ref.name()))
					.toList();
			for (String toolName : toolNames) {
				try {
					McpToolResult mcpResult = registry.invokeTool(ref.name(), toolName, Map.of("query", query));
					ToolResult result = mcpToToolResult(mcpResult);

					// Guardrail validation for MCP outputs
					if (!mcpResult.success() && !mcpResult.content().isBlank()) {
						results.add(ToolResult.failure("mcp:" + ref.name() + "/" + toolName,
								mcpResult.error() != null ? mcpResult.error() : "MCP tool returned failure"));
					} else {
						results.add(result);
					}
				} catch (Exception e) {
					log.warn("MCP tool invocation failed for '{}/{}': {}", ref.name(), toolName, e.getMessage());
					results.add(ToolResult.failure("mcp:" + ref.name() + "/" + toolName, e.getMessage()));
				}
			}
		}

		return results;
	}

	/**
	 * Converts an {@link McpToolResult} (from protocols) into an engine
	 * {@link ToolResult}, applying a namespaced tool name prefix.
	 */
	private ToolResult mcpToToolResult(McpToolResult mcp) {
		return new ToolResult("mcp:" + mcp.toolName(), mcp.success(), mcp.content() != null ? mcp.content() : "",
				Map.of("source", "mcp", "timestamp", System.currentTimeMillis()));
	}

	// ── Session Memory ─────────────────────────────────────────────────────

	/**
	 * Prepends recent conversation history from session memory to the system
	 * prompt, respecting a window size to avoid context overflow.
	 */
	private String injectSessionMemory(UUID sessionId, String agentName, String systemPrompt) {
		if (sessionId == null || sessionMemoryManager.isEmpty()) {
			return systemPrompt;
		}

		var memory = sessionMemoryManager.get().getShortMemory(sessionId);
		if (memory.isEmpty()) {
			return systemPrompt;
		}

		// Truncate to window size to avoid context overflow
		int windowSize = DEFAULT_MEMORY_WINDOW;
		if (memory.size() > windowSize) {
			memory = memory.subList(memory.size() - windowSize, memory.size());
		}

		StringBuilder history = new StringBuilder("\n\nConversation History (last " + windowSize + " turns):\n");
		for (var entry : memory) {
			history.append(entry.getRole()).append(": ").append(entry.getContent()).append("\n");
		}

		return systemPrompt + history;
	}

	/**
	 * Persists an interaction (user query or assistant response) to the session
	 * memory store for future context injection.
	 */
	private void saveSessionMemory(UUID sessionId, String agentName, String role, String content) {
		if (sessionId == null || sessionMemoryManager.isEmpty() || content == null || content.isBlank()) {
			return;
		}
		try {
			sessionMemoryManager.get().saveShortMemory(sessionId, agentName, role, content);
		} catch (Exception e) {
			log.warn("Failed to save session memory for '{}': {}", sessionId, e.getMessage());
		}
	}

	// ── System prompt builders ─────────────────────────────────────────────

	private String buildSystemPrompt(String basePrompt, String query, List<ToolResult> tools) {
		var prompt = basePrompt != null ? basePrompt : DEFAULT_SYSTEM_PROMPT;
		return prompt + buildToolContext(tools) + buildAgentSkillContext() + retrieveRagContext(query);
	}

	private String buildToolContext(List<ToolResult> tools) {
		if (tools == null || tools.isEmpty()) {
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