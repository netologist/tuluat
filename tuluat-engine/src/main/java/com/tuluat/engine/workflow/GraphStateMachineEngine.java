package com.tuluat.engine.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.EdgeDefinition;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import com.tuluat.engine.telemetry.WorkflowTelemetryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Component
@Slf4j
public class GraphStateMachineEngine {

	private final AgentExecutionService agentExecutionService;
	private final Optional<WorkflowSessionLogRepository> logRepository;
	private final Optional<WorkflowTelemetryService> telemetryService;
	private final Optional<com.tuluat.guardrails.GuardrailPipeline> guardrailPipeline;
	private final ExpressionParser parser = new SpelExpressionParser();
	private final ObjectMapper objectMapper;

	@Autowired
	public GraphStateMachineEngine(AgentExecutionService agentExecutionService,
			Optional<WorkflowSessionLogRepository> logRepository, Optional<WorkflowTelemetryService> telemetryService,
			Optional<com.tuluat.guardrails.GuardrailPipeline> guardrailPipeline, ObjectMapper objectMapper) {
		this.agentExecutionService = agentExecutionService;
		this.logRepository = logRepository;
		this.telemetryService = telemetryService;
		this.guardrailPipeline = guardrailPipeline;
		this.objectMapper = objectMapper;
	}

	public WorkflowSessionEntity executeNextStep(AiWorkflowSpec workflowSpec, WorkflowSessionEntity session,
			int maxLoops) {
		if (session.getLoopCount() >= maxLoops) {
			String errorMsg = String.format("Session %s exceeded max loops (%d)", session.getSessionId(), maxLoops);
			log.error(errorMsg);
			recordSessionLog(session.getSessionId(), session.getCurrentNodeId(), "ERROR", errorMsg);
			session.setStatus(SessionStatus.FAILED);
			telemetryService.ifPresent(ts -> ts.recordSessionCompleted(session.getWorkflowName(), "FAILED"));
			return session;
		}

		String currentNodeId = session.getCurrentNodeId();
		if (currentNodeId == null || currentNodeId.isEmpty()) {
			currentNodeId = workflowSpec.initialNode();
			session.setCurrentNodeId(currentNodeId);
		}

		final String targetId = currentNodeId;
		NodeDefinition currentNode = workflowSpec.nodes().stream().filter(n -> n.id().equals(targetId)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Node not found: " + session.getCurrentNodeId()));

		Map<String, Object> contextData = parseContext(session.getContextData());

		String infoMsg = String.format("Executing node '%s' (type: %s) for session %s", currentNode.id(),
				currentNode.type(), session.getSessionId());
		log.info(infoMsg);
		recordSessionLog(session.getSessionId(), currentNode.id(), "INFO", infoMsg);

		telemetryService.ifPresent(
				ts -> ts.recordNodeExecuted(session.getWorkflowName(), currentNode.type(), currentNode.id()));

		if ("AGENT".equalsIgnoreCase(currentNode.type())) {
			String prompt = resolvePromptTemplate(currentNode.inputTemplate(), contextData);
			recordSessionLog(session.getSessionId(), currentNode.id(), "INFO",
					"Executing Agent '" + currentNode.agentRef() + "' with prompt: " + prompt);

			AgentResponse response = agentExecutionService.executeAgent(currentNode.agentRef(), prompt, null,
					session.getSessionId());
			contextData.put(currentNode.outputKey(), response.answer());
			session.setContextData(writeContext(contextData));

			recordSessionLog(session.getSessionId(), currentNode.id(), "INFO", "Agent '" + currentNode.agentRef()
					+ "' output saved to key '" + currentNode.outputKey() + "': " + response.answer());

			if (guardrailPipeline.isPresent() && currentNode.outputSchema() != null
					&& !currentNode.outputSchema().isBlank()) {
				com.tuluat.guardrails.ValidationResult vr = guardrailPipeline.get().validateOutput(response.answer(),
						null, currentNode.outputSchema());
				if (!vr.valid()) {
					String errMsg = String.format("Node '%s' output failed schema validation (confidence=%.2f): %s",
							currentNode.id(), vr.confidence(), vr.errors());
					log.error(errMsg);
					recordSessionLog(session.getSessionId(), currentNode.id(), "ERROR", errMsg);
					session.setStatus(SessionStatus.FAILED);
					telemetryService.ifPresent(ts -> ts.recordSessionCompleted(session.getWorkflowName(), "FAILED"));
					return session;
				}
				recordSessionLog(session.getSessionId(), currentNode.id(), "INFO",
						"Node '" + currentNode.id() + "' output passed schema validation");
			}

			String nextNodeId = resolveNextNodeId(workflowSpec, currentNode.id(), true);
			if (nextNodeId == null) {
				log.info("No next node found for session {}. Marking COMPLETED.", session.getSessionId());
				recordSessionLog(session.getSessionId(), currentNode.id(), "INFO", "Workflow execution completed.");
				session.setStatus(SessionStatus.COMPLETED);
				telemetryService.ifPresent(ts -> ts.recordSessionCompleted(session.getWorkflowName(), "COMPLETED"));
			} else {
				session.setCurrentNodeId(nextNodeId);
			}
		} else if ("CONDITION".equalsIgnoreCase(currentNode.type())) {
			boolean result = evaluateCondition(currentNode.expression(), contextData);
			recordSessionLog(session.getSessionId(), currentNode.id(), "INFO",
					"Condition expression '" + currentNode.expression() + "' evaluated to: " + result
							+ " with context: " + writeContext(contextData));

			String nextNodeId = resolveNextNodeId(workflowSpec, currentNode.id(), result);
			if (nextNodeId == null) {
				log.info("No next node found after condition for session {}. Marking COMPLETED.",
						session.getSessionId());
				recordSessionLog(session.getSessionId(), currentNode.id(), "INFO",
						"Workflow execution completed after condition.");
				session.setStatus(SessionStatus.COMPLETED);
				telemetryService.ifPresent(ts -> ts.recordSessionCompleted(session.getWorkflowName(), "COMPLETED"));
			} else {
				session.setCurrentNodeId(nextNodeId);
			}
		} else if ("HUMAN_APPROVAL".equalsIgnoreCase(currentNode.type())) {
			if (contextData.containsKey("approvalStatus")) {
				String approvalStatus = String.valueOf(contextData.get("approvalStatus"));
				boolean approved = "APPROVED".equalsIgnoreCase(approvalStatus);
				recordSessionLog(session.getSessionId(), currentNode.id(), "INFO",
						"Processing approval decision: " + approvalStatus + ". Advancing graph.");
				String nextNodeId = resolveNextNodeId(workflowSpec, currentNode.id(), approved);
				if (nextNodeId == null) {
					session.setStatus(SessionStatus.COMPLETED);
				} else {
					session.setCurrentNodeId(nextNodeId);
				}
			} else {
				recordSessionLog(session.getSessionId(), currentNode.id(), "INFO",
						"Workflow paused at node '" + currentNode.id() + "' awaiting human approval.");
				session.setStatus(SessionStatus.WAITING_APPROVAL);
				return session;
			}
		}
		session.setLoopCount(session.getLoopCount() + 1);
		return session;
	}

	public boolean evaluateCondition(String expression, Map<String, Object> contextData) {
		if (expression == null || expression.isBlank())
			return true;
		StandardEvaluationContext evalContext = new StandardEvaluationContext();
		evalContext.setVariable("data", contextData);
		contextData.forEach(evalContext::setVariable);
		return Boolean.TRUE.equals(parser.parseExpression(expression).getValue(evalContext, Boolean.class));
	}

	public String resolveNextNodeId(AiWorkflowSpec spec, String fromNodeId, boolean conditionResult) {
		return spec.edges().stream().filter(e -> e.from().equals(fromNodeId))
				.filter(e -> e.condition() == null || e.condition().isEmpty()
						|| Boolean.parseBoolean(e.condition()) == conditionResult)
				.map(EdgeDefinition::to).findFirst().orElse(null);
	}

	private void recordSessionLog(UUID sessionId, String nodeId, String level, String message) {
		logRepository.ifPresent(repo -> {
			if (sessionId != null) {
				try {
					WorkflowSessionLogEntity entity = new WorkflowSessionLogEntity();
					entity.setSessionId(sessionId);
					entity.setNodeId(nodeId);
					entity.setLogLevel(level);
					entity.setMessage(message);
					repo.save(entity);
				} catch (Exception e) {
					log.warn("Failed to record session log to database: {}", e.getMessage());
				}
			}
		});
	}

	private String resolvePromptTemplate(String template, Map<String, Object> contextData) {
		if (template == null)
			return "";
		String result = template;
		for (Map.Entry<String, Object> entry : contextData.entrySet()) {
			result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseContext(String json) {
		try {
			return json != null ? objectMapper.readValue(json, Map.class) : new java.util.HashMap<>();
		} catch (JsonProcessingException e) {
			log.error("Failed to parse context data", e);
			return new java.util.HashMap<>();
		}
	}

	private String writeContext(Map<String, Object> data) {
		try {
			return objectMapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize context data", e);
			return "{}";
		}
	}
}
