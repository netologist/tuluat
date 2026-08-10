package com.tuluat.engine.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tuluat.engine.skill.SkillResult;
import java.util.List;

/**
 * Record representing the complete answer from an AI Agent execution including
 * usage statistics and cost.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentResponse(@JsonProperty("agentName") String agentName, @JsonProperty("model") String model,
		@JsonProperty("systemPrompt") String systemPrompt, @JsonProperty("answer") String answer,
		@JsonProperty("executedSkills") List<SkillResult> executedSkills, @JsonProperty("usage") UsageStats usage,
		@JsonProperty("timestamp") String timestamp, @JsonProperty("guardrailStatus") String guardrailStatus) {
	public static AgentResponse create(String agentName, String model, String systemPrompt, String answer,
			List<SkillResult> skills, UsageStats usage) {
		return new AgentResponse(agentName, model, systemPrompt, answer, skills, usage,
				java.time.Instant.now().toString(), null);
	}

	public static AgentResponse blocked(String agentName, String filterName, String reason) {
		return new AgentResponse(agentName, "blocked", "N/A",
				"Request blocked by guardrail [" + filterName + "]: " + reason, List.of(),
				UsageStats.calculate(0, 0, "blocked", 0), java.time.Instant.now().toString(), "BLOCKED");
	}

	public boolean isBlocked() {
		return "BLOCKED".equals(guardrailStatus);
	}
}
