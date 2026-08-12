package com.tuluat.engine.embabel;

import com.embabel.common.ai.autoconfig.ProviderInitialization;
import com.embabel.common.ai.autoconfig.RegisteredModel;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.tuluat.crd.provider.LlmProvider;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads {@link LlmProvider} CRDs at startup and periodically to register
 * additional models with Embabel's Goal-Oriented Action Planning engine.
 *
 * <p>
 * The {@code embabel-agent-starter-openai-custom} starter provides the static
 * fallback. This configuration adds CRD-sourced models on top. When no CRDs
 * exist, an empty {@link ProviderInitialization} is returned and the static
 * config handles everything.
 *
 * <p>
 * Dynamic reconciliation: every 60 seconds (via {@code @Scheduled}), the
 * configuration re-scans the cluster for new or updated {@code LlmProvider}
 * CRDs and registers their models without requiring an operator restart.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(KubernetesClient.class)
@EnableScheduling
@Slf4j
public class CrdEmbabelConfiguration {

	private static final Map<String, String> BEAN_NAME_BY_TYPE = Map.of("OPENAI", "openAiChatModel", "ANTHROPIC",
			"anthropicChatModel", "OLLAMA", "ollamaChatModel");

	static final String TULUAT_SYSTEM = "tuluat-system";

	private final KubernetesClient kubernetesClient;
	private final ConfigurableBeanFactory beanFactory;

	/**
	 * Tracks already-registered provider names so we skip redundant re-registration
	 * during periodic reconciliation.
	 */
	private final Set<String> registeredProviderNames = ConcurrentHashMap.newKeySet();

	public CrdEmbabelConfiguration(KubernetesClient kubernetesClient, ConfigurableBeanFactory beanFactory) {
		this.kubernetesClient = kubernetesClient;
		this.beanFactory = beanFactory;
	}

	/**
	 * Initial Embabel provider registration from CRDs at application startup.
	 * Subsequent changes are picked up by {@link #reconcileCrdProviders()}.
	 */
	@Bean
	ProviderInitialization crdProviderInitialization() {
		return scanAndRegisterProviders("startup");
	}

	/**
	 * Periodic reconciliation: re-scans the cluster for new or updated
	 * {@link LlmProvider} CRDs and registers their models dynamically.
	 * Already-registered providers are skipped to avoid re-creating singleton
	 * beans.
	 */
	@Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT30S")
	void reconcileCrdProviders() {
		log.debug("Embabel CRD reconciliation: scanning for new LlmProvider CRDs...");
		scanAndRegisterProviders("scheduled");
	}

	private ProviderInitialization scanAndRegisterProviders(String source) {
		var providers = kubernetesClient.resources(LlmProvider.class).inNamespace(TULUAT_SYSTEM).list().getItems();

		if (providers.isEmpty()) {
			if ("startup".equals(source)) {
				log.info("No LlmProvider CRDs found in '{}'. Static config will be used.", TULUAT_SYSTEM);
			}
			return new ProviderInitialization("crd-empty", List.of(), List.of(), Instant.now());
		}

		log.info("{}: discovered {} LlmProvider CRD(s) in '{}'", source, providers.size(), TULUAT_SYSTEM);
		List<RegisteredModel> registeredModels = new ArrayList<>();

		for (LlmProvider provider : providers) {
			registerFromCrd(provider, registeredModels);
		}

		if (registeredModels.isEmpty()) {
			log.warn("{}: no models registered from CRDs.", source);
			return new ProviderInitialization("crd-fallback", List.of(), List.of(), Instant.now());
		}

		log.info("{}: CRD-backed Embabel models: {}", source, registeredModels.size());
		return new ProviderInitialization("crd", registeredModels, List.of(), Instant.now());
	}

	private void registerFromCrd(LlmProvider provider, List<RegisteredModel> out) {
		var spec = provider.getSpec();
		var name = provider.getMetadata().getName();
		var ns = provider.getMetadata().getNamespace();
		var type = spec.providerType().toUpperCase();

		// Skip already-registered providers during periodic reconciliation
		if (registeredProviderNames.contains(name)) {
			log.debug("Provider '{}' already registered — skipping.", name);
			return;
		}

		String beanName = BEAN_NAME_BY_TYPE.get(type);
		if (beanName == null) {
			log.warn("Unknown providerType '{}' for LlmProvider '{}'. Skipping.", type, name);
			return;
		}

		if (beanFactory.containsSingleton(beanName)) {
			log.info("ChatModel bean '{}' already registered. Referencing existing for LlmProvider '{}'.", beanName,
					name);
			registeredProviderNames.add(name);
			out.add(new RegisteredModel(beanName, spec.defaultModel()));
			return;
		}

		try {
			String apiKey = resolveApiKey(spec, ns);
			ChatModel chatModel = buildOpenAiChatModel(spec.baseUrl(), apiKey);
			beanFactory.registerSingleton(beanName, chatModel);
			registeredProviderNames.add(name);
			out.add(new RegisteredModel(beanName, spec.defaultModel()));
			log.info("Registered model '{}' (bean: {}) from LlmProvider '{}'", spec.defaultModel(), beanName, name);
		} catch (Exception e) {
			log.error("Failed to register model for LlmProvider '{}': {}", name, e.getMessage());
		}
	}

	private String resolveApiKey(com.tuluat.crd.provider.LlmProviderSpec spec, String namespace) {
		var secretRef = spec.apiKeySecretRef();
		if (secretRef == null) {
			throw new IllegalStateException("LlmProvider has no apiKeySecretRef");
		}
		var secret = kubernetesClient.secrets().inNamespace(namespace).withName(secretRef.name()).get();
		if (secret == null || secret.getData() == null) {
			throw new IllegalStateException(
					"Secret '" + secretRef.name() + "' not found in namespace '" + namespace + "'");
		}
		var encoded = secret.getData().get(secretRef.key());
		if (encoded == null) {
			throw new IllegalStateException(
					"Key '" + secretRef.key() + "' not found in Secret '" + secretRef.name() + "'");
		}
		return new String(Base64.getDecoder().decode(encoded));
	}

	static ChatModel buildOpenAiChatModel(String baseUrl, String apiKey) {
		OpenAIClient client = OpenAIOkHttpClient.builder().baseUrl(baseUrl).apiKey(apiKey).build();
		return OpenAiChatModel.builder().openAiClient(client).build();
	}
}