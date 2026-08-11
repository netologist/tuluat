package com.tuluat.engine.temporal;

import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import com.tuluat.engine.telemetry.WorkflowTelemetryService;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.guardrails.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class GraphNodeActivitiesImpl implements GraphNodeActivities {

	private final AgentExecutionService agentExecutionService;
	private final Optional<WorkflowSessionLogRepository> logRepository;
	private final Optional<WorkflowTelemetryService> telemetryService;
	private final Optional<GuardrailPipeline> guardrailPipeline;
	private final ExpressionParser parser = new SpelExpressionParser();

	public GraphNodeActivitiesImpl(AgentExecutionService agentExecutionService,
			Optional<WorkflowSessionLogRepository> logRepository, Optional<WorkflowTelemetryService> telemetryService,
			Optional<GuardrailPipeline> guardrailPipeline) {
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

		if (guardrailPipeline.isPresent() && node.outputSchema() != null && !node.outputSchema().isBlank()) {
			ValidationResult vr = guardrailPipeline.get().validateOutput(response.answer(), null, node.outputSchema());
			if (!vr.valid()) {
				String errMsg = String.format("Node '%s' output failed schema validation (confidence=%.2f): %s",
						node.id(), vr.confidence(), vr.errors());
				log.error(errMsg);
				recordLog(sessionId, node.id(), "ERROR", errMsg);
				telemetryService.ifPresent(ts -> ts.recordNodeExecuted("temporal-activity", node.type(), node.id()));
				throw new RuntimeException(
						"GraphNodeActivities: Node '" + node.id() + "' output validation failed: " + vr.errors());
			}
			recordLog(sessionId, node.id(), "INFO", "Node '" + node.id() + "' output passed schema validation");
		}

		telemetryService.ifPresent(ts -> ts.recordNodeExecuted("temporal-activity", node.type(), node.id()));
		return contextData;
	}

	@Override
	public boolean evaluateConditionNode(UUID sessionId, NodeDefinition node, Map<String, Object> contextData) {
		log.info("Temporal Activity: Evaluating Condition Node '{}' for session {}", node.id(), sessionId);
		recordLog(sessionId, node.id(), "INFO", "Evaluating Condition Node: " + node.id());

		if (node.expression() == null || node.expression().isBlank()) {
			return true;
		}
		StandardEvaluationContext evalContext = new StandardEvaluationContext();
		contextData.forEach(evalContext::setVariable);
		Boolean result = parser.parseExpression(node.expression()).getValue(evalContext, Boolean.class);
		boolean finalResult = Boolean.TRUE.equals(result);

		recordLog(sessionId, node.id(), "INFO", "Condition result: " + finalResult);
		return finalResult;
	}

	@Override
	public void recordLog(UUID sessionId, String nodeId, String level, String message) {
		logRepository.ifPresent(repo -> {
			WorkflowSessionLogEntity entity = new WorkflowSessionLogEntity();
			entity.setSessionId(sessionId);
			entity.setNodeId(nodeId);
			entity.setLogLevel(level);
			entity.setMessage(message);
			repo.save(entity);
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
}
