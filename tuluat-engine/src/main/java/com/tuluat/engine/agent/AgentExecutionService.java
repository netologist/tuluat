package com.tuluat.engine.agent;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.engine.gateway.ModelGateway;
import com.tuluat.engine.gateway.ProviderResolver;
import com.tuluat.engine.rag.RagService;
import com.tuluat.engine.skill.SkillRegistry;
import com.tuluat.engine.skill.SkillResult;
import com.tuluat.guardrails.GuardrailBlockedException;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.guardrails.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentExecutionService {

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

	public AgentResponse processAgentPrompt(AiAgent agent, LlmProvider provider, String customInput) {
		long startTime = System.currentTimeMillis();
		var spec = agent.getSpec();
		String agentName = agent.getMetadata().getName();

		String model = (spec.model() != null && !spec.model().isBlank())
				? spec.model()
				: (provider != null && provider.getSpec() != null && provider.getSpec().defaultModel() != null)
						? provider.getSpec().defaultModel()
						: "deepseek-chat";

		String query = (customInput != null && !customInput.isBlank())
				? customInput
				: (spec.userPrompt() != null) ? spec.userPrompt() : "Hello AI Agent";

		String safeQuery = query;
		try {
			safeQuery = guardrailPipeline.processPrompt(query, spec.guardrails());
		} catch (GuardrailBlockedException e) {
			log.warn("Agent '{}' request blocked by guardrail [{}]: {}", agentName, e.getFilterName(), e.getMessage());
			return AgentResponse.blocked(agentName, e.getFilterName(), e.getMessage());
		}

		log.info("Executing skills for Agent '{}' on Virtual Thread", agentName);
		Map<String, SkillResult> skillResultsMap = skillRegistry.executeActiveSkills(spec.skills(), safeQuery);
		List<SkillResult> skillResults = new ArrayList<>(skillResultsMap.values());

		String skillContext = skillResults.stream().map(res -> String.format("[%s]: %s", res.skillName(), res.output()))
				.collect(Collectors.joining("\n"));

		String baseSystemPrompt = spec.systemPrompt() != null ? spec.systemPrompt() : "You are a helpful AI assistant.";
		String ragContext = ragService.isPresent() && safeQuery != null && !safeQuery.isBlank()
				? ragService.get().retrieveAsPrompt(safeQuery, 3)
				: "";
		String effectiveSystemPrompt = baseSystemPrompt;
		if (!skillContext.isBlank()) {
			effectiveSystemPrompt += "\n\nAvailable Context from Tools/Skills:\n" + skillContext;
		}
		if (!ragContext.isBlank()) {
			effectiveSystemPrompt += ragContext;
		}

		String aiAnswer;
		int inputTokens = 0;
		int outputTokens = 0;
		boolean usedFallback = false;
		double costUsd = 0.0;

		var systemMsg = new SystemMessage(effectiveSystemPrompt);
		var userMsg = new UserMessage(safeQuery);
		var prompt = new Prompt(List.of(systemMsg, userMsg));

		if (modelGateway.isPresent() && chatModel.isPresent()) {
			try {
				ModelGateway.GatewayCallResult gw = modelGateway.get().invoke(prompt, provider, model,
						providerResolver.orElse(null), null, agentName);
				aiAnswer = gw.answer();
				inputTokens = gw.inputTokens();
				outputTokens = gw.outputTokens();
				costUsd = gw.costUsd();
				usedFallback = gw.usedFallback();
			} catch (ModelGateway.BudgetExceededException e) {
				log.warn("Agent '{}' budget exceeded: {}", agentName, e.getMessage());
				return AgentResponse.blocked(agentName, "model-gateway-budget", e.getMessage());
			} catch (ModelGateway.ModelGatewayException e) {
				log.warn("Model Gateway failed for agent '{}', falling back: {}", agentName, e.getMessage());
				aiAnswer = generateSimulatedResponse(agentName, model, effectiveSystemPrompt, safeQuery, skillResults);
				inputTokens = estimateTokens(effectiveSystemPrompt + safeQuery);
				outputTokens = estimateTokens(aiAnswer);
			}
		} else if (chatModel.isPresent()) {
			try {
				log.info("Calling Spring AI ChatModel [{}] for agent '{}'", model, agentName);
				ChatModel cm = chatModel.get();
				var response = cm.call(prompt);
				aiAnswer = response.getResult().getOutput().getText();

				if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
					var usage = response.getMetadata().getUsage();
					inputTokens = usage.getPromptTokens().intValue();
					outputTokens = usage.getCompletionTokens().intValue();
				} else {
					inputTokens = estimateTokens(effectiveSystemPrompt + safeQuery);
					outputTokens = estimateTokens(aiAnswer);
				}
			} catch (Exception e) {
				log.warn("Spring AI call failed, falling back to simulated: {}", e.getMessage());
				aiAnswer = generateSimulatedResponse(agentName, model, effectiveSystemPrompt, safeQuery, skillResults);
				inputTokens = estimateTokens(effectiveSystemPrompt + safeQuery);
				outputTokens = estimateTokens(aiAnswer);
			}
		} else {
			log.info("ChatModel not bound. Generating simulated response for '{}'", agentName);
			aiAnswer = generateSimulatedResponse(agentName, model, effectiveSystemPrompt, safeQuery, skillResults);
			inputTokens = estimateTokens(effectiveSystemPrompt + safeQuery);
			outputTokens = estimateTokens(aiAnswer);
		}

		if (spec.guardrails() != null && spec.guardrails().outputValidation() != null
				&& spec.guardrails().outputValidation().isEnabled()) {
			ValidationResult vr = guardrailPipeline.validateOutput(aiAnswer, spec.guardrails(), null);
			if (!vr.valid()) {
				log.warn("Agent '{}' output rejected by guardrails: confidence={}, errors={}", agentName,
						vr.confidence(), vr.errors());
			}
		}

		long latency = System.currentTimeMillis() - startTime;
		UsageStats usageStats = UsageStats.calculate(inputTokens, outputTokens, model, latency);
		if (costUsd > 0) {
			usageStats = usageStats.withCostUsd(costUsd);
		}

		return AgentResponse.create(agentName, model, effectiveSystemPrompt, aiAnswer, skillResults, usageStats);
	}

	public AgentResponse executeAgent(String agentRef, String prompt, String context) {
		log.info("Executing agentRef '{}' with prompt '{}'", agentRef, prompt);
		String ns = (context != null && !context.isBlank()) ? context : null;

		var spec = agentResolver.flatMap(ar -> ar.resolve(agentRef, ns));
		var guardrails = spec.map(a -> a.getSpec() != null ? a.getSpec().guardrails() : null).orElse(null);

		String safePrompt = prompt;
		try {
			safePrompt = guardrailPipeline.processPrompt(prompt, guardrails);
		} catch (GuardrailBlockedException e) {
			log.warn("Agent '{}' request blocked by guardrail [{}]: {}", agentRef, e.getFilterName(), e.getMessage());
			return AgentResponse.blocked(agentRef, e.getFilterName(), e.getMessage());
		}

		AgentResponse response = AgentResponse.create(agentRef != null ? agentRef : "default-agent", "deepseek-chat",
				"Workflow Agent System Prompt", "Execution completed for: " + safePrompt, List.of(),
				UsageStats.calculate(10, 10, "deepseek-chat", 50));

		if (guardrails != null && guardrails.outputValidation() != null && guardrails.outputValidation().isEnabled()) {
			ValidationResult vr = guardrailPipeline.validateOutput(response.answer(), guardrails, null);
			if (!vr.valid()) {
				log.warn("Agent '{}' output rejected by guardrails: confidence={}, errors={}", agentRef,
						vr.confidence(), vr.errors());
			}
		}
		return response;
	}

	private int estimateTokens(String text) {
		return text != null ? (int) Math.ceil(text.length() / 4.0) : 0;
	}

	private String generateSimulatedResponse(String agentName, String model, String systemPrompt, String query,
			List<SkillResult> skills) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("[Simulated response from agent '%s' using model '%s']\n\n", agentName, model));
		sb.append(String.format("Query: %s\n\n", query));
		if (!skills.isEmpty()) {
			sb.append("Skills executed:\n");
			skills.forEach(s -> sb.append(String.format("  - %s: %s\n", s.skillName(), s.output())));
		}
		sb.append(String.format("System prompt: %s\n", systemPrompt));
		return sb.toString();
	}
}
