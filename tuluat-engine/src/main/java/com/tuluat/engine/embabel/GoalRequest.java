package com.tuluat.engine.embabel;

import java.util.Map;
import java.util.UUID;

/**
 * Domain object representing a goal execution request for a Tuluat AI agent.
 * <p>
 * This is a typed input for the Embabel Goal-Oriented Action Planning (GOAP)
 * engine. The framework uses the type to infer data flow between actions.
 *
 * @param agentName
 *            the AI agent to execute
 * @param goalDescription
 *            human-readable goal description (becomes the agent prompt)
 * @param context
 *            optional context data passed through the Embabel data flow graph
 * @param sessionId
 *            optional session ID for multi-turn conversation memory (ADR 013)
 */
public record GoalRequest(String agentName, String goalDescription, Map<String, Object> context, UUID sessionId) {

	public GoalRequest(String agentName, String goalDescription, Map<String, Object> context) {
		this(agentName, goalDescription, context, null);
	}
}