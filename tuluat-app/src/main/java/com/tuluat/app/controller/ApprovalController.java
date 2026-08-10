package com.tuluat.app.controller;

import com.tuluat.app.websocket.WorkflowEventPublisher;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import com.tuluat.engine.temporal.ApprovalSignal;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

	private final WorkflowSessionRepository sessionRepository;
	private final WorkflowExecutionService executionService;
	private final WorkflowEventPublisher eventPublisher;
	private final io.fabric8.kubernetes.client.KubernetesClient kubernetesClient;

	public ApprovalController(WorkflowSessionRepository sessionRepository, WorkflowExecutionService executionService) {
		this(sessionRepository, executionService, null, null);
	}

	public ApprovalController(WorkflowSessionRepository sessionRepository, WorkflowExecutionService executionService,
			WorkflowEventPublisher eventPublisher) {
		this(sessionRepository, executionService, eventPublisher, null);
	}
	@Autowired
	public ApprovalController(WorkflowSessionRepository sessionRepository, WorkflowExecutionService executionService,
			@Autowired(required = false) WorkflowEventPublisher eventPublisher,
			@Autowired(required = false) io.fabric8.kubernetes.client.KubernetesClient kubernetesClient) {
		this.sessionRepository = sessionRepository;
		this.executionService = executionService;
		this.eventPublisher = eventPublisher;
		this.kubernetesClient = kubernetesClient;
	}

	@GetMapping
	public ResponseEntity<List<Map<String, Object>>> getPendingApprovals() {
		List<WorkflowSessionEntity> pendingSessions = sessionRepository.findAll().stream()
				.filter(s -> "WAITING_APPROVAL".equalsIgnoreCase(s.getStatus())).collect(Collectors.toList());

		List<Map<String, Object>> response = pendingSessions.stream().map(session -> {
			Map<String, Object> map = new HashMap<>();
			map.put("sessionId", session.getSessionId());
			map.put("workflowName", session.getWorkflowName());
			map.put("currentNode", session.getCurrentNodeId());
			map.put("contextData", session.getContextData());
			map.put("phase", session.getStatus());
			map.put("startTime", session.getCreatedAt() != null ? session.getCreatedAt().toString() : "");
			return map;
		}).collect(Collectors.toList());

		return ResponseEntity.ok(response);
	}

	@GetMapping("/{sessionId}")
	public ResponseEntity<Map<String, Object>> getApprovalDetail(@PathVariable UUID sessionId) {
		return sessionRepository.findById(sessionId).filter(s -> "WAITING_APPROVAL".equalsIgnoreCase(s.getStatus()))
				.map(session -> {
					Map<String, Object> map = new HashMap<>();
					map.put("sessionId", session.getSessionId());
					map.put("workflowName", session.getWorkflowName());
					map.put("currentNode", session.getCurrentNodeId());
					map.put("contextData", session.getContextData());
					map.put("phase", session.getStatus());
					map.put("startTime", session.getCreatedAt() != null ? session.getCreatedAt().toString() : "");
					return ResponseEntity.ok(map);
				}).orElse(ResponseEntity.notFound().build());
	}

	@PostMapping("/{sessionId}/action")
	public ResponseEntity<Map<String, Object>> handleApprovalAction(@PathVariable UUID sessionId,
			@RequestBody Map<String, Object> request) {
		boolean approved = Boolean.TRUE.equals(request.get("approved"))
				|| "APPROVE".equalsIgnoreCase(String.valueOf(request.get("action")));
		String feedback = request.get("feedback") != null ? String.valueOf(request.get("feedback")) : "";

		@SuppressWarnings("unchecked")
		Map<String, Object> metadata = request.get("metadata") instanceof Map
				? (Map<String, Object>) request.get("metadata")
				: Collections.emptyMap();

		ApprovalSignal signal = new ApprovalSignal(approved, feedback, metadata);
		executionService.sendApprovalSignal(sessionId.toString(), signal);

		// Resume state machine loop if kubernetesClient & spec can be resolved
		Optional<WorkflowSessionEntity> opt = sessionRepository.findById(sessionId);
		if (opt.isPresent() && kubernetesClient != null) {
			WorkflowSessionEntity session = opt.get();
			try {
				com.tuluat.crd.workflow.AiWorkflow wf = kubernetesClient
						.resources(com.tuluat.crd.workflow.AiWorkflow.class).inNamespace("tuluat-system")
						.withName(session.getWorkflowName()).get();
				if (wf == null) {
					wf = kubernetesClient.resources(com.tuluat.crd.workflow.AiWorkflow.class).inNamespace("default")
							.withName(session.getWorkflowName()).get();
				}
				if (wf != null && wf.getSpec() != null) {
					executionService.processApprovalSignal(sessionId, wf.getSpec(), approved, feedback, 10);
				}
			} catch (Exception ignored) {
			}
		}

		if (eventPublisher != null) {
			eventPublisher.publishApprovalResolved(sessionId, approved, feedback);
		}

		Map<String, Object> response = new HashMap<>();
		response.put("status", "SUCCESS");
		response.put("sessionId", sessionId.toString());
		response.put("approved", approved);
		response.put("feedback", feedback);
		return ResponseEntity.ok(response);
	}
}
