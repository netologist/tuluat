package com.example.ai.engine;

import com.example.ai.engine.skill.SkillResult;
import java.util.List;

/**
 * Record representing the complete answer from an AI Agent execution.
 */
public record AgentResponse(
    String agentName,
    String model,
    String systemPrompt,
    String answer,
    List<SkillResult> executedSkills,
    long latencyMs,
    String timestamp
) {
    public static AgentResponse create(String agentName, String model, String systemPrompt, String answer, List<SkillResult> skills, long latencyMs) {
        return new AgentResponse(agentName, model, systemPrompt, answer, skills, latencyMs, java.time.Instant.now().toString());
    }
}
