package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Prompt injection defense policy for an AiAgent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromptInjectionConfig(@JsonProperty("enabled") Boolean enabled, @JsonProperty("strategy") String strategy // BLOCK,
																														// SANITIZE
) {
	public PromptInjectionConfig {
		if (strategy == null || strategy.isBlank()) {
			strategy = "BLOCK";
		}
	}

	public boolean isEnabled() {
		return enabled == null || enabled;
	}
}
