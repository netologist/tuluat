package com.tuluat.engine.embabel;

import com.tuluat.engine.agent.UsageStats;

/**
 * Domain object representing the result of a goal execution.
 * <p>
 * Carries the agent's answer and usage statistics back to the caller through
 * the Embabel data flow graph.
 */
public record GoalResult(String agentName, String answer, UsageStats usage) {
}
