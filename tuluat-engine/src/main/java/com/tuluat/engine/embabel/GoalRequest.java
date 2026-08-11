package com.tuluat.engine.embabel;

import java.util.Map;

/**
 * Domain object representing a goal execution request for a Tuluat AI agent.
 * <p>
 * This is a typed input for the Embabel Goal-Oriented Action Planning (GOAP)
 * engine. The framework uses the type to infer data flow between actions.
 */
public record GoalRequest(String agentName, String goalDescription, Map<String, Object> context) {
}
