package com.tuluat.engine.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import com.tuluat.engine.temporal.ApprovalSignal;
import com.tuluat.engine.telemetry.WorkflowTelemetryService;
import io.temporal.client.WorkflowClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Slf4j
@Service
public class WorkflowExecutionService {

	private final WorkflowSessionRepository sessionRepository;
	private final GraphStateMachineEngine engine;
	private final ObjectMapper objectMapper;
	private final Optional<WorkflowTelemetryService> telemetryService;
	private final Optional<WorkflowClient> workflowClient;

	public WorkflowExecutionService(WorkflowSessionRepository sessionRepository, GraphStateMachineEngine engine,
			ObjectMapper objectMapper, Optional<WorkflowTelemetryService> telemetryService,
			Optional<WorkflowClient> workflowClient) {
		this.sessionRepository = sessionRepository;
		this.engine = engine;
		this.objectMapper = objectMapper;
		this.telemetryService = telemetryService;
		this.workflowClient = workflowClient;
	}

	public WorkflowSessionEntity startSession(String workflowName, AiWorkflowSpec spec, String input, int maxLoops) {
		WorkflowSessionEntity session = new WorkflowSessionEntity();
		session.setWorkflowName(workflowName);
		session.setStatus(SessionStatus.RUNNING);
		session.setCurrentNodeId(spec.initialNode());
		session.setContextData(toJson(Map.of("input", input)));

		session = sessionRepository.save(session);

		telemetryService.ifPresent(ts -> ts.recordSessionCreated(workflowName));

		while (session.getStatus() == SessionStatus.RUNNING) {
			session = engine.executeNextStep(spec, session, maxLoops);
			session = sessionRepository.save(session);
		}

		return session;
	}

	public WorkflowSessionEntity processApprovalSignal(UUID sessionId, AiWorkflowSpec spec, boolean approved,
			String feedback, int maxLoops) {
		WorkflowSessionEntity session = sessionRepository.findById(sessionId)
				.orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

		String statusVal = approved ? "APPROVED" : "REJECTED";
		String escapedFeedback = feedback != null ? feedback : "";

		Map<String, Object> context = parseContext(session.getContextData());
		context.put("approvalStatus", statusVal);
		context.put("approvalFeedback", escapedFeedback);
		session.setContextData(toJson(context));
		session.setStatus(SessionStatus.RUNNING);
		session = sessionRepository.save(session);

		while (session.getStatus() == SessionStatus.RUNNING) {
			session = engine.executeNextStep(spec, session, maxLoops);
			session = sessionRepository.save(session);
		}

		return session;
	}

	public void sendApprovalSignal(String sessionId, ApprovalSignal signal) {
		try {
			UUID id = UUID.fromString(sessionId);
			sessionRepository.findById(id).ifPresent(s -> {
				String statusVal = signal.approved() ? "APPROVED" : "REJECTED";
				Map<String, Object> ctx = parseContext(s.getContextData());
				ctx.put("approvalStatus", statusVal);
				ctx.put("approvalFeedback", signal.feedback() != null ? signal.feedback() : "");
				s.setContextData(toJson(ctx));
				s.setStatus(signal.approved() ? SessionStatus.RUNNING : SessionStatus.REJECTED);
				sessionRepository.save(s);
			});
		} catch (Exception e) {
			log.warn("Failed to send approval signal for session {}: {}", sessionId, e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseContext(String contextData) {
		try {
			return contextData != null ? objectMapper.readValue(contextData, Map.class) : new java.util.HashMap<>();
		} catch (JsonProcessingException e) {
			log.error("Failed to parse context data", e);
			return new java.util.HashMap<>();
		}
	}

	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize context data", e);
			return "{}";
		}
	}
}
