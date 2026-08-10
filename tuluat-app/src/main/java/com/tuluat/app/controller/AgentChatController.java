package com.tuluat.app.controller;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.agent.UsageStats;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Spring Web Controller serving HTTP endpoints exposed via Kubernetes Ingress.
 */
@RestController
@RequestMapping("/api/v1")
public class AgentChatController {
	private static final Logger log = LoggerFactory.getLogger(AgentChatController.class);

	private final KubernetesClient client;
	private final AgentExecutionService agentExecutionService;

	@Autowired
	public AgentChatController(KubernetesClient client, AgentExecutionService agentExecutionService) {
		this.client = client;
		this.agentExecutionService = agentExecutionService;
	}

	/**
	 * Public chat endpoint invoked via Ingress: POST
	 * /api/v1/agents/{agentName}/chat
	 */
	@PostMapping("/agents/{agentName}/chat")
	public ResponseEntity<AgentResponse> chatWithAgent(@PathVariable("agentName") String agentName,
			@RequestBody(required = false) ChatRequest request) {

		String ns = (request != null && request.namespace() != null) ? request.namespace() : "default";
		String prompt = (request != null) ? request.prompt() : null;

		log.info("Received HTTP chat request for agent '{}/{}'", ns, agentName);

		// Fetch AiAgent Custom Resource from Kubernetes
		AiAgent agent = client.resources(AiAgent.class).inNamespace(ns).withName(agentName).get();
		if (agent == null) {
			log.error("AiAgent CR '{}/{}' not found", ns, agentName);
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(AgentResponse.create(agentName, "unknown", "N/A",
							"Error: AiAgent '" + agentName + "' not found", List.of(),
							UsageStats.calculate(0, 0, "unknown", 0)));
		}

		// Fetch referenced LlmProvider
		LlmProvider provider = null;
		if (agent.getSpec().providerRef() != null && agent.getSpec().providerRef().name() != null) {
			String pName = agent.getSpec().providerRef().name();
			String pNs = (agent.getSpec().providerRef().namespace() != null)
					? agent.getSpec().providerRef().namespace()
					: ns;
			provider = client.resources(LlmProvider.class).inNamespace(pNs).withName(pName).get();
		}

		// Process prompt via Spring AI & Skill execution engine on Virtual Threads
		AgentResponse response = agentExecutionService.processAgentPrompt(agent, provider, prompt);
		return ResponseEntity.ok(response);
	}

	/**
	 * Endpoint returning AiAgent details & status: GET /api/v1/agents/{agentName}
	 */
	@GetMapping("/agents/{agentName}")
	public ResponseEntity<?> getAgentDetails(@PathVariable("agentName") String agentName,
			@RequestParam(value = "namespace", defaultValue = "default") String ns) {

		AiAgent agent = client.resources(AiAgent.class).inNamespace(ns).withName(agentName).get();
		if (agent == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "AiAgent not found"));
		}
		return ResponseEntity.ok(agent);
	}
}
