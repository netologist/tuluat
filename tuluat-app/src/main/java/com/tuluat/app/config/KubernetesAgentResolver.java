package com.tuluat.app.config;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.engine.agent.AgentResolver;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves {@link AiAgent} resources via the Kubernetes API for workflow-path
 * guardrail enforcement.
 */
@Component
public class KubernetesAgentResolver implements AgentResolver {

	private final KubernetesClient client;

	public KubernetesAgentResolver(KubernetesClient client) {
		this.client = client;
	}

	@Override
	public Optional<AiAgent> resolve(String agentName, String namespace) {
		if (agentName == null || agentName.isBlank()) {
			return Optional.empty();
		}
		String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
		AiAgent agent = client.resources(AiAgent.class).inNamespace(ns).withName(agentName).get();
		return Optional.ofNullable(agent);
	}
}
