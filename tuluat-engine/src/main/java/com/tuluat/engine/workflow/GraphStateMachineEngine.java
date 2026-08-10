package com.tuluat.engine.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.crd.workflow.EdgeDefinition;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.telemetry.WorkflowTelemetryService;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

@Component
public class GraphStateMachineEngine {

	private static final Logger log = LoggerFactory.getLogger(GraphStateMachineEngine.class);
	private final AgentExecutionService agentExecutionService;
	private final WorkflowSessionLogRepository logRepository;
	private final WorkflowTelemetryService telemetryService;
	private final com.tuluat.guardrails.GuardrailPipeline guardrailPipeline;
	private final ExpressionParser parser = new SpelExpressionParser();
	private final ObjectMapper mapper = new ObjectMapper();

	public GraphStateMachineEngine(AgentExecutionService agentExecutionService) {
		this(agentExecutionService, null, null, null);
	}

	@Autowired
	public GraphStateMachineEngine(AgentExecutionService agentExecutionService,
			@Autowired(required = false) WorkflowSessionLogRepository logRepository,
			@Autowired(required = false) WorkflowTelemetryService telemetryService,
			@Autowired(required = false) com.tuluat.guardrails.GuardrailPipeline guardrailPipeline) {
		this.agentExecutionService = agentExecutionService;
		this.logRepository = logRepository;
		this.telemetryService = telemetryService;
		this.guardrailPipeline = guardrailPipeline;
	}

	public WorkflowSessionEntity executeNextStep(AiWorkflowSpec workflowSpec, WorkflowSessionEntity session,
			int maxLoops) {
		if (session.getLoopCount() >= maxLoops) {
			String errorMsg = String.format("Session %s exceeded max loops (%d)", session.getSessionId(), maxLoops);
			log.error(errorMsg);
			recordSessionLog(session.getSessionId(), session.getCurrentNodeId(), "ERROR", errorMsg);
		session.setStatus("FAILED");
			if (telemetryService != null) {
				telemetryService.recordSessionCompleted(session.getWorkflowName(), "FAILED");
			}
			return session;
		}

		String currentNodeId = session.getCurrentNodeId();
		if (currentNodeId == null || currentNodeId.isEmpty()) {
			currentNodeId = workflowSpec.getInitialNode();
			session.setCurrentNodeId(currentNodeId);
		}

		final String targetId = currentNodeId;
		NodeDefinition currentNode = workflowSpec.getNodes().stream().filter(n -> n.getId().equals(targetId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Node not found: " + session.getCurrentNodeId()));

		Map<String, Object> contextData = parseContext(session.getContextData());

		String infoMsg = String.format("Executing node '%s' (type: %s) for session %s", currentNode.getId(),
				currentNode.getType(), session.getSessionId());
		log.info(infoMsg);
		recordSessionLog(session.getSessionId(), currentNode.getId(), "INFO", infoMsg);

		if (telemetryService != null) {
			telemetryService.recordNodeExecuted(session.getWorkflowName(), currentNode.getType(), currentNode.getId());
		}

		if ("AGENT".equalsIgnoreCase(currentNode.getType())) {
			String prompt = resolvePromptTemplate(currentNode.getInputTemplate(), contextData);
			recordSessionLog(session.getSessionId(), currentNode.getId(), "INFO",
					"Executing Agent '" + currentNode.getAgentRef() + "' with prompt: " + prompt);

			AgentResponse response = agentExecutionService.executeAgent(currentNode.getAgentRef(), prompt, null);
			contextData.put(currentNode.getOutputKey(), response.answer());
			session.setContextData(writeContext(contextData));

			recordSessionLog(session.getSessionId(), currentNode.getId(), "INFO", "Agent '" + currentNode.getAgentRef()
					+ "' output saved to key '" + currentNode.getOutputKey() + "': " + response.answer());

			// Post-execution JSON Schema validation (ADR 004 / 007): node-level output
			// contract
			if (guardrailPipeline != null && currentNode.getOutputSchema() != null
					&& !currentNode.getOutputSchema().isBlank()) {
				com.tuluat.guardrails.ValidationResult vr = guardrailPipeline.validateOutput(response.answer(), null,
						currentNode.getOutputSchema());
				if (!vr.valid()) {
					String errMsg = String.format("Node '%s' output failed schema validation (confidence=%.2f): %s",
							currentNode.getId(), vr.confidence(), vr.errors());
					log.error(errMsg);
					recordSessionLog(session.getSessionId(), currentNode.getId(), "ERROR", errMsg);
					session.setStatus("FAILED");
					if (telemetryService != null) {
						telemetryService.recordSessionCompleted(session.getWorkflowName(), "FAILED");
					}
					return session;
				}
				recordSessionLog(session.getSessionId(), currentNode.getId(), "INFO",
						"Node '" + currentNode.getId() + "' output passed schema validation");
			}

			String nextNodeId = resolveNextNodeId(workflowSpec, currentNode.getId(), true);
			if (nextNodeId == null) {
				log.info("No next node found for session {}. Marking COMPLETED.", session.getSessionId());
				recordSessionLog(session.getSessionId(), currentNode.getId(), "INFO", "Workflow execution completed.");
				session.setStatus("COMPLETED");
				if (telemetryService != null) {
					telemetryService.recordSessionCompleted(session.getWorkflowName(), "COMPLETED");
				}
			} else {
				session.setCurrentNodeId(nextNodeId);
			}
		} else if ("CONDITION".equalsIgnoreCase(currentNode.getType())) {
			boolean result = evaluateCondition(currentNode.getExpression(), contextData);
			recordSessionLog(session.getSessionId(), currentNode.getId(), "INFO",
					"Condition expression '" + currentNode.getExpression() + "' evaluated to: " + result
							+ " with context: " + writeContext(contextData));

			String nextNodeId = resolveNextNodeId(workflowSpec, currentNode.getId(), result);
			if (nextNodeId == null) {
				log.info("No next node found after condition for session {}. Marking COMPLETED.",
						session.getSessionId());
				recordSessionLog(session.getSessionId(), currentNode.getId(), "INFO",
						"Workflow execution completed after condition.");
				session.setStatus("COMPLETED");
				if (telemetryService != null) {
					telemetryService.recordSessionCompleted(session.getWorkflowName(), "COMPLETED");
				}
			} else {
				session.setCurrentNodeId(nextNodeId);
			}
		} else if ("HUMAN_APPROVAL".equalsIgnoreCase(currentNode.getType())) {
			if (contextData.containsKey("approvalStatus")) {
				String approvalStatus = String.valueOf(contextData.get("approvalStatus"));
				boolean approved = "APPROVED".equalsIgnoreCase(approvalStatus);
				recordSessionLog(session.getSessionId(), currentNode.getId(), "INFO",
						"Processing approval decision: " + approvalStatus + ". Advancing graph.");
				String nextNodeId = resolveNextNodeId(workflowSpec, currentNode.getId(), approved);
				if (nextNodeId == null) {
					session.setStatus("COMPLETED");
				} else {
					session.setCurrentNodeId(nextNodeId);
				}
			} else {
				recordSessionLog(session.getSessionId(), currentNode.getId(), "INFO",
						"Workflow paused at node '" + currentNode.getId() + "' awaiting human approval.");
				session.setStatus("WAITING_APPROVAL");
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
		Boolean result = parser.parseExpression(expression).getValue(evalContext, Boolean.class);
		return Boolean.TRUE.equals(result);
	}

	public String resolveNextNodeId(AiWorkflowSpec spec, String fromNodeId, boolean conditionResult) {
		return spec.getEdges().stream().filter(e -> e.getFrom().equals(fromNodeId))
				.filter(e -> e.getCondition() == null || e.getCondition().isEmpty()
						|| Boolean.parseBoolean(e.getCondition()) == conditionResult)
				.map(EdgeDefinition::getTo).findFirst().orElse(null);
	}

	private void recordSessionLog(UUID sessionId, String nodeId, String level, String message) {
		if (logRepository != null && sessionId != null) {
			try {
				WorkflowSessionLogEntity entity = new WorkflowSessionLogEntity();
				entity.setSessionId(sessionId);
				entity.setNodeId(nodeId);
				entity.setLogLevel(level);
				entity.setMessage(message);
				logRepository.save(entity);
			} catch (Exception e) {
				log.warn("Failed to record session log to database: {}", e.getMessage());
			}
		}
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
			return json == null || json.isEmpty() ? new HashMap<>() : mapper.readValue(json, Map.class);
		} catch (Exception e) {
			return new HashMap<>();
		}
	}

	private String writeContext(Map<String, Object> data) {
		try {
			return mapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}
}
