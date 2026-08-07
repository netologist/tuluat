package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A2A (Agent-to-Agent) capability declaration for an AiAgent (optional).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record A2aConfig(
    @JsonProperty("enabled") Boolean enabled,
    @JsonProperty("remoteDiscovery") String remoteDiscovery
) {
    public A2aConfig {
        if (enabled == null) {
            enabled = false;
        }
    }
}
