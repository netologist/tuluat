package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Spec record for AiAgent Custom Resource.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiAgentSpec(
    @JsonProperty("providerRef") ProviderRef providerRef,
    @JsonProperty("model") String model,
    @JsonProperty("systemPrompt") String systemPrompt,
    @JsonProperty("userPrompt") String userPrompt,
    @JsonProperty("skills") List<SkillDefinition> skills,
    @JsonProperty("ingress") IngressSpec ingress,
    @JsonProperty("replicas") Integer replicas
) {
    public AiAgentSpec {
        if (skills == null) {
            skills = List.of();
        }
        if (replicas == null) {
            replicas = 1;
        }
        if (systemPrompt == null) {
            systemPrompt = "You are a helpful AI assistant.";
        }
    }
}
