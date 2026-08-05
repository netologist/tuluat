package com.tuluat.ai.crd.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Status record for LLM Provider CRD.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmProviderStatus(
    @JsonProperty("phase") String phase,             // Ready, Error, Pending
    @JsonProperty("message") String message,
    @JsonProperty("observedGeneration") Long observedGeneration,
    @JsonProperty("lastUpdated") String lastUpdated
) {
    public static LlmProviderStatus ready(String message, Long gen) {
        return new LlmProviderStatus("Ready", message, gen, java.time.Instant.now().toString());
    }

    public static LlmProviderStatus error(String message, Long gen) {
        return new LlmProviderStatus("Error", message, gen, java.time.Instant.now().toString());
    }

    public static LlmProviderStatus pending(String message, Long gen) {
        return new LlmProviderStatus("Pending", message, gen, java.time.Instant.now().toString());
    }
}
