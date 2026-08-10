package com.tuluat.crd.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Spec record for LLM Provider CRD.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmProviderSpec(@JsonProperty("providerType") String providerType, // OPENAI, OLLAMA, ANTHROPIC, etc.
		@JsonProperty("baseUrl") String baseUrl, @JsonProperty("apiKeySecretRef") SecretKeyRef apiKeySecretRef,
		@JsonProperty("defaultModel") String defaultModel, @JsonProperty("temperature") Double temperature,
		@JsonProperty("maxTokens") Integer maxTokens, @JsonProperty("costPer1kInputTokens") Double costPer1kInputTokens,
		@JsonProperty("costPer1kOutputTokens") Double costPer1kOutputTokens,
		@JsonProperty("fallbacks") List<ModelFallback> fallbacks) {
	public LlmProviderSpec {
		if (providerType == null || providerType.isBlank()) {
			providerType = "OPENAI";
		}
		if (temperature == null) {
			temperature = 0.7;
		}
		if (maxTokens == null) {
			maxTokens = 2048;
		}
		if (costPer1kInputTokens == null) {
			costPer1kInputTokens = 0.0;
		}
		if (costPer1kOutputTokens == null) {
			costPer1kOutputTokens = 0.0;
		}
		if (fallbacks == null) {
			fallbacks = List.of();
		}
	}
}
