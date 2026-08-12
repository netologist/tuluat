package com.tuluat.engine.gateway;

import java.util.Map;

/**
 * Shared constant mapping of provider type names to Spring AI starter bean
 * names. Used by {@link ModelGateway} for routing and
 * {@code CrdEmbabelConfiguration} for dynamic registration.
 *
 * <p>
 * Centralised so that provider renames or additions affect one location instead
 * of two duplicated maps.
 */
public final class ProviderBeanNames {

	/** Provider type → Spring AI ChatModel bean name. */
	public static final Map<String, String> CHAT_MODEL_BEANS = Map.of("OPENAI", "openAiChatModel", "OLLAMA",
			"ollamaChatModel", "ANTHROPIC", "anthropicChatModel");

	private ProviderBeanNames() {
	}
}