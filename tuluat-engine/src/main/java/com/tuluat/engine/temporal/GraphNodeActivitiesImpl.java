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
    private final ExpressionParser parser = new SpelExpressionParser();

    @Autowired
    public GraphNodeActivitiesImpl(AgentExecutionService agentExecutionService,
                                  @Autowired(required = false) WorkflowSessionLogRepository logRepository,
                                  @Autowired(required = false) WorkflowTelemetryService telemetryService) {
        this.agentExecutionService = agentExecutionService;
        this.logRepository = logRepository;
        this.telemetryService = telemetryService;
    }

    @Override
    public Map<String, Object> executeAgentNode(UUID sessionId, NodeDefinition node, Map<String, Object> contextData) {
        log.info("Temporal Activity: Executing Agent Node '{}' for session {}", node.getId(), sessionId);
        recordLog(sessionId, node.getId(), "INFO", "Executing Agent Node: " + node.getId());

        String prompt = resolvePromptTemplate(node.getInputTemplate(), contextData);
        AgentResponse response = agentExecutionService.executeAgent(node.getAgentRef(), prompt, null);

        contextData.put(node.getOutputKey(), response.answer());

        if (telemetryService != null) {
            telemetryService.recordNodeExecuted("temporal-workflow", "AGENT", node.getId());
        }

        return contextData;
    }

    @Override
    public boolean evaluateConditionNode(UUID sessionId, NodeDefinition node, Map<String, Object> contextData) {
        log.info("Temporal Activity: Evaluating Condition Node '{}' for session {}", node.getId(), sessionId);
        if (node.getExpression() == null || node.getExpression().isBlank()) return true;

        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.setVariable("data", contextData);
        Boolean result = parser.parseExpression(node.getExpression()).getValue(evalContext, Boolean.class);
        boolean eval = Boolean.TRUE.equals(result);

        recordLog(sessionId, node.getId(), "INFO", "Condition expression evaluated to: " + eval);

        if (telemetryService != null) {
            telemetryService.recordNodeExecuted("temporal-workflow", "CONDITION", node.getId());
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
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : contextData.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
