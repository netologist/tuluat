package com.tuluat.app.controller;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/agent-specs")
public class AiAgentController {

	private final KubernetesClient kubernetesClient;
	private final WorkflowSessionLogRepository logRepository;

	@Autowired
	public AiAgentController(KubernetesClient kubernetesClient,
			@Autowired(required = false) WorkflowSessionLogRepository logRepository) {
		this.kubernetesClient = kubernetesClient;
		this.logRepository = logRepository;
	}

	@GetMapping
	public ResponseEntity<List<Map<String, Object>>> listAgents(@RequestParam(required = false) String namespace) {
		String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
		List<AiAgent> items = kubernetesClient.resources(AiAgent.class).inNamespace(ns).list().getItems();

		if (items.isEmpty()) {
			items = kubernetesClient.resources(AiAgent.class).inNamespace("default").list().getItems();
		}

		List<Map<String, Object>> response = items.stream().map(agent -> {
			Map<String, Object> map = new HashMap<>();
			map.put("name", agent.getMetadata().getName());
			map.put("namespace", agent.getMetadata().getNamespace());
			map.put("providerRef", agent.getSpec() != null ? agent.getSpec().providerRef() : null);
			map.put("model", agent.getSpec() != null ? agent.getSpec().model() : "");
			map.put("systemPrompt", agent.getSpec() != null ? agent.getSpec().systemPrompt() : "");
			map.put("tools", agent.getSpec() != null ? agent.getSpec().tools() : List.of());
			map.put("guardrails", agent.getSpec() != null ? agent.getSpec().guardrails() : null);
			map.put("status", agent.getStatus());
			return map;
		}).collect(Collectors.toList());

		return ResponseEntity.ok(response);
	}

	@GetMapping("/{name}")
	public ResponseEntity<Map<String, Object>> getAgent(@PathVariable String name,
			@RequestParam(required = false) String namespace) {
		String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
		AiAgent agent = kubernetesClient.resources(AiAgent.class).inNamespace(ns).withName(name).get();

		if (agent == null) {
			agent = kubernetesClient.resources(AiAgent.class).inNamespace("default").withName(name).get();
		}

		if (agent == null) {
			return ResponseEntity.notFound().build();
		}

		Map<String, Object> map = new HashMap<>();
		map.put("name", agent.getMetadata().getName());
		map.put("namespace", agent.getMetadata().getNamespace());
		map.put("spec", agent.getSpec());
		map.put("status", agent.getStatus());
		return ResponseEntity.ok(map);
	}

	@GetMapping("/{name}/logs")
	public ResponseEntity<List<Map<String, Object>>> getAgentLogs(@PathVariable String name) {
		if (logRepository == null) {
			return ResponseEntity.ok(List.of());
		}

		List<WorkflowSessionLogEntity> allLogs = logRepository.findAll();
		List<Map<String, Object>> agentLogs = allLogs.stream()
				.filter(l -> l.getMessage() != null && l.getMessage().contains(name)).map(l -> {
					Map<String, Object> m = new HashMap<>();
					m.put("id", l.getId());
					m.put("sessionId", l.getSessionId());
					m.put("nodeId", l.getNodeId());
					m.put("logLevel", l.getLogLevel());
					m.put("message", l.getMessage());
					m.put("createdAt", l.getCreatedAt());

					// Structured request/response extraction if present in log message
					String msg = l.getMessage();
					if (msg.contains("Executing Agent")) {
						m.put("type", "REQUEST");
						m.put("prompt", msg.substring(msg.indexOf("prompt:") + 7).trim());
					} else if (msg.contains("output saved")) {
						m.put("type", "RESPONSE");
						m.put("outputKey", msg.substring(msg.indexOf("key '") + 5, msg.lastIndexOf("'")).trim());
					} else {
						m.put("type", "EVENT");
					}
					return m;
				}).collect(Collectors.toList());

		return ResponseEntity.ok(agentLogs);
	}
}
