package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Status record for AiAgent Custom Resource.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiAgentStatus(@JsonProperty("phase") String phase, @JsonProperty("ingressUrl") String ingressUrl,
		@JsonProperty("activeTools") List<String> activeTools, @JsonProperty("effectiveModel") String effectiveModel,
		@JsonProperty("message") String message, @JsonProperty("observedGeneration") Long observedGeneration,
		@JsonProperty("lastReconciledAt") String lastReconciledAt) {
	public static AiAgentStatus ready(String ingressUrl, List<String> activeTools, String effectiveModel,
			String message, Long gen) {
		return new AiAgentStatus("Ready", ingressUrl, activeTools, effectiveModel, message, gen,
				java.time.Instant.now().toString());
	}

	public static AiAgentStatus reconciling(String message, Long gen) {
		return new AiAgentStatus("Reconciling", null, List.of(), null, message, gen,
				java.time.Instant.now().toString());
	}

	public static AiAgentStatus failed(String message, Long gen) {
		return new AiAgentStatus("Failed", null, List.of(), null, message, gen, java.time.Instant.now().toString());
	}
}
