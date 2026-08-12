package com.tuluat.engine.temporal;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.workflow.NodeDefinition;
import com.tuluat.engine.agent.AgentExecutionService;
import com.tuluat.engine.agent.AgentResolver;
import com.tuluat.engine.agent.AgentResponse;
import com.tuluat.engine.agent.UsageStats;
import com.tuluat.engine.entity.NodeExecutionEntity;
import com.tuluat.engine.entity.WorkflowSessionLogEntity;
import com.tuluat.engine.repository.NodeExecutionRepository;
import com.tuluat.engine.repository.WorkflowSessionLogRepository;
import com.tuluat.engine.telemetry.WorkflowTelemetryService;
import com.tuluat.guardrails.GuardrailPipeline;
import com.tuluat.guardrails.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Component
@Slf4j
public class GraphNodeActivitiesImpl implements GraphNodeActivities {

	private final AgentExecutionService agentExecutionService;
	private final Optional<WorkflowSessionLogRepository> logRepository;
	private final Optional<WorkflowTelemetryService> telemetryService;
	private final Optional<GuardrailPipeline> guardrailPipeline;
	private final Optional<NodeExecutionRepository> nodeExecutionRepository;
	private final Optional<AgentResolver> agentResolver;
	private final ExpressionParser parser = new SpelExpressionParser();

	public GraphNodeActivitiesImpl(AgentExecutionService agentExecutionService,
			Optional<WorkflowSessionLogRepository> logRepository, Optional<WorkflowTelemetryService> telemetryService,
			Optional<GuardrailPipeline> guardrailPipeline, Optional<NodeExecutionRepository> nodeExecutionRepository,
			Optional<AgentResolver> agentResolver) {
		this.agentExecutionService = agentExecutionService;
		this.logRepository = logRepository;
		this.telemetryService = telemetryService;
		this.guardrailPipeline = guardrailPipeline;
		this.nodeExecutionRepository = nodeExecutionRepository;
		this.agentResolver = agentResolver;
	}

	@Override
	public Map<String, Object> executeAgentNode(UUID sessionId, NodeDefinition node, Map<String, Object> contextData) {
		log.info("Temporal Activity: Executing Agent Node '{}' for session {}", node.id(), sessionId);
		recordLog(sessionId, node.id(), "INFO", "Executing Agent Node: " + node.id());

		OffsetDateTime startTime = OffsetDateTime.now();
		String prompt = resolvePromptTemplate(node.inputTemplate(), contextData);

		String provider = resolveProviderName(node.agentRef());
		AgentResponse response = agentExecutionService.executeAgent(node.agentRef(), prompt, null);
		OffsetDateTime endTime = OffsetDateTime.now();

		contextData.put(node.outputKey(), response.answer());

		// Persist node execution metrics
		persistNodeExecution(sessionId, node.id(), prompt, provider, response, startTime, endTime);

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

		String expression = node.expression() != null ? node.expression() : "true";
		StandardEvaluationContext evalCtx = new StandardEvaluationContext();
		evalCtx.setVariable("data", contextData);
		contextData.forEach(evalCtx::setVariable);

		Boolean result = parser.parseExpression(expression).getValue(evalCtx, Boolean.class);
		boolean evaluated = result != null && result;
		recordLog(sessionId, node.id(), "INFO",
				"Condition Node '" + node.id() + "' evaluated to " + evaluated + " (expr: " + expression + ")");

		telemetryService.ifPresent(ts -> ts.recordNodeExecuted("temporal-activity", node.type(), node.id()));
		return evaluated;
	}

	@Override
	public void recordLog(UUID sessionId, String nodeId, String level, String message) {
		logRepository.ifPresent(repo -> {
			var logEntry = new WorkflowSessionLogEntity();
			logEntry.setSessionId(sessionId);
			logEntry.setNodeId(nodeId);
			logEntry.setLogLevel(level);
			logEntry.setMessage(message);
			repo.save(logEntry);
		});
	}

	private String resolvePromptTemplate(String template, Map<String, Object> contextData) {
		if (template == null || template.isBlank()) {
			return "";
		}
		return template.replace("{{input}}", String.valueOf(contextData.getOrDefault("input", "")));
	}

	private void persistNodeExecution(UUID sessionId, String nodeId, String input, String provider,
			AgentResponse response, OffsetDateTime startTime, OffsetDateTime endTime) {
		nodeExecutionRepository.ifPresent(repo -> {
			var execution = new NodeExecutionEntity();
			execution.setSessionId(sessionId);
			execution.setNodeId(nodeId);
			execution.setAgentName(response.agentName());
			execution.setProvider(provider);
			execution.setModel(response.model());
			execution.setInputPrompt(input);
			execution.setOutputText(response.answer());
			execution.setStartTime(startTime);
			execution.setEndTime(endTime);
			execution.setDurationMs(java.time.Duration.between(startTime, endTime).toMillis());

			UsageStats usage = response.usage();
			if (usage != null) {
				execution.setTotalTokens(usage.totalTokens());
				execution.setInputTokens(usage.inputTokens());
				execution.setOutputTokens(usage.outputTokens());
				execution.setCostUsd(BigDecimal.valueOf(usage.estimatedCostUsd()));
			}

			execution.setStatus(response.isBlocked() ? "BLOCKED" : "COMPLETED");
			repo.save(execution);
			log.debug("Persisted node execution for session={} node={} tokens={} cost={}", sessionId, nodeId,
					execution.getTotalTokens(), execution.getCostUsd());
		});
	}

	private String resolveProviderName(String agentRef) {
		if (agentRef == null || agentRef.isBlank()) {
			return "default";
		}
		return agentResolver.flatMap(r -> r.resolve(agentRef, null)).map(agent -> {
			var spec = agent.getSpec();
			if (spec != null && spec.providerRef() != null && spec.providerRef().name() != null) {
				return spec.providerRef().name();
			}
			return agentRef;
		}).orElse(agentRef);
	}
}
