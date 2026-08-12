package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Status record for AiAgent Custom Resource.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiAgentStatus(@JsonProperty("phase") String phase, @JsonProperty("ingressUrl") String ingressUrl,
		@JsonProperty("activeSkills") List<String> activeSkills, @JsonProperty("activeTools") List<String> activeTools,
		@JsonProperty("activeMcpServers") List<String> activeMcpServers,
		@JsonProperty("effectiveModel") String effectiveModel, @JsonProperty("message") String message,
		@JsonProperty("observedGeneration") Long observedGeneration,
		@JsonProperty("lastReconciledAt") String lastReconciledAt) {

	public static AiAgentStatus ready(String ingressUrl, List<String> activeSkills, List<String> activeTools,
			List<String> activeMcpServers, String effectiveModel, String message, Long gen) {
		return new AiAgentStatus("Ready", ingressUrl, activeSkills, activeTools, activeMcpServers, effectiveModel,
				message, gen, java.time.Instant.now().toString());
	}

	public static AiAgentStatus ready(String ingressUrl, List<String> activeSkills, List<String> activeTools,
			String effectiveModel, String message, Long gen) {
		return ready(ingressUrl, activeSkills, activeTools, List.of(), effectiveModel, message, gen);
	}

	public static AiAgentStatus ready(String ingressUrl, List<String> activeTools, String effectiveModel,
			String message, Long gen) {
		return ready(ingressUrl, List.of(), activeTools, List.of(), effectiveModel, message, gen);
	}

	public static AiAgentStatus reconciling(String message, Long gen) {
		return new AiAgentStatus("Reconciling", null, List.of(), List.of(), List.of(), null, message, gen,
				java.time.Instant.now().toString());
	}

	public static AiAgentStatus failed(String message, Long gen) {
		return new AiAgentStatus("Failed", null, List.of(), List.of(), List.of(), null, message, gen,
				java.time.Instant.now().toString());
	}
}