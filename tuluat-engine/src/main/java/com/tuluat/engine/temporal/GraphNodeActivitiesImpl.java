package com.tuluat.engine.temporal;

import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.telemetry.WorkflowTelemetryService;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class GraphNodeActivitiesImpl implements GraphNodeActivities {

	private static final Logger log = LoggerFactory.getLogger(GraphNodeActivitiesImpl.class);
	private final AgentExecutionService agentExecutionService;
	private final WorkflowSessionLogRepository logRepository;
	private final WorkflowTelemetryService telemetryService;
	private final com.tuluat.guardrails.GuardrailPipeline guardrailPipeline;
	private final ExpressionParser parser = new SpelExpressionParser();

	@Autowired
	public GraphNodeActivitiesImpl(AgentExecutionService agentExecutionService,
			@Autowired(required = false) WorkflowSessionLogRepository logRepository,
			@Autowired(required = false) WorkflowTelemetryService telemetryService,
			@Autowired(required = false) com.tuluat.guardrails.GuardrailPipeline guardrailPipeline) {
		this.agentExecutionService = agentExecutionService;
		this.logRepository = logRepository;
		this.telemetryService = telemetryService;
		this.guardrailPipeline = guardrailPipeline;
	}

	@Override
	public Map<String, Object> executeAgentNode(UUID sessionId, NodeDefinition node, Map<String, Object> contextData) {
		log.info("Temporal Activity: Executing Agent Node '{}' for session {}", node.id(), sessionId);
		recordLog(sessionId, node.id(), "INFO", "Executing Agent Node: " + node.id());

		String prompt = resolvePromptTemplate(node.inputTemplate(), contextData);
		AgentResponse response = agentExecutionService.executeAgent(node.agentRef(), prompt, null);

		contextData.put(node.outputKey(), response.answer());

		// Post-execution JSON Schema validation (ADR 004 / 007); failure fails the
		// activity
		if (guardrailPipeline != null && node.outputSchema() != null && !node.outputSchema().isBlank()) {
			com.tuluat.guardrails.ValidationResult vr = guardrailPipeline.validateOutput(response.answer(), null,
					node.outputSchema());
			if (!vr.valid()) {
				String errMsg = String.format(
						"Temporal node '%s' output failed schema validation (confidence=%.2f): %s", node.id(),
						vr.confidence(), vr.errors());
				log.error(errMsg);
				recordLog(sessionId, node.id(), "ERROR", errMsg);
				throw new IllegalStateException(errMsg);
			}
			recordLog(sessionId, node.id(), "INFO",
					"Temporal node '" + node.id() + "' output passed schema validation");
		}

		if (telemetryService != null) {
			telemetryService.recordNodeExecuted("temporal-workflow", "AGENT", node.id());
		}

		return contextData;
	}

	@Override
	public boolean evaluateConditionNode(UUID sessionId, NodeDefinition node, Map<String, Object> contextData) {
		log.info("Temporal Activity: Evaluating Condition Node '{}' for session {}", node.id(), sessionId);
		if (node.expression() == null || node.expression().isBlank())
			return true;

		StandardEvaluationContext evalContext = new StandardEvaluationContext();
		evalContext.setVariable("data", contextData);
		Boolean result = parser.parseExpression(node.expression()).getValue(evalContext, Boolean.class);
		boolean eval = Boolean.TRUE.equals(result);

		recordLog(sessionId, node.id(), "INFO", "Condition expression evaluated to: " + eval);

		if (telemetryService != null) {
			telemetryService.recordNodeExecuted("temporal-workflow", "CONDITION", node.id());
		}

		return eval;
	}

	@Override
	public void recordLog(UUID sessionId, String nodeId, String level, String message) {
		if (logRepository != null && sessionId != null) {
			try {
				WorkflowSessionLogEntity entity = new WorkflowSessionLogEntity();
				entity.setSessionId(sessionId);
				entity.setNodeId(nodeId);
				entity.setLogLevel(level);
				entity.setMessage(message);
				logRepository.save(entity);
			} catch (Exception e) {
				log.warn("Failed to record Temporal session log: {}", e.getMessage());
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
}
