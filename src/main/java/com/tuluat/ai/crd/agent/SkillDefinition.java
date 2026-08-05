package com.tuluat.ai.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Definition of a Skill / Tool enabled for the AiAgent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SkillDefinition(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("enabled") Boolean enabled,
    @JsonProperty("parameters") Map<String, String> parameters
) {
    public SkillDefinition {
        if (enabled == null) {
            enabled = true;
        }
        if (parameters == null) {
            parameters = Map.of();
        }
    }
}
