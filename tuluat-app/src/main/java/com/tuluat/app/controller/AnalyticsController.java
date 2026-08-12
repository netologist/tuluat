package com.tuluat.app.controller;

import com.tuluat.app.config.KubernetesResourceResolver;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.engine.entity.NodeExecutionEntity;
import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.engine.repository.NodeExecutionRepository;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {

	private final KubernetesResourceResolver resolver;
	private final WorkflowSessionRepository sessionRepository;
	private final NodeExecutionRepository nodeExecutionRepository;

	public AnalyticsController(KubernetesResourceResolver resolver, WorkflowSessionRepository sessionRepository,
			NodeExecutionRepository nodeExecutionRepository) {
		this.resolver = resolver;
		this.sessionRepository = sessionRepository;
		this.nodeExecutionRepository = nodeExecutionRepository;
	}

	@GetMapping("/analytics/providers")
	public ResponseEntity<List<Map<String, Object>>> listProviders(@RequestParam(required = false) String namespace) {
		List<Map<String, Object>> response = resolver.list(LlmProvider.class, namespace).stream().map(provider -> {
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
		List<NodeExecutionEntity> executions = nodeExecutionRepository.findAll();

		long totalInputTokens = executions.stream().mapToLong(NodeExecutionEntity::getInputTokens).sum();
		long totalOutputTokens = executions.stream().mapToLong(NodeExecutionEntity::getOutputTokens).sum();
		BigDecimal totalCostUsd = executions.stream().map(NodeExecutionEntity::getCostUsd).filter(c -> c != null)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		Map<String, ModelAggregate> byModel = new HashMap<>();
		for (NodeExecutionEntity e : executions) {
			String model = e.getModel() != null ? e.getModel() : "unknown";
			byModel.computeIfAbsent(model, k -> new ModelAggregate()).add(e);
		}

		List<Map<String, Object>> modelBreakdown = byModel.entrySet().stream().map(entry -> {
			ModelAggregate agg = entry.getValue();
			return Map.<String, Object>of("model", entry.getKey(), "sessions", agg.sessions(), "costUsd",
					round(agg.costUsd));
		}).collect(Collectors.toList());

		Map<String, Object> response = new HashMap<>();
		response.put("totalSessions", sessionRepository.count());
		response.put("completedSessions", sessionRepository.countByStatus(SessionStatus.COMPLETED));
		response.put("waitingApprovals", sessionRepository.countByStatus(SessionStatus.WAITING_APPROVAL));
		response.put("failedSessions", sessionRepository.countByStatus(SessionStatus.FAILED));
		response.put("totalInputTokens", totalInputTokens);
		response.put("totalOutputTokens", totalOutputTokens);
		response.put("totalCostUsd", round(totalCostUsd));
		response.put("modelBreakdown", modelBreakdown);

		return ResponseEntity.ok(response);
	}

	private static double round(BigDecimal value) {
		return value.setScale(4, RoundingMode.HALF_UP).doubleValue();
	}

	private static final class ModelAggregate {
		private final Set<UUID> sessionIds = new HashSet<>();
		private BigDecimal costUsd = BigDecimal.ZERO;

		void add(NodeExecutionEntity e) {
			if (e.getSessionId() != null) {
				sessionIds.add(e.getSessionId());
			}
			if (e.getCostUsd() != null) {
				costUsd = costUsd.add(e.getCostUsd());
			}
		}

		long sessions() {
			return sessionIds.size();
		}
	}
}
