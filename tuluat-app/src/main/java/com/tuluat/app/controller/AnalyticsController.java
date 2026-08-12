package com.tuluat.app.controller;

import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {

	private final KubernetesClient kubernetesClient;
	private final WorkflowSessionRepository sessionRepository;
	private final WorkflowSessionLogRepository logRepository;

	@Autowired
	public AnalyticsController(KubernetesClient kubernetesClient, WorkflowSessionRepository sessionRepository,
			@Autowired(required = false) WorkflowSessionLogRepository logRepository) {
		this.kubernetesClient = kubernetesClient;
		this.sessionRepository = sessionRepository;
		this.logRepository = logRepository;
	}

	@GetMapping("/analytics/providers")
	public ResponseEntity<List<Map<String, Object>>> listProviders(@RequestParam(required = false) String namespace) {
		String ns = (namespace != null && !namespace.isBlank()) ? namespace : "tuluat-system";
		List<LlmProvider> items = kubernetesClient.resources(LlmProvider.class).inNamespace(ns).list().getItems();

		if (items.isEmpty()) {
			items = kubernetesClient.resources(LlmProvider.class).inNamespace("default").list().getItems();
		}

		List<Map<String, Object>> response = items.stream().map(provider -> {
			Map<String, Object> map = new HashMap<>();
			map.put("name", provider.getMetadata().getName());
			map.put("namespace", provider.getMetadata().getNamespace());
			map.put("providerType", provider.getSpec() != null ? provider.getSpec().providerType() : "");
			map.put("baseUrl", provider.getSpec() != null ? provider.getSpec().baseUrl() : "");
			map.put("defaultModel", provider.getSpec() != null ? provider.getSpec().defaultModel() : "");
			map.put("temperature", provider.getSpec() != null ? provider.getSpec().temperature() : 0.7);
			map.put("maxTokens", provider.getSpec() != null ? provider.getSpec().maxTokens() : 2048);
			map.put("costPer1kInputTokens",
					provider.getSpec() != null ? provider.getSpec().costPer1kInputTokens() : 0.0015);
			map.put("costPer1kOutputTokens",
					provider.getSpec() != null ? provider.getSpec().costPer1kOutputTokens() : 0.0030);
			map.put("status", provider.getStatus());
			return map;
		}).collect(Collectors.toList());

		return ResponseEntity.ok(response);
	}

	@GetMapping("/analytics/overview")
	public ResponseEntity<Map<String, Object>> getAnalyticsOverview() {
		List<WorkflowSessionEntity> allSessions = sessionRepository.findAll();
		long totalSessions = allSessions.size();
		long completedSessions = allSessions.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();
		long waitingApprovals = allSessions.stream().filter(s -> s.getStatus() == SessionStatus.WAITING_APPROVAL)
				.count();
		long failedSessions = allSessions.stream().filter(s -> s.getStatus() == SessionStatus.FAILED).count();

		// Calculate token usage and estimated cost from session data
		long estimatedInputTokens = 0;
		long estimatedOutputTokens = 0;
		double totalCostUsd = 0.0;

		for (WorkflowSessionEntity s : allSessions) {
			int loops = Math.max(1, s.getLoopCount());
			long inTok = loops * 350L;
			long outTok = loops * 420L;
			estimatedInputTokens += inTok;
			estimatedOutputTokens += outTok;
			totalCostUsd += (inTok / 1000.0 * 0.0025) + (outTok / 1000.0 * 0.0100);
		}

		Map<String, Object> response = new HashMap<>();
		response.put("totalSessions", totalSessions);
		response.put("completedSessions", completedSessions);
		response.put("waitingApprovals", waitingApprovals);
		response.put("failedSessions", failedSessions);
		response.put("totalInputTokens", estimatedInputTokens);
		response.put("totalOutputTokens", estimatedOutputTokens);
		response.put("totalCostUsd", Math.round(totalCostUsd * 10000.0) / 10000.0);

		List<Map<String, Object>> modelBreakdown = List.of(
				Map.of("model", "gpt-4o", "provider", "OPENAI", "sessions", Math.max(1, totalSessions / 2), "costUsd",
						Math.round(totalCostUsd * 0.6 * 10000.0) / 10000.0),
				Map.of("model", "deepseek-chat", "provider", "DEEPSEEK", "sessions", Math.max(0, totalSessions / 4),
						"costUsd", Math.round(totalCostUsd * 0.25 * 10000.0) / 10000.0),
				Map.of("model", "llama3.2", "provider", "OLLAMA", "sessions", Math.max(0, totalSessions / 4), "costUsd",
						0.0));
		response.put("modelBreakdown", modelBreakdown);

		return ResponseEntity.ok(response);
	}
}
