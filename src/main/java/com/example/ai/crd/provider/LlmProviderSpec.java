package com.example.ai.crd.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Spec record for LLM Provider CRD.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmProviderSpec(
    @JsonProperty("providerType") String providerType, // OPENAI, OLLAMA, ANTHROPIC, etc.
    @JsonProperty("baseUrl") String baseUrl,
    @JsonProperty("apiKeySecretRef") SecretKeyRef apiKeySecretRef,
    @JsonProperty("defaultModel") String defaultModel,
    @JsonProperty("temperature") Double temperature,
    @JsonProperty("maxTokens") Integer maxTokens
) {
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
    }
}
