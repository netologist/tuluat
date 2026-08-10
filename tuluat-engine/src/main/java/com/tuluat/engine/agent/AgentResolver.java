package com.tuluat.engine.agent;

import com.tuluat.crd.agent.AiAgent;

import java.util.Optional;

/**
 * Resolves an {@link AiAgent} by reference for workflow-path guardrail
 * enforcement (ADR 004 / 007). Implementations typically look up the agent via
 * the Kubernetes API; {@code AgentExecutionService.executeAgent} uses it to
 * apply the agent's guardrails policy to workflow node prompts.
 */
public interface AgentResolver {

	Optional<AiAgent> resolve(String agentName, String namespace);
}
