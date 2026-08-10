package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Pre-execution PII masking policy for an AiAgent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PiiMaskingConfig(@JsonProperty("enabled") Boolean enabled, @JsonProperty("modes") List<String> modes, // EMAIL,
																													// CREDIT_CARD,
																													// SSN,
																													// PHONE,
																													// ...
		@JsonProperty("replacementToken") String replacementToken) {
	public PiiMaskingConfig {
		if (modes == null) {
			modes = List.of("EMAIL", "CREDIT_CARD", "SSN", "PHONE");
		}
		if (replacementToken == null) {
			replacementToken = "[REDACTED]";
		}
	}

	public boolean isEnabled() {
		return enabled == null || enabled;
	}
}
