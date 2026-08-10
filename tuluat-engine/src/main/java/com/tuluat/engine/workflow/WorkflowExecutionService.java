package com.tuluat.engine.workflow;

import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.engine.telemetry.WorkflowTelemetryService;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.repository.WorkflowSessionRepository;
import io.temporal.client.WorkflowClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WorkflowExecutionService {

	private final WorkflowSessionRepository sessionRepository;
	private final GraphStateMachineEngine engine;
	private final WorkflowTelemetryService telemetryService;
	private final WorkflowClient workflowClient;

	public WorkflowExecutionService(WorkflowSessionRepository sessionRepository, GraphStateMachineEngine engine) {
		this(sessionRepository, engine, null, null);
	}

	@Autowired
	public WorkflowExecutionService(WorkflowSessionRepository sessionRepository, GraphStateMachineEngine engine,
			@Autowired(required = false) WorkflowTelemetryService telemetryService,
			@Autowired(required = false) WorkflowClient workflowClient) {
		this.sessionRepository = sessionRepository;
		this.engine = engine;
		this.telemetryService = telemetryService;
		this.workflowClient = workflowClient;
	}

	@Transactional
	public WorkflowSessionEntity startSession(String workflowName, AiWorkflowSpec spec, String input, int maxLoops) {
		WorkflowSessionEntity session = new WorkflowSessionEntity();
		session.setSessionId(UUID.randomUUID());
		session.setWorkflowName(workflowName);
		session.setStatus("RUNNING");
		session.setCurrentNodeId(spec.getInitialNode());
		session.setContextData("{\"input\":\"" + input + "\"}");

		session = sessionRepository.save(session);

		if (telemetryService != null) {
			telemetryService.recordSessionCreated(workflowName);
		}

		while ("RUNNING".equalsIgnoreCase(session.getStatus())) {
			session = engine.executeNextStep(spec, session, maxLoops);
			session = sessionRepository.save(session);
		}

		return session;
	}

	@Transactional
	public WorkflowSessionEntity processApprovalSignal(UUID sessionId, AiWorkflowSpec spec, boolean approved,
			String feedback, int maxLoops) {
		WorkflowSessionEntity session = sessionRepository.findById(sessionId)
				.orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

		String contextJson = session.getContextData() != null ? session.getContextData() : "{}";
		String statusVal = approved ? "APPROVED" : "REJECTED";
		String escapedFeedback = feedback != null ? feedback.replace("\"", "\\\"") : "";

		if (contextJson.endsWith("}")) {
			contextJson = contextJson.substring(0, contextJson.length() - 1) + ",\"approvalStatus\":\"" + statusVal
					+ "\",\"approvalFeedback\":\"" + escapedFeedback + "\"}";
		} else {
			contextJson = "{\"approvalStatus\":\"" + statusVal + "\",\"approvalFeedback\":\"" + escapedFeedback + "\"}";
		}
		session.setContextData(contextJson);
		session.setStatus("RUNNING");
		session = sessionRepository.save(session);

		while ("RUNNING".equalsIgnoreCase(session.getStatus())) {
			session = engine.executeNextStep(spec, session, maxLoops);
			session = sessionRepository.save(session);
		}

		return session;
	}

	public void sendApprovalSignal(String sessionId, com.tuluat.engine.temporal.ApprovalSignal signal) {
		try {
			UUID id = UUID.fromString(sessionId);
			sessionRepository.findById(id).ifPresent(s -> {
				String statusVal = signal.isApproved() ? "APPROVED" : "REJECTED";
				String ctx = s.getContextData() != null ? s.getContextData() : "{}";
				if (ctx.endsWith("}")) {
					ctx = ctx.substring(0, ctx.length() - 1) + ",\"approvalStatus\":\"" + statusVal
							+ "\",\"approvalFeedback\":\""
							+ (signal.getFeedback() != null ? signal.getFeedback().replace("\"", "\\\"") : "") + "\"}";
				}
				s.setContextData(ctx);
				s.setStatus(signal.isApproved() ? "RUNNING" : "REJECTED");
				sessionRepository.save(s);
			});
		} catch (Exception ignored) {
		}
	}
}
