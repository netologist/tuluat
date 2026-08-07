package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Post-execution output validation policy for an AiAgent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutputValidationConfig(
    @JsonProperty("enabled") Boolean enabled,
    @JsonProperty("minConfidence") Double minConfidence
) {
    public OutputValidationConfig {
        if (minConfidence == null) {
            minConfidence = 0.5;
        }
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}
