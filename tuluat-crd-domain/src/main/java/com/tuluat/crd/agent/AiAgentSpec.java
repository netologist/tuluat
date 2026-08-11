package com.tuluat.crd.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Spec record for AiAgent Custom Resource.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiAgentSpec(@JsonProperty("providerRef") ProviderRef providerRef, @JsonProperty("model") String model,
		@JsonProperty("systemPrompt") String systemPrompt, @JsonProperty("userPrompt") String userPrompt,
		@JsonProperty("skills") List<SkillDefinition> skills,
		@JsonProperty("skillSources") List<SkillSource> skillSources,
		@JsonProperty("tools") List<ToolDefinition> tools,
		@JsonProperty("toolSources") List<ToolSource> toolSources,
		@JsonProperty("mcpServers") List<McpServerRef> mcpServers,
		@JsonProperty("guardrails") GuardrailsConfig guardrails, @JsonProperty("a2a") A2aConfig a2a,
		@JsonProperty("ingress") IngressSpec ingress, @JsonProperty("replicas") Integer replicas) {
	public AiAgentSpec {
		if (skills == null) {
			skills = List.of();
		}
		if (skillSources == null) {
			skillSources = List.of();
		}
		if (tools == null) {
			tools = List.of();
		}
		if (toolSources == null) {
			toolSources = List.of();
		}
		if (mcpServers == null) {
			mcpServers = List.of();
		}
		if (replicas == null) {
			replicas = 1;
		}
		if (systemPrompt == null) {
			systemPrompt = "You are a helpful AI assistant.";
		}
	}
}
