package com.tuluat.engine.gateway;

import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import com.tuluat.crd.provider.ModelFallback;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Model Gateway (ADR 007): route resolution, ordered fallback chains, budget
 * enforcement, and cost tracking. Sits between {@code AgentExecutionService}
 * and the Spring AI provider beans. {@code LlmProvider} stays a pure transport
 * definition; routing policy lives here.
 */
@Service
@Slf4j
public class ModelGateway {
/** Bean-name resolution for provider types (Spring AI starter bean names). */
	private static final Map<String, String> PROVIDER_BEAN_NAMES = Map.of("OPENAI", "openAiChatModel", "OLLAMA",
			"ollamaChatModel", "ANTHROPIC", "anthropicChatModel");

	private final Map<String, ChatModel> chatModels;
	private final Map<String, BudgetState> budgets = new ConcurrentHashMap<>();

	public ModelGateway(Map<String, ChatModel> chatModels) {
		this.chatModels = chatModels;
	}

	/**
	 * Result of a gateway-invoked model call.
	 *
	 * @param answer
	 *            model output text
	 * @param modelUsed
	 *            effective model (after fallback resolution)
	 * @param inputTokens
	 *            prompt tokens
	 * @param outputTokens
	 *            completion tokens
	 * @param costUsd
	 *            estimated cost in USD
	 * @param usedFallback
	 *            true when the primary route failed and a fallback succeeded
	 */
	public record GatewayCallResult(String answer, String modelUsed, int inputTokens, int outputTokens, double costUsd,
			boolean usedFallback) {
	}

	/**
	 * Invoke the model for an agent with fallback and budget enforcement.
	 *
	 * @param prompt
	 *            assembled Spring AI prompt
	 * @param provider
	 *            primary LlmProvider (may be null)
	 * @param agentModel
	 *            agent-level model override (may be null)
	 * @param providerResolver
	 *            resolves fallback providers by name
	 * @param budgetLimitUsd
	 *            max spend for this execution; null disables budget
	 * @param agentName
	 *            agent id for budget tracking
	 * @return call result
	 */
	public GatewayCallResult invoke(Prompt prompt, LlmProvider provider, String agentModel,
			ProviderResolver providerResolver, Double budgetLimitUsd, String agentName) {
		// Resolve effective model name
		LlmProviderSpec spec = provider != null ? provider.getSpec() : null;
		String model = agentModel != null && !agentModel.isBlank()
				? agentModel
				: (spec != null && spec.defaultModel() != null ? spec.defaultModel() : "deepseek-chat");

		// Budget pre-check
		if (budgetLimitUsd != null && agentName != null) {
			BudgetState budget = budgets.computeIfAbsent(agentName, k -> new BudgetState());
			if (budget.spentUsd() >= budgetLimitUsd) {
				log.warn("ModelGateway budget exhausted for agent [{}]: spent=${}, limit=${}", agentName,
						budget.spentUsd(), budgetLimitUsd);
				throw new BudgetExceededException(agentName, budget.spentUsd(), budgetLimitUsd);
			}
		}

		// Primary route
		ChatModel primary = chatModelFor(provider);
		GatewayCallResult primaryResult = tryCall(prompt, model, primary, provider);
		if (primaryResult != null) {
			recordSpend(agentName, primaryResult.costUsd());
			return primaryResult;
		}

		// Fallback chain (ordered)
		if (provider != null && spec != null && spec.fallbacks() != null) {
			for (ModelFallback fallback : spec.fallbacks()) {
				Optional<LlmProvider> fbProvider = providerResolver == null
						? Optional.empty()
						: providerResolver.resolve(fallback.providerName(), fallback.namespace());
				if (fbProvider.isEmpty()) {
					log.warn("Fallback provider [{}] not found", fallback.providerName());
					continue;
				}
				LlmProvider fb = fbProvider.get();
				ChatModel fbModel = chatModelFor(fb);
				String fbModelName = fallback.model() != null && !fallback.model().isBlank()
						? fallback.model()
						: fb.getSpec() != null && fb.getSpec().defaultModel() != null
								? fb.getSpec().defaultModel()
								: model;
				GatewayCallResult fbResult = tryCall(prompt, fbModelName, fbModel, fb);
				if (fbResult != null) {
					log.info("ModelGateway fell back to provider [{}] model [{}]", fallback.providerName(),
							fbModelName);
					recordSpend(agentName, fbResult.costUsd());
					return new GatewayCallResult(fbResult.answer(), fbModelName, fbResult.inputTokens(),
							fbResult.outputTokens(), fbResult.costUsd(), true);
				}
			}
		}

		throw new ModelGatewayException(
				"All model routes failed for agent: " + (agentName != null ? agentName : "unknown"));
	}

	/** Current spend snapshot per agent (for observability). */
	public Map<String, Double> getSpendByAgent() {
		Map<String, Double> out = new ConcurrentHashMap<>();
		budgets.forEach((k, v) -> out.put(k, v.spentUsd()));
		return out;
	}

	private GatewayCallResult tryCall(Prompt prompt, String model, ChatModel chatModel, LlmProvider provider) {
		if (chatModel == null) {
			log.warn("No ChatModel bean for provider [{}]; skipping route", providerName(provider));
			return null;
		}
		try {
			var response = chatModel.call(prompt);
			if (response == null || response.getResult() == null) {
				return null;
			}
			String answer = response.getResult().getOutput().getText();
			int inputTokens = 0;
			int outputTokens = 0;
			if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
				inputTokens = response.getMetadata().getUsage().getPromptTokens().intValue();
				outputTokens = response.getMetadata().getUsage().getCompletionTokens().intValue();
			}
			double cost = estimateCost(provider, inputTokens, outputTokens);
			return new GatewayCallResult(answer, model, inputTokens, outputTokens, cost, false);
		} catch (Exception e) {
			log.warn("Model route [{}] failed: {}", model, e.getMessage());
			return null;
		}
	}

	private ChatModel chatModelFor(LlmProvider provider) {
		if (provider == null) {
			return null;
		}
		String beanName = PROVIDER_BEAN_NAMES.getOrDefault(providerName(provider), "openAiChatModel");
		return chatModels.get(beanName);
	}

	private String providerName(LlmProvider provider) {
		if (provider == null || provider.getSpec() == null) {
			return null;
		}
		String type = provider.getSpec().providerType();
		return type != null ? type.toUpperCase() : null;
	}

	private double estimateCost(LlmProvider provider, int inputTokens, int outputTokens) {
		if (provider == null || provider.getSpec() == null) {
			return 0.0;
		}
		LlmProviderSpec spec = provider.getSpec();
		double inCost = spec.costPer1kInputTokens() != null ? spec.costPer1kInputTokens() : 0.0;
		double outCost = spec.costPer1kOutputTokens() != null ? spec.costPer1kOutputTokens() : 0.0;
		return (inputTokens / 1000.0) * inCost + (outputTokens / 1000.0) * outCost;
	}

	private void recordSpend(String agentName, double costUsd) {
		if (agentName != null && costUsd > 0) {
			budgets.computeIfAbsent(agentName, k -> new BudgetState()).add(costUsd);
		}
	}

	private static final class BudgetState {
		private final AtomicReference<Double> spent = new AtomicReference<>(0.0);

		double spentUsd() {
			return spent.get();
		}

		void add(double amount) {
			spent.accumulateAndGet(amount, Double::sum);
		}
	}

	/** Thrown when an agent's configured budget cap is exceeded. */
	public static class BudgetExceededException extends RuntimeException {
		public BudgetExceededException(String agentName, double spentUsd, double limitUsd) {
			super(String.format("Budget exceeded for agent [%s]: spent=$%.4f limit=$%.4f", agentName, spentUsd,
					limitUsd));
		}
	}

	/** Thrown when every route in the fallback chain fails. */
	public static class ModelGatewayException extends RuntimeException {
		public ModelGatewayException(String message) {
			super(message);
		}
	}
}
