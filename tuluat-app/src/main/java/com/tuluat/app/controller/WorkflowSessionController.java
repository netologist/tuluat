package com.tuluat.app.controller;

import com.tuluat.app.config.KubernetesResourceResolver;
import com.tuluat.app.websocket.WorkflowEventPublisher;
import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.engine.entity.NodeExecutionEntity;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.repository.NodeExecutionRepository;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import com.tuluat.engine.temporal.ApprovalSignal;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1")
public class WorkflowSessionController {

	private final WorkflowExecutionService executionService;
	private final WorkflowSessionRepository sessionRepository;
	private final NodeExecutionRepository nodeExecutionRepository;
	private final KubernetesResourceResolver resolver;
	private final WorkflowSessionLogRepository logRepository;
	private final WorkflowEventPublisher eventPublisher;

	@Autowired
	public WorkflowSessionController(WorkflowExecutionService executionService,
			WorkflowSessionRepository sessionRepository, NodeExecutionRepository nodeExecutionRepository,
			KubernetesResourceResolver resolver,
			@Autowired(required = false) WorkflowSessionLogRepository logRepository,
			@Autowired(required = false) WorkflowEventPublisher eventPublisher) {
		this.executionService = executionService;
		this.sessionRepository = sessionRepository;
		this.nodeExecutionRepository = nodeExecutionRepository;
		this.resolver = resolver;
		this.logRepository = logRepository;
		this.eventPublisher = eventPublisher;
	}

	@PostMapping("/workflows/{workflowName}/sessions")
	public ResponseEntity<WorkflowSessionEntity> createSession(@PathVariable String workflowName,
			@RequestParam(required = false) String namespace, @RequestBody Map<String, Object> request) {
		String input = (String) request.getOrDefault("input", "");
		int maxLoops = (int) request.getOrDefault("maxLoops", 10);

		AiWorkflow workflow = resolver.get(AiWorkflow.class, namespace, workflowName);
		if (workflow == null) {
			return ResponseEntity.notFound().build();
		}

		WorkflowSessionEntity session = executionService.startSession(workflowName, workflow.getSpec(), input,
				maxLoops);
		if (eventPublisher != null) {
			eventPublisher.publishSessionState(session.getSessionId(), workflowName, session.getStatus().name(),
					session.getCurrentNodeId(), session.getContextData());
		}
		return ResponseEntity.ok(session);
	}

	@GetMapping("/sessions")
	public ResponseEntity<List<Map<String, Object>>> getSessions(@RequestParam(required = false) String workflowName) {
		List<WorkflowSessionEntity> sessions;
		if (workflowName != null && !workflowName.isBlank()) {
			sessions = sessionRepository.findByWorkflowNameOrderByCreatedAtDesc(workflowName);
		} else {
			sessions = sessionRepository.findAllByOrderByCreatedAtDesc();
		}

		Map<UUID, SessionTotals> totalsBySession = aggregateSessionTotals();

		List<Map<String, Object>> response = sessions.stream().map(s -> {
			Map<String, Object> map = new HashMap<>();
			map.put("sessionId", s.getSessionId());
			map.put("workflowName", s.getWorkflowName());
			map.put("status", s.getStatus().name());
			map.put("currentNodeId", s.getCurrentNodeId());
			map.put("loopCount", s.getLoopCount());
			map.put("contextData", s.getContextData());
			map.put("createdAt", s.getCreatedAt());
			map.put("updatedAt", s.getUpdatedAt());

			long durationMs = 0;
			if (s.getCreatedAt() != null && s.getUpdatedAt() != null) {
				durationMs = java.time.Duration.between(s.getCreatedAt(), s.getUpdatedAt()).toMillis();
			}
			map.put("totalDurationMs", durationMs);

			SessionTotals totals = totalsBySession.getOrDefault(s.getSessionId(), SessionTotals.EMPTY);
			map.put("totalTokens", totals.totalTokens);
			map.put("totalCostUsd", totals.costUsd.doubleValue());
			map.put("stepCount", totals.stepCount);
			return map;
		}).collect(Collectors.toList());

		return ResponseEntity.ok(response);
	}

	@GetMapping("/sessions/{sessionId}")
	public ResponseEntity<WorkflowSessionEntity> getSession(@PathVariable UUID sessionId) {
		return sessionRepository.findById(sessionId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/sessions/{sessionId}/logs")
	public ResponseEntity<List<WorkflowSessionLogEntity>> getSessionLogs(@PathVariable UUID sessionId) {
		if (logRepository == null) {
			return ResponseEntity.ok(List.of());
		}
		return ResponseEntity.ok(logRepository.findBySessionIdOrderByCreatedAtAsc(sessionId));
	}

	@GetMapping("/sessions/{sessionId}/execution-tree")
	public ResponseEntity<List<Map<String, Object>>> getSessionExecutionTree(@PathVariable UUID sessionId) {
		Optional<WorkflowSessionEntity> opt = sessionRepository.findById(sessionId);
		if (opt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		WorkflowSessionEntity session = opt.get();
		List<WorkflowSessionLogEntity> logs = logRepository != null
				? logRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
				: List.of();

		Map<String, NodeExecutionEntity> executionsByNode = nodeExecutionRepository
				.findBySessionIdOrderByStartTimeAsc(sessionId).stream()
				.collect(Collectors.toMap(NodeExecutionEntity::getNodeId, e -> e, (a, b) -> a));

		List<Map<String, Object>> steps = new ArrayList<>();
		int stepIndex = 1;

		for (WorkflowSessionLogEntity logEntry : logs) {
			String msg = logEntry.getMessage();
			if (msg == null)
				continue;

			if (msg.contains("Executing node")) {
				Map<String, Object> step = new HashMap<>();
				step.put("stepNumber", stepIndex++);
				step.put("nodeId", logEntry.getNodeId());
				step.put("timestamp", logEntry.getCreatedAt() != null ? logEntry.getCreatedAt().toString() : "");

				if (msg.contains("type: AGENT")) {
					step.put("nodeType", "AGENT");
					enrichAgentStep(step, logEntry.getNodeId(), executionsByNode);
				} else if (msg.contains("type: CONDITION")) {
					step.put("nodeType", "CONDITION");
				} else if (msg.contains("type: HUMAN_APPROVAL")) {
					step.put("nodeType", "HUMAN_APPROVAL");
				} else {
					step.put("nodeType", "TOOL");
				}
				step.put("status", "COMPLETED");
				steps.add(step);
			} else if (msg.contains("Executing Agent") && !steps.isEmpty()) {
				Map<String, Object> lastStep = steps.get(steps.size() - 1);
				String prompt = msg.contains("prompt:") ? msg.substring(msg.indexOf("prompt:") + 7).trim() : msg;
				lastStep.put("requestPayload", Map.of("prompt", prompt));
			} else if (msg.contains("output saved to key") && !steps.isEmpty()) {
				Map<String, Object> lastStep = steps.get(steps.size() - 1);
				String output = msg;
				if (msg.contains(": ")) {
					output = msg.substring(msg.indexOf(": ") + 2).trim();
				}
				lastStep.put("responsePayload", Map.of("output", output));
			} else if (msg.contains("Condition expression") && !steps.isEmpty()) {
				Map<String, Object> lastStep = steps.get(steps.size() - 1);
				lastStep.put("evaluationResult", msg.contains("evaluated to: true"));
				String expr = "";
				if (msg.contains("expression '")) {
					int start = msg.indexOf("expression '") + 12;
					int end = msg.indexOf("' evaluated");
					if (end > start) {
						expr = msg.substring(start, end);
					}
				}
				lastStep.put("expression", expr);

				String contextStr = session.getContextData() != null ? session.getContextData() : "{}";
				if (msg.contains("with context: ")) {
					contextStr = msg.substring(msg.indexOf("with context: ") + 14).trim();
				}
				lastStep.put("evaluatedValues", Map.of("context", contextStr));
			} else if (msg.contains("awaiting human approval") && !steps.isEmpty()) {
				Map<String, Object> lastStep = steps.get(steps.size() - 1);
				lastStep.put("status", "WAITING_APPROVAL");
				lastStep.put("requestPayload", Map.of("prompt", "Human Fraud Officer approval required to proceed"));
			} else if (msg.contains("Processing approval decision:") && !steps.isEmpty()) {
				Map<String, Object> lastStep = steps.get(steps.size() - 1);
				lastStep.put("status", "COMPLETED");
				lastStep.put("reviewerFeedback", msg);
			}
		}

		return ResponseEntity.ok(steps);
	}

	private void enrichAgentStep(Map<String, Object> step, String nodeId,
			Map<String, NodeExecutionEntity> executionsByNode) {
		NodeExecutionEntity execution = executionsByNode.get(nodeId);
		if (execution == null) {
			return;
		}
		step.put("agent", execution.getAgentName());
		step.put("provider", execution.getProvider());
		step.put("model", execution.getModel());
		step.put("metrics",
				Map.of("durationMs", execution.getDurationMs(), "inputTokens", execution.getInputTokens(),
						"outputTokens", execution.getOutputTokens(), "costUsd",
						execution.getCostUsd() != null ? execution.getCostUsd().doubleValue() : 0.0));
	}

	private Map<UUID, SessionTotals> aggregateSessionTotals() {
		Map<UUID, SessionTotals> result = new HashMap<>();
		for (NodeExecutionEntity e : nodeExecutionRepository.findAll()) {
			if (e.getSessionId() == null)
				continue;
			SessionTotals totals = result.computeIfAbsent(e.getSessionId(), k -> new SessionTotals());
			totals.totalTokens += e.getTotalTokens();
			totals.costUsd = totals.costUsd.add(e.getCostUsd() != null ? e.getCostUsd() : BigDecimal.ZERO);
			totals.stepCount++;
		}
		return result;
	}

	@PostMapping("/sessions/{sessionId}/approve")
	public ResponseEntity<Map<String, Object>> approveSessionStep(@PathVariable UUID sessionId,
			@RequestBody Map<String, Object> request) {
		boolean approved = Boolean.parseBoolean(String.valueOf(request.getOrDefault("approved", true)));
		String feedback = (String) request.get("feedback");
		@SuppressWarnings("unchecked")
		Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");

		ApprovalSignal signal = new ApprovalSignal(approved, feedback, metadata);
		executionService.sendApprovalSignal(sessionId.toString(), signal);
		if (eventPublisher != null) {
			eventPublisher.publishApprovalResolved(sessionId, approved, feedback);
		}

		return ResponseEntity.ok(Map.of("sessionId", sessionId.toString(), "status", "SIGNAL_SENT", "approved",
				signal.approved(), "feedback", signal.feedback() != null ? signal.feedback() : ""));
	}

	private static final class SessionTotals {
		static final SessionTotals EMPTY = new SessionTotals();
		long totalTokens;
		long stepCount;
		BigDecimal costUsd = BigDecimal.ZERO;
	}
}
